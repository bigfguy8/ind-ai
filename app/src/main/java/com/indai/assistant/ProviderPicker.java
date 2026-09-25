package com.indai.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class ProviderPicker {

    public interface OnPick {
        void onPick(Providers.Provider provider, String apiKey);
    }

    public static void show(final Activity activity, String currentName,
                            String currentKey, final OnPick picker) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(activity, Theme.S3);
        layout.setPadding(pad, pad, pad, pad);

        final AlertDialog[] dialogRef = new AlertDialog[1];

        for (final Providers.Provider p : Providers.ALL) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            int rpH = Theme.dp(activity, Theme.S4);
            int rpV = Theme.dp(activity, Theme.S3);
            row.setPadding(rpH, rpV, rpH, rpV);
            row.setBackground(Drawables.outlined(activity,
                    Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_MD, 1f));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = Theme.dp(activity, Theme.S2);
            row.setLayoutParams(rlp);

            TextView name = new TextView(activity);
            String mark = p.name.equals(currentName) ? "✓  " : "    ";
            StringBuilder tags = new StringBuilder();
            if (p.vision) tags.append("  [vision]");
            if (!p.requiresKey) tags.append("  [no key]");
            name.setText(mark + p.name + tags);
            name.setTextColor(Theme.TEXT_PRIMARY);
            name.setTextSize(Theme.T_BODY);
            name.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.NORMAL));
            row.addView(name);

            TextView ep = new TextView(activity);
            ep.setText("    " + p.defaultModel);
            ep.setTextColor(Theme.TEXT_SECONDARY);
            ep.setTextSize(Theme.T_CAPTION - 1f);
            row.addView(ep);

            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    v.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    if (dialogRef[0] != null) {
                        dialogRef[0].dismiss();
                        dialogRef[0] = null;
                    }
                    if (p.requiresKey) {
                        askForKey(activity, p, picker);
                    } else {
                        picker.onPick(p, "");
                    }
                }
            });

            layout.addView(row);
        }

        ScrollView sw = new ScrollView(activity);
        sw.addView(layout);

        dialogRef[0] = new AlertDialog.Builder(activity)
                .setTitle("Choose provider")
                .setView(sw)
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void askForKey(final Activity activity,
                                  final Providers.Provider provider,
                                  final OnPick picker) {
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(activity, Theme.S5);
        wrap.setPadding(pad, pad, pad, pad);

        TextView label = new TextView(activity);
        label.setText(provider.name + " API key");
        label.setTextColor(Theme.TEXT_PRIMARY);
        label.setTextSize(Theme.T_BODY);
        label.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        wrap.addView(label);

        TextView hint = new TextView(activity);
        hint.setText("Format: " + provider.keyHint);
        hint.setTextColor(Theme.TEXT_SECONDARY);
        hint.setTextSize(Theme.T_CAPTION);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Theme.dp(activity, Theme.S1);
        hlp.bottomMargin = Theme.dp(activity, Theme.S3);
        wrap.addView(hint, hlp);

        final EditText keyField = new EditText(activity);
        keyField.setHint("Paste key");
        keyField.setHintTextColor(Theme.TEXT_TERTIARY);
        keyField.setTextColor(Theme.TEXT_PRIMARY);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setSingleLine(true);
        wrap.addView(keyField);

        new AlertDialog.Builder(activity)
                .setTitle("Enter API key")
                .setView(wrap)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String key = keyField.getText().toString().trim();
                        if (key.isEmpty()) {
                            Toast.makeText(activity, "Key required",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        picker.onPick(provider, key);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private ProviderPicker() {}
}
