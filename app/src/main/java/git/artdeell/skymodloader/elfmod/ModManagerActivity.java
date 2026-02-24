package git.artdeell.skymodloader.elfmod;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;

import git.artdeell.skymodloader.BuildConfig;
import git.artdeell.skymodloader.DialogY;
import git.artdeell.skymodloader.LogcatMonitorService;
import git.artdeell.skymodloader.MainActivity;
import git.artdeell.skymodloader.R;
import git.artdeell.skymodloader.SettingsActivity;
import git.artdeell.skymodloader.SMLApplication;
import git.artdeell.skymodloader.updater.CanvasUpdaterConnection;
import git.artdeell.skymodloader.updater.CanvasUpdaterService;
import git.artdeell.skymodloader.updater.ModUpdater;
import git.artdeell.skymodloader.updater.ModUpdaterService;
import git.artdeell.skymodloader.updater.VersionNumber;

public class ModManagerActivity extends Activity implements LoadingListener, ModUpdater {
    private static final int REQUEST_MOD = 1024 * 121;
    @SuppressLint("StaticFieldLeak")
    private static ElfUIBackbone loader;
    private RecyclerView modListView;
    private View addModButton;
    private View loadingBar;
    private Button btnLaunchLive;
    private Button btnLaunchHuawei;
    private Button btnLaunchChplay;
    private String skyPackageName;
    private SharedPreferences sharedPreferences;
    private ArrayList<String> skyPackages;
    private ModUpdaterDialogManager mDialogManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        runUpdater();
        setContentView(R.layout.mod_manager);
        modListView = findViewById(R.id.mm_modList);
        addModButton = findViewById(R.id.mm_addMod);
        loadingBar = findViewById(R.id.mm_loadBar);
        btnLaunchLive = findViewById(R.id.mm_launch_live);
        btnLaunchHuawei = findViewById(R.id.mm_launch_huawei);
        btnLaunchChplay = findViewById(R.id.mm_launch_chplay);
        ((TextView) findViewById(R.id.mm_versionName)).setText(getString(R.string.mod_canvas_version, BuildConfig.VERSION_NAME));
        initializeSkyPackages();
        sharedPreferences = getSharedPreferences("package_configs", Context.MODE_PRIVATE);
        updateButtonTextColor();
        initializeModUpdater();
        initializeLoader();
        modListView.setLayoutManager(new LinearLayoutManager(this));
        modListView.setAdapter(new ModListAdapter(loader));
        setButtonClickListeners();
        setButtonLongClickListeners();
    }

    private void initializeModUpdater() {
        mDialogManager = new ModUpdaterDialogManager(this);
        bindService(new Intent(this, ModUpdaterService.class), mDialogManager, 0);
    }

    public void startModUpdater(ElfModUIMetadata metadata) {
        Log.i("MMA", "Starting mod update...");
        if (mDialogManager.isConnected()) {
            Toast.makeText(this, R.string.updater_busy, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent serviceStartIntent = new Intent(this, ModUpdaterService.class);
        serviceStartIntent.putExtra(ModUpdaterService.EXTRA_UPDATE_URL, metadata.getGithubReleasesUrl());
        serviceStartIntent.putExtra(ModUpdaterService.EXTRA_LIB_NAME, metadata.name);
        serviceStartIntent.putExtra(ModUpdaterService.EXTRA_VERSION_NUMBER,
            new VersionNumber(metadata.majorVersion, metadata.minorVersion, metadata.patchVersion)
        );
        startService(serviceStartIntent);
        bindService(new Intent(this, ModUpdaterService.class), mDialogManager, 0);
    }

    private void initializeSkyPackages() {
        skyPackages = new ArrayList<>();
        skyPackages.add("com.tgc.sky.android");
        skyPackages.add("com.tgc.sky.vn.android");
        skyPackages.add("com.tgc.sky.android.huawei");
        SMLApplication.skyPName = skyPackages.get(0);
    }

    private void updateButtonTextColor() {
        skyPackageName = sharedPreferences.getString("sky_package_name", null);
        if (skyPackageName == null) {
            skyPackageName = "com.tgc.sky.android";
            sharedPreferences.edit().putString("sky_package_name", skyPackageName).apply();
        }

        setButtonTextColor(btnLaunchLive, skyPackages.get(0));
        setButtonTextColor(btnLaunchChplay, skyPackages.get(1));
        setButtonTextColor(btnLaunchHuawei, skyPackages.get(2));
    }

    private void setButtonTextColor(Button button, String packageName) {
        if (skyPackageName != null && skyPackageName.equals(packageName)) {
            button.setTextColor(getColor(R.color.teal_700));
        } else {
            button.setTextColor(getColor(R.color.text));
        }
    }

    private void initializeLoader() {
        if (loader == null) {
            loader = new ElfUIBackbone(this, this);
            loader.addListener(this);
            loader.startLoadingAsync(new File(getFilesDir(), "mods"));
            mDialogManager.setLoader(loader);
        } else {
            handleLoading();
            handleUnsafeModRemoval();
            handleException();
            loader.addListener(this);
        }
    }

    private void setButtonClickListeners() {
        btnLaunchLive.setOnClickListener(view -> {
            skyPackageName = skyPackages.get(0);
            launchGame();
        });
        btnLaunchChplay.setOnClickListener(view -> {
            skyPackageName = skyPackages.get(1);
            launchGame();
        });
        btnLaunchHuawei.setOnClickListener(view -> {
            skyPackageName = skyPackages.get(2);
            launchGame();
        });
    }

    private void setButtonLongClickListeners() {
        btnLaunchLive.setOnLongClickListener(view -> {
            setSkyPackageName(skyPackages.get(0));
            return true;
        });
        btnLaunchChplay.setOnLongClickListener(view -> {
            setSkyPackageName(skyPackages.get(1));
            return true;
        });
        btnLaunchHuawei.setOnLongClickListener(view -> {
            setSkyPackageName(skyPackages.get(2));
            return true;
        });
    }

    public void setSkyPackageName(String pkg) {
        if (findPackage(pkg)) {
            sharedPreferences.edit().putString("sky_package_name", pkg).apply();
            updateButtonTextColor();
        } else {
            Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.game_not_installed_warning),
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    public void setHideCanvasMenu(boolean flag) {
        sharedPreferences.edit().putBoolean("hide_canvas_menu", flag).apply();
    }

    public void setSkipUpdates(boolean flag) {
        sharedPreferences.edit().putBoolean("skip_updates", flag).apply();
    }

    public void setCeserver(boolean flag) {
        sharedPreferences.edit().putBoolean("ceserver", flag).apply();
    }

    public void setCustomServer(boolean flag) {
        sharedPreferences.edit().putBoolean("custom_server", flag).apply();
    }

    public void setServerUrl(String url) {
        sharedPreferences.edit().putString("server_host", url).apply();
    }

    public void setLogcatEnabled(boolean flag) {
        sharedPreferences.edit().putBoolean("logcat_enabled", flag).apply();
        Intent logcatIntent = new Intent(this, LogcatMonitorService.class);
        if (flag) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(logcatIntent);
            } else {
                startService(logcatIntent);
            }
            Toast.makeText(this, "Logcat monitoring enabled", Toast.LENGTH_SHORT).show();
        } else {
            stopService(logcatIntent);
            Toast.makeText(this, "Logcat monitoring disabled", Toast.LENGTH_SHORT).show();
        }
    }

    public void onAddMod(View v) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_MOD);
    }

    public void onModInfo(View v) {
        SpannableString message = new SpannableString(
            "How to add a mod:\n\n" +
            "① Download a compatible .so file\n\n" +
            "② Tap on \"Add Mod\" and select the file\n\n" +
            "③ You can activate or disable the mod with the toggle\n\n" +
            "④ Start the game\n\n" +
            "⚠︎ Sometimes mods are broken/need to be updated!\n\n" +
            "───────────────────\n\n" +
            "Community\n\n" +
            "Discord  |  Telegram"
        );

        String full = message.toString();

        int discordStart = full.indexOf("Discord");
        int discordEnd = discordStart + "Discord".length();
        int telegramStart = full.indexOf("Telegram");
        int telegramEnd = telegramStart + "Telegram".length();

        message.setSpan(new URLSpan("https://discord.gg/ekpUFWcCFN") {
            @Override
            public void onClick(View widget) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/ekpUFWcCFN")));
            }
        }, discordStart, discordEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        message.setSpan(new URLSpan("https://t.me/skyautowax") {
            @Override
            public void onClick(View widget) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/skyautowax")));
            }
        }, telegramStart, telegramEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Info & Community")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create();

        dialog.show();
        ((TextView) dialog.findViewById(android.R.id.message))
            .setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MOD && resultCode == Activity.RESULT_OK) {
            try {
                InputStream dataStream = getContentResolver().openInputStream(Objects.requireNonNull(data.getData()));
                loader.addModSafely(dataStream);
            } catch (FileNotFoundException e) {
                Toast.makeText(this, R.string.mod_ioe, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setLoadingStatus(boolean enable) {
        loadingBar.setVisibility(enable ? View.VISIBLE : View.GONE);
        addModButton.setEnabled(!enable);
        btnLaunchLive.setEnabled(!enable);
        btnLaunchChplay.setEnabled(!enable);
        btnLaunchHuawei.setEnabled(!enable);
        modListView.setClickable(!enable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loader != null) loader.removeListener();
        unbindService(mDialogManager);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void refreshModList(int mode, int which) {
        runOnUiThread(() -> {
            ModListAdapter adapter = (ModListAdapter) modListView.getAdapter();
            if (adapter != null) {
                switch (mode) {
                    case 0:
                        adapter.notifyItemRemoved(which);
                        break;
                    case 1:
                        adapter.notifyItemInserted(which);
                        break;
                    case 2:
                        adapter.notifyItemChanged(which);
                        break;
                    case 3:
                        adapter.notifyDataSetChanged();
                }
            } else modListView.setAdapter(new ModListAdapter(loader));
        });
    }

    @Override
    public void onLoadingUpdated() {
        runOnUiThread(this::handleLoading);
    }

    @Override
    public void signalModRemovalUnsafe() {
        runOnUiThread(this::handleUnsafeModRemoval);
    }

    @Override
    public void signalModAddException() {
        runOnUiThread(this::handleException);
    }

    @Override
    public void signalModRemovalError() {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.mod_remove_unable);
            builder.setMessage(R.string.mod_ioe);
            builder.setPositiveButton(android.R.string.ok, (d, w) -> {});
            builder.show();
        });
    }

    private void handleException() {
        Exception e = loader.getException();
        if (e == null) return;
        String message;
        if (e instanceof NoDependenciesException) {
            NoDependenciesException exc = (NoDependenciesException) e;
            StringBuilder stringBuilder = new StringBuilder();
            for (ElfModMetadata meta : exc.failedDependencies) {
                stringBuilder.append(getString(R.string.mod_add_missingdep, meta.name, meta.majorVersion, meta.minorVersion));
                stringBuilder.append('\n');
            }
            message = stringBuilder.toString();
        } else if (e instanceof InvalidModException) {
            message = getString(R.string.mod_add_wrongformat);
        } else if (e instanceof IOException) {
            message = e.getMessage();
        } else if (e instanceof ModExistsException) {
            message = getString(R.string.mod_add_exists);
        } else {
            e.printStackTrace();
            message = e.getMessage();
        }

        DialogY dialogY = DialogY.createFromActivity(this);
        dialogY.positiveButton.setVisibility(View.GONE);
        dialogY.title.setText(R.string.mod_add_unable);
        dialogY.content.setText(message);
        dialogY.negativeButton.setOnClickListener((view) -> dialogY.dialog.dismiss());
        dialogY.dialog.setCancelable(true);
        dialogY.dialog.show();
    }

    private void handleUnsafeModRemoval() {
        ElfUIBackbone.UnsafeRemovalMetadata metadata = loader.getUnsafeRemovalMetadata();
        if (metadata == null) return;
        StringBuilder sb = new StringBuilder();
        for (ElfModUIMetadata meta : metadata.dependingMods) {
            sb.append(getString(R.string.mod_remove_dep, ModListAdapter.getVisibleModName(meta)));
            sb.append('\n');
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.mod_remove_unable);
        builder.setMessage(sb.toString());
        builder.setPositiveButton(android.R.string.ok, (d, w) -> loader.resetModRemovalMetadata());
        builder.setOnCancelListener((d) -> loader.resetModRemovalMetadata());
        builder.show();
    }

    private void handleLoading() {
        if (loader.getProgressBarState()) {
            setLoadingStatus(true);
        } else {
            modListView.setAdapter(new ModListAdapter(loader));
            setLoadingStatus(false);
        }
    }

    public boolean findPackage(String packageName) {
        PackageManager packageManager = getPackageManager();
        try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SHARED_LIBRARY_FILES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public void launchGame() {
        if (findPackage(skyPackageName)) {
            setSkyPackageName(skyPackageName);
            startActivity(new Intent(this, MainActivity.class));
        } else {
            Toast.makeText(
                getApplicationContext(),
                getResources().getString(R.string.game_not_installed_warning),
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    public void onExtraSettingsDialog(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void runUpdater() {
        Intent updaterService = new Intent(this, CanvasUpdaterService.class);
        bindService(updaterService, new CanvasUpdaterConnection(this), BIND_AUTO_CREATE);
    }

    public void onClearAppData(View view) {
        clearAppDataSelective();
    }

    private void clearAppDataSelective() {
        new AlertDialog.Builder(this)
            .setTitle("Clear App Data")
            .setMessage("This will delete all Canvas data except:\n\n" +
                "✓ AccountAuthInfo.bin\n" +
                "✓ mods folder\n" +
                "✓ Accounts folder\n" +
                "✓ config/configs folders\n\n" +
                "⚠️ The app will restart after clearing.")
            .setPositiveButton("Clear", (dialog, which) -> {
                new Thread(() -> {
                    try {
                        String packageName = getPackageName();
                        File dataDir = new File("/data/data/" + packageName);
                        File filesDir = getFilesDir();
                        File externalDataDir = new File("/sdcard/Android/data/" + packageName);

                        Log.i("ClearData", "Starting selective clear for package: " + packageName);

                        clearDirectorySelective(filesDir, new String[]{"mods", "Accounts", "config"}, new String[]{"AccountAuthInfo.bin"});

                        File cacheDir = getCacheDir();
                        if (cacheDir != null && cacheDir.exists()) {
                            deleteRecursive(cacheDir);
                            Log.i("ClearData", "Cache cleared");
                        }

                        File codeCacheDir = getCodeCacheDir();
                        if (codeCacheDir != null && codeCacheDir.exists()) {
                            deleteRecursive(codeCacheDir);
                            Log.i("ClearData", "Code cache cleared");
                        }

                        File extractedLibs = new File(filesDir, "extracted_libs");
                        if (extractedLibs.exists()) {
                            deleteRecursive(extractedLibs);
                            Log.i("ClearData", "Extracted libs cleared");
                        }

                        File logsDir = new File(externalDataDir, "files/logs");
                        if (logsDir.exists()) {
                            deleteRecursive(logsDir);
                            Log.i("ClearData", "Logs cleared");
                        }

                        if (externalDataDir.exists()) {
                            clearDirectorySelective(externalDataDir, new String[]{"mods", "Accounts", "config", "configs"}, new String[]{});
                            Log.i("ClearData", "External data cleared (selective)");
                        }

                        File sharedPrefsDir = new File(dataDir, "shared_prefs");
                        if (sharedPrefsDir.exists()) {
                            File[] prefFiles = sharedPrefsDir.listFiles();
                            if (prefFiles != null) {
                                for (File prefFile : prefFiles) {
                                    if (!prefFile.getName().contains("package_configs")) {
                                        prefFile.delete();
                                        Log.i("ClearData", "Deleted pref: " + prefFile.getName());
                                    }
                                }
                            }
                        }

                        Log.i("ClearData", "Selective clear completed successfully");
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Data cleared successfully. Restarting...", Toast.LENGTH_SHORT).show();
                            new android.os.Handler().postDelayed(() -> {
                                Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(intent);
                                }
                                System.exit(0);
                            }, 500);
                        });
                    } catch (Exception e) {
                        Log.e("ClearData", "Error clearing data", e);
                        runOnUiThread(() ->
                            Toast.makeText(this, "Error clearing data: " + e.getMessage(), Toast.LENGTH_LONG).show()
                        );
                    }
                }).start();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void clearDirectorySelective(File dir, String[] preserveFolders, String[] preserveFiles) {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            boolean shouldPreserve = false;
            if (file.isDirectory()) {
                for (String folder : preserveFolders) {
                    if (file.getName().equals(folder)) {
                        shouldPreserve = true;
                        Log.i("ClearData", "Preserving folder: " + file.getName());
                        break;
                    }
                }
            }
            if (file.isFile()) {
                for (String fileName : preserveFiles) {
                    if (file.getName().equals(fileName)) {
                        shouldPreserve = true;
                        Log.i("ClearData", "Preserving file: " + file.getName());
                        break;
                    }
                }
            }
            if (!shouldPreserve) {
                if (file.isDirectory()) {
                    deleteRecursive(file);
                } else {
                    file.delete();
                }
                Log.i("ClearData", "Deleted: " + file.getName());
            }
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
}
