package com.indai.assistant;

import android.content.Context;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Verifies that tool actions actually had the intended effect.
 *
 * The verifier only checks tools where verification is reliable:
 *   - open_app   -> confirm the target package is now foreground
 *   - click_text -> confirm the target text is gone OR screen changed
 *   - type_text  -> confirm the focused field contains the text
 *   - press_home -> confirm no package has focus (or launcher)
 *
 * For tools where verification is impossible (open_url, send_sms,
 * set_alarm, etc.), the verifier returns UNVERIFIABLE and the caller
 * trusts the tool's self-report.
 */
public final class ToolVerifier {

    public enum Result { VERIFIED, FAILED, UNVERIFIABLE }

    public static class Outcome {
        public final Result result;
        public final String detail;

        public Outcome(Result result, String detail) {
            this.result = result;
            this.detail = detail;
        }

        public boolean isFailed() { return result == Result.FAILED; }
        public boolean isVerified() { return result == Result.VERIFIED; }
        public boolean isUnverifiable() { return result == Result.UNVERIFIABLE; }
    }

    /** Delay before reading state — lets an app come to foreground. */
    public static final long OPEN_APP_WAIT_MS = 900L;
    public static final long CLICK_WAIT_MS = 300L;

    public static Outcome verify(Context ctx, Tools.Call call, String toolResult) {
        if (call == null) return new Outcome(Result.UNVERIFIABLE, "No call");

        // If the tool already self-reported failure, we don't need to verify
        if (toolResult == null || looksLikeFailure(toolResult)) {
            return new Outcome(Result.UNVERIFIABLE, "Tool self-reported failure");
        }

        IndAIAccessibilityService svc = IndAIAccessibilityService.get();
        if (svc == null) {
            return new Outcome(Result.UNVERIFIABLE, "No accessibility service");
        }

        try {
            switch (call.name) {
                case Tools.OPEN_APP:
                    return verifyOpenApp(svc, call);

                case Tools.CLICK_TEXT:
                    return verifyClickText(svc, call, toolResult);

                case Tools.TYPE_TEXT:
                    return verifyTypeText(svc, call);

                case Tools.PRESS_HOME:
                    return verifyHome(svc, ctx);

                default:
                    // open_url, send_sms, make_call, alarm, navigate, share,
                    // search, music, get_time, reminders, etc. — not verifiable
                    return new Outcome(Result.UNVERIFIABLE, "Not verifiable");
            }
        } catch (Exception e) {
            return new Outcome(Result.UNVERIFIABLE,
                    "Verifier error: " + e.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------
    //  Per-tool verification
    // ------------------------------------------------------------------

    private static Outcome verifyOpenApp(IndAIAccessibilityService svc, Tools.Call call) {
        String wantPkg = call.args.optString("package", "").trim();
        if (wantPkg.isEmpty()) {
            return new Outcome(Result.UNVERIFIABLE, "No package in call");
        }
        sleep(OPEN_APP_WAIT_MS);
        String active = svc.getActiveAppPackage();
        if (active == null) {
            return new Outcome(Result.UNVERIFIABLE, "No active window");
        }
        if (active.equals(wantPkg)) {
            return new Outcome(Result.VERIFIED, "Confirmed " + wantPkg + " is foreground");
        }
        return new Outcome(Result.FAILED,
                "Expected " + wantPkg + ", foreground is " + active);
    }

    private static Outcome verifyClickText(IndAIAccessibilityService svc,
                                            Tools.Call call, String toolResult) {
        String text = call.args.optString("text", "").trim();
        if (text.isEmpty()) {
            return new Outcome(Result.UNVERIFIABLE, "No text in call");
        }
        // If the tool already said "Could not find or tap", treat as tool-failure
        if (toolResult != null && toolResult.toLowerCase().contains("could not")) {
            return new Outcome(Result.UNVERIFIABLE, "Tool self-reported miss");
        }
        sleep(CLICK_WAIT_MS);
        AccessibilityNodeInfo node = svc.findByText(text);
        if (node == null) {
            // Text gone — click likely worked (dialog opened, item selected, etc.)
            return new Outcome(Result.VERIFIED, "Text \"" + text + "\" no longer on screen");
        }
        // Text still present — click may or may not have worked
        return new Outcome(Result.UNVERIFIABLE,
                "Text \"" + text + "\" still visible after click");
    }

    private static Outcome verifyTypeText(IndAIAccessibilityService svc,
                                           Tools.Call call) {
        String text = call.args.optString("text", "").trim();
        if (text.isEmpty()) {
            return new Outcome(Result.UNVERIFIABLE, "No text in call");
        }
        sleep(200);
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) {
            return new Outcome(Result.UNVERIFIABLE, "No active window");
        }
        AccessibilityNodeInfo focused = root.findFocus(
                AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            return new Outcome(Result.FAILED, "No focused field after type_text");
        }
        CharSequence current = focused.getText();
        if (current != null && current.toString().contains(text)) {
            return new Outcome(Result.VERIFIED, "Text confirmed in focused field");
        }
        return new Outcome(Result.FAILED,
                "Focused field does not contain typed text");
    }

    private static Outcome verifyHome(IndAIAccessibilityService svc, Context ctx) {
        sleep(400);
        String active = svc.getActiveAppPackage();
        if (active == null) {
            // Launcher sometimes returns null — accept as "home"
            return new Outcome(Result.VERIFIED, "Reached home");
        }
        // Launcher package varies by device — if it's not our app, treat as success
        if (!active.equals(ctx.getPackageName())) {
            return new Outcome(Result.VERIFIED, "Foreground is " + active);
        }
        return new Outcome(Result.FAILED, "Still on Ind AI");
    }

    // ------------------------------------------------------------------

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static boolean looksLikeFailure(String result) {
        if (result == null) return true;
        String r = result.toLowerCase();
        return r.startsWith("could not")
                || r.startsWith("couldn't")
                || r.startsWith("no ")
                || r.contains("not found")
                || r.contains("not enabled")
                || r.contains("disconnected")
                || r.contains("screenshot")
                || r.startsWith("tool error")
                || r.contains("__blocked_");
    }

    private ToolVerifier() {}
}
