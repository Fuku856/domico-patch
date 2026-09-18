package vn.com.bravesoft.androidapp.patch;

import android.content.Context;
import android.widget.CompoundButton;

/** 設定画面のトグル1個ぶんの変更を {@link PatchPrefs} へ永続化する。 */
public final class PatchSwitchListener implements CompoundButton.OnCheckedChangeListener {

    final Context ctx;
    final String key;

    PatchSwitchListener(Context ctx, String key) {
        this.ctx = ctx;
        this.key = key;
    }

    @Override
    public void onCheckedChanged(CompoundButton button, boolean checked) {
        PatchPrefs.set(ctx, key, checked);
    }
}
