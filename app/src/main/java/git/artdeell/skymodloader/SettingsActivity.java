package git.artdeell.skymodloader;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "ClearAppData";
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
        Button btnClearAppData = findViewById(R.id.btn_clear_app_data);

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
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

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
                Toast.makeText(this, "Logcat monitoring enabled", Toast.LENGTH_SHORT).show();
            } else {
                stopService(logcatIntent);
                Toast.makeText(this, "Logcat monitoring disabled", Toast.LENGTH_SHORT).show();
            }
        });

        btnClearAppData.setOnClickListener(v -> clearAppDataComplete());
    }

    private void clearAppDataComplete() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚠️ Clear App Data");
        builder.setMessage("Delete all game data?\n\n✅ PRESERVED:\n• mods/\n• Accounts/\n• config/\n• AccountAuthInfo.bin\n\n❌ DELETED:\n• Everything else");
        builder.setPositiveButton("Clear", (dialog, which) -> {
            Toast.makeText(this, "Clearing data...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                int deletedFiles = 0;
                int deletedDirs = 0;
                try {
                    Log.i(TAG, "Starting Complete Clear App Data");

                    File externalFilesDir = getExternalFilesDir(null);
                    if (externalFilesDir != null) {
                        File externalDataRoot = externalFilesDir.getParentFile();
                        if (externalDataRoot != null && externalDataRoot.exists()) {
                            Log.i(TAG, "Clearing external: " + externalDataRoot.getAbsolutePath());
                            int[] counts = deleteRecursiveCount(externalDataRoot);
                            deletedFiles += counts[0];
                            deletedDirs += counts[1];
                            Log.i(TAG, "External cleared: " + counts[0] + " files, " + counts[1] + " dirs");
                        }
                    }

                    File internalDataRoot = getFilesDir().getParentFile();
                    if (internalDataRoot != null && internalDataRoot.exists()) {
                        Log.i(TAG, "Processing root: " + internalDataRoot.getAbsolutePath());
                        int[] rootCounts = clearInternalDataRoot(internalDataRoot);
                        deletedFiles += rootCounts[0];
                        deletedDirs += rootCounts[1];
                        Log.i(TAG, "Internal root cleared: " + rootCounts[0] + " files, " + rootCounts[1] + " dirs");
                    }

                    final int totalFiles = deletedFiles;
                    final int totalDirs = deletedDirs;
                    Log.i(TAG, "TOTAL DELETED: " + totalFiles + " files, " + totalDirs + " dirs");

                    runOnUiThread(() -> {
                        String message = "Cleared " + totalFiles + " files, " + totalDirs + " dirs\nRestarting...";
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private int[] clearInternalDataRoot(File dataRoot) {
        int fileCount = 0;
        int dirCount = 0;
        File[] contents = dataRoot.listFiles();
        if (contents == null) return new int[]{0, 0};

        for (File item : contents) {
            String name = item.getName();
            if (name.equals("files")) {
                Log.i(TAG, "Processing files/ directory selectively...");
                int[] filesCounts = clearFilesDirectory(item);
                fileCount += filesCounts[0];
                dirCount += filesCounts[1];
            } else {
                Log.i(TAG, "Deleting ROOT item: " + name);
                int[] counts = deleteRecursiveCount(item);
                fileCount += counts[0];
                dirCount += counts[1];
                Log.i(TAG, "❌ DELETED ROOT: " + name + " (" + counts[0] + " files, " + counts[1] + " dirs)");
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
                for (String preserved : preservedDirs) {
                    if (itemName.equals(preserved)) {
                        shouldPreserve = true;
                        Log.i(TAG, "✅ PRESERVED DIR: files/" + itemName + "/");
                        break;
                    }
                }
            } else if (item.isFile()) {
                for (String preserved : preservedFiles) {
                    if (itemName.equals(preserved)) {
                        shouldPreserve = true;
                        Log.i(TAG, "✅ PRESERVED FILE: files/" + itemName);
                        break;
                    }
                }
            }

            if (!shouldPreserve) {
                if (item.isFile()) {
                    if (item.delete()) {
                        fileCount++;
                        Log.i(TAG, "❌ DELETED FILE: files/" + itemName);
                    } else {
                        Log.w(TAG, "Failed to delete file: files/" + itemName);
                    }
                } else if (item.isDirectory()) {
                    int[] counts = deleteRecursiveCount(item);
                    fileCount += counts[0];
                    dirCount += counts[1];
                    Log.i(TAG, "❌ DELETED DIR: files/" + itemName + " (" + counts[0] + " files, " + counts[1] + " dirs)");
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
                    int[] childCounts = deleteRecursiveCount(child);
                    fileCount += childCounts[0];
                    dirCount += childCounts[1];
                }
            }
            if (fileOrDirectory.delete()) {
                dirCount++;
            } else {
                Log.w(TAG, "Failed to delete dir: " + fileOrDirectory.getAbsolutePath());
            }
        } else {
            if (fileOrDirectory.delete()) {
                fileCount++;
            } else {
                Log.w(TAG, "Failed to delete file: " + fileOrDirectory.getAbsolutePath());
            }
        }
        return new int[]{fileCount, dirCount};
    }
}
