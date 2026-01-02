package com.example.simplestbook;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class CategoryAdapter extends ArrayAdapter<Category> {
    private String selectedCategoryName = "";

    public CategoryAdapter(Context context, List<Category> categories) {
        super(context, 0, categories);
    }

    public void setSelectedCategory(String name) {
        this.selectedCategoryName = name;
        notifyDataSetChanged();
    }

    public String getSelectedCategoryName() {
        return selectedCategoryName;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        Category category = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_category, parent, false);
        }

        MaterialCardView card = (MaterialCardView) convertView;
        TextView nameText = convertView.findViewById(R.id.categoryText);
        
        if (category != null) {
            nameText.setText(category.getName());
        }

        // 移除所有 Material You 動態取色，改用固定顏色
        int colorPrimary = Color.parseColor("#6750A4");
        int colorSecondaryContainer = Color.parseColor("#E8DEF8");
        int colorOnSecondaryContainer = Color.parseColor("#1D192B");
        int colorOutline = Color.parseColor("#79747E");
        int colorSurface = Color.parseColor("#FFFBFE");
        int colorOnSurface = Color.parseColor("#1C1B1F");

        int strokeWidth2dp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getContext().getResources().getDisplayMetrics());
        int strokeWidth1dp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getContext().getResources().getDisplayMetrics());

        if (category != null && category.getName().equals(selectedCategoryName)) {
            card.setStrokeColor(colorPrimary);
            card.setStrokeWidth(strokeWidth2dp);
            card.setCardBackgroundColor(colorSecondaryContainer);
            nameText.setTextColor(colorOnSecondaryContainer);
        } else {
            card.setStrokeColor(colorOutline);
            card.setStrokeWidth(strokeWidth1dp);
            card.setCardBackgroundColor(colorSurface);
            nameText.setTextColor(colorOnSurface);
        }

        return convertView;
    }
}