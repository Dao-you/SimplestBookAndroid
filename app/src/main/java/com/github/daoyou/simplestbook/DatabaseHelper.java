package com.github.daoyou.simplestbook;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.UUID;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "SimplestBook.db";
    private static final int DATABASE_VERSION = 9; // 增加版本號以加入週期性付款欄位

    public static final String TABLE_RECORDS = "records";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_AMOUNT = "amount";
    public static final String COLUMN_CATEGORY = "category";
    public static final String COLUMN_NOTE = "note";
    public static final String COLUMN_LOCATION_NAME = "location_name";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_LATITUDE = "latitude";
    public static final String COLUMN_LONGITUDE = "longitude";

    public static final String TABLE_RECURRING = "recurring_payments";
    public static final String COLUMN_RECURRING_ID = "id";
    public static final String COLUMN_RECURRING_AMOUNT = "amount";
    public static final String COLUMN_RECURRING_CATEGORY = "category";
    public static final String COLUMN_RECURRING_NOTE = "note";
    public static final String COLUMN_RECURRING_FREQUENCY = "frequency";
    public static final String COLUMN_RECURRING_DAY_OF_WEEK = "day_of_week";
    public static final String COLUMN_RECURRING_DAY_OF_MONTH = "day_of_month";
    public static final String COLUMN_RECURRING_MONTH = "month";
    public static final String COLUMN_RECURRING_LAST_RUN_DATE = "last_run_date";

    public static final String TABLE_CATEGORIES = "categories";
    public static final String COLUMN_CAT_ID = "id";
    public static final String COLUMN_CAT_NAME = "name";
    public static final String COLUMN_CAT_ORDER = "sort_order";

    private static final String TABLE_RECORDS_CREATE =
            "CREATE TABLE " + TABLE_RECORDS + " (" +
                    COLUMN_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_AMOUNT + " INTEGER, " +
                    COLUMN_CATEGORY + " TEXT, " +
                    COLUMN_NOTE + " TEXT, " +
                    COLUMN_TIMESTAMP + " INTEGER, " +
                    COLUMN_LATITUDE + " REAL, " +
                    COLUMN_LONGITUDE + " REAL, " +
                    COLUMN_LOCATION_NAME + " TEXT" +
                    ");";

    private static final String TABLE_CATEGORIES_CREATE =
            "CREATE TABLE " + TABLE_CATEGORIES + " (" +
                    COLUMN_CAT_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_CAT_NAME + " TEXT, " +
                    COLUMN_CAT_ORDER + " INTEGER" +
                    ");";

    private static final String TABLE_RECURRING_CREATE =
            "CREATE TABLE " + TABLE_RECURRING + " (" +
                    COLUMN_RECURRING_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_RECURRING_AMOUNT + " INTEGER, " +
                    COLUMN_RECURRING_CATEGORY + " TEXT, " +
                    COLUMN_RECURRING_NOTE + " TEXT, " +
                    COLUMN_RECURRING_FREQUENCY + " TEXT, " +
                    COLUMN_RECURRING_DAY_OF_WEEK + " INTEGER, " +
                    COLUMN_RECURRING_DAY_OF_MONTH + " INTEGER, " +
                    COLUMN_RECURRING_MONTH + " INTEGER, " +
                    COLUMN_RECURRING_LAST_RUN_DATE + " INTEGER" +
                    ");";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_RECORDS_CREATE);
        db.execSQL(TABLE_CATEGORIES_CREATE);
        db.execSQL(TABLE_RECURRING_CREATE);
        seedCategories(db);
    }

    private void seedCategories(SQLiteDatabase db) {
        String[] defaultCategories = {"餐飲", "交通", "購物", "娛樂", "醫療"};
        for (int i = 0; i < defaultCategories.length; i++) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_CAT_ID, UUID.randomUUID().toString());
            values.put(COLUMN_CAT_NAME, defaultCategories[i]);
            values.put(COLUMN_CAT_ORDER, i);
            db.insert(TABLE_CATEGORIES, null, values);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECORDS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATEGORIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECURRING);
        onCreate(db);
    }

    public void insertRecord(int amount, String category, String note, double latitude, double longitude) {
        insertRecord(amount, category, note, "", System.currentTimeMillis(), latitude, longitude);
    }

    public void insertRecord(int amount, String category, String note, String locationName, long timestamp, double latitude, double longitude) {
        insertRecordAndReturnId(amount, category, note, locationName, timestamp, latitude, longitude);
    }

    public String insertRecordAndReturnId(int amount, String category, String note, String locationName,
                                          long timestamp, double latitude, double longitude) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        String recordId = UUID.randomUUID().toString();
        values.put(COLUMN_ID, recordId);
        values.put(COLUMN_AMOUNT, amount);
        values.put(COLUMN_CATEGORY, category);
        values.put(COLUMN_NOTE, note);
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_LATITUDE, latitude);
        values.put(COLUMN_LONGITUDE, longitude);
        values.put(COLUMN_LOCATION_NAME, locationName);
        db.insert(TABLE_RECORDS, null, values);
        db.close();
        return recordId;
    }

    public void updateRecord(String id, int amount, String category, String note, String locationName, long timestamp, double latitude, double longitude) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_AMOUNT, amount);
        values.put(COLUMN_CATEGORY, category);
        values.put(COLUMN_NOTE, note);
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_LATITUDE, latitude);
        values.put(COLUMN_LONGITUDE, longitude);
        values.put(COLUMN_LOCATION_NAME, locationName);
        db.update(TABLE_RECORDS, values, COLUMN_ID + " = ?", new String[]{id});
        db.close();
    }

    public void updateRecordCategory(String id, String category) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_CATEGORY, category);
        db.update(TABLE_RECORDS, values, COLUMN_ID + " = ?", new String[]{id});
        db.close();
    }

    public void updateLocationName(String id, String locationName) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_LOCATION_NAME, locationName);
        db.update(TABLE_RECORDS, values, COLUMN_ID + " = ?", new String[]{id});
        db.close();
    }

    public void deleteRecord(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RECORDS, COLUMN_ID + " = ?", new String[]{id});
        db.close();
    }

    public void deleteAllRecords() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RECORDS, null, null);
        db.close();
    }

    public Cursor getAllRecords() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_RECORDS, null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");
    }

    public int getTotalAmount() {
        SQLiteDatabase db = this.getReadableDatabase();
        int total = 0;
        Cursor cursor = db.rawQuery("SELECT SUM(" + COLUMN_AMOUNT + ") FROM " + TABLE_RECORDS, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                total = cursor.isNull(0) ? 0 : cursor.getInt(0);
            }
            cursor.close();
        }
        db.close();
        return total;
    }

    public String getLocationNameById(String id) {
        SQLiteDatabase db = this.getReadableDatabase();
        String locationName = null;
        Cursor cursor = db.query(TABLE_RECORDS, new String[]{COLUMN_LOCATION_NAME}, COLUMN_ID + " = ?",
                new String[]{id}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                locationName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOCATION_NAME));
            }
            cursor.close();
        }
        db.close();
        return locationName;
    }

    public void insertCategory(String name, int order) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_CAT_ID, UUID.randomUUID().toString());
        values.put(COLUMN_CAT_NAME, name);
        values.put(COLUMN_CAT_ORDER, order);
        db.insert(TABLE_CATEGORIES, null, values);
        db.close();
    }

    public void updateCategoryOrder(String id, int order) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_CAT_ORDER, order);
        db.update(TABLE_CATEGORIES, values, COLUMN_CAT_ID + " = ?", new String[]{id});
        db.close();
    }

    public void deleteCategory(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_CATEGORIES, COLUMN_CAT_ID + " = ?", new String[]{id});
        db.close();
    }

    public void deleteAllCategories() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_CATEGORIES, null, null);
        db.close();
    }

    public void insertCategoryWithId(String id, String name, int order) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_CAT_ID, id);
        values.put(COLUMN_CAT_NAME, name);
        values.put(COLUMN_CAT_ORDER, order);
        db.insert(TABLE_CATEGORIES, null, values);
        db.close();
    }

    public Cursor getAllCategories() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_CATEGORIES, null, null, null, null, null, COLUMN_CAT_ORDER + " ASC");
    }

    public void insertRecurringPayment(String id, int amount, String category, String note, String frequency,
                                        int dayOfWeek, int dayOfMonth, int month) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_RECURRING_ID, id);
        values.put(COLUMN_RECURRING_AMOUNT, amount);
        values.put(COLUMN_RECURRING_CATEGORY, category);
        values.put(COLUMN_RECURRING_NOTE, note);
        values.put(COLUMN_RECURRING_FREQUENCY, frequency);
        values.put(COLUMN_RECURRING_DAY_OF_WEEK, dayOfWeek);
        values.put(COLUMN_RECURRING_DAY_OF_MONTH, dayOfMonth);
        values.put(COLUMN_RECURRING_MONTH, month);
        values.put(COLUMN_RECURRING_LAST_RUN_DATE, 0);
        db.insertWithOnConflict(TABLE_RECURRING, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public Cursor getAllRecurringPayments() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_RECURRING, null, null, null, null, null, COLUMN_RECURRING_NOTE + " ASC");
    }

    public Cursor getRecurringPaymentById(String id) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_RECURRING, null, COLUMN_RECURRING_ID + " = ?",
                new String[]{id}, null, null, null);
    }

    public void updateRecurringPayment(String id, int amount, String category, String note, String frequency,
                                        int dayOfWeek, int dayOfMonth, int month) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_RECURRING_AMOUNT, amount);
        values.put(COLUMN_RECURRING_CATEGORY, category);
        values.put(COLUMN_RECURRING_NOTE, note);
        values.put(COLUMN_RECURRING_FREQUENCY, frequency);
        values.put(COLUMN_RECURRING_DAY_OF_WEEK, dayOfWeek);
        values.put(COLUMN_RECURRING_DAY_OF_MONTH, dayOfMonth);
        values.put(COLUMN_RECURRING_MONTH, month);
        db.update(TABLE_RECURRING, values, COLUMN_RECURRING_ID + " = ?", new String[]{id});
        db.close();
    }

    public void deleteRecurringPayment(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RECURRING, COLUMN_RECURRING_ID + " = ?", new String[]{id});
        db.close();
    }

    public void deleteAllRecurringPayments() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RECURRING, null, null);
        db.close();
    }

    public void updateRecurringLastRunDate(String id, int lastRunDate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_RECURRING_LAST_RUN_DATE, lastRunDate);
        db.update(TABLE_RECURRING, values, COLUMN_RECURRING_ID + " = ?", new String[]{id});
        db.close();
    }
}
