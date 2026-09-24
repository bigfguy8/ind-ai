package com.nova.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
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

import java.util.List;

public class MemoryActivity extends Activity {

    private LinearLayout listContainer;
    private TextView countLabel;
    private MemoryStore memoryStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiConfig config = new SecureKeyStore(this).load();
        memoryStore = new MemoryStore(this, config);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
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
        title.setText("Memory");
        title.setTextColor(Theme.TEXT_PRIMARY);
        title.setTextSize(Theme.T_DISPLAY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title);

        countLabel = new TextView(this);
        countLabel.setTextSize(Theme.T_CAPTION);
        countLabel.setTextColor(Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Theme.dp(this, Theme.S1);
        header.addView(countLabel, clp);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout listFrame = new FrameLayout(this);
        root.addView(listFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ScrollView scroll = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(padH, Theme.dp(this, Theme.S2),
                padH, Theme.dp(this, Theme.S5));
        scroll.addView(listContainer);
        listFrame.addView(scroll);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setPadding(padH, Theme.dp(this, Theme.S2),
                padH, Theme.dp(this, Theme.S3));

        final EditText input = new EditText(this);
        input.setHint("Add a fact...");
        input.setHintTextColor(Theme.TEXT_TERTIARY);
        input.setTextColor(Theme.TEXT_PRIMARY);
        input.setTextSize(Theme.T_BODY);
        input.setBackground(Drawables.outlined(this,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_PILL, 1f));
        int ipH = Theme.dp(this, Theme.S4);
        int ipV = Theme.dp(this, Theme.S2);
        input.setPadding(ipH, ipV, ipH, ipV);
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        composer.addView(input);

        View add = buildAddButton(input);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                Theme.dp(this, 52), Theme.dp(this, 52));
        alp.leftMargin = Theme.dp(this, Theme.S3);
        add.setLayoutParams(alp);
        composer.addView(add);

        root.addView(composer);

        root.addView(BottomNav.build(this, BottomNav.TAB_MEMORY));

        setContentView(root);
    }

    private View buildAddButton(final EditText input) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackground(Drawables.tappable(this, Theme.PRIMARY, Theme.R_PILL));
        wrap.setElevation(Theme.dp(this, 4));

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(26);
        plus.setTextColor(Theme.ON_PRIMARY);
        plus.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        wrap.addView(plus, lp);

        wrap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                String fact = input.getText().toString().trim();
                if (fact.isEmpty()) return;
                memoryStore.add(fact);
                input.setText("");
                refresh();
                Toast.makeText(MemoryActivity.this, "Saved", Toast.LENGTH_SHORT).show();
            }
        });
        return wrap;
    }

    private void refresh() {
        listContainer.removeAllViews();
        List<String> facts = memoryStore.load();
        countLabel.setText(facts.size() + " fact"
                + (facts.size() == 1 ? "" : "s")
                + " · " + memoryStore.vectorCount() + " embedded");

        if (facts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nothing remembered yet.\n\nFacts you tell Ind AI to remember "
                    + "will appear here automatically.");
            empty.setTextColor(Theme.TEXT_SECONDARY);
            empty.setTextSize(Theme.T_CAPTION);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(Theme.dp(this, 3), 1f);
            empty.setPadding(0, Theme.dp(this, Theme.S6), 0, Theme.dp(this, Theme.S6));
            listContainer.addView(empty);
            return;
        }

        for (final String fact : facts) {
            TextView row = new TextView(this);
            row.setText(fact);
            row.setTextColor(Theme.TEXT_PRIMARY);
            row.setTextSize(Theme.T_BODY);
            row.setLineSpacing(Theme.dp(this, 2), 1f);
            int rpH = Theme.dp(this, Theme.S4);
            int rpV = Theme.dp(this, Theme.S4);
            row.setPadding(rpH, rpV, rpH, rpV);
            row.setBackground(Drawables.outlined(this,
                    Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_MD, 1f));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = Theme.dp(this, Theme.S2);
            row.setLayoutParams(rlp);

            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new AlertDialog.Builder(MemoryActivity.this)
                            .setTitle("Forget this?")
                            .setMessage(fact)
                            .setPositiveButton("Forget", (d, w) -> {
                                memoryStore.remove(fact);
                                refresh();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });

            listContainer.addView(row);
        }
    }
}
