package git.artdeell.skymodloader.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.security.Permission;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StarwatchBlocker {

    private static final String TAG = "StarwatchBlocker";
    private static final String BLOCKED_KEYWORD = "starwatch";
    public static final String PREFS_NAME = "package_configs";
    public static final String PREF_ALLOW_STARWATCH = "allow_starwatch";

    private static volatile boolean starwatchAllowed = false;

    private StarwatchBlocker() {}

    public static void init(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        setStarwatchAllowed(preferences.getBoolean(PREF_ALLOW_STARWATCH, false));
    }

    public static void setStarwatchAllowed(boolean allowed) {
        starwatchAllowed = allowed;
    }

    public static boolean shouldBlock(String urlOrHost) {
        return !starwatchAllowed
                && urlOrHost != null
                && urlOrHost.toLowerCase(Locale.ROOT).contains(BLOCKED_KEYWORD);
    }

    public static void install() {
        try {
            URL.setURLStreamHandlerFactory(new BlockingStreamHandlerFactory());
            Log.i(TAG, "Starwatch domain blocker installed");
        } catch (Error e) {
            Log.w(TAG, "URLStreamHandlerFactory already set, using fallback mode", e);
        }
    }

    public static HttpURLConnection wrapConnection(HttpURLConnection conn) {
        if (shouldBlock(conn.getURL().getHost())) {
            Log.d(TAG, "Blocked (wrap): " + conn.getURL());
            conn.disconnect();
            return new NullHttpURLConnection(conn.getURL());
        }
        return conn;
    }

    public static WebResourceResponse interceptWebViewRequest(WebResourceRequest request) {
        String host = request.getUrl().getHost();
        if (shouldBlock(host)) {
            Log.d(TAG, "Blocked WebView request: " + request.getUrl());
            return new WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    new ByteArrayInputStream(new byte[0])
            );
        }
        return null;
    }

    private static class BlockingStreamHandlerFactory implements URLStreamHandlerFactory {
        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            if ("http".equals(protocol) || "https".equals(protocol)) {
                return new BlockingStreamHandler(protocol);
            }
            return null;
        }
    }

    private static class BlockingStreamHandler extends URLStreamHandler {
        private final String protocol;

        BlockingStreamHandler(String protocol) {
            this.protocol = protocol;
        }

        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            if (shouldBlock(url.getHost())) {
                Log.d(TAG, "Blocked (factory): " + url);
                return new NullHttpURLConnection(url);
            }
            return createRealConnection(url, null);
        }

        @Override
        protected URLConnection openConnection(URL url, Proxy proxy) throws IOException {
            if (shouldBlock(url.getHost())) {
                Log.d(TAG, "Blocked (factory+proxy): " + url);
                return new NullHttpURLConnection(url);
            }
            return createRealConnection(url, proxy);
        }

        private URLConnection createRealConnection(URL url, Proxy proxy) throws IOException {
            try {
                Class<?> handlerClass;
                if ("https".equals(protocol)) {
                    handlerClass = Class.forName("com.android.okhttp.HttpsHandler");
                } else {
                    handlerClass = Class.forName("com.android.okhttp.HttpHandler");
                }
                URLStreamHandler realHandler = (URLStreamHandler) handlerClass.getDeclaredConstructor().newInstance();
                URL realUrl = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile(), realHandler);
                if (proxy != null) {
                    return realUrl.openConnection(proxy);
                }
                return realUrl.openConnection();
            } catch (Exception e) {
                throw new IOException("Failed to create real connection for " + url, e);
            }
        }
    }

    static class NullHttpURLConnection extends HttpURLConnection {

        NullHttpURLConnection(URL url) {
            super(url);
            this.responseCode = 204;
            this.responseMessage = "";
        }

        @Override public void connect() { connected = true; }
        @Override public void disconnect() { connected = false; }
        @Override public boolean usingProxy() { return false; }

        @Override public int getResponseCode() { return 204; }
        @Override public String getResponseMessage() { return ""; }

        @Override public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override public OutputStream getOutputStream() {
            return new OutputStream() {
                @Override public void write(int b) {}
                @Override public void write(byte[] b, int off, int len) {}
            };
        }

        @Override public String getContentType() { return "text/plain"; }
        @Override public int getContentLength() { return 0; }
        @Override public long getContentLengthLong() { return 0; }
        @Override public String getHeaderField(String name) { return null; }
        @Override public Map<String, List<String>> getHeaderFields() {
            return Collections.emptyMap();
        }
        @Override public Permission getPermission() { return null; }
    }
}
