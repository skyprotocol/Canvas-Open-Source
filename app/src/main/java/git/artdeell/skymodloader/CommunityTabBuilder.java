package git.artdeell.skymodloader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CommunityTabBuilder {

    private static final String TAG = "CommunityTab";

    private static final Object[][] SERVERS = {
        {"ekpUFWcCFN", "https://discord.gg/ekpUFWcCFN", R.string.community_android},
        {"mpytQTuuWR",  "https://discord.gg/mpytQTuuWR", R.string.community_pc}
    };

    private static boolean built = false;

    public static void build(Activity activity, LinearLayout container) {
        if (built && container.getChildCount() > 0) return;
        built = true;
        container.removeAllViews();

        // Title
        TextView title = new TextView(activity);
        title.setText(R.string.community_title);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(activity, 4);
        titleParams.topMargin = dp(activity, 2);
        title.setLayoutParams(titleParams);
        container.addView(title);

        // Subtitle
        TextView subtitle = new TextView(activity);
        subtitle.setText(R.string.community_subtitle);
        subtitle.setTextSize(13);
        subtitle.setAlpha(0.5f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.bottomMargin = dp(activity, 16);
        subtitle.setLayoutParams(subParams);
        container.addView(subtitle);

        // Separator
        View sep = new View(activity);
        LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 1));
        sepParams.setMarginStart(dp(activity, 4));
        sepParams.setMarginEnd(dp(activity, 4));
        sepParams.bottomMargin = dp(activity, 16);
        sep.setLayoutParams(sepParams);
        sep.setBackgroundResource(R.drawable.separator);
        container.addView(sep);

        for (Object[] server : SERVERS) {
            addCategoryLabel(activity, container, (Integer) server[2]);
            addServerCard(activity, container, (String) server[0], (String) server[1]);
        }
    }

    private static void addCategoryLabel(Activity activity, LinearLayout container, int stringResId) {
        TextView label = new TextView(activity);
        label.setText(stringResId);
        label.setTextSize(13);
        label.setTypeface(null, Typeface.BOLD);
        label.setAllCaps(true);
        label.setAlpha(0.6f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(activity, 4));
        params.topMargin = dp(activity, 8);
        params.bottomMargin = dp(activity, 12);
        label.setLayoutParams(params);
        container.addView(label);
    }

    private static void addServerCard(Activity activity, LinearLayout container, String inviteCode, String inviteUrl) {
        int cornerRadius = dp(activity, 12);

        // Card wrapper
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(cornerRadius);
        cardBg.setColor(getWidgetColor(activity));
        card.setBackground(cardBg);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(activity, 16);
        card.setLayoutParams(cardParams);
        card.setClipToOutline(true);

        // Banner + icon overlap section
        FrameLayout bannerSection = new FrameLayout(activity);
        LinearLayout.LayoutParams bannerSectionParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 168));
        bannerSection.setLayoutParams(bannerSectionParams);

        // Banner image
        ImageView banner = new ImageView(activity);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(activity, 140));
        banner.setLayoutParams(bannerParams);
        banner.setBackgroundColor(0xFF2F3136); // Discord dark fallback
        bannerSection.addView(banner);

        // Server icon (positioned at bottom of banner area, overlapping)
        ImageView icon = new ImageView(activity);
        int iconSize = dp(activity, 56);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginStart(dp(activity, 14));
        iconParams.topMargin = dp(activity, 112); // 140 - 28 = sits at bottom of banner, half below
        icon.setLayoutParams(iconParams);
        GradientDrawable iconBorder = new GradientDrawable();
        iconBorder.setShape(GradientDrawable.OVAL);
        iconBorder.setColor(getWidgetColor(activity));
        iconBorder.setStroke(dp(activity, 3), getWidgetColor(activity));
        icon.setBackground(iconBorder);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setClipToOutline(true);
        bannerSection.addView(icon);

        card.addView(bannerSection);

        // Info section (name, description, counts, join button)
        LinearLayout infoSection = new LinearLayout(activity);
        infoSection.setOrientation(LinearLayout.VERTICAL);
        int infoPad = dp(activity, 14);
        infoSection.setPadding(infoPad, dp(activity, 4), infoPad, infoPad);

        // Server name
        TextView nameView = new TextView(activity);
        nameView.setText(R.string.loading_server);
        nameView.setTextSize(17);
        nameView.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.bottomMargin = dp(activity, 6);
        nameView.setLayoutParams(nameParams);
        infoSection.addView(nameView);

        // Server description
        TextView descView = new TextView(activity);
        descView.setTextSize(12);
        descView.setAlpha(0.55f);
        descView.setVisibility(View.GONE);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.bottomMargin = dp(activity, 8);
        descView.setLayoutParams(descParams);
        infoSection.addView(descView);

        // Counts row
        LinearLayout countsRow = new LinearLayout(activity);
        countsRow.setOrientation(LinearLayout.HORIZONTAL);
        countsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams countsParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countsParams.bottomMargin = dp(activity, 14);
        countsRow.setLayoutParams(countsParams);

        // Online dot + count
        View onlineDot = new View(activity);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(activity.getColor(R.color.discord_online_green));
        onlineDot.setBackground(dotBg);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
            dp(activity, 8), dp(activity, 8));
        dotParams.setMarginEnd(dp(activity, 4));
        onlineDot.setLayoutParams(dotParams);
        countsRow.addView(onlineDot);

        TextView onlineCount = new TextView(activity);
        onlineCount.setText("—");
        onlineCount.setTextSize(12);
        onlineCount.setAlpha(0.55f);
        LinearLayout.LayoutParams onlineParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        onlineParams.setMarginEnd(dp(activity, 14));
        onlineCount.setLayoutParams(onlineParams);
        countsRow.addView(onlineCount);

        // Members dot + count
        View memberDot = new View(activity);
        GradientDrawable memberDotBg = new GradientDrawable();
        memberDotBg.setShape(GradientDrawable.OVAL);
        memberDotBg.setColor(0xFF747F8D);
        memberDot.setBackground(memberDotBg);
        LinearLayout.LayoutParams memberDotParams = new LinearLayout.LayoutParams(
            dp(activity, 8), dp(activity, 8));
        memberDotParams.setMarginEnd(dp(activity, 4));
        memberDot.setLayoutParams(memberDotParams);
        countsRow.addView(memberDot);

        TextView memberCount = new TextView(activity);
        memberCount.setText("—");
        memberCount.setTextSize(12);
        memberCount.setAlpha(0.55f);
        countsRow.addView(memberCount);

        infoSection.addView(countsRow);

        // Join button
        TextView joinBtn = new TextView(activity);
        joinBtn.setText(R.string.join_server);
        joinBtn.setTextSize(14);
        joinBtn.setTypeface(null, Typeface.BOLD);
        joinBtn.setTextColor(Color.WHITE);
        joinBtn.setGravity(Gravity.CENTER);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(dp(activity, 8));
        btnBg.setColor(activity.getColor(R.color.discord_blurple));
        joinBtn.setBackground(btnBg);
        int btnPadH = dp(activity, 16);
        int btnPadV = dp(activity, 12);
        joinBtn.setPadding(btnPadH, btnPadV, btnPadH, btnPadV);
        joinBtn.setClickable(true);
        joinBtn.setFocusable(true);
        joinBtn.setOnClickListener(v ->
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(inviteUrl)))
        );
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        joinBtn.setLayoutParams(btnParams);
        infoSection.addView(joinBtn);

        card.addView(infoSection);
        container.addView(card);

        // Fetch server data in background
        fetchServerData(activity, inviteCode, nameView, descView, onlineCount, memberCount, banner, icon);
    }

    private static void fetchServerData(Activity activity, String inviteCode,
            TextView nameView, TextView descView, TextView onlineCount, TextView memberCount,
            ImageView banner, ImageView icon) {

        new Thread(() -> {
            try {
                String apiUrl = "https://discord.com/api/v9/invites/" + inviteCode
                    + "?with_counts=true&with_expiration=true";
                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    Log.e(TAG, "Discord API returned " + responseCode + " for " + inviteCode);
                    activity.runOnUiThread(() -> nameView.setText(R.string.error_loading_server));
                    return;
                }

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONObject guild = json.getJSONObject("guild");

                String name = guild.getString("name");
                String guildId = guild.getString("id");
                String iconHash = guild.optString("icon", null);
                String splashHash = guild.optString("splash", null);
                String bannerHash = guild.optString("banner", null);
                String description = guild.optString("description", null);
                int approxOnline = json.optInt("approximate_presence_count", 0);
                int approxMembers = json.optInt("approximate_member_count", 0);

                activity.runOnUiThread(() -> {
                    nameView.setText(name);
                    onlineCount.setText(activity.getString(R.string.online_count, formatCount(approxOnline)));

                    // Set description
                    if (description != null && !description.isEmpty() && !description.equals("null")) {
                        descView.setText(description);
                        descView.setVisibility(View.VISIBLE);
                    }
                    memberCount.setText(activity.getString(R.string.members_count, formatCount(approxMembers)));

                    // Load banner
                    String bannerUrl = null;
                    if (bannerHash != null && !bannerHash.isEmpty() && !bannerHash.equals("null")) {
                        bannerUrl = "https://cdn.discordapp.com/banners/" + guildId + "/" + bannerHash + ".png?size=512";
                    } else if (splashHash != null && !splashHash.isEmpty() && !splashHash.equals("null")) {
                        bannerUrl = "https://cdn.discordapp.com/splashes/" + guildId + "/" + splashHash + ".png?size=512";
                    }

                    if (bannerUrl != null) {
                        Glide.with(activity)
                            .load(bannerUrl)
                            .transform(new CenterCrop())
                            .into(banner);
                    }

                    // Load icon
                    if (iconHash != null && !iconHash.isEmpty() && !iconHash.equals("null")) {
                        String iconUrl = "https://cdn.discordapp.com/icons/" + guildId + "/" + iconHash + ".png?size=128";
                        Glide.with(activity)
                            .load(iconUrl)
                            .circleCrop()
                            .placeholder(R.drawable.logo_bg)
                            .into(icon);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch Discord data for " + inviteCode, e);
                activity.runOnUiThread(() -> nameView.setText(R.string.error_loading_server));
            }
        }).start();
    }

    private static String formatCount(int count) {
        if (count >= 1000) {
            return String.format("%.1fk", count / 1000.0);
        }
        return String.valueOf(count);
    }

    private static int getWidgetColor(Activity activity) {
        return activity.getColor(R.color.widgets);
    }

    private static int dp(android.content.Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
