package com.github.daoyou.simplestbook;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class FullMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private double latitude;
    private double longitude;
    private String title;
    private String recordId;
    private String locationName;
    private android.widget.TextView locationNameView;
    private com.google.android.material.card.MaterialCardView locationCard;
    private DatabaseHelper dbHelper;
    private SharedPreferences.OnSharedPreferenceChangeListener backupIndicatorListener;
    private MenuItem cloudStatusItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_full_map);

        // 確保根佈局對齊狀態列
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.mapToolbar);
        setSupportActionBar(toolbar);
        locationNameView = findViewById(R.id.textLocationName);
        locationCard = findViewById(R.id.cardTotal);
        dbHelper = new DatabaseHelper(this);
        
        latitude = getIntent().getDoubleExtra("latitude", 0.0);
        longitude = getIntent().getDoubleExtra("longitude", 0.0);
        title = getIntent().getStringExtra("title");
        recordId = getIntent().getStringExtra("recordId");
        locationName = getIntent().getStringExtra("locationName");
        updateLocationNameDisplay(locationName);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (title != null) {
                getSupportActionBar().setTitle(title); // 使用 ActionBar 設置標題
            }
        }
        
        toolbar.setNavigationOnClickListener(v -> finish());
        locationNameView.setOnClickListener(v -> showLocationNameDialog());
        if (locationCard != null) {
            locationCard.setOnClickListener(v -> showLocationNameDialog());
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.full_map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        LatLng location = new LatLng(latitude, longitude);
        googleMap.addMarker(new MarkerOptions().position(location).title("位置"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 13f));
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setAllGesturesEnabled(true);
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

    private void showLocationNameDialog() {
        if (recordId == null) {
            return;
        }
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("新增地點備註...");
        TextInputEditText editText = new TextInputEditText(this);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        if (locationName != null) {
            editText.setText(locationName);
        }
        inputLayout.addView(editText);

        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(padding, padding, padding, padding);
        container.addView(inputLayout);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("地點備註")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("儲存", (dialogInterface, which) -> {
                    String newValue = editText.getText() == null ? "" : editText.getText().toString().trim();
                    new Thread(() -> {
                        dbHelper.updateLocationName(recordId, newValue);
                        CloudBackupManager.requestSyncIfEnabled(getApplicationContext());
                        runOnUiThread(() -> {
                            locationName = newValue;
                            updateLocationNameDisplay(locationName);
                        });
                    }).start();
                })
                .setOnDismissListener(dialogInterface -> {
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null && editText.getWindowToken() != null) {
                        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                    }
                })
                .create();
        dialog.setOnShowListener(d -> {
            editText.setFocusableInTouchMode(true);
            editText.requestFocus();
            editText.post(() -> {
                if (editText.getText() != null) {
                    editText.selectAll();
                }
            });
        });
        dialog.show();
    }

    private void updateLocationNameDisplay(String value) {
        if (locationNameView == null) {
            return;
        }
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            locationNameView.setText("新增地點備註...");
            int hintColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0);
            locationNameView.setTextColor(hintColor);
        } else {
            locationNameView.setText(text);
            int normalColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0);
            locationNameView.setTextColor(normalColor);
        }
    }
}
