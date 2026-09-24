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

public final class AgentHistoryDialog {

    public static void show(final Activity activity) {
        AgentHistoryStore store = new AgentHistoryStore(activity);
        final List<String> raw = store.rawList();

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(activity, Theme.S4);
        layout.setPadding(pad, pad, pad, pad);

        if (raw.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("No agent runs yet.\n\nGo to the Agent tab and give it a goal.");
            empty.setTextColor(Theme.TEXT_SECONDARY);
            empty.setTextSize(Theme.T_CAPTION);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(Theme.dp(activity, 3), 1f);
            empty.setPadding(0, Theme.dp(activity, Theme.S6),
                    0, Theme.dp(activity, Theme.S6));
            layout.addView(empty);
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());
            for (final String json : raw) {
                Plan plan = Plan.fromJson(json);
                if (plan == null) continue;

                TextView row = new TextView(activity);
                String status = plan.status == Plan.OverallStatus.DONE ? "✓"
                        : plan.status == Plan.OverallStatus.CANCELLED ? "⊘"
                        : "✗";
                String when = fmt.format(new Date(plan.createdAt));
                row.setText(status + "  " + plan.goal + "\n"
                        + when + " · " + plan.completedCount() + "/" + plan.size()
                        + " steps");
                row.setTextColor(Theme.TEXT_PRIMARY);
                row.setTextSize(Theme.T_BODY);
                row.setLineSpacing(Theme.dp(activity, 2), 1f);
                int rpH = Theme.dp(activity, Theme.S3);
                int rpV = Theme.dp(activity, Theme.S3);
                row.setPadding(rpH, rpV, rpH, rpV);
                row.setBackground(Drawables.outlined(activity,
                        Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_MD, 1f));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = Theme.dp(activity, Theme.S2);
                row.setLayoutParams(lp);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        showRunDetail(activity, json);
                    }
                });
                layout.addView(row);
            }
        }

        ScrollView sw = new ScrollView(activity);
        sw.addView(layout);

        new AlertDialog.Builder(activity)
                .setTitle("Agent history")
                .setView(sw)
                .setNeutralButton("Clear", (d, w) -> store.clear())
                .setNegativeButton("Close", null)
                .show();
    }

    private static void showRunDetail(final Activity activity, String json) {
        Plan plan = Plan.fromJson(json);
        if (plan == null) return;

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(activity, Theme.S4);
        layout.setPadding(pad, pad, pad, pad);

        TextView goal = new TextView(activity);
        goal.setText("Goal\n" + plan.goal);
        goal.setTextColor(Theme.TEXT_PRIMARY);
        goal.setTextSize(Theme.T_BODY);
        goal.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        goal.setLineSpacing(Theme.dp(activity, 2), 1f);
        goal.setPadding(0, 0, 0, Theme.dp(activity, Theme.S3));
        layout.addView(goal);

        for (Plan.Step s : plan.steps) {
            String mark = s.status == Plan.StepStatus.DONE ? "✓"
                    : s.status == Plan.StepStatus.FAILED ? "✗"
                    : s.status == Plan.StepStatus.SKIPPED ? "–"
                    : "○";
            TextView step = new TextView(activity);
            step.setText(mark + "  " + s.description);
            step.setTextColor(s.status == Plan.StepStatus.FAILED
                    ? Theme.ERROR : Theme.TEXT_PRIMARY);
            step.setTextSize(Theme.T_BODY - 0.5f);
            step.setPadding(0, Theme.dp(activity, Theme.S1),
                    0, Theme.dp(activity, Theme.S1));
            layout.addView(step);

            if (s.result != null && !s.result.isEmpty()) {
                TextView res = new TextView(activity);
                res.setText("    " + s.result);
                res.setTextColor(Theme.TEXT_SECONDARY);
                res.setTextSize(Theme.T_CAPTION);
                res.setLineSpacing(Theme.dp(activity, 2), 1f);
                res.setPadding(Theme.dp(activity, Theme.S3), 0, 0,
                        Theme.dp(activity, Theme.S2));
                layout.addView(res);
            }
        }

        ScrollView sw = new ScrollView(activity);
        sw.addView(layout);

        new AlertDialog.Builder(activity)
                .setTitle("Run detail")
                .setView(sw)
                .setNegativeButton("Close", null)
                .show();
    }

    private AgentHistoryDialog() {}
}
