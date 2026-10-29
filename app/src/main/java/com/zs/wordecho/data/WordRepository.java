package com.zs.wordecho.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads the bundled, de-duplicated CET-4 / CET-6 word bank once. */
public final class WordRepository {
    public interface Callback {
        void onLoaded(List<Word> words);
        void onError(Throwable error);
    }

    private static volatile WordRepository instance;

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Word> all = new ArrayList<>();
    private volatile boolean loaded;

    private WordRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static WordRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (WordRepository.class) {
                if (instance == null) {
                    instance = new WordRepository(context);
                }
            }
        }
        return instance;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void load(Callback callback) {
        if (loaded) {
            callback.onLoaded(getAll());
            return;
        }
        executor.execute(() -> {
            try {
                List<Word> parsed = new ArrayList<>(7000);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        appContext.getAssets().open("words.tsv"), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            continue;
                        }
                        String[] parts = line.split("\\t", 3);
                        if (parts.length < 3) {
                            continue;
                        }
                        try {
                            int mask = Integer.parseInt(parts[0].trim());
                            String word = parts[1].trim();
                            String meaning = parts[2].trim();
                            if (!word.isEmpty() && !meaning.isEmpty()) {
                                parsed.add(new Word(mask, word, meaning));
                            }
                        } catch (NumberFormatException ignored) {
                            // Skip a malformed line rather than losing the whole word bank.
                        }
                    }
                }
                synchronized (all) {
                    all.clear();
                    all.addAll(parsed);
                    loaded = true;
                }
                mainHandler.post(() -> callback.onLoaded(getAll()));
            } catch (Throwable error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    public List<Word> getAll() {
        synchronized (all) {
            return Collections.unmodifiableList(new ArrayList<>(all));
        }
    }

    /** level: 0 = all, 1 = CET-4, 2 = CET-6. */
    public List<Word> getByLevel(int level) {
        if (level == 0) {
            return getAll();
        }
        List<Word> result = new ArrayList<>();
        synchronized (all) {
            for (Word word : all) {
                if (word.hasLevel(level)) {
                    result.add(word);
                }
            }
        }
        return result;
    }

    public List<Word> getFavorites(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> lookup = new HashSet<>(keys);
        List<Word> result = new ArrayList<>();
        synchronized (all) {
            for (Word word : all) {
                if (lookup.contains(word.getKey())) {
                    result.add(word);
                }
            }
        }
        result.sort((a, b) -> a.getText().compareToIgnoreCase(b.getText()));
        return result;
    }

    public List<Word> search(String query, int level) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Word> result = new ArrayList<>();
        synchronized (all) {
            for (Word word : all) {
                if (!word.hasLevel(level)) {
                    continue;
                }
                if (needle.isEmpty()
                        || word.getText().toLowerCase(Locale.ROOT).contains(needle)
                        || word.getMeaning().contains(needle)) {
                    result.add(word);
                }
            }
        }
        result.sort((a, b) -> a.getText().compareToIgnoreCase(b.getText()));
        return result;
    }

    public Word findByKey(String key) {
        if (key == null) {
            return null;
        }
        synchronized (all) {
            for (Word word : all) {
                if (word.getKey().equals(key)) {
                    return word;
                }
            }
        }
        return null;
    }

    public int countForLevel(int level) {
        if (level == 0) {
            return getAll().size();
        }
        int count = 0;
        synchronized (all) {
            for (Word word : all) {
                if (word.hasLevel(level)) {
                    count++;
                }
            }
        }
        return count;
    }
}
