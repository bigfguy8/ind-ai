package com.indai.assistant;

import android.content.Context;

/**
 * Routes user requests:
 *   1. Try deterministic fast path (no LLM)
 *   2. If unhandled, hand off to GoalOrchestrator (LLM planning + execution)
 *
 * Budget is enforced across the whole lifecycle.
 */
public class AgentEngine {

    public interface Listener {
        void onFastPathResult(String result);
        void onNeedsConfirmation(String summary, Runnable confirm, Runnable cancel);
        void onFastPathFailed(String reason);
        void onHandoffToPlanner(String goalText);
        void onBudgetExhausted(String reason);
    }

    private final Context ctx;
    private final Listener listener;
    private BudgetManager budget;

    public AgentEngine(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    /** Start processing a user request. */
    public void handle(final String userInput, final ApiConfig config,
                       final MemoryStore memoryStore) {
        budget = new BudgetManager();

        FastPathHandler.tryHandle(ctx, userInput, new FastPathHandler.Callback() {
            @Override public void onHandled(String userVisibleResult) {
                listener.onFastPathResult(userVisibleResult);
            }

            @Override public void onNeedsConfirmation(String summary,
                                                      Runnable confirm,
                                                      Runnable cancel) {
                listener.onNeedsConfirmation(summary, confirm, cancel);
            }

            @Override public void onUnhandled() {
                // Check budget before starting LLM-backed goal
                if (budget.isExhausted()) {
                    listener.onBudgetExhausted(budget.killReason());
                    return;
                }
                listener.onHandoffToPlanner(userInput);
            }
        });
    }

    public BudgetManager budget() {
        return budget;
    }

    /** True if this request would be handled deterministically. */
    public static boolean isFastPath(String input) {
        return IntentClassifier.classify(input) != null;
    }
}
