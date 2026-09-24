package com.nova.assistant;

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

    private static final String[] PRESETS = {
        "agnes-3.0-flash",
        "agnes-2.5-flash",
        "agnes-2.5-pro",
        "gpt-4o-mini",
        "gpt-4o",
        "claude-3-5-sonnet-20241022"
    };

    public static void show(final Activity activity, final String current,
                            final OnPick picker) {
        final String[] labels = new String[PRESETS.length + 1];
        for (int i = 0; i < PRESETS.length; i++) {
            labels[i] = PRESETS[i].equals(current)
                    ? "✓  " + PRESETS[i]
                    : "    " + PRESETS[i];
        }
        labels[PRESETS.length] = "Custom...";

        new AlertDialog.Builder(activity)
                .setTitle("Model")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == PRESETS.length) {
                            promptCustom(activity, current, picker);
                        } else {
                            picker.onPick(PRESETS[which]);
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
