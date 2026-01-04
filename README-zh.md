# SimplestBookAndroid（繁體中文）

Simplest Book Android 是一款主打「快速、極簡」的記帳 App，以最少步驟完成記錄，同時提供週期性付款、位置備註、統計圖表與雲端備份等功能。

## 功能特色

- **極速記帳**：輸入金額與備註、選類別即可完成儲存。
- **自動分類（選用）**：可先以「其他」存檔，背景由 AI 自動回填類別。
- **歷史與編輯**：查詢清單、編輯金額/備註/時間/地點備註。
- **統計圖表**：依類別彙總金額、筆數與平均。
- **週期性付款**：每日/每週/每月/每年與每分鐘排程通知。
- **定位支援**：可選擇記錄座標，並在地圖頁顯示與補充備註。
- **CSV 匯入匯出**：支援分享、匯出與匯入資料。
- **雲端備份**：Firestore 同步與還原，狀態圖示提示。
- **離開提示**：離開 App 顯示總額小視窗。

## 簡化整體架構

App 以 SQLite 為核心資料庫，搭配背景服務完成同步與自動化。

```
UI (Activities)
  - MainActivity：輸入、類別選擇、儲存
  - HistoryActivity：列表 + CSV 匯入/匯出
  - EditRecordActivity / FullMapActivity：編輯 + 地點備註
  - ChartActivity：統計彙整
  - SettingsActivity：偏好設定 + 雲端登入
  - RecurringPaymentActivity/Edit：週期性付款管理

Local Data
  - DatabaseHelper (SQLite)
  - Models：Record, Category, RecurringPayment
  - Adapters：CategoryAdapter, RecordAdapter, RecurringPaymentAdapter

Background + Services
  - AutoCategoryService + AutoCategoryClient（AI 自動分類）
  - RecurringPaymentWorker / RecurringPaymentAlarmReceiver
  - CloudBackupManager + CloudBackupIndicator
  - StatusChipService
```

## 主要流程（簡述）

1. **新增記帳**
   - MainActivity 驗證輸入 → 取得類別/座標 → 寫入 SQLite。
   - 若啟用自動分類，AutoCategoryService 背景更新類別。
   - 若啟用雲端備份，觸發同步。

2. **週期性付款**
   - RecurringPaymentActivity 管理項目並寫入 SQLite。
   - WorkManager 處理日/週/月/年；AlarmManager 處理每分鐘。

3. **雲端備份**
   - SettingsActivity 開啟備份並完成 Google 登入。
   - CloudBackupManager 上傳/驗證或從雲端還原資料。

## 專案結構

- `app/src/main/java/com/github/daoyou/simplestbook/`：主要程式碼
- `app/src/main/res/`：版面、圖示、字串等資源
- `app/src/test/`：本機單元測試
- `app/src/androidTest/`：儀器測試

## 編譯與測試

請在專案根目錄使用 Gradle wrapper：

```
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

## 備註

- 雲端備份與自動分類需設定 API Key/登入帳號。
- 定位與通知可在設定頁啟用或關閉。
