package com.nova.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class SearchDialog {

    public interface OnPick {
        void onPick(int historyIndex);
    }

    public static void show(final Activity activity, JSONArray history, final OnPick picker) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(Theme.dp(activity, Theme.S4), Theme.dp(activity, Theme.S3),
                Theme.dp(activity, Theme.S4), Theme.dp(activity, Theme.S3));

        final EditText query = new EditText(activity);
        query.setHint("Search messages...");
        query.setHintTextColor(Theme.TEXT_TERTIARY);
        query.setTextColor(Theme.TEXT_PRIMARY);
        query.setBackground(Drawables.outlined(activity,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_PILL, 1f));
        int qpH = Theme.dp(activity, Theme.S4);
        int qpV = Theme.dp(activity, Theme.S2);
        query.setPadding(qpH, qpV, qpH, qpV);
        layout.addView(query);

        final LinearLayout results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Theme.dp(activity, Theme.S3);
        layout.addView(results, rlp);

        ScrollView sw = new ScrollView(activity);
        sw.addView(layout);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Search")
                .setView(sw)
                .setNegativeButton("Close", null)
                .create();

        query.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable e) {
                String q = e.toString().trim().toLowerCase();
                results.removeAllViews();
                if (q.isEmpty()) return;
                List<int[]> found = new ArrayList<>();
                List<String> previews = new ArrayList<>();
                for (int i = 0; i < history.length(); i++) {
                    try {
                        JSONObject m = history.getJSONObject(i);
                        String role = m.optString("role");
                        if ("system".equals(role)) continue;
                        String content = m.optString("content", "");
                        if (content.toLowerCase().contains(q)) {
                            found.add(new int[]{i});
                            String who = "user".equals(role) ? "You" : "Ind AI";
                            String snippet = content.replace('\n', ' ');
                            if (snippet.length() > 100) snippet = snippet.substring(0, 100) + "...";
                            previews.add(who + ": " + snippet);
                        }
                    } catch (Exception ignored) {}
                }
                if (found.isEmpty()) {
                    TextView empty = new TextView(activity);
                    empty.setText("No matches.");
                    empty.setTextColor(Theme.TEXT_SECONDARY);
                    empty.setTextSize(Theme.T_CAPTION);
                    empty.setGravity(Gravity.CENTER);
                    empty.setPadding(0, Theme.dp(activity, Theme.S4), 0, Theme.dp(activity, Theme.S4));
                    results.addView(empty);
                    return;
                }
                for (int k = 0; k < found.size(); k++) {
                    final int histIdx = found.get(k)[0];
                    TextView row = new TextView(activity);
                    row.setText(previews.get(k));
                    row.setTextColor(Theme.TEXT_PRIMARY);
                    row.setTextSize(Theme.T_BODY - 1f);
                    row.setLineSpacing(Theme.dp(activity, 2), 1f);
                    int rpH = Theme.dp(activity, Theme.S3);
                    int rpV = Theme.dp(activity, Theme.S3);
                    row.setPadding(rpH, rpV, rpH, rpV);
                    row.setBackground(Drawables.outlined(activity,
                            Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_MD, 1f));
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowLp.bottomMargin = Theme.dp(activity, Theme.S2);
                    row.setLayoutParams(rowLp);
                    row.setOnClickListener(v -> {
                        dialog.dismiss();
                        picker.onPick(histIdx);
                    });
                    results.addView(row);
                }
            }
        });

        dialog.show();
        query.requestFocus();
    }

    private SearchDialog() {}
}
