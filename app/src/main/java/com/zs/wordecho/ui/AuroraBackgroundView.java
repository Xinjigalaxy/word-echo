package com.zs.wordecho.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.zs.wordecho.R;

/**
 * Quiet paper-like backdrop with two soft colour fields. The decoration stays
 * deliberately low-contrast so the word card remains the focus.
 */
public final class AuroraBackgroundView extends View {
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader baseShader;
    private Shader greenGlow;
    private Shader amberGlow;

    public AuroraBackgroundView(Context context) {
        this(context, null);
    }

    public AuroraBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(UiUtils.dp(context, 0.65f));
        linePaint.setColor(withAlpha(UiUtils.color(context, R.color.paper_line), 42));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        int background = UiUtils.color(getContext(), R.color.background);
        int surfaceLow = UiUtils.color(getContext(), R.color.surface_container_low);
        baseShader = new LinearGradient(0, 0, 0, h,
                new int[]{background, surfaceLow, background},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP);
        basePaint.setShader(baseShader);

        int primary = UiUtils.color(getContext(), R.color.primary);
        greenGlow = new RadialGradient(
                w * 0.12f, -h * 0.02f, w * 0.82f,
                new int[]{withAlpha(primary, 30), withAlpha(primary, 0)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        int amber = UiUtils.color(getContext(), R.color.accent_amber);
        amberGlow = new RadialGradient(
                w * 1.06f, h * 0.53f, w * 0.72f,
                new int[]{withAlpha(amber, 25), withAlpha(amber, 0)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (baseShader == null) {
            canvas.drawColor(UiUtils.color(getContext(), R.color.background));
            return;
        }
        canvas.drawRect(0, 0, w, h, basePaint);

        glowPaint.setShader(greenGlow);
        canvas.drawRect(0, 0, w, h, glowPaint);
        glowPaint.setShader(amberGlow);
        canvas.drawRect(0, 0, w, h, glowPaint);

        int step = UiUtils.dp(getContext(), 52);
        for (int y = step * 3; y < h; y += step) {
            canvas.drawLine(UiUtils.dp(getContext(), 26), y, w - UiUtils.dp(getContext(), 26), y, linePaint);
        }
    }
}
