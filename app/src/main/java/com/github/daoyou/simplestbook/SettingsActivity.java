package com.github.daoyou.simplestbook;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "SimplestBookSettings";
    public static final String KEY_LOCATION_ENABLED = "location_enabled";
    public static final String KEY_DEFAULT_CATEGORY = "default_category"; // 0: First, 1: Other, 2: None, 3: Auto
    public static final String KEY_AUTO_CATEGORY_API_KEY = "auto_category_api_key";
    public static final String KEY_AUTO_CATEGORY_API_URL = "auto_category_api_url";
    public static final String KEY_AUTO_HISTORY = "auto_history";
    public static final String KEY_THEME = "theme_mode"; // -1: System, 1: Light, 2: Dark
    public static final String KEY_CLOUD_BACKUP_ENABLED = "cloud_backup_enabled";
    public static final String KEY_RECURRING_NOTIFY_ENABLED = "recurring_notify_enabled";

    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener backupIndicatorListener;
    private MaterialSwitch switchCloudBackup;
    private TextView textCloudAccount;
    private MaterialButton buttonCloudSignIn;
    private MaterialButton buttonCloudBackupNow;
    private MaterialButton buttonCloudRestore;
    private MaterialButton buttonDebugInsertRecords;
    private MaterialButton buttonDebugRecurringMinute;
    private boolean suppressCloudSwitchListener;
    private MenuItem cloudStatusItem;
    private GoogleSignInClient googleSignInClient;
    private FirebaseAuth firebaseAuth;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private Runnable pendingSignInAction;
    private DatabaseHelper dbHelper;
    private TextInputLayout inputLayoutApiKey;
    private TextInputLayout inputLayoutApiUrl;
    private TextInputEditText editApiKey;
    private MaterialAutoCompleteTextView editApiUrl;
    private MaterialButton buttonApiKeyHelp;
    private MaterialRadioButton radioFirst;
    private MaterialRadioButton radioOther;
    private MaterialRadioButton radioNone;
    private MaterialRadioButton radioAuto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        dbHelper = new DatabaseHelper(this);
        firebaseAuth = FirebaseAuth.getInstance();
        googleSignInClient = GoogleSignIn.getClient(this,
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build());
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleGoogleSignInResult(result.getData())
        );

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar settingsToolbar = findViewById(R.id.settingsToolbar);
        MaterialSwitch switchLocation = findViewById(R.id.switchLocation);
        MaterialSwitch switchAutoHistory = findViewById(R.id.switchAutoHistory);
        MaterialSwitch switchRecurringNotify = findViewById(R.id.switchRecurringNotify);
        switchCloudBackup = findViewById(R.id.switchCloudBackup);
        textCloudAccount = findViewById(R.id.textCloudAccount);
        buttonCloudSignIn = findViewById(R.id.buttonCloudSignIn);
        buttonCloudBackupNow = findViewById(R.id.buttonCloudBackupNow);
        buttonCloudRestore = findViewById(R.id.buttonCloudRestore);
        buttonDebugInsertRecords = findViewById(R.id.buttonDebugInsertRecords);
        buttonDebugRecurringMinute = findViewById(R.id.buttonDebugRecurringMinute);
        RadioGroup radioGroupDefaultCategory = findViewById(R.id.radioGroupDefaultCategory);
        RadioGroup radioGroupTheme = findViewById(R.id.radioGroupTheme);
        
        radioFirst = findViewById(R.id.radioFirst);
        radioOther = findViewById(R.id.radioOther);
        radioNone = findViewById(R.id.radioNone);
        radioAuto = findViewById(R.id.radioAuto);

        inputLayoutApiKey = findViewById(R.id.inputLayoutApiKey);
        inputLayoutApiUrl = findViewById(R.id.inputLayoutApiUrl);
        editApiKey = findViewById(R.id.editApiKey);
        editApiUrl = findViewById(R.id.editApiUrl);
        buttonApiKeyHelp = findViewById(R.id.buttonApiKeyHelp);
        
        MaterialRadioButton radioThemeSystem = findViewById(R.id.radioThemeSystem);
        MaterialRadioButton radioThemeLight = findViewById(R.id.radioThemeLight);
        MaterialRadioButton radioThemeDark = findViewById(R.id.radioThemeDark);

        if (settingsToolbar != null) {
            setSupportActionBar(settingsToolbar);
            settingsToolbar.setNavigationOnClickListener(v -> finish());
        }

        // 外觀設定 (主題)
        int currentTheme = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (currentTheme == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM && radioThemeSystem != null) radioThemeSystem.setChecked(true);
        else if (currentTheme == AppCompatDelegate.MODE_NIGHT_NO && radioThemeLight != null) radioThemeLight.setChecked(true);
        else if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES && radioThemeDark != null) radioThemeDark.setChecked(true);

        if (radioGroupTheme != null) {
            radioGroupTheme.setOnCheckedChangeListener((group, checkedId) -> {
                int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                if (checkedId == R.id.radioThemeLight) mode = AppCompatDelegate.MODE_NIGHT_NO;
                else if (checkedId == R.id.radioThemeDark) mode = AppCompatDelegate.MODE_NIGHT_YES;
                
                prefs.edit().putInt(KEY_THEME, mode).apply();
                AppCompatDelegate.setDefaultNightMode(mode);
            });
        }

        // 載入其他目前設定
        if (switchLocation != null) {
            switchLocation.setChecked(prefs.getBoolean(KEY_LOCATION_ENABLED, true));
            switchLocation.setOnCheckedChangeListener((v, isChecked) -> 
                prefs.edit().putBoolean(KEY_LOCATION_ENABLED, isChecked).apply());
        }

        if (switchAutoHistory != null) {
            switchAutoHistory.setChecked(prefs.getBoolean(KEY_AUTO_HISTORY, true));
            switchAutoHistory.setOnCheckedChangeListener((v, isChecked) -> 
                prefs.edit().putBoolean(KEY_AUTO_HISTORY, isChecked).apply());
        }
        if (switchRecurringNotify != null) {
            switchRecurringNotify.setChecked(prefs.getBoolean(KEY_RECURRING_NOTIFY_ENABLED, true));
            switchRecurringNotify.setOnCheckedChangeListener((v, isChecked) ->
                prefs.edit().putBoolean(KEY_RECURRING_NOTIFY_ENABLED, isChecked).apply());
        }

        if (switchCloudBackup != null) {
            setCloudSwitchChecked(prefs.getBoolean(KEY_CLOUD_BACKUP_ENABLED, false));
            switchCloudBackup.setOnCheckedChangeListener((v, isChecked) -> {
                if (suppressCloudSwitchListener) {
                    return;
                }
                handleCloudBackupToggle(isChecked);
            });
        }

        if (buttonCloudSignIn != null) {
            buttonCloudSignIn.setOnClickListener(v -> startGoogleSignIn(null));
        }

        if (buttonCloudBackupNow != null) {
            buttonCloudBackupNow.setOnClickListener(v -> ensureSignedInThen(() -> {
                if (!prefs.getBoolean(KEY_CLOUD_BACKUP_ENABLED, false)) {
                    Toast.makeText(this, "請先啟用雲端備份", Toast.LENGTH_SHORT).show();
                    return;
                }
                CloudBackupManager.syncNow(getApplicationContext());
                Toast.makeText(this, "開始備份", Toast.LENGTH_SHORT).show();
            }));
        }

        if (buttonCloudRestore != null) {
            buttonCloudRestore.setOnClickListener(v -> ensureSignedInThen(this::showRestoreOptions));
        }
        if (buttonDebugInsertRecords != null) {
            buttonDebugInsertRecords.setOnClickListener(v -> insertDebugRecords());
        }
        if (buttonDebugRecurringMinute != null) {
            buttonDebugRecurringMinute.setOnClickListener(v -> addDebugRecurringMinute());
        }
        
        int defaultCat = prefs.getInt(KEY_DEFAULT_CATEGORY, 0);
        if (radioFirst != null && defaultCat == 0) radioFirst.setChecked(true);
        else if (radioOther != null && defaultCat == 1) radioOther.setChecked(true);
        else if (radioNone != null && defaultCat == 2) radioNone.setChecked(true);
        else if (radioAuto != null && defaultCat == 3) radioAuto.setChecked(true);
        updateAutoCategoryInputs(defaultCat == 3);

        if (radioGroupDefaultCategory != null) {
            radioGroupDefaultCategory.setOnCheckedChangeListener((group, checkedId) -> {
                int value = 0;
                if (checkedId == R.id.radioFirst) value = 0;
                else if (checkedId == R.id.radioOther) value = 1;
                else if (checkedId == R.id.radioNone) value = 2;
                else if (checkedId == R.id.radioAuto) value = 3;
                prefs.edit().putInt(KEY_DEFAULT_CATEGORY, value).apply();
                updateAutoCategoryInputs(value == 3);
            });
        }

        if (editApiKey != null) {
            editApiKey.setText(prefs.getString(KEY_AUTO_CATEGORY_API_KEY, ""));
            editApiKey.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    prefs.edit().putString(KEY_AUTO_CATEGORY_API_KEY, s.toString()).apply();
                }
            });
        }

        if (editApiUrl != null) {
            String defaultApiUrl = getString(R.string.default_openai_api_url);
            String storedUrl = prefs.getString(KEY_AUTO_CATEGORY_API_URL, "");
            if (storedUrl == null || storedUrl.trim().isEmpty()) {
                storedUrl = defaultApiUrl;
                prefs.edit().putString(KEY_AUTO_CATEGORY_API_URL, storedUrl).apply();
            }
            editApiUrl.setText(storedUrl);
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    this,
                    android.R.layout.simple_list_item_1,
                    new String[]{
                            getString(R.string.api_url_openai_label) + " - " + getString(R.string.default_openai_api_url),
                            getString(R.string.api_url_github_label) + " - " + getString(R.string.default_github_models_api_url)
                    });
            editApiUrl.setAdapter(adapter);
            editApiUrl.setOnItemClickListener((parent, view, position, id) -> {
                String value = (String) parent.getItemAtPosition(position);
                int index = value.indexOf(" - ");
                if (index >= 0) {
                    String url = value.substring(index + 3).trim();
                    editApiUrl.setText(url);
                    editApiUrl.setSelection(url.length());
                    prefs.edit().putString(KEY_AUTO_CATEGORY_API_URL, url).apply();
                }
            });
            editApiUrl.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    prefs.edit().putString(KEY_AUTO_CATEGORY_API_URL, s.toString()).apply();
                }
            });
        }

        if (buttonApiKeyHelp != null) {
            buttonApiKeyHelp.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.api_key_help_url)));
                startActivity(intent);
            });
        }

        updateCloudUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        enforceDefaultCategoryWhenMissingApi();
    }

    private void enforceDefaultCategoryWhenMissingApi() {
        String apiKey = prefs.getString(KEY_AUTO_CATEGORY_API_KEY, "");
        String apiUrl = prefs.getString(KEY_AUTO_CATEGORY_API_URL, "");
        boolean missingApi = apiKey == null || apiKey.trim().isEmpty()
                || apiUrl == null || apiUrl.trim().isEmpty();
        if (!missingApi) {
            return;
        }
        int current = prefs.getInt(KEY_DEFAULT_CATEGORY, 0);
        if (current != 0) {
            prefs.edit().putInt(KEY_DEFAULT_CATEGORY, 0).apply();
            if (radioFirst != null) {
                radioFirst.setChecked(true);
            }
            Toast.makeText(this, "已改回預設：第一項", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAutoCategoryInputs(boolean enabled) {
        if (inputLayoutApiKey != null) {
            inputLayoutApiKey.setEnabled(enabled);
        }
        if (inputLayoutApiUrl != null) {
            inputLayoutApiUrl.setEnabled(enabled);
        }
        if (editApiKey != null) {
            editApiKey.setEnabled(enabled);
        }
        if (editApiUrl != null) {
            editApiUrl.setEnabled(enabled);
        }
    }

    @Override
    protected void onDestroy() {
        CloudBackupIndicator.unregister(this, backupIndicatorListener);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCloudUi();
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

    private void handleCloudBackupToggle(boolean enabled) {
        if (!enabled) {
            prefs.edit().putBoolean(KEY_CLOUD_BACKUP_ENABLED, false).apply();
            CloudBackupManager.markDisabled(getApplicationContext());
            updateCloudUi();
            return;
        }

        startGoogleSignIn(() -> {
            prefs.edit().putBoolean(KEY_CLOUD_BACKUP_ENABLED, true).apply();
            CloudBackupManager.syncNow(getApplicationContext());
            updateCloudUi();
        });
    }

    private void startGoogleSignIn(Runnable onSuccess) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            updateCloudUi();
            return;
        }
        pendingSignInAction = onSuccess;
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void ensureSignedInThen(Runnable action) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            if (action != null) {
                action.run();
            }
            return;
        }
        startGoogleSignIn(action);
    }

    private void showRestoreOptions() {
        if (!prefs.getBoolean(KEY_CLOUD_BACKUP_ENABLED, false)) {
            Toast.makeText(this, "請先啟用雲端備份", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] options = {"加入現有資料 (Append)", "覆蓋現有資料 (Overwrite)"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("恢復選項")
                .setItems(options, (dialog, which) -> {
                    boolean overwrite = (which == 1);
                    CloudBackupManager.restoreFromCloud(getApplicationContext(), overwrite,
                            (success, message, recordCount, categoryCount) -> {
                                if (success) {
                                    String text = "已恢復 " + recordCount + " 筆資料";
                                    if (categoryCount > 0) {
                                        text += "，類別 " + categoryCount + " 筆";
                                    }
                                    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "恢復失敗", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateCloudUi() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        String accountText = "尚未登入";
        if (user != null) {
            if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                accountText = user.getEmail();
            } else if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                accountText = user.getDisplayName();
            } else {
                accountText = "已登入";
            }
        }
        if (textCloudAccount != null) {
            textCloudAccount.setText(accountText);
        }

        boolean enabled = prefs.getBoolean(KEY_CLOUD_BACKUP_ENABLED, false);
        if (buttonCloudBackupNow != null) {
            buttonCloudBackupNow.setEnabled(enabled && user != null);
        }
        if (buttonCloudRestore != null) {
            buttonCloudRestore.setEnabled(enabled && user != null);
        }
        if (buttonCloudSignIn != null) {
            buttonCloudSignIn.setEnabled(true);
        }
        if (switchCloudBackup != null && switchCloudBackup.isChecked() != enabled) {
            setCloudSwitchChecked(enabled);
        }
    }

    private void setCloudSwitchChecked(boolean checked) {
        if (switchCloudBackup == null) {
            return;
        }
        suppressCloudSwitchListener = true;
        switchCloudBackup.setChecked(checked);
        suppressCloudSwitchListener = false;
    }

    private void handleGoogleSignInResult(Intent data) {
        if (data == null) {
            onGoogleSignInFailed();
            return;
        }
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || account.getIdToken() == null) {
                onGoogleSignInFailed();
                return;
            }
            firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(account.getIdToken(), null))
                    .addOnSuccessListener(result -> onGoogleSignInSuccess())
                    .addOnFailureListener(e -> onGoogleSignInFailed());
        } catch (ApiException e) {
            onGoogleSignInFailed();
        }
    }

    private void onGoogleSignInSuccess() {
        if (pendingSignInAction != null) {
            pendingSignInAction.run();
            pendingSignInAction = null;
        }
        updateCloudUi();
    }

    private void onGoogleSignInFailed() {
        pendingSignInAction = null;
        if (switchCloudBackup != null && !prefs.getBoolean(KEY_CLOUD_BACKUP_ENABLED, false)) {
            setCloudSwitchChecked(false);
        }
        Toast.makeText(this, "Google 登入失敗", Toast.LENGTH_SHORT).show();
        updateCloudUi();
    }

    private void insertDebugRecords() {
        new Thread(() -> {
            List<DebugRecord> records = loadDebugRecords();
            if (records.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "偵錯資料讀取失敗", Toast.LENGTH_SHORT).show());
                return;
            }
            Random random = new Random();
            long now = System.currentTimeMillis();
            long maxOffset = 30L * 24 * 60 * 60 * 1000;
            for (int i = 0; i < 200; i++) {
                DebugRecord source = records.get(random.nextInt(records.size()));
                long ts = now - (long) (random.nextDouble() * maxOffset);
                dbHelper.insertRecord(source.amount, source.category, source.note, "", ts, 0.0, 0.0);
            }
            CloudBackupManager.requestSyncIfEnabled(getApplicationContext());
            runOnUiThread(() -> Toast.makeText(this, "已新增 200 筆記帳資料", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void addDebugRecurringMinute() {
        new Thread(() -> {
            String id = UUID.randomUUID().toString();
            dbHelper.insertRecurringPayment(
                    id,
                    50,
                    "其他",
                    "偵錯：每分鐘付款",
                    RecurringPayment.FREQ_MINUTE,
                    0,
                    0,
                    0
            );
            RecurringPaymentAlarmReceiver.scheduleAllMinute(getApplicationContext());
            runOnUiThread(() -> Toast.makeText(this, "已新增每分鐘付款", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private List<DebugRecord> loadDebugRecords() {
        List<DebugRecord> records = new ArrayList<>();
        try (InputStream inputStream = getResources().openRawResource(R.raw.debug_records);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String note = obj.optString("note", "");
                String category = obj.optString("category", "其他");
                int amount = obj.optInt("amount", 0);
                if (amount > 0 && !note.isEmpty()) {
                    records.add(new DebugRecord(amount, category, note));
                }
            }
        } catch (Exception ignored) {
            return records;
        }
        return records;
    }

    private static class DebugRecord {
        final int amount;
        final String category;
        final String note;

        DebugRecord(int amount, String category, String note) {
            this.amount = amount;
            this.category = category;
            this.note = note;
        }
    }
}
