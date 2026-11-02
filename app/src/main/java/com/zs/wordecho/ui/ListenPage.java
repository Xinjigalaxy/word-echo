package com.zs.wordecho.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.zs.wordecho.R;
import com.zs.wordecho.data.Word;

/** The listening page. Owns no playback state; it is a renderer + input surface. */
public final class ListenPage extends FrameLayout {
    public interface Listener {
        void onPlayPause();
        void onNext();
        void onPrevious();
        void onReplay();
        void onFavorite();
        void onLevelChanged(int level);
        void onAutoPlayChanged(boolean enabled);
        void onIntervalChanged(float seconds);
        void onRevealToggled(boolean hidden);
        void onOpenSettings();
        void onOpenTtsSettings();
    }

    private final Listener listener;
    private final TextView dailyCount;
    private final TextView dailyGoal;
    private final TextView dailyHint;
    private final LinearProgressIndicator dailyProgress;
    private final MaterialButtonToggleGroup levelGroup;
    private final TextView levelPill;
    private final TextView wordCounter;
    private final TextView wordText;
    private final TextView posPill;
    private final TextView voiceTag;
    private final LinearLayout meaningPanel;
    private final TextView meaningText;
    private final TextView meaningHiddenHint;
    private final WaveformView waveform;
    private final TextView speechState;
    private final ImageButton favoriteButton;
    private final TextView playbackStatus;
    private final MaterialCardView playCard;
    private final ImageView playIcon;
    private final MaterialSwitch autoSwitch;
    private final Slider intervalSlider;
    private final TextView intervalValue;
    private final View ttsDot;
    private final TextView ttsStatus;
    private final LinearLayout wordContent;
    private final View[] controls;
    private int renderToken;
    private boolean meaningHidden;
    private boolean suppressCallbacks;

    public ListenPage(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        LayoutInflater.from(context).inflate(R.layout.view_listen, this, true);

        dailyCount = findViewById(R.id.daily_count);
        dailyGoal = findViewById(R.id.daily_goal);
        dailyHint = findViewById(R.id.daily_hint);
        dailyProgress = findViewById(R.id.daily_progress);
        levelGroup = findViewById(R.id.level_group);
        levelPill = findViewById(R.id.level_pill);
        wordCounter = findViewById(R.id.word_counter);
        wordText = findViewById(R.id.word_text);
        posPill = findViewById(R.id.pos_pill);
        voiceTag = findViewById(R.id.voice_tag);
        meaningPanel = findViewById(R.id.meaning_panel);
        meaningText = findViewById(R.id.meaning_text);
        meaningHiddenHint = findViewById(R.id.meaning_hidden_hint);
        waveform = findViewById(R.id.waveform);
        speechState = findViewById(R.id.speech_state);
        favoriteButton = findViewById(R.id.btn_favorite);
        playbackStatus = findViewById(R.id.playback_status);
        playCard = findViewById(R.id.btn_play);
        playIcon = findViewById(R.id.play_icon);
        autoSwitch = findViewById(R.id.auto_switch);
        intervalSlider = findViewById(R.id.interval_slider);
        intervalValue = findViewById(R.id.interval_value);
        ttsDot = findViewById(R.id.tts_dot);
        ttsStatus = findViewById(R.id.tts_status);
        wordContent = findViewById(R.id.word_content);

        controls = new View[]{
                findViewById(R.id.btn_replay),
                findViewById(R.id.btn_previous),
                playCard,
                findViewById(R.id.btn_next),
                findViewById(R.id.btn_reveal)
        };

        levelGroup.check(R.id.seg_all);
        bindListeners();
        setDaily(0, 30);
        setMeaningHidden(false);
        setPlaying(false, false);
        setVoiceTag("美音 · 0.95×");
        setEmptyWord();
    }

    private void bindListeners() {
        levelGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || suppressCallbacks) {
                return;
            }
            int level = 0;
            if (checkedId == R.id.seg_four) {
                level = 1;
            } else if (checkedId == R.id.seg_six) {
                level = 2;
            }
            listener.onLevelChanged(level);
        });

        playCard.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            listener.onPlayPause();
        });
        findViewById(R.id.btn_next).setOnClickListener(v -> listener.onNext());
        findViewById(R.id.btn_previous).setOnClickListener(v -> listener.onPrevious());
        findViewById(R.id.btn_replay).setOnClickListener(v -> listener.onReplay());
        favoriteButton.setOnClickListener(v -> listener.onFavorite());
        meaningPanel.setOnClickListener(v -> {
            setMeaningHidden(!meaningHidden);
            listener.onRevealToggled(meaningHidden);
        });
        findViewById(R.id.btn_reveal).setOnClickListener(v -> {
            setMeaningHidden(!meaningHidden);
            listener.onRevealToggled(meaningHidden);
        });
        findViewById(R.id.tts_settings_link).setOnClickListener(v -> listener.onOpenTtsSettings());

        autoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppressCallbacks) {
                listener.onAutoPlayChanged(isChecked);
            }
        });
        intervalSlider.addOnChangeListener((slider, value, fromUser) -> {
            intervalValue.setText(getContext().getString(R.string.interval_value, value));
            if (fromUser && !suppressCallbacks) {
                listener.onIntervalChanged(value);
            }
        });
    }

    private void setEmptyWord() {
        wordText.setText("echo");
        wordCounter.setText("— / —");
        posPill.setVisibility(View.GONE);
        meaningText.setText("让每个单词，都有回声。轻点播放，开始今天的听词。");
        meaningHiddenHint.setVisibility(View.GONE);
        speechState.setText(R.string.state_standby);
    }

    public void showWord(Word word, int position, int total, boolean animate) {
        if (word == null) {
            return;
        }
        final int token = ++renderToken;
        boolean hasContent = !TextUtils.isEmpty(wordText.getText())
                && !"echo".contentEquals(wordText.getText());
        Runnable render = () -> {
            if (token != renderToken) {
                return;
            }
            bindWord(word, position, total);
        };

        wordContent.animate().cancel();
        if (!animate || !hasContent) {
            render.run();
            wordContent.setAlpha(1f);
            wordContent.setTranslationY(0f);
            return;
        }
        wordContent.animate()
                .alpha(0f)
                .translationY(UiUtils.dp(getContext(), 10))
                .setDuration(110)
                .setInterpolator(new FastOutSlowInInterpolator())
                .withEndAction(() -> {
                    if (token != renderToken) {
                        return;
                    }
                    render.run();
                    wordContent.setTranslationY(-UiUtils.dp(getContext(), 8));
                    wordContent.setAlpha(0f);
                    wordContent.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(210)
                            .setInterpolator(new FastOutSlowInInterpolator())
                            .start();
                })
                .start();
    }

    private void bindWord(Word word, int position, int total) {
        UiUtils.applyLevelTag(levelPill, word.getLevelMask());
        if (position <= 0) {
            wordCounter.setText(R.string.preview_word);
        } else {
            wordCounter.setText(getContext().getString(R.string.word_position, position, total));
        }
        wordText.setText(word.getText());
        wordText.setGravity(Gravity.START);
        if (TextUtils.isEmpty(word.getPosLabel())) {
            posPill.setVisibility(View.GONE);
        } else {
            posPill.setVisibility(View.VISIBLE);
            posPill.setText(word.getPosLabel());
        }
        meaningText.setText(word.getMeaningBody());
        refreshMeaningVisibility();
        setFavorite(false);
    }

    public void setFavorite(boolean favorite) {
        favoriteButton.setSelected(favorite);
        favoriteButton.setImageResource(favorite ? R.drawable.ic_star_filled : R.drawable.ic_star);
        favoriteButton.setContentDescription(getContext().getString(
                favorite ? R.string.unfavorite : R.string.favorite));
        favoriteButton.setColorFilter(ContextCompat.getColor(getContext(),
                favorite ? R.color.secondary : R.color.on_surface_variant));
    }

    public void setMeaningHidden(boolean hidden) {
        meaningHidden = hidden;
        refreshMeaningVisibility();
    }

    private void refreshMeaningVisibility() {
        meaningText.setVisibility(meaningHidden ? View.GONE : View.VISIBLE);
        meaningHiddenHint.setVisibility(meaningHidden ? View.VISIBLE : View.GONE);
        View reveal = findViewById(R.id.btn_reveal);
        if (reveal instanceof ImageButton) {
            ((ImageButton) reveal).setImageResource(meaningHidden ? R.drawable.ic_eye_off : R.drawable.ic_eye);
            reveal.setContentDescription(getContext().getString(
                    meaningHidden ? R.string.show_meaning : R.string.hide_meaning));
        }
    }

    public void setPlaying(boolean playing, boolean speaking) {
        playIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        playCard.setContentDescription(getContext().getString(
                playing ? R.string.pause : R.string.cd_play_pause));
        waveform.setActive(speaking);
        if (speaking) {
            speechState.setText(R.string.state_speaking);
        } else if (playing) {
            speechState.setText(R.string.state_preparing);
        } else {
            speechState.setText(R.string.state_standby);
        }
    }

    public void setPlaybackStatus(String text) {
        playbackStatus.setText(text);
    }

    public void setSpeechState(String text) {
        speechState.setText(text);
    }

    public void setVoiceTag(String text) {
        voiceTag.setText(text);
    }

    public void setDaily(int count, int goal) {
        dailyCount.setText(String.valueOf(count));
        dailyGoal.setText(getContext().getString(R.string.daily_goal, goal));
        int remaining = Math.max(0, goal - count);
        dailyHint.setText(remaining == 0
                ? getContext().getString(R.string.daily_hint_done)
                : getContext().getString(R.string.daily_hint_remaining, remaining));
        int progress = goal <= 0 ? 0 : Math.min(100, Math.round(count * 100f / goal));
        dailyProgress.setProgressCompat(progress, true);
    }

    public void setTtsStatus(String text, int colorRes) {
        ttsStatus.setText(text);
        int color = ContextCompat.getColor(getContext(), colorRes);
        ttsDot.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    public void setLevelSelection(int level) {
        suppressCallbacks = true;
        if (level == 1) {
            levelGroup.check(R.id.seg_four);
        } else if (level == 2) {
            levelGroup.check(R.id.seg_six);
        } else {
            levelGroup.check(R.id.seg_all);
        }
        suppressCallbacks = false;
    }

    public void setAutoPlay(boolean enabled) {
        suppressCallbacks = true;
        autoSwitch.setChecked(enabled);
        suppressCallbacks = false;
    }

    public void setInterval(float seconds) {
        suppressCallbacks = true;
        intervalSlider.setStepSize(0f);
        intervalSlider.setValue(Math.max(1f, Math.min(10f, seconds)));
        intervalValue.setText(getContext().getString(R.string.interval_value, seconds));
        suppressCallbacks = false;
    }

    public void setLoading(boolean loading) {
        for (View control : controls) {
            control.setEnabled(!loading);
            control.setAlpha(loading ? 0.45f : 1f);
        }
        intervalSlider.setEnabled(!loading);
        autoSwitch.setEnabled(!loading);
        if (loading) {
            playbackStatus.setText(R.string.status_loading);
            wordText.setText("…");
            wordCounter.setText("");
            meaningText.setText(R.string.loading_wordbank_desc);
        }
    }

    public void setControlsEnabled(boolean enabled) {
        for (View control : controls) {
            control.setEnabled(enabled);
            control.setAlpha(enabled ? 1f : 0.45f);
        }
        intervalSlider.setEnabled(enabled);
        autoSwitch.setEnabled(enabled);
    }

    public void setWordPosition(int position, int total) {
        wordCounter.setText(getContext().getString(R.string.word_position, position, total));
    }

    public void updateLevelHint(String text) {
        playbackStatus.setText(text);
    }
}
