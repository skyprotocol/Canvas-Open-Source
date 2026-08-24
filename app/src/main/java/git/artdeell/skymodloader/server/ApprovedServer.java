package git.artdeell.skymodloader.server;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import git.artdeell.skymodloader.R;

public class ApprovedServer {
    public final String id;
    public final String name;
    public final String host;
    @ColorRes
    public final int accentColorRes;
    public final String description;
    @DrawableRes
    public final int iconRes;

    public ApprovedServer(String id, String name, String host, @ColorRes int accentColorRes, String description, @DrawableRes int iconRes) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.accentColorRes = accentColorRes;
        this.description = description;
        this.iconRes = (iconRes != 0) ? iconRes : R.drawable.ic_server;
    }

    public ApprovedServer(String id, String name, String host, @ColorRes int accentColorRes, String description) {
        this(id, name, host, accentColorRes, description, R.drawable.ic_server);
    }

    public ApprovedServer(String id, String name, String host, @ColorRes int accentColorRes) {
        this(id, name, host, accentColorRes, host, R.drawable.ic_server);
    }

    public boolean hasCustomIcon() {
        return iconRes != 0 && iconRes != R.drawable.ic_server;
    }
}
