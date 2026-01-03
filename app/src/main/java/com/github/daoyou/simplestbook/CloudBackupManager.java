package com.github.daoyou.simplestbook;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class CloudBackupManager {

    private static final String TAG = "CloudBackup";
    public static final int STATUS_IDLE = 0;
    public static final int STATUS_SYNCING = 1;
    public static final int STATUS_SUCCESS = 2;
    public static final int STATUS_ERROR = 3;

    public static final String KEY_CLOUD_BACKUP_STATUS = "cloud_backup_status";
    public static final String KEY_CLOUD_BACKUP_LAST_SYNC = "cloud_backup_last_sync";
    public static final String KEY_CLOUD_BACKUP_LAST_ERROR = "cloud_backup_last_error";
    private static final String KEY_CLOUD_BACKUP_EXPECTED_RECORDS = "cloud_backup_expected_records";
    private static final String KEY_CLOUD_BACKUP_EXPECTED_CATEGORIES = "cloud_backup_expected_categories";
    private static final String KEY_CLOUD_BACKUP_VERIFY_ATTEMPTS = "cloud_backup_verify_attempts";
    private static final long VERIFY_INTERVAL_MS = 3000L;
    private static final int VERIFY_MAX_ATTEMPTS = 3;
    private static final AtomicBoolean VERIFY_IN_FLIGHT = new AtomicBoolean(false);
    private CloudBackupManager() {}

    public interface RestoreCallback {
        void onComplete(boolean success, String message, int recordCount, int categoryCount);
    }

    public static void requestSyncIfEnabled(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (!prefs.getBoolean(SettingsActivity.KEY_CLOUD_BACKUP_ENABLED, false)) {
            return;
        }
        syncNow(context);
    }

    public static void syncNow(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = getPrefs(appContext);
        if (!prefs.getBoolean(SettingsActivity.KEY_CLOUD_BACKUP_ENABLED, false)) {
            updateStatus(prefs, STATUS_IDLE, null);
            return;
        }

        updateStatus(prefs, STATUS_SYNCING, null);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            updateStatus(prefs, STATUS_ERROR, "not signed in");
            return;
        }
        uploadBackup(appContext, user.getUid(), prefs);
    }

    public static void markDisabled(Context context) {
        SharedPreferences prefs = getPrefs(context);
        updateStatus(prefs, STATUS_IDLE, null);
    }

    public static void restoreFromCloud(Context context, boolean overwrite, RestoreCallback callback) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = getPrefs(appContext);
        if (!prefs.getBoolean(SettingsActivity.KEY_CLOUD_BACKUP_ENABLED, false)) {
            updateStatus(prefs, STATUS_IDLE, null);
            postRestoreResult(callback, false, "cloud backup disabled", 0, 0);
            return;
        }

        updateStatus(prefs, STATUS_SYNCING, null);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            updateStatus(prefs, STATUS_ERROR, "not signed in");
            postRestoreResult(callback, false, "not signed in", 0, 0);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .collection("backups")
                .document("latest")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Object raw = snapshot.get("records");
                    Object rawCategories = snapshot.get("categories");
                    if (!(raw instanceof List)) {
                        updateStatus(prefs, STATUS_ERROR, "empty backup");
                        postRestoreResult(callback, false, "empty backup", 0, 0);
                        return;
                    }
                    List<?> list = (List<?>) raw;
                    List<?> categories = rawCategories instanceof List ? (List<?>) rawCategories : null;
                    new Thread(() -> {
                        int recordCount = restoreRecords(appContext, list, overwrite);
                        int categoryCount = restoreCategories(appContext, categories, overwrite);
                        updateStatus(prefs, STATUS_SUCCESS, null);
                        postRestoreResult(callback, true, "ok", recordCount, categoryCount);
                    }).start();
                })
                .addOnFailureListener(e -> {
                    updateStatus(prefs, STATUS_ERROR, e.getMessage());
                    postRestoreResult(callback, false, e.getMessage(), 0, 0);
                });
    }

    public static void verifyPendingSync(Context context) {
        SharedPreferences prefs = getPrefs(context);
        int status = prefs.getInt(KEY_CLOUD_BACKUP_STATUS, STATUS_IDLE);
        int expectedRecords = prefs.getInt(KEY_CLOUD_BACKUP_EXPECTED_RECORDS, -1);
        int expectedCategories = prefs.getInt(KEY_CLOUD_BACKUP_EXPECTED_CATEGORIES, -1);
        Log.d(TAG, "verifyPendingSync: status=" + status + " expectedRecords=" + expectedRecords + " expectedCategories=" + expectedCategories);
        if (status != STATUS_SYNCING || expectedRecords < 0 || expectedCategories < 0) {
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            updateStatus(prefs, STATUS_ERROR, "not signed in");
            clearVerifyState(prefs);
            return;
        }
        scheduleVerify(context.getApplicationContext(), user.getUid(), prefs, 0L);
    }

    private static void uploadBackup(Context context, String uid, SharedPreferences prefs) {
        new Thread(() -> {
            try {
                Log.d(TAG, "uploadBackup: Reading data from local database...");
                List<Map<String, Object>> records = readRecords(context);
                List<Map<String, Object>> categories = readCategories(context);
                int recordCount = records.size();
                int categoryCount = categories.size();
                
                Map<String, Object> payload = new HashMap<>();
                payload.put("records", records);
                payload.put("categories", categories);
                payload.put("updatedAt", FieldValue.serverTimestamp());

                Log.d(TAG, "uploadBackup: Starting Firestore sync. Records: " + records.size());
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(KEY_CLOUD_BACKUP_EXPECTED_RECORDS, recordCount);
                editor.putInt(KEY_CLOUD_BACKUP_EXPECTED_CATEGORIES, categoryCount);
                editor.putInt(KEY_CLOUD_BACKUP_VERIFY_ATTEMPTS, 0);
                editor.apply();
                VERIFY_IN_FLIGHT.set(false);
                Log.d(TAG, "uploadBackup: Expected counts records=" + recordCount + " categories=" + categoryCount);
                FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(uid)
                        .collection("backups")
                        .document("latest")
                        .set(payload)
                        .addOnSuccessListener(unused -> {
                            Log.d(TAG, "uploadBackup: Successfully synced to Firestore.");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "uploadBackup: Firestore task execution failed", e);
                            updateStatus(prefs, STATUS_ERROR, e.getMessage());
                            clearVerifyState(prefs);
                        })
                        .addOnCanceledListener(() -> {
                            Log.e(TAG, "uploadBackup: Firestore task was cancelled");
                            updateStatus(prefs, STATUS_ERROR, "Cancelled");
                            clearVerifyState(prefs);
                        });
                Log.d(TAG, "uploadBackup: Scheduling verify in " + VERIFY_INTERVAL_MS + "ms");
                scheduleVerify(context.getApplicationContext(), uid, prefs, VERIFY_INTERVAL_MS);

            } catch (Exception e) {
                Log.e(TAG, "uploadBackup: Unexpected error during backup process", e);
                updateStatus(prefs, STATUS_ERROR, e.getMessage());
                clearVerifyState(prefs);
            }
        }).start();
    }

    private static List<Map<String, Object>> readRecords(Context context) {
        List<Map<String, Object>> records = new ArrayList<>();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        Cursor cursor = dbHelper.getAllRecords();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    Map<String, Object> record = new HashMap<>();
                    record.put("id", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID)));
                    record.put("amount", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AMOUNT)));
                    record.put("category", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CATEGORY)));
                    record.put("note", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NOTE)));
                    record.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP)));
                    record.put("latitude", cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LATITUDE)));
                    record.put("longitude", cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LONGITUDE)));
                    records.add(record);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return records;
    }

    private static List<Map<String, Object>> readCategories(Context context) {
        List<Map<String, Object>> categories = new ArrayList<>();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        Cursor cursor = dbHelper.getAllCategories();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    Map<String, Object> category = new HashMap<>();
                    category.put("id", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CAT_ID)));
                    category.put("name", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CAT_NAME)));
                    category.put("sortOrder", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CAT_ORDER)));
                    categories.add(category);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return categories;
    }

    private static int restoreRecords(Context context, List<?> list, boolean overwrite) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        if (overwrite) {
            dbHelper.deleteAllRecords();
        }
        int restored = 0;
        for (Object item : list) {
            if (!(item instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> record = (Map<String, Object>) item;
            Integer amount = getInt(record.get("amount"));
            String category = getString(record.get("category"));
            String note = getString(record.get("note"));
            Long timestamp = getLong(record.get("timestamp"));
            Double latitude = getDouble(record.get("latitude"));
            Double longitude = getDouble(record.get("longitude"));

            if (amount == null || category == null || note == null) {
                continue;
            }
            long safeTimestamp = timestamp == null ? System.currentTimeMillis() : timestamp;
            double safeLat = latitude == null ? 0.0 : latitude;
            double safeLon = longitude == null ? 0.0 : longitude;
            dbHelper.insertRecord(amount, category, note, safeTimestamp, safeLat, safeLon);
            restored++;
        }
        return restored;
    }

    private static int restoreCategories(Context context, List<?> list, boolean overwrite) {
        if (list == null) {
            return 0;
        }
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        java.util.HashSet<String> existingNames = new java.util.HashSet<>();
        int restored = 0;
        int startOrder = 0;
        if (overwrite) {
            dbHelper.deleteAllCategories();
        } else {
            Cursor cursor = dbHelper.getAllCategories();
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int maxOrder = 0;
                    do {
                        int order = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CAT_ORDER));
                        if (order >= maxOrder) {
                            maxOrder = order + 1;
                        }
                    } while (cursor.moveToNext());
                    startOrder = maxOrder;
                }
                cursor.close();
            }
        }

        if (!overwrite) {
            Cursor cursor = dbHelper.getAllCategories();
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CAT_NAME));
                        if (name != null) {
                            existingNames.add(name);
                        }
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }

        for (Object item : list) {
            if (!(item instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> category = (Map<String, Object>) item;
            String id = getString(category.get("id"));
            String name = getString(category.get("name"));
            Integer order = getInt(category.get("sortOrder"));
            if (name == null || id == null) {
                continue;
            }
            if (!overwrite && existingNames.contains(name)) {
                continue;
            }
            int insertOrder = overwrite ? (order == null ? restored : order) : startOrder++;
            dbHelper.insertCategoryWithId(id, name, insertOrder);
            existingNames.add(name);
            restored++;
        }
        return restored;
    }

    private static void postRestoreResult(RestoreCallback callback, boolean success, String message, int recordCount, int categoryCount) {
        if (callback == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(success, message, recordCount, categoryCount));
    }

    private static Integer getInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private static Long getLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private static Double getDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private static String getString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private static void updateStatus(SharedPreferences prefs, int status, String error) {
        Log.d(TAG, "updateStatus: " + status + (error == null ? "" : " (Error: " + error + ")"));
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_CLOUD_BACKUP_STATUS, status);
        if (status == STATUS_SUCCESS) {
            editor.putLong(KEY_CLOUD_BACKUP_LAST_SYNC, System.currentTimeMillis());
        }
        if (error == null || error.isEmpty()) {
            editor.remove(KEY_CLOUD_BACKUP_LAST_ERROR);
        } else {
            editor.putString(KEY_CLOUD_BACKUP_LAST_ERROR, error);
        }
        editor.apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void scheduleVerify(Context context, String uid, SharedPreferences prefs, long delayMs) {
        if (!VERIFY_IN_FLIGHT.compareAndSet(false, true)) {
            Log.d(TAG, "scheduleVerify: already in flight, skip");
            return;
        }
        Log.d(TAG, "scheduleVerify: delayMs=" + delayMs);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> checkRemoteCounts(context.getApplicationContext(), uid, prefs), delayMs);
    }

    private static void checkRemoteCounts(Context context, String uid, SharedPreferences prefs) {
        int status = prefs.getInt(KEY_CLOUD_BACKUP_STATUS, STATUS_IDLE);
        if (status != STATUS_SYNCING) {
            Log.d(TAG, "checkRemoteCounts: status not syncing (" + status + "), stop");
            VERIFY_IN_FLIGHT.set(false);
            return;
        }
        int expectedRecords = prefs.getInt(KEY_CLOUD_BACKUP_EXPECTED_RECORDS, -1);
        int expectedCategories = prefs.getInt(KEY_CLOUD_BACKUP_EXPECTED_CATEGORIES, -1);
        if (expectedRecords < 0 || expectedCategories < 0) {
            Log.d(TAG, "checkRemoteCounts: missing expected counts, stop");
            VERIFY_IN_FLIGHT.set(false);
            return;
        }
        int attempt = prefs.getInt(KEY_CLOUD_BACKUP_VERIFY_ATTEMPTS, 0);
        Log.d(TAG, "checkRemoteCounts: attempt=" + attempt + " expectedRecords=" + expectedRecords + " expectedCategories=" + expectedCategories);
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("backups")
                .document("latest")
                .get()
                .addOnSuccessListener(snapshot -> {
                    int remoteRecords = 0;
                    int remoteCategories = 0;
                    Object raw = snapshot.get("records");
                    Object rawCategories = snapshot.get("categories");
                    if (raw instanceof List) {
                        remoteRecords = ((List<?>) raw).size();
                    }
                    if (rawCategories instanceof List) {
                        remoteCategories = ((List<?>) rawCategories).size();
                    }
                    Log.d(TAG, "checkRemoteCounts: remoteRecords=" + remoteRecords + " remoteCategories=" + remoteCategories
                            + " fromCache=" + snapshot.getMetadata().isFromCache());
                    if (remoteRecords == expectedRecords && remoteCategories == expectedCategories) {
                        updateStatus(prefs, STATUS_SUCCESS, null);
                        clearVerifyState(prefs);
                        VERIFY_IN_FLIGHT.set(false);
                        return;
                    }
                    retryOrFail(context, uid, prefs);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "checkRemoteCounts: server fetch failed", e);
                    retryOrFail(context, uid, prefs);
                });
    }

    private static void retryOrFail(Context context, String uid, SharedPreferences prefs) {
        int attempts = prefs.getInt(KEY_CLOUD_BACKUP_VERIFY_ATTEMPTS, 0) + 1;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_CLOUD_BACKUP_VERIFY_ATTEMPTS, attempts);
        editor.apply();
        Log.d(TAG, "retryOrFail: attempt=" + attempts + "/" + VERIFY_MAX_ATTEMPTS);
        if (attempts >= VERIFY_MAX_ATTEMPTS) {
            updateStatus(prefs, STATUS_ERROR, "Sync timeout");
            clearVerifyState(prefs);
            VERIFY_IN_FLIGHT.set(false);
            return;
        }
        VERIFY_IN_FLIGHT.set(false);
        scheduleVerify(context.getApplicationContext(), uid, prefs, VERIFY_INTERVAL_MS);
    }

    private static void clearVerifyState(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_CLOUD_BACKUP_EXPECTED_RECORDS);
        editor.remove(KEY_CLOUD_BACKUP_EXPECTED_CATEGORIES);
        editor.remove(KEY_CLOUD_BACKUP_VERIFY_ATTEMPTS);
        editor.apply();
    }
}
