package com.nova.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL = "nova_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        int id = intent.getIntExtra("id", 0);
        String text = intent.getStringExtra("text");
        if (text == null) text = "Reminder";

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "NOVA Reminders", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Reminders you asked NOVA to schedule.");
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPI = PendingIntent.getActivity(
                context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(context, CHANNEL);
        } else {
            b = new Notification.Builder(context);
        }
        b.setSmallIcon(R.drawable.ic_mic)
         .setContentTitle("NOVA Reminder")
         .setContentText(text)
         .setStyle(new Notification.BigTextStyle().bigText(text))
         .setAutoCancel(true)
         .setContentIntent(contentPI);

        nm.notify(id, b.build());
    }
}
