package com.indai.assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class BottomNav {

    public static final int TAB_CHAT = 0;
    public static final int TAB_MEMORY = 1;
    public static final int TAB_AGENT = 2;
    public static final int TAB_CAPS = 3;

    public static View build(final Activity activity, int activeTab) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int padH = Theme.dp(activity, Theme.S3);
        int padV = Theme.dp(activity, Theme.S2);
        bar.setPadding(padH, padV, padH, padV);
        bar.setBackgroundColor(Theme.BG_ELEVATED);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        bar.addView(tab(activity, TAB_CHAT, activeTab,
                R.drawable.ic_send, "Chat", MainActivity.class));
        bar.addView(tab(activity, TAB_MEMORY, activeTab,
                R.drawable.ic_memory_new, "Memory", MemoryActivity.class));
        bar.addView(tab(activity, TAB_AGENT, activeTab,
                R.drawable.ic_bolt, "Agent", AgentActivity.class));
        // Caps tab removed — accessibility is optional
        // (kept in code for compatibility)

        return bar;
    }

    private static View tab(final Activity activity, final int thisTab, int activeTab,
                            int iconRes, String label, final Class<?> target) {
        boolean active = thisTab == activeTab;

        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(0, Theme.dp(activity, Theme.S1), 0, Theme.dp(activity, Theme.S1));
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView iv = new ImageView(activity);
        iv.setImageResource(iconRes);
        int size = Theme.dp(activity, 20);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(size, size);
        iv.setLayoutParams(ilp);
        iv.setColorFilter(active ? Theme.PRIMARY : Theme.TEXT_TERTIARY);
        wrap.addView(iv);

        TextView tv = new TextView(activity);
        tv.setText(label);
        tv.setTextSize(10.5f);
        tv.setLetterSpacing(0.05f);
        tv.setTypeface(Typeface.create(
                active ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        tv.setTextColor(active ? Theme.PRIMARY : Theme.TEXT_TERTIARY);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Theme.dp(activity, 3);
        wrap.addView(tv, tlp);

        if (!active) {
            wrap.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    v.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    Intent i = new Intent(activity, target);
                    i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    activity.startActivity(i);
                    activity.overridePendingTransition(0, 0);
                }
            });
        } else {
            wrap.setAlpha(1f);
        }

        return wrap;
    }

    private BottomNav() {}
}
