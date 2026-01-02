package com.example.simplestbook;

import android.content.Intent;
import android.database.Cursor;
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

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private MaterialToolbar historyToolbar;
    private TextView totalAmountText;
    private ListView historyListView;
    private FloatingActionButton fabAdd;
    private DatabaseHelper dbHelper;
    private List<Record> recordList;
    private RecordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);

        dbHelper = new DatabaseHelper(this);

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

        // 讀取資料庫
        loadRecords();

        // 點擊清單項目跳轉至編輯頁面
        historyListView.setOnItemClickListener((parent, view, position, id) -> {
            if (recordList != null && position < recordList.size()) {
                Record selectedRecord = recordList.get(position);
                Intent intent = new Intent(this, EditRecordActivity.class);
                intent.putExtra("id", selectedRecord.getId());
                intent.putExtra("amount", selectedRecord.getAmount());
                intent.putExtra("category", selectedRecord.getCategory());
                intent.putExtra("note", selectedRecord.getNote());
                intent.putExtra("timestamp", selectedRecord.getTimestamp());
                intent.putExtra("latitude", selectedRecord.getLatitude());
                intent.putExtra("longitude", selectedRecord.getLongitude());
                startActivity(intent);
            }
        });
    }

    private void loadRecords() {
        // 使用 Thread 執行資料庫讀取，避免阻塞 UI 執行緒
        new Thread(() -> {
            List<Record> tempRecords = new ArrayList<>();
            Cursor cursor = dbHelper.getAllRecords();
            int total = 0;

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        String id = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID));
                        int amount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AMOUNT));
                        String category = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CATEGORY));
                        String note = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NOTE));
                        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP));
                        double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LATITUDE));
                        double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LONGITUDE));

                        tempRecords.add(new Record(id, amount, category, note, timestamp, latitude, longitude));
                        total += amount;
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }

            final int finalTotal = total;
            // 切換回主執行緒更新 UI
            runOnUiThread(() -> {
                recordList = tempRecords;
                totalAmountText.setText("$ " + finalTotal);
                adapter = new RecordAdapter(HistoryActivity.this, recordList);
                historyListView.setAdapter(adapter);
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecords(); // 重新讀取資料
    }
}