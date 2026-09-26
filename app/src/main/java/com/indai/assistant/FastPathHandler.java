package com.indai.assistant;

import android.content.Context;

/**
 * Executes simple intents without calling the LLM.
 * Returns true if handled; false if the request needs the agent.
 */
public final class FastPathHandler {

    public interface Callback {
        void onHandled(String userVisibleResult);
        void onNeedsConfirmation(String summary, Runnable confirm, Runnable cancel);
        void onUnhandled();
    }

    public static void tryHandle(final Context ctx, String input, final Callback cb) {
        IntentClassifier.Match match = IntentClassifier.classify(input);
        if (match == null) {
            cb.onUnhandled();
            return;
        }

        // Inline math result
        if ("__math__".equals(match.tool)) {
            cb.onHandled(match.confirmation);
            return;
        }

        // Route through existing ToolExecutor
        final Tools.Call call = new Tools.Call(match.tool, match.args);

        // Dangerous tools need confirmation
        if (Tools.isDangerous(match.tool)) {
            String summary = match.tool.replace('_', ' ')
                    + " — " + match.args.toString();
            cb.onNeedsConfirmation(summary,
                    new Runnable() {
                        @Override public void run() {
                            executeNow(ctx, call, cb);
                        }
                    },
                    new Runnable() {
                        @Override public void run() {
                            cb.onHandled("Cancelled.");
                        }
                    });
            return;
        }

        executeNow(ctx, call, cb);
    }

    private static void executeNow(final Context ctx, final Tools.Call call,
                                   final Callback cb) {
        ToolExecutor.execute(ctx, call, new ToolExecutor.Listener() {
            @Override public void onNeedsConfirmation(String summary,
                                                      final Runnable confirm) {
                cb.onNeedsConfirmation(summary, confirm,
                        new Runnable() {
                            @Override public void run() {
                                cb.onHandled("Cancelled.");
                            }
                        });
            }
            @Override public void onResult(String result) {
                cb.onHandled(summarize(call.name, result));
            }
        });
    }

    /** Format result concisely for display. */
    private static String summarize(String tool, String raw) {
        if (raw == null || raw.isEmpty()) return "Done.";
        String r = raw.trim();
        // Cap length
        if (r.length() > 200) r = r.substring(0, 197) + "...";
        return r;
    }

    private FastPathHandler() {}
}
