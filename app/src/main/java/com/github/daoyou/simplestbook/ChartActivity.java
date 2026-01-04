package com.github.daoyou.simplestbook;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChartActivity extends AppCompatActivity {

    private MaterialToolbar chartToolbar;
    private TextView totalAmountText;
    private TextView totalCountText;
    private TextView averageAmountText;
    private TextView emptyStateText;
    private LinearLayout chartContainer;
    private DatabaseHelper dbHelper;
    private SharedPreferences.OnSharedPreferenceChangeListener backupIndicatorListener;
    private MenuItem cloudStatusItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chart);

        dbHelper = new DatabaseHelper(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        chartToolbar = findViewById(R.id.chartToolbar);
        totalAmountText = findViewById(R.id.totalAmountText);
        totalCountText = findViewById(R.id.totalCountText);
        averageAmountText = findViewById(R.id.averageAmountText);
        emptyStateText = findViewById(R.id.emptyStateText);
        chartContainer = findViewById(R.id.chartContainer);

        setSupportActionBar(chartToolbar);
        chartToolbar.setNavigationOnClickListener(v -> finish());

        loadChart();
    }

    private void loadChart() {
        new Thread(() -> {
            Map<String, Integer> categoryTotals = new HashMap<>();
            int totalAmount = 0;
            int totalCount = 0;

            Cursor cursor = dbHelper.getAllRecords();
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        int amount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AMOUNT));
                        String category = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CATEGORY));
                        totalAmount += amount;
                        totalCount++;
                        int current = categoryTotals.containsKey(category) ? categoryTotals.get(category) : 0;
                        categoryTotals.put(category, current + amount);
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }

            List<Map.Entry<String, Integer>> entries = new ArrayList<>(categoryTotals.entrySet());
            Collections.sort(entries, (a, b) -> Integer.compare(b.getValue(), a.getValue()));

            int finalTotalAmount = totalAmount;
            int finalTotalCount = totalCount;
            runOnUiThread(() -> renderChart(entries, finalTotalAmount, finalTotalCount));
        }).start();
    }

    private void renderChart(List<Map.Entry<String, Integer>> entries, int totalAmount, int totalCount) {
        totalAmountText.setText("$ " + totalAmount);
        totalCountText.setText(totalCount + " 筆");
        int avg = totalCount == 0 ? 0 : Math.round((float) totalAmount / totalCount);
        averageAmountText.setText("$ " + avg);

        chartContainer.removeAllViews();
        if (totalAmount == 0 || entries.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            return;
        }
        emptyStateText.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Map.Entry<String, Integer> entry : entries) {
            View item = inflater.inflate(R.layout.item_chart_bar, chartContainer, false);
            TextView categoryName = item.findViewById(R.id.categoryName);
            TextView categoryAmount = item.findViewById(R.id.categoryAmount);
            TextView categoryPercent = item.findViewById(R.id.categoryPercent);
            LinearProgressIndicator progress = item.findViewById(R.id.categoryProgress);

            int amount = entry.getValue();
            int percent = Math.round(100f * amount / totalAmount);

            categoryName.setText(entry.getKey());
            categoryAmount.setText("$ " + amount);
            categoryPercent.setText(percent + "%");

            progress.setMax(100);
            progress.setProgressCompat(percent, false);

            chartContainer.addView(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadChart();
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

    @Override
    protected void onDestroy() {
        CloudBackupIndicator.unregister(this, backupIndicatorListener);
        super.onDestroy();
    }
}
