package git.artdeell.skymodloader.updater;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import git.artdeell.skymodloader.R;
import git.artdeell.skymodloader.elfmod.ElfModMetadata;
import git.artdeell.skymodloader.elfmod.ElfRefcountLoader;

public class ModUpdaterService extends AbstractUpdaterService {
    public static final String EXTRA_UPDATE_URL = "update_url";
    public static final String EXTRA_OFFSETS_URL = "offsets_url";
    public static final String EXTRA_LIB_NAME = "lib_name";
    public static final String EXTRA_VERSION_NUMBER = "version_number";
    private String mGithubUpdaterURL;
    private String mOffsetsURL;
    private File mLibraryPath;
    private VersionNumber mCurrentVersionNumber;
    private boolean mModUpdateAvailable;
    private boolean mOffsetsUpdateAvailable;
    private String mModDownloadURL;
    private String mPendingOffsetsJson;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("ModUpdaterService", "onStartCommand...");
        Bundle extras = intent.getExtras();
        if(extras == null) {
            Log.w("ModUpdater","Extras missing for ModUpdater startup");
        }else {
            mGithubUpdaterURL = extras.getString(EXTRA_UPDATE_URL);
            mOffsetsURL = extras.getString(EXTRA_OFFSETS_URL);
            mCurrentVersionNumber = (VersionNumber) extras.getSerializable(EXTRA_VERSION_NUMBER);
            String libName = extras.getString(EXTRA_LIB_NAME);
            mLibraryPath = new File(getFilesDir(), "mods"+File.separator+libName);
            start();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected String getCacheFileName() {
        return "libtemp.so.temp";
    }

    @Override
    protected String getUpdateCheckerURL() {
        return mGithubUpdaterURL;
    }

    @Override
    protected boolean isTargetAsset(String assetName) {
        return assetName.startsWith("lib") && assetName.endsWith(".so");
    }

    @Override
    protected boolean hasInstallActions() {
        return true;
    }

    @Override
    protected boolean serviceAutoStarts() {
        return false;
    }

    @Override
    protected boolean needsUpdate(JSONObject updateInfo) throws JSONException {
        String tag = updateInfo.getString("tag_name");
        Log.i("ModUpdaterService", "Tag: "+tag);
        VersionNumber newVersion = VersionNumber.parseVersion(tag);
        if(newVersion == null) return false;
        return mCurrentVersionNumber.compare(newVersion) < 0;
    }

    @Override
    public void findUpdates() {
        if(getDownloadTarget().exists()) getDownloadTarget().delete();
        mModUpdateAvailable = false;
        mOffsetsUpdateAvailable = false;
        mModDownloadURL = null;
        mPendingOffsetsJson = null;

        try {
            StringBuilder changelog = new StringBuilder();
            if(hasText(mGithubUpdaterURL)) {
                JSONObject updateInfo = readJsonObject(mGithubUpdaterURL);
                String assetURL = getAssetURL(updateInfo);
                if(assetURL != null && needsUpdate(updateInfo)) {
                    mModUpdateAvailable = true;
                    mModDownloadURL = assetURL;
                    String releaseNotes = updateInfo.optString("body");
                    if(hasText(releaseNotes)) {
                        changelog.append(releaseNotes);
                    }
                }
            }

            if(hasText(mOffsetsURL)) {
                String offsetsJson = readUrl(mOffsetsURL);
                Object parsedOffsetsJson = parseJson(offsetsJson);
                String normalizedRemoteOffsets = normalizeJson(parsedOffsetsJson);
                String normalizedLocalOffsets = readLocalOffsetsJson();
                if(!normalizedRemoteOffsets.equals(normalizedLocalOffsets)) {
                    mOffsetsUpdateAvailable = true;
                    mPendingOffsetsJson = offsetsJson;
                    if(changelog.length() > 0) {
                        changelog.append("\n\n");
                    }
                    changelog.append(buildOffsetsChangelog(parsedOffsetsJson));
                }
            }

            setProgressBarMax(0);
            if(mModUpdateAvailable || mOffsetsUpdateAvailable) {
                setUpdateChangelog(changelog.length() > 0 ? changelog.toString() : null);
                changeState(SERVICE_STATE_UPDATE_AVAILABLE);
            } else {
                changeState(SERVICE_STATE_PROCEED);
            }
        } catch (Exception e) {
            setDownloadException(e);
            setProgressBarMax(0);
            changeState(SERVICE_STATE_FAILURE);
        }
    }

    @Override
    protected void downloadUpdate0() {
        try {
            if(mModUpdateAvailable) {
                changeState(SERVICE_STATE_DOWNLOADING);
                downloadToFile(mModDownloadURL, getDownloadTarget());
                changeState(SERVICE_STATE_INSTALLING);
                performInstallActions();
            }

            if(mOffsetsUpdateAvailable) {
                changeState(SERVICE_STATE_DOWNLOADING);
                String offsetsJson = mPendingOffsetsJson != null ? mPendingOffsetsJson : readUrl(mOffsetsURL);
                parseJson(offsetsJson);

                changeState(SERVICE_STATE_INSTALLING);
                byte[] offsetsBytes = offsetsJson.getBytes(StandardCharsets.UTF_8);
                setProgressBarMax(offsetsBytes.length);
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(offsetsBytes);
                     FileOutputStream outputStream = new FileOutputStream(getOffsetsFile())) {
                    copyStream(inputStream, outputStream, true);
                }
            }

            changeState(SERVICE_STATE_INSTALL_FINISHED);
        } catch (Exception e) {
            setDownloadException(e);
            changeState(SERVICE_STATE_FAILURE);
        }
    }

    @Override
    protected void performInstallActions() throws Exception {
        File source = getDownloadTarget();
        ElfModMetadata metadata = ElfRefcountLoader.loadMetadata(source);
        // Make sure that we actually replace the mod name after an update
        // if the library name changes.
        if(!mLibraryPath.delete())
            throw new IOException("Failed to delete old mod file");
        // Pick the new name based on metadata.
        File modsFolder = mLibraryPath.getParentFile();
        assert modsFolder != null;
        mLibraryPath = new File(modsFolder, metadata.name);
        // Copy the file.
        long length = source.length();
        setProgressBarMax(length);
        try (FileInputStream inputStream = new FileInputStream(getDownloadTarget())) {
            try (FileOutputStream outputStream = new FileOutputStream(mLibraryPath)) {
                copyStream(inputStream, outputStream, length != -1);
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private JSONObject readJsonObject(String url) throws IOException, JSONException {
        return new JSONObject(readUrl(url));
    }

    private String readUrl(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        try {
            int responseCode = connection.getResponseCode();
            InputStream inputStream = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
            String response = readStream(inputStream);
            if(responseCode < 200 || responseCode >= 300) {
                throw new IOException("Request failed: HTTP " + responseCode);
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private void downloadToFile(String url, File target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.connect();
        try (InputStream inputStream = connection.getInputStream();
             FileOutputStream outputStream = new FileOutputStream(target)) {
            long totalLength = connection.getContentLength();
            setProgressBarMax(totalLength);
            copyStream(inputStream, outputStream, totalLength != -1);
        } finally {
            connection.disconnect();
        }
    }

    private String readStream(InputStream inputStream) throws IOException {
        if(inputStream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    private String readLocalOffsetsJson() {
        File offsetsFile = getOffsetsFile();
        if(!offsetsFile.exists()) return "";
        try (FileInputStream inputStream = new FileInputStream(offsetsFile)) {
            return normalizeJson(parseJson(readStream(inputStream)));
        } catch (Exception e) {
            return "";
        }
    }

    private Object parseJson(String json) throws JSONException {
        Object parsedJson = new JSONTokener(json).nextValue();
        if(parsedJson instanceof JSONObject || parsedJson instanceof JSONArray) {
            return parsedJson;
        }
        throw new JSONException("Offsets URL did not return a JSON object or array");
    }

    private String normalizeJson(Object parsedJson) throws JSONException {
        if(parsedJson instanceof JSONObject || parsedJson instanceof JSONArray) {
            return parsedJson.toString();
        }
        throw new JSONException("Offsets URL did not return a JSON object or array");
    }

    private String buildOffsetsChangelog(Object parsedJson) {
        StringBuilder changelog = new StringBuilder(getString(R.string.mod_check_offsets_found));
        if(!(parsedJson instanceof JSONObject)) {
            return changelog.toString();
        }

        JSONObject offsetsInfo = (JSONObject) parsedJson;
        String version = offsetsInfo.optString("version");
        String contributors = formatContributors(offsetsInfo.opt("contributors"));
        String comments = formatOffsetsComments(offsetsInfo);

        if(hasText(version)) {
            changelog.append("\n\n").append(getString(R.string.offsets_version, escapeMarkdown(version)));
        }
        if(hasText(contributors)) {
            changelog.append("\n").append(getString(R.string.offsets_contributors, contributors));
        }
        if(hasText(comments)) {
            changelog.append("\n\n").append(comments);
        }
        return changelog.toString();
    }

    private String formatContributors(Object contributors) {
        if(contributors instanceof JSONArray) {
            return formatContributorList((JSONArray) contributors);
        }
        if(contributors != null && contributors != JSONObject.NULL) {
            return escapeMarkdown(String.valueOf(contributors));
        }
        return "";
    }

    private String formatContributorList(JSONArray contributors) {
        if(contributors.length() == 0) return "";

        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < contributors.length(); i++) {
            String contributor = contributors.optString(i);
            if(!hasText(contributor)) continue;
            if(builder.length() > 0) builder.append(", ");
            builder.append(escapeMarkdown(contributor));
        }
        return builder.toString();
    }

    private String formatOffsetsComments(JSONObject offsetsInfo) {
        Object comments = offsetsInfo.opt("comments");
        if(comments instanceof JSONArray) {
            return formatCommentList((JSONArray) comments);
        }
        if(comments != null && comments != JSONObject.NULL) {
            return escapeMarkdown(String.valueOf(comments));
        }
        return "";
    }

    private String formatCommentList(JSONArray comments) {
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < comments.length(); i++) {
            String comment = comments.optString(i);
            if(!hasText(comment)) continue;
            if(builder.length() > 0) builder.append('\n');
            builder.append("- ").append(escapeMarkdown(comment));
        }
        return builder.toString();
    }

    private String escapeMarkdown(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("`", "\\`")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("#", "\\#");
    }

    private File getOffsetsFile() {
        String modLibName = mLibraryPath.getName();
        if(modLibName.endsWith(".so")) {
            modLibName = modLibName.substring(0, modLibName.length() - 3);
        }
        File configDir = new File(getFilesDir(), "config");
        if(!configDir.exists()) configDir.mkdirs();
        return new File(configDir, modLibName + "_offsets.json");
    }
}
