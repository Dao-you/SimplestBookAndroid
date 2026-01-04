package com.github.daoyou.simplestbook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private static final String TAG = "HistoryActivity";
    private MaterialToolbar historyToolbar;
    private TextView totalAmountText;
    private MaterialCardView cardTotal;
    private ListView historyListView;
    private FloatingActionButton fabAdd;
    private DatabaseHelper dbHelper;
    private List<Record> recordList;
    private RecordAdapter adapter;
    private String pendingCsvContent;
    private CsvHelper csvHelper;
    private SharedPreferences.OnSharedPreferenceChangeListener backupIndicatorListener;
    private MenuItem cloudStatusItem;
    private boolean recordReceiverRegistered;

    private long lastExitStatusTime = 0L;
    private boolean isNavigatingWithinApp = false;

    private final BroadcastReceiver recordUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received update broadcast");
            String recordId = intent != null ? intent.getStringExtra("extra_record_id") : null;
            String category = intent != null ? intent.getStringExtra("extra_category") : null;
            loadRecords(recordId, category);
        }
    };

    // 處理「儲存到檔案」的回傳
    private final ActivityResultLauncher<Intent> saveFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && pendingCsvContent != null) {
                        if (csvHelper.writeCsvToUri(uri, pendingCsvContent)) {
                            Toast.makeText(this, "已儲存至檔案系統", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "儲存失敗", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    // 處理「匯入資料」的回傳
    private final ActivityResultLauncher<Intent> importFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        // 彈窗詢問是要加入 (Append) 還是覆蓋 (Overwrite)
                        String[] options = {"加入現有資料 (Append)", "覆蓋現有資料 (Overwrite)"};
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("匯入選項")
                                .setItems(options, (dialog, which) -> {
                                    boolean overwrite = (which == 1);
                                    processImport(uri, overwrite);
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                }
            });

    private void processImport(Uri uri, boolean overwrite) {
        new Thread(() -> {
            List<Record> imported = csvHelper.importCsv(uri);
            if (!imported.isEmpty()) {
                if (overwrite) {
                    dbHelper.deleteAllRecords();
                }
                for (Record r : imported) {
                    dbHelper.insertRecord(r.getAmount(), r.getCategory(), r.getNote(), r.getLocationName(),
                            r.getTimestamp(), r.getLatitude(), r.getLongitude());
                }
                CloudBackupManager.requestSyncIfEnabled(getApplicationContext());
                runOnUiThread(() -> {
                    Toast.makeText(this, "成功匯入 " + imported.size() + " 筆資料", Toast.LENGTH_SHORT).show();
                    loadRecords();
                });
            } else {
                runOnUiThread(() -> Toast.makeText(this, "匯入失敗或無有效資料", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);

        dbHelper = new DatabaseHelper(this);
        csvHelper = new CsvHelper(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 綁定元素
        historyToolbar = findViewById(R.id.historyToolbar);
        totalAmountText = findViewById(R.id.totalAmountText);
        cardTotal = findViewById(R.id.cardTotal);
        historyListView = findViewById(R.id.historyListView);
        fabAdd = findViewById(R.id.fabAdd);

        setSupportActionBar(historyToolbar);

        // 工具列返回按鈕
        historyToolbar.setNavigationOnClickListener(v -> {
            triggerExitStatusChip();
            finish();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                triggerExitStatusChip();
                finish();
            }
        });

        // 點擊卡片跳轉至圖表頁面
        cardTotal.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChartActivity.class);
            startActivityWithExitSkip(intent);
        });

        // 點擊 FAB 返回主畫面
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivityWithExitSkip(intent);
        });

        if (!recordReceiverRegistered) {
            IntentFilter filter = new IntentFilter(AutoCategoryService.ACTION_RECORD_UPDATED);
            androidx.core.content.ContextCompat.registerReceiver(
                    this,
                    recordUpdatedReceiver,
                    filter,
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
            recordReceiverRegistered = true;
            Log.d(TAG, "Registered update receiver");
        }

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
                intent.putExtra("locationName", selectedRecord.getLocationName());
                intent.putExtra("timestamp", selectedRecord.getTimestamp());
                intent.putExtra("latitude", selectedRecord.getLatitude());
                intent.putExtra("longitude", selectedRecord.getLongitude());
                startActivityWithExitSkip(intent);
            }
        });

    }

    private void loadRecords() {
        loadRecords(null, null);
    }

    private void loadRecords(String animateRecordId, String toastCategory) {
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
                        String locationName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LOCATION_NAME));
                        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP));
                        double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LATITUDE));
                        double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LONGITUDE));

                        tempRecords.add(new Record(id, amount, category, note, locationName, timestamp, latitude, longitude));
                        total += amount;
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }

            final int finalTotal = total;
            runOnUiThread(() -> {
                if (recordList == null) {
                    recordList = new ArrayList<>();
                }
                recordList.clear();
                recordList.addAll(tempRecords);
                totalAmountText.setText("$ " + finalTotal);
                if (adapter == null) {
                    adapter = new RecordAdapter(HistoryActivity.this, recordList);
                    historyListView.setAdapter(adapter);
                } else {
                    adapter.notifyDataSetChanged();
                }
                if (animateRecordId != null) {
                    animateUpdatedRow(animateRecordId);
                }
                if (toastCategory != null && !toastCategory.isEmpty()) {
                    Toast.makeText(this, "自動類別：" + toastCategory, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void animateUpdatedRow(String recordId) {
        int position = -1;
        for (int i = 0; i < recordList.size(); i++) {
            if (recordId.equals(recordList.get(i).getId())) {
                position = i;
                break;
            }
        }
        if (position < 0) {
            return;
        }
        int targetPosition = position;
        historyListView.post(() -> {
            int first = historyListView.getFirstVisiblePosition();
            int last = historyListView.getLastVisiblePosition();
            if (targetPosition < first || targetPosition > last) {
                return;
            }
            android.view.View row = historyListView.getChildAt(targetPosition - first);
            if (row == null) {
                return;
            }
            float width = row.getWidth() > 0 ? row.getWidth() : historyListView.getWidth();
            row.animate().cancel();
            row.setTranslationX(width);
            row.animate()
                    .translationX(0f)
                    .setDuration(260)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_history, menu);
        cloudStatusItem = menu.findItem(R.id.action_cloud_status);
        CloudBackupIndicator.unregister(this, backupIndicatorListener);
        backupIndicatorListener = CloudBackupIndicator.register(this, cloudStatusItem);
        
        MenuItem clearItem = menu.findItem(R.id.action_clear_data);
        if (clearItem != null) {
            SpannableString s = new SpannableString(clearItem.getTitle());
            s.setSpan(new ForegroundColorSpan(Color.RED), 0, s.length(), 0);
            clearItem.setTitle(s);
        }
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_csv_export_file) {
            exportToFile();
            return true;
        } else if (id == R.id.action_csv_share) {
            shareByCsv();
            return true;
        } else if (id == R.id.action_csv_import) {
            importFromCsv();
            return true;
        } else if (id == R.id.action_cloud_status) {
            CloudBackupIndicator.showStatusSnackbar(this);
            return true;
        } else if (id == R.id.action_clear_data) {
            confirmClearData();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void confirmClearData() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("警告")
                .setMessage("確定要清除所有記帳資料嗎？此操作無法復原。")
                .setPositiveButton("確定清除", (dialog, which) -> {
                    new Thread(() -> {
                        dbHelper.deleteAllRecords();
                        runOnUiThread(() -> {
                            loadRecords();
                            Toast.makeText(this, "資料已全部清除", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportToFile() {
        if (recordList == null || recordList.isEmpty()) {
            Toast.makeText(this, "無資料可供匯出", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            pendingCsvContent = csvHelper.generateCsvContent(recordList);
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/csv");
                intent.putExtra(Intent.EXTRA_TITLE, "records.csv");
                saveFileLauncher.launch(intent);
            });
        }).start();
    }

    private void shareByCsv() {
        if (recordList == null || recordList.isEmpty()) {
            Toast.makeText(this, "無資料可供分享", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            String content = csvHelper.generateCsvContent(recordList);
            runOnUiThread(() -> csvHelper.shareCsv(content));
        }).start();
    }

    private void importFromCsv() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"text/csv", "text/comma-separated-values", "application/csv", "text/plain"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        importFileLauncher.launch(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecords();
        lastExitStatusTime = 0L;
        isNavigatingWithinApp = false;
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isNavigatingWithinApp) {
            return;
        }
        triggerExitStatusChip();
    }

    private void startActivityWithExitSkip(Intent intent) {
        isNavigatingWithinApp = true;
        startActivity(intent);
    }

    private void triggerExitStatusChip() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastExitStatusTime < 1000L) {
            return;
        }
        lastExitStatusTime = now;
        StatusChipService.show(this);
    }

    @Override
    protected void onDestroy() {
        if (recordReceiverRegistered) {
            unregisterReceiver(recordUpdatedReceiver);
            recordReceiverRegistered = false;
        }
        CloudBackupIndicator.unregister(this, backupIndicatorListener);
        super.onDestroy();
    }
}
