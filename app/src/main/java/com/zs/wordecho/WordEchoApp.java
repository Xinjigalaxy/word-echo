package com.zs.wordecho;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.zs.wordecho.data.AppPrefs;

public final class WordEchoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppPrefs prefs = new AppPrefs(this);
        AppCompatDelegate.setDefaultNightMode(toDelegateMode(prefs.getThemeMode()));
    }

    public static int toDelegateMode(int mode) {
        switch (mode) {
            case AppPrefs.THEME_LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case AppPrefs.THEME_DARK:
                return AppCompatDelegate.MODE_NIGHT_YES;
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }
}
