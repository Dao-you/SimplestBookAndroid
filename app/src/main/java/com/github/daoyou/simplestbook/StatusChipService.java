package com.github.daoyou.simplestbook;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

public class StatusChipService extends Service {

    private static final String ACTION_SHOW_STATUS_CHIP =
            "com.github.daoyou.simplestbook.action.SHOW_STATUS_CHIP";
    private static final long AUTO_DISMISS_MS = 5000L;
    private static final long FADE_OUT_DELAY_MS = 4500L;
    private static final long FADE_OUT_DURATION_MS = 300L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlayView;

    public static void show(Context context) {
        Intent intent = new Intent(context, StatusChipService.class);
        intent.setAction(ACTION_SHOW_STATUS_CHIP);
        context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_SHOW_STATUS_CHIP.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (!canDrawOverlays()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        new Thread(() -> {
            DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
            int total = dbHelper.getTotalAmount();
            handler.post(() -> {
                showOverlay(total);
                handler.postDelayed(() -> startFadeOut(startId), FADE_OUT_DELAY_MS);
                handler.postDelayed(() -> {
                    removeOverlay();
                    stopSelf(startId);
                }, AUTO_DISMISS_MS);
            });
        }).start();

        return START_NOT_STICKY;
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return Settings.canDrawOverlays(this);
    }

    private void showOverlay(int total) {
        removeOverlay();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        overlayView = inflater.inflate(R.layout.activity_status_chip, null);
        android.widget.TextView chip = overlayView.findViewById(R.id.statusChip);
        chip.setText(getString(R.string.status_chip_total, total));

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = getResources().getDimensionPixelOffset(R.dimen.status_chip_offset_y);
        overlayView.setAlpha(0f);
        windowManager.addView(overlayView, params);
        overlayView.post(() -> overlayView.animate().alpha(1f).setDuration(200L).start());
    }

    private void removeOverlay() {
        if (windowManager != null && overlayView != null) {
            windowManager.removeView(overlayView);
        }
        overlayView = null;
    }

    private void startFadeOut(int startId) {
        if (overlayView == null) {
            return;
        }
        overlayView.animate().alpha(0f).setDuration(FADE_OUT_DURATION_MS).start();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
