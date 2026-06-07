package com.tgc.sky;

import android.util.Log;

import com.tgc.sky.commerce.ProductInfo;
import com.tgc.sky.commerce.Receipt;

import java.util.HashMap;
import java.util.Locale;

public class SystemCommerce_android
{
    private static final String TAG = "SystemCommerce_android";
    private static final String DEFAULT_PACKAGE_NAME = "com.tgc.sky.android";

    private static volatile SystemCommerce_android sInstance;
    private final GameActivity mActivity;
    private final HashMap<String, ProductInfo> mProductInfo = new HashMap<>();
    private boolean mProductsInitialized;

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
        return false;
    }

    public void LoadProducts(final String[] productIds) {
        mProductInfo.clear();
        for (String productId : productIds) {
            ProductInfo productInfo = createDisplayOnlyProductInfo(toSystemProductId(productId));
            Log.d(TAG, "Product loaded: " + productInfo.systemProductId);
            mProductInfo.put(productInfo.systemProductId, productInfo);
        }
        mProductsInitialized = true;
        mActivity.onCommerceUpdate(true, false, false);
    }

    public boolean MakePurchase(String productIdToSystemProductId) {
        return false;
    }

    public boolean MakePurchase(String productIdToSystemProductId, String accountId, String profileId) {
        return false;
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
