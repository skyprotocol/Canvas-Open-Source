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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import git.artdeell.skymodloader.R;
import git.artdeell.skymodloader.elfmod.ElfModMetadata;
import git.artdeell.skymodloader.elfmod.ElfRefcountLoader;

public class ModUpdaterService extends AbstractUpdaterService {
    private static final String GITHUB_USER_AGENT = "Canvas-ModUpdater/1.8.0";
    private static final String GITHUB_API_REPOS_PREFIX = "https://api.github.com/repos/";
    private static final String GITHUB_WEB_PREFIX = "https://github.com/";
    private static final Pattern HREF_ATTRIBUTE_PATTERN = Pattern.compile(
        "\\shref\\s*=\\s*([\"'])([^\"']*)\\1", Pattern.CASE_INSENSITIVE);
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

    private static final class HttpRequestException extends IOException {
        private final int responseCode;
        private final String rateLimitRemaining;
        private final String responseBody;

        private HttpRequestException(int responseCode, String rateLimitRemaining,
                                     String responseBody, String message) {
            super(message);
            this.responseCode = responseCode;
            this.rateLimitRemaining = rateLimitRemaining;
            this.responseBody = responseBody == null ? "" : responseBody;
        }

        private boolean isGithubRateLimit() {
            if(responseCode != 403) return false;
            String normalizedBody = responseBody.toLowerCase(Locale.ROOT);
            return "0".equals(rateLimitRemaining)
                || normalizedBody.contains("rate limit");
        }
    }

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
        try {
            return new JSONObject(readUrl(url));
        } catch(HttpRequestException error) {
            if(!url.startsWith(GITHUB_API_REPOS_PREFIX) || !error.isGithubRateLimit()) {
                throw error;
            }

            Log.w("ModUpdaterService", "GitHub API quota exhausted; using public release fallback");
            return readLatestReleaseWithoutApi(url);
        } catch(IOException error) {
            throw error;
        }
    }

    private JSONObject readLatestReleaseWithoutApi(String apiUrl) throws IOException, JSONException {
        String repository = githubRepositoryFromApiUrl(apiUrl);
        if(repository == null) {
            throw new IOException("Unsupported GitHub release API URL");
        }

        String tag = readLatestReleaseTag(repository);
        String expandedAssetsUrl = GITHUB_WEB_PREFIX + repository
            + "/releases/expanded_assets/" + tag;
        JSONArray assets = extractReleaseAssets(readUrl(expandedAssetsUrl), repository, tag);
        if(assets.length() == 0) {
            throw new IOException("GitHub latest release has no downloadable assets");
        }

        JSONObject release = new JSONObject();
        release.put("tag_name", tag);
        release.put("body", "");
        release.put("assets", assets);
        return release;
    }

    private JSONArray extractReleaseAssets(String html, String repository, String tag)
        throws IOException, JSONException {
        String marker = "/" + repository + "/releases/download/" + tag + "/";
        Set<String> seenUrls = new HashSet<>();
        JSONArray assets = new JSONArray();
        int cursor = 0;

        while(cursor < html.length()) {
            int anchorStart = html.indexOf("<a", cursor);
            if(anchorStart < 0) break;
            if(anchorStart + 2 < html.length()
                && !Character.isWhitespace(html.charAt(anchorStart + 2))
                && html.charAt(anchorStart + 2) != '>') {
                cursor = anchorStart + 2;
                continue;
            }

            int anchorEnd = html.indexOf('>', anchorStart);
            if(anchorEnd < 0) break;

            String anchor = html.substring(anchorStart, anchorEnd + 1);
            Matcher hrefMatcher = HREF_ATTRIBUTE_PATTERN.matcher(anchor);
            if(!hrefMatcher.find()) {
                cursor = anchorEnd + 1;
                continue;
            }

            String path = hrefMatcher.group(2);
            if(path.startsWith(marker)) {
                String encodedName = path.substring(marker.length());
                if(!encodedName.isEmpty() && !encodedName.contains("/")
                    && !encodedName.contains("\\") && !encodedName.contains("?")
                    && !encodedName.contains("#") && !encodedName.equals(".")
                    && !encodedName.equals("..")) {
                    String assetName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name());
                    if(!assetName.isEmpty() && !assetName.contains("/")
                        && !assetName.contains("\\") && !assetName.equals(".")
                        && !assetName.equals("..")) {
                        String downloadUrl = GITHUB_WEB_PREFIX + path;
                        if(seenUrls.add(downloadUrl)) {
                            JSONObject asset = new JSONObject();
                            asset.put("name", assetName);
                            asset.put("browser_download_url", downloadUrl);
                            assets.put(asset);
                        }
                    }
                }
            }
            cursor = anchorEnd + 1;
        }

        return assets;
    }

    private String githubRepositoryFromApiUrl(String apiUrl) {
        String suffix = "/releases/latest";
        if(!apiUrl.startsWith(GITHUB_API_REPOS_PREFIX) || !apiUrl.endsWith(suffix)) {
            return null;
        }

        String repository = apiUrl.substring(
            GITHUB_API_REPOS_PREFIX.length(), apiUrl.length() - suffix.length());
        return repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
            ? repository
            : null;
    }

    private String readLatestReleaseTag(String repository) throws IOException {
        URL latestUrl = new URL(GITHUB_WEB_PREFIX + repository + "/releases/latest");
        HttpURLConnection connection = (HttpURLConnection) latestUrl.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", GITHUB_USER_AGENT);
        try {
            int responseCode = connection.getResponseCode();
            if(isRedirectResponse(responseCode)) {
                String location = connection.getHeaderField("Location");
                if(location == null || location.isEmpty()) {
                    throw new IOException("GitHub latest release redirect has no Location header");
                }
                URL redirectUrl = new URL(latestUrl, location);
                if(!"https".equalsIgnoreCase(redirectUrl.getProtocol())
                    || !"github.com".equalsIgnoreCase(redirectUrl.getHost())) {
                    throw new IOException("GitHub latest release redirected to an unexpected host");
                }
                return extractReleaseTagFromRedirectPath(redirectUrl.getPath(), repository);
            }

            throw new IOException("GitHub latest release did not return a redirect: HTTP "
                + responseCode);
        } finally {
            connection.disconnect();
        }
    }

    private boolean isRedirectResponse(int responseCode) {
        return responseCode == 301 || responseCode == 302 || responseCode == 303
            || responseCode == 307 || responseCode == 308;
    }

    private String extractReleaseTagFromRedirectPath(String path, String repository)
        throws IOException {
        String marker = "/" + repository + "/releases/tag/";
        if(!path.startsWith(marker)) {
            throw new IOException("GitHub latest release redirect path is unexpected");
        }
        String tag = path.substring(marker.length());
        if(!isValidReleaseTag(tag)) {
            throw new IOException("GitHub latest release tag is invalid");
        }
        return tag;
    }

    private boolean isValidReleaseTag(String tag) {
        if(tag.isEmpty() || tag.endsWith("/") || tag.contains("..")) return false;
        for(int i = 0; i < tag.length(); i++) {
            char character = tag.charAt(i);
            if(Character.isLetterOrDigit(character) || character == '.' || character == '_'
                || character == '-' || character == '+' || character == '/') {
                continue;
            }
            if(character == '%' && i + 2 < tag.length()
                && isHexDigit(tag.charAt(i + 1)) && isHexDigit(tag.charAt(i + 2))) {
                i += 2;
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean isHexDigit(char character) {
        return Character.digit(character, 16) >= 0;
    }

    private String readUrl(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", GITHUB_USER_AGENT);
        if(url.startsWith("https://api.github.com/")) {
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        }
        try {
            int responseCode = connection.getResponseCode();
            InputStream inputStream = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
            String response = readStream(inputStream);
            if(responseCode < 200 || responseCode >= 300) {
                String responsePreview = response.replace('\n', ' ').replace('\r', ' ').trim();
                if(responsePreview.length() > 500) {
                    responsePreview = responsePreview.substring(0, 500);
                }
                String rateLimitRemaining = connection.getHeaderField("X-RateLimit-Remaining");
                String rateLimitReset = connection.getHeaderField("X-RateLimit-Reset");
                String requestId = connection.getHeaderField("X-GitHub-Request-Id");
                String contentType = connection.getHeaderField("Content-Type");
                String errorMessage = "Request failed: HTTP " + responseCode
                    + " | Content-Type: " + contentType
                    + " | RateLimit-Remaining: " + rateLimitRemaining
                    + " | RateLimit-Reset: " + rateLimitReset
                    + " | GitHub-Request-Id: " + requestId
                    + " | Body: " + responsePreview;
                Log.e("ModUpdaterService", errorMessage);
                throw new HttpRequestException(
                    responseCode, rateLimitRemaining, response, errorMessage);
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private void downloadToFile(String url, File target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", GITHUB_USER_AGENT);
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
