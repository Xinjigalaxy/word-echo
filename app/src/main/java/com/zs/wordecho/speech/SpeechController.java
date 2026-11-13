package com.zs.wordecho.speech;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.zs.wordecho.BuildConfig;
import com.zs.wordecho.data.Word;

import java.util.Locale;

/**
 * Thin, lifecycle-safe wrapper around the platform TextToSpeech engine.
 *
 * <p>The reading sequence is: English word (once or twice), Chinese meaning
 * (once), then optional letter-by-letter spelling. Only one language is active
 * at a time, which keeps third-party engines reliable.</p>
 */
public final class SpeechController {
    public interface Listener {
        void onReady(boolean chineseAvailable);
        void onUnavailable();
        void onSpeakingChanged(boolean speaking);
        void onWordFinished();
        void onError(String message);
    }

    private final Context context;
    private final Listener listener;
    private final String enginePackage;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextToSpeech tts;
    private boolean ready;
    private boolean released;
    private boolean speaking;
    private boolean chineseAvailable;
    private boolean englishAvailable;
    private int generation;
    private int wordRepeatTarget = 1;
    private boolean speakMeaning;
    private boolean speakSpelling;
    private String spellingText = "";
    private Word currentWord;
    private Locale englishLocale = Locale.US;
    private float rate = 0.95f;
    private Runnable pendingRepeat;

    public SpeechController(Context context, String enginePackage, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.enginePackage = enginePackage;
        createEngine();
    }

    private void createEngine() {
        if (enginePackage != null && !enginePackage.isEmpty()) {
            tts = new TextToSpeech(context, status -> mainHandler.post(() -> onInitialized(status)),
                    enginePackage);
        } else {
            tts = new TextToSpeech(context, status -> mainHandler.post(() -> onInitialized(status)));
        }
    }

    public String getEnginePackage() {
        return enginePackage;
    }

    private void onInitialized(int status) {
        if (released) {
            return;
        }
        if (status != TextToSpeech.SUCCESS) {
            ready = false;
            listener.onUnavailable();
            return;
        }
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        tts.setSpeechRate(rate);
        tts.setPitch(1.0f);
        tts.setOnUtteranceProgressListener(progressListener);
        chineseAvailable = isLanguageUsable(Locale.SIMPLIFIED_CHINESE);
        englishAvailable = isLanguageUsable(Locale.US);
        ready = true;
        listener.onReady(chineseAvailable);
    }

    private boolean isLanguageUsable(Locale locale) {
        try {
            int result = tts.isLanguageAvailable(locale);
            return result >= TextToSpeech.LANG_AVAILABLE;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isChineseAvailable() {
        return chineseAvailable;
    }

    public boolean isEnglishAvailable() {
        return englishAvailable;
    }

    public boolean isSpeaking() {
        return speaking;
    }

    public void setEnglishAccent(boolean uk) {
        englishLocale = uk ? Locale.UK : Locale.US;
    }

    public void setSpeechRate(float value) {
        rate = Math.max(0.5f, Math.min(1.5f, value));
        if (tts != null) {
            tts.setSpeechRate(rate);
        }
    }

    /**
     * @param wordRepeats  how many times the English headword is read (1–3)
     * @param withMeaning  read the Chinese meaning once after the headword
     * @param withSpelling read the letters once after the meaning
     */
    public void speakWord(Word word, int wordRepeats, boolean withMeaning, boolean withSpelling) {
        if (!ready) {
            listener.onError("语音引擎尚未就绪");
            return;
        }
        if (word == null) {
            return;
        }
        cancelPendingRepeat();
        generation++;
        tts.stop();
        currentWord = word;
        wordRepeatTarget = Math.max(1, Math.min(3, wordRepeats));
        speakMeaning = withMeaning;
        speakSpelling = withSpelling;
        spellingText = buildSpelling(word.getText());
        speaking = true;
        listener.onSpeakingChanged(true);
        speakEnglish(generation, 0);
    }

    private void speakEnglish(int gen, int repetition) {
        if (gen != generation || currentWord == null) {
            return;
        }
        if (tts.setLanguage(englishLocale) < TextToSpeech.LANG_AVAILABLE) {
            fail("当前语音引擎不支持英语发音");
            return;
        }
        tts.setSpeechRate(rate);
        String id = "en_" + gen + "_" + repetition;
        log("english #" + (repetition + 1) + " " + currentWord.getText());
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        int result = tts.speak(currentWord.getText(), TextToSpeech.QUEUE_FLUSH, params, id);
        if (result == TextToSpeech.ERROR) {
            fail("无法启动语音合成");
        }
    }

    private void speakChinese(int gen) {
        if (gen != generation || currentWord == null) {
            return;
        }
        if (!chineseAvailable) {
            afterMeaning(gen);
            return;
        }
        if (tts.setLanguage(Locale.SIMPLIFIED_CHINESE) < TextToSpeech.LANG_AVAILABLE) {
            chineseAvailable = false;
            afterMeaning(gen);
            return;
        }
        tts.setSpeechRate(rate);
        String id = "zh_" + gen;
        log("chinese " + currentWord.getSpokenMeaning());
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        int result = tts.speak(currentWord.getSpokenMeaning(),
                TextToSpeech.QUEUE_FLUSH, params, id);
        if (result == TextToSpeech.ERROR) {
            afterMeaning(gen);
        }
    }

    private void speakSpelling(int gen) {
        if (gen != generation) {
            return;
        }
        if (!speakSpelling || spellingText.isEmpty()) {
            completeWord(gen);
            return;
        }
        if (tts.setLanguage(englishLocale) < TextToSpeech.LANG_AVAILABLE) {
            completeWord(gen);
            return;
        }
        tts.setSpeechRate(rate);
        String id = "sp_" + gen;
        log("spelling " + spellingText);
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        int result = tts.speak(spellingText, TextToSpeech.QUEUE_FLUSH, params, id);
        if (result == TextToSpeech.ERROR) {
            completeWord(gen);
        }
    }

    private void afterMeaning(int gen) {
        if (gen != generation) {
            return;
        }
        if (speakSpelling && !spellingText.isEmpty()) {
            speakSpelling(gen);
        } else {
            completeWord(gen);
        }
    }

    private void completeWord(int gen) {
        if (gen != generation) {
            return;
        }
        speaking = false;
        listener.onSpeakingChanged(false);
        listener.onWordFinished();
    }

    private String buildSpelling(String word) {
        if (word == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (Character.isLetter(c)) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    private void fail(String message) {
        generation++;
        speaking = false;
        listener.onSpeakingChanged(false);
        listener.onError(message);
    }

    private void cancelPendingRepeat() {
        if (pendingRepeat != null) {
            mainHandler.removeCallbacks(pendingRepeat);
            pendingRepeat = null;
        }
    }

    public void stop() {
        generation++;
        cancelPendingRepeat();
        if (tts != null) {
            tts.stop();
        }
        if (speaking) {
            speaking = false;
            listener.onSpeakingChanged(false);
        }
    }

    public void shutdown() {
        released = true;
        generation++;
        cancelPendingRepeat();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
        speaking = false;
    }

    private final UtteranceProgressListener progressListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {
            // The UI already shows the speaking state as soon as speech is queued.
        }

        @Override
        public void onDone(String utteranceId) {
            if (utteranceId == null) {
                return;
            }
            mainHandler.post(() -> handleDone(utteranceId));
        }

        @Override
        public void onError(String utteranceId) {
            mainHandler.post(() -> {
                if (isCurrentUtterance(utteranceId)) {
                    fail("系统语音合成出错");
                }
            });
        }

        @Override
        public void onError(String utteranceId, int errorCode) {
            mainHandler.post(() -> {
                if (isCurrentUtterance(utteranceId)) {
                    fail("系统语音合成出错 (" + errorCode + ")");
                }
            });
        }
    };

    private boolean isCurrentUtterance(String utteranceId) {
        if (utteranceId == null) {
            return false;
        }
        String[] parts = utteranceId.split("_");
        if (parts.length < 2) {
            return false;
        }
        try {
            return Integer.parseInt(parts[1]) == generation;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void handleDone(String utteranceId) {
        if (utteranceId.startsWith("en_")) {
            String[] parts = utteranceId.split("_");
            if (parts.length < 3) {
                return;
            }
            try {
                int gen = Integer.parseInt(parts[1]);
                int repetition = Integer.parseInt(parts[2]);
                if (gen != generation) {
                    return;
                }
                if (repetition + 1 < wordRepeatTarget) {
                    scheduleEnglishRepeat(gen, repetition + 1);
                } else if (speakMeaning && chineseAvailable) {
                    speakChinese(gen);
                } else {
                    afterMeaning(gen);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed ids from an engine.
            }
        } else if (utteranceId.startsWith("zh_")) {
            String[] parts = utteranceId.split("_");
            if (parts.length < 2) {
                return;
            }
            try {
                int gen = Integer.parseInt(parts[1]);
                if (gen == generation) {
                    afterMeaning(gen);
                }
            } catch (NumberFormatException ignored) {
                // Ignore.
            }
        } else if (utteranceId.startsWith("sp_")) {
            String[] parts = utteranceId.split("_");
            if (parts.length < 2) {
                return;
            }
            try {
                int gen = Integer.parseInt(parts[1]);
                if (gen == generation) {
                    completeWord(gen);
                }
            } catch (NumberFormatException ignored) {
                // Ignore.
            }
        }
    }

    private void log(String stage) {
        if (BuildConfig.DEBUG) {
            Log.d("WordEchoTTS", "gen=" + generation + " " + stage);
        }
    }

    private void scheduleEnglishRepeat(int gen, int repetition) {
        cancelPendingRepeat();
        pendingRepeat = () -> {
            if (gen == generation) {
                speakEnglish(gen, repetition);
            }
        };
        mainHandler.postDelayed(pendingRepeat, 420L);
    }
}
