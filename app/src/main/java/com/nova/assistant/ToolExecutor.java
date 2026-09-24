package com.nova.assistant;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.accessibility.AccessibilityNodeInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ToolExecutor {

    public interface Listener {
        void onNeedsConfirmation(String summary, Runnable confirmAction);
        void onResult(String result);
    }

    public static void execute(final Context ctx, final Tools.Call call, final Listener listener) {
        if (call == null) { listener.onResult("Empty tool call."); return; }
        if (!Tools.isKnown(call.name)) {
            listener.onResult("Refused: unknown tool '" + call.name + "'");
            return;
        }
        if (!NovaAccessibilityService.isRunning()) {
            listener.onResult("Accessibility service is not enabled. "
                    + "Open Ind AI -> Settings -> Capabilities and enable it.");
            return;
        }
        if (Tools.isDangerous(call.name)) {
            listener.onNeedsConfirmation(describe(call),
                    () -> runNow(ctx, call, listener));
        } else {
            runNow(ctx, call, listener);
        }
    }

    private static void runNow(Context ctx, Tools.Call call, Listener listener) {
        NovaAccessibilityService svc = NovaAccessibilityService.get();
        if (svc == null) {
            listener.onResult("Accessibility service disconnected.");
            return;
        }
        try {
            switch (call.name) {

                case Tools.OPEN_APP: {
                    String pkg = call.args.optString("package", "").trim();
                    if (pkg.isEmpty()) { listener.onResult("open_app: missing package"); return; }
                    Intent i = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
                    if (i == null) {
                        listener.onResult("App not installed: " + pkg);
                        return;
                    }
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    listener.onResult("Opened " + pkg);
                    return;
                }

                case Tools.OPEN_URL: {
                    String url = call.args.optString("url", "").trim();
                    if (url.isEmpty()) { listener.onResult("open_url: missing url"); return; }
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://" + url;
                    }
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    listener.onResult("Opened URL in browser.");
                    return;
                }

                case Tools.CLICK_TEXT: {
                    String t = call.args.optString("text", "").trim();
                    if (t.isEmpty()) { listener.onResult("click_text: missing text"); return; }
                    AccessibilityNodeInfo node = svc.findByText(t);
                    boolean ok = svc.clickNode(node);
                    listener.onResult(ok
                            ? "Tapped element containing \"" + t + "\"."
                            : "Could not find or tap \"" + t + "\".");
                    return;
                }

                case Tools.CLICK_DESCRIPTION: {
                    String d = call.args.optString("description", "").trim();
                    if (d.isEmpty()) { listener.onResult("click_description: missing description"); return; }
                    AccessibilityNodeInfo node = svc.findByDescription(d);
                    boolean ok = svc.clickNode(node);
                    listener.onResult(ok
                            ? "Tapped [" + d + "]."
                            : "Could not find or tap [" + d + "].");
                    return;
                }

                case Tools.CLICK_AT: {
                    int x = call.args.optInt("x", -1);
                    int y = call.args.optInt("y", -1);
                    if (x < 0 || y < 0) {
                        listener.onResult("click_at: needs x and y coordinates");
                        return;
                    }
                    boolean ok = svc.clickAt(x, y);
                    listener.onResult(ok
                            ? "Tapped at (" + x + ", " + y + ")."
                            : "Could not tap at (" + x + ", " + y + ").");
                    return;
                }

                case Tools.TYPE_TEXT: {
                    String t = call.args.optString("text", "");
                    if (t.isEmpty()) { listener.onResult("type_text: missing text"); return; }
                    boolean ok = svc.setTextOnFocused(t);
                    listener.onResult(ok
                            ? "Typed text into focused field."
                            : "No focused editable field on screen.");
                    return;
                }

                case Tools.SCROLL: {
                    String dir = call.args.optString("direction", "down");
                    boolean ok = "up".equalsIgnoreCase(dir)
                            ? svc.scrollBackward()
                            : svc.scrollForward();
                    listener.onResult(ok
                            ? "Scrolled " + dir + "."
                            : "Could not scroll — no scrollable view found.");
                    return;
                }

                case Tools.PRESS_BACK: {
                    boolean ok = svc.globalBack();
                    listener.onResult(ok ? "Pressed back." : "Back gesture not permitted.");
                    return;
                }

                case Tools.PRESS_HOME: {
                    boolean ok = svc.globalHome();
                    listener.onResult(ok ? "Pressed home." : "Home gesture not permitted.");
                    return;
                }

                case Tools.READ_SCREEN: {
                    String pkg = svc.getActiveAppPackage();
                    if (pkg != null && pkg.equals(ctx.getPackageName())) {
                        listener.onResult(
                            "Ind AI is on screen. Press home first, or open the target "
                            + "app before reading the screen.");
                        return;
                    }
                    listener.onResult(svc.describeScreen());
                    return;
                }

                case Tools.LIST_TAPPABLE: {
                    String pkg = svc.getActiveAppPackage();
                    if (pkg != null && pkg.equals(ctx.getPackageName())) {
                        listener.onResult(
                            "Ind AI is on screen. Press home first, or open the target "
                            + "app before listing tappable elements.");
                        return;
                    }
                    listener.onResult(svc.describeClickableElements());
                    return;
                }

                case Tools.SEE_SCREEN: {
                    String pkg = svc.getActiveAppPackage();
                    if (pkg != null && pkg.equals(ctx.getPackageName())) {
                        listener.onResult(
                            "Ind AI is on screen. Press home first, or open the target "
                            + "app before capturing the screen.");
                        return;
                    }
                    if (!svc.canScreenshot()) {
                        listener.onResult("Screenshots require Android 11+. "
                                + "Use list_tappable or read_screen instead.");
                        return;
                    }
                    listener.onResult("__SEE_SCREEN_PENDING__");
                    final ToolExecutor.Listener lf = listener;
                    svc.takeScreenshotBitmap(new NovaAccessibilityService.ScreenshotCallback() {
                        @Override public void onBitmap(android.graphics.Bitmap bmp) {
                            try {
                                java.io.File f = new java.io.File(
                                        ctx.getCacheDir(), "see_screen.jpg");
                                java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, fos);
                                fos.close();
                                bmp.recycle();
                                String tappable = svc.describeClickableElements();
                                lf.onResult("__SEE_SCREEN_PATH__" + f.getAbsolutePath()
                                        + "\n\n" + tappable);
                            } catch (Exception e) {
                                lf.onResult("Screenshot save failed: " + e.getMessage());
                            }
                        }
                        @Override public void onError(String reason) {
                            lf.onResult("Screenshot unavailable: " + reason
                                    + "\n\n" + svc.describeClickableElements());
                        }
                    });
                    return;
                }

                case Tools.SET_REMINDER: {
                    String when = call.args.optString("when", "").trim();
                    String text = call.args.optString("text", "").trim();
                    if (when.isEmpty() || text.isEmpty()) {
                        listener.onResult("set_reminder: needs when and text");
                        return;
                    }
                    long at = parseWhen(when);
                    if (at <= 0) {
                        listener.onResult("set_reminder: could not parse time \"" + when + "\"");
                        return;
                    }
                    int id = ReminderScheduler.schedule(ctx, at, text);
                    if (id < 0) {
                        listener.onResult("set_reminder: failed to schedule");
                        return;
                    }
                    String pretty = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                            .format(new Date(at));
                    listener.onResult("Reminder #" + id + " set for " + pretty + ": " + text);
                    return;
                }

                case Tools.LIST_REMINDERS: {
                    List<ReminderScheduler.Item> items = ReminderScheduler.list(ctx);
                    if (items.isEmpty()) {
                        listener.onResult("No reminders scheduled.");
                        return;
                    }
                    StringBuilder sb = new StringBuilder("Scheduled reminders:\n");
                    SimpleDateFormat fmt = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());
                    for (ReminderScheduler.Item it : items) {
                        sb.append("#").append(it.id).append("  ")
                          .append(fmt.format(new Date(it.atMillis)))
                          .append("  ").append(it.text).append("\n");
                    }
                    listener.onResult(sb.toString());
                    return;
                }

                case Tools.CANCEL_REMINDER: {
                    int id = call.args.optInt("id", -1);
                    if (id < 0) {
                        listener.onResult("cancel_reminder: needs id");
                        return;
                    }
                    ReminderScheduler.cancel(ctx, id);
                    listener.onResult("Cancelled reminder #" + id);
                    return;
                }

                case Tools.SEND_SMS: {
                    String phone = call.args.optString("phone", "").trim();
                    String msg = call.args.optString("message", "").trim();
                    if (phone.isEmpty() || msg.isEmpty()) {
                        listener.onResult("send_sms: needs phone and message");
                        return;
                    }
                    Intent i = new Intent(Intent.ACTION_SENDTO,
                            Uri.parse("smsto:" + phone));
                    i.putExtra("sms_body", msg);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        ctx.startActivity(i);
                        listener.onResult("SMS composer opened for " + phone
                                + ". Tap send to confirm.");
                    } catch (Exception e) {
                        listener.onResult("No SMS app available.");
                    }
                    return;
                }

                case Tools.MAKE_CALL: {
                    String phone = call.args.optString("phone", "").trim();
                    if (phone.isEmpty()) {
                        listener.onResult("make_call: needs phone");
                        return;
                    }
                    Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        ctx.startActivity(i);
                        listener.onResult("Dialer opened for " + phone + ".");
                    } catch (Exception e) {
                        listener.onResult("No dialer available.");
                    }
                    return;
                }

                case Tools.SET_ALARM: {
                    String time = call.args.optString("time", "").trim();
                    String label = call.args.optString("label", "Ind AI alarm").trim();
                    if (time.isEmpty()) {
                        listener.onResult("set_alarm: needs time");
                        return;
                    }
                    long at = parseWhen(time);
                    if (at <= 0) {
                        listener.onResult("set_alarm: could not parse time \"" + time + "\"");
                        return;
                    }
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTimeInMillis(at);
                    int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
                    int minute = cal.get(java.util.Calendar.MINUTE);
                    Intent i = new Intent(android.provider.AlarmClock.ACTION_SET_ALARM);
                    i.putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour);
                    i.putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute);
                    i.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        ctx.startActivity(i);
                        listener.onResult(String.format(
                                "Alarm set for %02d:%02d — %s", hour, minute, label));
                    } catch (Exception e) {
                        listener.onResult("Could not open alarm app.");
                    }
                    return;
                }

                case Tools.NAVIGATE_TO: {
                    String loc = call.args.optString("location", "").trim();
                    if (loc.isEmpty()) {
                        listener.onResult("navigate_to: needs location");
                        return;
                    }
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("geo:0,0?q=" + Uri.encode(loc)));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        ctx.startActivity(i);
                        listener.onResult("Opened Maps for: " + loc);
                    } catch (Exception e) {
                        listener.onResult("No Maps app available.");
                    }
                    return;
                }

                case Tools.SHARE_TEXT: {
                    String text = call.args.optString("text", "").trim();
                    if (text.isEmpty()) {
                        listener.onResult("share_text: needs text");
                        return;
                    }
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    Intent chooser = Intent.createChooser(i, "Share");
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        ctx.startActivity(chooser);
                        listener.onResult("Share sheet opened.");
                    } catch (Exception e) {
                        listener.onResult("No share target available.");
                    }
                    return;
                }

                case Tools.SEARCH_WEB: {
                    String q = call.args.optString("query", "").trim();
                    if (q.isEmpty()) {
                        listener.onResult("search_web: needs query");
                        return;
                    }
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        ctx.startActivity(i);
                        listener.onResult("Opened search for: " + q);
                    } catch (Exception e) {
                        listener.onResult("No browser available.");
                    }
                    return;
                }

                case Tools.PLAY_MUSIC: {
                    String q = call.args.optString("query", "").trim();
                    Intent i;
                    if (q.isEmpty()) {
                        i = new Intent(android.content.Intent.ACTION_MAIN);
                        i.addCategory(android.content.Intent.CATEGORY_APP_MUSIC);
                    } else {
                        i = new Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH");
                        i.putExtra(android.app.SearchManager.QUERY, q);
                    }
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        ctx.startActivity(i);
                        listener.onResult(q.isEmpty()
                                ? "Opened music player."
                                : "Playing: " + q);
                    } catch (Exception e) {
                        listener.onResult("No music app available.");
                    }
                    return;
                }

                case Tools.GET_TIME: {
                    java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                            "EEEE, MMM d, yyyy · HH:mm", java.util.Locale.getDefault());
                    listener.onResult(fmt.format(new java.util.Date()));
                    return;
                }

                default:
                    listener.onResult("Unsupported tool: " + call.name);
            }
        } catch (Exception e) {
            listener.onResult("Tool error: "
                    + e.getClass().getSimpleName() + " — " + e.getMessage());
        }
    }

    private static String describe(Tools.Call call) {
        return call.name + " " + call.args.toString();
    }

    private static long parseWhen(String s) {
        s = s.trim();
        try {
            if (s.startsWith("+")) {
                long now = System.currentTimeMillis();
                char unit = s.charAt(s.length() - 1);
                int n = Integer.parseInt(s.substring(1, s.length() - 1).trim());
                switch (unit) {
                    case 's': return now + n * 1000L;
                    case 'm': return now + n * 60_000L;
                    case 'h': return now + n * 3_600_000L;
                    case 'd': return now + n * 86_400_000L;
                }
            }
            String[] patterns = {
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss",
                "MMM d, yyyy HH:mm"
            };
            for (String pat : patterns) {
                try {
                    SimpleDateFormat f = new SimpleDateFormat(pat, Locale.getDefault());
                    Date d = f.parse(s);
                    if (d != null) return d.getTime();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private ToolExecutor() {}
}
