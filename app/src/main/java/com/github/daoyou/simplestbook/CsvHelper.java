package com.github.daoyou.simplestbook;

import android.app.Activity;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CsvHelper {

    private final Activity activity;

    public CsvHelper(Activity activity) {
        this.activity = activity;
    }

    /**
     * 產生 CSV 字串內容
     */
    public String generateCsvContent(List<Record> recordList) {
        StringBuilder csvContent = new StringBuilder();
        // 標題列: time,desc,cate,amount,location,location_name,id
        csvContent.append("time,desc,cate,amount,location,location_name,id\n");

        Geocoder geocoder = new Geocoder(activity, Locale.TRADITIONAL_CHINESE);

        for (Record record : recordList) {
            String addressStr = "Unknown";
            if (record.getLatitude() != 0 || record.getLongitude() != 0) {
                try {
                    List<Address> addresses = geocoder.getFromLocation(record.getLatitude(), record.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address addr = addresses.get(0);
                        String city = addr.getAdminArea() != null ? addr.getAdminArea() : "";
                        String district = addr.getSubAdminArea() != null ? addr.getSubAdminArea() : (addr.getLocality() != null ? addr.getLocality() : "");
                        String village = addr.getSubLocality() != null ? addr.getSubLocality() : "";
                        addressStr = city + district + village;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // 確保內容不包含逗號導致格式錯誤
            String note = record.getNote() != null ? record.getNote().replace(",", " ") : "";
            String category = record.getCategory() != null ? record.getCategory() : "";

            String locationName = record.getLocationName() != null ? record.getLocationName().replace(",", " ") : "";

            csvContent.append(String.format("%d,%s,%s,%d,%s,%s,%s\n",
                    record.getTimestamp(),
                    note,
                    category,
                    record.getAmount(),
                    addressStr,
                    locationName,
                    record.getId() == null ? "" : record.getId()));
        }
        return csvContent.toString();
    }

    /**
     * 從 CSV 檔案讀取紀錄 (匯入)
     */
    public List<Record> importCsv(Uri uri) {
        List<Record> importedRecords = new ArrayList<>();
        Geocoder geocoder = new Geocoder(activity, Locale.TRADITIONAL_CHINESE);

        try (InputStream inputStream = activity.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                // 處理 UTF-8 BOM
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 4) { // 現在至少要有 4 個欄位 (time, desc, cate, amount)
                    long timestamp;
                    try {
                        timestamp = Long.parseLong(parts[0].trim());
                    } catch (NumberFormatException e) {
                        timestamp = System.currentTimeMillis();
                    }

                    String desc = parts[1].trim();
                    String cate = parts[2].trim();
                    int amount = 0;
                    try {
                        amount = Integer.parseInt(parts[3].trim());
                    } catch (NumberFormatException ignored) {}

                    String locationStr = parts.length > 4 ? parts[4].trim() : "";
                    String locationName = parts.length > 5 ? parts[5].trim() : "";
                    String id = parts.length > 6 ? parts[6].trim() : "";
                    double lat = 0, lng = 0;

                    // 嘗試將地址轉回經緯度
                    if (!locationStr.isEmpty() && !locationStr.equalsIgnoreCase("Unknown")) {
                        try {
                            List<Address> addresses = geocoder.getFromLocationName(locationStr, 1);
                            if (addresses != null && !addresses.isEmpty()) {
                                lat = addresses.get(0).getLatitude();
                                lng = addresses.get(0).getLongitude();
                            }
                        } catch (IOException ignored) {}
                    }

                    String safeId = id.isEmpty() ? UUID.randomUUID().toString() : id;
                    importedRecords.add(new Record(
                            safeId,
                            amount,
                            cate,
                            desc,
                            locationName,
                            timestamp,
                            lat,
                            lng
                    ));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(activity, "匯入失敗", Toast.LENGTH_SHORT).show();
        }
        return importedRecords;
    }

    /**
     * 執行分享功能 (透過 Intent Chooser)
     */
    public void shareCsv(String content) {
        try {
            File exportDir = new File(activity.getCacheDir(), "exports");
            if (!exportDir.exists()) exportDir.mkdirs();
            File file = new File(exportDir, "records.csv");

            try (FileOutputStream out = new FileOutputStream(file)) {
                // 加入 UTF-8 BOM 避免 Excel 中文亂碼
                out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                out.write(content.getBytes());
            }

            Uri contentUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);

            Intent shareIntent = ShareCompat.IntentBuilder.from(activity)
                    .setType("text/csv")
                    .setStream(contentUri)
                    .setSubject("Export Records")
                    .getIntent();

            Intent chooser = Intent.createChooser(shareIntent, "Share CSV");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(chooser);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(activity, "Failed to share CSV", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 寫入檔案系統 URI
     */
    public boolean writeCsvToUri(Uri uri, String content) {
        try (OutputStream outputStream = activity.getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                outputStream.write(content.getBytes());
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
