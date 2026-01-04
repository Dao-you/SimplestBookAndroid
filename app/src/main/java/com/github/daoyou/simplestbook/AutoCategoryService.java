package com.github.daoyou.simplestbook;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoCategoryService extends Service {

    public static final String ACTION_RECORD_UPDATED = "com.github.daoyou.simplestbook.ACTION_RECORD_UPDATED";
    private static final String EXTRA_RECORD_ID = "extra_record_id";
    private static final String EXTRA_CATEGORY = "extra_category";
    private static final String EXTRA_AMOUNT = "extra_amount";
    private static final String EXTRA_NOTE = "extra_note";
    private static final String EXTRA_OPTIONS = "extra_options";

    private static final String TAG = "AutoCategory";

    private ExecutorService executor;

    public static void start(Context context, String recordId, int amount, String note, ArrayList<String> options) {
        Intent intent = new Intent(context, AutoCategoryService.class);
        intent.putExtra(EXTRA_RECORD_ID, recordId);
        intent.putExtra(EXTRA_AMOUNT, amount);
        intent.putExtra(EXTRA_NOTE, note);
        intent.putStringArrayListExtra(EXTRA_OPTIONS, options);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        String recordId = intent.getStringExtra(EXTRA_RECORD_ID);
        int amount = intent.getIntExtra(EXTRA_AMOUNT, 0);
        String note = intent.getStringExtra(EXTRA_NOTE);
        ArrayList<String> options = intent.getStringArrayListExtra(EXTRA_OPTIONS);

        if (recordId == null || recordId.isEmpty()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        executor.execute(() -> {
            Log.d(TAG, "Auto select start, recordId=" + recordId);
            SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
            String apiKey = prefs.getString(SettingsActivity.KEY_AUTO_CATEGORY_API_KEY, "");
            String apiUrl = prefs.getString(SettingsActivity.KEY_AUTO_CATEGORY_API_URL, "");
            List<String> optionList = options == null ? new ArrayList<>() : options;

            String selected = AutoCategoryClient.requestAutoCategory(
                    prefs, apiKey, apiUrl, amount, note, optionList);
            if (selected == null || selected.isEmpty()) {
                selected = "其他";
                Log.d(TAG, "Fallback to 其他");
            }

            DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
            dbHelper.updateRecordCategory(recordId, selected);
            CloudBackupManager.requestSyncIfEnabled(getApplicationContext());
            Log.d(TAG, "Auto select updated: " + selected);

            Intent updateIntent = new Intent(ACTION_RECORD_UPDATED);
            updateIntent.putExtra(EXTRA_RECORD_ID, recordId);
            updateIntent.putExtra(EXTRA_CATEGORY, selected);
            updateIntent.setPackage(getPackageName());
            Log.d(TAG, "Sending update broadcast");
            sendBroadcast(updateIntent);
            stopSelf(startId);
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
