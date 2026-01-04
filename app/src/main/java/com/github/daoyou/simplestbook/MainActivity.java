package com.github.daoyou.simplestbook;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.GridView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private MaterialToolbar topAppBar;
    private TextInputEditText amountInput;
    private TextInputEditText noteInput;
    private GridView categoryGrid;
    private MaterialButton saveButton;
    private com.google.android.material.floatingactionbutton.FloatingActionButton recurringFab;
    private DatabaseHelper dbHelper;
    private CategoryAdapter categoryAdapter;
    private List<Category> categories;
    private FusedLocationProviderClient fusedLocationClient;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener backupIndicatorListener;
    private MenuItem cloudStatusItem;
    private static final int REQ_NOTIFICATIONS = 2001;

    private double preFetchedLat = 0.0;
    private double preFetchedLon = 0.0;
    private long lastExitStatusTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 綁定元素
        topAppBar = findViewById(R.id.topAppBar);
        amountInput = findViewById(R.id.amountInput);
        noteInput = findViewById(R.id.noteInput);
        categoryGrid = findViewById(R.id.categoryGrid);
        saveButton = findViewById(R.id.saveButton);
        recurringFab = findViewById(R.id.recurringFab);
        cloudStatusItem = topAppBar.getMenu().findItem(R.id.action_cloud_status);
        backupIndicatorListener = CloudBackupIndicator.register(this, cloudStatusItem);

        loadCategories();

        categoryGrid.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = categories.get(position).getName();
            categoryAdapter.setSelectedCategory(selectedName);
        });

        saveButton.setOnClickListener(v -> handlePrimaryAction());
        recurringFab.setOnClickListener(v -> {
            Intent intent = new Intent(this, RecurringPaymentActivity.class);
            startActivity(intent);
        });

        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_manage_categories) {
                Intent intent = new Intent(this, ManageCategoriesActivity.class);
                startActivity(intent);
                return true;
            } else if (item.getItemId() == R.id.action_settings) {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            } else if (item.getItemId() == R.id.action_cloud_status) {
                CloudBackupIndicator.showStatusSnackbar(this);
                return true;
            }
            return false;
        });

        updatePrimaryActionLabel();

        noteInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveRecord();
                return true;
            }
            return false;
        });

        amountInput.addTextChangedListener(new SimpleTextWatcher(this::updatePrimaryActionLabel));
        noteInput.addTextChangedListener(new SimpleTextWatcher(this::updatePrimaryActionLabel));

        checkPermission();
        RecurringPaymentWorker.schedule(getApplicationContext());
        RecurringPaymentAlarmReceiver.scheduleAllMinute(getApplicationContext());
        checkRecurringNotificationPermission();
    }

    private void checkPermission() {
        if (prefs.getBoolean(SettingsActivity.KEY_LOCATION_ENABLED, true)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void loadCategories() {
        categories = new ArrayList<>();
        Cursor cursor = dbHelper.getAllCategories();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String id = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CAT_ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CAT_NAME));
                    categories.add(new Category(id, name));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        categories.add(new Category("fixed_other", "其他"));
        categoryAdapter = new CategoryAdapter(this, categories);

        // 套用預設類別設定
        int defaultType = prefs.getInt(SettingsActivity.KEY_DEFAULT_CATEGORY, 0);
        if (defaultType == 0 && !categories.isEmpty()) {
            categoryAdapter.setSelectedCategory(categories.get(0).getName());
        } else if (defaultType == 1) {
            categoryAdapter.setSelectedCategory("其他");
        } else {
            categoryAdapter.setSelectedCategory(""); // 不預設
        }

        categoryGrid.setAdapter(categoryAdapter);
    }

    private void prefetchLocation() {
        if (!prefs.getBoolean(SettingsActivity.KEY_LOCATION_ENABLED, true)) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            preFetchedLat = location.getLatitude();
                            preFetchedLon = location.getLongitude();
                        }
                    });
        }
    }

    private void saveRecord() {
        String amountStr = amountInput.getText().toString();
        String note = noteInput.getText().toString().trim();

        if (amountStr.isEmpty()) {
            Toast.makeText(this, "請輸入金額", Toast.LENGTH_SHORT).show();
            return;
        }
        if (note.isEmpty()) {
            Toast.makeText(this, "請輸入說明", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int amount = Integer.parseInt(amountStr);
            String category = categoryAdapter.getSelectedCategoryName();
            if (category == null || category.isEmpty()) {
                Toast.makeText(this, "請選擇類別", Toast.LENGTH_SHORT).show();
                return;
            }

            if (prefs.getBoolean(SettingsActivity.KEY_LOCATION_ENABLED, true)) {
                if (preFetchedLat != 0.0) {
                    performDatabaseSave(amount, category, note, preFetchedLat, preFetchedLon);
                } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                        double lat = 0.0, lon = 0.0;
                        if (location != null) {
                            lat = location.getLatitude();
                            lon = location.getLongitude();
                        }
                        performDatabaseSave(amount, category, note, lat, lon);
                    }).addOnFailureListener(e -> performDatabaseSave(amount, category, note, 0.0, 0.0));
                } else {
                    Toast.makeText(this, "位置取得失敗", Toast.LENGTH_SHORT).show();
                    performDatabaseSave(amount, category, note, 0.0, 0.0);
                }
            } else {
                // 定位關閉時直接存入 0,0
                performDatabaseSave(amount, category, note, 0.0, 0.0);
            }

        } catch (NumberFormatException e) {
            Toast.makeText(this, "金額格式錯誤", Toast.LENGTH_SHORT).show();
        }
    }

    private void performDatabaseSave(int amount, String category, String note, double lat, double lon) {
        new Thread(() -> {
            dbHelper.insertRecord(amount, category, note, lat, lon);
            CloudBackupManager.requestSyncIfEnabled(getApplicationContext());
            runOnUiThread(this::completeSave);
        }).start();
    }

    private void completeSave() {
        amountInput.setText("");
        noteInput.setText("");
        preFetchedLat = 0.0;
        preFetchedLon = 0.0;
        Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();

        if (prefs.getBoolean(SettingsActivity.KEY_AUTO_HISTORY, true)) {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
        } else {
            prefetchLocation(); // 留在本頁則重新預抽樣下一筆
        }
        updatePrimaryActionLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCategories();
        amountInput.requestFocus();
        prefetchLocation();
        CloudBackupManager.verifyPendingSync(getApplicationContext());
        updatePrimaryActionLabel();
        lastExitStatusTime = 0L;
    }

    private void checkRecurringNotificationPermission() {
        if (!prefs.getBoolean(SettingsActivity.KEY_RECURRING_NOTIFY_ENABLED, true)) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(
                        this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            }
        }
    }

    private void handlePrimaryAction() {
        if (hasInput()) {
            saveRecord();
        } else {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
        }
    }

    private void updatePrimaryActionLabel() {
        if (hasInput()) {
            saveButton.setText("儲存");
        } else {
            saveButton.setText("查看歷史紀錄");
        }
    }

    private boolean hasInput() {
        String amount = amountInput.getText() == null ? "" : amountInput.getText().toString().trim();
        String note = noteInput.getText() == null ? "" : noteInput.getText().toString().trim();
        return !amount.isEmpty() || !note.isEmpty();
    }

    private static class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable onChanged;

        SimpleTextWatcher(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            onChanged.run();
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {}
    }

    @Override
    protected void onDestroy() {
        CloudBackupIndicator.unregister(this, backupIndicatorListener);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        triggerExitStatusChip();
        finish();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        triggerExitStatusChip();
    }

    private void triggerExitStatusChip() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastExitStatusTime < 1000L) {
            return;
        }
        lastExitStatusTime = now;
        StatusChipService.show(this);
    }
}
