package com.github.daoyou.simplestbook;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecurringPaymentEditActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private TextInputEditText amountInput;
    private TextInputEditText noteInput;
    private GridView categoryGrid;
    private CategoryAdapter categoryAdapter;
    private List<Category> categories;
    private MaterialButtonToggleGroup frequencyGroup;
    private Spinner weekdaySpinner;
    private View monthlyContainer;
    private View yearlyContainer;
    private Spinner dayOfMonthSpinner;
    private Spinner yearMonthSpinner;
    private Spinner yearDaySpinner;
    private String recurringId;
    private boolean isEditMode;
    private int originalAmount;
    private String originalNote;
    private String originalCategory;
    private String originalFrequency;
    private int originalDayOfWeek;
    private int originalDayOfMonth;
    private int originalMonth;
    private com.google.android.material.button.MaterialButton saveButton;
    private com.google.android.material.button.MaterialButton deleteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_recurring_payment_edit);

        dbHelper = new DatabaseHelper(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.recurringEditToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        amountInput = findViewById(R.id.recurringAmountInput);
        noteInput = findViewById(R.id.recurringNoteInput);
        categoryGrid = findViewById(R.id.recurringCategoryGrid);
        frequencyGroup = findViewById(R.id.recurringFrequencyGroup);
        weekdaySpinner = findViewById(R.id.recurringWeekday);
        monthlyContainer = findViewById(R.id.recurringMonthlyContainer);
        yearlyContainer = findViewById(R.id.recurringYearlyContainer);
        dayOfMonthSpinner = findViewById(R.id.recurringDayOfMonth);
        yearMonthSpinner = findViewById(R.id.recurringMonth);
        yearDaySpinner = findViewById(R.id.recurringYearDay);
        saveButton = findViewById(R.id.recurringSaveButton);
        deleteButton = findViewById(R.id.recurringDeleteButton);

        setupCategoryGrid();
        setupSpinners();
        setupFrequencyGroup();

        recurringId = getIntent().getStringExtra("recurringId");
        if (recurringId != null) {
            isEditMode = true;
            loadExisting(recurringId);
            toolbar.setTitle("編輯週期性付款");
        } else {
            isEditMode = false;
            frequencyGroup.check(R.id.recurringWeeklyButton);
        }
        deleteButton.setVisibility(View.GONE);

        saveButton.setOnClickListener(v -> handlePrimaryAction());
        deleteButton.setOnClickListener(v -> {});

        amountInput.addTextChangedListener(new SimpleTextWatcher(this::updatePrimaryActionLabel));
        noteInput.addTextChangedListener(new SimpleTextWatcher(this::updatePrimaryActionLabel));
        updatePrimaryActionLabel();
    }

    private void setupCategoryGrid() {
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
        categories.add(new Category("fixed_other", "其他"));
        categoryAdapter = new CategoryAdapter(this, categories);
        categoryGrid.setAdapter(categoryAdapter);
        categoryGrid.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = categories.get(position).getName();
            categoryAdapter.setSelectedCategory(selectedName);
            updatePrimaryActionLabel();
        });
    }

    private void setupSpinners() {
        android.widget.ArrayAdapter<CharSequence> weekdayAdapter =
                android.widget.ArrayAdapter.createFromResource(this,
                        R.array.recurring_weekday_labels,
                        android.R.layout.simple_spinner_item);
        weekdayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        weekdaySpinner.setAdapter(weekdayAdapter);

        android.widget.ArrayAdapter<String> dayAdapter =
                new android.widget.ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item,
                        buildNumberLabels(1, 31, "日"));
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dayOfMonthSpinner.setAdapter(dayAdapter);
        yearDaySpinner.setAdapter(dayAdapter);

        android.widget.ArrayAdapter<String> monthAdapter =
                new android.widget.ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item,
                        buildNumberLabels(1, 12, "月"));
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        yearMonthSpinner.setAdapter(monthAdapter);

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePrimaryActionLabel();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        weekdaySpinner.setOnItemSelectedListener(listener);
        dayOfMonthSpinner.setOnItemSelectedListener(listener);
        yearMonthSpinner.setOnItemSelectedListener(listener);
        yearDaySpinner.setOnItemSelectedListener(listener);
    }

    private void setupFrequencyGroup() {
        frequencyGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.recurringWeeklyButton) {
                weekdaySpinner.setVisibility(View.VISIBLE);
                monthlyContainer.setVisibility(View.GONE);
                yearlyContainer.setVisibility(View.GONE);
            } else if (checkedId == R.id.recurringMonthlyButton) {
                weekdaySpinner.setVisibility(View.GONE);
                monthlyContainer.setVisibility(View.VISIBLE);
                yearlyContainer.setVisibility(View.GONE);
            } else {
                weekdaySpinner.setVisibility(View.GONE);
                monthlyContainer.setVisibility(View.GONE);
                yearlyContainer.setVisibility(View.VISIBLE);
            }
            updatePrimaryActionLabel();
        });
    }

    private void loadExisting(String id) {
        Cursor cursor = dbHelper.getRecurringPaymentById(id);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int amount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_AMOUNT));
                String category = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_CATEGORY));
                String note = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_NOTE));
                String frequency = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_FREQUENCY));
                int dayOfWeek = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_DAY_OF_WEEK));
                int dayOfMonth = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_DAY_OF_MONTH));
                int month = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RECURRING_MONTH));

                amountInput.setText(String.valueOf(amount));
                noteInput.setText(note);
                categoryAdapter.setSelectedCategory(category);

                if (RecurringPayment.FREQ_WEEKLY.equals(frequency)) {
                    frequencyGroup.check(R.id.recurringWeeklyButton);
                    weekdaySpinner.setSelection(Math.max(dayOfWeek - 1, 0));
                } else if (RecurringPayment.FREQ_MONTHLY.equals(frequency)) {
                    frequencyGroup.check(R.id.recurringMonthlyButton);
                    dayOfMonthSpinner.setSelection(Math.max(dayOfMonth - 1, 0));
                } else {
                    frequencyGroup.check(R.id.recurringYearlyButton);
                    yearMonthSpinner.setSelection(Math.max(month - 1, 0));
                    yearDaySpinner.setSelection(Math.max(dayOfMonth - 1, 0));
                }

                originalAmount = amount;
                originalNote = note == null ? "" : note;
                originalCategory = category == null ? "" : category;
                originalFrequency = frequency == null ? "" : frequency;
                originalDayOfWeek = dayOfWeek;
                originalDayOfMonth = dayOfMonth;
                originalMonth = month;
            }
            cursor.close();
        }
        updatePrimaryActionLabel();
    }

    private void save() {
        String amountStr = amountInput.getText() == null ? "" : amountInput.getText().toString().trim();
        String note = noteInput.getText() == null ? "" : noteInput.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Toast.makeText(this, "請輸入金額", Toast.LENGTH_SHORT).show();
            return;
        }
        if (note.isEmpty()) {
            Toast.makeText(this, "請輸入說明", Toast.LENGTH_SHORT).show();
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "金額格式錯誤", Toast.LENGTH_SHORT).show();
            return;
        }
        String category = categoryAdapter.getSelectedCategoryName();
        if (category == null || category.isEmpty()) {
            Toast.makeText(this, "請選擇類別", Toast.LENGTH_SHORT).show();
            return;
        }

        int checked = frequencyGroup.getCheckedButtonId();
        String frequency;
        int dayOfWeek = 0;
        int dayOfMonth = 0;
        int month = 0;

        if (checked == R.id.recurringWeeklyButton) {
            frequency = RecurringPayment.FREQ_WEEKLY;
            dayOfWeek = weekdaySpinner.getSelectedItemPosition() + 1;
        } else if (checked == R.id.recurringMonthlyButton) {
            frequency = RecurringPayment.FREQ_MONTHLY;
            dayOfMonth = dayOfMonthSpinner.getSelectedItemPosition() + 1;
        } else {
            frequency = RecurringPayment.FREQ_YEARLY;
            month = yearMonthSpinner.getSelectedItemPosition() + 1;
            dayOfMonth = yearDaySpinner.getSelectedItemPosition() + 1;
        }

        if (recurringId == null) {
            recurringId = UUID.randomUUID().toString();
            dbHelper.insertRecurringPayment(recurringId, amount, category, note, frequency, dayOfWeek, dayOfMonth, month);
        } else {
            dbHelper.updateRecurringPayment(recurringId, amount, category, note, frequency, dayOfWeek, dayOfMonth, month);
        }
        RecurringPaymentWorker.schedule(getApplicationContext());
        Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void handlePrimaryAction() {
        if (!isEditMode) {
            if (hasInput()) {
                save();
            } else {
                finish();
            }
            return;
        }
        if (isEdited()) {
            save();
        } else {
            confirmDelete();
        }
    }

    private void updatePrimaryActionLabel() {
        if (!isEditMode) {
            if (hasInput()) {
                saveButton.setText("儲存");
                int primary = androidx.core.content.ContextCompat.getColor(this, R.color.colorPrimary);
                saveButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary));
                saveButton.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
            } else {
                saveButton.setText("取消");
                int primary = androidx.core.content.ContextCompat.getColor(this, R.color.colorPrimary);
                saveButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary));
                saveButton.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
            }
            return;
        }
        if (isEdited()) {
            saveButton.setText("更新");
            int primary = androidx.core.content.ContextCompat.getColor(this, R.color.colorPrimary);
            saveButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary));
            saveButton.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
        } else {
            saveButton.setText("刪除");
            int errorColor = androidx.core.content.ContextCompat.getColor(this, R.color.colorError);
            saveButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(errorColor));
            saveButton.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
        }
    }

    private boolean isEdited() {
        if (!isEditMode) {
            return false;
        }
        String amountStr = amountInput.getText() == null ? "" : amountInput.getText().toString().trim();
        int amount = 0;
        try {
            amount = amountStr.isEmpty() ? 0 : Integer.parseInt(amountStr);
        } catch (NumberFormatException ignored) {}
        String note = noteInput.getText() == null ? "" : noteInput.getText().toString().trim();
        String category = categoryAdapter.getSelectedCategoryName();
        String safeCategory = category == null ? "" : category;

        String frequency;
        int dayOfWeek = 0;
        int dayOfMonth = 0;
        int month = 0;
        int checked = frequencyGroup.getCheckedButtonId();
        if (checked == R.id.recurringWeeklyButton) {
            frequency = RecurringPayment.FREQ_WEEKLY;
            dayOfWeek = weekdaySpinner.getSelectedItemPosition() + 1;
        } else if (checked == R.id.recurringMonthlyButton) {
            frequency = RecurringPayment.FREQ_MONTHLY;
            dayOfMonth = dayOfMonthSpinner.getSelectedItemPosition() + 1;
        } else {
            frequency = RecurringPayment.FREQ_YEARLY;
            month = yearMonthSpinner.getSelectedItemPosition() + 1;
            dayOfMonth = yearDaySpinner.getSelectedItemPosition() + 1;
        }

        return amount != originalAmount
                || !note.equals(originalNote)
                || !safeCategory.equals(originalCategory)
                || !frequency.equals(originalFrequency)
                || dayOfWeek != originalDayOfWeek
                || dayOfMonth != originalDayOfMonth
                || month != originalMonth;
    }

    private boolean hasInput() {
        String amountStr = amountInput.getText() == null ? "" : amountInput.getText().toString().trim();
        String note = noteInput.getText() == null ? "" : noteInput.getText().toString().trim();
        String category = categoryAdapter.getSelectedCategoryName();
        String safeCategory = category == null ? "" : category;
        return !amountStr.isEmpty() || !note.isEmpty() || !safeCategory.isEmpty();
    }

    private void confirmDelete() {
        if (recurringId == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("刪除週期性付款")
                .setMessage("確定要刪除這筆週期性付款嗎？")
                .setPositiveButton("刪除", (dialog, which) -> {
                    dbHelper.deleteRecurringPayment(recurringId);
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private List<String> buildNumberLabels(int start, int end, String suffix) {
        List<String> labels = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            labels.add(i + suffix);
        }
        return labels;
    }

    private static class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable onChanged;

        SimpleTextWatcher(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            onChanged.run();
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {}
    }
}
