package com.tgc.sky;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.app.Dialog;

import androidx.appcompat.app.AlertDialog;

import java.util.HashMap;
import java.util.Map;

import git.artdeell.skymodloader.R;

public class SystemSupport_android implements GameActivity.OnActivityResultListener {
    private static final String TAG = "SystemSupport_android";
    private static volatile SystemSupport_android mInstance;
    private GameActivity m_activity;
    private Map<String, String[]> m_customIssueFields = new HashMap();
    private boolean m_disableCustomerSupport;
    private boolean m_hasUnreadMessages;
    private Map<String, Object> m_metaData = new HashMap();
    private boolean m_supportSessionActive;
    private boolean m_supportSessionShowing;
    private boolean m_surveyActive;
    private boolean m_surveyCompleted;


    static void OnApplicationCreate(Application application) {

    }

    public static SystemSupport_android getInstance() {
        if (mInstance == null) {
            synchronized (SystemSupport_android.class) {
                if (mInstance == null) {
                    mInstance = new SystemSupport_android();
                }
            }
        }
        return mInstance;
    }

    /* access modifiers changed from: package-private */
    public void Initialize(GameActivity gameActivity) {
        this.m_activity = gameActivity;
    }

    public static void HandlePush(Context context, Map<String, String> map) {
    }

    public static void RegisterDeviceToken(String str) {
    }

    private Object BuildHelpshiftConversationConfig(String[] strArr) {
        return null;
    }

    public void AddMetaData(String str, boolean z) {
    }

    public void AddMetaData(String str, float f) {
    }

    public void AddMetaData(String str, String str2) {
    }

    public void ClearMetaData() {
    }

    public void EnableCustomerSupport(boolean z) {

    }

    public boolean IsCustomerSupportEnabled() {
        return false;
    }

    public void ShowFAQs() {
        m_activity.runOnUiThread(()->{
            Dialog dialog = new Dialog(m_activity, R.style.AboutDialog);
            dialog.setContentView(R.layout.about_dialog);

            // Support links
            setupLink(dialog, R.id.englishTelegram, "https://t.me/+Hc6QeQVTH1JjNjky");
            setupLink(dialog, R.id.russianTelegram, "https://t.me/+mue7BZWmWOxkMDUy");

            // Original developers
            setupLink(dialog, R.id.lukas0x1Github, "https://github.com/lukas0x1");
            setupLink(dialog, R.id.artDevGithub, "https://github.com/artdeell");

            // Contributors
            setupLink(dialog, R.id.romanGithub, "https://github.com/RomanChamelo");
            setupLink(dialog, R.id.kiojeenGithub, "https://github.com/Kiojeen");
            setupLink(dialog, R.id.gxostyGithub, "https://github.com/gxosty");

            // Design credits
            setupLink(dialog, R.id.bannerVk, "https://vk.com/son583");
            setupLink(dialog, R.id.iconTelegram, "https://t.me/Eliatshaha");

            // Original game
            setupLink(dialog, R.id.thatgamecompanyLink, "https://thatgamecompany.com/");

            dialog.show();
        });
    }

    private void setupLink(Dialog dialog, int viewId, String url) {
        TextView textView = dialog.findViewById(viewId);
        textView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            m_activity.startActivity(intent);
        });
    }

    public void ShowSupportSession() {
    }

    public void ShowErrorReportConversation() {

    }

    public boolean IsSupportUIShowing() {
        return this.m_supportSessionActive;
    }

    public boolean HasOpenSupportSession() {
        return this.m_supportSessionShowing;
    }

    public boolean HasUnreadSupportSessionMessages() {
        return this.m_hasUnreadMessages;
    }

    public void sessionBegan() {
        this.m_supportSessionShowing = true;
    }

    public void sessionEnded() {
        this.m_supportSessionShowing = false;
        this.m_hasUnreadMessages = false;
    }

    public void newConversationStarted(String str) {
        this.m_supportSessionActive = true;
    }

    public void conversationEnded() {
        this.m_supportSessionActive = false;
        this.m_hasUnreadMessages = false;
    }

    public void didReceiveNotification(int i) {
        this.m_hasUnreadMessages = i > 0;
    }

    public boolean ShowQuestionnaire(String str, String str2) {
        return false;
    }

    public boolean IsQuestionnaireShowing() {
        return this.m_surveyActive;
    }

    public boolean WasQuestionnaireCompleted() {
        return this.m_surveyCompleted;
    }

    public void onActivityResult(int i, int i2, Intent intent) {

    }
}
