package com.tgc.sky.ui.panels;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import com.tgc.sky.BuildConfig;
import com.tgc.sky.GameActivity;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.net.Uri;

import git.artdeell.skymodloader.net.StarwatchBlocker;

import com.tgc.sky.SystemUI_android;
import com.tgc.sky.ui.panels.CodeScanner;
import com.tgc.sky.ui.text.Markup;
import com.tgc.sky.ui.webview.RoundCornerWebView;

import git.artdeell.skymodloader.R;
import git.artdeell.skymodloader.SMLApplication;


public class Starboard extends BasePanel implements View.OnLayoutChangeListener, GameActivity.OnKeyboardListener {
    private final PanelButton _closeButton;
    public Handle mHandler;
    private final RelativeLayout view;

    public interface Handle {
        void run(String str, int i, boolean z);
    }

    @Override
    public void onKeyboardChange(boolean z, int i) {
    }

    public void setPreviewOrientation() {
    }

    public Starboard(Context context, SystemUI_android systemUI_android, Markup markup, Handle handle) {
        super(context, systemUI_android, markup);
        this.mHandler = handle;
        setBackgroundDrawable(new ColorDrawable(0));
        RelativeLayout relativeLayout = new RelativeLayout(this.m_activity);
        this.view = relativeLayout;
        relativeLayout.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        setContentView(relativeLayout);
        setFocusable(true);
        setWidth(-1);
        setHeight(-1);
        RelativeLayout relativeLayout2 = new RelativeLayout(this.m_activity);
        GradientDrawable gradientDrawable = (GradientDrawable) relativeLayout2.getBackground();
        if (gradientDrawable == null) {
            gradientDrawable = new GradientDrawable();
            relativeLayout2.setBackground(gradientDrawable);
        }
        gradientDrawable.setColor(0);
        relativeLayout.addView(relativeLayout2);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        this.m_activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        relativeLayout2.setLayoutParams(setLayoutParams(displayMetrics));
        ConstraintLayout constraintLayout = new ConstraintLayout(this.m_activity);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.gravity = 5;
        constraintLayout.setLayoutParams(layoutParams);
        PanelButton panelButton = new PanelButton(this.m_activity, new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                Starboard.this.m777lambda$new$0$comtgcskyuipanelsStarboard(view);
            }
        });
        this._closeButton = panelButton;
        panelButton.setId(View.generateViewId());
        panelButton.setLayoutParams(new ConstraintLayout.LayoutParams(-2, -2));
        panelButton.setVisibility(View.VISIBLE);
        panelButton.setEnabled(true);
        ScaleDrawable scaleDrawable = new ScaleDrawable(SMLApplication.skyRes.getDrawable(SMLApplication.skyRes.getIdentifier("systemui_closebutton", "drawable", SMLApplication.skyPName), null), 17, 0.7f, 0.7f);
        scaleDrawable.setLevel(100);
        panelButton.setBackground(scaleDrawable);
        final FrameLayout createLoadingScreen = createLoadingScreen();
        RoundCornerWebView roundCornerWebView = new RoundCornerWebView(this.m_activity);
        roundCornerWebView.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
        roundCornerWebView.setBackgroundColor(0);
        roundCornerWebView.getSettings().setJavaScriptEnabled(true);
        roundCornerWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        StringBuilder sb = new StringBuilder(BuildConfig.SKY_SERVER_HOSTNAME);
        sb.delete(sb.indexOf("radiance"), sb.indexOf("radiance") + 8);
        sb.insert(sb.indexOf(".") + 1, "starboard");
        sb.insert(0, "https://");
        final String sb2 = sb.toString();
        roundCornerWebView.setWebViewClient(new WebViewClient() {
            @Override // android.webkit.WebViewClient
            public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest webResourceRequest) {
                return handleUrl(webResourceRequest.getUrl());
            }

            @Override // android.webkit.WebViewClient
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse blocked = StarwatchBlocker.interceptWebViewRequest(request);
                if (blocked != null) return blocked;
                return super.shouldInterceptRequest(view, request);
            }

            @Override // android.webkit.WebViewClient
            public void onPageFinished(WebView webView, String str) {
                super.onPageFinished(webView, str);
                createLoadingScreen.setVisibility(View.GONE);
            }

            private boolean handleUrl(Uri uri) {
                if (uri.toString().contains(sb2)) {
                    return false;
                }
                Starboard.this.m_activity.startActivity(new Intent("android.intent.action.VIEW", uri));
                return true;
            }
        });
        roundCornerWebView.setWebChromeClient(new WebChromeClient());
        roundCornerWebView.loadUrl(sb2);
        relativeLayout2.addView(createLoadingScreen, new FrameLayout.LayoutParams(-1, -1));
        relativeLayout2.addView(roundCornerWebView);
        relativeLayout2.addView(constraintLayout);
        constraintLayout.addView(panelButton);
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(constraintLayout);
        constraintSet.connect(panelButton.getId(), 2, 0, 2);
        constraintSet.applyTo(constraintLayout);
    }


    void m777lambda$new$0$comtgcskyuipanelsStarboard(View view) {
        onCloseButton();
    }

    private static RelativeLayout.LayoutParams setLayoutParams(DisplayMetrics displayMetrics) {
        int i = displayMetrics.widthPixels;
        int i2 = (int) (displayMetrics.heightPixels * 0.9f);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams((i2 / 2) * 3, i2);
        layoutParams.addRule(14);
        layoutParams.addRule(13);
        layoutParams.addRule(15);
        return layoutParams;
    }

    @Override
    public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
        viewWillLayoutSubviews();
    }

    public void viewWillLayoutSubviews() {
        setPreviewOrientation();
    }

    private FrameLayout createLoadingScreen() {
        FrameLayout frameLayout = new FrameLayout(this.m_activity);
        frameLayout.setForegroundGravity(17);
        frameLayout.setVisibility(View.VISIBLE);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(GradientDrawable.RECTANGLE);
        gradientDrawable.setCornerRadius(dp2px(16.0f));
        gradientDrawable.setColor(Color.argb(102, 0, 0, 0));
        frameLayout.setBackground(gradientDrawable);
        ProgressBar progressBar = new ProgressBar(this.m_activity);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(-1));
        frameLayout.addView(progressBar, new FrameLayout.LayoutParams(-2, -2, 17));
        return frameLayout;
    }

    public void onCloseButton() {
        this.view.performHapticFeedback(7);
        dismissInternal();
        this.mHandler.run(null, CodeScanner.ResultOptions.kCodeScanner_UserClosedPanel.ordinal(), true);
    }

    public void dismissInternal() {
        super.dismiss();
        viewDidDisappear();
    }

    public void viewDidDisappear() {
        this.m_activity.RemoveOnKeyboardListener(this);
    }

    @Override
    public void dismiss() {
        if (this._closeButton.isEnabled()) {
            dismissInternal();
            this.mHandler.run(null, CodeScanner.ResultOptions.kCodeScanner_UserClosedPanel.ordinal(), true);
        }
    }
}