package com.indai.assistant;

import android.content.Context;

import java.util.List;

/**
 * Orchestrates persistent goals:
 *   - Creates a Goal, saves it, plans it via Planner, executes via Executor
 *   - Persists Goal state after every step (never reports state before saving)
 *   - Restores a paused/blocked goal and continues from the saved step
 *   - Bounded retries; preserves goal on failure (never silently drops)
 *
 * Uses the existing Planner + Executor engine — same engine AgentSession wraps.
 * No new agent engine.
 */
public class GoalOrchestrator {

    private static final int MAX_GOAL_RETRIES = 3;

    private final Context ctx;
    private final ApiConfig config;
    private final MemoryStore memoryStore;
    private final GoalStore goalStore;

    private volatile boolean cancelled = false;
    private volatile String currentGoalId = null;

    public GoalOrchestrator(Context ctx, ApiConfig config, MemoryStore memoryStore) {
        this.ctx = ctx.getApplicationContext();
        this.config = config;
        this.memoryStore = memoryStore;
        this.goalStore = new GoalStore(ctx);
    }

    public GoalStore store() {
        return goalStore;
    }

    public String currentGoalId() {
        return currentGoalId;
    }

    public boolean isRunning() {
        return currentGoalId != null;
    }

    // ------------------------------------------------------------------
    //  Create
    // ------------------------------------------------------------------

    public void createGoal(final String request, final GoalListener listener) {
        cancelled = false;
        if (request == null || request.trim().isEmpty()) {
            if (listener != null) listener.onGoalFailed(null, "Empty goal");
            return;
        }

        final Goal goal = new Goal(deriveTitle(request), request.trim());
        goal.status = Goal.Status.ACTIVE;
        goalStore.save(goal);
        currentGoalId = goal.id;

        if (listener != null) listener.onGoalUpdated(snapshot(goal));

        String memBlock = memoryStore != null ? memoryStore.asSystemBlock() : "";
        String goalContext = buildGoalContext(goal);
        // Prepend the goal-management directive to the memory block, not to the goal text,
        // so it shapes the model's behavior without becoming the plan title.
        String systemCtx = goalContext + "\n" + memBlock;
        Planner.plan(config, goal.description, systemCtx,
                new Planner.Callback() {
            @Override public void onPlan(Plan plan) {
                if (plan == null || plan.size() == 0) {
                    goal.status = Goal.Status.BLOCKED;
                    goal.lastError = "Planner returned empty plan";
                    goalStore.update(goal);
                    currentGoalId = null;
                    if (listener != null) listener.onGoalFailed(snapshot(goal), goal.lastError);
                    return;
                }
                goal.planJson = plan.toJson();
                goal.totalSteps = plan.size();
                goal.currentStep = 0;
                goal.nextAction = plan.steps.get(0).description;
                goalStore.update(goal);
                if (listener != null) listener.onGoalUpdated(snapshot(goal));

                executePlan(goal, plan, listener);
            }

            @Override public void onError(String reason) {
                goal.status = Goal.Status.BLOCKED;
                goal.lastError = reason == null ? "Planning failed" : reason;
                goalStore.update(goal);
                currentGoalId = null;
                if (listener != null) listener.onGoalFailed(snapshot(goal), goal.lastError);
            }
        });
    }

    // ------------------------------------------------------------------
    //  Resume
    // ------------------------------------------------------------------

    public void resumeGoal(final String goalId, final GoalListener listener) {
        cancelled = false;
        final Goal goal = goalStore.get(goalId);
        if (goal == null) {
            if (listener != null) listener.onGoalFailed(null, "Goal not found");
            return;
        }
        if (goal.status == Goal.Status.COMPLETED) {
            if (listener != null) listener.onGoalCompleted(snapshot(goal));
            return;
        }
        currentGoalId = goal.id;

        // Try to restore plan
        Plan plan = null;
        if (goal.planJson != null && !goal.planJson.isEmpty()) {
            plan = Plan.fromJson(goal.planJson);
        }

        if (plan == null || plan.size() == 0) {
            // Re-plan from scratch
            goal.status = Goal.Status.ACTIVE;
            goalStore.update(goal);
            if (listener != null) listener.onGoalUpdated(snapshot(goal));
            createFromExisting(goal, listener);
            return;
        }

        goal.status = Goal.Status.ACTIVE;
        goalStore.update(goal);
        if (listener != null) listener.onGoalUpdated(snapshot(goal));
        executePlan(goal, plan, listener);
    }

    private void createFromExisting(final Goal goal, final GoalListener listener) {
        String memBlock = memoryStore != null ? memoryStore.asSystemBlock() : "";
        Planner.plan(config, goal.description, memBlock, new Planner.Callback() {
            @Override public void onPlan(Plan plan) {
                if (plan == null || plan.size() == 0) {
                    goal.status = Goal.Status.BLOCKED;
                    goal.lastError = "Planner returned empty plan";
                    goalStore.update(goal);
                    currentGoalId = null;
                    if (listener != null) listener.onGoalFailed(snapshot(goal), goal.lastError);
                    return;
                }
                goal.planJson = plan.toJson();
                goal.totalSteps = plan.size();
                goal.currentStep = 0;
                goal.nextAction = plan.steps.get(0).description;
                goalStore.update(goal);
                if (listener != null) listener.onGoalUpdated(snapshot(goal));
                executePlan(goal, plan, listener);
            }

            @Override public void onError(String reason) {
                goal.status = Goal.Status.BLOCKED;
                goal.lastError = reason;
                goalStore.update(goal);
                currentGoalId = null;
                if (listener != null) listener.onGoalFailed(snapshot(goal), reason);
            }
        });
    }

    // ------------------------------------------------------------------
    //  Pause / Cancel
    // ------------------------------------------------------------------

    public void pauseGoal(String goalId) {
        Goal goal = goalStore.get(goalId);
        if (goal == null) return;
        if (goal.status == Goal.Status.ACTIVE) {
            goal.status = Goal.Status.PAUSED;
            goalStore.update(goal);
        }
        cancelled = true;
        currentGoalId = null;
    }

    public void cancelGoal(String goalId) {
        Goal goal = goalStore.get(goalId);
        if (goal == null) return;
        goal.status = Goal.Status.FAILED;
        goal.lastError = "Cancelled by user";
        goalStore.update(goal);
        cancelled = true;
        currentGoalId = null;
    }

    public void cancelCurrent() {
        if (currentGoalId != null) cancelGoal(currentGoalId);
    }

    public void resumeActiveGoals(GoalListener listener) {
        List<Goal> active = goalStore.getActiveGoals();
        if (!active.isEmpty()) {
            resumeGoal(active.get(0).id, listener);
        }
    }

    // ------------------------------------------------------------------
    //  Execute
    // ------------------------------------------------------------------

    private void executePlan(final Goal goal, final Plan plan, final GoalListener listener) {
        Executor.run(ctx, config, plan, new Executor.Listener() {

            @Override public void onStepStarted(int index) {
                goal.currentStep = index;
                goal.nextAction = (index < plan.size())
                        ? plan.steps.get(index).description : "";
                goalStore.update(goal);
                if (listener != null) listener.onGoalUpdated(snapshot(goal));
            }

            @Override public void onStepNote(int index, String note) {
                // notes are transient; do not persist to avoid bloat
            }

            @Override public void onStepFinished(int index, boolean success, String result) {
                goal.currentStep = index + 1;
                goal.planJson = plan.toJson();
                goal.nextAction = (index + 1 < plan.size())
                        ? plan.steps.get(index + 1).description : "";
                if (!success) {
                    goal.lastError = result == null ? "Step failed" : result;
                }
                goalStore.update(goal);
                if (listener != null) listener.onGoalUpdated(snapshot(goal));
            }

            @Override public void onStepRetrying(int index) {
                goal.retryCount++;
                if (goal.retryCount > MAX_GOAL_RETRIES) {
                    goal.status = Goal.Status.FAILED;
                    goal.lastError = "Exceeded retry limit";
                    goalStore.update(goal);
                    cancelled = true; // signal executor
                    if (listener != null) listener.onGoalFailed(snapshot(goal), goal.lastError);
                    return;
                }
                goalStore.update(goal);
                if (listener != null) listener.onGoalUpdated(snapshot(goal));
            }

            @Override public void onReplanning(int afterIndex, String reason) {
                goal.status = Goal.Status.BLOCKED;
                goal.lastError = reason == null ? "Replanning" : reason;
                goalStore.update(goal);
                if (listener != null) listener.onGoalUpdated(snapshot(goal));
            }

            @Override public void onPlanRevised(Plan revised) {
                goal.planJson = revised.toJson();
                goal.totalSteps = revised.size();
                goal.status = Goal.Status.ACTIVE;
                goalStore.update(goal);
                if (listener != null) listener.onGoalUpdated(snapshot(goal));
            }

            @Override public void onPlanDone(Plan done) {
                goal.planJson = done.toJson();
                goal.totalSteps = done.size();
                goal.currentStep = done.completedCount();

                // Guard: don't call it complete if nothing actually ran.
                if (done.size() == 0 || done.completedCount() == 0) {
                    goal.status = Goal.Status.BLOCKED;
                    // Include the first failed step's reason for a useful message.
                    String reason = "No steps completed";
                    for (Plan.Step s : done.steps) {
                        if (s.status == Plan.StepStatus.FAILED
                                && s.result != null && !s.result.isEmpty()) {
                            String r = s.result;
                            if (r.contains("429")) {
                                reason = "Rate limit hit (429). Wait 1 minute or switch "
                                       + "provider in Settings.";
                            } else {
                                reason = r;
                            }
                            break;
                        }
                    }
                    goal.lastError = reason;
                    goalStore.update(goal);
                    currentGoalId = null;
                    if (listener != null) listener.onGoalFailed(snapshot(goal), goal.lastError);
                    return;
                }

                if (done.status == Plan.OverallStatus.DONE
                        && done.failedCount() == 0) {
                    goal.status = Goal.Status.COMPLETED;
                    goal.nextAction = "";
                    goalStore.update(goal);
                    currentGoalId = null;
                    if (memoryStore != null) {
                        try {
                            memoryStore.add(
                                    "Completed goal: " + goal.title,
                                    MemoryEntry.CAT_PROJECTS);
                        } catch (Exception ignored) {}
                    }
                    if (listener != null) listener.onGoalCompleted(snapshot(goal));
                    return;
                }

                if (done.status == Plan.OverallStatus.CANCELLED) {
                    goal.status = Goal.Status.PAUSED;
                    goalStore.update(goal);
                    currentGoalId = null;
                    if (listener != null) listener.onGoalUpdated(snapshot(goal));
                    return;
                }

                // FAILED or mixed outcome
                goal.status = Goal.Status.BLOCKED;
                if (goal.lastError == null || goal.lastError.isEmpty()) {
                    goal.lastError = done.failedCount() + " step(s) failed";
                }
                goal.nextAction = done.current() != null
                        ? done.current().description
                        : "Review and resume";
                goalStore.update(goal);
                currentGoalId = null;
                if (listener != null) listener.onGoalFailed(snapshot(goal), goal.lastError);
            }

            @Override public boolean isCancelled() {
                return cancelled;
            }
        });
    }

    // ------------------------------------------------------------------

    private static String buildGoalContext(Goal goal) {
        return "You manage persistent user goals. Treat goal state as durable state, "
                + "not conversation context. Before executing, inspect the current goal step. "
                + "After every successful or failed action, report structured progress. "
                + "Never claim completion without verified tool success. "
                + "If blocked, preserve the goal and explain the exact next action required.\n"
                + "Goal ID: " + goal.id + "\n"
                + "Goal title: " + goal.title + "\n";
    }

    private static String deriveTitle(String request) {
        if (request == null) return "Untitled";
        String t = request.trim().replaceAll("\\s+", " ");
        if (t.length() > 60) t = t.substring(0, 57) + "...";
        if (t.length() > 0) {
            t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }
        return t;
    }

    /** Return a snapshot so listener cannot mutate the persisted instance. */
    private static Goal snapshot(Goal g) {
        if (g == null) return null;
        try {
            return Goal.fromJson(g.toJson());
        } catch (Exception e) {
            return g;
        }
    }
}
