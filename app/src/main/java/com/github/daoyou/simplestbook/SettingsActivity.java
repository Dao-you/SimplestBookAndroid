package com.github.daoyou.simplestbook;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "SimplestBookSettings";
    public static final String KEY_LOCATION_ENABLED = "location_enabled";
    public static final String KEY_DEFAULT_CATEGORY = "default_category"; // 0: First, 1: Other, 2: None
    public static final String KEY_AUTO_HISTORY = "auto_history";
    public static final String KEY_THEME = "theme_mode"; // -1: System, 1: Light, 2: Dark

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar settingsToolbar = findViewById(R.id.settingsToolbar);
        MaterialSwitch switchLocation = findViewById(R.id.switchLocation);
        MaterialSwitch switchAutoHistory = findViewById(R.id.switchAutoHistory);
        RadioGroup radioGroupDefaultCategory = findViewById(R.id.radioGroupDefaultCategory);
        RadioGroup radioGroupTheme = findViewById(R.id.radioGroupTheme);
        
        MaterialRadioButton radioFirst = findViewById(R.id.radioFirst);
        MaterialRadioButton radioOther = findViewById(R.id.radioOther);
        MaterialRadioButton radioNone = findViewById(R.id.radioNone);
        
        MaterialRadioButton radioThemeSystem = findViewById(R.id.radioThemeSystem);
        MaterialRadioButton radioThemeLight = findViewById(R.id.radioThemeLight);
        MaterialRadioButton radioThemeDark = findViewById(R.id.radioThemeDark);

        if (settingsToolbar != null) {
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
        
        int defaultCat = prefs.getInt(KEY_DEFAULT_CATEGORY, 0);
        if (radioFirst != null && defaultCat == 0) radioFirst.setChecked(true);
        else if (radioOther != null && defaultCat == 1) radioOther.setChecked(true);
        else if (radioNone != null && defaultCat == 2) radioNone.setChecked(true);

        if (radioGroupDefaultCategory != null) {
            radioGroupDefaultCategory.setOnCheckedChangeListener((group, checkedId) -> {
                int value = 0;
                if (checkedId == R.id.radioFirst) value = 0;
                else if (checkedId == R.id.radioOther) value = 1;
                else if (checkedId == R.id.radioNone) value = 2;
                prefs.edit().putInt(KEY_DEFAULT_CATEGORY, value).apply();
            });
        }
    }
}
