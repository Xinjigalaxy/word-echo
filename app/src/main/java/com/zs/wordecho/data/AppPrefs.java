package com.zs.wordecho.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small, dependency-free preference store for the player and appearance. */
public final class AppPrefs {
    public static final int ACCENT_US = 0;
    public static final int ACCENT_UK = 1;

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int DEFAULT_DAILY_GOAL = 30;
    public static final int MIN_DAILY_GOAL = 10;
    public static final int MAX_DAILY_GOAL = 200;

    private static final String FILE = "word_echo_settings";
    private static final String KEY_LEVEL = "level_filter";
    private static final String KEY_AUTO_PLAY = "auto_play";
    private static final String KEY_INTERVAL = "interval_seconds";
    private static final String KEY_ACCENT = "accent";
    private static final String KEY_RATE = "speech_rate";
    private static final String KEY_TRANSLATE = "translate_voice";
    private static final String KEY_REPEAT = "repeat_count";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_HIDDEN = "meaning_hidden";
    private static final String KEY_DAILY_DATE = "daily_date";
    private static final String KEY_DAILY_COUNT = "daily_count";
    private static final String KEY_LAST_PLAYED = "last_played_key";
    private static final String KEY_SPELL_WORD = "spell_word";
    private static final String KEY_SCREEN_OFF = "screen_off_playback";
    private static final String KEY_DAILY_GOAL = "daily_goal";
    private static final String KEY_DYNAMIC_COLOR = "dynamic_color";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int getLevelFilter() {
        return prefs.getInt(KEY_LEVEL, 0);
    }

    public void setLevelFilter(int value) {
        prefs.edit().putInt(KEY_LEVEL, value).apply();
    }

    public boolean isAutoPlay() {
        return prefs.getBoolean(KEY_AUTO_PLAY, true);
    }

    public void setAutoPlay(boolean value) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY, value).apply();
    }

    public float getIntervalSeconds() {
        return prefs.getFloat(KEY_INTERVAL, 3f);
    }

    public void setIntervalSeconds(float value) {
        prefs.edit().putFloat(KEY_INTERVAL, value).apply();
    }

    public int getAccent() {
        return prefs.getInt(KEY_ACCENT, ACCENT_US);
    }

    public void setAccent(int value) {
        prefs.edit().putInt(KEY_ACCENT, value).apply();
    }

    public float getSpeechRate() {
        return prefs.getFloat(KEY_RATE, 0.95f);
    }

    public void setSpeechRate(float value) {
        prefs.edit().putFloat(KEY_RATE, value).apply();
    }

    public boolean isTranslateVoice() {
        return prefs.getBoolean(KEY_TRANSLATE, true);
    }

    public void setTranslateVoice(boolean value) {
        prefs.edit().putBoolean(KEY_TRANSLATE, value).apply();
    }

    public int getRepeatCount() {
        return Math.max(1, Math.min(2, prefs.getInt(KEY_REPEAT, 1)));
    }

    public void setRepeatCount(int value) {
        prefs.edit().putInt(KEY_REPEAT, Math.max(1, Math.min(2, value))).apply();
    }

    public boolean isSpellWord() {
        return prefs.getBoolean(KEY_SPELL_WORD, false);
    }

    public void setSpellWord(boolean value) {
        prefs.edit().putBoolean(KEY_SPELL_WORD, value).apply();
    }

    public boolean isScreenOffPlayback() {
        return prefs.getBoolean(KEY_SCREEN_OFF, false);
    }

    public void setScreenOffPlayback(boolean value) {
        prefs.edit().putBoolean(KEY_SCREEN_OFF, value).apply();
    }

    public boolean isDynamicColor() {
        return prefs.getBoolean(KEY_DYNAMIC_COLOR, false);
    }

    public void setDynamicColor(boolean value) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply();
    }

    public int getDailyGoal() {
        return Math.max(MIN_DAILY_GOAL, Math.min(MAX_DAILY_GOAL,
                prefs.getInt(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL)));
    }

    public void setDailyGoal(int value) {
        prefs.edit().putInt(KEY_DAILY_GOAL,
                Math.max(MIN_DAILY_GOAL, Math.min(MAX_DAILY_GOAL, value))).apply();
    }

    public int getThemeMode() {
        return prefs.getInt(KEY_THEME, THEME_SYSTEM);
    }

    public void setThemeMode(int value) {
        prefs.edit().putInt(KEY_THEME, value).apply();
    }

    public boolean isMeaningHidden() {
        return prefs.getBoolean(KEY_HIDDEN, false);
    }

    public void setMeaningHidden(boolean value) {
        prefs.edit().putBoolean(KEY_HIDDEN, value).apply();
    }

    private String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private void ensureToday() {
        String stored = prefs.getString(KEY_DAILY_DATE, "");
        if (!today().equals(stored)) {
            prefs.edit()
                    .putString(KEY_DAILY_DATE, today())
                    .putInt(KEY_DAILY_COUNT, 0)
                    .remove(KEY_LAST_PLAYED)
                    .apply();
        }
    }

    public int getDailyCount() {
        ensureToday();
        return prefs.getInt(KEY_DAILY_COUNT, 0);
    }

    /** Counts a word once per day, so replays do not inflate the daily rhythm. */
    public int recordPlayed(Word word) {
        ensureToday();
        String last = prefs.getString(KEY_LAST_PLAYED, "");
        if (word != null && !word.getKey().equals(last)) {
            int next = prefs.getInt(KEY_DAILY_COUNT, 0) + 1;
            prefs.edit()
                    .putInt(KEY_DAILY_COUNT, next)
                    .putString(KEY_LAST_PLAYED, word.getKey())
                    .apply();
            return next;
        }
        return prefs.getInt(KEY_DAILY_COUNT, 0);
    }
}

