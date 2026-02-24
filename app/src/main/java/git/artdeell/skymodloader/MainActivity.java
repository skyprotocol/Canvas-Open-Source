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
            String nativeLibraryDir = info.applicationInfo.nativeLibraryDir;
            String libPath = nativeLibraryDir;
            File libDir = new File(nativeLibraryDir);
            if (!libDir.exists() || libDir.listFiles() == null || libDir.listFiles().length == 0) {
                libPath = extractLibrariesFromApk(info.applicationInfo);
            }

            File modsDir = new File(getFilesDir(), "mods");
            File configDir = new File(getFilesDir(), "config");
            if (!configDir.isDirectory() && !configDir.mkdirs())
                throw new IOException("Failed to create mod configuration directory");
            android.util.Log.i("MainActivity", "Pre-loading FMOD dependencies from: " + libPath);
            org.fmod.FMOD.init(this);
            File fmodLibDir = new File(libPath);
            android.util.Log.i("MainActivity", "Listing all files in: " + libPath);
            File[] allFiles = fmodLibDir.listFiles();
            if (allFiles != null) {
                for (File f : allFiles) {
                    android.util.Log.i("MainActivity", "Found file: " + f.getName());
                }
            } else {
                android.util.Log.e("MainActivity", "Directory is empty or doesn't exist!");
            }

            String[] libsToLoad = {
                "libc++_shared.so",
                "libOpenSLES.so",
                "libfmod.so",
                "libfmodstudio.so"
            };
            for (String libName : libsToLoad) {
                File lib = new File(fmodLibDir, libName);
                if (lib.exists()) {
                    try {
                        android.util.Log.i("MainActivity", "Loading: " + libName);
                        System.load(lib.getAbsolutePath());
                        android.util.Log.i("MainActivity", "Successfully loaded: " + libName);
                    } catch (Throwable e) {
                        android.util.Log.e("MainActivity", "Failed to load " + libName + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    android.util.Log.w("MainActivity", "Not found: " + libName + " at " + lib.getAbsolutePath());
                }
            }
            ElfLoader loader = new ElfLoader(libPath + ":/system/lib64");
            loader.loadLib("libBootloader.so");
            System.loadLibrary("ciphered");

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
            MainActivity.settle(
                info.versionCode,
                gameType == null ? 0 : gameType,
                BuildConfig.SKY_SERVER_HOSTNAME,
                configDir.getAbsolutePath(),
                SMLApplication.skyRes.getAssets(),
                ceserverEnabled,
                hideCanvasMenu
            );
            if (sharedPreferences.getBoolean("custom_server", false)) {
                BuildConfig.SKY_SERVER_HOSTNAME = sharedPreferences.getString("server_host",
                    BuildConfig.SKY_SERVER_HOSTNAME);
                MainActivity.customServer(BuildConfig.SKY_SERVER_HOSTNAME);
            }

            new ElfRefcountLoader(libPath + ":/system/lib64", modsDir).load();
            BuildConfig.APPLICATION_ID = SKY_PACKAGE_NAME;
            startActivity(new Intent(this, GameActivity.class));
        } catch (PackageManager.NameNotFoundException e) {
            alertDialog(getString(R.string.sky_not_installed));
        } catch (Throwable e) {
            alertDialog(e);
        }
    }

    private String extractLibrariesFromApk(ApplicationInfo appInfo) throws IOException {
        File extractDir = new File(getFilesDir(), "extracted_libs");
        if (!extractDir.exists() && !extractDir.mkdirs()) {
            throw new IOException("Failed to create extraction directory");
        }

        android.util.Log.i("MainActivity", "Looking for split APKs...");
        android.util.Log.i("MainActivity", "sourceDir: " + appInfo.sourceDir);
        String[] splitSourceDirs = appInfo.splitSourceDirs;
        if (splitSourceDirs != null) {
            android.util.Log.i("MainActivity", "Found " + splitSourceDirs.length + " split APKs");
            for (String splitApk : splitSourceDirs) {
                android.util.Log.i("MainActivity", "Checking split APK: " + splitApk);
                if (splitApk.contains("arm64_v8a") || splitApk.contains("config.arm64") || splitApk.contains("arm64")) {
                    android.util.Log.i("MainActivity", "Found ARM64 split APK: " + splitApk);
                    extractLibsFromZip(splitApk, extractDir);
                    break;
                }
            }
        } else {
            android.util.Log.w("MainActivity", "No split APKs found, trying base APK");
            extractLibsFromZip(appInfo.sourceDir, extractDir);
        }

        return extractDir.getAbsolutePath();
    }

    private void extractLibsFromZip(String apkPath, File destDir) throws IOException {
        android.util.Log.i("MainActivity", "Extracting libs from: " + apkPath);
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
                    android.util.Log.i("MainActivity", "Extracted: " + libName);
                } else {
                    android.util.Log.d("MainActivity", "Already exists: " + libName);
                }
            }
        }
        zipFile.close();
        android.util.Log.i("MainActivity", "Extracted " + libCount + " libraries");
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
            Toast.makeText(this, "Stack trace copied to clipboard", Toast.LENGTH_SHORT).show();
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

        deviceInfo.deviceName = (deviceInfo.deviceName == null || deviceInfo.deviceName.isEmpty()) ? "NO_DEVICE_NAME"
            : deviceInfo.deviceName;
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
}