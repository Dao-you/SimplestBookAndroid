package com.github.daoyou.simplestbook;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

public class CloudBackupIndicator {

    private CloudBackupIndicator() {}

    public static SharedPreferences.OnSharedPreferenceChangeListener register(Activity activity, @Nullable MenuItem menuItem) {
        if (menuItem == null) {
            return null;
        }

        SharedPreferences prefs = activity.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.OnSharedPreferenceChangeListener listener = (sharedPreferences, key) -> {
            if (key == null) {
                return;
            }
            if (key.equals(SettingsActivity.KEY_CLOUD_BACKUP_ENABLED) || key.startsWith("cloud_backup_")) {
                updateMenuItem(activity, sharedPreferences, menuItem);
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);
        updateMenuItem(activity, prefs, menuItem);
        return listener;
    }

    public static void unregister(Activity activity, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        if (listener == null) {
            return;
        }
        SharedPreferences prefs = activity.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    public static void showStatusSnackbar(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String message = getStatusMessage(prefs);
        View anchor = activity.findViewById(R.id.main);
        if (anchor == null) {
            anchor = activity.getWindow().getDecorView();
        }
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
                .setAction("設定", v -> activity.startActivity(new Intent(activity, SettingsActivity.class)))
                .show();
    }

    private static void updateMenuItem(Context context, SharedPreferences prefs, MenuItem menuItem) {
        boolean enabled = prefs.getBoolean(SettingsActivity.KEY_CLOUD_BACKUP_ENABLED, false);
        if (!enabled) {
            menuItem.setVisible(false);
            return;
        }
        menuItem.setVisible(true);

        int status = prefs.getInt(CloudBackupManager.KEY_CLOUD_BACKUP_STATUS, CloudBackupManager.STATUS_IDLE);
        int iconRes = R.drawable.ic_cloud;
        int colorAttr = com.google.android.material.R.attr.colorOnSurfaceVariant;
        String desc = "雲端備份：待同步";

        if (status == CloudBackupManager.STATUS_SYNCING) {
            iconRes = R.drawable.ic_arrow_upload_progress;
            colorAttr = com.google.android.material.R.attr.colorSecondary;
            desc = "雲端備份：同步中";
        } else if (status == CloudBackupManager.STATUS_SUCCESS) {
            iconRes = R.drawable.ic_cloud_done;
            colorAttr = R.attr.colorPrimary;
            desc = "雲端備份：已同步";
        } else if (status == CloudBackupManager.STATUS_ERROR) {
            iconRes = R.drawable.ic_alert;
            colorAttr = R.attr.colorPrimary;
            desc = "雲端備份：同步失敗";
        }

        menuItem.setIcon(iconRes);
        menuItem.setContentDescription(desc);
        int color = MaterialColors.getColor(context, colorAttr, 0);
        if (menuItem.getIcon() != null) {
            menuItem.getIcon().setTint(color);
        }
    }

    private static String getStatusMessage(SharedPreferences prefs) {
        boolean enabled = prefs.getBoolean(SettingsActivity.KEY_CLOUD_BACKUP_ENABLED, false);
        if (!enabled) {
            return "雲端備份未啟用";
        }
        int status = prefs.getInt(CloudBackupManager.KEY_CLOUD_BACKUP_STATUS, CloudBackupManager.STATUS_IDLE);
        if (status == CloudBackupManager.STATUS_SYNCING) {
            return "雲端備份：同步中...";
        } else if (status == CloudBackupManager.STATUS_SUCCESS) {
            long last = prefs.getLong(CloudBackupManager.KEY_CLOUD_BACKUP_LAST_SYNC, 0L);
            if (last > 0L) {
                java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                return "雲端備份：已同步 " + format.format(new java.util.Date(last));
            }
            return "雲端備份：已同步";
        } else if (status == CloudBackupManager.STATUS_ERROR) {
            return "雲端備份：同步失敗";
        }
        return "雲端備份：待同步";
    }
}
