package vn.com.bravesoft.androidapp.patch;

import android.app.Application;
import android.content.Context;
import android.util.Log;

/**
 * MyApplication.onCreate から呼ばれる唯一の入口。
 *
 * <p>設定の読み込み、テレメトリ停止、ロード入力ガード用の Activity トラッカ
 * 登録をまとめて行う。ここで落ちるとアプリが起動しなくなるため、全体を
 * catch して握り潰す。
 */
public final class PatchInit {

    public static void onAppCreate(Application app) {
        if (app == null) {
            return;
        }
        Log.i("domico-patch", "PatchInit.onAppCreate: enter");
        try {
            Context ctx = app.getApplicationContext();
            PatchPrefs.load(ctx);
            PatchTelemetry.apply(ctx);
            app.registerActivityLifecycleCallbacks(new PatchActivityTracker());
        } catch (Throwable ignored) {
            // 起動経路なので、失敗しても公式の起動処理は続行させる。
        }
    }
}
