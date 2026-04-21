package git.artdeell.skymodloader.elfmod;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;

import git.artdeell.skymodloader.AboutDialogHelper;
import git.artdeell.skymodloader.BuildConfig;
import git.artdeell.skymodloader.CommunityTabBuilder;
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

    // Tab navigation
    private ConstraintLayout modsTabContent;
    private ScrollView communityTabContent;
    private LinearLayout communityContainer;
    private TextView navModsText;
    private TextView navCommunityText;
    private boolean isModsTabActive = true;
    private boolean communityLoaded = false;

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
        initializeTabNavigation();
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
            Toast.makeText(this, R.string.toast_logcat_enabled, Toast.LENGTH_SHORT).show();
        } else {
            stopService(logcatIntent);
            Toast.makeText(this, R.string.toast_logcat_disabled, Toast.LENGTH_SHORT).show();
        }
    }

    public void onAddMod(View v) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_MOD);
    }

    public void onModInfo(View v) {
        AboutDialogHelper.show(this);
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
                    case 0: adapter.notifyItemRemoved(which); break;
                    case 1: adapter.notifyItemInserted(which); break;
                    case 2: adapter.notifyItemChanged(which); break;
                    case 3: adapter.notifyDataSetChanged();
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
        runOnUiThread(() ->
            new AlertDialog.Builder(this)
                .setTitle(R.string.mod_remove_unable)
                .setMessage(R.string.mod_ioe)
                .setPositiveButton(android.R.string.ok, (d, w) -> {})
                .show()
        );
    }

    private void handleException() {
        Exception e = loader.getException();
        if (e == null) return;
        String message;
        if (e instanceof NoDependenciesException) {
            NoDependenciesException exc = (NoDependenciesException) e;
            StringBuilder sb = new StringBuilder();
            for (ElfModMetadata meta : exc.failedDependencies) {
                sb.append(getString(R.string.mod_add_missingdep, meta.name, meta.majorVersion, meta.minorVersion));
                sb.append('\n');
            }
            message = sb.toString();
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
        dialogY.negativeButton.setOnClickListener(view -> dialogY.dialog.dismiss());
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
        new AlertDialog.Builder(this)
            .setTitle(R.string.mod_remove_unable)
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, (d, w) -> loader.resetModRemovalMetadata())
            .setOnCancelListener(d -> loader.resetModRemovalMetadata())
            .show();
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
        try {
            getPackageManager().getPackageInfo(packageName, PackageManager.GET_SHARED_LIBRARY_FILES);
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
        startActivity(new Intent(this, SettingsActivity.class));
    }

    public void runUpdater() {
        bindService(
            new Intent(this, CanvasUpdaterService.class),
            new CanvasUpdaterConnection(this),
            BIND_AUTO_CREATE
        );
    }

    private void initializeTabNavigation() {
        modsTabContent = findViewById(R.id.mods_tab_content);
        communityTabContent = findViewById(R.id.community_tab_content);
        communityContainer = findViewById(R.id.community_container);
        navModsText = findViewById(R.id.nav_tab_mods_text);
        navCommunityText = findViewById(R.id.nav_tab_community_text);

        findViewById(R.id.nav_tab_mods).setOnClickListener(v -> switchTab(true));
        findViewById(R.id.nav_tab_community).setOnClickListener(v -> switchTab(false));
    }

    private void switchTab(boolean showMods) {
        if (showMods == isModsTabActive) return;
        isModsTabActive = showMods;

        modsTabContent.setVisibility(showMods ? View.VISIBLE : View.GONE);
        communityTabContent.setVisibility(showMods ? View.GONE : View.VISIBLE);

        // Active tab: bold + full opacity; inactive: normal + dimmed
        navModsText.setTypeface(null, showMods ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        navModsText.setAlpha(showMods ? 1.0f : 0.45f);
        navCommunityText.setTypeface(null, showMods ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        navCommunityText.setAlpha(showMods ? 0.45f : 1.0f);

        if (!showMods && !communityLoaded) {
            communityLoaded = true;
            CommunityTabBuilder.build(this, communityContainer);
        }
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
                        File filesDir = getFilesDir();
                        File externalDataDir = new File("/sdcard/Android/data/" + getPackageName());

                        clearDirectorySelective(filesDir,
                            new String[]{"mods", "Accounts", "config"},
                            new String[]{"AccountAuthInfo.bin"});

                        File cacheDir = getCacheDir();
                        if (cacheDir != null && cacheDir.exists()) deleteRecursive(cacheDir);

                        File codeCacheDir = getCodeCacheDir();
                        if (codeCacheDir != null && codeCacheDir.exists()) deleteRecursive(codeCacheDir);

                        if (externalDataDir.exists()) {
                            clearDirectorySelective(externalDataDir,
                                new String[]{"mods", "Accounts", "config", "configs"},
                                new String[]{});
                        }

                        runOnUiThread(() -> {
                            Toast.makeText(this, R.string.toast_data_cleared_restarting, Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
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
                    if (file.getName().equals(folder)) { shouldPreserve = true; break; }
                }
            } else if (file.isFile()) {
                for (String fileName : preserveFiles) {
                    if (file.getName().equals(fileName)) { shouldPreserve = true; break; }
                }
            }
            if (!shouldPreserve) {
                if (file.isDirectory()) deleteRecursive(file);
                else file.delete();
            }
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }
}
