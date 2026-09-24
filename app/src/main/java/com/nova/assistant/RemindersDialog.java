package com.nova.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class RemindersDialog {

    public static void show(final Activity activity) {
        render(activity);
    }

    private static void render(final Activity activity) {
        final List<ReminderScheduler.Item> items = ReminderScheduler.list(activity);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(Theme.dp(activity, Theme.S5), Theme.dp(activity, Theme.S3),
                Theme.dp(activity, Theme.S5), Theme.dp(activity, Theme.S3));

        if (items.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("No reminders scheduled.\n\nAsk Ind AI something like:\n\"remind me to call mom at 6pm\"");
            empty.setTextColor(Theme.TEXT_SECONDARY);
            empty.setTextSize(Theme.T_CAPTION);
            empty.setLineSpacing(Theme.dp(activity, 3), 1f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Theme.dp(activity, Theme.S6), 0, Theme.dp(activity, Theme.S6));
            layout.addView(empty);
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());
            for (final ReminderScheduler.Item it : items) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = Theme.dp(activity, Theme.S3);
                row.setLayoutParams(rlp);

                LinearLayout textCol = new LinearLayout(activity);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView when = new TextView(activity);
                when.setText(fmt.format(new Date(it.atMillis)));
                when.setTextSize(Theme.T_CAPTION);
                when.setTextColor(Theme.PRIMARY);
                when.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                textCol.addView(when);

                TextView what = new TextView(activity);
                what.setText(it.text);
                what.setTextSize(Theme.T_BODY);
                what.setTextColor(Theme.TEXT_PRIMARY);
                what.setLineSpacing(Theme.dp(activity, 2), 1f);
                LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                wlp.topMargin = Theme.dp(activity, Theme.S1);
                textCol.addView(what, wlp);

                row.addView(textCol);

                TextView cancel = new TextView(activity);
                cancel.setText("Cancel");
                cancel.setTextSize(Theme.T_CAPTION);
                cancel.setTextColor(Theme.ERROR);
                cancel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                cancel.setPadding(Theme.dp(activity, Theme.S3),
                        Theme.dp(activity, Theme.S2),
                        Theme.dp(activity, Theme.S2),
                        Theme.dp(activity, Theme.S2));
                cancel.setBackground(Drawables.rounded(activity,
                        0x20FF5252, Theme.R_PILL));
                cancel.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                        ReminderScheduler.cancel(activity, it.id);
                    }
                });
                row.addView(cancel);

                layout.addView(row);
            }
        }

        ScrollView sw = new ScrollView(activity);
        sw.addView(layout);

        new AlertDialog.Builder(activity)
                .setTitle("Reminders")
                .setView(sw)
                .setPositiveButton("Close", null)
                .show();
    }

    private RemindersDialog() {}
}
