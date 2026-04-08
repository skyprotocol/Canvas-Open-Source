package git.artdeell.skymodloader;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "ClearAppData";
    private static final int REQUEST_PICK_BOOTLOADER = 9001;
    private Switch hideCanvasMenuSwitch;
    private Switch ceserverSwitch;
    private Switch customServerSwitch;
    private Switch logcatSwitch;
    private EditText serverUrlInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setting_layout);

        ImageView backButton = findViewById(R.id.back_button);
        hideCanvasMenuSwitch = findViewById(R.id.mm_hideCanvasMenu);
        ceserverSwitch = findViewById(R.id.mm_enableCeserver);
        customServerSwitch = findViewById(R.id.mm_enableCustomServer);
        logcatSwitch = findViewById(R.id.mm_enableLogcat);
        serverUrlInput = findViewById(R.id.server_url_input);

        backButton.setOnClickListener(v -> finish());

        hideCanvasMenuSwitch.setChecked(getSharedPreferences("package_configs", MODE_PRIVATE)
            .getBoolean("hide_canvas_menu", false));
        hideCanvasMenuSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
            getSharedPreferences("package_configs", MODE_PRIVATE)
                .edit().putBoolean("hide_canvas_menu", isChecked).apply()
        );

        ceserverSwitch.setChecked(getSharedPreferences("package_configs", MODE_PRIVATE)
            .getBoolean("ceserver", false));
        ceserverSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
            getSharedPreferences("package_configs", MODE_PRIVATE)
                .edit().putBoolean("ceserver", isChecked).apply()
        );

        customServerSwitch.setChecked(getSharedPreferences("package_configs", MODE_PRIVATE)
            .getBoolean("custom_server", false));
        customServerSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
            getSharedPreferences("package_configs", MODE_PRIVATE)
                .edit().putBoolean("custom_server", isChecked).apply()
        );

        serverUrlInput.setText(getSharedPreferences("package_configs", MODE_PRIVATE)
            .getString("server_host", ""));
        serverUrlInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                getSharedPreferences("package_configs", MODE_PRIVATE)
                    .edit().putString("server_host", s.toString()).apply();
            }
        });

        logcatSwitch.setChecked(getSharedPreferences("package_configs", MODE_PRIVATE)
            .getBoolean("logcat_enabled", false));
        logcatSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences("package_configs", MODE_PRIVATE)
                .edit().putBoolean("logcat_enabled", isChecked).apply();
            Intent logcatIntent = new Intent(this, LogcatMonitorService.class);
            if (isChecked) {
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
        });

        findViewById(R.id.btn_clear_app_data).setOnClickListener(v -> showClearDataDialog());
        findViewById(R.id.btn_custom_bootloader).setOnClickListener(v -> showBootloaderDialog());
    }

    private void showBootloaderDialog() {
        File current = new File(getFilesDir(), "extracted_libs/libBootloader.so");
        boolean isCustomActive = getSharedPreferences("package_configs", MODE_PRIVATE)
            .getBoolean("use_custom_bootloader", false);
        String status = (isCustomActive && current.exists())
            ? "Custom enabled · " + current.length() / 1024 + " KB"
            : "Original version extracted from Sky's APK";

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_custom_bootloader);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.88),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );

        ((TextView) dialog.findViewById(R.id.dialog_bootloader_status)).setText(status);

        dialog.findViewById(R.id.dialog_bootloader_cancel)
            .setOnClickListener(v -> dialog.dismiss());

        dialog.findViewById(R.id.dialog_bootloader_reset)
            .setOnClickListener(v -> {
                dialog.dismiss();
                resetBootloaderToOriginal();
            });

        dialog.findViewById(R.id.dialog_bootloader_pick)
            .setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("application/octet-stream");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
                startActivityForResult(intent, REQUEST_PICK_BOOTLOADER);
            });

        dialog.show();
    }

    private void showClearDataDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_clear_data);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.88),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );

        dialog.findViewById(R.id.dialog_clear_cancel)
            .setOnClickListener(v -> dialog.dismiss());

        dialog.findViewById(R.id.dialog_clear_confirm)
            .setOnClickListener(v -> {
                dialog.dismiss();
                executeClearAppData();
            });

        dialog.show();
    }

    private void resetBootloaderToOriginal() {
        File extractedLib = new File(getFilesDir(), "extracted_libs/libBootloader.so");
        if (extractedLib.exists()) extractedLib.delete();
        getSharedPreferences("package_configs", MODE_PRIVATE).edit()
            .putBoolean("use_custom_bootloader", false)
            .putInt("bootloader_version", -1)
            .apply();
        Toast.makeText(this, R.string.toast_bootloader_reset, Toast.LENGTH_LONG).show();
    }

    private void copyBootloaderFromUri(Uri uri) {
        new Thread(() -> {
            try {
                File extractDir = new File(getFilesDir(), "extracted_libs");
                if (!extractDir.exists()) extractDir.mkdirs();
                File dest = new File(extractDir, "libBootloader.so");

                try (InputStream in = getContentResolver().openInputStream(uri);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                    if (in == null) throw new IOException("Impossible to load the selected file");
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
                }

                dest.setExecutable(true, false);
                dest.setReadable(true, false);

                getSharedPreferences("package_configs", MODE_PRIVATE)
                    .edit().putBoolean("use_custom_bootloader", true).apply();

                Log.i(TAG, "Custom libBootloader.so copied: " + dest.length() / 1024 + " KB");
                runOnUiThread(() -> Toast.makeText(this,
                    R.string.toast_bootloader_applied,
                    Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                Log.e(TAG, "Copie failed: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this,
                    "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_BOOTLOADER && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) copyBootloaderFromUri(uri);
        }
    }

    private void executeClearAppData() {
        Toast.makeText(this, R.string.toast_clearing_data, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int deletedFiles = 0;
            int deletedDirs = 0;
            try {
                Log.i(TAG, "Starting Complete Clear App Data");

                File externalFilesDir = getExternalFilesDir(null);
                if (externalFilesDir != null) {
                    File externalDataRoot = externalFilesDir.getParentFile();
                    if (externalDataRoot != null && externalDataRoot.exists()) {
                        int[] counts = deleteRecursiveCount(externalDataRoot);
                        deletedFiles += counts[0];
                        deletedDirs += counts[1];
                    }
                }

                File internalDataRoot = getFilesDir().getParentFile();
                if (internalDataRoot != null && internalDataRoot.exists()) {
                    int[] rootCounts = clearInternalDataRoot(internalDataRoot);
                    deletedFiles += rootCounts[0];
                    deletedDirs += rootCounts[1];
                }

                final int totalFiles = deletedFiles;
                final int totalDirs = deletedDirs;
                Log.i(TAG, "TOTAL DELETED: " + totalFiles + " files, " + totalDirs + " dirs");

                runOnUiThread(() -> {
                    Toast.makeText(this,
                        getString(R.string.toast_cleared_data, totalFiles, totalDirs),
                        Toast.LENGTH_LONG).show();
                    new android.os.Handler().postDelayed(() -> {
                        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        System.exit(0);
                    }, 1500);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error clearing data", e);
                runOnUiThread(() ->
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private int[] clearInternalDataRoot(File dataRoot) {
        int fileCount = 0;
        int dirCount = 0;
        File[] contents = dataRoot.listFiles();
        if (contents == null) return new int[]{0, 0};
        for (File item : contents) {
            String name = item.getName();
            if (name.equals("files")) {
                int[] counts = clearFilesDirectory(item);
                fileCount += counts[0];
                dirCount += counts[1];
            } else if (name.equals("shared_prefs")) {
                int[] counts = clearSharedPrefsDirectory(item);
                fileCount += counts[0];
                dirCount += counts[1];
            } else {
                int[] counts = deleteRecursiveCount(item);
                fileCount += counts[0];
                dirCount += counts[1];
            }
        }
        return new int[]{fileCount, dirCount};
    }

    private int[] clearSharedPrefsDirectory(File sharedPrefsDir) {
        int fileCount = 0;
        int dirCount = 0;
        if (!sharedPrefsDir.exists() || !sharedPrefsDir.isDirectory()) return new int[]{0, 0};

        File[] contents = sharedPrefsDir.listFiles();
        if (contents == null) return new int[]{0, 0};

        for (File item : contents) {
            if (item.getName().equals("user.xml")) {
                Log.i(TAG, "✅ PRESERVED PREF: user.xml");
                continue;
            }
            String prefName = item.getName().replace(".xml", "");
            getSharedPreferences(prefName, MODE_PRIVATE).edit().clear().commit();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (deleteSharedPreferences(prefName)) {
                    fileCount++;
                    Log.i(TAG, "❌ DELETED PREF: " + item.getName());
                }
            } else {
                if (item.delete()) {
                    fileCount++;
                    Log.i(TAG, "❌ DELETED PREF (legacy): " + item.getName());
                }
            }
        }
        return new int[]{fileCount, dirCount};
    }

    private int[] clearFilesDirectory(File filesDir) {
        int fileCount = 0;
        int dirCount = 0;
        if (!filesDir.exists() || !filesDir.isDirectory()) return new int[]{0, 0};

        String[] preservedDirs = {"mods", "Accounts", "config"};
        String[] preservedFiles = {"AccountAuthInfo.bin"};
        File[] contents = filesDir.listFiles();
        if (contents == null) return new int[]{0, 0};

        for (File item : contents) {
            boolean shouldPreserve = false;
            String itemName = item.getName();
            if (item.isDirectory()) {
                for (String p : preservedDirs) {
                    if (itemName.equals(p)) { shouldPreserve = true; break; }
                }
            } else if (item.isFile()) {
                for (String p : preservedFiles) {
                    if (itemName.equals(p)) { shouldPreserve = true; break; }
                }
            }
            if (!shouldPreserve) {
                if (item.isFile()) {
                    if (item.delete()) fileCount++;
                } else if (item.isDirectory()) {
                    int[] counts = deleteRecursiveCount(item);
                    fileCount += counts[0];
                    dirCount += counts[1];
                }
            }
        }
        return new int[]{fileCount, dirCount};
    }

    private int[] deleteRecursiveCount(File fileOrDirectory) {
        int fileCount = 0;
        int dirCount = 0;
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    int[] counts = deleteRecursiveCount(child);
                    fileCount += counts[0];
                    dirCount += counts[1];
                }
            }
            if (fileOrDirectory.delete()) dirCount++;
        } else {
            if (fileOrDirectory.delete()) fileCount++;
        }
        return new int[]{fileCount, dirCount};
    }
}
