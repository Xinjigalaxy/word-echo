package com.zs.wordecho.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Favorites are just a local set of word keys. */
public final class FavoriteStore {
    private static final String FILE = "word_echo_favorites";
    private static final String KEY_WORDS = "words";

    private final SharedPreferences prefs;

    public FavoriteStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean isFavorite(Word word) {
        return word != null && getKeys().contains(word.getKey());
    }

    public Set<String> getKeys() {
        Set<String> stored = prefs.getStringSet(KEY_WORDS, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public boolean toggle(Word word) {
        if (word == null) {
            return false;
        }
        Set<String> keys = getKeys();
        boolean added;
        if (keys.contains(word.getKey())) {
            keys.remove(word.getKey());
            added = false;
        } else {
            keys.add(word.getKey());
            added = true;
        }
        prefs.edit().putStringSet(KEY_WORDS, keys).apply();
        return added;
    }

    public void add(Word word) {
        if (word == null) {
            return;
        }
        Set<String> keys = getKeys();
        if (keys.add(word.getKey())) {
            prefs.edit().putStringSet(KEY_WORDS, keys).apply();
        }
    }

    public void remove(Word word) {
        if (word == null) {
            return;
        }
        Set<String> keys = getKeys();
        if (keys.remove(word.getKey())) {
            prefs.edit().putStringSet(KEY_WORDS, keys).apply();
        }
    }

    public int size() {
        return getKeys().size();
    }
}
