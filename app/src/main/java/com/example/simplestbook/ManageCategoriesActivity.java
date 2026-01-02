package com.example.simplestbook;

import android.os.Bundle;
import android.widget.ListView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ManageCategoriesActivity extends AppCompatActivity {

    private MaterialToolbar categoryToolbar;
    private TextInputLayout newCategoryLayout;
    private TextInputEditText newCategoryInput;
    private ListView categoryListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_manage_categories);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 綁定元素
        categoryToolbar = findViewById(R.id.categoryToolbar);
        newCategoryLayout = findViewById(R.id.newCategoryLayout);
        newCategoryInput = findViewById(R.id.newCategoryInput);
        categoryListView = findViewById(R.id.categoryListView);

        // 工具列返回按鈕
        categoryToolbar.setNavigationOnClickListener(v -> finish());

        // 設定新增類別的點擊監聽 (End Icon)
        newCategoryLayout.setEndIconOnClickListener(v -> {
            // TODO: 實作新增類別邏輯
            newCategoryInput.setText("");
        });
    }
}