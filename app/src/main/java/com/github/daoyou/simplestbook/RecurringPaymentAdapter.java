package com.github.daoyou.simplestbook;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class RecurringPaymentAdapter extends ArrayAdapter<RecurringPayment> {
    public RecurringPaymentAdapter(Context context, List<RecurringPayment> items) {
        super(context, 0, items);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RecurringPayment item = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_recurring_payment, parent, false);
        }

        TextView title = convertView.findViewById(R.id.recurringTitle);
        TextView detail = convertView.findViewById(R.id.recurringDetail);

        if (item != null) {
            title.setText("$ " + item.getAmount() + " - " + item.getCategory());
            detail.setText(formatSchedule(item) + " · " + item.getNote());
        }

        return convertView;
    }

    private String formatSchedule(RecurringPayment item) {
        String freq = item.getFrequency();
        if (RecurringPayment.FREQ_WEEKLY.equals(freq)) {
            String[] weekdays = {"", "週一", "週二", "週三", "週四", "週五", "週六", "週日"};
            int day = item.getDayOfWeek();
            return "每週 " + (day >= 1 && day <= 7 ? weekdays[day] : "未設定");
        }
        if (RecurringPayment.FREQ_DAILY.equals(freq)) {
            return "每日";
        }
        if (RecurringPayment.FREQ_MINUTE.equals(freq)) {
            return "每分鐘";
        }
        if (RecurringPayment.FREQ_MONTHLY.equals(freq)) {
            return "每月 " + item.getDayOfMonth() + " 日";
        }
        if (RecurringPayment.FREQ_YEARLY.equals(freq)) {
            return "每年 " + item.getMonth() + " 月 " + item.getDayOfMonth() + " 日";
        }
        return "未設定";
    }
}
