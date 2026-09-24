package com.nova.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class FirstRunSetup {

    public interface OnReady {
        void onReady();
    }

    public static boolean needsSetup(ApiConfig config) {
        return config.apiKey == null || config.apiKey.trim().isEmpty();
    }

    public static void show(final Activity activity, final OnReady onReady) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(activity, Theme.S5);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(activity);
        title.setText("Welcome to Ind AI");
        title.setTextColor(Theme.TEXT_PRIMARY);
        title.setTextSize(Theme.T_DISPLAY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        layout.addView(title);

        TextView sub = new TextView(activity);
        sub.setText("Pick a free AI provider to get started. All options listed are free "
                + "with generous daily limits. You can switch providers any time in Settings.");
        sub.setTextColor(Theme.TEXT_SECONDARY);
        sub.setTextSize(Theme.T_CAPTION);
        sub.setLineSpacing(Theme.dp(activity, 3), 1f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Theme.dp(activity, Theme.S2);
        slp.bottomMargin = Theme.dp(activity, Theme.S5);
        sub.setLayoutParams(slp);
        layout.addView(sub);

        final AlertDialog[] dialogRef = new AlertDialog[1];

        TextView start = new TextView(activity);
        start.setText("  Choose a provider");
        start.setTextColor(Theme.ON_PRIMARY);
        start.setTextSize(Theme.T_BODY);
        start.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        start.setGravity(Gravity.CENTER);
        int btnPad = Theme.dp(activity, Theme.S4);
        start.setPadding(btnPad, btnPad, btnPad, btnPad);
        start.setBackground(Drawables.rounded(activity, Theme.PRIMARY, Theme.R_PILL));
        start.setOnClickListener(v -> {
            ProviderPicker.show(activity, "", "", new ProviderPicker.OnPick() {
                @Override public void onPick(Providers.Provider provider, String apiKey) {
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    SecureKeyStore ks = new SecureKeyStore(activity);
                    ApiConfig cfg = ks.load();
                    ApiConfig updated = new ApiConfig(
                            provider.endpoint, apiKey, provider.defaultModel,
                            cfg.systemPrompt, cfg.visionModel,
                            cfg.fallbackEndpoint, cfg.fallbackApiKey, cfg.fallbackModel,
                            cfg.visionEndpoint, cfg.visionApiKey,
                            provider.name, cfg.fallbackProviderName, cfg.visionProviderName);
                    ks.save(updated);
                    onReady.onReady();
                }
            });
        });
        layout.addView(start);

        TextView skip = new TextView(activity);
        skip.setText("Set up later in Settings");
        skip.setTextColor(Theme.TEXT_TERTIARY);
        skip.setTextSize(Theme.T_CAPTION);
        skip.setGravity(Gravity.CENTER);
        int skipPad = Theme.dp(activity, Theme.S3);
        skip.setPadding(0, skipPad, 0, 0);
        skip.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        layout.addView(skip);

        ScrollView sw = new ScrollView(activity);
        sw.addView(layout);

        dialogRef[0] = new AlertDialog.Builder(activity)
                .setView(sw)
                .setCancelable(false)
                .show();
    }

    private FirstRunSetup() {}
}
