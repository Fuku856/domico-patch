package vn.com.bravesoft.androidapp.patch;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 各パッチの ON/OFF を保持する SharedPreferences ラッパ。
 *
 * <p>static フィールドは公式コードへ注入した trampoline から
 * {@code sget-boolean} で直接読まれる。フィールド名・型を変えると
 * classes4 側の参照が解決できなくなるので注意。
 *
 * <p>既定値は「パッチ有効」側に倒してあるため、{@link #load(Context)} が
 * 走る前でもトグル未設定時と同じ挙動になる。
 */
public final class PatchPrefs {

    static final String FILE = "domico_patch";

    static final String KEY_TOAST = "toast_clickthrough";
    static final String KEY_LOADING = "loading_clickthrough";
    static final String KEY_TELEMETRY = "telemetry_off";
    static final String KEY_CHECKIN = "checkin_outoftime";
    static final String KEY_AUTO_CHECKIN = "checkin_autocheckin";
    static final String KEY_PUSH_DEBUG = "push_debug_log";

    public static volatile boolean toastEnabled = true;
    public static volatile boolean loadingEnabled = true;
    public static volatile boolean telemetryOff = true;
    public static volatile boolean checkinEnabled;
    public static volatile boolean autoCheckinEnabled;

    public static boolean get(Context ctx, String key, boolean def) {
        if (ctx == null) {
            return def;
        }
        return ctx.getSharedPreferences(FILE, 0).getBoolean(key, def);
    }

    /**
     * キーごとの既定値。裏機能 (時間外チェックイン / 自動チェックイン) と
     * デバッグ用の push ログだけ既定 OFF、他は既定 ON。
     */
    public static boolean defaultOf(String key) {
        return !(KEY_CHECKIN.equals(key)
                || KEY_AUTO_CHECKIN.equals(key)
                || KEY_PUSH_DEBUG.equals(key));
    }

    public static void load(Context ctx) {
        if (ctx == null) {
            return;
        }
        SharedPreferences sp = ctx.getSharedPreferences(FILE, 0);
        toastEnabled = sp.getBoolean(KEY_TOAST, defaultOf(KEY_TOAST));
        loadingEnabled = sp.getBoolean(KEY_LOADING, defaultOf(KEY_LOADING));
        telemetryOff = sp.getBoolean(KEY_TELEMETRY, defaultOf(KEY_TELEMETRY));
        checkinEnabled = sp.getBoolean(KEY_CHECKIN, defaultOf(KEY_CHECKIN));
        autoCheckinEnabled = sp.getBoolean(KEY_AUTO_CHECKIN, defaultOf(KEY_AUTO_CHECKIN));
    }

    public static void set(Context ctx, String key, boolean value) {
        if (ctx == null) {
            return;
        }
        ctx.getSharedPreferences(FILE, 0).edit().putBoolean(key, value).apply();
        load(ctx);
    }
}
