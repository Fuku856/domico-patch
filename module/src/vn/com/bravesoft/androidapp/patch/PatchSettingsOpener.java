package vn.com.bravesoft.androidapp.patch;

import android.view.View;

/**
 * 設定画面を開く導線。クリックでも長押しでも開く。
 *
 * <p>状態を持たないシングルトン。{@code INSTANCE} は classes4 に残る
 * PatchSettingsEntry から {@code sget-object} で参照されるため、
 * フィールド名と型を変えないこと。
 */
public final class PatchSettingsOpener implements View.OnClickListener, View.OnLongClickListener {

    public static final PatchSettingsOpener INSTANCE = new PatchSettingsOpener();

    @Override
    public void onClick(View v) {
        PatchSettingsDialog.show(v.getContext());
    }

    @Override
    public boolean onLongClick(View v) {
        PatchSettingsDialog.show(v.getContext());
        return true;
    }
}
