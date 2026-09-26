package com.indai.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AccessibilityOnboarding {

    private static final String PREFS = "indai_onboarding";
    private static final String KEY_ASKED = "acc_asked";

    /** Should we show the accessibility prompt right now? */
    public static boolean shouldPrompt(Activity a) {
        if (IndAIAccessibilityService.isRunning()) return false;
        SharedPreferences p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean(KEY_ASKED, false)) return false;
        return true;
    }

    /** Mark as asked — never prompt again unless reset. */
    public static void markAsked(Activity a) {
        a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ASKED, true).apply();
    }

    public static void resetAsked(Activity a) {
        a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_ASKED).apply();
    }

    /** Show the friendly optional-accessibility modal. */
    public static void show(final Activity activity) {
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(activity, Theme.S5);
        wrap.setPadding(pad, pad, pad, pad);

        // Title row
        TextView title = new TextView(activity);
        title.setText("Optional: Screen Control");
        title.setTextColor(Theme.TEXT_PRIMARY);
        title.setTextSize(Theme.T_TITLE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        wrap.addView(title);

        // Explanation
        TextView body = new TextView(activity);
        body.setText(
                "Ind AI already works without this. Chat, voice, memory, alarms, "
                + "SMS, calls, maps, music, and reminders all run fine.\n\n"
                + "Enable Accessibility only if you want Ind AI to also tap buttons, "
                + "type text, scroll, and read the screen on your behalf.\n\n"
                + "Android does not allow apps to enable this automatically. "
                + "You will be taken to system settings where you tap Ind AI and flip "
                + "the switch. That is the whole setup — about 10 seconds.");
        body.setTextColor(Theme.TEXT_SECONDARY);
        body.setTextSize(Theme.T_CAPTION);
        body.setLineSpacing(Theme.dp(activity, 4), 1f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = Theme.dp(activity, Theme.S3);
        wrap.addView(body, bodyLp);

        // Enable button
        TextView enable = new TextView(activity);
        enable.setText("Open accessibility settings");
        enable.setTextColor(Theme.ON_PRIMARY);
        enable.setTextSize(Theme.T_BODY);
        enable.setGravity(Gravity.CENTER);
        enable.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int bpH = Theme.dp(activity, Theme.S4);
        int bpV = Theme.dp(activity, Theme.S4);
        enable.setPadding(bpH, bpV, bpH, bpV);
        enable.setBackground(Drawables.rounded(activity, Theme.PRIMARY, Theme.R_PILL));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = Theme.dp(activity, Theme.S5);
        wrap.addView(enable, elp);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(wrap)
                .setCancelable(true)
                .setNegativeButton("Not now", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        markAsked(activity);
                    }
                })
                .create();

        enable.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                markAsked(activity);
                openAccessibilitySettings(activity);
                if (dialog != null) dialog.dismiss();
            }
        });

        dialog.show();
    }

    /** Opens the system accessibility settings page. */
    public static void openAccessibilitySettings(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Exception ignored) {}
        }
    }

    private AccessibilityOnboarding() {}
}
