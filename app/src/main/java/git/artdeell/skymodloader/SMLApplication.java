package git.artdeell.skymodloader;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.res.Resources;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import git.artdeell.skymodloader.net.StarwatchBlocker;

public class SMLApplication extends Application {
    public static final String MOD_UPDATER_SERVICE_NOTIFICATION_CHANNEL = "canvas_mod_updater_service_notif_ch";
    private static SMLApplication smlApplication;
    public static String skyPName;
    public static Resources skyRes;
    public static Resources smlRes;

    public static SMLApplication deez() {
        return smlApplication;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        StarwatchBlocker.init(this);
        StarwatchBlocker.install();
        Log.i("Canvas", "SMLApplication.onCreate() called");
        smlApplication = this;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notifManager = ((NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE));
            NotificationChannel notifChannel = notifManager.getNotificationChannel(MOD_UPDATER_SERVICE_NOTIFICATION_CHANNEL);
            if (notifChannel == null) {
                notifChannel = new NotificationChannel(
                    MOD_UPDATER_SERVICE_NOTIFICATION_CHANNEL,
                    "Mod Updater Status",
                    NotificationManager.IMPORTANCE_MIN
                );
                notifChannel.setDescription("Notification showing the state of mod update");
                notifManager.createNotificationChannel(notifChannel);
            }
        }

    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        smlApplication = null;
    }
}
