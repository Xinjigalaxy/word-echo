package com.zs.wordecho.ui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.zs.wordecho.R;
import com.zs.wordecho.data.AppPrefs;

import java.text.NumberFormat;
import java.util.Locale;

public final class UiUtils {
    private UiUtils() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static int color(Context context, int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }

    public static String formatNumber(int value) {
        return NumberFormat.getIntegerInstance(Locale.CHINA).format(value);
    }

    public static String levelLabel(Context context, int mask) {
        if (mask == 3) {
            return context.getString(R.string.level_both);
        }
        if (mask == 2) {
            return context.getString(R.string.level_six);
        }
        return context.getString(R.string.level_four);
    }

    public static String levelChipLabel(Context context, int mask) {
        if (mask == 3) {
            return "四六级";
        }
        return mask == 2 ? "CET-6" : "CET-4";
    }

    public static String accentLabel(Context context, int accent) {
        return context.getString(accent == AppPrefs.ACCENT_UK ? R.string.accent_uk : R.string.accent_us);
    }

    public static String ttsEngineLabel(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return context.getString(R.string.system_default_engine);
        }
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return packageName;
        }
    }

    public static void applyLevelTag(TextView view, int mask) {
        int bg;
        int fg;
        String label;
        if (mask == 3) {
            bg = R.drawable.bg_level_both;
            fg = R.color.level_both_fg;
            label = "四六级";
        } else if (mask == 2) {
            bg = R.drawable.bg_level_six;
            fg = R.color.level_six_fg;
            label = "CET-6";
        } else {
            bg = R.drawable.bg_level_four;
            fg = R.color.level_four_fg;
            label = "CET-4";
        }
        view.setBackgroundResource(bg);
        view.setTextColor(ContextCompat.getColor(view.getContext(), fg));
        view.setText(label);
    }

    public static void setSelectedTint(TextView view, boolean selected) {
        view.setSelected(selected);
    }

    public static void setViewSelected(View view, boolean selected) {
        view.setSelected(selected);
        if (view.getBackground() != null) {
            view.getBackground().mutate();
        }
    }

    public static ColorStateList starTint(Context context) {
        return ContextCompat.getColorStateList(context, R.color.selector_star_tint);
    }
}
