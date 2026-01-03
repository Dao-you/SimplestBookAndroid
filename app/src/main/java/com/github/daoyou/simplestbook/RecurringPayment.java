package com.github.daoyou.simplestbook;

public class RecurringPayment {
    public static final String FREQ_WEEKLY = "weekly";
    public static final String FREQ_MONTHLY = "monthly";
    public static final String FREQ_YEARLY = "yearly";

    private final String id;
    private final int amount;
    private final String category;
    private final String note;
    private final String frequency;
    private final int dayOfWeek;
    private final int dayOfMonth;
    private final int month;
    private final int lastRunDate;

    public RecurringPayment(String id, int amount, String category, String note, String frequency,
                            int dayOfWeek, int dayOfMonth, int month, int lastRunDate) {
        this.id = id;
        this.amount = amount;
        this.category = category;
        this.note = note;
        this.frequency = frequency;
        this.dayOfWeek = dayOfWeek;
        this.dayOfMonth = dayOfMonth;
        this.month = month;
        this.lastRunDate = lastRunDate;
    }

    public String getId() { return id; }
    public int getAmount() { return amount; }
    public String getCategory() { return category; }
    public String getNote() { return note; }
    public String getFrequency() { return frequency; }
    public int getDayOfWeek() { return dayOfWeek; }
    public int getDayOfMonth() { return dayOfMonth; }
    public int getMonth() { return month; }
    public int getLastRunDate() { return lastRunDate; }
}
