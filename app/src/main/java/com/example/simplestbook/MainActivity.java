package com.example.simplestbook;

import android.content.Intent;
import android.os.Bundle;
import android.widget.GridView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity {

    private MaterialToolbar topAppBar;
    private TextInputEditText amountInput;
    private TextInputEditText noteInput;
    private GridView categoryGrid;
    private MaterialButton saveButton;
    private MaterialButton viewHistoryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
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

        // 儲存按鈕暫時不處理資料
        saveButton.setOnClickListener(v -> {
            // TODO: 實作儲存邏輯
        });
    }
}