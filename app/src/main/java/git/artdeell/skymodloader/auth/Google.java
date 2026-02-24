package git.artdeell.skymodloader.auth;

import com.tgc.sky.accounts.SystemAccountType;



public class Google extends WebLogin {
    public Google() {
        super("Google", SystemAccountType.kSystemAccountType_Google); }
}