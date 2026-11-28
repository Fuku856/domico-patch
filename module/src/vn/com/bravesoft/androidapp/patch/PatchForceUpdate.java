package vn.com.bravesoft.androidapp.patch;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import vn.com.bravesoft.androidapp.model.ForceUpdateDTO;

/**
 * 公式アプリの強制アップデートダイアログをスキップし、控えめに通知する。
 *
 * <p>{@code domico-patch} 版は再署名されているため Google Play からの直接更新が
 * できない。公式サーバー ({@code GET v1/settings}) が強制アップデートを要求した際に
 * そのままキャンセル不可のダイアログを出すと、Play ストアへ飛ばされて
 * {@code MainActivity.finish()} され、アプリが操作不能になる。
 *
 * <p>また {@code SplashFragment} は {@code MainActivity} が更新確認後に投げる
 * {@code CheckUpdateEvent} / {@code PassUpdateEvent} を待って画面遷移するため、
 * {@code AppModelView.forceUpdate()} 自体を止めるとスプラッシュ画面で停止する。
 * そこで {@code MainActivity} の {@code onForceUpdateApp} 観測メソッド内で
 * バージョン比較が成立した直後に {@link #shouldSkip(Activity, ForceUpdateDTO)} を呼び、
 * スキップ有効時は公式の通常通過パスへジャンプさせてイベント配信を維持する。
 */
public final class PatchForceUpdate {

    /** 現プロセス内で検知した強制要求先の公式バージョン文字列 (未検知時は null)。 */
    private static volatile String detectedVersion;

    /** 現プロセス内で控えめな Toast 通知を既に出したかどうか。 */
    private static volatile boolean toastShown;

    private PatchForceUpdate() {
    }

    /**
     * 強制アップデート判定成立時に {@code MainActivity} から呼ばれるゲート。
     *
     * @return {@code true} なら公式ダイアログを出さず通常通過パスへ合流させる。
     *         {@code false} なら公式の強制アップデートダイアログを表示させる。
     */
    public static boolean shouldSkip(Activity activity, ForceUpdateDTO dto) {
        try {
            String ver = null;
            if (dto != null) {
                String raw = dto.getAndroidVersion();
                if (raw != null) {
                    String trimmed = raw.trim();
                    if (!trimmed.isEmpty()) {
                        ver = trimmed;
                    }
                }
            }
            if (ver != null) {
                detectedVersion = ver;
            }
            if (!PatchPrefs.forceUpdateSkip) {
                return false;
            }
            if (!toastShown && activity != null && !activity.isFinishing()) {
                toastShown = true;
                Context ctx = activity.getApplicationContext();
                if (ctx == null) {
                    ctx = activity;
                }
                String msg = (ver != null)
                        ? "公式アプリの新バージョン (v" + ver + ") が公開されています（強制更新をスキップ中）"
                        : "公式アプリの新バージョンが公開されています（強制更新をスキップ中）";
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
            }
            return true;
        } catch (Throwable ignored) {
            // 通知表示等で失敗しても、設定が ON であれば操作不能回避を優先する。
            return PatchPrefs.forceUpdateSkip;
        }
    }

    /**
     * 現プロセス内で強制アップデート要求を検知していれば、その公式バージョン名を返す。
     * 未検知時は {@code null}。
     */
    public static String getDetectedVersion() {
        return detectedVersion;
    }
}
