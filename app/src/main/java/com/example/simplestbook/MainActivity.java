package com.example.simplestbook;

import android.content.Intent;
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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private MaterialToolbar topAppBar;
    private TextInputEditText amountInput;
    private TextInputEditText noteInput;
    private GridView categoryGrid;
    private MaterialButton saveButton;
    private MaterialButton viewHistoryButton;
    private DatabaseHelper dbHelper;
    private CategoryAdapter categoryAdapter;
    private List<Category> categories;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        dbHelper = new DatabaseHelper(this);

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
        viewHistoryButton = findViewById(R.id.viewHistoryButton);

        // 載入類別
        loadCategories();

        // 類別點擊監聽
        categoryGrid.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = categories.get(position).getName();
            categoryAdapter.setSelectedCategory(selectedName);
        });

        // 跳轉到歷史紀錄
        viewHistoryButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
        });

        // 設定選單點擊監聽
        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_manage_categories) {
                Intent intent = new Intent(this, ManageCategoriesActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });

        // 儲存按鈕處理資料
        saveButton.setOnClickListener(v -> saveRecord());

        // 鍵盤「完成/送出」按鈕處理資料
        noteInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveRecord();
                return true;
            }
            return false;
        });
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
        
        // 「其他」為固定類別，直接在末尾生成，不用寫入資料庫
        categories.add(new Category("fixed_other", "其他"));
        
        categoryAdapter = new CategoryAdapter(this, categories);
        // 預設選中第一個
        if (!categories.isEmpty()) {
            categoryAdapter.setSelectedCategory(categories.get(0).getName());
        }
        categoryGrid.setAdapter(categoryAdapter);
    }

    private void saveRecord() {
        String amountStr = amountInput.getText().toString();
        if (amountStr.isEmpty()) {
            Toast.makeText(this, "請輸入金額", Toast.LENGTH_SHORT).show();
            return;
        }

        String note = noteInput.getText().toString().trim();
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

            // 寫入資料庫
            dbHelper.insertRecord(amount, category, note);

            // 清除輸入
            amountInput.setText("");
            noteInput.setText("");
            Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();

            // 儲存後跳轉至歷史紀錄
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);

        } catch (NumberFormatException e) {
            Toast.makeText(this, "請輸入有效的整數金額", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCategories();
        
        // 進入時自動焦點為金額
        amountInput.requestFocus();
    }
}