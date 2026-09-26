package com.indai.assistant;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class IndAIAccessibilityService extends AccessibilityService {

    private static volatile IndAIAccessibilityService INSTANCE;

    public interface ScreenshotCallback {
        void onBitmap(Bitmap bitmap);
        void onError(String reason);
    }

    public static class Clickable {
        public final String label;
        public final String description;
        public final int centerX;
        public final int centerY;
        public final String className;
        public Clickable(String label, String description,
                         int cx, int cy, String cls) {
            this.label = label;
            this.description = description;
            this.centerX = cx;
            this.centerY = cy;
            this.className = cls;
        }
        public String toLine() {
            StringBuilder sb = new StringBuilder();
            sb.append("- ");
            if (label != null && !label.isEmpty()) sb.append("\"").append(label).append("\"");
            else if (description != null && !description.isEmpty())
                sb.append("[").append(description).append("]");
            else sb.append("(unlabeled)");
            sb.append(" @ ").append(centerX).append(",").append(centerY);
            return sb.toString();
        }
    }

    public static IndAIAccessibilityService get() { return INSTANCE; }
    public static boolean isRunning() { return INSTANCE != null; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        INSTANCE = null;
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    //  Node search
    // ------------------------------------------------------------------

    public AccessibilityNodeInfo findByText(String text) {
        if (text == null || text.isEmpty()) return null;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        return bfs(root, node -> {
            CharSequence t = node.getText();
            if (t != null && t.toString().toLowerCase().contains(text.toLowerCase())) return true;
            CharSequence c = node.getContentDescription();
            return c != null && c.toString().toLowerCase().contains(text.toLowerCase());
        });
    }

    public AccessibilityNodeInfo findByDescription(String desc) {
        if (desc == null || desc.isEmpty()) return null;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        return bfs(root, node -> {
            CharSequence c = node.getContentDescription();
            return c != null && c.toString().toLowerCase().contains(desc.toLowerCase());
        });
    }

    public boolean clickAt(int x, int y) {
        if (Build.VERSION.SDK_INT < 24) return false;
        try {
            android.accessibilityservice.GestureDescription.Builder b =
                    new android.accessibilityservice.GestureDescription.Builder();
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(x, y);
            b.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(
                    path, 0, 60));
            return dispatchGesture(b.build(), null, null);
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    //  Action helpers
    // ------------------------------------------------------------------

    public boolean clickNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        AccessibilityNodeInfo clickable = node;
        while (clickable != null && !clickable.isClickable()) {
            clickable = clickable.getParent();
        }
        if (clickable == null) return false;
        return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    public boolean setTextOnFocused(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null || !focused.isEditable()) return false;
        Bundle args = new Bundle();
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    public boolean scrollForward() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo scrollable = bfs(root, AccessibilityNodeInfo::isScrollable);
        if (scrollable == null) return false;
        return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    public boolean scrollBackward() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo scrollable = bfs(root, AccessibilityNodeInfo::isScrollable);
        if (scrollable == null) return false;
        return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }

    public boolean globalBack() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public boolean globalHome() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public boolean globalRecents() {
        return performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    // ------------------------------------------------------------------
    //  Screen reading
    // ------------------------------------------------------------------

    public String describeScreen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "(no active window)";
        StringBuilder sb = new StringBuilder();
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int guard = 0;
        while (!q.isEmpty() && guard++ < 400) {
            AccessibilityNodeInfo n = q.poll();
            if (n == null) continue;
            CharSequence t = n.getText();
            CharSequence d = n.getContentDescription();
            if ((t != null && t.length() > 0) || (d != null && d.length() > 0)) {
                nodes.add(n);
            }
            int c = n.getChildCount();
            for (int i = 0; i < c; i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) q.add(ch);
            }
        }
        for (AccessibilityNodeInfo n : nodes) {
            if (sb.length() > 1400) { sb.append("..."); break; }
            CharSequence t = n.getText();
            if (t != null && t.length() > 0) {
                sb.append("- ").append(t).append('\n');
            } else {
                CharSequence d = n.getContentDescription();
                if (d != null && d.length() > 0) sb.append("[icon] ").append(d).append('\n');
            }
        }
        return sb.length() == 0 ? "(no readable text on screen)" : sb.toString();
    }

    public String describeClickableElements() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "(no active window)";
        List<Clickable> items = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int guard = 0;
        android.graphics.Rect r = new android.graphics.Rect();
        while (!q.isEmpty() && guard++ < 1000) {
            AccessibilityNodeInfo n = q.poll();
            if (n == null) continue;
            if (n.isClickable() && n.isVisibleToUser()) {
                n.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0) {
                    String label = n.getText() == null ? "" : n.getText().toString();
                    String desc = n.getContentDescription() == null
                            ? "" : n.getContentDescription().toString();
                    String cls = n.getClassName() == null ? "" : n.getClassName().toString();
                    items.add(new Clickable(label, desc,
                            r.centerX(), r.centerY(), cls));
                }
            }
            int c = n.getChildCount();
            for (int i = 0; i < c; i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) q.add(ch);
            }
        }
        if (items.isEmpty()) return "(no tappable elements found)";
        StringBuilder sb = new StringBuilder("Tappable elements on screen:\n");
        int shown = 0;
        for (Clickable it : items) {
            if (shown++ > 30) { sb.append("... (more)\n"); break; }
            sb.append(it.toLine()).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    //  Screenshot (Android 11+ / API 30+)
    // ------------------------------------------------------------------

    public String getActiveAppPackage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        CharSequence pkg = root.getPackageName();
        return pkg == null ? null : pkg.toString();
    }

    public boolean canScreenshot() {
        return Build.VERSION.SDK_INT >= 30;
    }

    public void takeScreenshotBitmap(final ScreenshotCallback cb) {
        if (Build.VERSION.SDK_INT < 30) {
            cb.onError("Screenshots require Android 11 or newer.");
            return;
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY,
                    getMainExecutor(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult result) {
                            HardwareBuffer buf = result.getHardwareBuffer();
                            try {
                                Bitmap bmp = Bitmap.wrapHardwareBuffer(
                                        buf, result.getColorSpace());
                                if (bmp == null) {
                                    cb.onError("Could not wrap buffer.");
                                    return;
                                }
                                Bitmap copy = bmp.copy(Bitmap.Config.ARGB_8888, false);
                                bmp.recycle();
                                if (copy == null) {
                                    cb.onError("Could not copy bitmap.");
                                    return;
                                }
                                cb.onBitmap(copy);
                            } finally {
                                buf.close();
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            cb.onError("Screenshot failed, code " + errorCode
                                    + " (secure windows cannot be captured).");
                        }
                    });
        } catch (Exception e) {
            cb.onError("Screenshot exception: " + e.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------
    //  BFS utility
    // ------------------------------------------------------------------

    private interface NodeFilter {
        boolean matches(AccessibilityNodeInfo node);
    }

    private AccessibilityNodeInfo bfs(AccessibilityNodeInfo root, NodeFilter filter) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int guard = 0;
        while (!queue.isEmpty() && guard++ < 4000) {
            AccessibilityNodeInfo node = queue.poll();
            if (node == null) continue;
            if (filter.matches(node)) return node;
            int n = node.getChildCount();
            for (int i = 0; i < n; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }
}
