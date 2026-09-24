package com.nova.assistant;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownRenderer {

    private static final Pattern CODE_BLOCK =
            Pattern.compile("```([a-zA-Z0-9+#-]*)\\n?([\\s\\S]*?)```");
    private static final Pattern BOLD =
            Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__");
    private static final Pattern ITALIC =
            Pattern.compile("(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)|(?<!_)_([^_\\n]+?)_(?!_)");
    private static final Pattern INLINE_CODE =
            Pattern.compile("`([^`\\n]+?)`");
    private static final Pattern HEADING =
            Pattern.compile("(?m)^(#{1,3})\\s+(.+)$");
    private static final Pattern BULLET =
            Pattern.compile("(?m)^\\s*[-*]\\s+");

    private MarkdownRenderer() {}

    public static CharSequence render(String raw) {
        if (raw == null) return "";
        // Normalize code fences that may have single newlines
        String text = raw.replace("\r\n", "\n");

        SpannableString ss = new SpannableString(text);

        // --- code blocks (must run first; hides inline parsing inside) ---
        Matcher cb = CODE_BLOCK.matcher(text);
        while (cb.find()) {
            int start = cb.start();
            int end = cb.end();
            String body = cb.group(2);
            ss.setSpan(new TypefaceSpan("monospace"), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new BackgroundColorSpan(0xFF0A0F1A), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(0xFF7FDBFF), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new RelativeSizeSpan(0.92f), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // --- headings ---
        Matcher h = HEADING.matcher(text);
        while (h.find()) {
            int start = h.start();
            int end = h.end();
            int level = h.group(1).length();
            float scale = level == 1 ? 1.35f : level == 2 ? 1.2f : 1.1f;
            ss.setSpan(new RelativeSizeSpan(scale), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(Theme.TEXT_PRIMARY), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // --- bold ---
        Matcher b = BOLD.matcher(text);
        while (b.find()) {
            int s = b.start();
            int e = b.end();
            ss.setSpan(new StyleSpan(Typeface.BOLD), s, e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // --- italic ---
        Matcher it = ITALIC.matcher(text);
        while (it.find()) {
            int s = it.start();
            int e = it.end();
            ss.setSpan(new StyleSpan(Typeface.ITALIC), s, e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // --- inline code ---
        Matcher ic = INLINE_CODE.matcher(text);
        while (ic.find()) {
            int s = ic.start();
            int e = ic.end();
            ss.setSpan(new TypefaceSpan("monospace"), s, e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new BackgroundColorSpan(0xFF1A2030), s, e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(0xFF7FDBFF), s, e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // --- bullets: replace leading -/* with • ---
        // (Can't mutate the SpannableString without breaking spans, so we skip
        //  textual replacement and instead just bold the bullet mark.)
        Matcher bl = BULLET.matcher(text);
        while (bl.find()) {
            int s = bl.start();
            int e = bl.end();
            ss.setSpan(new ForegroundColorSpan(Theme.PRIMARY), s, e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new StyleSpan(Typeface.BOLD), s, e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return ss;
    }
}
