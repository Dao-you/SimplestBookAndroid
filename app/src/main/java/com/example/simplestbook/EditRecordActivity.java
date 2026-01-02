package com.example.simplestbook;

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

public class EditRecordActivity extends AppCompatActivity {

    private MaterialToolbar editToolbar;
    private TextInputEditText editAmountInput;
    private TextInputEditText editNoteInput;
    private GridView editCategoryGrid;
    private MaterialButton updateButton;
    private MaterialButton deleteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_record);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 綁定元素
        editToolbar = findViewById(R.id.editToolbar);
        editAmountInput = findViewById(R.id.editAmountInput);
        editNoteInput = findViewById(R.id.editNoteInput);
        editCategoryGrid = findViewById(R.id.editCategoryGrid);
        updateButton = findViewById(R.id.updateButton);
        deleteButton = findViewById(R.id.deleteButton);

        // 工具列返回按鈕
        editToolbar.setNavigationOnClickListener(v -> finish());

        // 按鈕邏輯
        updateButton.setOnClickListener(v -> {
            // TODO: 實作更新邏輯
            finish();
        });

        deleteButton.setOnClickListener(v -> {
            // TODO: 實作刪除邏輯
            finish();
        });
    }
}