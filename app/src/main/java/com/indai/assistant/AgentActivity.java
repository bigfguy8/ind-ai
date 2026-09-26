package com.indai.assistant;

import android.app.AlertDialog;
import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AgentActivity extends Activity {

    private LinearLayout feed;
    private ScrollView scroll;
    private EditText input;
    private View runButton;
    private TextView headerStatus;

    private SecureKeyStore keyStore;
    private MemoryStore memoryStore;
    private ApiConfig config;
    private AgentSession currentSession;
    private AgentRenderer.PlanCard currentCard;
    private GoalOrchestrator orchestrator;
    private GoalStore goalStore;
    private AgentEngine agentEngine;
    private volatile boolean uiBusy = false;
    private View runButtonRef;

    private final Handler main = new Handler(Looper.getMainLooper());

    private AgentHistoryStore historyStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keyStore = new SecureKeyStore(this);
        config = keyStore.load();
        memoryStore = new MemoryStore(this, config);
        historyStore = new AgentHistoryStore(this);
        orchestrator = new GoalOrchestrator(this, config, memoryStore);
        goalStore = orchestrator.store();
        agentEngine = new AgentEngine(this, engineListener);
        build();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);
        root.setFitsSystemWindows(true);

        int padH = Theme.dp(this, Theme.S5);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(padH, Theme.dp(this, Theme.S5),
                padH, Theme.dp(this, Theme.S3));

        TextView title = new TextView(this);
        title.setText("Agent");
        title.setTextColor(Theme.TEXT_PRIMARY);
        title.setTextSize(Theme.T_DISPLAY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title);

        android.widget.ImageView historyBtn = new android.widget.ImageView(this);
        historyBtn.setImageResource(R.drawable.ic_clear);
        int hbSize = Theme.dp(this, 20);
        LinearLayout.LayoutParams hblp = new LinearLayout.LayoutParams(hbSize, hbSize);
        hblp.topMargin = Theme.dp(this, Theme.S3);
        historyBtn.setLayoutParams(hblp);
        historyBtn.setColorFilter(Theme.PRIMARY);
        historyBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                AgentHistoryDialog.show(AgentActivity.this);
            }
        });
        header.addView(historyBtn);

        // Goals button
        android.widget.ImageView goalsBtn = new android.widget.ImageView(this);
        goalsBtn.setImageResource(R.drawable.ic_memory);
        int gbSize = Theme.dp(this, 20);
        LinearLayout.LayoutParams gblp = new LinearLayout.LayoutParams(gbSize, gbSize);
        gblp.leftMargin = Theme.dp(this, Theme.S3);
        gblp.topMargin = Theme.dp(this, Theme.S3);
        goalsBtn.setLayoutParams(gblp);
        goalsBtn.setColorFilter(Theme.PRIMARY);
        goalsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                showGoalsList();
            }
        });
        header.addView(goalsBtn);

        headerStatus = new TextView(this);
        headerStatus.setText("Give Ind AI a goal. It plans, executes, and reports.");
        headerStatus.setTextSize(Theme.T_CAPTION);
        headerStatus.setTextColor(Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Theme.dp(this, Theme.S1);
        header.addView(headerStatus, slp);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout feedFrame = new FrameLayout(this);
        root.addView(feedFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        feed.setPadding(padH, Theme.dp(this, Theme.S2),
                padH, Theme.dp(this, Theme.S3));
        scroll.addView(feed);
        feedFrame.addView(scroll);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(padH, Theme.dp(this, Theme.S2),
                padH, Theme.dp(this, Theme.S3));

        input = new EditText(this);
        input.setHint("What should Ind AI do?");
        input.setHintTextColor(Theme.TEXT_TERTIARY);
        input.setTextColor(Theme.TEXT_PRIMARY);
        input.setTextSize(Theme.T_BODY);
        input.setBackground(Drawables.outlined(this,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_PILL, 1f));
        int ipH = Theme.dp(this, Theme.S4);
        int ipV = Theme.dp(this, Theme.S2);
        input.setPadding(ipH, ipV, ipH, ipV);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(4);
        input.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        composer.addView(input);

        runButton = buildRunButton();
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                Theme.dp(this, 52), Theme.dp(this, 52));
        blp.leftMargin = Theme.dp(this, Theme.S3);
        runButton.setLayoutParams(blp);
        composer.addView(runButton);

        root.addView(composer);

        root.addView(BottomNav.build(this, BottomNav.TAB_AGENT));

        setContentView(root);
    }

    private View buildRunButton() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackground(Drawables.tappable(this, Theme.PRIMARY, Theme.R_PILL));
        wrap.setElevation(Theme.dp(this, 4));

        TextView label = new TextView(this);
        label.setText("▶");
        label.setTextSize(20);
        label.setTextColor(Theme.ON_PRIMARY);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        wrap.addView(label, lp);

        runButtonRef = wrap;
        wrap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                hideKeyboard();
                if (orchestrator != null && orchestrator.isRunning()) {
                    orchestrator.cancelCurrent();
                    toast("Cancelling goal...");
                    return;
                }
                if (currentSession != null) {
                    currentSession.cancel();
                    toast("Cancelling...");
                    return;
                }
                if (uiBusy || (orchestrator != null && orchestrator.isRunning())) {
                    toast("A goal is already running. Cancel it first.");
                    return;
                }
                uiBusy = true;
                v.setEnabled(false);
                v.setAlpha(0.5f);
                String goal = input.getText().toString().trim();
                if (goal.isEmpty()) return;
                input.setText("");
                appendGoalBubble(goal);
                agentEngine.handle(goal, config, memoryStore);
            }
        });
        return wrap;
    }

    private final AgentEngine.Listener engineListener = new AgentEngine.Listener() {
        @Override public void onFastPathResult(final String result) {
            main.post(new Runnable() {
                @Override public void run() {
                    uiBusy = false;
                    if (runButtonRef != null) {
                        runButtonRef.setEnabled(true);
                        runButtonRef.setAlpha(1f);
                    }
                    appendReportBubble(result);
                    headerStatus.setText("Give Ind AI a goal. It plans, executes, and reports.");
                }
            });
        }

        @Override public void onNeedsConfirmation(String summary,
                                                  final Runnable confirm,
                                                  final Runnable cancel) {
            main.post(new Runnable() {
                @Override public void run() {
                    uiBusy = false;
                    if (runButtonRef != null) {
                        runButtonRef.setEnabled(true);
                        runButtonRef.setAlpha(1f);
                    }
                    new AlertDialog.Builder(AgentActivity.this)
                            .setTitle("Confirm action")
                            .setMessage(summary)
                            .setPositiveButton("Allow", (d, w) -> confirm.run())
                            .setNegativeButton("Deny", (d, w) -> cancel.run())
                            .show();
                }
            });
        }

        @Override public void onFastPathFailed(final String reason) {
            main.post(new Runnable() {
                @Override public void run() {
                    appendNoteBubble("Fast path failed: " + reason);
                }
            });
        }

        @Override public void onHandoffToPlanner(final String goalText) {
            main.post(new Runnable() {
                @Override public void run() {
                    runAgentPlan(goalText);
                }
            });
        }

        @Override public void onBudgetExhausted(final String reason) {
            main.post(new Runnable() {
                @Override public void run() {
                    uiBusy = false;
                    if (runButtonRef != null) {
                        runButtonRef.setEnabled(true);
                        runButtonRef.setAlpha(1f);
                    }
                    appendReportBubble("Budget exhausted: " + reason);
                    headerStatus.setText("Give Ind AI a goal. It plans, executes, and reports.");
                }
            });
        }
    };

    private void runAgentPlan(final String goalText) {
        headerStatus.setText("Planning goal...");

        // Wire orchestrator listener to render plan card + feed
        orchestrator.createGoal(goalText, new GoalListener() {
            @Override public void onGoalUpdated(final Goal goal) {
                main.post(new Runnable() {
                    @Override public void run() {
                        updateFromGoal(goal);
                    }
                });
            }

            @Override public void onGoalCompleted(final Goal goal) {
                uiBusy = false;
                if (runButtonRef != null) {
                    runButtonRef.setEnabled(true);
                    runButtonRef.setAlpha(1f);
                }
                main.post(new Runnable() {
                    @Override public void run() {
                        updateFromGoal(goal);
                        appendReportBubble("Goal complete. "
                                + goal.progressLabel() + ".");
                        headerStatus.setText("Give Ind AI a goal. It plans, executes, and reports.");
                    }
                });
            }

            @Override public void onGoalFailed(final Goal goal, final String error) {
                uiBusy = false;
                if (runButtonRef != null) {
                    runButtonRef.setEnabled(true);
                    runButtonRef.setAlpha(1f);
                }
                main.post(new Runnable() {
                    @Override public void run() {
                        if (goal != null) updateFromGoal(goal);
                        appendNoteBubble("Goal blocked: " + error);
                        headerStatus.setText("Goal blocked — see card above.");
                    }
                });
            }
        });
    }

    /**
     * Sync the plan card with the current Goal state.
     * Rebuilds the card when the plan changes (revision or new plan).
     */
    private void updateFromGoal(Goal goal) {
        if (goal == null) return;

        Plan plan = null;
        if (goal.planJson != null && !goal.planJson.isEmpty()) {
            plan = Plan.fromJson(goal.planJson);
        }
        if (plan == null || plan.size() == 0) {
            headerStatus.setText("Goal: " + goal.statusLabel());
            return;
        }

        // Rebuild card if size changed or first time
        if (currentCard == null
                || currentCard.rows.size() != plan.size()) {
            if (currentCard != null
                    && currentCard.root.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) currentCard.root.getParent();
                parent.removeView(currentCard.root);
            }
            currentCard = AgentRenderer.build(AgentActivity.this, plan);
            feed.addView(currentCard.root);
            currentCard.root.setAlpha(0f);
            currentCard.root.animate().alpha(1f).setDuration(180).start();
        }

        // Update each step status
        for (int i = 0; i < plan.size(); i++) {
            AgentRenderer.updateStep(AgentActivity.this, currentCard, plan, i);
        }

        String label = goal.statusLabel() + "  ·  " + goal.progressLabel();
        if (!goal.nextAction.isEmpty()) {
            label += "  ·  Next: " + goal.nextAction;
        }
        headerStatus.setText(label);
        scrollDown();
    }

    private void showGoalsList() {
        final java.util.List<Goal> goals = goalStore.getAll();
        if (goals.isEmpty()) {
            Toast.makeText(this, "No goals yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(this, Theme.S4);
        layout.setPadding(pad, pad, pad, pad);

        final AlertDialog[] dialogRef = new AlertDialog[1];

        for (final Goal g : goals) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            int rpH = Theme.dp(this, Theme.S4);
            int rpV = Theme.dp(this, Theme.S3);
            row.setPadding(rpH, rpV, rpH, rpV);
            row.setBackground(Drawables.outlined(this,
                    Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_MD, 1f));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = Theme.dp(this, Theme.S2);
            row.setLayoutParams(rlp);

            TextView title = new TextView(this);
            title.setText(g.title);
            title.setTextColor(Theme.TEXT_PRIMARY);
            title.setTextSize(Theme.T_BODY);
            title.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.NORMAL));
            row.addView(title);

            TextView meta = new TextView(this);
            meta.setText(g.statusLabel() + "  ·  " + g.progressLabel());
            meta.setTextColor(Theme.TEXT_SECONDARY);
            meta.setTextSize(Theme.T_CAPTION);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            mlp.topMargin = Theme.dp(this, Theme.S1);
            row.addView(meta, mlp);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = Theme.dp(this, Theme.S3);
            actions.setLayoutParams(alp);

            if (g.status == Goal.Status.ACTIVE || g.status == Goal.Status.BLOCKED) {
                actions.addView(smallBtn("Resume", Theme.PRIMARY, new Runnable() {
                    @Override public void run() {
                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                        resumeExistingGoal(g);
                    }
                }));
            }
            if (g.status == Goal.Status.ACTIVE) {
                actions.addView(smallBtn("Pause", Theme.TEXT_SECONDARY, new Runnable() {
                    @Override public void run() {
                        goalStore.get(g.id);
                        Goal fresh = goalStore.get(g.id);
                        if (fresh != null) {
                            fresh.status = Goal.Status.PAUSED;
                            goalStore.update(fresh);
                        }
                        Toast.makeText(AgentActivity.this, "Paused",
                                Toast.LENGTH_SHORT).show();
                    }
                }));
            }
            actions.addView(smallBtn("Delete", Theme.ERROR, new Runnable() {
                @Override public void run() {
                    goalStore.delete(g.id);
                    Toast.makeText(AgentActivity.this, "Deleted",
                            Toast.LENGTH_SHORT).show();
                }
            }));

            row.addView(actions);
            layout.addView(row);
        }

        ScrollView sw = new ScrollView(this);
        sw.addView(layout);

        dialogRef[0] = new AlertDialog.Builder(this)
                .setTitle("Goals")
                .setView(sw)
                .setNegativeButton("Close", null)
                .show();
    }

    private View smallBtn(String text, int color, final Runnable onClick) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(Theme.T_CAPTION);
        t.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        int pH = Theme.dp(this, Theme.S3);
        int pV = Theme.dp(this, Theme.S2);
        t.setPadding(pH, pV, pH, pV);
        t.setBackground(Drawables.outlined(this,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_PILL, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Theme.dp(this, Theme.S2);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                onClick.run();
            }
        });
        return t;
    }

    private void resumeExistingGoal(final Goal goal) {
        appendNoteBubble("Resuming: " + goal.title);
        headerStatus.setText("Resuming goal...");
        orchestrator.resumeGoal(goal.id, new GoalListener() {
            @Override public void onGoalUpdated(final Goal g) {
                main.post(new Runnable() {
                    @Override public void run() { updateFromGoal(g); }
                });
            }
            @Override public void onGoalCompleted(final Goal g) {
                main.post(new Runnable() {
                    @Override public void run() {
                        updateFromGoal(g);
                        appendReportBubble("Goal complete. " + g.progressLabel() + ".");
                    }
                });
            }
            @Override public void onGoalFailed(final Goal g, final String err) {
                main.post(new Runnable() {
                    @Override public void run() {
                        if (g != null) updateFromGoal(g);
                        appendNoteBubble("Blocked: " + err);
                    }
                });
            }
        });
    }

    private void appendGoalBubble(String text) {
        TextView t = new TextView(this);
        t.setText("GOAL\n" + text);
        t.setTextSize(Theme.T_BODY);
        t.setTextColor(Theme.BUBBLE_USER_TEXT);
        int bpH = Theme.dp(this, Theme.S4);
        int bpV = Theme.dp(this, Theme.S3);
        t.setPadding(bpH, bpV, bpH, bpV);
        t.setBackground(Drawables.bubble(this, Theme.BUBBLE_USER, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END;
        lp.bottomMargin = Theme.dp(this, Theme.S3);
        t.setLayoutParams(lp);
        feed.addView(t);
        scrollDown();
    }

    private void appendNoteBubble(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(Theme.T_CAPTION);
        t.setTextColor(Theme.TEXT_SECONDARY);
        int bpH = Theme.dp(this, Theme.S3);
        int bpV = Theme.dp(this, Theme.S2);
        t.setPadding(bpH, bpV, bpH, bpV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Theme.dp(this, Theme.S2);
        t.setLayoutParams(lp);
        feed.addView(t);
        scrollDown();
    }

    private void appendReportBubble(String text) {
        TextView t = new TextView(this);
        t.setText("REPORT\n" + text);
        t.setTextSize(Theme.T_BODY);
        t.setTextColor(Theme.TEXT_PRIMARY);
        int bpH = Theme.dp(this, Theme.S4);
        int bpV = Theme.dp(this, Theme.S3);
        t.setPadding(bpH, bpV, bpH, bpV);
        t.setBackground(Drawables.bubble(this, Theme.BUBBLE_AI, false));
        t.setLineSpacing(Theme.dp(this, 3), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Theme.dp(this, Theme.S3);
        t.setLayoutParams(lp);
        feed.addView(t);
        scrollDown();
    }

    private void scrollDown() {
        scroll.post(new Runnable() {
            @Override public void run() {
                scroll.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
            View v = getCurrentFocus();
            if (v != null && imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        } catch (Exception ignored) {}
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
