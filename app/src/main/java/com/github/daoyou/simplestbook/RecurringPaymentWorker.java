package com.github.daoyou.simplestbook;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

public class RecurringPaymentWorker extends Worker {

    private static final String WORK_NAME = "recurring_payment_daily_check";
    private static final String CHANNEL_ID = "recurring_payment_channel";

    public RecurringPaymentWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                RecurringPaymentWorker.class, 24, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
        android.database.Cursor cursor = dbHelper.getAllRecurringPayments();
        LocalDate today = LocalDate.now();
        int todayKey = today.getYear() * 10000 + today.getMonthValue() * 100 + today.getDayOfMonth();

        if (cursor != null) {
            while (cursor.moveToNext()) {
                RecurringPayment item = readRecurringFromCursor(cursor);
                if (item == null) {
                    continue;
                }
                if (item.getLastRunDate() == todayKey) {
                    continue;
                }
                if (isDueToday(item, today)) {
                    dbHelper.insertRecord(
                            item.getAmount(),
                            item.getCategory(),
                            item.getNote(),
                            "",
                            System.currentTimeMillis(),
                            0.0,
                            0.0
                    );
                    dbHelper.updateRecurringLastRunDate(item.getId(), todayKey);
                    sendNotification(item);
                }
            }
            cursor.close();
        }
        return Result.success();
    }

    private RecurringPayment readRecurringFromCursor(android.database.Cursor cursor) {
        try {
            String id = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_ID));
            int amount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_AMOUNT));
            String category = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_CATEGORY));
            String note = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_NOTE));
            String frequency = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_FREQUENCY));
            int dayOfWeek = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_DAY_OF_WEEK));
            int dayOfMonth = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_DAY_OF_MONTH));
            int month = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_MONTH));
            int lastRunDate = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_LAST_RUN_DATE));
            return new RecurringPayment(id, amount, category, note, frequency, dayOfWeek, dayOfMonth, month, lastRunDate);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isDueToday(RecurringPayment item, LocalDate today) {
        String freq = item.getFrequency();
        if (RecurringPayment.FREQ_MINUTE.equals(freq)) {
            return false;
        }
        if (RecurringPayment.FREQ_DAILY.equals(freq)) {
            return true;
        }
        if (RecurringPayment.FREQ_WEEKLY.equals(freq)) {
            return today.getDayOfWeek().getValue() == item.getDayOfWeek();
        }
        if (RecurringPayment.FREQ_MONTHLY.equals(freq)) {
            return today.getDayOfMonth() == item.getDayOfMonth();
        }
        if (RecurringPayment.FREQ_YEARLY.equals(freq)) {
            return today.getMonthValue() == item.getMonth()
                    && today.getDayOfMonth() == item.getDayOfMonth();
        }
        return false;
    }

    private void sendNotification(RecurringPayment item) {
        android.content.SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(SettingsActivity.KEY_RECURRING_NOTIFY_ENABLED, true)) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        NotificationManager manager =
                (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "週期性記帳提醒", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }

        String title = "已新增週期性記帳";
        String content = item.getNote() + " · $" + item.getAmount();
        android.content.Intent intent = new android.content.Intent(getApplicationContext(), HistoryActivity.class);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                getApplicationContext(),
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cycle)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        manager.notify((int) (System.currentTimeMillis() % 100000), builder.build());
    }
}
