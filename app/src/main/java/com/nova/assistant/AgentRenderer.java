package com.nova.assistant;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class AgentRenderer {

    public static class PlanCard {
        public View root;
        public TextView statusLabel;
        public LinearLayout stepsContainer;
        public final List<StepRow> rows = new ArrayList<>();
    }

    public static class StepRow {
        public TextView dot;
        public TextView label;
        public TextView note;
        public LinearLayout wrapper;
    }

    public static PlanCard build(Context c, Plan plan) {
        PlanCard card = new PlanCard();

        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(Theme.dp(c, Theme.S4), Theme.dp(c, Theme.S4),
                Theme.dp(c, Theme.S4), Theme.dp(c, Theme.S4));
        wrap.setBackground(Drawables.outlined(c,
                Theme.SURFACE_HIGH, Theme.SURFACE_STROKE, Theme.R_LG, 1f));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = Theme.dp(c, Theme.S3);
        wrap.setLayoutParams(clp);

        TextView tag = new TextView(c);
        tag.setText("AGENT PLAN");
        tag.setTextSize(Theme.T_LABEL);
        tag.setLetterSpacing(0.18f);
        tag.setTextColor(Theme.PRIMARY);
        tag.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        wrap.addView(tag);

        TextView goal = new TextView(c);
        goal.setText(plan.goal);
        goal.setTextColor(Theme.TEXT_PRIMARY);
        goal.setTextSize(Theme.T_BODY);
        goal.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        goal.setPadding(0, Theme.dp(c, Theme.S2), 0, Theme.dp(c, Theme.S3));
        goal.setLineSpacing(Theme.dp(c, 2), 1f);
        wrap.addView(goal);

        LinearLayout steps = new LinearLayout(c);
        steps.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(steps);
        card.stepsContainer = steps;

        for (int i = 0; i < plan.steps.size(); i++) {
            Plan.Step s = plan.steps.get(i);
            StepRow row = buildStepRow(c, s);
            card.rows.add(row);
            steps.addView(row.wrapper);
        }

        TextView status = new TextView(c);
        status.setTextSize(Theme.T_CAPTION);
        status.setTextColor(Theme.TEXT_SECONDARY);
        status.setPadding(0, Theme.dp(c, Theme.S3), 0, 0);
        wrap.addView(status);
        card.statusLabel = status;

        card.root = wrap;
        updateAll(c, card, plan);
        return card;
    }

    private static StepRow buildStepRow(Context c, Plan.Step step) {
        StepRow row = new StepRow();

        LinearLayout wrapper = new LinearLayout(c);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.bottomMargin = Theme.dp(c, Theme.S2);
        wrapper.setLayoutParams(wlp);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.TOP);

        TextView dot = new TextView(c);
        dot.setText("○");
        dot.setTextSize(16);
        dot.setTextColor(Theme.TEXT_TERTIARY);
        dot.setPadding(0, 0, Theme.dp(c, Theme.S2), 0);
        head.addView(dot);
        row.dot = dot;

        TextView label = new TextView(c);
        label.setText(step.description);
        label.setTextColor(Theme.TEXT_PRIMARY);
        label.setTextSize(Theme.T_BODY - 0.5f);
        label.setLineSpacing(Theme.dp(c, 2), 1f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        head.addView(label, llp);
        row.label = label;

        wrapper.addView(head);

        TextView note = new TextView(c);
        note.setTextSize(Theme.T_CAPTION);
        note.setTextColor(Theme.TEXT_SECONDARY);
        note.setPadding(Theme.dp(c, Theme.S5), Theme.dp(c, Theme.S1), 0, 0);
        note.setVisibility(View.GONE);
        note.setLineSpacing(Theme.dp(c, 2), 1f);
        wrapper.addView(note);
        row.note = note;

        row.wrapper = wrapper;
        return row;
    }

    public static void updateStep(Context c, PlanCard card, Plan plan, int index) {
        if (card == null || index < 0 || index >= card.rows.size()) return;
        StepRow row = card.rows.get(index);
        Plan.Step s = plan.steps.get(index);

        switch (s.status) {
            case PENDING:
                row.dot.setText("○");
                row.dot.setTextColor(Theme.TEXT_TERTIARY);
                row.label.setTextColor(Theme.TEXT_PRIMARY);
                break;
            case RUNNING:
                row.dot.setText("●");
                row.dot.setTextColor(Theme.PRIMARY);
                row.label.setTextColor(Theme.TEXT_PRIMARY);
                break;
            case DONE:
                row.dot.setText("✓");
                row.dot.setTextColor(Theme.SUCCESS);
                row.label.setTextColor(Theme.TEXT_PRIMARY);
                break;
            case FAILED:
                row.dot.setText("✗");
                row.dot.setTextColor(Theme.ERROR);
                row.label.setTextColor(Theme.ERROR);
                break;
            case SKIPPED:
                row.dot.setText("–");
                row.dot.setTextColor(Theme.TEXT_TERTIARY);
                row.label.setTextColor(Theme.TEXT_TERTIARY);
                break;
        }

        if (s.result != null && !s.result.isEmpty()) {
            row.note.setVisibility(View.VISIBLE);
            row.note.setText(s.result);
        }

        updateAll(c, card, plan);
    }

    public static void appendNote(Context c, PlanCard card, int index, String note) {
        if (card == null || index < 0 || index >= card.rows.size()) return;
        StepRow row = card.rows.get(index);
        row.note.setVisibility(View.VISIBLE);
        String existing = row.note.getText() == null ? "" : row.note.getText().toString();
        if (existing.isEmpty()) {
            row.note.setText(note);
        } else {
            row.note.setText(existing + "\n" + note);
        }
    }

    public static void updateAll(Context c, PlanCard card, Plan plan) {
        if (card == null) return;
        String status;
        switch (plan.status) {
            case PLANNING:  status = "planning"; break;
            case RUNNING:   status = plan.completedCount() + " of " + plan.size()
                                    + " steps · running"; break;
            case DONE:      status = plan.completedCount() + " of " + plan.size()
                                    + " steps · done"; break;
            case FAILED:    status = plan.completedCount() + " of " + plan.size()
                                    + " steps · " + plan.failedCount() + " failed"; break;
            case CANCELLED: status = "cancelled at " + plan.completedCount()
                                    + " of " + plan.size(); break;
            default:        status = "";
        }
        card.statusLabel.setText(status);
    }

    private AgentRenderer() {}
}
