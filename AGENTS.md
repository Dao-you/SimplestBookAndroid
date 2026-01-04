# Repository Guidelines

## Project Structure & Module Organization
- `app/src/main/java/com/github/daoyou/simplestbook/`: primary Android app code (Activities, adapters, data helpers).
- `app/src/main/res/`: layouts (`layout/`), menus (`menu/`), drawables (`drawable/`), values (`values/`), and other assets.
- `app/src/test/`: local JVM tests (JUnit).
- `app/src/androidTest/`: instrumentation tests (AndroidJUnitRunner/Espresso).
- `gradle/` and `build.gradle.kts`: Gradle config and version catalog.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repo root.
- `.\gradlew assembleDebug`: build a debug APK.
- `.\gradlew assembleRelease`: build a release APK (uses `proguard-rules.pro`).
- `.\gradlew test`: run local unit tests.
- `.\gradlew connectedAndroidTest`: run instrumentation tests on a device/emulator.
- `.\gradlew lint`: run Android lint checks.

## Coding Style & Naming Conventions
- Java 11 source/target; 4-space indentation; braces on same line.
- Package naming follows `com.github.daoyou.simplestbook`.
- Activities end with `Activity` (e.g., `MainActivity.java`); adapters end with `Adapter`.
- Resource names use lowercase snake_case (e.g., `activity_main.xml`, `ic_cloud.xml`).
- Keep UI strings in `app/src/main/res/values/strings.xml`.

## Testing Guidelines
- Unit tests use JUnit4 (`app/src/test`).
- Instrumentation/UI tests use AndroidX test + Espresso (`app/src/androidTest`).
- Test classes follow `*Test` naming (see `ExampleUnitTest.java`).

## Commit & Pull Request Guidelines
- Recent commits use short prefixes like `feat:` and status tags like `[in progress]`.
- Prefer concise, imperative messages (e.g., `feat: add recurring payments`).
- PRs should describe user-facing changes, link relevant issues, and include screenshots for UI updates.

## Configuration & Security Tips
- `local.properties` is used by the secrets plugin; keep machine-specific values local.
- Google services and Firebase dependencies are configured in `app/build.gradle.kts`; update cautiously.

## Agent Notes
- Avoid editing generated build output in `app/build/` or `build/`.
- Keep changes scoped to the `app/` module unless explicitly needed.

## Java Program Flow (Detailed)
### Application Entry
- `app/src/main/java/com/github/daoyou/simplestbook/SimplestBookApplication.java`: app process entry; `onCreate()` applies dynamic Material color to all activities.

### Core Screens
- `app/src/main/java/com/github/daoyou/simplestbook/MainActivity.java`: main input screen. `onCreate()` binds UI, loads categories, configures toolbar, requests location and notification permissions, schedules recurring tasks. `saveRecord()` validates input, fetches location if enabled, then `performDatabaseSave()` writes to DB in a background thread and triggers cloud sync. `completeSave()` clears inputs and optionally opens history.
- `app/src/main/java/com/github/daoyou/simplestbook/HistoryActivity.java`: history list. `loadRecords()` reads DB on background thread, computes total, updates list adapter. Menu actions provide CSV export/share/import and “clear all” confirmation. Item click opens `EditRecordActivity` with record details.
- `app/src/main/java/com/github/daoyou/simplestbook/EditRecordActivity.java`: edit/delete record. Loads record data from intent, renders map preview if location exists, and allows date/time editing. `handlePrimaryAction()` updates if edited; otherwise prompts delete. Updates DB and triggers cloud sync.
- `app/src/main/java/com/github/daoyou/simplestbook/ChartActivity.java`: aggregates records by category on background thread, then renders bar items with totals, counts, and averages.
- `app/src/main/java/com/github/daoyou/simplestbook/FullMapActivity.java`: full map view for a record. Shows marker and allows editing “location note” via dialog; saves it back to DB and syncs.
- `app/src/main/java/com/github/daoyou/simplestbook/SettingsActivity.java`: settings and cloud sign-in. Toggles preferences (location, theme, auto history, recurring notifications). Handles Google sign-in, enables cloud backup, runs manual backup/restore, and inserts debug data.
- `app/src/main/java/com/github/daoyou/simplestbook/StatusChipService.java`: overlay service that shows a non-interactive status chip with total amount for ~5 seconds when exiting the app.
- `app/src/main/java/com/github/daoyou/simplestbook/AutoCategoryService.java`: background service that auto-selects category via AI, updates the record, syncs cloud backup, and broadcasts updates.

### Category Management
- `app/src/main/java/com/github/daoyou/simplestbook/ManageCategoriesActivity.java`: add/reorder/delete categories. Loads from DB, uses RecyclerView with drag-and-drop; on drag end updates sort order in DB.
- `app/src/main/java/com/github/daoyou/simplestbook/Category.java`: category model (id, name).
- `app/src/main/java/com/github/daoyou/simplestbook/CategoryAdapter.java`: grid adapter; highlights selected category and updates styles.

### Record Display
- `app/src/main/java/com/github/daoyou/simplestbook/Record.java`: record model (amount, note, category, timestamp, location).
- `app/src/main/java/com/github/daoyou/simplestbook/RecordAdapter.java`: list adapter; formats time into “剛剛/今天/昨天/一週內” styles.

### CSV Import/Export
- `app/src/main/java/com/github/daoyou/simplestbook/CsvHelper.java`: builds CSV with geo text, writes to file/URI, shares via FileProvider. `importCsv()` parses rows, converts address to lat/lng when possible, then returns `Record` list for DB insert.

### Cloud Backup
- `app/src/main/java/com/github/daoyou/simplestbook/CloudBackupIndicator.java`: listens for preference changes and updates the toolbar cloud icon; shows status snackbar and links to settings.
- `app/src/main/java/com/github/daoyou/simplestbook/CloudBackupManager.java`: sync/restore engine. `syncNow()` reads DB, uploads to Firestore, and verifies remote counts. `restoreFromCloud()` downloads and restores records/categories/recurring, then re-schedules recurring tasks.

### Recurring Payments
- `app/src/main/java/com/github/daoyou/simplestbook/RecurringPayment.java`: recurring payment model and frequency constants.
- `app/src/main/java/com/github/daoyou/simplestbook/RecurringPaymentActivity.java`: list screen; loads all recurring items; opens edit screen; can delete all.
- `app/src/main/java/com/github/daoyou/simplestbook/RecurringPaymentEditActivity.java`: create/edit recurring payment. Validates inputs, computes frequency parameters, saves to DB, triggers cloud sync, and schedules WorkManager/Alarm.
- `app/src/main/java/com/github/daoyou/simplestbook/RecurringPaymentWorker.java`: daily WorkManager task; checks due items, inserts records, updates last run date, sends notification.
- `app/src/main/java/com/github/daoyou/simplestbook/RecurringPaymentAlarmReceiver.java`: minute-level AlarmManager flow; on receive inserts record, sends notification, and schedules next minute.

### Database Layer
- `app/src/main/java/com/github/daoyou/simplestbook/DatabaseHelper.java`: SQLite schema and CRUD for records, categories, recurring payments; seeds default categories on first run.
- `DatabaseHelper.getTotalAmount()`: computes the total sum of recorded amounts for exit status display.

### AI Auto Category
- `app/src/main/java/com/github/daoyou/simplestbook/AutoCategoryClient.java`: calls AI API (OpenAI/GitHub Models) with fallback model detection and stores the preferred model per API URL.
- `MainActivity.saveRecord()`: when default category is auto and none is selected, saves as "其他" first and starts `AutoCategoryService`.
- `HistoryActivity`: listens for auto-category broadcasts, reloads records, and animates the updated row.

### Test Templates
- `app/src/test/java/com/example/simplestbook/ExampleUnitTest.java`: sample local JUnit test.
- `app/src/androidTest/java/com/example/simplestbook/ExampleInstrumentedTest.java`: sample instrumentation test.
