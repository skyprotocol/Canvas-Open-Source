package com.tgc.sky.ui.text;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.SpannableStringBuilder;

import androidx.constraintlayout.core.motion.utils.TypedValues;
import androidx.core.app.FrameMetricsAggregator;
import com.tgc.sky.GameActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.lang.reflect.Field;
import java.util.Map;

import git.artdeell.skymodloader.SMLApplication;


public class LocalizationManager {
    private static final Map<String, String> DEFAULT_COUNTRY_CODES;
    private static LocalizationManager g_instance = null;
    private static int kMaxLocalizedStrings = 512;
    private GameActivity m_activity;
    private boolean m_touchControls = true;
    private boolean m_proControls = false;
    private boolean m_leftHanded = false;
    private boolean m_gamepad = false;
    private boolean m_invertFlight = false;
    private boolean m_invertCamera = false;
    private boolean m_enableHaptics = true;
    private ArrayList<LocalizedStringArgs> m_localizedStrings = new ArrayList<>(kMaxLocalizedStrings);

    public native boolean FreeTextId(int i);

    static {
        HashMap map = new HashMap();
        DEFAULT_COUNTRY_CODES = map;
        map.put("en", "US");
        map.put("es", "ES");
        map.put("fr", "FR");
        map.put("de", "DE");
        map.put("ja", "JP");
        map.put("ko", "KR");
        map.put("zh", "CN");
        map.put("zh-Hant", "TW");
        map.put("pt", "BR");
        map.put("it", "IT");
        map.put("ru", "RU");
        map.put("ar", "SA");
        map.put("nl", "NL");
        map.put("hi", "IN");
        map.put("tr", "TR");
        map.put("th", "TH");
        map.put("vi", "VN");
        map.put("pl", "PL");
        map.put("sv", "SE");
        map.put("fi", "FI");
        map.put("da", "DK");
        map.put("no", "NO");
        map.put("he", "IL");
        map.put("cs", "CZ");
        map.put("hu", "HU");
        map.put("el", "GR");
        map.put("ro", "RO");
        map.put("sk", "SK");
        map.put("uk", "UA");
        map.put("bg", "BG");
        map.put("hr", "HR");
        map.put("sr", "RS");
        map.put("ms", "MY");
        map.put("id", "ID");
    }

    public LocalizationManager(GameActivity gameActivity) {
        g_instance = this;
        this.m_activity = gameActivity;
        for (int i = 0; i < kMaxLocalizedStrings; i++) {
            this.m_localizedStrings.add(new LocalizedStringArgs());
        }
        this.m_localizedStrings.get(0).baseText = null;
        this.m_localizedStrings.get(0).localizedString = "";
        this.m_localizedStrings.get(0).args = null;
        this.m_localizedStrings.get(0).lastChangeCounter = -1;
        this.m_localizedStrings.get(0).compounded = null;
    }

    public void SetGameInputConfig(boolean z, boolean z2, boolean z3, boolean z4, boolean z5, boolean z6, boolean z7) {
        this.m_touchControls = z;
        this.m_proControls = z2;
        this.m_leftHanded = z3;
        this.m_gamepad = z4;
        this.m_invertFlight = z5;
        this.m_invertCamera = z6;
        this.m_enableHaptics = z7;
    }

    public String LocalizeString(String r10) {
        if(r10 == null) return null;
        int id = SMLApplication.skyRes.getIdentifier(r10, "string", SMLApplication.skyPName);
        if(id != 0) {
            return SMLApplication.skyRes.getString(id);
        }
        return r10;
    }

    public static String GetActiveLocalization() {
        return Locale.getDefault().getLanguage();
    }
    public static String GetIETFLanguageTag() {
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        String country = locale.getCountry();
        if (!country.isEmpty()) {
            return language + "-" + country;
        }
        return GetIETFLanguageTag(language);
    }
	
	public static LocalizationManager getInstance() {
    return g_instance;
	}

    public static String GetIETFLanguageTag(String str) {
        String str2 = DEFAULT_COUNTRY_CODES.get(str);
        return (str2 == null || str2.isEmpty()) ? str : str + "-" + str2;
    }

    public static void SetActiveLocalization(String str) {
        GameActivity gameActivity;
        String str2 = DEFAULT_COUNTRY_CODES.get(str);
        if (str2 == null) {
            str2 = "";
        }
        Locale locale = new Locale(str, str2);
        Locale.setDefault(locale);
        LocalizationManager localizationManager = g_instance;
        if (localizationManager == null || (gameActivity = localizationManager.m_activity) == null) {
            return;
        }
        Resources resources = gameActivity.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());
        configuration.locale = locale;
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
    }

    static int getFirstStringResourceId() {
        /*
        try {
            Field[] declaredFields = R.string.class.getDeclaredFields();
            if (declaredFields.length > 0) {
                return declaredFields[0].getInt(null);
            }
            return 0;
        } catch (Exception unused) {
            return 0;
        }

         */
        return 0x7f10001b;
    }

    public boolean SupportsLocalization(String str) throws Resources.NotFoundException {
//        Locale locale = Locale.getDefault();
//        Locale locale2 = new Locale(str);
//        if (locale.getLanguage().equals(locale2.getLanguage())) {
//            return true;
//        }
//        Locale.setDefault(locale2);
//        Resources resources = this.m_activity.getResources();
//        Configuration configuration = new Configuration(resources.getConfiguration());
//        configuration.locale = locale2;
//        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
//        String string = resources.getString(getFirstStringResourceId());
//        Locale.setDefault(locale);
//        configuration.locale = locale;
//        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
//        return !string.equals(resources.getString(r1));
        return true;
    }

    public boolean HasLocalizedString(String str) {
        return str != null && SMLApplication.skyRes.getIdentifier(str, "string", SMLApplication.skyPName) != 0;
    }

    public LocalizedStringArgs GetLocalizedStringArgs(int i) {
        int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        return GetAllocatedTextIdx < kMaxLocalizedStrings ? this.m_localizedStrings.get(GetAllocatedTextIdx) : this.m_localizedStrings.get(0);
    }

    public void FreeLocalizedText(final int i) {
        final int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        if (GetAllocatedTextIdx < kMaxLocalizedStrings) {
            if (this.m_localizedStrings.get(GetAllocatedTextIdx).compounded == null) {
                this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.1
                    @Override // java.lang.Runnable
                    public void run() {
                        ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).baseText = null;
                        ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).localizedString = null;
                        ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).args = null;
                        ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).lastChangeCounter = 0;
                        ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).compounded = null;
                        LocalizationManager.this.FreeTextId(i);
                    }
                });
                return;
            }
            for (int i2 : this.m_localizedStrings.get(GetAllocatedTextIdx).compounded) {
                FreeLocalizedText(i2);
            }
            this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.2
                @Override // java.lang.Runnable
                public void run() {
                    ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).baseText = null;
                    ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).localizedString = null;
                    ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).args = null;
                    ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).lastChangeCounter = 0;
                    ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).compounded = null;
                    LocalizationManager.this.FreeTextId(i);
                }
            });
        }
    }

    public void SetLocalizedText(int i, final String str) {
        final int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        if (GetAllocatedTextIdx >= kMaxLocalizedStrings) {
            return;
        }
        if (this.m_localizedStrings.get(GetAllocatedTextIdx).args == null && this.m_localizedStrings.get(GetAllocatedTextIdx).compounded == null && this.m_localizedStrings.get(GetAllocatedTextIdx).baseText != null && this.m_localizedStrings.get(GetAllocatedTextIdx).baseText.equals(str)) {
            return;
        }
        this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.3
            @Override // java.lang.Runnable
            public void run() {
                LocalizedStringArgs localizedStringArgs = (LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx);
                localizedStringArgs.baseText = str;
                localizedStringArgs.localizedString = LocalizationManager.this.LocalizeString(str);
                localizedStringArgs.args = null;
                localizedStringArgs.lastChangeCounter++;
                localizedStringArgs.refresh = null;
                localizedStringArgs.compounded = null;
            }
        });
    }

    public void SetLocalizedTextWithArgs(int i, final String str, float f) {
        final int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        if (GetAllocatedTextIdx >= kMaxLocalizedStrings) {
            return;
        }
        final String FormatFloat = FormatFloat(f);
        this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.4
            @Override // java.lang.Runnable
            public void run() {
                LocalizedStringArgs localizedStringArgs = (LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx);
                localizedStringArgs.baseText = str;
                localizedStringArgs.localizedString = LocalizationManager.this.LocalizeString(str);
                localizedStringArgs.args = new ArrayList<Object>() { // from class: com.tgc.sky.ui.text.LocalizationManager.4.1
                    {
                        add(FormatFloat);
                    }
                };
                localizedStringArgs.lastChangeCounter++;
                localizedStringArgs.refresh = null;
                localizedStringArgs.compounded = null;
            }
        });
    }

    public void SetLocalizedTextWithArgs(int i, final String str, final String str2) {
        final int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        if (GetAllocatedTextIdx >= kMaxLocalizedStrings) {
            return;
        }
        this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.5
            @Override // java.lang.Runnable
            public void run() {
                LocalizedStringArgs localizedStringArgs = (LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx);
                localizedStringArgs.baseText = str;
                localizedStringArgs.localizedString = LocalizationManager.this.LocalizeString(str);
                localizedStringArgs.args = new ArrayList<Object>() { // from class: com.tgc.sky.ui.text.LocalizationManager.5.1
                    {
                        add(str2 != null ? str2 : "");
                    }
                };
                localizedStringArgs.lastChangeCounter++;
                localizedStringArgs.refresh = null;
                localizedStringArgs.compounded = null;
            }
        });
    }

    public void SetLocalizedTextWithArgs(int i, final String str, float f, float f2) {
        final int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        if (GetAllocatedTextIdx >= kMaxLocalizedStrings) {
            return;
        }
        final String FormatFloat = FormatFloat(f);
        final String FormatFloat2 = FormatFloat(f2);
        this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.6
            @Override // java.lang.Runnable
            public void run() {
                LocalizedStringArgs localizedStringArgs = (LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx);
                localizedStringArgs.baseText = str;
                localizedStringArgs.localizedString = LocalizationManager.this.LocalizeString(str);
                localizedStringArgs.args = new ArrayList<Object>() { // from class: com.tgc.sky.ui.text.LocalizationManager.6.1
                    {
                        add(FormatFloat);
                        add(FormatFloat2);
                    }
                };
                localizedStringArgs.lastChangeCounter++;
                localizedStringArgs.refresh = null;
                localizedStringArgs.compounded = null;
            }
        });
    }

    public void SetLocalizedTextWithArgs(int i, final String str, float f, final String str2) {
        final int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        if (GetAllocatedTextIdx >= kMaxLocalizedStrings) {
            return;
        }
        final String FormatFloat = FormatFloat(f);
        this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.7
            @Override // java.lang.Runnable
            public void run() {
                LocalizedStringArgs localizedStringArgs = (LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx);
                localizedStringArgs.baseText = str;
                localizedStringArgs.localizedString = LocalizationManager.this.LocalizeString(str);
                localizedStringArgs.args = new ArrayList<Object>() { // from class: com.tgc.sky.ui.text.LocalizationManager.7.1
                    {
                        add(FormatFloat);
                        add(str2 != null ? str2 : "");
                    }
                };
                localizedStringArgs.lastChangeCounter++;
                localizedStringArgs.refresh = null;
                localizedStringArgs.compounded = null;
            }
        });
    }

    public void SetLocalizedTextWithArgs(int i, final String str, final String str2, float f) {
        final int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        if (GetAllocatedTextIdx >= kMaxLocalizedStrings) {
            return;
        }
        final String FormatFloat = FormatFloat(f);
        this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.8
            @Override // java.lang.Runnable
            public void run() {
                LocalizedStringArgs localizedStringArgs = (LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx);
                localizedStringArgs.baseText = str;
                localizedStringArgs.localizedString = LocalizationManager.this.LocalizeString(str);
                localizedStringArgs.args = new ArrayList<Object>() { // from class: com.tgc.sky.ui.text.LocalizationManager.8.1
                    {
                        add(str2 != null ? str2 : "");
                        add(FormatFloat);
                    }
                };
                localizedStringArgs.lastChangeCounter++;
                localizedStringArgs.refresh = null;
                localizedStringArgs.compounded = null;
            }
        });
    }

    public void SetLocalizedTextWithArgs(int i, final String str, final String str2, final String str3) {
        final int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        if (GetAllocatedTextIdx >= kMaxLocalizedStrings) {
            return;
        }
        this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.9
            @Override // java.lang.Runnable
            public void run() {
                LocalizedStringArgs localizedStringArgs = (LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx);
                localizedStringArgs.baseText = str;
                localizedStringArgs.localizedString = LocalizationManager.this.LocalizeString(str);
                localizedStringArgs.args = new ArrayList<Object>() { // from class: com.tgc.sky.ui.text.LocalizationManager.9.1
                    {
                        add(str2 != null ? str2 : "");
                        add(str3 != null ? str3 : "");
                    }
                };
                localizedStringArgs.lastChangeCounter++;
                localizedStringArgs.refresh = null;
                localizedStringArgs.compounded = null;
            }
        });
    }

    public void SetLocalizedTextWithArgs(int i, final String str, float f, float f2, float f3) {
        final int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        if (GetAllocatedTextIdx >= kMaxLocalizedStrings) {
            return;
        }
        final String FormatFloat = FormatFloat(f);
        final String FormatFloat2 = FormatFloat(f2);
        final String FormatFloat3 = FormatFloat(f3);
        this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.10
            @Override // java.lang.Runnable
            public void run() {
                LocalizedStringArgs localizedStringArgs = (LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx);
                localizedStringArgs.baseText = str;
                localizedStringArgs.localizedString = LocalizationManager.this.LocalizeString(str);
                localizedStringArgs.args = new ArrayList<Object>() { // from class: com.tgc.sky.ui.text.LocalizationManager.10.1
                    {
                        add(FormatFloat);
                        add(FormatFloat2);
                        add(FormatFloat3);
                    }
                };
                localizedStringArgs.lastChangeCounter++;
                localizedStringArgs.refresh = null;
                localizedStringArgs.compounded = null;
            }
        });
    }

    public void SetLocalizedTextWithArgs(int i, final String str, final String str2, final String str3, final String str4) {
        final int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        if (GetAllocatedTextIdx >= kMaxLocalizedStrings) {
            return;
        }
        this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.11
            @Override // java.lang.Runnable
            public void run() {
                LocalizedStringArgs localizedStringArgs = (LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx);
                localizedStringArgs.baseText = str;
                localizedStringArgs.localizedString = LocalizationManager.this.LocalizeString(str);
                localizedStringArgs.args = new ArrayList<Object>() { // from class: com.tgc.sky.ui.text.LocalizationManager.11.1
                    {
                        add(str2 != null ? str2 : "");
                        add(str3 != null ? str3 : "");
                        add(str4 != null ? str4 : "");
                    }
                };
                localizedStringArgs.lastChangeCounter++;
                localizedStringArgs.refresh = null;
                localizedStringArgs.compounded = null;
            }
        });
    }

    public void SetLocalizedTextCompounded(int i, final int[] iArr) {
        final int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        if (GetAllocatedTextIdx >= kMaxLocalizedStrings) {
            return;
        }
        this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.12
            @Override // java.lang.Runnable
            public void run() {
                ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).baseText = null;
                ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).localizedString = null;
                ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).args = null;
                ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).lastChangeCounter++;
                ((LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx)).compounded = iArr;
            }
        });
    }

    public void SetPreLocalizedText(int i, final String str) {
        final int GetAllocatedTextIdx = GetAllocatedTextIdx(i);
        if (GetAllocatedTextIdx >= kMaxLocalizedStrings) {
            return;
        }
        this.m_activity.runOnUiThread(new Runnable() { // from class: com.tgc.sky.ui.text.LocalizationManager.13
            @Override // java.lang.Runnable
            public void run() {
                LocalizedStringArgs localizedStringArgs = (LocalizedStringArgs) LocalizationManager.this.m_localizedStrings.get(GetAllocatedTextIdx);
                localizedStringArgs.baseText = null;
                localizedStringArgs.localizedString = str;
                localizedStringArgs.args = null;
                localizedStringArgs.lastChangeCounter++;
                localizedStringArgs.refresh = null;
                localizedStringArgs.compounded = null;
            }
        });
    }

    public static SpannableStringBuilder ApplyTextArgs(SpannableStringBuilder sb, ArrayList<Object> args) {
    if (args == null || args.size() == 0) return sb;
    String[] placeholders = {"{{1}}", "{{2}}", "{{3}}"};
    for (int i = 0; i < args.size() && i < placeholders.length; i++) {
        String replacement = args.get(i) != null ? args.get(i).toString() : "";
        int pos = 0;
        while (true) {
            String s = sb.toString();
            int idx = s.indexOf(placeholders[i], pos);
            if (idx == -1) break;
            sb.replace(idx, idx + 5, replacement);
            pos = idx + replacement.length();
        }
    }
    return sb;
}

private SpannableStringBuilder GetLocalizedTextFromTextIdRecursive(int i) {
    int idx = GetAllocatedTextIdx(i);
    if (idx >= kMaxLocalizedStrings) return null;

    LocalizedStringArgs args = m_localizedStrings.get(idx);

    if (args.localizedString != null) {
        SpannableStringBuilder sb = new SpannableStringBuilder(args.localizedString);
        return ApplyTextArgs(sb, args.args);
    }

    if (args.compounded != null) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int id : args.compounded) {
            SpannableStringBuilder part = GetLocalizedTextFromTextIdRecursive(id);
            if (part != null) sb.append(part);
        }
        return sb.length() > 0 ? sb : null;
    }

    return null;
	}

    public String GetLocalizedTextFromTextId(int textId) {
        SpannableStringBuilder result = GetLocalizedTextFromTextIdRecursive(textId);
        if (result == null) return null;
        return result.toString();
    }

    private int GetAllocatedTextIdx(int i) {
        return i != -1 ? i & FrameMetricsAggregator.EVERY_DURATION : kMaxLocalizedStrings;
    }

    private String FormatFloat(float f) {
        long j = (long) f;
        if (f == ((float) j)) {
            return Long.toString(j);
        }
        return Double.toString(f);
    }
}
