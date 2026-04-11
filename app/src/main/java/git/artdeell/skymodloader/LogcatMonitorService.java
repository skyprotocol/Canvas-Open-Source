package git.artdeell.skymodloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogcatMonitorService extends Service {
    private static final String TAG = "LogcatMonitor";
    private static final String CHANNEL_ID = "logcat_monitor_channel";
    private static final int NOTIFICATION_ID = 1001;

    private Process logcatProcess;
    private Thread monitorThread;
    private volatile boolean isRunning = false;
    private FileWriter logWriter;
    private File logFile;
    private int lineCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification("Starting..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, createNotification("Starting..."));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            isRunning = true;
            startMonitoring();
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Logcat Monitor",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitoring logcat in background");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String text) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Logcat Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true);

        return builder.build();
    }

    private void startMonitoring() {
        Log.d(TAG, "Full monitoring started");

        monitorThread = new Thread(() -> {
            try {
                // create directory logs
                File logsDir = new File(getExternalFilesDir(null), "logs");
                if (!logsDir.exists()) {
                    logsDir.mkdirs();
                }

                // create log file with timestamp
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                    .format(new Date());
                logFile = new File(logsDir, "canvas_full_logcat_" + timestamp + ".txt");
                logWriter = new FileWriter(logFile, true);

                Log.d(TAG, "Log file: " + logFile.getAbsolutePath());

                // starts logcat
                ProcessBuilder processBuilder = new ProcessBuilder(
                    "logcat",
                    "--pid=" + android.os.Process.myPid(),
                    "-v", "threadtime"
                );
                logcatProcess = processBuilder.start();

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(logcatProcess.getInputStream())
                );

                String line;
                while (isRunning && (line = reader.readLine()) != null) {
                    logWriter.write(line + "\n");
                    lineCount++;

                    // update every 50 lines
                    if (lineCount % 50 == 0) {
                        updateNotification("Captured " + lineCount + " lines");
                    }
                }

                logWriter.flush();
                logWriter.close();
                Log.d(TAG, "Captured " + lineCount + " lines");

            } catch (IOException e) {
                Log.e(TAG, "Error monitoring logcat", e);
            }
        });

        monitorThread.start();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(text));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

        isRunning = false;

        if (logcatProcess != null) {
            logcatProcess.destroy();
        }

        if (monitorThread != null) {
            try {
                monitorThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping monitor thread", e);
            }
        }

        if (logWriter != null) {
            try {
                logWriter.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing log file", e);
            }
        }

        Log.d(TAG, "Captured " + lineCount + " lines");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}