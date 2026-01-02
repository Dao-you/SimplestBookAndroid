package com.example.simplestbook;

public class Record {
    private String id;
    private int amount;
    private String category;
    private String note;
    private long timestamp;
    private double latitude;
    private double longitude;

    public Record(String id, int amount, String category, String note, long timestamp, double latitude, double longitude) {
        this.id = id;
        this.amount = amount;
        this.category = category;
        this.note = note;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() { return id; }
    public int getAmount() { return amount; }
    public String getCategory() { return category; }
    public String getNote() { return note; }
    public long getTimestamp() { return timestamp; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}