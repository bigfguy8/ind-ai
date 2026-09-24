package com.nova.assistant;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

public final class PermissionCenter {

    public static final int REQ_MIC = 1001;

    public static boolean accessibilityEnabled(Context c) {
        if (NovaAccessibilityService.isRunning()) return true;
        String enabled = Settings.Secure.getString(
                c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        String pkg = c.getPackageName();
        String[] parts = enabled.split(":");
        for (String p : parts) {
            if (p.startsWith(pkg + "/")) return true;
        }
        return false;
    }

    public static boolean micGranted(Context c) {
        return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean notificationsGranted(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void openAccessibilitySettings(Context c) {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    public static void openAppDetails(Context c) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(android.net.Uri.parse("package:" + c.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    private PermissionCenter() {}
}
