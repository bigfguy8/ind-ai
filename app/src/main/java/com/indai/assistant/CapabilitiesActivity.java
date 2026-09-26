package com.indai.assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class CapabilitiesActivity extends Activity {

    private LinearLayout rows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setFitsSystemWindows(true);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        int padH = Theme.dp(this, Theme.S5);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(padH, Theme.dp(this, Theme.S5),
                padH, Theme.dp(this, Theme.S3));

        FrameLayout back = iconButton(R.drawable.ic_clear, v -> finish());
        header.addView(back);

        TextView title = new TextView(this);
        title.setText("Capabilities");
        title.setTextColor(Theme.TEXT_PRIMARY);
        title.setTextSize(Theme.T_TITLE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Theme.dp(this, Theme.S3);
        header.addView(title, tlp);

        column.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(padH, Theme.dp(this, Theme.S2),
                padH, Theme.dp(this, Theme.S5));
        scroll.addView(rows, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        column.addView(BottomNav.build(this, BottomNav.TAB_CAPS));

        setContentView(root);
    }

    private void refresh() {
        rows.removeAllViews();

        addRow("Accessibility Service",
                PermissionCenter.accessibilityEnabled(this) ? "Enabled" : "Disabled",
                "Lets Ind AI tap buttons, type, scroll and read visible text on your "
                        + "instruction. Ind AI never reads passwords and never bypasses "
                        + "Android security prompts.",
                PermissionCenter.accessibilityEnabled(this) ? null
                        : v -> PermissionCenter.openAccessibilitySettings(this));

        addRow("Microphone",
                PermissionCenter.micGranted(this) ? "Granted" : "Denied",
                "Required for voice input. Audio is only sent to your configured "
                        + "speech provider when you tap the mic.",
                PermissionCenter.micGranted(this) ? null
                        : v -> requestPermissions(
                                new String[]{android.Manifest.permission.RECORD_AUDIO},
                                PermissionCenter.REQ_MIC));

        addRow("Notifications",
                PermissionCenter.notificationsGranted(this) ? "Granted" : "Denied",
                "Optional. Used only for informational notices from Ind AI.",
                PermissionCenter.notificationsGranted(this) ? null
                        : v -> {
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                requestPermissions(
                                        new String[]{"android.permission.POST_NOTIFICATIONS"},
                                        1002);
                            } else {
                                PermissionCenter.openAppDetails(this);
                            }
                        });

        addRow("App details",
                "Open",
                "All Android permissions Ind AI currently holds can be reviewed "
                        + "or revoked in system settings.",
                v -> PermissionCenter.openAppDetails(this));
    }

    private void addRow(String title, String status, String desc, View.OnClickListener action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Theme.dp(this, Theme.S4), Theme.dp(this, Theme.S4),
                Theme.dp(this, Theme.S4), Theme.dp(this, Theme.S4));
        card.setBackground(Drawables.outlined(this,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_LG, 1f));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = Theme.dp(this, Theme.S3);
        card.setLayoutParams(clp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Theme.TEXT_PRIMARY);
        t.setTextSize(Theme.T_TITLE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(t);

        TextView s = new TextView(this);
        s.setText(status);
        s.setTextSize(Theme.T_CAPTION);
        s.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        s.setTextColor(statusColor(status));
        s.setPadding(Theme.dp(this, Theme.S3), Theme.dp(this, Theme.S1),
                Theme.dp(this, Theme.S3), Theme.dp(this, Theme.S1));
        s.setBackground(Drawables.rounded(this,
                statusBg(status), Theme.R_PILL));
        top.addView(s);

        card.addView(top);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(Theme.TEXT_SECONDARY);
        d.setTextSize(Theme.T_CAPTION);
        d.setLineSpacing(Theme.dp(this, 2), 1f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Theme.dp(this, Theme.S3);
        card.addView(d, dlp);

        if (action != null) {
            TextView btn = new TextView(this);
            btn.setText("Open settings");
            btn.setTextColor(Theme.PRIMARY);
            btn.setTextSize(Theme.T_BODY);
            btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            btn.setPadding(0, Theme.dp(this, Theme.S4), 0, Theme.dp(this, Theme.S1));
            btn.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                action.onClick(v);
            });
            card.addView(btn);
        }

        rows.addView(card);
    }

    private int statusColor(String s) {
        if ("Enabled".equals(s) || "Granted".equals(s) || "Available".equals(s)) {
            return Theme.SUCCESS;
        }
        if ("Open".equals(s)) return Theme.PRIMARY;
        return Theme.ERROR;
    }

    private int statusBg(String s) {
        if ("Enabled".equals(s) || "Granted".equals(s) || "Available".equals(s)) {
            return 0x204ADE80;
        }
        if ("Open".equals(s)) return Theme.PRIMARY_SOFT;
        return 0x20FF5252;
    }

    private FrameLayout iconButton(int res, View.OnClickListener click) {
        FrameLayout wrap = new FrameLayout(this);
        int size = Theme.dp(this, 40);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        wrap.setBackground(Drawables.tappable(this, Theme.SURFACE, Theme.R_PILL));

        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        int iSize = Theme.dp(this, 18);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iSize, iSize);
        lp.gravity = Gravity.CENTER;
        wrap.addView(iv, lp);
        iv.setColorFilter(Theme.TEXT_SECONDARY);

        wrap.setOnClickListener(click);
        return wrap;
    }
}
