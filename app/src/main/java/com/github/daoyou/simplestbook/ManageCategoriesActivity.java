package com.github.daoyou.simplestbook;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManageCategoriesActivity extends AppCompatActivity {

    private MaterialToolbar categoryToolbar;
    private TextInputLayout newCategoryLayout;
    private TextInputEditText newCategoryInput;
    private RecyclerView categoryRecyclerView;
    private MaterialButton finishCategoryButton;
    private DatabaseHelper dbHelper;
    private List<Category> categories;
    private CategoryManageAdapter adapter;
    private SharedPreferences.OnSharedPreferenceChangeListener backupIndicatorListener;
    private MenuItem cloudStatusItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_manage_categories);

        dbHelper = new DatabaseHelper(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        categoryToolbar = findViewById(R.id.categoryToolbar);
        newCategoryLayout = findViewById(R.id.newCategoryLayout);
        newCategoryInput = findViewById(R.id.newCategoryInput);
        categoryRecyclerView = findViewById(R.id.categoryRecyclerView);
        finishCategoryButton = findViewById(R.id.finishCategoryButton);
        setSupportActionBar(categoryToolbar);

        categoryToolbar.setNavigationOnClickListener(v -> finish());
        finishCategoryButton.setOnClickListener(v -> finish());

        loadCategories();

        // 點擊圖示新增
        newCategoryLayout.setEndIconOnClickListener(v -> addCategory());

        // 鍵盤送出新增
        newCategoryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addCategory();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        CloudBackupIndicator.unregister(this, backupIndicatorListener);
        super.onDestroy();
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

    private void addCategory() {
        String name = newCategoryInput.getText().toString().trim();
        if (!name.isEmpty()) {
            dbHelper.insertCategory(name, categories.size());
            newCategoryInput.setText("");
            loadCategories();
            Toast.makeText(this, "類別已新增", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCategories() {
        categories = new ArrayList<>();
        Cursor cursor = dbHelper.getAllCategories();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String id = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CAT_ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CAT_NAME));
                    if (!name.equals("其他")) {
                        categories.add(new Category(id, name));
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        adapter = new CategoryManageAdapter(categories);
        categoryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        categoryRecyclerView.setAdapter(adapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                Collections.swap(categories, fromPosition, toPosition);
                adapter.notifyItemMoved(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                for (int i = 0; i < categories.size(); i++) {
                    dbHelper.updateCategoryOrder(categories.get(i).getId(), i);
                }
            }
        });
        itemTouchHelper.attachToRecyclerView(categoryRecyclerView);
    }

    private class CategoryManageAdapter extends RecyclerView.Adapter<CategoryManageAdapter.ViewHolder> {
        private List<Category> list;
        public CategoryManageAdapter(List<Category> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Category cat = list.get(position);
            holder.text.setText(cat.getName());
            holder.itemView.setOnClickListener(v -> {
                new AlertDialog.Builder(ManageCategoriesActivity.this)
                        .setTitle("刪除類別")
                        .setMessage("確定要刪除「" + cat.getName() + "」嗎？")
                        .setPositiveButton("刪除", (dialog, which) -> {
                            dbHelper.deleteCategory(cat.getId());
                            loadCategories();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            ViewHolder(View itemView) {
                super(itemView);
                text = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}
