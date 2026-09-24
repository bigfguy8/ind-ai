package com.nova.assistant;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ChatExporter {

    public static String toMarkdown(JSONArray history) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        sb.append("# Ind AI conversation\n\n");
        sb.append("_Exported ").append(fmt.format(new Date())).append("_\n\n---\n\n");

        for (int i = 0; i < history.length(); i++) {
            try {
                JSONObject m = history.getJSONObject(i);
                String role = m.optString("role");
                if ("system".equals(role)) continue;
                String content = m.optString("content", "");
                if (content.isEmpty()) continue;

                if ("user".equals(role)) {
                    if (content.startsWith("TOOL_RESULT:")) {
                        sb.append("**Action:** ").append(content.substring(12).trim()).append("\n\n");
                    } else {
                        sb.append("## You\n\n").append(content).append("\n\n");
                    }
                } else if ("assistant".equals(role)) {
                    sb.append("## Ind AI\n\n").append(content).append("\n\n");
                }
            } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    public static void share(Context ctx, JSONArray history) {
        String md = toMarkdown(history);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Ind AI conversation");
        i.putExtra(Intent.EXTRA_TEXT, md);
        ctx.startActivity(Intent.createChooser(i, "Export conversation"));
    }

    private ChatExporter() {}
}
