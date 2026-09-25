package com.indai.assistant;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolParser {

    public static class Result {
        public final String cleanedText;
        public final List<Tools.Call> calls = new ArrayList<>();
        public Result(String cleanedText) { this.cleanedText = cleanedText; }
    }

    private static final String OPEN_TAG = "<tool_call>";
    private static final String CLOSE_TAG = "</tool_call>";

    private static final Pattern CODE_FENCE = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)\\s*```");

    public static Result parse(String raw) {
        if (raw == null) return new Result("");
        List<Tools.Call> calls = new ArrayList<>();
        StringBuilder cleaned = new StringBuilder();
        String working = raw;
        int cursor = 0;

        // Pass 1: walk through <tool_call> openings
        while (true) {
            int open = working.indexOf(OPEN_TAG, cursor);
            if (open < 0) {
                cleaned.append(working.substring(cursor));
                break;
            }
            // Preserve text before the tag
            cleaned.append(working, cursor, open);
            int afterOpen = open + OPEN_TAG.length();
            int close = working.indexOf(CLOSE_TAG, afterOpen);

            if (close < 0) {
                // ---- Truncated tool_call (no closing tag) ----
                String tail = working.substring(afterOpen);
                Tools.Call c = parseOne(tail);
                if (c != null) {
                    calls.add(c);
                    // Everything from the tag to the end is dropped.
                    cursor = working.length();
                } else {
                    // Could not parse — drop the raw tag but keep the JSON attempt text?
                    // Safer: drop the whole tail to avoid leaking raw JSON to the user.
                    cursor = working.length();
                }
                break;
            } else {
                String inner = working.substring(afterOpen, close).trim();
                Tools.Call c = parseOne(inner);
                if (c != null) calls.add(c);
                cursor = close + CLOSE_TAG.length();
            }
        }
        working = cleaned.toString();

        // Pass 2: code fence fallback (only if nothing found yet)
        if (calls.isEmpty()) {
            Matcher f = CODE_FENCE.matcher(working);
            StringBuilder cleaned2 = new StringBuilder();
            int lastEnd = 0;
            while (f.find()) {
                cleaned2.append(working, lastEnd, f.start());
                String inner = f.group(1).trim();
                Tools.Call c = parseOne(inner);
                if (c != null) calls.add(c);
                else cleaned2.append(f.group(0));
                lastEnd = f.end();
            }
            cleaned2.append(working.substring(lastEnd));
            working = cleaned2.toString();
        }

        // Pass 3: bare JSON at start
        if (calls.isEmpty()) {
            String trimmed = working.trim();
            if (trimmed.startsWith("{") && trimmed.contains("\"name\"")
                    && trimmed.contains("\"arguments\"")) {
                Tools.Call c = parseOne(trimmed);
                if (c != null) {
                    calls.add(c);
                    working = "";
                }
            }
        }

        Result r = new Result(working.trim());
        r.calls.addAll(calls);
        return r;
    }

    private static Tools.Call parseOne(String json) {
        if (json == null || json.isEmpty()) return null;
        Tools.Call c = tryParse(json);
        if (c != null) return c;
        // Extract the first balanced { ... } object
        int start = json.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escape) { escape = false; continue; }
            if (ch == '\\') { escape = true; continue; }
            if (ch == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return tryParse(json.substring(start, i + 1));
                }
            }
        }
        return null;
    }

    private static Tools.Call tryParse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String name = o.optString("name", "");
            JSONObject args = o.optJSONObject("arguments");
            if (args == null) args = new JSONObject();
            if (!Tools.isKnown(name)) return null;
            return new Tools.Call(name, args);
        } catch (Exception e) {
            return null;
        }
    }

    private ToolParser() {}
}
