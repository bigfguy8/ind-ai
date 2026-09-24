package com.nova.assistant;

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

    private final Handler main = new Handler(Looper.getMainLooper());

    private AgentHistoryStore historyStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keyStore = new SecureKeyStore(this);
        config = keyStore.load();
        memoryStore = new MemoryStore(this, config);
        historyStore = new AgentHistoryStore(this);
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

        wrap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (currentSession != null) {
                    currentSession.cancel();
                    toast("Cancelling...");
                    return;
                }
                String goal = input.getText().toString().trim();
                if (goal.isEmpty()) return;
                input.setText("");
                startTask(goal);
            }
        });
        return wrap;
    }

    private void startTask(final String goal) {
        appendGoalBubble(goal);

        currentSession = new AgentSession(this, config, memoryStore,
                new AgentSession.Listener() {
            @Override public void onPlanning() {
                main.post(new Runnable() {
                    @Override public void run() {
                        appendNoteBubble("Planning...");
                    }
                });
            }

            @Override public void onPlanReady(final Plan plan) {
                main.post(new Runnable() {
                    @Override public void run() {
                        currentCard = AgentRenderer.build(AgentActivity.this, plan);
                        feed.addView(currentCard.root);
                        currentCard.root.setAlpha(0f);
                        currentCard.root.animate().alpha(1f).setDuration(220).start();
                        scrollDown();
                        headerStatus.setText("Running: " + plan.size() + " steps");
                    }
                });
            }

            @Override public void onStepStarted(Plan plan, final int index) {
                main.post(new Runnable() {
                    @Override public void run() {
                        AgentRenderer.updateStep(AgentActivity.this,
                                currentCard, plan, index);
                    }
                });
            }

            @Override public void onStepNote(Plan plan, final int index, final String note) {
                main.post(new Runnable() {
                    @Override public void run() {
                        AgentRenderer.appendNote(AgentActivity.this,
                                currentCard, index, note);
                        scrollDown();
                    }
                });
            }

            @Override public void onStepFinished(Plan plan, final int index,
                                                 boolean success, String result) {
                main.post(new Runnable() {
                    @Override public void run() {
                        AgentRenderer.updateStep(AgentActivity.this,
                                currentCard, plan, index);
                        scrollDown();
                    }
                });
            }

            @Override public void onStepRetrying(Plan plan, final int index) {
                main.post(new Runnable() {
                    @Override public void run() {
                        if (currentCard != null) {
                            AgentRenderer.appendNote(AgentActivity.this,
                                    currentCard, index, "↻ retrying...");
                        }
                        scrollDown();
                    }
                });
            }

            @Override public void onReplanning(Plan plan, final int afterIndex,
                                                final String reason) {
                main.post(new Runnable() {
                    @Override public void run() {
                        appendNoteBubble("Replanning — " + reason);
                    }
                });
            }

            @Override public void onPlanRevised(final Plan plan) {
                main.post(new Runnable() {
                    @Override public void run() {
                        // Rebuild the plan card
                        if (currentCard != null
                                && currentCard.root.getParent() instanceof ViewGroup) {
                            ViewGroup parent = (ViewGroup) currentCard.root.getParent();
                            int idx = parent.indexOfChild(currentCard.root);
                            parent.removeView(currentCard.root);
                            currentCard = AgentRenderer.build(AgentActivity.this, plan);
                            parent.addView(currentCard.root, idx);
                        }
                        appendNoteBubble("Plan revised (Rev " + plan.revision + ")");
                        scrollDown();
                    }
                });
            }

            @Override public void onReport(final String summary) {
                main.post(new Runnable() {
                    @Override public void run() {
                        appendReportBubble(summary);
                    }
                });
            }

            @Override public void onError(final String reason) {
                main.post(new Runnable() {
                    @Override public void run() {
                        appendNoteBubble("Error: " + reason);
                    }
                });
            }

            @Override public void onFinished(Plan plan) {
                main.post(new Runnable() {
                    @Override public void run() {
                        if (plan != null) historyStore.save(plan);
                        currentSession = null;
                        currentCard = null;
                        headerStatus.setText("Give Ind AI a goal. It plans, executes, and reports.");
                        if (plan != null) {
                            String st = plan.completedCount() + "/" + plan.size()
                                    + " steps completed";
                            if (plan.status == Plan.OverallStatus.CANCELLED) {
                                st += " (cancelled)";
                            }
                            toast(st);
                        }
                    }
                });
            }
        });
        currentSession.start(goal);
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

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
