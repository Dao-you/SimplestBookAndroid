package com.github.daoyou.simplestbook;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.MenuItem;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

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

    private static void updateMenuItem(Context context, SharedPreferences prefs, MenuItem menuItem) {
        boolean enabled = prefs.getBoolean(SettingsActivity.KEY_CLOUD_BACKUP_ENABLED, false);
        if (!enabled) {
            menuItem.setVisible(false);
            return;
        }
        menuItem.setVisible(true);

        int status = prefs.getInt(CloudBackupManager.KEY_CLOUD_BACKUP_STATUS, CloudBackupManager.STATUS_IDLE);
        int iconRes = R.drawable.ic_cloud_idle;
        int colorAttr = com.google.android.material.R.attr.colorOnSurfaceVariant;
        String desc = "雲端備份：待同步";

        if (status == CloudBackupManager.STATUS_SYNCING) {
            iconRes = R.drawable.ic_cloud_sync;
            colorAttr = com.google.android.material.R.attr.colorSecondary;
            desc = "雲端備份：同步中";
        } else if (status == CloudBackupManager.STATUS_SUCCESS) {
            iconRes = R.drawable.ic_cloud_done;
            colorAttr = R.attr.colorPrimary;
            desc = "雲端備份：已同步";
        } else if (status == CloudBackupManager.STATUS_ERROR) {
            iconRes = R.drawable.ic_cloud_error;
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
}
