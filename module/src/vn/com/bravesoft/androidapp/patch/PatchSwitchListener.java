package vn.com.bravesoft.androidapp.patch;

import android.content.Context;
import android.widget.CompoundButton;

/**
 * 設定画面のトグル1個ぶんの変更を {@link PatchPrefs} へ永続化する。
 *
 * <p>他の行の表示を連動させたい場合は {@code afterChange} を渡す。保存が
 * 済んで static ミラーが更新されたあとに呼ばれる。
 */
public final class PatchSwitchListener implements CompoundButton.OnCheckedChangeListener {

    final Context ctx;
    final String key;
    private final Runnable afterChange;

    PatchSwitchListener(Context ctx, String key) {
        this(ctx, key, null);
    }

    PatchSwitchListener(Context ctx, String key, Runnable afterChange) {
        this.ctx = ctx;
        this.key = key;
        this.afterChange = afterChange;
    }

    @Override
    public void onCheckedChanged(CompoundButton button, boolean checked) {
        PatchPrefs.set(ctx, key, checked);
        if (afterChange != null) {
            afterChange.run();
        }
    }
}
