package vn.com.bravesoft.androidapp.patch;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.tabs.TabLayout;

import org.greenrobot.eventbus.EventBus;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import vn.com.bravesoft.androidapp.databinding.NoticePagerLayoutBinding;
import vn.com.bravesoft.androidapp.event.ChangeColorEvent;
import vn.com.bravesoft.androidapp.helper.UserCtrl;
import vn.com.bravesoft.androidapp.utils.ThemeUtils;
import vn.com.bravesoft.androidapp.utils.Utils;

/**
 * カスタムテーマカラーの派生色計算・リソース解決・即時反映を担うエンジン。
 *
 * <p>公式 {@code ThemeUtils} の各メソッド先頭へ注入した trampoline から呼ばれ、
 * 1つのベースカラー ({@code 0xFFRRGGBB}) からボタン背景・文字コントラスト色・
 * 下部ナビ色・画面全体の極薄パステル背景・チャット吹き出し色などを一貫して算出する。
 */
public final class PatchThemeColor {

    private static final String TAG = "domico-patch";

    /** {@code ChangeColorDialog} 内でカスタムカラー選択中を表す仮想 themeId。 */
    public static final int THEME_ID_CUSTOM = -100;

    /** 公式アプリの標準テキスト濃紺 (@color/darkColor)。 */
    public static final int COLOR_DARK = 0xFF232A37;
    public static final int COLOR_WHITE = 0xFFFFFFFF;
    public static final int COLOR_BLACK = 0xFF000000;
    /** キャンセルボタンの標準赤文字色 (@color/redFC3232)。 */
    public static final int COLOR_RED_CANCEL = 0xFFFC3232;
    /** 公式デフォルトのテーマ色 (#FFDD00)。 */
    public static final int DEFAULT_YELLOW = 0xFFFFDD00;

    /** 公式プリセット4色の代表値 (index = ThemeColor.value: Yellow=0, Pink=1, Green=2, Blue=3)。 */
    public static final int[] PRESET_COLORS = {
            0xFFFFDD00, // Yellow
            0xFFF9848A, // Pink
            0xFF97C85B, // Green
            0xFF0038FF, // Blue
    };

    public static final String[] PRESET_NAMES = {
            "イエロー", "ピンク", "グリーン", "ブルー"
    };

    /** 公式 ChangeColorDialog で「保存」を押す前の一時プレビュー用カラー。 */
    private static volatile int sPreviewColor = DEFAULT_YELLOW;

    private static WeakReference<Activity> sTopActivity;

    private static int sDrawableCancelRed;
    private static int sDrawableCancelGrey;
    private static boolean sDrawableResolved;

    private PatchThemeColor() {
    }

    static void setTopActivity(Activity activity) {
        if (activity != null) {
            sTopActivity = new WeakReference<>(activity);
        }
    }

    public static void setPreviewColor(int color) {
        sPreviewColor = 0xFF000000 | color;
    }

    public static int getPreviewColor() {
        return sPreviewColor;
    }

    public static int getPreviewTextColor() {
        return onPrimaryColor(sPreviewColor);
    }

    /** 現在有効なカスタムプライマリ色 (常に不透明 0xFFRRGGBB)。 */
    public static int primaryColor() {
        return 0xFF000000 | PatchPrefs.customThemeColor;
    }

    /**
     * ダークモードの {@link PatchDarkTint} で反転・無彩色置換から保護すべきカスタム色か。
     * 極端に黒い色 (#000000〜#181818) 以外は、選んだカスタム色をそのまま描画させる。
     */
    static boolean isProtectedAccentColor(int color) {
        int rgb = color & 0x00FFFFFF;
        int primary = PatchPrefs.customThemeColor & 0x00FFFFFF;
        int bubble = chatBubbleColor(primaryColor()) & 0x00FFFFFF;
        if (rgb != primary && rgb != bubble) {
            return false;
        }
        double[] lab = PatchDarkTint.toLab(0xFF000000 | rgb);
        return lab[0] >= 12.0;
    }

    // ---- ThemeUtils フック先メソッド --------------------------------------------

    public static int getColor(Context ctx) {
        return primaryColor();
    }

    public static int getColorBottomBar(Context ctx) {
        return bottomBarColor(ctx, primaryColor());
    }

    public static int getBackgroundColor(Context ctx) {
        return screenBackgroundColor(primaryColor());
    }

    public static ColorStateList buttonBgColor(Context ctx) {
        return ColorStateList.valueOf(primaryColor());
    }

    public static int buttonTextColor(Context ctx) {
        return onPrimaryColor(primaryColor());
    }

    public static int buttonIconColor(Context ctx) {
        return onPrimaryColor(primaryColor());
    }

    public static int buttonCancel(Context ctx) {
        resolveCancelDrawables();
        boolean grey = useGreyCancel(primaryColor());
        return grey ? sDrawableCancelGrey : sDrawableCancelRed;
    }

    public static int buttonCancelLabelColor(Context ctx) {
        return useGreyCancel(primaryColor()) ? COLOR_DARK : COLOR_RED_CANCEL;
    }

    public static ColorStateList getBgMessageChat(Context ctx) {
        return ColorStateList.valueOf(chatBubbleColor(primaryColor()));
    }

    public static int getColorMessageChat(Context ctx) {
        return chatTextColor(primaryColor());
    }

    /**
     * PNG セレクタ等を返すメソッド向け: カスタム有効時は Lab 色差が最も近い
     * 公式プリセット (0..3) へマッピングする。無効時は元の themeId をそのまま返す。
     */
    public static int remapPresetThemeId(int origThemeId) {
        if (!PatchPrefs.customThemeEnabled) {
            return origThemeId;
        }
        return nearestPresetId(primaryColor());
    }

    // ---- 派生カラー計算ロジック -------------------------------------------------

    /**
     * プライマリ背景上の文字・アイコン色。
     * WCAG 相対輝度 Y > 0.42 で濃紺 (#232A37)、それ以下で白 (#FFFFFF) を返す。
     * 公式4色 (Yellow/Green -> 濃紺, Pink/Blue -> 白) の挙動とも完全に一致する。
     */
    public static int onPrimaryColor(int bgColor) {
        return relativeLuminance(bgColor) > 0.42 ? COLOR_DARK : COLOR_WHITE;
    }

    /**
     * 下部ナビゲーションの選択中アイコン・テキスト色。
     * ライト表示で極端に白に近い色 (L* > 86) は白地に埋もれないよう明度を 78 に抑え、
     * ダーク表示で極端に暗い色 (L* < 25) は黒地に埋もれないよう明度を 38 に持ち上げる。
     */
    public static int bottomBarColor(Context ctx, int color) {
        int opaque = 0xFF000000 | color;
        double[] lab = PatchDarkTint.toLab(opaque);
        boolean dark = isDarkActive(ctx);
        if (!dark && lab[0] > 86.0) {
            return PatchDarkTint.fromLab(78.0, lab[1], lab[2], 0xFF);
        }
        if (dark && lab[0] < 25.0) {
            return PatchDarkTint.fromLab(38.0, lab[1], lab[2], 0xFF);
        }
        return opaque;
    }

    /**
     * ホーム・メニュー・カレンダー等の画面全体の極薄パステル背景色。
     * 公式の Pink/Green/Blue と同様に白へ約 7% ブレンドする。極端に暗い色が選ばれた場合でも
     * 背景が灰色に濁らないよう HSV の明度 V に下限 (0.65) を掛けてからブレンドする。
     */
    public static int screenBackgroundColor(int primary) {
        float[] hsv = new float[3];
        Color.colorToHSV(0xFF000000 | primary, hsv);
        if (hsv[2] < 0.65f) {
            hsv[2] = 0.65f;
        }
        int lifted = Color.HSVToColor(hsv);
        return blendRgb(COLOR_WHITE, lifted, 0.07f);
    }

    /** チャット画面の自分のメッセージ吹き出し色（白へ 38% 近づけて柔らかくする）。 */
    public static int chatBubbleColor(int primary) {
        return blendRgb(0xFF000000 | primary, COLOR_WHITE, 0.38f);
    }

    /** チャット吹き出し内の文字色。 */
    public static int chatTextColor(int primary) {
        int bubble = chatBubbleColor(primary);
        return relativeLuminance(bubble) > 0.30 ? COLOR_BLACK : COLOR_WHITE;
    }

    /**
     * 隣り合うプライマリボタンと赤枠キャンセルボタンの色干渉を避ける判定。
     * 公式と同様に、赤〜ピンク〜マゼンタ系および青〜紫系ではグレー塗りキャンセルボタンを使う。
     */
    public static boolean useGreyCancel(int primary) {
        float[] hsv = new float[3];
        Color.colorToHSV(0xFF000000 | primary, hsv);
        float h = hsv[0];
        float s = hsv[1];
        if (s < 0.22f) {
            return false;
        }
        boolean reddish = (h <= 28.0f || h >= 315.0f);
        boolean bluish = (h >= 200.0f && h <= 285.0f && s >= 0.35f);
        return reddish || bluish;
    }

    /** CIE Lab 空間でのユークリッド距離が最小の公式プリセット ID (0..3) を返す。 */
    public static int nearestPresetId(int color) {
        double[] target = PatchDarkTint.toLab(0xFF000000 | color);
        int bestId = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            double[] p = PatchDarkTint.toLab(PRESET_COLORS[i]);
            double dl = target[0] - p[0];
            double da = target[1] - p[1];
            double db = target[2] - p[2];
            double dist = dl * dl + da * da + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                bestId = i;
            }
        }
        return bestId;
    }

    // ---- 設定画面・UI 連携ヘルパ ------------------------------------------------

    /** 現在の実効テーマカラー（カスタム有効時はカスタム色、無効時は公式の現在色）。 */
    public static int resolveEffectiveColor(Context ctx) {
        if (PatchPrefs.customThemeEnabled) {
            return primaryColor();
        }
        if (ctx != null) {
            try {
                return ThemeUtils.INSTANCE.getColor(ctx);
            } catch (Throwable ignored) {
            }
        }
        return DEFAULT_YELLOW;
    }

    /** パッチ設定画面の行右端に出す現在値ラベル。 */
    public static String currentStatusLabel(Context ctx) {
        if (PatchPrefs.customThemeEnabled) {
            return formatHex(primaryColor());
        }
        int id = 0;
        if (ctx != null) {
            try {
                UserCtrl uc = Utils.INSTANCE.getUserCtrl(ctx);
                if (uc != null) {
                    id = uc.getThemeId();
                }
            } catch (Throwable ignored) {
            }
        }
        if (id < 0 || id >= PRESET_NAMES.length) {
            id = 0;
        }
        return "公式 (" + PRESET_NAMES[id] + ")";
    }

    /** #RRGGBB 形式の大文字 HEX 文字列に整形する。 */
    public static String formatHex(int color) {
        return String.format(Locale.US, "#%06X", color & 0x00FFFFFF);
    }

    /**
     * お知らせ画面 (NoticeFragment) のタブ選択背景・文字色にカスタムカラーを適用する。
     */
    public static void applyNoticeTabs(NoticePagerLayoutBinding binding) {
        if (!PatchPrefs.customThemeEnabled || binding == null || binding.tablayout == null) {
            return;
        }
        try {
            TabLayout tabLayout = binding.tablayout;
            int primary = primaryColor();
            int onPrimary = onPrimaryColor(primary);
            int count = tabLayout.getTabCount();
            for (int i = 0; i < count; i++) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                if (tab != null && tab.view != null) {
                    StateListDrawable sld = new StateListDrawable();
                    sld.addState(
                            new int[] {android.R.attr.state_selected},
                            new ColorDrawable(primary));
                    sld.addState(
                            new int[0],
                            new ColorDrawable(COLOR_WHITE));
                    ((View) tab.view).setBackground(sld);
                }
            }
            ColorStateList existing = tabLayout.getTabTextColors();
            int unselected = existing != null ? existing.getDefaultColor() : 0xFF777777;
            tabLayout.setTabTextColors(unselected, onPrimary);
        } catch (Throwable t) {
            Log.w(TAG, "PatchThemeColor.applyNoticeTabs failed", t);
        }
    }

    /**
     * テーマカラー変更をアプリ全体へ即時反映する（再起動不要）。
     * EventBus 購読中の画面に加え、非購読の可視 Fragment (HomeFragment 等) も更新する。
     */
    public static void broadcastColorChanged() {
        PatchDarkTint.onThemeColorChanged();
        try {
            EventBus bus = EventBus.getDefault();
            if (bus != null) {
                bus.post(new ChangeColorEvent());
            }
        } catch (Throwable t) {
            Log.w(TAG, "PatchThemeColor: EventBus post failed", t);
        }
        try {
            Activity act = sTopActivity != null ? sTopActivity.get() : null;
            if (act instanceof FragmentActivity) {
                refreshFragments(((FragmentActivity) act).getSupportFragmentManager());
            }
        } catch (Throwable t) {
            Log.w(TAG, "PatchThemeColor: fragment refresh failed", t);
        }
        try {
            PatchDarkMode.onModeChanged();
        } catch (Throwable ignored) {
        }
    }

    private static void refreshFragments(FragmentManager fm) {
        if (fm == null) {
            return;
        }
        List<Fragment> fragments = fm.getFragments();
        if (fragments == null) {
            return;
        }
        for (Fragment f : fragments) {
            if (f == null || !f.isAdded()) {
                continue;
            }
            try {
                Method m = f.getClass().getDeclaredMethod("onChangeColorEvent");
                m.setAccessible(true);
                m.invoke(f);
            } catch (NoSuchMethodException ignored) {
                // 引数なし onChangeColorEvent を持たない Fragment はスキップ
            } catch (Throwable t) {
                Log.w(TAG, "PatchThemeColor: onChangeColorEvent invoke failed on "
                        + f.getClass().getSimpleName(), t);
            }
            try {
                if ("vn.com.bravesoft.androidapp.ui.NoticeFragment".equals(f.getClass().getName())) {
                    Field bf = f.getClass().getDeclaredField("binding");
                    bf.setAccessible(true);
                    Object b = bf.get(f);
                    if (b instanceof NoticePagerLayoutBinding) {
                        applyNoticeTabs((NoticePagerLayoutBinding) b);
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                refreshFragments(f.getChildFragmentManager());
            } catch (Throwable ignored) {
            }
        }
    }

    // ---- 内部ユーティリティ -----------------------------------------------------

    private static synchronized void resolveCancelDrawables() {
        if (sDrawableResolved) {
            return;
        }
        sDrawableResolved = true;
        try {
            Class<?> r = Class.forName("vn.com.bravesoft.androidapp.R$drawable");
            sDrawableCancelRed = r.getField("bg_button_red").getInt(null);
            sDrawableCancelGrey = r.getField("bg_button_check_in_grey").getInt(null);
        } catch (Throwable t) {
            Log.w(TAG, "PatchThemeColor: R$drawable lookup failed", t);
        }
    }

    private static boolean isDarkActive(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        int mode = PatchPrefs.darkMode;
        if (mode == PatchDarkMode.MODE_ALWAYS) {
            return true;
        }
        if (mode == PatchDarkMode.MODE_SYSTEM && ctx != null) {
            int night = ctx.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return night == Configuration.UI_MODE_NIGHT_YES;
        }
        return false;
    }

    public static double relativeLuminance(int color) {
        double r = PatchDarkTint.lin(((color >> 16) & 0xFF) / 255.0);
        double g = PatchDarkTint.lin(((color >> 8) & 0xFF) / 255.0);
        double b = PatchDarkTint.lin((color & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    public static int blendRgb(int base, int overlay, float ratio) {
        float inv = 1.0f - ratio;
        int r = Math.round(((base >> 16) & 0xFF) * inv + ((overlay >> 16) & 0xFF) * ratio);
        int g = Math.round(((base >> 8) & 0xFF) * inv + ((overlay >> 8) & 0xFF) * ratio);
        int b = Math.round((base & 0xFF) * inv + (overlay & 0xFF) * ratio);
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
