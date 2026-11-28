package vn.com.bravesoft.androidapp.patch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

/**
 * リソース不要・純 Java Canvas 描画によるカラーピッカー描画部品群。
 *
 * <ul>
 *   <li>{@link SvBoxView}: 2D 彩度(S) × 明度(V) パレット
 *   <li>{@link HueBarView}: 色相(H) スライダーバー
 *   <li>{@link RainbowCircleDrawable}: 未設定時のカスタム枠を示す色相環グラデーション円
 * </ul>
 */
public final class PatchColorPickerView {

    private PatchColorPickerView() {
    }

    static float dp(Context ctx, float value) {
        return ctx.getResources().getDisplayMetrics().density * value;
    }

    static void disableForceDark(View v) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.setForceDarkAllowed(false);
        }
    }

    /** 2D 彩度(横軸 0..1) × 明度(縦軸 1..0) パレット View。 */
    public static final class SvBoxView extends View {

        public interface Listener {
            void onSvChanged(float sat, float val, boolean fromUser);
        }

        private float hue = 52f;
        private float sat = 1f;
        private float val = 1f;

        private final Paint satPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint valPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final RectF boxRect = new RectF();
        private final Path clipPath = new Path();

        private int cachedW = -1;
        private int cachedH = -1;
        private float cachedHue = -1f;

        private Listener listener;

        public SvBoxView(Context ctx) {
            super(ctx);
            disableForceDark(this);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(ctx, 1f));
            borderPaint.setColor(0x26000000);

            thumbShadowPaint.setStyle(Paint.Style.STROKE);
            thumbShadowPaint.setStrokeWidth(dp(ctx, 2f));
            thumbShadowPaint.setColor(0x66000000);

            thumbRingPaint.setStyle(Paint.Style.STROKE);
            thumbRingPaint.setStrokeWidth(dp(ctx, 2.5f));
            thumbRingPaint.setColor(0xFFFFFFFF);

            thumbFillPaint.setStyle(Paint.Style.FILL);
        }

        public void setListener(Listener l) {
            this.listener = l;
        }

        public void setHsv(float h, float s, float v) {
            this.hue = clamp(h, 0f, 360f);
            this.sat = clamp(s, 0f, 1f);
            this.val = clamp(v, 0f, 1f);
            ((View) this).invalidate();
        }

        public void setHue(float h) {
            this.hue = clamp(h, 0f, 360f);
            ((View) this).invalidate();
        }

        private void ensureShaders() {
            View self = this;
            int w = self.getWidth();
            int h = self.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            Context ctx = self.getContext();
            float pad = dp(ctx, 6f);
            boxRect.set(pad, pad, w - pad, h - pad);
            if (w != cachedW || h != cachedH || hue != cachedHue) {
                cachedW = w;
                cachedH = h;
                cachedHue = hue;

                int pureHue = Color.HSVToColor(new float[] {hue, 1f, 1f});
                satPaint.setShader(new LinearGradient(
                        boxRect.left, boxRect.top, boxRect.right, boxRect.top,
                        0xFFFFFFFF, pureHue, Shader.TileMode.CLAMP));
                valPaint.setShader(new LinearGradient(
                        boxRect.left, boxRect.top, boxRect.left, boxRect.bottom,
                        0x00000000, 0xFF000000, Shader.TileMode.CLAMP));

                float r = dp(ctx, 12f);
                clipPath.reset();
                clipPath.addRoundRect(boxRect, r, r, Path.Direction.CW);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            ensureShaders();
            if (boxRect.width() <= 0 || boxRect.height() <= 0) {
                return;
            }

            Context ctx = ((View) this).getContext();
            float r = dp(ctx, 12f);
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawRect(boxRect, satPaint);
            canvas.drawRect(boxRect, valPaint);
            canvas.restore();

            canvas.drawRoundRect(boxRect, r, r, borderPaint);

            float cx = boxRect.left + sat * boxRect.width();
            float cy = boxRect.top + (1f - val) * boxRect.height();
            int curColor = Color.HSVToColor(new float[] {hue, sat, val});

            thumbFillPaint.setColor(curColor);
            float rFill = dp(ctx, 7.5f);
            float rRing = dp(ctx, 9f);
            float rShadow = dp(ctx, 10.5f);
            canvas.drawCircle(cx, cy, rShadow, thumbShadowPaint);
            canvas.drawCircle(cx, cy, rFill, thumbFillPaint);
            canvas.drawCircle(cx, cy, rRing, thumbRingPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN
                    || action == MotionEvent.ACTION_MOVE
                    || action == MotionEvent.ACTION_UP) {
                View self = this;
                ViewParent parent = self.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                ensureShaders();
                if (boxRect.width() > 0 && boxRect.height() > 0) {
                    sat = clamp((event.getX() - boxRect.left) / boxRect.width(), 0f, 1f);
                    val = clamp(1f - (event.getY() - boxRect.top) / boxRect.height(), 0f, 1f);
                    self.invalidate();
                    if (listener != null) {
                        listener.onSvChanged(sat, val, true);
                    }
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    /** 0°〜360° の色相スライダーバー View。 */
    public static final class HueBarView extends View {

        public interface Listener {
            void onHueChanged(float hue, boolean fromUser);
        }

        private static final int[] HUE_STOPS = {
                0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
                0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
        };

        private float hue = 52f;

        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final RectF barRect = new RectF();
        private int cachedW = -1;
        private int cachedH = -1;

        private Listener listener;

        public HueBarView(Context ctx) {
            super(ctx);
            disableForceDark(this);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(ctx, 1f));
            borderPaint.setColor(0x22000000);

            thumbShadowPaint.setStyle(Paint.Style.STROKE);
            thumbShadowPaint.setStrokeWidth(dp(ctx, 2f));
            thumbShadowPaint.setColor(0x55000000);

            thumbRingPaint.setStyle(Paint.Style.STROKE);
            thumbRingPaint.setStrokeWidth(dp(ctx, 2.5f));
            thumbRingPaint.setColor(0xFFFFFFFF);

            thumbFillPaint.setStyle(Paint.Style.FILL);
        }

        public void setListener(Listener l) {
            this.listener = l;
        }

        public void setHue(float h) {
            this.hue = clamp(h, 0f, 360f);
            ((View) this).invalidate();
        }

        private void ensureShader() {
            View self = this;
            int w = self.getWidth();
            int h = self.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            if (w != cachedW || h != cachedH) {
                cachedW = w;
                cachedH = h;
                Context ctx = self.getContext();
                float padX = dp(ctx, 12f);
                float barH = dp(ctx, 16f);
                float top = (h - barH) * 0.5f;
                barRect.set(padX, top, w - padX, top + barH);
                barPaint.setShader(new LinearGradient(
                        barRect.left, barRect.centerY(), barRect.right, barRect.centerY(),
                        HUE_STOPS, null, Shader.TileMode.CLAMP));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            ensureShader();
            if (barRect.width() <= 0) {
                return;
            }

            float radius = barRect.height() * 0.5f;
            canvas.drawRoundRect(barRect, radius, radius, barPaint);
            canvas.drawRoundRect(barRect, radius, radius, borderPaint);

            float cx = barRect.left + (hue / 360f) * barRect.width();
            float cy = barRect.centerY();
            int pureHue = Color.HSVToColor(new float[] {hue, 1f, 1f});

            Context ctx = ((View) this).getContext();
            thumbFillPaint.setColor(pureHue);
            float rFill = dp(ctx, 8.5f);
            float rRing = dp(ctx, 10f);
            float rShadow = dp(ctx, 11.5f);
            canvas.drawCircle(cx, cy, rShadow, thumbShadowPaint);
            canvas.drawCircle(cx, cy, rFill, thumbFillPaint);
            canvas.drawCircle(cx, cy, rRing, thumbRingPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN
                    || action == MotionEvent.ACTION_MOVE
                    || action == MotionEvent.ACTION_UP) {
                View self = this;
                ViewParent parent = self.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                ensureShader();
                if (barRect.width() > 0) {
                    float ratio = clamp((event.getX() - barRect.left) / barRect.width(), 0f, 1f);
                    hue = ratio * 360f;
                    self.invalidate();
                    if (listener != null) {
                        listener.onHueChanged(hue, true);
                    }
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    /**
     * 公式 ChangeColorDialog の5つ目のカスタムスウォッチ用 Drawable。
     * 未設定時は滑らかな色相環スイープグラデーション円を描画する。
     */
    public static final class RainbowCircleDrawable extends Drawable {

        private static final int[] SWEEP_COLORS = {
                0xFFFF5252, 0xFFFFAB40, 0xFFFFEA00, 0xFF69F0AE,
                0xFF40C4FF, 0xFF7C4DFF, 0xFFFF4081, 0xFFFF5252
        };

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int lastCx = -1;
        private int lastCy = -1;

        public RainbowCircleDrawable(Context ctx) {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(ctx, 1.2f));
            strokePaint.setColor(0x26000000);
        }

        @Override
        public void draw(Canvas canvas) {
            Drawable self = this;
            Rect b = self.getBounds();
            if (b.isEmpty()) {
                return;
            }
            int cx = b.centerX();
            int cy = b.centerY();
            if (cx != lastCx || cy != lastCy) {
                lastCx = cx;
                lastCy = cy;
                fillPaint.setShader(new SweepGradient(cx, cy, SWEEP_COLORS, null));
            }
            float r = Math.min(b.width(), b.height()) * 0.5f - strokePaint.getStrokeWidth();
            if (r > 0) {
                canvas.drawCircle(cx, cy, r, fillPaint);
                canvas.drawCircle(cx, cy, r, strokePaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
