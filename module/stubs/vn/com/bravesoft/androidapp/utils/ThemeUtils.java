package vn.com.bravesoft.androidapp.utils;

import android.content.Context;

/** コンパイル専用スタブ。dex には含まれず、実行時は公式クラスへ解決される。 */
public final class ThemeUtils {
    public static final ThemeUtils INSTANCE = new ThemeUtils();

    private ThemeUtils() {
    }

    public final int getColor(Context context) {
        return 0;
    }

    public final int getBtnBackground(Context context, int themeId) {
        return 0;
    }

    public final int buttonTextColorV2(Context context, int themeId) {
        return 0;
    }
}
