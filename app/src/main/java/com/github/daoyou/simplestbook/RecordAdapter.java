package com.github.daoyou.simplestbook;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordAdapter extends ArrayAdapter<Record> {
    public RecordAdapter(Context context, List<Record> records) {
        super(context, 0, records);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Record record = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_record, parent, false);
        }

        TextView itemNote = convertView.findViewById(R.id.itemNote);
        TextView itemCategory = convertView.findViewById(R.id.itemCategory);
        TextView itemAmount = convertView.findViewById(R.id.itemAmount);

        itemNote.setText(record.getNote());
        
        // 格式化顯示：類別 - 時間
        String timeDisplay = formatTime(record.getTimestamp());
        itemCategory.setText(record.getCategory() + " - " + timeDisplay);
        
        itemAmount.setText("$ " + record.getAmount());

        convertView.setOnClickListener(v -> {
            if (parent instanceof ListView) {
                ((ListView) parent).performItemClick(v, position, getItemId(position));
            }
        });

        return convertView;
    }

    private String formatTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        // 1. 半小時內 (1800000 毫秒)
        if (diff < 1800000) {
            return "剛剛";
        }

        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestamp);
        Calendar today = Calendar.getInstance();
        
        // 判斷是否為今天 (24小時內且日期相同)
        boolean isSameDay = target.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                           target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);

        if (isSameDay) {
            // 2. 24 小時內 (今天)
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
        }

        // 判斷是否為昨天
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        boolean isYesterday = target.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                             target.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR);

        if (isYesterday) {
            // 3. 48 小時內 (昨天)
            int hour = target.get(Calendar.HOUR_OF_DAY);
            String period;
            if (hour < 5) period = "清晨";
            else if (hour < 11) period = "早上";
            else if (hour < 13) period = "中午";
            else if (hour < 18) period = "下午";
            else period = "晚上";
            return "昨天" + period;
        }

        // 判斷是否在一周內 (7天內)
        if (diff < 7 * 24 * 60 * 60 * 1000L) {
            // 4. 一周內
            String[] weekDays = {"", "周日", "周一", "周二", "周三", "周四", "周五", "周六"};
            String dayOfWeek = weekDays[target.get(Calendar.DAY_OF_WEEK)];
            int hour = target.get(Calendar.HOUR_OF_DAY);
            String period;
            if (hour < 5) period = "清晨";
            else if (hour < 11) period = "早上";
            else if (hour < 13) period = "中午";
            else if (hour < 18) period = "下午";
            else period = "晚上";
            return dayOfWeek + period;
        }

        // 5. 超過一周
        return new SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }
}