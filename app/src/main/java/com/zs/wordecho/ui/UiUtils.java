package com.zs.wordecho.ui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
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

    public static int themeColor(Context context, int attr, int fallbackColorRes) {
        return MaterialColors.getColor(context, attr, color(context, fallbackColorRes));
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
        int bgAttr;
        int fgAttr;
        int bgFallback;
        int fgFallback;
        String label;
        if (mask == 3) {
            bgAttr = com.google.android.material.R.attr.colorTertiaryContainer;
            fgAttr = com.google.android.material.R.attr.colorOnTertiaryContainer;
            bgFallback = R.color.level_both_bg;
            fgFallback = R.color.level_both_fg;
            label = "四六级";
        } else if (mask == 2) {
            bgAttr = com.google.android.material.R.attr.colorSecondaryContainer;
            fgAttr = com.google.android.material.R.attr.colorOnSecondaryContainer;
            bgFallback = R.color.level_six_bg;
            fgFallback = R.color.level_six_fg;
            label = "CET-6";
        } else {
            bgAttr = com.google.android.material.R.attr.colorPrimaryContainer;
            fgAttr = com.google.android.material.R.attr.colorOnPrimaryContainer;
            bgFallback = R.color.level_four_bg;
            fgFallback = R.color.level_four_fg;
            label = "CET-4";
        }
        view.setBackgroundResource(R.drawable.bg_level_four);
        view.setBackgroundTintList(ColorStateList.valueOf(
                themeColor(view.getContext(), bgAttr, bgFallback)));
        view.setTextColor(themeColor(view.getContext(), fgAttr, fgFallback));
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
