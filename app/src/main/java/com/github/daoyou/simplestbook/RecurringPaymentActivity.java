package com.github.daoyou.simplestbook;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

public class RecurringPaymentActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private ListView recurringList;
    private RecurringPaymentAdapter adapter;
    private List<RecurringPayment> recurringItems;
    private SharedPreferences.OnSharedPreferenceChangeListener backupIndicatorListener;
    private MenuItem cloudStatusItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_recurring_payment);

        dbHelper = new DatabaseHelper(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.recurringToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recurringList = findViewById(R.id.recurringList);
        loadRecurringList();

        findViewById(R.id.recurringAddButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, RecurringPaymentEditActivity.class);
            startActivity(intent);
        });
        recurringList.setOnItemClickListener((parent, view, position, id) -> {
            RecurringPayment item = recurringItems.get(position);
            Intent intent = new Intent(this, RecurringPaymentEditActivity.class);
            intent.putExtra("recurringId", item.getId());
            startActivity(intent);
        });
    }


    private void loadRecurringList() {
        recurringItems = new ArrayList<>();
        Cursor cursor = dbHelper.getAllRecurringPayments();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    recurringItems.add(readRecurring(cursor));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        adapter = new RecurringPaymentAdapter(this, recurringItems);
        recurringList.setAdapter(adapter);
    }

    private RecurringPayment readRecurring(Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_ID));
        int amount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_AMOUNT));
        String category = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_CATEGORY));
        String note = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_NOTE));
        String frequency = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_FREQUENCY));
        int dayOfWeek = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_DAY_OF_WEEK));
        int dayOfMonth = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_DAY_OF_MONTH));
        int month = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_MONTH));
        int lastRunDate = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_LAST_RUN_DATE));
        return new RecurringPayment(id, amount, category, note, frequency, dayOfWeek, dayOfMonth, month, lastRunDate);
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
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

    @Override
    protected void onResume() {
        super.onResume();
        loadRecurringList();
    }
}
