package com.github.daoyou.simplestbook;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class EditRecordActivity extends AppCompatActivity implements OnMapReadyCallback {

    private MaterialToolbar editToolbar;
    private TextInputEditText editAmountInput;
    private TextInputEditText editNoteInput;
    private TextInputEditText editTimeInput;
    private GridView editCategoryGrid;
    private MaterialButton updateButton;
    private View mapContainer;
    private TextView textAddress;

    private DatabaseHelper dbHelper;
    private String recordId;
    private String locationName;
    private CategoryAdapter categoryAdapter;
    private List<Category> categories;
    private Calendar calendar;
    private double latitude = 0.0;
    private double longitude = 0.0;
    private int originalAmount;
    private String originalCategory;
    private String originalNote;
    private long originalTimestamp;
    private SharedPreferences.OnSharedPreferenceChangeListener backupIndicatorListener;
    private MenuItem cloudStatusItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_record);

        dbHelper = new DatabaseHelper(this);
        calendar = Calendar.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 綁定元素
        editToolbar = findViewById(R.id.editToolbar);
        editAmountInput = findViewById(R.id.editAmountInput);
        editNoteInput = findViewById(R.id.editNoteInput);
        editTimeInput = findViewById(R.id.editTimeInput);
        editCategoryGrid = findViewById(R.id.editCategoryGrid);
        updateButton = findViewById(R.id.updateButton);
        mapContainer = findViewById(R.id.mapContainer);
        textAddress = findViewById(R.id.textAddress);

        setSupportActionBar(editToolbar);

        loadCategories();

        if (getIntent() != null) {
            recordId = getIntent().getStringExtra("id");
            int amount = getIntent().getIntExtra("amount", 0);
            String category = getIntent().getStringExtra("category");
            String note = getIntent().getStringExtra("note");
            locationName = getIntent().getStringExtra("locationName");
            long timestamp = getIntent().getLongExtra("timestamp", System.currentTimeMillis());
            latitude = getIntent().getDoubleExtra("latitude", 0.0);
            longitude = getIntent().getDoubleExtra("longitude", 0.0);

            originalAmount = amount;
            originalCategory = category == null ? "" : category;
            originalNote = note == null ? "" : note;
            originalTimestamp = timestamp;

            editAmountInput.setText(String.valueOf(amount));
            editNoteInput.setText(note);
            calendar.setTimeInMillis(timestamp);
            updateTimeDisplay();
            
            if (category != null) {
                categoryAdapter.setSelectedCategory(category);
            }

            if (latitude != 0.0 || longitude != 0.0) {
                mapContainer.setVisibility(View.VISIBLE);
                
                // 先顯示地點備註，沒有則顯示區域名稱
                updateAddressLabel();

                SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_container_view);
                if (mapFragment != null) {
                    mapFragment.getMapAsync(this);
                }

                // 設定點擊卡片跳轉
                mapContainer.setOnClickListener(v -> {
                    Intent intent = new Intent(this, FullMapActivity.class);
                    intent.putExtra("latitude", latitude);
                    intent.putExtra("longitude", longitude);
                    intent.putExtra("title", "$" + editAmountInput.getText() + " - " + editNoteInput.getText());
                    intent.putExtra("recordId", recordId);
                    intent.putExtra("locationName", locationName);
                    startActivity(intent);
                });
            } else {
                mapContainer.setVisibility(View.GONE);
            }
        }

        editTimeInput.setOnClickListener(v -> showDateTimePicker());
        editCategoryGrid.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = categories.get(position).getName();
            categoryAdapter.setSelectedCategory(selectedName);
            updatePrimaryActionLabel();
        });

        editToolbar.setNavigationOnClickListener(v -> finish());
        updateButton.setOnClickListener(v -> handlePrimaryAction());
        editNoteInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handlePrimaryAction();
                return true;
            }
            return false;
        });

        editAmountInput.addTextChangedListener(new SimpleTextWatcher(this::updatePrimaryActionLabel));
        editNoteInput.addTextChangedListener(new SimpleTextWatcher(this::updatePrimaryActionLabel));
    }

    private void updateAddressLabel() {
        if (locationName != null && !locationName.trim().isEmpty()) {
            textAddress.setText(locationName.trim());
            return;
        }
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                
                // 收集所有可能的地址片段 (縣市、大區域、小區域、里/村)
                List<String> rawParts = new ArrayList<>();
                if (addr.getAdminArea() != null) rawParts.add(addr.getAdminArea());
                if (addr.getSubAdminArea() != null) rawParts.add(addr.getSubAdminArea());
                if (addr.getLocality() != null) rawParts.add(addr.getLocality());
                if (addr.getSubLocality() != null) rawParts.add(addr.getSubLocality());
                
                StringBuilder sb = new StringBuilder();
                for (String part : rawParts) {
                    if (part == null || part.isEmpty()) continue;
                    
                    if (sb.length() == 0) {
                        sb.append(part);
                    } else {
                        String current = sb.toString();
                        // 處理包含關係：
                        // 1. 如果新片段包含了目前的字串 (例如 "台北市" 遇到 "台北市大安區")
                        if (part.startsWith(current)) {
                            sb.setLength(0);
                            sb.append(part);
                        } 
                        // 2. 如果目前的字串還沒包含新片段 (例如 "台北市大安區" 遇到 "光明里")
                        else if (!current.contains(part)) {
                            sb.append(part);
                        }
                    }
                }
                
                String display = sb.toString();
                textAddress.setText(display.isEmpty() ? "未知位置" : display);
            } else {
                textAddress.setText("無法取得位置名稱");
            }
        } catch (IOException e) {
            textAddress.setText("讀取位置錯誤");
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        LatLng location = new LatLng(latitude, longitude);
        googleMap.addMarker(new MarkerOptions().position(location).title("記帳位置"));
        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition(location, 15, 0, 0)));
        googleMap.getUiSettings().setAllGesturesEnabled(false);
    }

    private void showDateTimePicker() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            new TimePickerDialog(this, (view1, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                updateTimeDisplay();
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateTimeDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());
        editTimeInput.setText(sdf.format(calendar.getTime()));
        updatePrimaryActionLabel();
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
        editCategoryGrid.setAdapter(categoryAdapter);
    }

    private void updateRecord() {
        String amountStr = editAmountInput.getText().toString();
        String note = editNoteInput.getText().toString().trim();
        
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

            String latestLocationName = dbHelper.getLocationNameById(recordId);
            String safeLocationName = latestLocationName == null ? "" : latestLocationName;
            dbHelper.updateRecord(recordId, amount, category, note, safeLocationName,
                    calendar.getTimeInMillis(), latitude, longitude);
            CloudBackupManager.requestSyncIfEnabled(getApplicationContext());
            Toast.makeText(this, "更新成功", Toast.LENGTH_SHORT).show();
            finish();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "金額格式錯誤", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        CloudBackupIndicator.unregister(this, backupIndicatorListener);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (recordId == null) {
            return;
        }
        String latest = dbHelper.getLocationNameById(recordId);
        if (latest != null) {
            locationName = latest;
        }
        if (latitude != 0.0 || longitude != 0.0) {
            updateAddressLabel();
        }
        updatePrimaryActionLabel();
    }

    private void handlePrimaryAction() {
        if (isEdited()) {
            updateRecord();
        } else {
            confirmDelete();
        }
    }

    private void updatePrimaryActionLabel() {
        if (isEdited()) {
            updateButton.setText("更新");
            int primary = androidx.core.content.ContextCompat.getColor(this, R.color.purple_200);
            int white = androidx.core.content.ContextCompat.getColor(this, R.color.white);
            updateButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary));
            updateButton.setTextColor(android.content.res.ColorStateList.valueOf(white));
        } else {
            updateButton.setText("刪除紀錄");
            int errorColor = androidx.core.content.ContextCompat.getColor(this, R.color.colorError);
            int onError = androidx.core.content.ContextCompat.getColor(this, R.color.white);
            updateButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(errorColor));
            updateButton.setTextColor(onError);
        }
    }

    private boolean isEdited() {
        String amountStr = editAmountInput.getText() == null ? "" : editAmountInput.getText().toString().trim();
        int amount = 0;
        try {
            amount = amountStr.isEmpty() ? 0 : Integer.parseInt(amountStr);
        } catch (NumberFormatException ignored) {}
        String note = editNoteInput.getText() == null ? "" : editNoteInput.getText().toString();
        String category = categoryAdapter.getSelectedCategoryName();
        String safeCategory = category == null ? "" : category;
        return amount != originalAmount
                || !safeCategory.equals(originalCategory)
                || !note.equals(originalNote)
                || calendar.getTimeInMillis() != originalTimestamp;
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("確認刪除")
                .setMessage("確定要刪除這筆紀錄嗎？此動作無法復原。")
                .setPositiveButton("刪除", (dialog, which) -> {
                    if (recordId != null) {
                        dbHelper.deleteRecord(recordId);
                        CloudBackupManager.requestSyncIfEnabled(getApplicationContext());
                        Toast.makeText(this, "已刪除", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar_cloud, menu);
        cloudStatusItem = menu.findItem(R.id.action_cloud_status);
        CloudBackupIndicator.unregister(this, backupIndicatorListener);
        backupIndicatorListener = CloudBackupIndicator.register(this, cloudStatusItem);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_cloud_status) {
            CloudBackupIndicator.showStatusSnackbar(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
