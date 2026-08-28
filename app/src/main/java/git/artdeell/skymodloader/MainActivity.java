package git.artdeell.skymodloader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.tgc.sky.BuildConfig;
import com.tgc.sky.GameActivity;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dalvik.system.DexClassLoader;
import git.artdeell.skymodloader.elfmod.ElfRefcountLoader;
import git.artdeell.skymodloader.iconloader.IconLoader;
import git.artdeell.skymodloader.net.StarwatchBlocker;
import git.artdeell.skymodloader.server.ServerManager;

public class MainActivity extends Activity {
    private SharedPreferences sharedPreferences;
    private boolean ceserverEnabled;
    private boolean hideCanvasMenu;
    public static String SKY_PACKAGE_NAME;
    private Map<String, Integer> skyPackages;
    public static DeviceInfo deviceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        deviceInfo = getDeviceInfo();
        sharedPreferences = getSharedPreferences("package_configs", Context.MODE_PRIVATE);

        boolean logcatEnabled = sharedPreferences.getBoolean("logcat_enabled", false);
        if (logcatEnabled) {
            startLogcatMonitoring();
        }

        SKY_PACKAGE_NAME = sharedPreferences.getString("sky_package_name", "com.tgc.sky.android");
        ceserverEnabled = sharedPreferences.getBoolean("ceserver", false);
        hideCanvasMenu = sharedPreferences.getBoolean("hide_canvas_menu", false);

        sharedPreferences.edit().putString("sky_package_name", SKY_PACKAGE_NAME).apply();
        skyPackages = new HashMap<>();
        skyPackages.put("com.tgc.sky.android", 0);
        skyPackages.put("com.tgc.sky.vn.android", 0);
        skyPackages.put("com.tgc.sky.android.huawei", 1);
        loadGame();
    }

    private void startLogcatMonitoring() {
        try {
            Intent logcatIntent = new Intent(this, LogcatMonitorService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(logcatIntent);
            } else {
                startService(logcatIntent);
            }
            Log.d("MainActivity", "Logcat monitoring started");
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to start logcat monitoring", e);
        }
    }

    private String getSkyBuildAccessKey() {
        try {
            ApplicationInfo skyInfo = getPackageManager().getApplicationInfo(
                SKY_PACKAGE_NAME, PackageManager.GET_META_DATA
            );
            DexClassLoader skyLoader = new DexClassLoader(
                skyInfo.sourceDir,
                getCacheDir().getAbsolutePath(),
                skyInfo.nativeLibraryDir,
                ClassLoader.getSystemClassLoader()
            );
            Class<?> buildConfig = skyLoader.loadClass("com.tgc.sky.BuildConfig");
            Field field = buildConfig.getDeclaredField("SKY_BUILD_ACCESS_KEY");
            field.setAccessible(true);
            String key = (String) field.get(null);
            Log.i("MainActivity", "SkyBuildKey loaded from Sky BuildConfig");
            return key;
        } catch (Exception e) {
            Log.e("MainActivity", "getSkyBuildAccessKey failed: " + e.getMessage());
            return null;
        }
    }

    private void loadCppShared(String libPath) {
        try {
            System.loadLibrary("c++_shared");
            Log.i("MainActivity", "libc++_shared loaded via loadLibrary");
            return;
        } catch (UnsatisfiedLinkError e) {
            Log.w("MainActivity", "loadLibrary c++_shared failed, trying from libPath: " + e.getMessage());
        }
        try {
            if (libPath.contains("!/lib")) {
                System.load(libPath + "/libc++_shared.so");
            } else {
                File lib = new File(libPath, "libc++_shared.so");
                if (lib.exists()) System.load(lib.getAbsolutePath());
                else Log.w("MainActivity", "libc++_shared.so not found at: " + lib.getAbsolutePath());
            }
            Log.i("MainActivity", "libc++_shared loaded via fallback path");
        } catch (UnsatisfiedLinkError e) {
            Log.w("MainActivity", "libc++_shared fallback also failed, continuing: " + e.getMessage());
        }
    }

    private void loadGame() {
        PackageManager pm = getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(SKY_PACKAGE_NAME, PackageManager.GET_SHARED_LIBRARY_FILES);
            SMLApplication.skyPName = info.packageName;
            SMLApplication.skyRes = pm.getResourcesForApplication(info.packageName);
            SMLApplication.smlRes = getResources();
            String versionName = info.versionName;
            BuildConfig.SKY_VERSION = versionName.substring(0, versionName.indexOf(' ')).trim();
            BuildConfig.VERSION_CODE = info.versionCode;

            String libPath = resolveLibPath(info.applicationInfo);
            String elfLibPath = resolveElfLibPath(info.applicationInfo, libPath);
            Log.i("MainActivity", "elfLibPath: " + elfLibPath);

            File modsDir = new File(getFilesDir(), "mods");
            File configDir = new File(getFilesDir(), "config");
            if (!configDir.isDirectory() && !configDir.mkdirs())
                throw new IOException("Failed to create mod configuration directory");

            Log.i("MainActivity", "Pre-loading FMOD dependencies from: " + libPath);
            org.fmod.FMOD.init(this);

            if (!libPath.contains("!/lib")) {
                File fmodLibDir = new File(libPath);
                Log.i("MainActivity", "Listing all files in: " + libPath);
                File[] allFiles = fmodLibDir.listFiles();
                if (allFiles != null) {
                    for (File f : allFiles) {
                        Log.i("MainActivity", "Found file: " + f.getName());
                    }
                } else {
                    Log.e("MainActivity", "Directory is empty or doesn't exist!");
                }
            } else {
                Log.i("MainActivity", "Loading libs directly from APK (no extraction)");
            }

            loadCppShared(libPath);

            String[] libsToLoad = {
                "libOpenSLES.so",
                "libfmod.so",
                "libfmodstudio.so"
            };

            for (String libName : libsToLoad) {
                try {
                    if (libPath.contains("!/lib")) {
                        String fullApkLibPath = libPath + "/" + libName;
                        Log.i("MainActivity", "Loading from APK: " + fullApkLibPath);
                        System.load(fullApkLibPath);
                    } else {
                        File lib = new File(libPath, libName);
                        if (!lib.exists()) {
                            Log.w("MainActivity", "Not found: " + libName + " at " + lib.getAbsolutePath());
                            continue;
                        }
                        Log.i("MainActivity", "Loading: " + libName);
                        System.load(lib.getAbsolutePath());
                    }
                    Log.i("MainActivity", "Successfully loaded: " + libName);
                } catch (Throwable e) {
                    Log.e("MainActivity", "Failed to load " + libName + ": " + e.getMessage());
                }
            }

            ElfLoader loader = new ElfLoader(elfLibPath);
            loader.loadLib("libBootloader.so");
            System.loadLibrary("ciphered");
            nativeSetStarwatchAllowed(!sharedPreferences.getBoolean(
                StarwatchBlocker.PREF_BLOCK_STARWATCH, false));

            String buildKey = getSkyBuildAccessKey();
            if (buildKey != null) {
                BuildConfig.SKY_BUILD_ACCESS_KEY = buildKey;
                nativeSetSkyBuildKey(buildKey);
                Log.i("MainActivity", "BuildKey applied: " + buildKey.substring(0, Math.min(20, buildKey.length())) + "...");
            } else {
                Log.w("MainActivity", "NOT FOUND, DEFAULT INSTEAD: " + BuildConfig.SKY_BUILD_ACCESS_KEY);
            }

            setDeviceInfoNative(
                deviceInfo.xdpi,
                deviceInfo.ydpi,
                deviceInfo.density,
                Optional.ofNullable(deviceInfo.deviceName).orElse(""),
                Optional.ofNullable(deviceInfo.deviceManufacturer).orElse(""),
                Optional.ofNullable(deviceInfo.deviceModel).orElse(""));
            IconLoader.findIcons();
            BuildConfig.VERSION_CODE = sharedPreferences.getBoolean("skip_updates", false) ? 0x99999 : info.versionCode;
            Integer gameType = skyPackages.getOrDefault(SKY_PACKAGE_NAME, 0);
            if (sharedPreferences.getBoolean("custom_server", false)) {
                String customHost = sharedPreferences.getString("server_host", ServerManager.getDefaultHost());
                if (customHost != null) {
                    customHost = ServerManager.sanitizeHost(customHost);
                    if (!customHost.isEmpty()) {
                        BuildConfig.SKY_SERVER_HOSTNAME = customHost;
                    }
                }
                MainActivity.customServer(BuildConfig.SKY_SERVER_HOSTNAME);
            }
            MainActivity.settle(
                info.versionCode,
                gameType == null ? 0 : gameType,
                BuildConfig.SKY_SERVER_HOSTNAME,
                configDir.getAbsolutePath(),
                SMLApplication.skyRes.getAssets(),
                ceserverEnabled,
                hideCanvasMenu
            );
            AccountStorage.sync(this);

            new ElfRefcountLoader(elfLibPath, modsDir).load();
            BuildConfig.APPLICATION_ID = SKY_PACKAGE_NAME;
            startActivity(new Intent(this, GameActivity.class));

        } catch (PackageManager.NameNotFoundException e) {
            alertDialog(getString(R.string.sky_not_installed));
        } catch (Throwable e) {
            alertDialog(e);
        }
    }

    private String resolveLibPath(ApplicationInfo appInfo) throws IOException {
        File libDir = new File(appInfo.nativeLibraryDir);
        if (libDir.exists() && libDir.listFiles() != null && libDir.listFiles().length > 0) {
            Log.i("MainActivity", "Using nativeLibraryDir: " + appInfo.nativeLibraryDir);
            return appInfo.nativeLibraryDir;
        }

        String apkDirectPath = tryGetApkDirectPath(appInfo);
        if (apkDirectPath != null) {
            Log.i("MainActivity", "Using direct APK path: " + apkDirectPath);
            return apkDirectPath;
        }

        Log.w("MainActivity", "Libs are compressed, falling back to extraction");
        return extractLibrariesFromApk(appInfo);
    }

    private String tryGetApkDirectPath(ApplicationInfo appInfo) {
        String[] splitDirs = appInfo.splitSourceDirs;
        if (splitDirs != null) {
            for (String splitApk : splitDirs) {
                if (splitApk.contains("arm64_v8a") || splitApk.contains("config.arm64") || splitApk.contains("arm64")) {
                    if (areLibsStoredUncompressed(splitApk)) {
                        Log.i("MainActivity", "Found stored libs in split APK: " + splitApk);
                        return splitApk + "!/lib/arm64-v8a";
                    }
                    break;
                }
            }
        }
        if (areLibsStoredUncompressed(appInfo.sourceDir)) {
            Log.i("MainActivity", "Found stored libs in base APK: " + appInfo.sourceDir);
            return appInfo.sourceDir + "!/lib/arm64-v8a";
        }
        return null;
    }

    private boolean areLibsStoredUncompressed(String apkPath) {
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(apkPath)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                if (entry.getName().startsWith("lib/arm64-v8a/") && entry.getName().endsWith(".so")) {
                    boolean stored = entry.getMethod() == java.util.zip.ZipEntry.STORED;
                    Log.d("MainActivity", entry.getName() + " compression=" + (stored ? "STORED" : "DEFLATED"));
                    return stored;
                }
            }
        } catch (IOException e) {
            Log.e("MainActivity", "areLibsStoredUncompressed failed for " + apkPath + ": " + e.getMessage());
        }
        return false;
    }

    private String resolveElfLibPath(ApplicationInfo appInfo, String mainLibPath) throws IOException {
        if (!mainLibPath.contains("!/lib")) {
            return mainLibPath;
        }

        File extractDir = new File(getFilesDir(), "extracted_libs");
        if (!extractDir.exists() && !extractDir.mkdirs()) {
            throw new IOException("Failed to create extraction directory");
        }

        File bootloaderFile = new File(extractDir, "libBootloader.so");

        boolean useCustomBootloader = sharedPreferences.getBoolean("use_custom_bootloader", false);
        if (useCustomBootloader && bootloaderFile.exists()) {
            Log.d("MainActivity", "Custom libBootloader.so enabled, skip extraction");
            return extractDir.getAbsolutePath();
        }

        int lastExtractedVersion = sharedPreferences.getInt("bootloader_version", -1);
        int currentVersion;
        try {
            currentVersion = getPackageManager().getPackageInfo(SKY_PACKAGE_NAME, 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException("Sky package not found: " + e.getMessage());
        }

        if (bootloaderFile.exists() && lastExtractedVersion == currentVersion) {
            Log.d("MainActivity", "libBootloader.so already updated (v" + currentVersion + "), skip extraction");
            return extractDir.getAbsolutePath();
        }

        if (bootloaderFile.exists()) {
			Log.i("MainActivity", "Sky updated (v" + lastExtractedVersion + " → v" + currentVersion + "), re-extraction");
			bootloaderFile.delete();
			for (String dep : new String[]{"libfmod.so", "libfmodstudio.so", "libc++_shared.so"}) {
				new File(extractDir, dep).delete();
				}
		}

        String apkPath = mainLibPath.substring(0, mainLibPath.indexOf("!/lib"));
        Log.i("MainActivity", "Extracting native libs from: " + apkPath);
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(apkPath)) {
            java.util.zip.ZipEntry entry = zipFile.getEntry("lib/arm64-v8a/libBootloader.so");
            if (entry == null) throw new IOException("libBootloader.so not found in APK");
            try (java.io.InputStream in = zipFile.getInputStream(entry);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(bootloaderFile)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
            }
            bootloaderFile.setExecutable(true);
            bootloaderFile.setReadable(true);
            Log.i("MainActivity", "libBootloader.so extracted (v" + currentVersion + ")");

            String[] deps = {"libfmod.so", "libfmodstudio.so", "libc++_shared.so"};
            for (String dep : deps) {
                File depFile = new File(extractDir, dep);
                if (!depFile.exists()) {
                    java.util.zip.ZipEntry depEntry = zipFile.getEntry("lib/arm64-v8a/" + dep);
                    if (depEntry != null) {
                        try (java.io.InputStream in = zipFile.getInputStream(depEntry);
                             java.io.FileOutputStream out = new java.io.FileOutputStream(depFile)) {
                            byte[] buf = new byte[8192];
                            int read;
                            while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
                        }
                        depFile.setExecutable(true);
                        depFile.setReadable(true);
                        Log.i("MainActivity", dep + " extracted as ElfLoader dependency");
                    } else {
                        Log.w("MainActivity", dep + " not found in APK");
                    }
                }
            }
        }

        sharedPreferences.edit().putInt("bootloader_version", currentVersion).apply();
        return extractDir.getAbsolutePath();
    }

    private String extractLibrariesFromApk(ApplicationInfo appInfo) throws IOException {
        File extractDir = new File(getFilesDir(), "extracted_libs");
        if (!extractDir.exists() && !extractDir.mkdirs()) {
            throw new IOException("Failed to create extraction directory");
        }

        Log.i("MainActivity", "Looking for split APKs...");
        Log.i("MainActivity", "sourceDir: " + appInfo.sourceDir);
        String[] splitSourceDirs = appInfo.splitSourceDirs;
        if (splitSourceDirs != null) {
            Log.i("MainActivity", "Found " + splitSourceDirs.length + " split APKs");
            for (String splitApk : splitSourceDirs) {
                Log.i("MainActivity", "Checking split APK: " + splitApk);
                if (splitApk.contains("arm64_v8a") || splitApk.contains("config.arm64") || splitApk.contains("arm64")) {
                    Log.i("MainActivity", "Found ARM64 split APK: " + splitApk);
                    extractLibsFromZip(splitApk, extractDir);
                    break;
                }
            }
        } else {
            Log.w("MainActivity", "No split APKs found, trying base APK");
            extractLibsFromZip(appInfo.sourceDir, extractDir);
        }

        return extractDir.getAbsolutePath();
    }

    private void extractLibsFromZip(String apkPath, File destDir) throws IOException {
        Log.i("MainActivity", "Extracting libs from: " + apkPath);
        java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(apkPath);
        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
        int libCount = 0;
        while (entries.hasMoreElements()) {
            java.util.zip.ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith("lib/arm64-v8a/") && name.endsWith(".so")) {
                String libName = name.substring(name.lastIndexOf('/') + 1);
                File destFile = new File(destDir, libName);
                if (!destFile.exists()) {
                    try (java.io.InputStream in = zipFile.getInputStream(entry);
                         java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                    destFile.setExecutable(true);
                    destFile.setReadable(true);
                    libCount++;
                    Log.i("MainActivity", "Extracted: " + libName);
                } else {
                    Log.d("MainActivity", "Already exists: " + libName);
                }
            }
        }
        zipFile.close();
        Log.i("MainActivity", "Extracted " + libCount + " libraries");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void alertDialog(Throwable th) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        th.printStackTrace(pw);
        String stackTrace = sw.toString();
        pw.close();
        AlertDialog dialog = getAlertDialog(stackTrace);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            copyToClipboard(stackTrace);
        });
    }

    private @NonNull AlertDialog getAlertDialog(String stackTrace) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(stackTrace);
        builder.setPositiveButton(android.R.string.ok, (d, w) -> finish());
        AlertDialog dialog = builder.create();
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Copy", (d, which) -> {
            copyToClipboard(stackTrace);
        });
        dialog.show();
        return dialog;
    }

    private void copyToClipboard(String stackTrace) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Stack Trace", stackTrace);
        clipboard.setPrimaryClip(clip);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.toast_stack_trace_copied, Toast.LENGTH_SHORT).show();
        }
    }

    public void alertDialog(String th) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(th);
        builder.setPositiveButton(android.R.string.ok, (d, w) -> finish());
        builder.show();
    }

    public DeviceInfo getDeviceInfo() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.xdpi = displayMetrics.xdpi;
        deviceInfo.ydpi = displayMetrics.ydpi;
        deviceInfo.density = displayMetrics.density;
        deviceInfo.deviceName = Settings.Global.getString(getContentResolver(), "device_name");
        if (deviceInfo.deviceName == null || deviceInfo.deviceName.isEmpty()) {
            deviceInfo.deviceName = Settings.Secure.getString(getContentResolver(), "bluetooth_name");
        }
        deviceInfo.deviceName = (deviceInfo.deviceName == null || deviceInfo.deviceName.isEmpty())
            ? "NO_DEVICE_NAME" : deviceInfo.deviceName;
        deviceInfo.deviceManufacturer = Build.MANUFACTURER;
        deviceInfo.deviceModel = Build.MODEL;
        return deviceInfo;
    }

    public static native void settle(
        int _gameVersion,
        int _gameType,
        String _hostName,
        String _configDir,
        android.content.res.AssetManager _gameAssets,
        boolean _ceserverEnabled,
        boolean _hideCanvasMenu
    );

    public static native void setDeviceInfoNative(
        float _xdpi,
        float _ydpi,
        float _density,
        String _deviceName,
        String _manufacturer,
        String _model
    );

    public static native void onKeyboardCompleteNative(String message);
    public static native void customServer(String url);
    public static native void lateInitUserLibs();
    public static native void getSysetemUI(Object systemUI);
    private static native void nativeSetSkyBuildKey(String key);
    private static native void nativeSetStarwatchAllowed(boolean allowed);

    // Video stats pushed down from SystemIO_android.UpdateMediaPlayer, which
    // already runs per frame on the thread that owns the ExoPlayer instance.
    // Mods read these through CanvasQueryHostNumber; see the key registry in
    // main.cpp. Pass -1 for a value that is not available - none of these can
    // legitimately be negative, and the native side translates it.
    public static native void nativeSetVideoStats(long durationMs, long positionMs,
                                                  int isLive, int isSeekable,
                                                  int playbackState);

    public static class DeviceInfo {
        public float xdpi;
        public float ydpi;
        public float density;
        public String deviceName;
        public String deviceManufacturer;
        public String deviceModel;
    }
}
