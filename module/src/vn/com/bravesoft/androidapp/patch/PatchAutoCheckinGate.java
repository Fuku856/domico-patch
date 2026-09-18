package vn.com.bravesoft.androidapp.patch;

import android.view.View;
import android.view.ViewGroup;

/**
 * 「開始時間に自動チェックイン」行の見た目を、親である「時間外チェックイン」の
 * 状態に追従させる。
 *
 * <p>親が OFF のときは行を半透明にしてスイッチを無効化する。親トグルの
 * {@link PatchSwitchListener} から呼ばれるので、設定画面を開き直さなくても
 * 即座に反映される。
 */
public final class PatchAutoCheckinGate implements Runnable {

    private static final float ALPHA_ENABLED = 1.0f;
    private static final float ALPHA_DISABLED = 0.5f;

    private final ViewGroup row;

    PatchAutoCheckinGate(ViewGroup row) {
        this.row = row;
    }

    @Override
    public void run() {
        // PatchSwitchListener は PatchPrefs.set 経由で static ミラーを更新して
        // から呼ぶため、ここでは最新値が読める。
        boolean parentEnabled = PatchPrefs.checkinEnabled;
        row.setAlpha(parentEnabled ? ALPHA_ENABLED : ALPHA_DISABLED);
        View toggle = row.getChildAt(1);
        if (toggle != null) {
            // 子の保存値は触らない。親を戻したときに元の状態へ復帰させるため。
            toggle.setEnabled(parentEnabled);
        }
    }
}
