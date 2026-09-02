package git.artdeell.skymodloader.auth;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.tgc.sky.BuildConfig;
import com.tgc.sky.GameActivity;
import com.tgc.sky.SystemIO_android;
import com.tgc.sky.accounts.SystemAccountClientInfo;
import com.tgc.sky.accounts.SystemAccountClientRequestState;
import com.tgc.sky.accounts.SystemAccountClientState;
import com.tgc.sky.accounts.SystemAccountInterface;
import com.tgc.sky.accounts.SystemAccountServerInfo;
import com.tgc.sky.accounts.SystemAccountServerState;
import com.tgc.sky.accounts.SystemAccountType;

import git.artdeell.skymodloader.net.StarwatchBlocker;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class WebLogin extends WebViewClient implements SystemAccountInterface {
    private static final String TAG = "WebLogin";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";
    private final SystemAccountType accountType;
    private final String webLoginType;
    private final String explicitToken;
    private Dialog dialog;
    private WebView webView;
    private SystemAccountClientInfo m_accountClientInfo;
    private SystemAccountServerInfo m_accountServerInfo;
    private GameActivity m_activity;
    private SystemAccountInterface.UpdateClientInfoCallback m_callback;
    private boolean m_signedInSuccessfully = false;
    private volatile boolean m_resultPending = false;

    public WebLogin(String webLoginType, String token, SystemAccountType systemAccountType) {
        this.webLoginType = webLoginType;
        this.explicitToken = token;
        this.accountType = systemAccountType;
    }

    public WebLogin(String webLoginType, SystemAccountType systemAccountType) {
        this(webLoginType, null, systemAccountType);
    }

    private String getLoginUrl() {
        String host = BuildConfig.SKY_SERVER_HOSTNAME;
        if (host != null) {
            host = host.trim().replaceFirst("^https?://", "").replaceAll("/.*$", "");
        } else {
            host = "live.radiance.thatgamecompany.com";
        }
        String token = "";
        try {
            String raw = explicitToken;
            if (raw == null || raw.isEmpty()) {
                SystemIO_android sysIO = SystemIO_android.getInstance();
                if (sysIO != null) raw = sysIO.GetPushNotificationToken();
            }
            if (raw != null && !raw.isEmpty()) {
                token = URLEncoder.encode(raw, StandardCharsets.UTF_8.name());
            }
        } catch (UnsupportedEncodingException ignored) {}
        return String.format("https://%s/account/auth/oauth_signin?type=%s&token=%s", host, webLoginType, token);
    }

    private String getRedirectUrl() {
        String host = BuildConfig.SKY_SERVER_HOSTNAME;
        if (host != null) {
            host = host.trim().replaceFirst("^https?://", "").replaceAll("/.*$", "");
        } else {
            host = "live.radiance.thatgamecompany.com";
        }
        return String.format("https://%s/account/auth/oauth_redirect", host);
    }

    @Override
    public SystemAccountClientInfo GetClientInfo() {
        return m_accountClientInfo;
    }

    @Override
    public SystemAccountServerInfo GetServerInfo() {
        return m_accountServerInfo;
    }

    public void Initialize(GameActivity gameActivity, SystemAccountInterface.UpdateClientInfoCallback updateClientInfoCallback) {
        this.m_activity = gameActivity;
        this.m_callback = updateClientInfoCallback;
        SystemAccountClientInfo systemAccountClientInfo = new SystemAccountClientInfo();
        this.m_accountClientInfo = systemAccountClientInfo;
        systemAccountClientInfo.accountType = accountType;
        if ("beta.radiance.thatgamecompany.com".equals(BuildConfig.SKY_SERVER_HOSTNAME) && systemAccountClientInfo.accountType == SystemAccountType.kSystemAccountType_Google) {
            this.m_accountClientInfo.state = SystemAccountClientState.kSystemAccountClientState_NotAvailable;
        } else {
            this.m_accountClientInfo.state = SystemAccountClientState.kSystemAccountClientState_SignedOut;
        }
        this.m_accountClientInfo.requestState = SystemAccountClientRequestState.kSystemAccountClientRequestState_Idle;
        SystemAccountServerInfo systemAccountServerInfo = new SystemAccountServerInfo();
        this.m_accountServerInfo = systemAccountServerInfo;
        systemAccountServerInfo.type = accountType;
        systemAccountServerInfo.state = SystemAccountServerState.kSystemAccountServerState_Initializing;
        this.m_callback.UpdateClientInfo(this.m_accountClientInfo);
    }

    public void SignIn() {
        m_activity.runOnUiThread(() -> {
            m_signedInSuccessfully = false;
            m_resultPending = false;
            m_accountClientInfo.state = SystemAccountClientState.kSystemAccountClientState_SigningIn;
            m_callback.UpdateClientInfo(m_accountClientInfo);
            startSignIn();
        });
    }

    public void SignOut() {
        m_activity.runOnUiThread(() -> {
            m_accountClientInfo.state = SystemAccountClientState.kSystemAccountClientState_SigningOut;
            CookieManager.getInstance().removeAllCookies((bool) -> {
                m_accountClientInfo.state = SystemAccountClientState.kSystemAccountClientState_SignedOut;
                m_callback.UpdateClientInfo(m_accountClientInfo);
            });
        });
    }

    public void RefreshCredentials(SystemAccountClientRequestState systemAccountClientRequestState) {
        SignIn();
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void startSignIn() {
        dialog = new Dialog(m_activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(dialog1 -> {
            restoreGameImmersiveFocus();
            if (!m_signedInSuccessfully && !m_resultPending) {
                submitSignOutState();
            }
        });

        webView = new WebView(m_activity) {
            @Override
            public boolean onCheckIsTextEditor() {
                return true;
            }
        };
        dialog.setContentView(webView);
        webView.setWebViewClient(this);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView newWebView = new WebView(m_activity);
                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        return checkAndHandleRedirect(url);
                    }
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        return checkAndHandleRedirect(req.getUrl().toString());
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                return true;
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setUserAgentString(USER_AGENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        String url = getLoginUrl();
        Log.i(TAG, "Starting sign in for " + webLoginType + " URL: " + url);
        webView.loadUrl(url);
        dialog.show();
        showWebView();
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest webResourceRequest) {
        return checkAndHandleRedirect(webResourceRequest.getUrl().toString());
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView webView, String url) {
        return checkAndHandleRedirect(url);
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        Log.w(TAG, "SSL Error during auth flow: " + error);
        handler.proceed();
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        Log.e(TAG, "WebView error " + errorCode + ": " + description + " for " + failingUrl);
    }

    private boolean checkAndHandleRedirect(String url) {
        if (url == null || url.isEmpty()) return false;
        Log.i(TAG, "checkAndHandleRedirect URL: " + url);

        String redirectUrl = getRedirectUrl();
        String urlWithoutQuery = url.split("\\?")[0];

        // Check if the current URL itself (not query params) is the oauth_redirect endpoint
        if (urlWithoutQuery.equalsIgnoreCase(redirectUrl) || url.startsWith(redirectUrl + "?") || url.equals(redirectUrl)) {
            Log.i(TAG, "Real OAuth redirect reached: " + url);
            m_resultPending = true;
            m_activity.runOnUiThread(() -> {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
            });
            new Thread(() -> processLoading(url)).start();
            return true;
        }

        // Handle custom URL schemes (intent://, hwid://, market://, hms://, etc.)
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("about:") && !url.startsWith("javascript:")) {
            Log.i(TAG, "Handling custom URI scheme: " + url);
            try {
                Intent intent;
                if (url.startsWith("intent://")) {
                    intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                } else {
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (m_activity != null && intent.resolveActivity(m_activity.getPackageManager()) != null) {
                        m_activity.startActivity(intent);
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to resolve intent for: " + url, e);
            }
            Log.w(TAG, "No handler for scheme, not consuming: " + url);
            return false;
        }

        return false;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        WebResourceResponse blocked = StarwatchBlocker.interceptWebViewRequest(request);
        if (blocked != null) return blocked;
        return super.shouldInterceptRequest(view, request);
    }

    private void processLoading(final String url) {
        Log.i(TAG, "processLoading starting for: " + url);
        CookieManager.getInstance().flush();
        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(url).openConnection();
            httpURLConnection.setInstanceFollowRedirects(true);
            httpURLConnection.setRequestProperty("User-Agent", USER_AGENT);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) {
                httpURLConnection.setRequestProperty("Cookie", cookies);
            }
            InputStream inputStream;
            int responseCode = httpURLConnection.getResponseCode();
            Log.i(TAG, "OAuth redirect HTTP response code: " + responseCode);
            if (responseCode >= 200 && responseCode < 300) {
                inputStream = httpURLConnection.getInputStream();
            } else {
                inputStream = httpURLConnection.getErrorStream();
            }
            if (inputStream == null) {
                throw new IOException("HTTP response code: " + responseCode);
            }
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[1024];
            int read;
            while ((read = inputStream.read(bArr)) != -1) {
                byteArrayOutputStream.write(bArr, 0, read);
            }
            byteArrayOutputStream.flush();
            inputStream.close();
            httpURLConnection.disconnect();
            
            String responseBody = new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
            Log.i(TAG, "OAuth redirect response body: " + responseBody);
            JSONObject obj = new JSONObject(responseBody);
            String id = obj.optString("id", "");
            if (id.isEmpty()) {
                Log.w(TAG, "OAuth response missing id or returned error: " + responseBody);
                submitSignOutState();
                return;
            }
            String token = obj.optString("token", "");
            if (token.isEmpty()) {
                token = obj.optString("signature", "");
            }
            Log.i(TAG, "OAuth success, submitting sign in state for id: " + id);
            submitSignInState(id, obj.optString("alias", ""), token);
        } catch (Exception e) {
            Log.e(TAG, "processLoading error: " + e.getMessage(), e);
            submitSignOutState();
        }
    }

    private void submitSignOutState() {
        m_signedInSuccessfully = false;
        m_activity.runOnUiThread(() -> {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
            restoreGameImmersiveFocus();
            m_accountClientInfo.state = SystemAccountClientState.kSystemAccountClientState_SignedOut;
            m_callback.UpdateClientInfo(m_accountClientInfo);
        });
    }

    private void submitSignInState(final String id, final String alias, final String signature) {
        m_signedInSuccessfully = true;
        m_activity.runOnUiThread(() -> {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
            restoreGameImmersiveFocus();
            m_accountClientInfo.accountId = id;
            m_accountClientInfo.alias = alias;
            m_accountClientInfo.signature = signature;
            m_accountClientInfo.state = SystemAccountClientState.kSystemAccountClientState_SignedIn;
            m_callback.UpdateClientInfo(this.m_accountClientInfo);
        });
    }

    private void restoreGameImmersiveFocus() {
        if (m_activity == null) return;
        m_activity.runOnUiThread(() -> {
            try {
                Window window = m_activity.getWindow();
                if (window != null && window.getDecorView() != null) {
                    View decor = window.getDecorView();
                    decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                    );
                    decor.requestFocus();
                }
            } catch (Throwable ignored) {}
        });
    }

    private void showWebView() {
        if (dialog == null) return;
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND, WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialogWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        showWebView();
    }
}