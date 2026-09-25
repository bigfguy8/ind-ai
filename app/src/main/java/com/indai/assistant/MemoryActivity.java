package com.indai.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MemoryActivity extends Activity {

    private LinearLayout listContainer;
    private TextView countLabel;
    private EditText searchField;
    private LinearLayout categoryChips;
    private MemoryStore memoryStore;
    private String activeCategory = null; // null = All

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

        // ---------- Header ----------
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(padH, Theme.dp(this, Theme.S5),
                padH, Theme.dp(this, Theme.S3));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("Memory");
        title.setTextColor(Theme.TEXT_PRIMARY);
        title.setTextSize(Theme.T_DISPLAY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleBlock.addView(title);

        countLabel = new TextView(this);
        countLabel.setTextSize(Theme.T_CAPTION);
        countLabel.setTextColor(Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Theme.dp(this, Theme.S1);
        titleBlock.addView(countLabel, clp);

        header.addView(titleBlock);

        TextView exportBtn = new TextView(this);
        exportBtn.setText("Export");
        exportBtn.setTextColor(Theme.PRIMARY);
        exportBtn.setTextSize(Theme.T_CAPTION);
        exportBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        exportBtn.setPadding(Theme.dp(this, Theme.S3), Theme.dp(this, Theme.S2),
                Theme.dp(this, Theme.S3), Theme.dp(this, Theme.S2));
        exportBtn.setBackground(Drawables.outlined(this,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_PILL, 1f));
        exportBtn.setOnClickListener(v -> doExport());
        header.addView(exportBtn);

        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        importLp.leftMargin = Theme.dp(this, Theme.S2);
        TextView importBtn = new TextView(this);
        importBtn.setText("Import");
        importBtn.setTextColor(Theme.PRIMARY);
        importBtn.setTextSize(Theme.T_CAPTION);
        importBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        importBtn.setPadding(Theme.dp(this, Theme.S3), Theme.dp(this, Theme.S2),
                Theme.dp(this, Theme.S3), Theme.dp(this, Theme.S2));
        importBtn.setBackground(Drawables.outlined(this,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_PILL, 1f));
        importBtn.setOnClickListener(v -> doImport());
        header.addView(importBtn, importLp);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---------- Search bar ----------
        searchField = new EditText(this);
        searchField.setHint("Search memories...");
        searchField.setHintTextColor(Theme.TEXT_TERTIARY);
        searchField.setTextColor(Theme.TEXT_PRIMARY);
        searchField.setTextSize(Theme.T_BODY);
        searchField.setBackgroundColor(0x00000000);
        searchField.setSingleLine(true);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout searchWrap = new LinearLayout(this);
        searchWrap.setPadding(
                Theme.dp(this, Theme.S4), Theme.dp(this, Theme.S2),
                Theme.dp(this, Theme.S4), Theme.dp(this, Theme.S2));
        searchWrap.setBackground(Drawables.outlined(this,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_PILL, 1f));
        searchWrap.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        searchWrap.addView(searchField);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.leftMargin = padH;
        swLp.rightMargin = padH;
        swLp.bottomMargin = Theme.dp(this, Theme.S3);
        searchWrap.setLayoutParams(swLp);
        root.addView(searchWrap);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { refresh(); }
        });

        // ---------- Category chips ----------
        categoryChips = new LinearLayout(this);
        categoryChips.setOrientation(LinearLayout.HORIZONTAL);
        categoryChips.setPadding(padH, 0, padH, Theme.dp(this, Theme.S3));

        ScrollView chipScroll = new ScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(categoryChips);
        LinearLayout.LayoutParams cslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(chipScroll, cslp);

        // ---------- List ----------
        FrameLayout listFrame = new FrameLayout(this);
        root.addView(listFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ScrollView scroll = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(padH, 0, padH, Theme.dp(this, Theme.S5));
        scroll.addView(listContainer);
        listFrame.addView(scroll);

        // ---------- Composer ----------
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

        wrap.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            String fact = input.getText().toString().trim();
            if (fact.isEmpty()) return;
            askCategoryForNew(fact, input);
        });
        return wrap;
    }

    private void askCategoryForNew(final String fact, final EditText input) {
        new AlertDialog.Builder(this)
                .setTitle("Category for new memory")
                .setItems(MemoryEntry.CATEGORIES, (d, which) -> {
                    memoryStore.add(fact, MemoryEntry.CATEGORIES[which]);
                    input.setText("");
                    refresh();
                    Toast.makeText(MemoryActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------
    //  Refresh
    // ------------------------------------------------------------------

    private void refresh() {
        listContainer.removeAllViews();
        buildCategoryChips();

        String q = searchField.getText().toString();
        List<MemoryEntry> entries = memoryStore.filter(q, activeCategory);

        int total = memoryStore.load().size();
        int emb = memoryStore.vectorCount();
        countLabel.setText(total + " fact" + (total == 1 ? "" : "s")
                + " · " + emb + " embedded");

        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            String msg = memoryStore.isEmpty()
                    ? "Nothing remembered yet.\n\nFacts you tell Ind AI to remember "
                        + "will appear here automatically."
                    : "No memories match this filter.";
            empty.setText(msg);
            empty.setTextColor(Theme.TEXT_SECONDARY);
            empty.setTextSize(Theme.T_CAPTION);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(Theme.dp(this, 3), 1f);
            empty.setPadding(0, Theme.dp(this, Theme.S6), 0, Theme.dp(this, Theme.S6));
            listContainer.addView(empty);
            return;
        }

        // Group by category when showing All
        if (activeCategory == null) {
            for (String cat : MemoryEntry.CATEGORIES) {
                List<MemoryEntry> group = new ArrayList<>();
                for (MemoryEntry e : entries) {
                    if (e.category.equals(cat)) group.add(e);
                }
                if (group.isEmpty()) continue;
                addSectionHeader(cat, group.size());
                for (MemoryEntry e : group) addEntryRow(e);
            }
        } else {
            for (MemoryEntry e : entries) addEntryRow(e);
        }
    }

    private void buildCategoryChips() {
        categoryChips.removeAllViews();
        addChip("All", activeCategory == null, () -> {
            activeCategory = null;
            refresh();
        });
        for (String cat : MemoryEntry.CATEGORIES) {
            final String c = cat;
            int n = memoryStore.countInCategory(cat);
            String label = MemoryEntry.emojiFor(cat) + " " + cat;
            if (n > 0) label += " (" + n + ")";
            addChip(label, c.equals(activeCategory), () -> {
                activeCategory = c;
                refresh();
            });
        }
    }

    private void addChip(String label, boolean active, final Runnable onClick) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(Theme.T_CAPTION);
        chip.setTypeface(Typeface.create(
                active ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        chip.setTextColor(active ? Theme.ON_PRIMARY : Theme.TEXT_PRIMARY);
        chip.setBackground(Drawables.rounded(this,
                active ? Theme.PRIMARY : Theme.SURFACE, Theme.R_PILL));
        int pH = Theme.dp(this, Theme.S3);
        int pV = Theme.dp(this, Theme.S2);
        chip.setPadding(pH, pV, pH, pV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Theme.dp(this, Theme.S2);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            onClick.run();
        });
        categoryChips.addView(chip);
    }

    private void addSectionHeader(String category, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(this, Theme.S4);
        lp.bottomMargin = Theme.dp(this, Theme.S2);
        row.setLayoutParams(lp);

        TextView header = new TextView(this);
        header.setText(MemoryEntry.emojiFor(category) + "  " + category
                + "   ·   " + count);
        header.setTextSize(Theme.T_CAPTION);
        header.setLetterSpacing(0.05f);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.setTextColor(Theme.TEXT_SECONDARY);
        row.addView(header);

        listContainer.addView(row);
    }

    private void addEntryRow(final MemoryEntry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        int pH = Theme.dp(this, Theme.S4);
        int pV = Theme.dp(this, Theme.S4);
        row.setPadding(pH, pV, pH, pV);
        row.setBackground(Drawables.outlined(this,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_MD, 1f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = Theme.dp(this, Theme.S2);
        row.setLayoutParams(rlp);

        TextView text = new TextView(this);
        text.setText(entry.text);
        text.setTextColor(Theme.TEXT_PRIMARY);
        text.setTextSize(Theme.T_BODY);
        text.setLineSpacing(Theme.dp(this, 2), 1f);
        row.addView(text);

        row.setOnClickListener(v -> showEntryActions(entry));

        listContainer.addView(row);
    }

    private void showEntryActions(final MemoryEntry entry) {
        String[] actions = { "Edit", "Change category", "Forget", "Cancel" };
        new AlertDialog.Builder(this)
                .setTitle(entry.text)
                .setItems(actions, (d, which) -> {
                    switch (which) {
                        case 0: editEntry(entry); break;
                        case 1: changeCategory(entry); break;
                        case 2: confirmForget(entry); break;
                    }
                })
                .show();
    }

    private void editEntry(final MemoryEntry entry) {
        final EditText input = new EditText(this);
        input.setText(entry.text);
        input.setTextColor(Theme.TEXT_PRIMARY);
        input.setSelectAllOnFocus(true);
        int pad = Theme.dp(this, Theme.S5);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Edit memory")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String newText = input.getText().toString().trim();
                    if (newText.isEmpty()) return;
                    memoryStore.update(entry.text, newText, entry.category);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void changeCategory(final MemoryEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Category")
                .setItems(MemoryEntry.CATEGORIES, (d, which) -> {
                    memoryStore.update(entry.text, entry.text,
                            MemoryEntry.CATEGORIES[which]);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmForget(final MemoryEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Forget this?")
                .setMessage(entry.text)
                .setPositiveButton("Forget", (d, w) -> {
                    memoryStore.remove(entry.text);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------
    //  Export / Import
    // ------------------------------------------------------------------

    private void doExport() {
        String json = memoryStore.exportJson();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Ind AI memory export");
        intent.putExtra(Intent.EXTRA_TEXT, json);
        try {
            startActivity(Intent.createChooser(intent, "Export memories"));
        } catch (Exception e) {
            Toast.makeText(this, "No app available to share.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void doImport() {
        final EditText input = new EditText(this);
        input.setHint("Paste JSON export here");
        input.setHintTextColor(Theme.TEXT_TERTIARY);
        input.setTextColor(Theme.TEXT_PRIMARY);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMinLines(5);
        int pad = Theme.dp(this, Theme.S5);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Import memories")
                .setMessage("Paste a JSON export. Duplicates are skipped.")
                .setView(input)
                .setPositiveButton("Import", (d, w) -> {
                    int added = memoryStore.importJson(input.getText().toString());
                    Toast.makeText(MemoryActivity.this,
                            added + " new memories imported.", Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
