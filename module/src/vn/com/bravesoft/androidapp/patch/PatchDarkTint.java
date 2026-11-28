package vn.com.bravesoft.androidapp.patch;

import android.content.res.ColorStateList;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Force Dark の見た目を補正する (ダークモード有効中のみ、各フレームの描画前に呼ばれる)。
 *
 * <p>HWUI の Force Dark は背景色を Lab の明度で {@code L -> 110 - L} と反転するだけなので、
 * 白と薄いグレーが「ほぼ黒」と「濃いグレー」に潰れて境目が消えたり、面ごとに微妙に違う
 * グレーが混ざったりする。また鮮やかな色見本やテーマ色のボタンまで暗く濁る。
 * そこで反転「前」の色を次のように差し替える:
 *
 * <ul>
 *   <li><b>無彩色の明るい面は「黒」と「グレー」の2色だけにする。</b> 画面の地 (Activity) は黒、
 *       ポップアップ (Dialog・ボトムシート) の地はグレー。その上に載る「親と色の違う面」
 *       (カード・行・ピル・ボタン) は親と逆の色にする。親と同色の面や、画面の大半を覆う面は
 *       親と同じ色。黒・グレーは Force Dark が触らない暗さなのでそのまま描かれる。
 *       グレーは Force Dark が白を変換した色と同じにして、補正できない白とも揃える。
 *   <li>色味のある明るい面 (薄いオレンジの見出しなど) は Force Dark の変換に任せる。
 *   <li>鮮やかな背景色を持つ View は Force Dark の対象外にし、元の色で描く。
 *   <li>明るい枠線は、反転後に中間のグレーとして見えるよう明度の上限を設ける。
 *   <li>ホームのロゴ画像は、黄色のアイコンを保ったまま黒い文字だけ明るくする。
 * </ul>
 *
 * <p>軽量化: 色の判定 (Lab 変換) は色ごとに1回だけ計算してキャッシュし、毎フレームの走査は
 * 既に補正済みかどうかの比較だけで済むようにしている。
 *
 * <p>書き換えた Drawable は元の値を覚えておき、ダークをやめたら {@link #restoreAll} で戻す。
 * アプリが後から色を設定し直した場合は、それを新しい元の値として扱う。
 */
final class PatchDarkTint {

    static final int BLACK = 0xFF000000;
    /** Force Dark が白 (L=100) を変換した色 (L=10) と同じ。 */
    static final int GRAY = 0xFF1B1B1B;

    /** 鮮やか判定: Lab の彩度がこれ以上なら元の色のまま描く。 */
    static final double ACCENT_CHROMA = 35;
    /** これ未満の彩度は無彩色 (白・グレー) とみなして2色に揃える。 */
    static final double NEUTRAL_CHROMA = 12;
    /** Force Dark が背景を暗くするのは L がこれを超える色だけ。 */
    static final double DARKEN_FROM_L = 55;
    /** 枠線は反転後にこの明度になるよう、反転前の明度をここで頭打ちにする。 */
    static final double STROKE_MAX_L = 60;

    static final int K_DARK = 0;     // 暗い色。Force Dark も触らない
    static final int K_NEUTRAL = 1;  // 明るい無彩色 -> 黒/グレー
    static final int K_COLORED = 2;  // 明るい有彩色 -> Force Dark 任せ
    static final int K_ACCENT = 3;   // 鮮やか -> 対象外

    /**
     * ロゴ用の色行列。灰色 g は 1-g に (黒文字 -> 明るいグレー)、ロゴの黄色 #FCCD00 は
     * そのままになるよう解いたもの。B チャンネルを明るさの代わりに使う。
     */
    static final ColorMatrixColorFilter LOGO_FILTER = new ColorMatrixColorFilter(new ColorMatrix(new float[] {
            -0.012f, 0, -0.988f, 0, 255,
            -0.198f, 0, -0.802f, 0, 255,
            -1.012f, 0, 0.012f, 0, 255,
            0, 0, 0, 1, 0,
    }));

    /**
     * 起動画面の KYORITSU ロゴ用。黒い文字 (#3E3938) -> 明るいグレー、青 (#005BAC) -> 明るい青
     * (#5AA0EB)、桃色の円 (#EB6D93..#F7C0C8) はそのまま、となるよう4色から解いたもの。
     */
    static final ColorMatrixColorFilter KYORITSU_FILTER = new ColorMatrixColorFilter(new ColorMatrix(new float[] {
            0.4367f, 0.7972f, -1.1209f, 0, 210.26f,
            -0.5103f, 1.9416f, -1.3591f, 0, 217.08f,
            -0.4944f, 0.9795f, -0.4220f, 0, 218.45f,
            0, 0, 0, 1, 0,
    }));

    /** 色フィルタを掛ける画像 (R.drawable のフィールド名) と、そのフィルタ。 */
    static final String[] LOGO_NAMES = {"ic_domico_home", "ic_splash", "ic_logo_kyoritsu_splash"};
    static final ColorMatrixColorFilter[] LOGO_FILTERS = {LOGO_FILTER, LOGO_FILTER, KYORITSU_FILTER};

    /** 書き換えた Drawable の元の値と、こちらが設定した値。 */
    static final class Orig {
        ColorStateList fill;      // 元の塗り (戻す用)
        int fillColor;            // 元の塗りの代表色 (判定用)
        int fillApplied;
        boolean fillTouched;
        int strokeWidth;
        ColorStateList stroke;
        float dashWidth;
        float dashGap;
        int strokeApplied;
        boolean strokeTouched;
    }

    static final WeakHashMap<Drawable, Orig> sOrig = new WeakHashMap<>();
    /** Force Dark の対象外にした View (鮮やかな背景)。 */
    static final WeakHashMap<View, Boolean> sNoDark = new WeakHashMap<>();
    /** 色フィルタを掛けたロゴ。 */
    static final WeakHashMap<ImageView, Boolean> sLogos = new WeakHashMap<>();

    /** 色 -> (反転後の明度 << 4 | 種別)。 */
    static final SparseIntArray sColorInfo = new SparseIntArray();

    static Field sGdState;         // GradientDrawable#mGradientState
    static Field sStStrokeWidth;   // GradientState#mStrokeWidth
    static Field sStStrokeColors;  // GradientState#mStrokeColors
    static Field sStDashWidth;     // GradientState#mStrokeDashWidth
    static Field sStDashGap;       // GradientState#mStrokeDashGap
    static boolean sStrokeReady;
    static boolean sStrokeTried;

    static final int[] sLogoIds = new int[LOGO_NAMES.length];
    static final WeakHashMap<android.content.res.Resources, Object[]> sLogoByRes = new WeakHashMap<>();
    /** ImageView ごとに、判定済みの Drawable (同じなら毎フレーム比べ直さない)。 */
    static final WeakHashMap<ImageView, Drawable> sChecked = new WeakHashMap<>();
    static boolean sLogoTried;

    /** デバッグ版 (パッケージ名が .debug) のときだけ計測を logcat に出す。 */
    static boolean sStats;

    // 走査中の状態 (メインスレッドのみ)
    static int sRootW;
    static int sRootH;
    static int sVisited;

    // 計測 (logcat で確認する用)
    static long sStatNanos;
    static long sStatMax;
    static int sStatFrames;
    static int sStatViews;
    static long sStatSince;

    private PatchDarkTint() {
    }

    // ---- 走査 -------------------------------------------------------------------

    /** ウィンドウの View 木全体を補正する。popup はダイアログ・ボトムシートなど。 */
    static void adjustWindow(View decor, boolean popup) {
        long t0 = SystemClock.elapsedRealtimeNanos();
        sRootW = decor.getWidth();
        sRootH = decor.getHeight();
        sVisited = 0;
        walk(decor, false, 0, 0, popup ? 1 : 0);
        record(SystemClock.elapsedRealtimeNanos() - t0, sVisited);
    }

    /**
     * @param hasParent  祖先に不透明な塗りがあるか
     * @param parentOrig その塗りの元の色
     * @param parentTier その塗りの段 (0=黒, 1=グレー)
     * @param windowTier ウィンドウの地の段
     */
    static void walk(View v, boolean hasParent, int parentOrig, int parentTier, int windowTier) {
        if (v.getVisibility() != View.VISIBLE) {
            return;
        }
        sVisited++;
        if (v instanceof ImageView) {
            handleLogo((ImageView) v);
        }
        boolean nextHas = hasParent;
        int nextOrig = parentOrig;
        int nextTier = parentTier;

        ColorStateList tint = v.getBackgroundTintList();
        Drawable bg = v.getBackground();
        if (tint != null) {
            int c = tint.getColorForState(v.getDrawableState(), tint.getDefaultColor());
            if ((c >>> 24) >= 0xCC) {
                int inf = info(c);
                if ((inf & 0xF) == K_ACCENT) {
                    markNoDark(v);
                    return; // 子もまとめて元の色で描かれる
                }
                nextHas = true;
                nextOrig = c;
                nextTier = (inf >> 4) < 14 ? 0 : 1;
            }
            unmarkNoDark(v);
        } else if (bg != null) {
            Drawable holder = fillHolder(bg, 0);
            if (holder != null) {
                int orig = originalFill(holder);
                if ((orig >>> 24) >= 0xCC) {
                    int inf = info(orig);
                    int kind = inf & 0xF;
                    if (kind == K_ACCENT) {
                        markNoDark(v);
                        return;
                    }
                    int tier;
                    if (kind == K_NEUTRAL) {
                        if (!hasParent) {
                            tier = windowTier;
                        } else if (similar(orig, parentOrig) || isLarge(v)) {
                            tier = parentTier;
                        } else {
                            tier = 1 - parentTier;
                        }
                        setFill(bg, holder, orig, tier == 0 ? BLACK : GRAY);
                    } else {
                        tier = (inf >> 4) < 14 ? 0 : 1;
                    }
                    nextHas = true;
                    nextOrig = orig;
                    nextTier = tier;
                }
            }
            unmarkNoDark(v);
            adjustStrokes(bg, 0);
        } else {
            unmarkNoDark(v);
        }

        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0, n = g.getChildCount(); i < n; i++) {
                walk(g.getChildAt(i), nextHas, nextOrig, nextTier, windowTier);
            }
        }
    }

    static boolean isLarge(View v) {
        return v.getWidth() >= sRootW * 0.95f && v.getHeight() >= sRootH * 0.5f;
    }

    static boolean similar(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) <= 6
                && Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) <= 6
                && Math.abs((a & 0xFF) - (b & 0xFF)) <= 6;
    }

    static void markNoDark(View v) {
        if (v.isForceDarkAllowed()) {
            v.setForceDarkAllowed(false);
        }
        sNoDark.put(v, Boolean.TRUE);
    }

    static void unmarkNoDark(View v) {
        if (!sNoDark.isEmpty() && sNoDark.remove(v) != null) {
            v.setForceDarkAllowed(true);
        }
    }

    // ---- 塗り -------------------------------------------------------------------

    /** 背景の「地の色」を持つ Drawable (単色)。無ければ null。 */
    static Drawable fillHolder(Drawable d, int depth) {
        if (d == null || depth > 4) {
            return null;
        }
        if (d instanceof ColorDrawable) {
            return d;
        }
        if (d instanceof GradientDrawable) {
            GradientDrawable g = (GradientDrawable) d;
            int[] colors = g.getColors();
            return (colors == null || colors.length < 2) && g.getColor() != null ? d : null;
        }
        if (d instanceof DrawableContainer) {
            return fillHolder(d.getCurrent(), depth + 1);
        }
        if (d instanceof DrawableWrapper) {
            return fillHolder(((DrawableWrapper) d).getDrawable(), depth + 1);
        }
        if (d instanceof LayerDrawable) {
            LayerDrawable ld = (LayerDrawable) d;
            for (int i = 0, n = ld.getNumberOfLayers(); i < n; i++) {
                if (ld.getId(i) == android.R.id.mask) {
                    continue; // リップルの形だけの層
                }
                Drawable h = fillHolder(ld.getDrawable(i), depth + 1);
                if (h != null) {
                    return h;
                }
            }
        }
        return null;
    }

    static int currentFill(Drawable holder) {
        if (holder instanceof ColorDrawable) {
            return ((ColorDrawable) holder).getColor();
        }
        ColorStateList c = ((GradientDrawable) holder).getColor();
        return c == null ? 0 : c.getColorForState(holder.getState(), c.getDefaultColor());
    }

    /** こちらが差し替えた色なら元の色、そうでなければ今の色。 */
    static int originalFill(Drawable holder) {
        int cur = currentFill(holder);
        Orig o = sOrig.get(holder);
        if (o != null && o.fillTouched) {
            if (cur == o.fillApplied) {
                return o.fillColor;
            }
            o.fillTouched = false; // アプリが色を設定し直した
        }
        return cur;
    }

    static void setFill(Drawable bg, Drawable holder, int orig, int target) {
        target = (orig & 0xFF000000) | (target & 0x00FFFFFF);
        Orig o = sOrig.get(holder);
        int cur = currentFill(holder);
        if (o != null && o.fillTouched && cur == o.fillApplied) {
            if (o.fillApplied == target) {
                return; // 補正済み
            }
        } else {
            if (o == null) {
                bg.mutate(); // 同じ見た目の他の View と状態を共有しないように
                o = new Orig();
                sOrig.put(holder, o);
            }
            o.fill = holder instanceof ColorDrawable
                    ? ColorStateList.valueOf(cur) : ((GradientDrawable) holder).getColor();
            o.fillColor = orig;
        }
        o.fillApplied = target;
        o.fillTouched = true;
        if (holder instanceof ColorDrawable) {
            ((ColorDrawable) holder).setColor(target);
        } else {
            ((GradientDrawable) holder).setColor(target);
        }
    }

    // ---- 枠線 -------------------------------------------------------------------

    static void adjustStrokes(Drawable d, int depth) {
        if (d == null || depth > 4) {
            return;
        }
        if (d instanceof GradientDrawable) {
            adjustStroke((GradientDrawable) d);
        } else if (d instanceof DrawableContainer) {
            adjustStrokes(d.getCurrent(), depth + 1);
        } else if (d instanceof DrawableWrapper) {
            adjustStrokes(((DrawableWrapper) d).getDrawable(), depth + 1);
        } else if (d instanceof LayerDrawable) {
            LayerDrawable ld = (LayerDrawable) d;
            for (int i = 0, n = ld.getNumberOfLayers(); i < n; i++) {
                adjustStrokes(ld.getDrawable(i), depth + 1);
            }
        }
    }

    static void adjustStroke(GradientDrawable d) {
        if (!prepareStroke()) {
            return;
        }
        try {
            Object st = sGdState.get(d);
            ColorStateList stroke = (ColorStateList) sStStrokeColors.get(st);
            if (stroke == null) {
                return;
            }
            Orig o = sOrig.get(d);
            int cur = stroke.getDefaultColor();
            if (o != null && o.strokeTouched && cur == o.strokeApplied && !stroke.isStateful()) {
                return; // 補正済み
            }
            int width = sStStrokeWidth.getInt(st);
            if (width <= 0) {
                return;
            }
            int capped = capStroke(cur);
            if (capped == cur) {
                return;
            }
            if (o == null) {
                d.mutate();
                o = new Orig();
                sOrig.put(d, o);
                st = sGdState.get(d);
            }
            o.strokeWidth = width;
            o.stroke = stroke;
            o.dashWidth = sStDashWidth.getFloat(st);
            o.dashGap = sStDashGap.getFloat(st);
            o.strokeApplied = capped;
            o.strokeTouched = true;
            d.setStroke(width, capped, o.dashWidth, o.dashGap);
        } catch (Throwable t) {
            sStrokeReady = false;
            Log.w(PatchDarkMode.TAG, "PatchDarkTint: stroke adjust failed", t);
        }
    }

    static boolean prepareStroke() {
        if (sStrokeTried) {
            return sStrokeReady;
        }
        sStrokeTried = true;
        try {
            sGdState = GradientDrawable.class.getDeclaredField("mGradientState");
            sGdState.setAccessible(true);
            Class<?> st = sGdState.getType();
            sStStrokeWidth = st.getDeclaredField("mStrokeWidth");
            sStStrokeColors = st.getDeclaredField("mStrokeColors");
            sStDashWidth = st.getDeclaredField("mStrokeDashWidth");
            sStDashGap = st.getDeclaredField("mStrokeDashGap");
            sStStrokeWidth.setAccessible(true);
            sStStrokeColors.setAccessible(true);
            sStDashWidth.setAccessible(true);
            sStDashGap.setAccessible(true);
            sStrokeReady = true;
        } catch (Throwable t) {
            Log.w(PatchDarkMode.TAG, "PatchDarkTint: stroke fields unavailable", t);
        }
        return sStrokeReady;
    }

    // ---- ロゴ -------------------------------------------------------------------

    static void handleLogo(ImageView iv) {
        Drawable d = iv.getDrawable();
        if (!(d instanceof BitmapDrawable)) {
            return;
        }
        if (sChecked.get(iv) == d) {
            return; // この画像は判定済み
        }
        sChecked.put(iv, d);
        if (!sLogoTried) {
            sLogoTried = true;
            try {
                Class<?> r = Class.forName("vn.com.bravesoft.androidapp.R$drawable");
                for (int i = 0; i < LOGO_NAMES.length; i++) {
                    try {
                        sLogoIds[i] = r.getField(LOGO_NAMES[i]).getInt(null);
                    } catch (Throwable t) {
                        Log.w(PatchDarkMode.TAG, "PatchDarkTint: logo " + LOGO_NAMES[i] + " not found", t);
                    }
                }
            } catch (Throwable t) {
                Log.w(PatchDarkMode.TAG, "PatchDarkTint: logo lookup failed", t);
            }
        }
        // Activity ごとに Resources (= 画像キャッシュ) が別なので、その View 自身の
        // Resources で読み込んだ画像と比べる。読み込み済みならキャッシュから同じ実体が返る。
        android.content.res.Resources res = iv.getResources();
        Object[] bitmaps = sLogoByRes.get(res);
        if (bitmaps == null) {
            bitmaps = new Object[LOGO_NAMES.length];
            for (int i = 0; i < LOGO_NAMES.length; i++) {
                try {
                    if (sLogoIds[i] != 0) {
                        Drawable ld = res.getDrawable(sLogoIds[i], null);
                        if (ld instanceof BitmapDrawable) {
                            bitmaps[i] = ((BitmapDrawable) ld).getBitmap();
                        }
                    }
                } catch (Throwable t) {
                    Log.w(PatchDarkMode.TAG, "PatchDarkTint: logo load failed", t);
                }
            }
            sLogoByRes.put(res, bitmaps);
        }
        android.graphics.Bitmap bmp = ((BitmapDrawable) d).getBitmap();
        for (int i = 0; i < LOGO_NAMES.length; i++) {
            Object lb = bitmaps[i];
            if (lb == null || bmp == null) {
                continue;
            }
            if (lb == bmp || ((android.graphics.Bitmap) lb).sameAs(bmp)) {
                if (iv.getColorFilter() != LOGO_FILTERS[i]) {
                    iv.setColorFilter(LOGO_FILTERS[i]);
                    sLogos.put(iv, Boolean.TRUE);
                }
                return;
            }
        }
    }

    // ---- 戻す -------------------------------------------------------------------

    /** ダークをやめたとき。書き換えた色・対象外指定・ロゴのフィルタをすべて元に戻す。 */
    static void restoreAll() {
        for (java.util.Map.Entry<Drawable, Orig> e : new ArrayList<>(sOrig.entrySet())) {
            Drawable d = e.getKey();
            Orig o = e.getValue();
            if (d == null || o == null) {
                continue;
            }
            try {
                if (o.fillTouched && currentFill(d) == o.fillApplied) {
                    if (d instanceof ColorDrawable) {
                        ((ColorDrawable) d).setColor(o.fill.getDefaultColor());
                    } else if (d instanceof GradientDrawable) {
                        ((GradientDrawable) d).setColor(o.fill);
                    }
                }
                if (o.strokeTouched && d instanceof GradientDrawable) {
                    ((GradientDrawable) d).setStroke(o.strokeWidth, o.stroke, o.dashWidth, o.dashGap);
                }
            } catch (Throwable t) {
                Log.w(PatchDarkMode.TAG, "PatchDarkTint: restore failed", t);
            }
        }
        sOrig.clear();
        for (View v : new ArrayList<>(sNoDark.keySet())) {
            if (v != null) {
                v.setForceDarkAllowed(true);
            }
        }
        sNoDark.clear();
        for (ImageView iv : new ArrayList<>(sLogos.keySet())) {
            if (iv != null && (iv.getColorFilter() == LOGO_FILTER
                    || iv.getColorFilter() == KYORITSU_FILTER)) {
                iv.clearColorFilter();
            }
        }
        sLogos.clear();
        sChecked.clear();
    }

    // ---- 計測 -------------------------------------------------------------------

    /**
     * 補正1回分の所要時間を集計し、約10秒ごとに logcat へ出す (tag: domico-patch)。
     * 一部メーカー (ColorOS など) はアプリの Info ログを捨てるので Warn で出す。
     * 本番版では出さない。
     */
    static void record(long nanos, int views) {
        if (!sStats) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (sStatSince == 0) {
            sStatSince = now;
        }
        sStatNanos += nanos;
        sStatFrames++;
        sStatViews = Math.max(sStatViews, views);
        if (nanos > sStatMax) {
            sStatMax = nanos;
        }
        if (now - sStatSince >= 10_000) {
            Log.w(PatchDarkMode.TAG, "PatchDarkTint stats: " + sStatFrames + " passes, avg "
                    + (sStatNanos / sStatFrames / 1000) + "us, max " + (sStatMax / 1000)
                    + "us, views<=" + sStatViews + ", colors=" + sColorInfo.size());
            sStatNanos = 0;
            sStatMax = 0;
            sStatFrames = 0;
            sStatViews = 0;
            sStatSince = now;
        }
    }

    // ---- 判定と色計算 -------------------------------------------------------------

    /** カスタムテーマカラー変更時に色判定キャッシュを破棄する。 */
    static void onThemeColorChanged() {
        sColorInfo.clear();
    }

    /** 色の種別と反転後の明度。色ごとに1回だけ計算する。 */
    static int info(int color) {
        if (PatchPrefs.customThemeEnabled && PatchThemeColor.isProtectedAccentColor(color)) {
            return (50 << 4) | K_ACCENT;
        }
        int i = sColorInfo.indexOfKey(color);
        if (i >= 0) {
            return sColorInfo.valueAt(i);
        }
        double[] lab = toLab(color);
        double l = lab[0];
        double c = Math.hypot(lab[1], lab[2]);
        int kind;
        double post;
        if (l <= DARKEN_FROM_L) {
            kind = K_DARK;
            post = l;
        } else {
            post = Math.min(110 - l, 100);
            kind = c >= ACCENT_CHROMA ? K_ACCENT : c < NEUTRAL_CHROMA ? K_NEUTRAL : K_COLORED;
        }
        int v = ((int) Math.round(post) << 4) | kind;
        if (sColorInfo.size() > 4096) {
            sColorInfo.clear();
        }
        sColorInfo.put(color, v);
        return v;
    }

    /** 明るい枠線を、反転後に中間グレー程度で見える明度へ頭打ちにする。 */
    static int capStroke(int color) {
        double[] lab = toLab(color);
        if (lab[0] <= STROKE_MAX_L + 0.5) {
            return color;
        }
        return fromLab(STROKE_MAX_L, lab[1], lab[2], color >>> 24);
    }

    // sRGB (D65) <-> CIE Lab

    static double[] toLab(int c) {
        double r = lin(((c >> 16) & 0xFF) / 255.0);
        double g = lin(((c >> 8) & 0xFF) / 255.0);
        double b = lin((c & 0xFF) / 255.0);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x);
        double fy = f(y);
        double fz = f(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    static int fromLab(double l, double a, double bb, int alpha) {
        double fy = (l + 16) / 116;
        double fx = fy + a / 500;
        double fz = fy - bb / 200;
        double x = finv(fx) * 0.95047;
        double y = finv(fy);
        double z = finv(fz) * 1.08883;
        double r = 3.2406 * x - 1.5372 * y - 0.4986 * z;
        double g = -0.9689 * x + 1.8758 * y + 0.0415 * z;
        double b = 0.0557 * x - 0.2040 * y + 1.0570 * z;
        return (alpha << 24) | (gam(r) << 16) | (gam(g) << 8) | gam(b);
    }

    static double lin(double v) {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    static int gam(double v) {
        double s = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(Math.max(v, 0), 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, s)) * 255);
    }

    static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0;
    }

    static double finv(double t) {
        double t3 = t * t * t;
        return t3 > 0.008856 ? t3 : (t - 16.0 / 116.0) / 7.787;
    }
}
