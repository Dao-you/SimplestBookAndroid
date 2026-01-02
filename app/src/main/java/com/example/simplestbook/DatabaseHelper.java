package com.example.simplestbook;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.UUID;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "SimplestBook.db";
    private static final int DATABASE_VERSION = 6;

    public static final String TABLE_RECORDS = "records";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_AMOUNT = "amount";
    public static final String COLUMN_CATEGORY = "category";
    public static final String COLUMN_NOTE = "note";
    public static final String COLUMN_TIMESTAMP = "timestamp";

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
                    COLUMN_TIMESTAMP + " INTEGER" +
                    ");";

    private static final String TABLE_CATEGORIES_CREATE =
            "CREATE TABLE " + TABLE_CATEGORIES + " (" +
                    COLUMN_CAT_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_CAT_NAME + " TEXT, " +
                    COLUMN_CAT_ORDER + " INTEGER" +
                    ");";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_RECORDS_CREATE);
        db.execSQL(TABLE_CATEGORIES_CREATE);
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
        onCreate(db);
    }

    public void insertRecord(int amount, String category, String note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, UUID.randomUUID().toString());
        values.put(COLUMN_AMOUNT, amount);
        values.put(COLUMN_CATEGORY, category);
        values.put(COLUMN_NOTE, note);
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        db.insert(TABLE_RECORDS, null, values);
        db.close();
    }

    public void updateRecord(String id, int amount, String category, String note, long timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_AMOUNT, amount);
        values.put(COLUMN_CATEGORY, category);
        values.put(COLUMN_NOTE, note);
        values.put(COLUMN_TIMESTAMP, timestamp);
        db.update(TABLE_RECORDS, values, COLUMN_ID + " = ?", new String[]{id});
        db.close();
    }

    public void deleteRecord(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RECORDS, COLUMN_ID + " = ?", new String[]{id});
        db.close();
    }

    public Cursor getAllRecords() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_RECORDS, null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");
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

    public Cursor getAllCategories() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_CATEGORIES, null, null, null, null, null, COLUMN_CAT_ORDER + " ASC");
    }
}