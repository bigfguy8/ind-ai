package com.indai.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public final class ModelPicker {

    public interface OnPick {
        void onPick(String model);
    }

    public static void show(final Activity activity, final ApiConfig config,
                            final OnPick picker) {
        // Try to find models from the current provider
        Providers.Provider provider =
                Providers.findByName(config.primaryProviderName);
        if (provider == null) {
            provider = Providers.findByEndpoint(config.endpoint);
        }

        final String[] options;
        if (provider != null && provider.models.length > 0) {
            // Add "Custom..." at the end
            options = new String[provider.models.length + 1];
            System.arraycopy(provider.models, 0, options, 0, provider.models.length);
            options[provider.models.length] = "Custom...";
        } else {
            options = new String[] {
                "gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo",
                "llama-3.1-8b-instant", "llama-3.3-70b-versatile",
                "gemini-2.0-flash", "mistral-small-latest", "Custom..."
            };
        }

        // Show checkmark on current model
        final String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = options[i].equals(config.model)
                    ? "✓  " + options[i]
                    : "    " + options[i];
        }

        final String providerName = provider != null ? provider.name : "current endpoint";

        new AlertDialog.Builder(activity)
                .setTitle("Model  ·  " + providerName)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == options.length - 1) {
                            promptCustom(activity, config.model, picker);
                        } else {
                            picker.onPick(options[which]);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void promptCustom(final Activity activity, String current,
                                     final OnPick picker) {
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(activity, Theme.S5);
        wrap.setPadding(pad, pad, pad, pad);

        final EditText input = new EditText(activity);
        input.setHint("Model name");
        input.setHintTextColor(Theme.TEXT_TERTIARY);
        input.setTextColor(Theme.TEXT_PRIMARY);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(current);
        wrap.addView(input);

        new AlertDialog.Builder(activity)
                .setTitle("Custom model")
                .setView(wrap)
                .setPositiveButton("Use", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String m = input.getText().toString().trim();
                        if (m.isEmpty()) {
                            Toast.makeText(activity, "Empty model name",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        picker.onPick(m);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private ModelPicker() {}
}
