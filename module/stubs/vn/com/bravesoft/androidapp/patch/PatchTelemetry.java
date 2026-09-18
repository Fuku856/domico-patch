package vn.com.bravesoft.androidapp.patch;

import android.content.Context;

/**
 * コンパイル用スタブ。実体は scripts/patch_assets 側の手書き smali で、
 * classes4 に入る。
 *
 * <p>このファイルは d8 に渡されないので dex には含まれず、実行時は
 * classes4 の本物が解決される。PatchTelemetry を module/src へ移行したら
 * このスタブは削除すること (build_module.py が重複を検出して落ちる)。
 */
public final class PatchTelemetry {

    public static void apply(Context ctx) {
        throw new RuntimeException("stub");
    }
}
