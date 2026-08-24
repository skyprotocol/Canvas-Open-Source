package com.tgc.sky;

import com.tgc.sky.accounts.Apple;
import com.tgc.sky.accounts.AppleGameCenter;
import git.artdeell.skymodloader.auth.Facebook;
import git.artdeell.skymodloader.auth.Google;
import git.artdeell.skymodloader.auth.Huawei;
import git.artdeell.skymodloader.auth.Nintendo;
import git.artdeell.skymodloader.auth.Sony;
import git.artdeell.skymodloader.auth.Steam;
import git.artdeell.skymodloader.auth.Twitch;
import git.artdeell.skymodloader.SkyAuthIntegration;

import com.tgc.sky.accounts.SystemAccountClientInfo;
import com.tgc.sky.accounts.SystemAccountClientRequestState;
import com.tgc.sky.accounts.SystemAccountInterface;
import com.tgc.sky.accounts.SystemAccountServerInfo;
import com.tgc.sky.accounts.SystemAccountServerState;
import com.tgc.sky.accounts.SystemAccountType;


public class SystemAccounts_android implements SystemAccountInterface.UpdateClientInfoCallback {
    private static volatile SystemAccounts_android sInstance;
    private GameActivity m_activity;
    private Apple m_systemAccountApple;
    private AppleGameCenter m_systemAccountAppleGameCenter;
    private Facebook m_systemAccountFacebook;
    private Google m_systemAccountGoogle;
    private Huawei m_systemAccountHuawei;
    private Nintendo m_systemAccountNintendo;
    private Sony m_systemAccountPlaystation;
    private Steam m_systemAccountSteam;
    private Twitch m_systemAccountTwitch;


    public native void OnSystemAccount(SystemAccountClientInfo systemAccountClientInfo);

    SystemAccounts_android(GameActivity gameActivity) {
        this.m_activity = gameActivity;
        AppleGameCenter appleGameCenter =  this.m_systemAccountAppleGameCenter = new AppleGameCenter();
        appleGameCenter.Initialize(gameActivity, this);
        Google google = this.m_systemAccountGoogle = new Google();
        google.Initialize(gameActivity, this);
        Facebook facebook = this.m_systemAccountFacebook = new Facebook();
        facebook.Initialize(gameActivity, this);
        Apple apple = this.m_systemAccountApple = new Apple();
        apple.Initialize(gameActivity, this);
        Nintendo nintendo = this.m_systemAccountNintendo = new Nintendo();
        nintendo.Initialize(gameActivity, this);
        Huawei huawei = this.m_systemAccountHuawei = new Huawei();
        huawei.Initialize(gameActivity, this);
        this.m_systemAccountPlaystation = new Sony();
        m_systemAccountPlaystation.Initialize(gameActivity, this);
        Steam steam = this.m_systemAccountSteam = new Steam();
        steam.Initialize(gameActivity, this);
        Twitch twitch = new Twitch();
        this.m_systemAccountTwitch = twitch;
        twitch.Initialize(gameActivity, this);

        sInstance = this;
    }

    public static SystemAccounts_android getInstance() {
        return sInstance;
    }

    /* renamed from: com.tgc.sky.SystemAccounts_android$1 */
    static /* synthetic */ class SystemAccountsSwitchMap {
        static final /* synthetic */ int[] INTS;

        /* JADX WARNING: Can't wrap try/catch for region: R(14:0|1|2|3|4|5|6|7|8|9|10|11|12|(3:13|14|16)) */
        /* JADX WARNING: Can't wrap try/catch for region: R(16:0|1|2|3|4|5|6|7|8|9|10|11|12|13|14|16) */
        /* JADX WARNING: Failed to process nested try/catch */
        /* JADX WARNING: Missing exception handler attribute for start block: B:11:0x003e */
        /* JADX WARNING: Missing exception handler attribute for start block: B:13:0x0049 */
        /* JADX WARNING: Missing exception handler attribute for start block: B:3:0x0012 */
        /* JADX WARNING: Missing exception handler attribute for start block: B:5:0x001d */
        /* JADX WARNING: Missing exception handler attribute for start block: B:7:0x0028 */
        /* JADX WARNING: Missing exception handler attribute for start block: B:9:0x0033 */
        static {
            INTS = new int[SystemAccountType.values().length];
            INTS[SystemAccountType.kSystemAccountType_AppleGameCenter.ordinal()] = 1;
            INTS[SystemAccountType.kSystemAccountType_Google.ordinal()] = 2;
            INTS[SystemAccountType.kSystemAccountType_Facebook.ordinal()] = 3;
            INTS[SystemAccountType.kSystemAccountType_Apple.ordinal()] = 4;
            INTS[SystemAccountType.kSystemAccountType_Nintendo.ordinal()] = 5;
            INTS[SystemAccountType.kSystemAccountType_Huawei.ordinal()] = 6;
            INTS[SystemAccountType.kSystemAccountType_Sony.ordinal()] = 7;
            INTS[SystemAccountType.kSystemAccountType_Steam.ordinal()] = 8;
            INTS[SystemAccountType.kSystemAccountType_Twitch.ordinal()] = 9;
            INTS[SystemAccountType.kSystemAccountType_Local.ordinal()] = 10;
            INTS[SystemAccountType.kSystemAccountType_AppleGameCenterVN.ordinal()] = 11;
            INTS[SystemAccountType.kSystemAccountType_AppleVN.ordinal()] = 12;
        }
    }

    public SystemAccountInterface GetSystemAccount(SystemAccountType systemAccountType) {
        switch (SystemAccountsSwitchMap.INTS[systemAccountType.ordinal()]) {
            case 1:
                return this.m_systemAccountAppleGameCenter;
            case 2:
                return this.m_systemAccountGoogle;
            case 3:
                return this.m_systemAccountFacebook;
            case 4:
                return this.m_systemAccountApple;
            case 5:
                return this.m_systemAccountNintendo;
            case 6:
                return this.m_systemAccountHuawei;
            case 7:
                return this.m_systemAccountPlaystation;
            case 8:
                return this.m_systemAccountSteam;
            case 9:
                return this.m_systemAccountTwitch;
            default:
                return null;
        }
    }

    /* access modifiers changed from: package-private */
    public void onResume() {
        //this.m_systemAccountGoogle.onResume();
    }

    public void InitializeCredentials(int i) {
        SystemAccountInterface GetSystemAccount = GetSystemAccount(SystemAccountType.values()[i]);
        if (GetSystemAccount != null) {
            UpdateClientInfo(GetSystemAccount.GetClientInfo());
        }
    }

    public void SignIn(int i) {
        SystemAccountInterface GetSystemAccount = GetSystemAccount(SystemAccountType.values()[i]);
        if (GetSystemAccount != null) {
            GetSystemAccount.SignIn();
        }
    }

    public void SignOut(int i) {
        if (SkyAuthIntegration.isEnabled(m_activity)) {
            // SKYAuth owns the game authorization. Keep the borrowed platform
            // account intact and replace only the authorization file.
            m_activity.refreshFreeSkyAuthorization();
            return;
        }
        SystemAccountInterface GetSystemAccount = GetSystemAccount(SystemAccountType.values()[i]);
        if (GetSystemAccount != null) {
            GetSystemAccount.SignOut();
        }
    }

    public void RefreshCredentials(int i, int i2) {
        SystemAccountInterface GetSystemAccount = GetSystemAccount(SystemAccountType.values()[i]);
        if (GetSystemAccount != null) {
            GetSystemAccount.RefreshCredentials(SystemAccountClientRequestState.values()[i2]);
        }
    }

    public void UpdateServerInfo(int i, int i2, String str, String str2) {
        SystemAccountInterface GetSystemAccount = GetSystemAccount(SystemAccountType.values()[i]);
        if (GetSystemAccount != null) {
            SystemAccountServerInfo GetServerInfo = GetSystemAccount.GetServerInfo();
            GetServerInfo.state = SystemAccountServerState.values()[i2];
            GetServerInfo.accountId = str;
            GetServerInfo.alias = str2;
        }
    }

    public void UpdateClientInfo(SystemAccountClientInfo systemAccountClientInfo) {
        OnSystemAccount(systemAccountClientInfo);
    }

    public static String systemAccountTypeToString(SystemAccountType systemAccountType) {
        switch (SystemAccountsSwitchMap.INTS[systemAccountType.ordinal()]) {
            case 1:
                return "AppleGameCenter";
            case 2:
                return "Google";
            case 3:
                return "Facebook";
            case 4:
                return "Apple";
            case 5:
                return "Nintendo";
            case 6:
                return "Huawei";
            case 7:
                return "Sony";
            case 8:
                return "Steam";
            case 9:
                return "Twitch";
            case 10:
                return "Local";
            case 11:
                return "AppleGameCenterVN";
            case 12:
                return "AppleVN";
            default:
                return null;
        }
    }
}
