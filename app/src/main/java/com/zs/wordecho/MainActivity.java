package com.zs.wordecho;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.snackbar.Snackbar;
import com.zs.wordecho.data.AppPrefs;
import com.zs.wordecho.data.FavoriteStore;
import com.zs.wordecho.data.Word;
import com.zs.wordecho.data.WordRepository;
import com.zs.wordecho.playback.PlaybackKeepAliveService;
import com.zs.wordecho.speech.SpeechController;
import com.zs.wordecho.ui.BrowsePage;
import com.zs.wordecho.ui.ListenPage;
import com.zs.wordecho.ui.SavedPage;
import com.zs.wordecho.ui.SettingsSheet;
import com.zs.wordecho.ui.UiUtils;
import com.zs.wordecho.ui.WordDetailSheet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class MainActivity extends AppCompatActivity {
    private static final String STATE_WORD_KEY = "current_word_key";
    private static final String STATE_FAVORITES_MODE = "favorites_mode";
    private static final int REQUEST_NOTIFICATIONS = 4102;

    private AppPrefs prefs;
    private FavoriteStore favorites;
    private WordRepository repository;
    private SpeechController speech;

    private ListenPage listenPage;
    private BrowsePage browsePage;
    private SavedPage savedPage;
    private BottomNavigationView bottomNav;
    private FrameLayout pageContainer;
    private View navSurface;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<Word> deck = new ArrayList<>();
    private final ArrayDeque<Word> history = new ArrayDeque<>();

    private Word currentWord;
    private int deckIndex = -1;
    private int currentTab;
    private boolean playing;
    private boolean previewing;
    private boolean favoritesMode;
    private boolean ttsReady;
    private boolean startWhenReady;
    private boolean pendingPreview;
    private boolean warnedMissingChinese;
    private boolean loading = true;
    private Runnable pendingNext;
    private String pendingRestoreKey;
    private String ttsStatusText;
    private int ttsStatusColorRes;
    private String activeEnginePackage = "";
    private boolean keepAliveRunning;
    private int systemBarBottomInset;

    private final BroadcastReceiver stopPlaybackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pausePlayback();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new AppPrefs(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && prefs.isDynamicColor()) {
            DynamicColors.applyToActivityIfAvailable(this);
        }
        favorites = new FavoriteStore(this);
        repository = WordRepository.getInstance(this);
        if (savedInstanceState != null) {
            pendingRestoreKey = savedInstanceState.getString(STATE_WORD_KEY);
            favoritesMode = savedInstanceState.getBoolean(STATE_FAVORITES_MODE, false);
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_main);
        applyInsetsAndSystemBars();

        pageContainer = findViewById(R.id.page_container);
        bottomNav = findViewById(R.id.bottom_nav);
        navSurface = findViewById(R.id.nav_surface);

        buildPages();
        bindNavigation();
        bindHeader();

        ttsStatusText = getString(R.string.voice_loading);
        ttsStatusColorRes = R.color.accent_amber;
        listenPage.setTtsStatus(ttsStatusText, ttsStatusColorRes);
        listenPage.setLevelSelection(prefs.getLevelFilter());
        listenPage.setAutoPlay(prefs.isAutoPlay());
        listenPage.setInterval(prefs.getIntervalSeconds());
        listenPage.setDaily(prefs.getDailyCount(), prefs.getDailyGoal());
        listenPage.setMeaningHidden(prefs.isMeaningHidden());
        listenPage.setLoading(true);

        setupSpeech();
        loadWordBank();
        ContextCompat.registerReceiver(this, stopPlaybackReceiver,
                new IntentFilter(PlaybackKeepAliveService.ACTION_STOP_PLAYBACK),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentTab != 0) {
                    goToTab(0);
                } else {
                    finish();
                }
            }
        });
    }

    private void buildPages() {
        listenPage = new ListenPage(this, new ListenPage.Listener() {
            @Override
            public void onPlayPause() {
                togglePlayback();
            }

            @Override
            public void onNext() {
                nextWord(true);
            }

            @Override
            public void onPrevious() {
                previousWord();
            }

            @Override
            public void onReplay() {
                replayCurrent();
            }

            @Override
            public void onFavorite() {
                if (currentWord != null) {
                    toggleFavorite(currentWord);
                }
            }

            @Override
            public void onLevelChanged(int level) {
                onLevelSelected(level);
            }

            @Override
            public void onAutoPlayChanged(boolean enabled) {
                prefs.setAutoPlay(enabled);
                if (!enabled && playing) {
                    handler.removeCallbacksAndMessages(null);
                    pendingNext = null;
                    playing = false;
                    listenPage.setPlaying(false, speech != null && speech.isSpeaking());
                    listenPage.setPlaybackStatus(getString(R.string.state_paused));
                    clearKeepScreenOn();
                }
            }

            @Override
            public void onIntervalChanged(float seconds) {
                prefs.setIntervalSeconds(Math.round(seconds * 2f) / 2f);
            }

            @Override
            public void onRevealToggled(boolean hidden) {
                prefs.setMeaningHidden(hidden);
            }

            @Override
            public void onOpenSettings() {
                openSettings();
            }

            @Override
            public void onOpenTtsSettings() {
                openSystemTtsSettings();
            }
        });

        browsePage = new BrowsePage(this, favorites, new BrowsePage.Listener() {
            @Override
            public void onWordClick(Word word) {
                openDetail(word);
            }

            @Override
            public void onSpeak(Word word) {
                previewWord(word);
            }

            @Override
            public void onToggleFavorite(Word word) {
                toggleFavorite(word);
                browsePage.refreshFavorites();
            }
        });

        savedPage = new SavedPage(this, favorites, new SavedPage.Listener() {
            @Override
            public void onWordClick(Word word) {
                openDetail(word);
            }

            @Override
            public void onSpeak(Word word) {
                previewWord(word);
            }

            @Override
            public void onToggleFavorite(Word word) {
                toggleFavorite(word);
                savedPage.refresh();
            }

            @Override
            public void onReview() {
                startFavoritesReview();
            }

            @Override
            public void onBrowse() {
                goToTab(1);
            }
        });

        addPage(listenPage);
        addPage(browsePage);
        addPage(savedPage);
        listenPage.setVisibility(View.VISIBLE);
        browsePage.setVisibility(View.GONE);
        savedPage.setVisibility(View.GONE);
    }

    private void addPage(View page) {
        pageContainer.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void bindHeader() {
        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings());
    }

    private void bindNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_library) {
                showTab(1, true);
            } else if (id == R.id.nav_saved) {
                showTab(2, true);
            } else {
                showTab(0, true);
            }
            return true;
        });
        bottomNav.setSelectedItemId(R.id.nav_listen);
    }

    private void showTab(int index, boolean animate) {
        if (index == currentTab && pageFor(index).getVisibility() == View.VISIBLE) {
            if (index == 1) {
                browsePage.refresh();
            } else if (index == 2) {
                savedPage.refresh();
            }
            return;
        }
        currentTab = index;
        for (int i = 0; i < 3; i++) {
            View page = pageFor(i);
            if (i == index) {
                page.setVisibility(View.VISIBLE);
                if (animate) {
                    page.setAlpha(0f);
                    page.setTranslationY(UiUtils.dp(this, 8));
                    page.animate().alpha(1f).translationY(0f).setDuration(220).start();
                }
            } else {
                page.setVisibility(View.GONE);
            }
        }
        if (index != 0) {
            browsePage.hideKeyboard();
        }
        if (index == 1) {
            browsePage.refresh();
        } else if (index == 2) {
            savedPage.refresh();
        }
    }

    private View pageFor(int index) {
        if (index == 1) {
            return browsePage;
        }
        if (index == 2) {
            return savedPage;
        }
        return listenPage;
    }

    private void goToTab(int index) {
        int itemId = index == 1 ? R.id.nav_library : index == 2 ? R.id.nav_saved : R.id.nav_listen;
        if (bottomNav.getSelectedItemId() != itemId) {
            bottomNav.setSelectedItemId(itemId);
        } else {
            showTab(index, true);
        }
    }

    private void applyInsetsAndSystemBars() {
        View root = findViewById(R.id.root);
        View appColumn = findViewById(R.id.app_column);
        View nav = findViewById(R.id.nav_surface);
        View bottomNav = findViewById(R.id.bottom_nav);
        // NavigationBarView adds its own bottom inset padding. Consume insets at the
        // container and keep the bar itself at zero bottom padding; the window-level
        // safe-area calculation below is the single source of truth.
        ViewCompat.setOnApplyWindowInsetsListener(nav,
                (v, insets) -> WindowInsetsCompat.CONSUMED);
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav,
                (v, insets) -> WindowInsetsCompat.CONSUMED);
        bottomNav.addOnLayoutChangeListener((v, left, top, right, bottom,
                                             oldLeft, oldTop, oldRight, oldBottom) -> {
            if (v.getPaddingBottom() != 0) {
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), 0);
                v.requestLayout();
            }
        });
        root.addOnLayoutChangeListener((v, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) ->
                updateNavSurfacePadding(root, nav));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            androidx.core.graphics.Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            systemBarBottomInset = bars.bottom;
            appColumn.setPadding(0, bars.top, 0, keyboardVisible ? ime.bottom : 0);
            nav.setVisibility(keyboardVisible ? View.GONE : View.VISIBLE);
            updateNavSurfacePadding(root, nav);
            return insets;
        });

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);
        root.requestApplyInsets();
    }

    private void updateNavSurfacePadding(View root, View nav) {
        if (root == null || nav == null || root.getHeight() == 0) {
            return;
        }
        int[] location = new int[2];
        root.getLocationOnScreen(location);
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        boolean windowCoversNavigationBar = location[1] + root.getHeight()
                >= screenHeight - UiUtils.dp(this, 4);
        int bottom = windowCoversNavigationBar ? systemBarBottomInset : 0;
        if (nav.getPaddingBottom() != bottom) {
            nav.setPadding(0, 0, 0, bottom);
        }
    }

    private void setupSpeech() {
        activeEnginePackage = resolveTtsEngine();
        speech = new SpeechController(this, activeEnginePackage,
                new SpeechController.Listener() {
            @Override
            public void onReady(boolean chineseAvailable) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                ttsReady = true;
                speech.setEnglishAccent(prefs.getAccent() == AppPrefs.ACCENT_UK);
                speech.setSpeechRate(prefs.getSpeechRate());
                updateTtsStatus();
                updateVoiceTag();
                if (!chineseAvailable && prefs.isTranslateVoice()) {
                    maybeWarnMissingChinese();
                }
                if (startWhenReady) {
                    startWhenReady = false;
                    if (pendingPreview) {
                        pendingPreview = false;
                        playing = true;
                        previewing = true;
                        speakCurrent();
                    } else {
                        startPlayback();
                    }
                }
            }

            @Override
            public void onUnavailable() {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                ttsReady = false;
                startWhenReady = false;
                pendingPreview = false;
                ttsStatusText = getString(R.string.voice_init_failed);
                ttsStatusColorRes = R.color.error;
                listenPage.setTtsStatus(ttsStatusText, ttsStatusColorRes);
                showMessage(getString(R.string.voice_engine_missing), true);
            }

            @Override
            public void onSpeakingChanged(boolean speaking) {
                if (listenPage != null) {
                    listenPage.setPlaying(playing, speaking);
                }
            }

            @Override
            public void onWordFinished() {
                handleWordFinished();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                playing = false;
                previewing = false;
                pendingPreview = false;
                cancelPendingNext();
                listenPage.setPlaying(false, false);
                listenPage.setPlaybackStatus(getString(R.string.voice_error, message));
                clearKeepScreenOn();
                showMessage(getString(R.string.voice_error, message), true);
            }
        });
    }

    private void loadWordBank() {
        repository.load(new WordRepository.Callback() {
            @Override
            public void onLoaded(List<Word> words) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                loading = false;
                listenPage.setLoading(false);
                listenPage.setControlsEnabled(true);
                boolean restored = false;
                if (pendingRestoreKey != null) {
                    restored = buildDeck(prefs.getLevelFilter(), favoritesMode, pendingRestoreKey);
                    pendingRestoreKey = null;
                }
                if (!restored) {
                    buildDeck(prefs.getLevelFilter(), false, null);
                }
                updateDaily();
                browsePage.refresh();
                savedPage.refresh();
            }

            @Override
            public void onError(Throwable error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                loading = false;
                listenPage.setLoading(false);
                listenPage.setControlsEnabled(false);
                listenPage.setPlaybackStatus(getString(R.string.wordbank_error));
                showMessage(getString(R.string.wordbank_error), false);
            }
        });
    }

    private boolean buildDeck(int level, boolean onlyFavorites, String restoreKey) {
        List<Word> source;
        if (onlyFavorites) {
            source = repository.getFavorites(favorites.getKeys());
            if (source.isEmpty()) {
                return false;
            }
        } else {
            source = new ArrayList<>(repository.getByLevel(level));
        }
        if (source.isEmpty()) {
            currentWord = null;
            listenPage.setPlaybackStatus(getString(R.string.wordbank_error));
            return false;
        }
        Collections.shuffle(source, random);
        deck.clear();
        deck.addAll(source);
        deckIndex = 0;
        history.clear();
        if (restoreKey != null) {
            for (int i = 0; i < deck.size(); i++) {
                if (deck.get(i).getKey().equals(restoreKey)) {
                    Collections.swap(deck, 0, i);
                    break;
                }
            }
        }
        currentWord = deck.get(0);
        renderCurrent(false, deckIndex + 1, deck.size());
        listenPage.setPlaybackStatus(getString(R.string.status_idle));
        return true;
    }

    private void renderCurrent(boolean animate, int position, int total) {
        if (currentWord == null) {
            return;
        }
        listenPage.showWord(currentWord, position, total, animate);
        listenPage.setFavorite(favorites.isFavorite(currentWord));
    }

    private void onLevelSelected(int level) {
        if (prefs.getLevelFilter() == level && !favoritesMode) {
            return;
        }
        int previousLevel = prefs.getLevelFilter();
        if (previousLevel == level && favoritesMode) {
            // Leaving favorites mode requires a new deck even if level is unchanged.
        }
        boolean wasPlaying = playing;
        stopTransport(false);
        prefs.setLevelFilter(level);
        favoritesMode = false;
        if (!buildDeck(level, false, null)) {
            return;
        }
        String label = level == 1 ? getString(R.string.level_four)
                : level == 2 ? getString(R.string.level_six) : getString(R.string.level_all);
        listenPage.setPlaybackStatus(getString(R.string.status_choose_level, label));
        if (wasPlaying) {
            playing = true;
            startPlayback();
        }
    }

    private void togglePlayback() {
        if (loading) {
            return;
        }
        if (playing || (speech != null && speech.isSpeaking())) {
            pausePlayback();
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        if (currentWord == null) {
            return;
        }
        if (!ttsReady) {
            startWhenReady = true;
            showMessage(getString(R.string.voice_not_ready), false);
            return;
        }
        playing = true;
        previewing = false;
        pendingPreview = false;
        speakCurrent();
    }

    private void speakCurrent() {
        if (currentWord == null) {
            return;
        }
        cancelPendingNext();
        speech.setEnglishAccent(prefs.getAccent() == AppPrefs.ACCENT_UK);
        speech.setSpeechRate(prefs.getSpeechRate());
        updateVoiceTag();
        speech.speakWord(currentWord, prefs.getRepeatCount(), prefs.isTranslateVoice(),
                prefs.isSpellWord());
        listenPage.setPlaying(true, true);
        listenPage.setPlaybackStatus(getString(R.string.status_speaking, currentWord.getText()));
        int count = prefs.recordPlayed(currentWord);
        listenPage.setDaily(count, prefs.getDailyGoal());
        if (prefs.isScreenOffPlayback()) {
            clearKeepScreenOn();
        } else {
            addKeepScreenOn();
        }
        startKeepAliveIfEnabled();
    }

    private void pausePlayback() {
        stopTransport(true);
    }

    private void stopTransport(boolean updateStatus) {
        cancelPendingNext();
        if (speech != null) {
            speech.stop();
        }
        playing = false;
        previewing = false;
        startWhenReady = false;
        pendingPreview = false;
        if (listenPage != null) {
            listenPage.setPlaying(false, false);
            if (updateStatus) {
                listenPage.setPlaybackStatus(getString(R.string.state_paused));
            }
        }
        stopKeepAlive();
        clearKeepScreenOn();
    }

    private void startKeepAliveIfEnabled() {
        if (prefs.isScreenOffPlayback() && playing && currentWord != null
                && !keepAliveRunning) {
            PlaybackKeepAliveService.start(this, currentWord.getText());
            keepAliveRunning = true;
        }
    }

    private void stopKeepAlive() {
        if (keepAliveRunning) {
            PlaybackKeepAliveService.stop(this);
            keepAliveRunning = false;
        }
    }

    private void replayCurrent() {
        if (currentWord == null) {
            return;
        }
        stopTransport(false);
        playing = true;
        previewing = false;
        startPlayback();
    }

    private void nextWord(boolean speak) {
        if (loading || deck.isEmpty()) {
            return;
        }
        stopTransport(false);
        previewing = false;
        advanceWord();
        if (!speak) {
            renderCurrent(true, deckIndex + 1, deck.size());
            return;
        }
        playing = true;
        startPlayback();
    }

    private void advanceWord() {
        if (deck.isEmpty()) {
            return;
        }
        if (currentWord != null) {
            history.push(currentWord);
            while (history.size() > 80) {
                history.removeLast();
            }
        }
        if (deckIndex < deck.size() - 1) {
            deckIndex++;
        } else {
            Word old = currentWord;
            Collections.shuffle(deck, random);
            if (deck.size() > 1 && old != null && deck.get(0).getKey().equals(old.getKey())) {
                Collections.swap(deck, 0, 1);
            }
            deckIndex = 0;
            showMessage(getString(R.string.cycle_restart), false);
        }
        currentWord = deck.get(deckIndex);
        renderCurrent(true, deckIndex + 1, deck.size());
    }

    private void previousWord() {
        if (loading || deck.isEmpty()) {
            return;
        }
        stopTransport(false);
        previewing = false;
        if (!history.isEmpty()) {
            currentWord = history.pop();
            int index = indexOf(currentWord);
            if (index >= 0) {
                deckIndex = index;
            }
        } else if (deckIndex > 0) {
            deckIndex--;
            currentWord = deck.get(deckIndex);
        } else {
            currentWord = deck.get(0);
            deckIndex = 0;
        }
        renderCurrent(true, deckIndex + 1, deck.size());
        playing = true;
        startPlayback();
    }

    private int indexOf(Word word) {
        for (int i = 0; i < deck.size(); i++) {
            if (deck.get(i).getKey().equals(word.getKey())) {
                return i;
            }
        }
        return -1;
    }

    private void handleWordFinished() {
        if (previewing) {
            previewing = false;
            playing = false;
            listenPage.setPlaying(false, false);
            listenPage.setPlaybackStatus(getString(R.string.status_finished));
            clearKeepScreenOn();
            return;
        }
        if (playing && prefs.isAutoPlay()) {
            listenPage.setPlaying(true, false);
            listenPage.setPlaybackStatus(getString(R.string.status_next));
            scheduleNext();
        } else {
            playing = false;
            listenPage.setPlaying(false, false);
            listenPage.setPlaybackStatus(getString(R.string.status_finished));
            clearKeepScreenOn();
        }
    }

    private void scheduleNext() {
        cancelPendingNext();
        long delay = Math.max(1000L, (long) (prefs.getIntervalSeconds() * 1000f));
        pendingNext = () -> {
            pendingNext = null;
            if (!playing) {
                return;
            }
            advanceWord();
            if (playing) {
                speakCurrent();
            }
        };
        handler.postDelayed(pendingNext, delay);
    }

    private void cancelPendingNext() {
        if (pendingNext != null) {
            handler.removeCallbacks(pendingNext);
            pendingNext = null;
        }
    }

    private void updateDaily() {
        listenPage.setDaily(prefs.getDailyCount(), prefs.getDailyGoal());
    }

    private void updateTtsStatus() {
        if (speech == null) {
            return;
        }
        if (!speech.isReady()) {
            ttsStatusText = getString(R.string.voice_loading);
            ttsStatusColorRes = R.color.accent_amber;
            listenPage.setTtsStatus(ttsStatusText, ttsStatusColorRes);
            return;
        }
        String accent = UiUtils.accentLabel(this, prefs.getAccent());
        String engine = UiUtils.ttsEngineLabel(this, speech.getEnginePackage());
        if (speech.isChineseAvailable() || !prefs.isTranslateVoice()) {
            ttsStatusText = getString(R.string.voice_ready_engine, engine, accent);
            ttsStatusColorRes = R.color.primary;
        } else {
            ttsStatusText = getString(R.string.voice_chinese_missing);
            ttsStatusColorRes = R.color.accent_amber;
        }
        listenPage.setTtsStatus(ttsStatusText, ttsStatusColorRes);
    }

    private String resolveTtsEngine() {
        String engine = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.TTS_DEFAULT_SYNTH);
        return engine == null ? "" : engine;
    }

    private void updateVoiceTag() {
        if (speech == null) {
            return;
        }
        String tag = getString(R.string.voice_tag,
                UiUtils.accentLabel(this, prefs.getAccent()), prefs.getSpeechRate());
        if (prefs.isSpellWord()) {
            tag = tag + " · " + getString(R.string.spell_tag);
        }
        if (prefs.isTranslateVoice() && !speech.isChineseAvailable()) {
            tag = tag + " · " + getString(R.string.only_english);
        }
        listenPage.setVoiceTag(tag);
    }

    private void maybeWarnMissingChinese() {
        if (warnedMissingChinese) {
            return;
        }
        warnedMissingChinese = true;
        Snackbar.make(findViewById(R.id.root), getString(R.string.voice_chinese_missing),
                        Snackbar.LENGTH_LONG)
                .setAnchorView(navSurface)
                .setAction(R.string.voice_settings, v -> openSystemTtsSettings())
                .show();
    }

    private void toggleFavorite(Word word) {
        if (word == null) {
            return;
        }
        boolean added = favorites.toggle(word);
        if (currentWord != null && currentWord.getKey().equals(word.getKey())) {
            listenPage.setFavorite(added);
        }
        browsePage.refreshFavorites();
        savedPage.refresh();
        showMessage(getString(added ? R.string.add_favorite : R.string.remove_favorite), false);
    }

    private void previewWord(Word word) {
        if (word == null) {
            return;
        }
        stopTransport(false);
        currentWord = word;
        previewing = true;
        int index = indexOf(word);
        if (index >= 0) {
            deckIndex = index;
            listenPage.showWord(word, deckIndex + 1, deck.size(), true);
        } else {
            listenPage.showWord(word, 0, deck.size(), true);
        }
        listenPage.setFavorite(favorites.isFavorite(word));
        if (!ttsReady) {
            playing = false;
            startWhenReady = true;
            pendingPreview = true;
            showMessage(getString(R.string.voice_not_ready), false);
            return;
        }
        playing = true;
        speakCurrent();
    }

    private void openDetail(Word word) {
        WordDetailSheet.show(this, word, favorites, new WordDetailSheet.Listener() {
            @Override
            public void onFavoriteChanged(Word changed, boolean favorite) {
                if (currentWord != null && currentWord.getKey().equals(changed.getKey())) {
                    listenPage.setFavorite(favorite);
                }
                browsePage.refreshFavorites();
                savedPage.refresh();
                showMessage(getString(favorite ? R.string.add_favorite : R.string.remove_favorite), false);
            }

            @Override
            public void onListen(Word selected) {
                goToTab(0);
                previewWord(selected);
            }
        });
    }

    private void startFavoritesReview() {
        if (favorites.size() == 0) {
            showMessage(getString(R.string.no_favorites_toast), false);
            return;
        }
        stopTransport(false);
        favoritesMode = true;
        if (!buildDeck(prefs.getLevelFilter(), true, null)) {
            favoritesMode = false;
            showMessage(getString(R.string.no_favorites_toast), false);
            return;
        }
        goToTab(0);
        playing = true;
        startPlayback();
    }

    private void openSettings() {
        SettingsSheet.show(this, prefs, ttsStatusText, ttsStatusColorRes,
                new SettingsSheet.Listener() {
                    @Override
                    public void onAccentChanged(int accent) {
                        if (speech != null) {
                            speech.setEnglishAccent(accent == AppPrefs.ACCENT_UK);
                        }
                        updateTtsStatus();
                        updateVoiceTag();
                        showMessage(getString(R.string.accent_changed,
                                getString(accent == AppPrefs.ACCENT_UK
                                        ? R.string.accent_uk : R.string.accent_us)), false);
                    }

                    @Override
                    public void onRateChanged(float rate) {
                        if (speech != null) {
                            speech.setSpeechRate(rate);
                        }
                        updateVoiceTag();
                    }

                    @Override
                    public void onTranslateChanged(boolean enabled) {
                        updateTtsStatus();
                        updateVoiceTag();
                        if (enabled && speech != null && speech.isReady()
                                && !speech.isChineseAvailable()) {
                            maybeWarnMissingChinese();
                        }
                    }

                    @Override
                    public void onScreenOffChanged(boolean enabled) {
                        if (enabled) {
                            requestNotificationPermissionIfNeeded();
                            clearKeepScreenOn();
                            if (playing) {
                                startKeepAliveIfEnabled();
                            }
                        } else {
                            stopKeepAlive();
                            if (playing) {
                                addKeepScreenOn();
                            }
                        }
                    }

                    @Override
                    public void onDailyGoalChanged(int goal) {
                        updateDaily();
                    }

                    @Override
                    public void onDynamicColorChanged(boolean enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            recreate();
                        }
                    }

                    @Override
                    public void onThemeChanged(int themeMode) {
                        AppCompatDelegate.setDefaultNightMode(WordEchoApp.toDelegateMode(themeMode));
                    }

                    @Override
                    public void onOpenSystemTts() {
                        openSystemTtsSettings();
                    }
                });
    }

    private void openSystemTtsSettings() {
        Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                showMessage(getString(R.string.voice_engine_missing), false);
            }
        }
    }

    private void showMessage(String message, boolean withSettingsAction) {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.root), message, Snackbar.LENGTH_SHORT)
                .setAnchorView(navSurface);
        if (withSettingsAction) {
            snackbar.setAction(R.string.voice_settings, v -> openSystemTtsSettings());
        }
        snackbar.show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void addKeepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void clearKeepScreenOn() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.isScreenOffPlayback() && playing) {
            startKeepAliveIfEnabled();
        } else if (!prefs.isScreenOffPlayback() && keepAliveRunning) {
            stopKeepAlive();
        }
        if (speech == null) {
            return;
        }
        String currentEngine = resolveTtsEngine();
        if (!currentEngine.equals(activeEnginePackage)) {
            stopTransport(false);
            speech.shutdown();
            setupSpeech();
            showMessage(getString(R.string.engine_switched,
                    UiUtils.ttsEngineLabel(this, currentEngine)), false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!prefs.isScreenOffPlayback()
                && (playing || (speech != null && speech.isSpeaking()))) {
            stopTransport(true);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (currentWord != null) {
            outState.putString(STATE_WORD_KEY, currentWord.getKey());
        }
        outState.putBoolean(STATE_FAVORITES_MODE, favoritesMode);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(stopPlaybackReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was already unregistered.
        }
        stopKeepAlive();
        handler.removeCallbacksAndMessages(null);
        if (speech != null) {
            speech.shutdown();
            speech = null;
        }
    }
}
