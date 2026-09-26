package com.indai.assistant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a compact message array for the LLM.
 *
 * The user-facing chat history is untouched; only the payload sent
 * to the LLM is compressed. Compression rules:
 *   - Keep the system message as-is (contains critical directives).
 *   - Keep the last MAX_VERBATIM messages exactly.
 *   - For older messages:
 *       - assistant messages longer than ASSISTANT_CAP are truncated.
 *       - user messages starting with "TOOL_RESULT:" longer than TOOL_CAP are truncated.
 *   - Drop everything older than MAX_MESSAGES.
 *
 * This is intentionally deterministic — no LLM call to summarize.
 */
public final class ContextBuilder {

    public static final int MAX_MESSAGES = 12;
    public static final int MAX_VERBATIM = 4;
    public static final int ASSISTANT_CAP = 800;
    public static final int TOOL_CAP = 150;
    public static final int USER_CAP = 2000;

    public static JSONArray build(List<JSONObject> history) {
        return build(history, MAX_MESSAGES, MAX_VERBATIM,
                ASSISTANT_CAP, TOOL_CAP, USER_CAP);
    }

    public static JSONArray build(List<JSONObject> history,
                                  int maxMessages, int maxVerbatim,
                                  int assistantCap, int toolCap, int userCap) {
        JSONArray out = new JSONArray();
        if (history == null || history.isEmpty()) return out;

        JSONObject system = null;
        List<JSONObject> rest = new ArrayList<>();
        for (JSONObject m : history) {
            if (system == null && "system".equals(m.optString("role"))) {
                system = m;
            } else {
                rest.add(m);
            }
        }

        if (system != null) out.put(system);

        int total = rest.size();
        int start = Math.max(0, total - maxMessages);
        List<JSONObject> recent = rest.subList(start, total);

        int verbatimStart = Math.max(0, recent.size() - maxVerbatim);

        for (int i = 0; i < recent.size(); i++) {
            JSONObject m = recent.get(i);
            String role = m.optString("role");
            String content = m.optString("content", "");
            boolean verbatim = i >= verbatimStart;

            try {
                JSONObject copy = new JSONObject();
                copy.put("role", role);

                if (verbatim) {
                    copy.put("content", content);
                } else if ("assistant".equals(role) && content.length() > assistantCap) {
                    copy.put("content", content.substring(0, assistantCap) + " …[truncated]");
                } else if ("user".equals(role)
                        && content.startsWith("TOOL_RESULT:")
                        && content.length() > toolCap) {
                    copy.put("content", content.substring(0, toolCap) + " …[truncated]");
                } else if ("user".equals(role) && content.length() > userCap) {
                    copy.put("content", content.substring(0, userCap) + " …[truncated]");
                } else {
                    copy.put("content", content);
                }

                out.put(copy);
            } catch (Exception ignored) {}
        }

        return out;
    }

    /** Rough token estimate: ~1 token per 4 chars. */
    public static int estimateTokens(JSONArray messages) {
        if (messages == null) return 0;
        int chars = 0;
        for (int i = 0; i < messages.length(); i++) {
            try {
                chars += messages.getJSONObject(i).optString("content", "").length();
            } catch (Exception ignored) {}
        }
        return chars / 4;
    }

    /** Returns a human-readable one-line summary. */
    public static String diagnostic(List<JSONObject> history) {
        if (history == null || history.isEmpty()) return "Empty history";
        JSONArray full = new JSONArray(history);
        JSONArray compact = build(history);
        int before = estimateTokens(full);
        int after = estimateTokens(compact);
        int saved = before > 0 ? (int) (100.0 * (before - after) / before) : 0;
        return "History: " + history.size() + " msgs → "
                + compact.length() + " msgs\n"
                + "Tokens: ~" + before + " → ~" + after
                + " (" + saved + "% saved)";
    }

    private ContextBuilder() {}
}
