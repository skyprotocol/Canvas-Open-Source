package git.artdeell.skymodloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.tgc.sky.GameActivity;

import git.artdeell.skymodloader.server.ServerManager;
import me.fengwu.skyauth.SkyAuth;

/** Coordinates the optional APK-bundled SKYAuth session with the running game. */
public final class SkyAuthIntegration implements SkyAuth.GameHookListener {
    private static final String TAG = "SkyAuthIntegration";
    private static final long SESSION_PREPARATION_DELAY_MILLIS = 3_000L;
    private static final long MINIMUM_LOADING_DURATION_MILLIS = 700L;
    private static final long MAXIMUM_LOADING_DURATION_MILLIS = 6_000L;
    private static final long WELCOME_DURATION_MILLIS = 2_400L;

    private final GameActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable prepareSession = this::beginSessionPreparation;
    private final SharedPreferences.OnSharedPreferenceChangeListener serverPreferenceListener;

    private SharedPreferences serverPreferences;
    private int preparationGeneration;
    private long loadingStartedAtMillis;
    private View loadingIndicator;
    private View welcomeBanner;
    private boolean sessionPreparationInProgress;
    private boolean gameHooksInstalled;
    private boolean authenticationActive;
    private boolean destroyed;
    private AlertDialog authenticationRetryDialog;

    public SkyAuthIntegration(GameActivity activity) {
        this.activity = activity;
        serverPreferenceListener = (preferences, key) -> {
            if ((ServerManager.KEY_CUSTOM_SERVER.equals(key)
                || ServerManager.KEY_SERVER_HOST.equals(key))
                && !isEnabled(activity)) {
                handler.post(() -> {
                    if (!isEnabled(activity)) deactivate();
                });
            }
        };
    }

    public static boolean isEnabled(Context context) {
        return ServerManager.isFreeSkyActive(context);
    }

    public void onCreate() {
        serverPreferences = ServerManager.getPrefs(activity);
        serverPreferences.registerOnSharedPreferenceChangeListener(
            serverPreferenceListener);
        reconcileAuthentication();
    }

    public void onResume() {
        if (!destroyed) reconcileAuthentication();
    }

    public void onDestroy() {
        destroyed = true;
        if (serverPreferences != null) {
            serverPreferences.unregisterOnSharedPreferenceChangeListener(
                serverPreferenceListener);
            serverPreferences = null;
        }
        deactivate();
    }

    public void refreshAuthorization() {
        if (!destroyed && authenticationActive && isEnabled(activity)) {
            handler.post(this::beginSessionPreparation);
        }
    }

    @Override
    public void onAccountCenterRequested(Activity ignored) {
        if (!destroyed && authenticationActive && isEnabled(activity)) {
            SkyAuth.showAccountCenter(activity);
        }
    }

    @Override
    public void onAccountSigningOut(Activity ignored) {
        refreshAuthorization();
    }

    private void reconcileAuthentication() {
        if (isEnabled(activity)) activate();
        else deactivate();
    }

    private void activate() {
        if (destroyed || !isEnabled(activity)) return;
        if (!authenticationActive) {
            authenticationActive = true;
            installGameHooks();
            handler.postDelayed(prepareSession, SESSION_PREPARATION_DELAY_MILLIS);
        } else if (!gameHooksInstalled) {
            installGameHooks();
        }
    }

    private void deactivate() {
        authenticationActive = false;
        preparationGeneration++;
        sessionPreparationInProgress = false;
        handler.removeCallbacksAndMessages(null);
        removeOverlay(loadingIndicator);
        removeOverlay(welcomeBanner);
        dismissAuthenticationRetryDialog();
        loadingIndicator = null;
        welcomeBanner = null;

        boolean hooksRemoved = SkyAuth.uninstallGameHooks();
        if (gameHooksInstalled && !hooksRemoved) {
            Log.w(TAG, "SKYAuth game hooks could not be fully removed");
        }
        gameHooksInstalled = false;
    }

    private void installGameHooks() {
        gameHooksInstalled = SkyAuth.installGameHooks(activity, this);
        if (!gameHooksInstalled) {
            Log.w(TAG, "SKYAuth game hooks are not available; will retry on resume");
        }
    }

    private void beginSessionPreparation() {
        if (destroyed || !authenticationActive || !isEnabled(activity)
            || activity.isFinishing() || activity.isDestroyed()
            || sessionPreparationInProgress) {
            return;
        }
        dismissAuthenticationRetryDialog();
        sessionPreparationInProgress = true;
        int generation = ++preparationGeneration;
        loadingStartedAtMillis = SystemClock.elapsedRealtime();
        showLoadingIndicator();
        handler.postDelayed(
            () -> expirePreparation(generation), MAXIMUM_LOADING_DURATION_MILLIS);
        Log.i(TAG, "Preparing SKYAuth session without presenting authentication UI");
        SkyAuth.prepareSession(activity, session -> finishPreparation(generation, session));
    }

    private void finishPreparation(int generation, SkyAuth.PreparedSession session) {
        if (generation != preparationGeneration || destroyed
            || !authenticationActive || !isEnabled(activity)
            || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        long elapsed = SystemClock.elapsedRealtime() - loadingStartedAtMillis;
        long remaining = Math.max(0L, MINIMUM_LOADING_DURATION_MILLIS - elapsed);
        handler.postDelayed(() -> {
            if (generation != preparationGeneration || destroyed
                || !authenticationActive || !isEnabled(activity)
                || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            preparationGeneration++;
            sessionPreparationInProgress = false;
            hideLoadingIndicator();
            Log.i(TAG, "SKYAuth session preparation state=" + session.getState());
            if (session.isReady()) {
                showWelcomeBanner(session.getNickname());
            } else if (session.isNetworkError()) {
                showAuthenticationRetryDialog();
            } else if (session.isBanned() || session.requiresAuthentication()) {
                showAuthentication();
            }
        }, remaining);
    }

    private void expirePreparation(int generation) {
        if (generation != preparationGeneration || destroyed
            || !authenticationActive || !isEnabled(activity)) return;
        preparationGeneration++;
        sessionPreparationInProgress = false;
        Log.w(TAG, "SKYAuth session preparation exceeded the startup display window");
        hideLoadingIndicator();
        showAuthenticationRetryDialog();
    }

    private void showAuthentication() {
        if (destroyed || !authenticationActive || !isEnabled(activity)
            || activity.isFinishing() || activity.isDestroyed()) return;
        SkyAuth.authenticate(
            activity, () -> showWelcomeBanner(SkyAuth.activeNickname(activity)));
    }

    private void showAuthenticationRetryDialog() {
        if (destroyed || !authenticationActive || !isEnabled(activity)
            || activity.isFinishing() || activity.isDestroyed()) return;
        if (authenticationRetryDialog != null && authenticationRetryDialog.isShowing()) return;
        authenticationRetryDialog = new AlertDialog.Builder(activity)
            .setTitle(R.string.sky_auth_network_title)
            .setMessage(R.string.sky_auth_network_message)
            .setPositiveButton(R.string.sky_auth_retry,
                (dialog, which) -> beginSessionPreparation())
            .setOnDismissListener(dialog -> authenticationRetryDialog = null)
            .create();
        authenticationRetryDialog.setCancelable(false);
        authenticationRetryDialog.setCanceledOnTouchOutside(false);
        authenticationRetryDialog.show();
    }

    private void dismissAuthenticationRetryDialog() {
        AlertDialog dialog = authenticationRetryDialog;
        authenticationRetryDialog = null;
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private void showLoadingIndicator() {
        RelativeLayout parent = activity.findViewById(R.id.sml_relLayout);
        if (parent == null || loadingIndicator != null) return;

        FrameLayout container = new FrameLayout(activity);
        container.setBackground(rounded(0xb8000000, 16));
        container.setContentDescription(activity.getString(
            R.string.sky_auth_session_preparing_accessibility));
        container.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        ProgressBar progress = new ProgressBar(activity);
        progress.getIndeterminateDrawable().setTint(Color.WHITE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        progressParams.gravity = Gravity.CENTER;
        container.addView(progress, progressParams);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(dp(96), dp(96));
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        parent.addView(container, params);
        loadingIndicator = container;
    }

    private void hideLoadingIndicator() {
        View indicator = loadingIndicator;
        loadingIndicator = null;
        if (indicator == null) return;
        indicator.animate().alpha(0f).setDuration(160L)
            .withEndAction(() -> removeOverlay(indicator)).start();
    }

    private void showWelcomeBanner(String nickname) {
        RelativeLayout parent = activity.findViewById(R.id.sml_relLayout);
        if (parent == null || destroyed || !authenticationActive
            || !isEnabled(activity)) return;
        removeOverlay(welcomeBanner);

        String message = nickname == null || nickname.isEmpty()
            ? activity.getString(R.string.sky_auth_session_welcome_generic)
            : activity.getString(R.string.sky_auth_session_welcome, nickname);
        LinearLayout banner = new LinearLayout(activity);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(28), dp(14), dp(28), dp(14));
        banner.setBackground(rounded(0xf2f6f6f6, 4));
        banner.setElevation(dp(6));

        View accountMark = new View(activity);
        accountMark.setBackground(rounded(0xff45c9c8, 20));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        markParams.setMarginEnd(dp(12));
        banner.addView(accountMark, markParams);

        TextView text = new TextView(activity);
        text.setText(message);
        text.setTextColor(0xff363636);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        text.setGravity(Gravity.CENTER_VERTICAL);
        banner.addView(text, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.topMargin = dp(18);
        parent.addView(banner, params);
        welcomeBanner = banner;
        banner.setAlpha(0f);
        banner.setTranslationY(-dp(8));
        banner.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        handler.postDelayed(() -> hideWelcomeBanner(banner), WELCOME_DURATION_MILLIS);
    }

    private void hideWelcomeBanner(View banner) {
        if (welcomeBanner != banner) return;
        welcomeBanner = null;
        banner.animate().alpha(0f).translationY(-dp(6)).setDuration(180L)
            .withEndAction(() -> removeOverlay(banner)).start();
    }

    private static void removeOverlay(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
