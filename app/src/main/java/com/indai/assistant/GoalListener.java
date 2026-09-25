package com.indai.assistant;

public interface GoalListener {
    void onGoalUpdated(Goal goal);
    void onGoalCompleted(Goal goal);
    void onGoalFailed(Goal goal, String error);
}
