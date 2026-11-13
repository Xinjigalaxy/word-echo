package com.zs.wordecho.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.zs.wordecho.R;

/** Lightweight animated voice waveform. Animates only while speech is active. */
public final class WaveformView extends View {
    private static final int BAR_COUNT = 27;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] idle = new float[BAR_COUNT];
    private ValueAnimator animator;
    private float phase;
    private boolean active;
    private int baseColor;

    public WaveformView(Context context) {
        this(context, null);
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        baseColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary,
                UiUtils.color(context, R.color.primary));
        for (int i = 0; i < BAR_COUNT; i++) {
            float normalized = (float) i / (BAR_COUNT - 1);
            float envelope = (float) Math.sin(normalized * Math.PI);
            idle[i] = 0.18f + envelope * (0.34f + 0.28f * Math.abs((float) Math.sin(i * 0.83f)));
        }
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        if (active) {
            startAnimator();
        } else {
            stopAnimator();
            phase = 0f;
            invalidate();
        }
    }

    public boolean isActive() {
        return active;
    }

    private void startAnimator() {
        if (animator != null && animator.isRunning()) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        animator.setDuration(1250L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (active) {
            startAnimator();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimator();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0) {
            return;
        }
        float centerY = getPaddingTop() + height / 2f;
        float slot = width / BAR_COUNT;
        float stroke = Math.max(UiUtils.dp(getContext(), 2f), slot * 0.34f);
        paint.setStrokeWidth(Math.min(stroke, UiUtils.dp(getContext(), 3.5f)));

        for (int i = 0; i < BAR_COUNT; i++) {
            float amp;
            if (active) {
                float p = phase * 1.35f + i * 0.52f;
                amp = 0.12f + 0.88f * Math.abs((float) Math.sin(p))
                        * (0.45f + 0.55f * Math.abs((float) Math.cos(i * 0.37f)));
            } else {
                amp = idle[i];
            }
            float barHeight = Math.max(UiUtils.dp(getContext(), 3f), height * amp);
            float x = getPaddingLeft() + slot * i + slot / 2f;
            int alpha = active
                    ? 90 + (int) (amp * 130)
                    : 70 + (int) (amp * 80);
            paint.setColor(Color.argb(Math.min(255, alpha),
                    Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)));
            canvas.drawLine(x, centerY - barHeight / 2f, x, centerY + barHeight / 2f, paint);
        }
    }
}
