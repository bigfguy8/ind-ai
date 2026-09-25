package com.indai.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class QuickSwitch {

    public interface Callback {
        void onConfigChanged(ApiConfig newConfig);
        void onOpenSettings();
    }

    public static void show(final Activity activity,
                            final ApiConfig config,
                            final Callback cb) {
        final AlertDialog[] dialogRef = new AlertDialog[1];

        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(activity, Theme.S5);
        wrap.setPadding(pad, pad, pad, pad);

        // ---- Header pill: current provider + model ----
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        int hpH = Theme.dp(activity, Theme.S4);
        int hpV = Theme.dp(activity, Theme.S3);
        header.setPadding(hpH, hpV, hpH, hpV);
        header.setBackground(Drawables.outlined(activity,
                Theme.SURFACE_HIGH, Theme.SURFACE_STROKE, Theme.R_LG, 1f));

        TextView providerLine = new TextView(activity);
        providerLine.setText("PROVIDER");
        providerLine.setTextSize(Theme.T_LABEL);
        providerLine.setTextColor(Theme.TEXT_SECONDARY);
        providerLine.setLetterSpacing(0.12f);
        header.addView(providerLine);

        String providerName = resolveProviderName(config);
        TextView providerValue = new TextView(activity);
        providerValue.setText(providerName);
        providerValue.setTextSize(Theme.T_TITLE);
        providerValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        providerValue.setTextColor(Theme.TEXT_PRIMARY);
        LinearLayout.LayoutParams pvlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pvlp.topMargin = Theme.dp(activity, Theme.S1);
        header.addView(providerValue, pvlp);

        TextView modelLine = new TextView(activity);
        modelLine.setText("MODEL");
        modelLine.setTextSize(Theme.T_LABEL);
        modelLine.setTextColor(Theme.TEXT_SECONDARY);
        modelLine.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams mllp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mllp.topMargin = Theme.dp(activity, Theme.S3);
        header.addView(modelLine, mllp);

        TextView modelValue = new TextView(activity);
        modelValue.setText(config.model);
        modelValue.setTextSize(Theme.T_BODY);
        modelValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        modelValue.setTextColor(Theme.PRIMARY);
        LinearLayout.LayoutParams mvlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mvlp.topMargin = Theme.dp(activity, Theme.S1);
        header.addView(modelValue, mvlp);

        wrap.addView(header);

        // ---- Actions row: Switch provider / Switch model ----
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actLp.topMargin = Theme.dp(activity, Theme.S3);
        wrap.addView(actions, actLp);

        // Switch provider button
        TextView btnProvider = new TextView(activity);
        btnProvider.setText("Switch provider");
        btnProvider.setTextColor(Theme.ON_PRIMARY);
        btnProvider.setTextSize(Theme.T_CAPTION);
        btnProvider.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btnProvider.setGravity(Gravity.CENTER);
        int bpH = Theme.dp(activity, Theme.S3);
        int bpV = Theme.dp(activity, Theme.S3);
        btnProvider.setPadding(bpH, bpV, bpH, bpV);
        btnProvider.setBackground(Drawables.rounded(activity, Theme.PRIMARY, Theme.R_PILL));
        LinearLayout.LayoutParams bplp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        actions.addView(btnProvider, bplp);

        // Switch model button
        TextView btnModel = new TextView(activity);
        btnModel.setText("Switch model");
        btnModel.setTextColor(Theme.PRIMARY);
        btnModel.setTextSize(Theme.T_CAPTION);
        btnModel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btnModel.setGravity(Gravity.CENTER);
        btnModel.setPadding(bpH, bpV, bpH, bpV);
        btnModel.setBackground(Drawables.outlined(activity,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_PILL, 1f));
        LinearLayout.LayoutParams bmlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bmlp.leftMargin = Theme.dp(activity, Theme.S2);
        actions.addView(btnModel, bmlp);

        // ---- Quick model list from current provider ----
        Providers.Provider provider = Providers.findByName(config.primaryProviderName);
        if (provider == null) provider = Providers.findByEndpoint(config.endpoint);

        if (provider != null && provider.models.length > 0) {
            TextView quickLabel = new TextView(activity);
            quickLabel.setText("QUICK MODELS — " + provider.name.toUpperCase());
            quickLabel.setTextColor(Theme.TEXT_SECONDARY);
            quickLabel.setTextSize(Theme.T_LABEL);
            quickLabel.setLetterSpacing(0.12f);
            LinearLayout.LayoutParams qllp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            qllp.topMargin = Theme.dp(activity, Theme.S5);
            qllp.bottomMargin = Theme.dp(activity, Theme.S2);
            wrap.addView(quickLabel, qllp);

            for (final String m : provider.models) {
                TextView row = new TextView(activity);
                boolean active = m.equals(config.model);
                row.setText(active ? "✓  " + m : "    " + m);
                row.setTextColor(active ? Theme.PRIMARY : Theme.TEXT_PRIMARY);
                row.setTextSize(Theme.T_BODY);
                row.setPadding(Theme.dp(activity, Theme.S4),
                        Theme.dp(activity, Theme.S3),
                        Theme.dp(activity, Theme.S4),
                        Theme.dp(activity, Theme.S3));
                row.setBackground(Drawables.outlined(activity,
                        Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_MD, 1f));
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = Theme.dp(activity, Theme.S2);
                row.setLayoutParams(rlp);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        v.performHapticFeedback(
                                android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                        ApiConfig updated = new ApiConfig(
                                config.endpoint, config.apiKey, m,
                                config.systemPrompt, config.visionModel,
                                config.fallbackEndpoint, config.fallbackApiKey,
                                config.fallbackModel,
                                config.visionEndpoint, config.visionApiKey,
                                config.primaryProviderName,
                                config.fallbackProviderName,
                                config.visionProviderName);
                        cb.onConfigChanged(updated);
                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                    }
                });
                wrap.addView(row);
            }
        }

        // ---- Explicit Settings row at the bottom ----
        TextView settingsRow = new TextView(activity);
        settingsRow.setText("⚙  Open full settings");
        settingsRow.setTextColor(Theme.PRIMARY);
        settingsRow.setTextSize(Theme.T_BODY);
        settingsRow.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int srpH = Theme.dp(activity, Theme.S4);
        int srpV = Theme.dp(activity, Theme.S3);
        settingsRow.setPadding(srpH, srpV, srpH, srpV);
        settingsRow.setGravity(Gravity.CENTER);
        settingsRow.setBackground(Drawables.outlined(activity,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_PILL, 1f));
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        srlp.topMargin = Theme.dp(activity, Theme.S5);
        settingsRow.setLayoutParams(srlp);
        settingsRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                cb.onOpenSettings();
            }
        });
        wrap.addView(settingsRow);

        ScrollView sw = new ScrollView(activity);
        sw.addView(wrap);

        dialogRef[0] = new AlertDialog.Builder(activity)
                .setTitle("Quick switch")
                .setView(sw)
                .setNegativeButton("Close", null)
                .create();

        btnProvider.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                ProviderPicker.show(activity, config.primaryProviderName,
                        config.apiKey, new ProviderPicker.OnPick() {
                    @Override public void onPick(Providers.Provider provider,
                                                String apiKey) {
                        ApiConfig updated = new ApiConfig(
                                provider.endpoint, apiKey, provider.defaultModel,
                                config.systemPrompt, config.visionModel,
                                config.fallbackEndpoint, config.fallbackApiKey,
                                config.fallbackModel,
                                config.visionEndpoint, config.visionApiKey,
                                provider.name,
                                config.fallbackProviderName,
                                config.visionProviderName);
                        cb.onConfigChanged(updated);
                    }
                });
            }
        });

        btnModel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                ModelPicker.show(activity, config, new ModelPicker.OnPick() {
                    @Override public void onPick(String model) {
                        ApiConfig updated = new ApiConfig(
                                config.endpoint, config.apiKey, model,
                                config.systemPrompt, config.visionModel,
                                config.fallbackEndpoint, config.fallbackApiKey,
                                config.fallbackModel,
                                config.visionEndpoint, config.visionApiKey,
                                config.primaryProviderName,
                                config.fallbackProviderName,
                                config.visionProviderName);
                        cb.onConfigChanged(updated);
                    }
                });
            }
        });

        dialogRef[0].show();
    }

    private static String resolveProviderName(ApiConfig config) {
        if (config.primaryProviderName != null
                && !config.primaryProviderName.isEmpty()) {
            return config.primaryProviderName;
        }
        Providers.Provider p = Providers.findByEndpoint(config.endpoint);
        if (p != null) return p.name;
        return "(custom)";
    }

    private QuickSwitch() {}
}
