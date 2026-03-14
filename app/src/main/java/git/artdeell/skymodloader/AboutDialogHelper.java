package git.artdeell.skymodloader;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

public class AboutDialogHelper {

    public static void show(Activity activity) {
        Dialog dialog = new Dialog(activity, R.style.AboutDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.about_dialog);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(
            (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );

        ((TextView) dialog.findViewById(R.id.about_version))
            .setText("v" + BuildConfig.VERSION_NAME);

        LinearLayout timeline = dialog.findViewById(R.id.about_timeline);

        addChapter(activity, timeline, "The Origin", "Built from scratch by two developers who love Sky.", true);
		addPerson(activity, timeline, "artdeell", "Author",  "artdeell",  "https://github.com/artdeell");
        addPerson(activity, timeline, "lukas0x1", "Author · retired", "lukas0x1",  "https://github.com/lukas0x1");
        endChapter(timeline);

        addChapter(activity, timeline, "The Present", "Currently keeping Canvas alive.", true);
        addPerson(activity, timeline, "MisterGatto", "Maintainer", "MisterGatto", "https://github.com/MisterGatto");
        endChapter(timeline);

        addChapter(activity, timeline, "The Team", "Everyone who made Canvas better.", true);
        addPerson(activity, timeline, "RomanChamelo",   null, "RomanChamelo",   "https://github.com/RomanChamelo");
        addPerson(activity, timeline, "Kiojeen",        null, "Kiojeen",        "https://github.com/Kiojeen");
        addPerson(activity, timeline, "Gxosty",         null, "Gxosty",         "https://github.com/gxosty");
        addPerson(activity, timeline, "achcyano",       null, "achcyano",       "https://github.com/achcyano");
        addPerson(activity, timeline, "HugeFrog24",     null, "HugeFrog24",     "https://github.com/HugeFrog24");
        addPerson(activity, timeline, "RadiatedExodus", null, "RadiatedExodus", "https://github.com/RadiatedExodus");
        addPerson(activity, timeline, "HungWah2",       null, "HungWah2",       "https://github.com/HungWah2");
        addPerson(activity, timeline, "alvindimas05",   null, "alvindimas05",   "https://github.com/alvindimas05");
        endChapter(timeline);

        addChapter(activity, timeline, "The Art", "The faces behind the visuals.", true);
        addPersonCustomLink(activity, timeline, "Сон~",         "Banner artwork", "VK ›", "https://vk.com/son583");
        addPersonCustomLink(activity, timeline, "Dysis", "App icon",      "TG ›", "https://t.me/Eliatshaha");
        endChapter(timeline);

        addChapter(activity, timeline, "The Community", "Join thousands of players who use Canvas every day.", false);
		addCommunityCard(activity, timeline, "Discord", "https://discord.gg/FrHP57VRPs",    "https://discord.gg/FrHP57VRPs");
        addCommunityCard(activity, timeline, "English Telegram", "t.me/skyautowax", "https://t.me/skyautowax");
        addCommunityCard(activity, timeline, "Russian Telegram",  "t.me/skyruswax",  "https://t.me/s/ruautowax");
		endChapter(timeline);

        dialog.findViewById(R.id.thatgamecompanyLink).setOnClickListener(v ->
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://thatgamecompany.com"))));
        dialog.findViewById(R.id.dialog_info_close).setOnClickListener(v ->
            dialog.dismiss());

        dialog.show();
    }

    private static void addChapter(Activity a, LinearLayout parent, String title, String subtitle, boolean withLine) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setTag("chapter_row");

        LinearLayout rail = new LinearLayout(a);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setLayoutParams(new LinearLayout.LayoutParams(dp(a, 32), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView dot = new TextView(a);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(a, 12), dp(a, 12));
        dotParams.topMargin = dp(a, 4);
        dot.setLayoutParams(dotParams);
        dot.setBackgroundResource(R.drawable.logo_bg);
        rail.addView(dot);

        if (withLine) {
            View line = new View(a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, 2), 0, 1f);
            lp.topMargin = dp(a, 6);
            line.setLayoutParams(lp);
            line.setBackgroundResource(R.drawable.separator);
            line.setAlpha(0.25f);
            rail.addView(line);
        }
        row.addView(rail);

        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMarginStart(dp(a, 12));
        content.setLayoutParams(cp);

        TextView tv = new TextView(a);
        tv.setText(title);
        tv.setTextSize(16);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tvp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvp.bottomMargin = dp(a, 4);
        tv.setLayoutParams(tvp);
        content.addView(tv);

        TextView sv = new TextView(a);
        sv.setText(subtitle);
        sv.setTextSize(12);
        sv.setAlpha(0.45f);
        sv.setLineSpacing(0, 1.4f);
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        svp.bottomMargin = dp(a, 14);
        sv.setLayoutParams(svp);
        content.addView(sv);

        row.addView(content);
        parent.addView(row);
    }

    private static void endChapter(LinearLayout parent) {
        View last = parent.getChildAt(parent.getChildCount() - 1);
        if (last != null) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) last.getLayoutParams();
            p.bottomMargin = dp(last.getContext(), 28);
            last.setLayoutParams(p);
        }
    }

    private static LinearLayout getCurrentChapterContent(LinearLayout parent) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child instanceof LinearLayout && "chapter_row".equals(child.getTag())) {
                return (LinearLayout) ((LinearLayout) child).getChildAt(1);
            }
        }
        return parent;
    }

    private static void addPerson(Activity a, LinearLayout parent, String name, String role, String githubUser, String githubUrl) {
        LinearLayout content = getCurrentChapterContent(parent);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(a, 10);
        row.setLayoutParams(rp);

        ImageView avatar = new ImageView(a);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(a, 38), dp(a, 38));
        ap.setMarginEnd(dp(a, 12));
        avatar.setLayoutParams(ap);
        avatar.setBackgroundResource(R.drawable.logo_bg);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(a)
            .load("https://github.com/" + githubUser + ".png?size=64")
            .circleCrop()
            .placeholder(R.drawable.logo_bg)
            .error(R.drawable.logo_bg)
            .into(avatar);
        row.addView(avatar);

        LinearLayout tb = new LinearLayout(a);
        tb.setOrientation(LinearLayout.VERTICAL);
        tb.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView nv = new TextView(a);
        nv.setText(name);
        nv.setTextSize(14);
        nv.setTypeface(null, Typeface.BOLD);
        tb.addView(nv);

        if (role != null) {
            TextView rv = new TextView(a);
            rv.setText(role);
            rv.setTextSize(11);
            rv.setAlpha(0.4f);
            tb.addView(rv);
        }
        row.addView(tb);

        TextView link = new TextView(a);
        link.setText("GitHub ›");
        link.setTextSize(12);
        link.setAlpha(0.35f);
        link.setPadding(dp(a, 4), dp(a, 4), dp(a, 4), dp(a, 4));
        link.setClickable(true);
        link.setFocusable(true);
        link.setOnClickListener(v -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))));
        row.addView(link);

        content.addView(row);
    }

    private static void addPersonCustomLink(Activity a, LinearLayout parent, String name, String role, String linkLabel, String linkUrl) {
        LinearLayout content = getCurrentChapterContent(parent);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(a, 10);
        row.setLayoutParams(rp);

        LinearLayout tb = new LinearLayout(a);
        tb.setOrientation(LinearLayout.VERTICAL);
        tb.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView nv = new TextView(a);
        nv.setText(name);
        nv.setTextSize(14);
        nv.setTypeface(null, Typeface.BOLD);
        tb.addView(nv);

        if (role != null) {
            TextView rv = new TextView(a);
            rv.setText(role);
            rv.setTextSize(11);
            rv.setAlpha(0.4f);
            tb.addView(rv);
        }
        row.addView(tb);

        TextView link = new TextView(a);
        link.setText(linkLabel);
        link.setTextSize(12);
        link.setAlpha(0.35f);
        link.setPadding(dp(a, 4), dp(a, 4), dp(a, 4), dp(a, 4));
        link.setClickable(true);
        link.setFocusable(true);
        link.setOnClickListener(v -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl))));
        row.addView(link);

        content.addView(row);
    }

    private static void addCommunityCard(Activity a, LinearLayout parent, String title, String subtitle, String url) {
        LinearLayout content = getCurrentChapterContent(parent);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.buttons);
        int p = dp(a, 14);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(a, 8);
        card.setLayoutParams(cp);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));

        LinearLayout tb = new LinearLayout(a);
        tb.setOrientation(LinearLayout.VERTICAL);
        tb.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tv = new TextView(a);
        tv.setText(title);
        tv.setTextSize(14);
        tv.setTypeface(null, Typeface.BOLD);
        tb.addView(tv);

        TextView sv = new TextView(a);
        sv.setText(subtitle);
        sv.setTextSize(11);
        sv.setAlpha(0.4f);
        tb.addView(sv);

        card.addView(tb);

        TextView arrow = new TextView(a);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setAlpha(0.3f);
        card.addView(arrow);

        content.addView(card);
    }

    private static int dp(android.content.Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
