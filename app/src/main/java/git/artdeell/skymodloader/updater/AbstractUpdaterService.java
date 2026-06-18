package git.artdeell.skymodloader.updater;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public abstract class AbstractUpdaterService extends Service {
    public static final byte SERVICE_STATE_CHECKING = 0;
    public static final byte SERVICE_STATE_UPDATE_AVAILABLE = 1;
    public static final byte SERVICE_STATE_DOWNLOADING = 2;
    public static final byte SERVICE_STATE_FAILURE = 3;
    public static final byte SERVICE_STATE_PROCEED = 4;
    public static final byte SERVICE_STATE_DOWNLOAD_FINISHED = 5;
    public static final byte SERVICE_STATE_INSTALLING = 6;
    public static final byte SERVICE_STATE_INSTALL_FINISHED = 5;
    private IUpdaterConnection mUpdaterListener = new IUpdaterConnection.Default();
    private final AtomicLong mCurrentProgress = new AtomicLong(0);
    private final AtomicLong mMaximumProgress = new AtomicLong(-1);
    private Exception mDownloadException;
    private String updateChangelog;
    private String updateURL;
    private File downloadTarget;
    private byte serviceState = SERVICE_STATE_CHECKING;


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new IUpdater.Stub() {
            @Override
            public void setUpdateListener(IUpdaterConnection listener) {
                mUpdaterListener = listener;
                Log.i("AbstractUpdater", "Set listener: "+listener);
                try {
                    mUpdaterListener.onStateChanged();
                    mUpdaterListener.onProgressBarChanged();
                }catch (RemoteException e) {
                    Log.wtf("AbstractUpdater", "Failed to announce changes to brand new listener?!?", e);
                }
            }

            public byte getServiceState() {
                return serviceState;
            }
            public String getUpdateChangelog() {
                return updateChangelog;
            }
            public void downloadUpdate() {
                new Thread(() -> AbstractUpdaterService.this.downloadUpdate0()).start();
            }
            public long getDownloadMaximum() {
                return mMaximumProgress.get();
            }
            public long getDownloadCurrent() {
                return mCurrentProgress.get();
            }
            public ParcelableException getException() {
                return new ParcelableException(mDownloadException);
            }
            public void suicide() {
                Log.i("UpdateService", "Suicide! Yay!");
                AbstractUpdaterService.this.stopSelf();
                Process.killProcess(Process.myPid());
            }
        };
    }

    @Override
    public void onCreate() {
        super.onCreate();
        downloadTarget = new File(getCacheDir(), getCacheFileName());
        if(serviceAutoStarts()) start();
    }

    public void onDestroy() {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    protected void downloadUpdate0() {
        try {
            changeState(SERVICE_STATE_DOWNLOADING);
            HttpURLConnection conn = (HttpURLConnection) new URL(updateURL).openConnection();
            conn.connect();
            long totalLength = conn.getContentLength();
            setProgressBarMax(totalLength);
            InputStream is = conn.getInputStream();
            try (FileOutputStream fos = new FileOutputStream(downloadTarget)) {
               copyStream(is, fos, totalLength != -1);
            }catch (IOException e) {
                is.close();
                throw e;
            }
            if(hasInstallActions()) {
                doInstall();
            } else {
                changeState(SERVICE_STATE_DOWNLOAD_FINISHED);
            }
        } catch (IOException e) {
            mDownloadException = e;
            changeState(SERVICE_STATE_FAILURE);
        }
    }

    private void doInstall() {
        changeState(SERVICE_STATE_INSTALLING);
        try {
            performInstallActions();
        }catch (Exception e) {
            mDownloadException = e;
            changeState(SERVICE_STATE_FAILURE);
            return;
        }
        changeState(SERVICE_STATE_INSTALL_FINISHED);
    }

    protected JSONObject pullUpdateInfo() throws IOException, JSONException {
        // "https://api.github.com/repos/RomanChamelo/Canvas-Open-Source/releases/latest"
        InputStream inputStream = new URL(getUpdateCheckerURL()).openStream();
        return new JSONObject(new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n")));
    }
    protected String getAssetURL(JSONObject release) throws JSONException {
        if(!release.has("assets")) return null;
        JSONArray assets = release.getJSONArray("assets");
        for(int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if(isTargetAsset(asset.getString("name"))) return asset.getString("browser_download_url");
        }
        return null;
    }

    private void escapeUpdater() {
        mMaximumProgress.set(0);
        changeState(SERVICE_STATE_PROCEED);
        announceProgressChange();
    }

    public void findUpdates() {
        if(downloadTarget.exists()) downloadTarget.delete();
        try {
            JSONObject updateInfo = pullUpdateInfo();
            updateURL = getAssetURL(updateInfo);
            if(updateURL != null && needsUpdate(updateInfo)) {
                updateChangelog = updateInfo.getString("body");
                serviceState = SERVICE_STATE_UPDATE_AVAILABLE;
                mMaximumProgress.set(0);
                mUpdaterListener.onStateChanged();
                mUpdaterListener.onProgressBarChanged();
                return;
            }
            escapeUpdater();
        }catch (Exception e) {
            mDownloadException = e;
            mMaximumProgress.set(0);
            changeState(SERVICE_STATE_FAILURE);
            announceProgressChange();
        }
    }

    protected void announceProgressChange() {
        try {
            mUpdaterListener.onProgressBarChanged();
        } catch (RemoteException e) {
            Log.e("AbstractUpdater", "Failed to announce changes in everything", e);
        }
    }

    protected void changeState(byte newState) {
        try {
            serviceState = newState;
            mUpdaterListener.onStateChanged();
        }catch (RemoteException e) {
            Log.e("AbstractUpdater", "Failed to announce state change", e);
        }
    }

    protected final void start() {
        new Thread(this::findUpdates).start();
    }

    protected final void setDownloadException(Exception exception) {
        mDownloadException = exception;
    }

    protected final void setUpdateChangelog(String changelog) {
        updateChangelog = changelog;
    }

    protected final File getDownloadTarget() {
        return downloadTarget;
    }

    protected final void setProgressBarMax(long max) {
        mMaximumProgress.set(max);
        announceProgressChange();
    }

    protected final void copyStream(InputStream inputStream,
                                    OutputStream outputStream,
                                    boolean broadcast) throws IOException{
        mCurrentProgress.set(0);
        byte[] buf = new byte[8192];
        int len;
        while ((len = inputStream.read(buf)) != -1) {
            outputStream.write(buf, 0, len);
            if(!broadcast) continue;
            mCurrentProgress.addAndGet(len);
            announceProgressChange();
        }
    }

    protected abstract String getCacheFileName();
    protected abstract String getUpdateCheckerURL();
    protected abstract boolean isTargetAsset(String assetName);
    protected abstract boolean hasInstallActions();
    protected abstract boolean serviceAutoStarts();
    protected abstract boolean needsUpdate(JSONObject updateInfo) throws JSONException;
    protected abstract void performInstallActions() throws Exception;
}
