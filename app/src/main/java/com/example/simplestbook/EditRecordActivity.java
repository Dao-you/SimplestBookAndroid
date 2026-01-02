package com.example.simplestbook;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.GridView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EditRecordActivity extends AppCompatActivity {

    private MaterialToolbar editToolbar;
    private TextInputEditText editAmountInput;
    private TextInputEditText editNoteInput;
    private TextInputEditText editTimeInput;
    private GridView editCategoryGrid;
    private MaterialButton updateButton;
    private MaterialButton deleteButton;
    
    private DatabaseHelper dbHelper;
    private String recordId;
    private CategoryAdapter categoryAdapter;
    private List<Category> categories;
    private Calendar calendar;

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
        deleteButton = findViewById(R.id.deleteButton);

        // 載入類別並設定 Adapter
        loadCategories();

        // 1. 從 Intent 取得資料並填入
        if (getIntent() != null) {
            recordId = getIntent().getStringExtra("id");
            int amount = getIntent().getIntExtra("amount", 0);
            String category = getIntent().getStringExtra("category");
            String note = getIntent().getStringExtra("note");
            long timestamp = getIntent().getLongExtra("timestamp", System.currentTimeMillis());

            editAmountInput.setText(String.valueOf(amount));
            editNoteInput.setText(note);
            calendar.setTimeInMillis(timestamp);
            updateTimeDisplay();
            
            // 設定當前記錄的類別為選中狀態
            if (category != null) {
                categoryAdapter.setSelectedCategory(category);
            }
        }

        // 時間輸入框點擊彈出選擇器
        editTimeInput.setOnClickListener(v -> showDateTimePicker());

        // 類別點擊監聽
        editCategoryGrid.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = categories.get(position).getName();
            categoryAdapter.setSelectedCategory(selectedName);
        });

        // 工具列返回按鈕
        editToolbar.setNavigationOnClickListener(v -> finish());

        // 更新按鈕邏輯
        updateButton.setOnClickListener(v -> updateRecord());

        // 鍵盤「完成/送出」按鈕邏輯
        editNoteInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                updateRecord();
                return true;
            }
            return false;
        });

        // 刪除邏輯
        deleteButton.setOnClickListener(v -> {
            if (recordId != null) {
                dbHelper.deleteRecord(recordId);
                Toast.makeText(this, "已刪除", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
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
        if (amountStr.isEmpty()) {
            Toast.makeText(this, "請輸入金額", Toast.LENGTH_SHORT).show();
            return;
        }

        String note = editNoteInput.getText().toString().trim();
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

            dbHelper.updateRecord(recordId, amount, category, note, calendar.getTimeInMillis());
            Toast.makeText(this, "更新成功", Toast.LENGTH_SHORT).show();
            finish();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "請輸入有效的整數金額", Toast.LENGTH_SHORT).show();
        }
    }
}