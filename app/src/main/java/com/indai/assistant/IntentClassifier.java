package com.indai.assistant;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic intent detection for simple requests.
 * Returns a Match when confident; null when the LLM should handle it.
 */
public final class IntentClassifier {

    public static class Match {
        public final String tool;
        public final JSONObject args;
        public final String confirmation; // what to show the user (may be null)
        public Match(String tool, JSONObject args, String confirmation) {
            this.tool = tool;
            this.args = args;
            this.confirmation = confirmation;
        }
    }

    private static final Pattern TIME = Pattern.compile(
            "(?i)\\b(what|which|tell).*\\b(time|clock|hour)\\b|^time\\??$");
    private static final Pattern DATE = Pattern.compile(
            "(?i)\\b(what|which|tell).*\\b(date|day|today)\\b|^date\\??$");
    private static final Pattern MATH = Pattern.compile(
            "^(?:what(?:'s| is)|calculate|compute|eval)\\s+([-0-9+*/().\\s^%]+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OPEN_X = Pattern.compile(
            "^(?:open|launch|start)\\s+([\\w .]+?)(?:\\s+app)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CALL_X = Pattern.compile(
            "^(?:call|dial)\\s+([+0-9\\-\\s()]{6,})$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_X = Pattern.compile(
            "^(?:search|google|look up)\\s+(?:for\\s+)?(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NAV_X = Pattern.compile(
            "^(?:navigate|directions|take me)\\s+(?:to\\s+)?(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAY_X = Pattern.compile(
            "^(?:play|put on)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);

    // App package lookup — common Android packages
    private static final String[][] KNOWN_APPS = {
            {"settings", "com.android.settings"},
            {"clock", "com.android.deskclock"},
            {"calendar", "com.android.calendar"},
            {"camera", "com.android.camera"},
            {"gallery", "com.android.gallery3d"},
            {"photos", "com.google.android.apps.photos"},
            {"contacts", "com.android.contacts"},
            {"messages", "com.android.messaging"},
            {"phone", "com.android.dialer"},
            {"browser", "com.android.chrome"},
            {"chrome", "com.android.chrome"},
            {"gmail", "com.google.android.gm"},
            {"maps", "com.google.android.apps.maps"},
            {"youtube", "com.google.android.youtube"},
            {"play store", "com.android.vending"},
            {"files", "com.android.documentsui"},
    };

    public static Match classify(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > 80) return null; // Too long for fast path

        // Skip anything with conjunctions — likely multi-step
        String lower = trimmed.toLowerCase();
        if (lower.contains(" and ") || lower.contains(" then ")
                || lower.contains(" after ") || lower.contains(" before ")) {
            return null;
        }

        try {
            if (TIME.matcher(trimmed).matches() || TIME.matcher(lower).find()) {
                return new Match(Tools.GET_TIME, new JSONObject(), null);
            }

            if (DATE.matcher(trimmed).matches() || DATE.matcher(lower).find()) {
                return new Match(Tools.GET_TIME, new JSONObject(), null);
            }

            Matcher mathM = MATH.matcher(trimmed);
            if (mathM.matches()) {
                String result = evalMath(mathM.group(1));
                if (result != null) {
                    JSONObject o = new JSONObject();
                    o.put("__inline_result__", result);
                    return new Match("__math__", o, result);
                }
            }

            Matcher callM = CALL_X.matcher(trimmed);
            if (callM.matches()) {
                JSONObject o = new JSONObject();
                o.put("phone", callM.group(1).replaceAll("[^0-9+]", ""));
                return new Match(Tools.MAKE_CALL, o, null);
            }

            Matcher openM = OPEN_X.matcher(trimmed);
            if (openM.matches()) {
                String name = openM.group(1).trim().toLowerCase();
                String pkg = lookupApp(name);
                if (pkg != null) {
                    JSONObject o = new JSONObject();
                    o.put("package", pkg);
                    return new Match(Tools.OPEN_APP, o, null);
                }
                // Unknown app name — not confident enough, let LLM handle
            }

            Matcher searchM = SEARCH_X.matcher(trimmed);
            if (searchM.matches() && searchM.group(1).length() > 2) {
                JSONObject o = new JSONObject();
                o.put("query", searchM.group(1).trim());
                return new Match(Tools.SEARCH_WEB, o, null);
            }

            Matcher navM = NAV_X.matcher(trimmed);
            if (navM.matches()) {
                JSONObject o = new JSONObject();
                o.put("location", navM.group(1).trim());
                return new Match(Tools.NAVIGATE_TO, o, null);
            }

            Matcher playM = PLAY_X.matcher(trimmed);
            if (playM.matches()) {
                JSONObject o = new JSONObject();
                o.put("query", playM.group(1).trim());
                return new Match(Tools.PLAY_MUSIC, o, null);
            }

        } catch (Exception ignored) {}

        return null; // Not confident — let the LLM handle it
    }

    private static String lookupApp(String name) {
        for (String[] row : KNOWN_APPS) {
            if (row[0].equals(name)) return row[1];
        }
        return null;
    }

    /** Very small math evaluator — supports + - * / % ^ and parentheses. */
    static String evalMath(String expr) {
        try {
            double result = new MathParser(expr).parse();
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        } catch (Exception e) {
            return null;
        }
    }

    /** Recursive descent math parser (no external dependencies). */
    private static class MathParser {
        private final String s;
        private int pos = 0;
        MathParser(String s) { this.s = s.replaceAll("\\s+", ""); }

        double parse() {
            double v = parseExpression();
            if (pos < s.length()) throw new RuntimeException("trailing");
            return v;
        }

        private double parseExpression() {
            double v = parseTerm();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '+') { pos++; v += parseTerm(); }
                else if (c == '-') { pos++; v -= parseTerm(); }
                else break;
            }
            return v;
        }

        private double parseTerm() {
            double v = parsePower();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '*') { pos++; v *= parsePower(); }
                else if (c == '/') { pos++; v /= parsePower(); }
                else if (c == '%') { pos++; v %= parsePower(); }
                else break;
            }
            return v;
        }

        private double parsePower() {
            double base = parseFactor();
            if (pos < s.length() && s.charAt(pos) == '^') {
                pos++;
                return Math.pow(base, parsePower());
            }
            return base;
        }

        private double parseFactor() {
            if (pos >= s.length()) throw new RuntimeException("eof");
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                double v = parseExpression();
                if (pos >= s.length() || s.charAt(pos) != ')')
                    throw new RuntimeException("no close paren");
                pos++;
                return v;
            }
            if (c == '-') { pos++; return -parseFactor(); }
            if (c == '+') { pos++; return parseFactor(); }
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos))
                    || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new RuntimeException("expected number");
            return Double.parseDouble(s.substring(start, pos));
        }
    }

    private IntentClassifier() {}
}
