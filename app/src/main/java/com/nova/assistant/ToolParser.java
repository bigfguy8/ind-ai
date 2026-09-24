package com.nova.assistant;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ToolParser {

    public static class Result {
        public final String cleanedText;
        public final List<Tools.Call> calls = new ArrayList<>();
        public Result(String cleanedText) { this.cleanedText = cleanedText; }
    }

    public static Result parse(String raw) {
        if (raw == null) return new Result("");
        StringBuilder cleaned = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            int start = raw.indexOf("<tool_call>", i);
            if (start < 0) { cleaned.append(raw, i, raw.length()); break; }
            cleaned.append(raw, i, start);
            int end = raw.indexOf("</tool_call>", start);
            if (end < 0) { cleaned.append(raw, start, raw.length()); break; }
            String inner = raw.substring(start + 11, end).trim();
            Tools.Call c = parseOne(inner);
            Result tmp = new Result("");
            tmp.calls.add(c);
            if (c != null) {
                // collect into a temporary list attached via holder
                // (kept simple to preserve insertion order)
            }
            i = end + 12;
            // attach below via outer
            pending.add(c);
        }
        Result r = new Result(cleaned.toString().trim());
        for (Tools.Call c : pending) if (c != null) r.calls.add(c);
        pending.clear();
        return r;
    }

    private static final List<Tools.Call> pending = new ArrayList<>();

    private static Tools.Call parseOne(String json) {
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
