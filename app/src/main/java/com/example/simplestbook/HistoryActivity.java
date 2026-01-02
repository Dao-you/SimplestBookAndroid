package com.example.simplestbook;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class HistoryActivity extends AppCompatActivity {

    private MaterialToolbar historyToolbar;
    private TextView totalAmountText;
    private ListView historyListView;
    private FloatingActionButton fabAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 綁定元素
        historyToolbar = findViewById(R.id.historyToolbar);
        totalAmountText = findViewById(R.id.totalAmountText);
        historyListView = findViewById(R.id.historyListView);
        fabAdd = findViewById(R.id.fabAdd);

        // 工具列返回按鈕
        historyToolbar.setNavigationOnClickListener(v -> finish());

        // 點擊 FAB 返回主畫面
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        // 點擊清單項目跳轉至編輯頁面
        historyListView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(this, EditRecordActivity.class);
            startActivity(intent);
        });
    }
}