package com.github.daoyou.simplestbook;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class RecurringPaymentAlarmReceiver extends BroadcastReceiver {

    private static final String EXTRA_RECURRING_ID = "recurring_id";
    private static final String CHANNEL_ID = "recurring_payment_channel";

    public static void schedule(Context context, String recurringId) {
        scheduleNext(context, recurringId, System.currentTimeMillis() + 60_000L);
    }

    public static void scheduleAllMinute(Context context) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        android.database.Cursor cursor = dbHelper.getAllRecurringPayments();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String frequency = cursor.getString(
                        cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_FREQUENCY));
                if (!RecurringPayment.FREQ_MINUTE.equals(frequency)) {
                    continue;
                }
                String id = cursor.getString(
                        cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_ID));
                if (id != null && !id.isEmpty()) {
                    scheduleNext(context, id, System.currentTimeMillis() + 60_000L);
                }
            }
            cursor.close();
        }
    }

    private static void scheduleNext(Context context, String recurringId, long triggerAt) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                return;
            }
        }
        Intent intent = new Intent(context, RecurringPaymentAlarmReceiver.class);
        intent.putExtra(EXTRA_RECURRING_ID, recurringId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                recurringId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String recurringId = intent.getStringExtra(EXTRA_RECURRING_ID);
        if (recurringId == null || recurringId.isEmpty()) {
            return;
        }
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        android.database.Cursor cursor = dbHelper.getRecurringPaymentById(recurringId);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int amount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_AMOUNT));
                String category = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_CATEGORY));
                String note = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_NOTE));
                dbHelper.insertRecord(amount, category, note, "", System.currentTimeMillis(), 0.0, 0.0);
                sendNotification(context, note, amount);
                scheduleNext(context, recurringId, System.currentTimeMillis() + 60_000L);
            }
            cursor.close();
        }
    }

    private void sendNotification(Context context, String note, int amount) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(SettingsActivity.KEY_RECURRING_NOTIFY_ENABLED, true)) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "週期性記帳提醒", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }

        String title = "已新增週期性記帳";
        String content = note + " · $" + amount;
        android.content.Intent intent = new android.content.Intent(context, HistoryActivity.class);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cycle)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        manager.notify((int) (System.currentTimeMillis() % 100000), builder.build());
    }
}
