package vn.com.bravesoft.androidapp.patch;

import android.app.Dialog;
import android.view.View;

/** 設定画面ヘッダの「←」に割り当てる、ダイアログを閉じるだけのリスナ。 */
public final class PatchDialogBackListener implements View.OnClickListener {

    private final Dialog dialog;

    public PatchDialogBackListener(Dialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void onClick(View v) {
        dialog.dismiss();
    }
}
