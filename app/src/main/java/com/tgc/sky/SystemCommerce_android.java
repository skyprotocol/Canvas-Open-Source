package com.tgc.sky;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.tgc.sky.commerce.ProductInfo;
import com.tgc.sky.commerce.Receipt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;

import git.artdeell.skymodloader.net.StarwatchBlocker;

public class SystemCommerce_android
{
    private static final String TAG = "SystemCommerce_android";
    private static final String DEFAULT_PACKAGE_NAME = "com.tgc.sky.android";
    private static final String SKY_STORE_URL = "https://store.thatskygame.com/";
    private static final String XSOLLA_CATALOG_URL = "https://store.xsolla.com/api/v2/project/207830/items/virtual_items/group";
    private static final String XSOLLA_SKU_PREFIX = "xsolla.sky.";
    private static final long EXTERNAL_PURCHASE_PENDING_TIMEOUT_MS = 1500L;
    private static final String[] XSOLLA_CATALOG_GROUPS = {
            "event2",
            "events",
            "seasons",
            "candles",
            "currency",
            "starterpack",
            "secondscreen",
            "psseasonpass"
    };

    private static volatile SystemCommerce_android sInstance;
    private final GameActivity mActivity;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final HashMap<String, ProductInfo> mProductInfo = new HashMap<>();
    private boolean mProductsInitialized;
    private String mPendingExternalProductId;
    private boolean mStoreCompleted;

    SystemCommerce_android(final GameActivity activity) {
        mActivity = activity;
        sInstance = this;
    }

    public static SystemCommerce_android getInstance() {
        return sInstance;
    }

    public boolean CanMakePayments() {
        return mProductsInitialized;
    }

    public boolean FinishPurchase(String productIdToSystemProductId, final String s) {
        return false;
    }

    public int GetPlatformInt() {
        return 0;
    }

    public ProductInfo GetProductInfo(String productIdToSystemProductId) {
        if (!mProductsInitialized) {
            return null;
        }
        return mProductInfo.get(toSystemProductId(productIdToSystemProductId));
    }

    public Receipt GetReceipt() {
        return null;
    }

    public boolean IsPurchasePending(String productIdToSystemProductId) {
        return mPendingExternalProductId != null
                && toXsollaSku(mPendingExternalProductId).equals(toXsollaSku(productIdToSystemProductId));
    }

    public void LoadProducts(final String[] productIds) {
        mProductInfo.clear();
        String[] systemProductIds = new String[productIds.length];
        for (int i = 0; i < productIds.length; ++i) {
            String systemProductId = toSystemProductId(productIds[i]);
            ProductInfo productInfo = createDisplayOnlyProductInfo(systemProductId);
            Log.d(TAG, "Product loaded: " + productInfo.systemProductId);
            mProductInfo.put(productInfo.systemProductId, productInfo);
            systemProductIds[i] = systemProductId;
        }
        mProductsInitialized = true;
        loadCatalogProducts(systemProductIds);
    }

    public boolean MakePurchase(String productIdToSystemProductId) {
        return MakePurchase(productIdToSystemProductId, null, null);
    }

    public boolean MakePurchase(String productIdToSystemProductId, String accountId, String profileId) {
        String url = createStoreUrl(productIdToSystemProductId, accountId);
        if (url == null) {
            return false;
        }
        mPendingExternalProductId = toSystemProductId(productIdToSystemProductId);
        mStoreCompleted = false;
        openStore(url);
        return true;
    }

    public boolean RefreshReceipt() {
        return true;
    }

    String GetErrorMessage() {
        return null;
    }

    public boolean RestorePurchases() {
        return true;
    }

    void onDestroy() {
        if (sInstance == this) {
            sInstance = null;
        }
    }

    void onResume() {
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void openStore(final String url) {
        mActivity.runOnUiThread(() -> {
            final Dialog dialog = new Dialog(mActivity);
            final WebView webView = new WebView(mActivity) {
                @Override
                public boolean onCheckIsTextEditor() {
                    return true;
                }
            };

            dialog.setContentView(webView);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            }

            webView.setWebChromeClient(new WebChromeClient());
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return handleStoreUrl(dialog, request.getUrl());
                }

                @Override
                @SuppressWarnings("deprecation")
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return handleStoreUrl(dialog, Uri.parse(url));
                }

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    WebResourceResponse blocked = StarwatchBlocker.interceptWebViewRequest(request);
                    if (blocked != null) return blocked;
                    return super.shouldInterceptRequest(view, request);
                }
            });

            webView.loadUrl(url);
            dialog.setOnDismissListener(d -> {
                CookieManager.getInstance().flush();
                finishExternalStoreFlow();
            });
            dialog.show();

            Window dialogWindow = dialog.getWindow();
            if (dialogWindow != null) {
                dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                dialogWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            }
        });
    }

    private void loadCatalogProducts(String[] systemProductIds) {
        new Thread(() -> {
            try {
                HashMap<String, ProductInfo> catalogProducts = fetchCatalogProducts(systemProductIds);
                mMainHandler.post(() -> {
                    boolean changed = false;
                    for (ProductInfo productInfo : catalogProducts.values()) {
                        if (mProductInfo.containsKey(productInfo.systemProductId)) {
                            mProductInfo.put(productInfo.systemProductId, productInfo);
                            changed = true;
                        }
                    }

                    if (changed) {
                        Log.d(TAG, "Sky Store catalog applied: " + catalogProducts.size() + " products");
                    }
                    mActivity.onCommerceUpdate(true, false, false);
                });
            } catch (Exception e) {
                Log.w(TAG, "Failed to load Sky Store catalog", e);
                mMainHandler.post(() -> mActivity.onCommerceUpdate(true, false, false));
            }
        }, "SkyStoreCatalog").start();
    }

    private HashMap<String, ProductInfo> fetchCatalogProducts(String[] systemProductIds) throws Exception {
        HashMap<String, String> requestedProducts = new HashMap<>();
        for (String systemProductId : systemProductIds) {
            requestedProducts.put(toXsollaSku(systemProductId), systemProductId);
        }

        String response = getUrl(buildCatalogUrl());
        JSONObject root = new JSONObject(response);
        JSONArray items = root.optJSONArray("items");
        HashMap<String, ProductInfo> catalogProducts = new HashMap<>();
        if (items == null) {
            return catalogProducts;
        }

        for (int i = 0; i < items.length(); ++i) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }

            String sku = item.optString("sku", "").toLowerCase(Locale.ROOT);
            String systemProductId = requestedProducts.get(sku);
            if (systemProductId == null) {
                continue;
            }

            catalogProducts.put(systemProductId, createCatalogProductInfo(systemProductId, item));
        }

        return catalogProducts;
    }

    private String buildCatalogUrl() {
        StringBuilder url = new StringBuilder(XSOLLA_CATALOG_URL);
        url.append("?locale=").append(urlEncode(getCatalogLocale()));
        for (String group : XSOLLA_CATALOG_GROUPS) {
            url.append("&external_id%5B%5D=").append(urlEncode(group));
        }
        return url.toString();
    }

    private String getUrl(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        try {
            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String response = readResponse(stream);
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Catalog request failed: HTTP " + responseCode);
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private String readResponse(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private boolean handleStoreUrl(Dialog dialog, Uri uri) {
        String url = uri.toString();
        if (url.contains("operationPayload=")) {
            mStoreCompleted = true;
            dialog.dismiss();
            mActivity.onOpenedWithURLNative(url, false);
            return true;
        }

        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return false;
        }

        mStoreCompleted = true;
        dialog.dismiss();
        mActivity.onOpenedWithURLNative(url, false);
        return true;
    }

    private void finishExternalStoreFlow() {
        if (mStoreCompleted || mPendingExternalProductId == null) {
            return;
        }

        mMainHandler.postDelayed(() -> {
            if (!mStoreCompleted) {
                mPendingExternalProductId = null;
                mActivity.onCommerceUpdate(true, false, false);
            }
        }, EXTERNAL_PURCHASE_PENDING_TIMEOUT_MS);
    }

    private String createStoreUrl(String productIdToSystemProductId, String accountId) {
        String sku = toXsollaSku(productIdToSystemProductId);
        if (sku.isEmpty()) {
            return null;
        }

        StringBuilder url = new StringBuilder(SKY_STORE_URL);
        url.append("?purchase-sku=").append(urlEncode(sku));
        if (accountId != null && !accountId.isEmpty()) {
            url.append("&user-id=").append(urlEncode(accountId));
        }
        return url.toString();
    }

    private ProductInfo createCatalogProductInfo(String systemProductId, JSONObject item) {
        ProductInfo productInfo = createDisplayOnlyProductInfo(systemProductId);

        String name = item.optString("name", "");
        if (!name.isEmpty()) {
            productInfo.name = name;
        }

        String description = item.optString("description", "");
        if (!description.isEmpty()) {
            productInfo.desc = description;
        }

        JSONObject price = item.optJSONObject("price");
        if (price != null) {
            String amount = price.optString("amount", "");
            String currency = price.optString("currency", "");
            productInfo.price = formatPrice(amount, currency);
            productInfo.currency = currency;
            productInfo.priceMicros = parsePriceMicros(amount);
        }

        return productInfo;
    }

    private String formatPrice(String amount, String currency) {
        if (amount == null || amount.isEmpty()) {
            return "";
        }
        if (currency == null || currency.isEmpty()) {
            return amount;
        }
        return amount + " " + currency;
    }

    private double parsePriceMicros(String amount) {
        if (amount == null || amount.isEmpty()) {
            return 0.0d;
        }

        try {
            return Double.parseDouble(amount) * 1000000.0d;
        } catch (NumberFormatException e) {
            return 0.0d;
        }
    }

    private String toXsollaSku(String productIdToSystemProductId) {
        if (productIdToSystemProductId == null) {
            return "";
        }

        String productId = productIdToSystemProductId.toLowerCase(Locale.ROOT);
        if (productId.startsWith(XSOLLA_SKU_PREFIX)) {
            return productId;
        }

        int lastDot = productId.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < productId.length()) {
            productId = productId.substring(lastDot + 1);
        }
        return XSOLLA_SKU_PREFIX + productId;
    }

    private String getCatalogLocale() {
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        String country = locale.getCountry();
        if (language == null || language.isEmpty()) {
            return "en-US";
        }
        if (country == null || country.isEmpty()) {
            return language;
        }
        return language + "-" + country;
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private ProductInfo createDisplayOnlyProductInfo(String systemProductId) {
        ProductInfo productInfo = new ProductInfo();
        productInfo.systemProductId = systemProductId;
        productInfo.name = getProductName(systemProductId);
        productInfo.desc = "";
        productInfo.price = "";
        productInfo.currency = "";
        productInfo.priceMicros = 0.0d;
        return productInfo;
    }

    private String toSystemProductId(String productId) {
        if (productId == null) {
            return "";
        }

        String normalizedProductId = productId.toLowerCase(Locale.ROOT);
        if (normalizedProductId.startsWith("com.tgc.sky.")) {
            return normalizedProductId;
        }

        return getProductPrefix() + normalizedProductId;
    }

    private String getProductPrefix() {
        String packageName = BuildConfig.APPLICATION_ID;
        if (packageName == null || packageName.isEmpty()) {
            packageName = DEFAULT_PACKAGE_NAME;
        }
        return packageName.toLowerCase(Locale.ROOT) + ".";
    }

    private String getProductName(String systemProductId) {
        String prefix = getProductPrefix();
        if (systemProductId != null && systemProductId.startsWith(prefix)) {
            return systemProductId.substring(prefix.length());
        }
        return systemProductId != null ? systemProductId : "";
    }
}
