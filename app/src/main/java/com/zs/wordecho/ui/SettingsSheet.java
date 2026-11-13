package com.zs.wordecho.ui;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.zs.wordecho.BuildConfig;
import com.zs.wordecho.R;
import com.zs.wordecho.data.AppPrefs;

public final class SettingsSheet {
    public interface Listener {
        void onAccentChanged(int accent);
        void onRateChanged(float rate);
        void onThemeChanged(int themeMode);
        void onTranslateChanged(boolean enabled);
        void onScreenOffChanged(boolean enabled);
        void onDailyGoalChanged(int goal);
        void onDynamicColorChanged(boolean enabled);
        void onOpenSystemTts();
    }

    private SettingsSheet() {
    }

    public static void show(Activity activity, AppPrefs prefs, String ttsStatus,
                            int statusColorRes, Listener listener) {
        if (activity.isFinishing()) {
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.sheet_settings, null, false);
        dialog.setContentView(view);

        ChipGroup accentGroup = view.findViewById(R.id.accent_group);
        ChipGroup repeatGroup = view.findViewById(R.id.repeat_group);
        ChipGroup themeGroup = view.findViewById(R.id.theme_group);
        Slider rateSlider = view.findViewById(R.id.rate_slider);
        TextView rateValue = view.findViewById(R.id.rate_value);
        MaterialSwitch translate = view.findViewById(R.id.translate_switch);
        MaterialSwitch spell = view.findViewById(R.id.spell_switch);
        MaterialSwitch screenOff = view.findViewById(R.id.screen_off_switch);
        MaterialSwitch dynamic = view.findViewById(R.id.dynamic_switch);
        TextView dynamicDesc = view.findViewById(R.id.dynamic_desc);
        TextView goalValue = view.findViewById(R.id.goal_value);
        MaterialButton goalMinus = view.findViewById(R.id.goal_minus);
        MaterialButton goalPlus = view.findViewById(R.id.goal_plus);
        TextView ttsStatusView = view.findViewById(R.id.settings_tts_status);
        MaterialButton systemTts = view.findViewById(R.id.btn_system_tts);
        TextView version = view.findViewById(R.id.about_version);

        boolean[] suppress = {true};
        int[] goal = {prefs.getDailyGoal()};
        boolean dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

        accentGroup.check(prefs.getAccent() == AppPrefs.ACCENT_UK ? R.id.chip_uk : R.id.chip_us);
        repeatGroup.check(prefs.getRepeatCount() >= 2 ? R.id.chip_twice : R.id.chip_once);
        int theme = prefs.getThemeMode();
        themeGroup.check(theme == AppPrefs.THEME_DARK ? R.id.chip_dark
                : theme == AppPrefs.THEME_LIGHT ? R.id.chip_light : R.id.chip_system);
        // Keep the rate slider continuous and clamp values written by older builds.
        // A stale value that does not align to a discrete step would otherwise throw
        // "Value must be equal to valueFrom plus a multiple of stepSize".
        rateSlider.setStepSize(0f);
        float initialRate = Math.max(0.6f, Math.min(1.4f, prefs.getSpeechRate()));
        rateSlider.setValue(initialRate);
        rateValue.setText(activity.getString(R.string.speech_rate_value, initialRate));
        translate.setChecked(prefs.isTranslateVoice());
        spell.setChecked(prefs.isSpellWord());
        screenOff.setChecked(prefs.isScreenOffPlayback());
        renderGoal(goal, goalValue);
        dynamic.setChecked(dynamicSupported && prefs.isDynamicColor());
        dynamic.setEnabled(dynamicSupported);
        dynamic.setAlpha(dynamicSupported ? 1f : 0.38f);
        dynamicDesc.setAlpha(dynamicSupported ? 1f : 0.55f);
        if (!dynamicSupported) {
            dynamicDesc.setText(R.string.dynamic_color_unavailable);
            if (prefs.isDynamicColor()) {
                prefs.setDynamicColor(false);
            }
        }
        ttsStatusView.setText(ttsStatus);
        ttsStatusView.setTextColor(UiUtils.color(activity, statusColorRes));
        version.setText(activity.getString(R.string.about_version, BuildConfig.VERSION_NAME));

        suppress[0] = false;

        accentGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (suppress[0] || checkedIds.isEmpty()) {
                return;
            }
            int accent = checkedIds.get(0) == R.id.chip_uk
                    ? AppPrefs.ACCENT_UK : AppPrefs.ACCENT_US;
            prefs.setAccent(accent);
            listener.onAccentChanged(accent);
        });

        repeatGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (suppress[0] || checkedIds.isEmpty()) {
                return;
            }
            prefs.setRepeatCount(checkedIds.get(0) == R.id.chip_twice ? 2 : 1);
        });

        themeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (suppress[0] || checkedIds.isEmpty()) {
                return;
            }
            int mode;
            if (checkedIds.get(0) == R.id.chip_dark) {
                mode = AppPrefs.THEME_DARK;
            } else if (checkedIds.get(0) == R.id.chip_light) {
                mode = AppPrefs.THEME_LIGHT;
            } else {
                mode = AppPrefs.THEME_SYSTEM;
            }
            prefs.setThemeMode(mode);
            dialog.dismiss();
            new Handler(Looper.getMainLooper())
                    .postDelayed(() -> listener.onThemeChanged(mode), 220L);
        });

        rateSlider.addOnChangeListener((slider, value, fromUser) -> {
            rateValue.setText(activity.getString(R.string.speech_rate_value, value));
            if (fromUser) {
                prefs.setSpeechRate(value);
                listener.onRateChanged(value);
            }
        });

        translate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppress[0]) {
                prefs.setTranslateVoice(isChecked);
                listener.onTranslateChanged(isChecked);
            }
        });

        spell.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppress[0]) {
                prefs.setSpellWord(isChecked);
            }
        });

        screenOff.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppress[0]) {
                prefs.setScreenOffPlayback(isChecked);
                listener.onScreenOffChanged(isChecked);
            }
        });

        goalMinus.setOnClickListener(v -> {
            goal[0] = Math.max(AppPrefs.MIN_DAILY_GOAL, goal[0] - 5);
            prefs.setDailyGoal(goal[0]);
            renderGoal(goal, goalValue);
            listener.onDailyGoalChanged(goal[0]);
        });
        goalPlus.setOnClickListener(v -> {
            goal[0] = Math.min(AppPrefs.MAX_DAILY_GOAL, goal[0] + 5);
            prefs.setDailyGoal(goal[0]);
            renderGoal(goal, goalValue);
            listener.onDailyGoalChanged(goal[0]);
        });

        dynamic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppress[0] && dynamicSupported) {
                prefs.setDynamicColor(isChecked);
                dialog.dismiss();
                new Handler(Looper.getMainLooper())
                        .postDelayed(() -> listener.onDynamicColorChanged(isChecked), 220L);
            }
        });

        systemTts.setOnClickListener(v -> {
            dialog.dismiss();
            listener.onOpenSystemTts();
        });
        view.findViewById(R.id.btn_done).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.settings_close).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void renderGoal(int[] goal, TextView goalValue) {
        goalValue.setText(goalValue.getContext().getString(R.string.daily_goal_value, goal[0]));
    }
}
