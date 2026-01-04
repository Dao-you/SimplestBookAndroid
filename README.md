# SimplestBookAndroid

Simplest Book Android is a minimal, fast bookkeeping app that prioritizes quick entry and low friction while still supporting recurring payments, location notes, charts, and optional cloud backup.

## Features

- **Ultra-fast entry**: Enter amount + note, pick a category, and save in one screen.
- **Auto category (optional)**: If enabled, records can be saved with a placeholder category and re-labeled by an AI service in the background.
- **History + editing**: Browse records, edit details, and update time/location notes.
- **Charts**: Category aggregation for totals, counts, and averages.
- **Recurring payments**: Daily/weekly/monthly/yearly and minute-based schedules with notifications.
- **Location support**: Optional location capture per record and a full map view for details.
- **CSV import/export**: Share, export, and import records.
- **Cloud backup**: Optional Firestore sync/restore with status indicators.
- **Status chip**: A brief overlay shows total spending when leaving the app.

## Simplified Architecture

The app is a single Android app module with an SQLite database at the core and optional background services for sync and automation.

```
UI (Activities)
  - MainActivity: entry, category selection, save
  - HistoryActivity: list + CSV import/export
  - EditRecordActivity / FullMapActivity: edit + location notes
  - ChartActivity: category aggregates
  - SettingsActivity: preferences + cloud sign-in
  - RecurringPaymentActivity/Edit: manage recurring items

Local Data
  - DatabaseHelper (SQLite)
  - Models: Record, Category, RecurringPayment
  - Adapters: CategoryAdapter, RecordAdapter, RecurringPaymentAdapter

Background + Services
  - AutoCategoryService + AutoCategoryClient (AI category)
  - RecurringPaymentWorker / RecurringPaymentAlarmReceiver
  - CloudBackupManager + CloudBackupIndicator
  - StatusChipService
```

## Key Flows (Short)

1. **Save a record**
   - MainActivity validates input, collects category, optionally uses location, and writes to SQLite.
   - If auto-category is enabled, AutoCategoryService updates the category later.
   - Cloud backup is triggered when enabled.

2. **Recurring payment**
   - RecurringPaymentActivity manages items saved in SQLite.
   - WorkManager handles daily/weekly/monthly/yearly items.
   - AlarmManager handles minute-level items.

3. **Cloud backup**
   - SettingsActivity enables backup and Google sign-in.
   - CloudBackupManager syncs to Firestore and can restore from the latest backup.

## Project Structure

- `app/src/main/java/com/github/daoyou/simplestbook/`: main app code
- `app/src/main/res/`: layouts, drawables, and values
- `app/src/test/`: local JVM tests
- `app/src/androidTest/`: instrumentation tests

## Build and Test

Use the Gradle wrapper from the repo root:

```
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

## Notes

- Cloud backup and auto-category require user-provided keys and sign-in.
- Location and notifications are optional and controlled in Settings.
