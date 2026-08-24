package git.artdeell.skymodloader.server;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import git.artdeell.skymodloader.AccountStorage;
import git.artdeell.skymodloader.R;

public class ServerManager {
    public static final String PREFS_NAME = "package_configs";
    public static final String KEY_CUSTOM_SERVER = "custom_server";
    public static final String KEY_SERVER_HOST = "server_host";
    public static final String FREESKY_HOST = "live.freesky.one";

    public static final List<ApprovedServer> APPROVED_SERVERS = Collections.unmodifiableList(Arrays.asList(
        new ApprovedServer(
            "radiance",
            "Radiance Official Private Server",
            "sky.thatskyradiance.duckdns.org",
            R.color.teal_700,
            "sky.thatskyradiance.duckdns.org",
            R.drawable.server_icon_radiance
        ),
        new ApprovedServer(
            "freesky",
            "FreeSky Private Server",
            FREESKY_HOST,
            R.color.teal_700,
            FREESKY_HOST,
            R.drawable.server_icon_freesky
        )
    ));

    public static String getDefaultHost() {
        if (!APPROVED_SERVERS.isEmpty()) {
            return APPROVED_SERVERS.get(0).host;
        }
        return "sky.thatskyradiance.duckdns.org";
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String sanitizeHost(String host) {
        if (host == null) return "";
        return host.trim().replaceFirst("^https?://", "").replaceAll("/.*$", "");
    }

    public static boolean isCustomServerEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_CUSTOM_SERVER, false);
    }

    public static String getCurrentHost(Context context) {
        return sanitizeHost(getPrefs(context).getString(KEY_SERVER_HOST, getDefaultHost()));
    }

    public static ApprovedServer getActiveApprovedServer(Context context) {
        if (!isCustomServerEnabled(context)) return null;
        return getConfiguredApprovedServer(context);
    }

    public static ApprovedServer getConfiguredApprovedServer(Context context) {
        String currentHost = sanitizeHost(
            getPrefs(context).getString(KEY_SERVER_HOST, ""));
        if (currentHost.isEmpty()) return null;
        for (ApprovedServer server : APPROVED_SERVERS) {
            if (server.host.equalsIgnoreCase(currentHost)) {
                return server;
            }
        }
        return null;
    }

    public static boolean isFreeSkyActive(Context context) {
        return isCustomServerEnabled(context)
            && FREESKY_HOST.equalsIgnoreCase(getCurrentHost(context));
    }

    public static int getActiveBootLogoRes(Context context) {
        ApprovedServer activeServer = getActiveApprovedServer(context);
        if (activeServer != null && activeServer.hasCustomIcon()) {
            return activeServer.iconRes;
        }
        return R.drawable.banner2;
    }

    public static void activateServer(Context context, ApprovedServer server) {
        getPrefs(context).edit()
            .putBoolean(KEY_CUSTOM_SERVER, true)
            .putString(KEY_SERVER_HOST, server.host)
            .apply();
        AccountStorage.sync(context);
    }

    public static void activateLiveServer(Context context) {
        getPrefs(context).edit()
            .putBoolean(KEY_CUSTOM_SERVER, false)
            .apply();
        AccountStorage.sync(context);
    }

}
