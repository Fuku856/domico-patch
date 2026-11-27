package vn.com.bravesoft.androidapp.patch;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.TextView;

/**
 * 設定画面のダークモード行。行タップで3択ダイアログを開き、選んだ値を保存して
 * その場で全ウィンドウへ反映する (再起動不要)。
 */
public final class PatchDarkModeChoiceListener
        implements View.OnClickListener, DialogInterface.OnClickListener {

    /** index が PatchDarkMode.MODE_* と一致する。 */
    static final String[] LABELS = {"オフ", "端末設定に従う", "常にダーク"};

    private final Context ctx;
    private final TextView valueView;

    PatchDarkModeChoiceListener(Context ctx, TextView valueView) {
        this.ctx = ctx;
        this.valueView = valueView;
    }

    static int currentMode(Context ctx) {
        int mode = PatchPrefs.getInt(ctx, PatchPrefs.KEY_DARK_MODE, PatchDarkMode.MODE_OFF);
        return mode >= 0 && mode < LABELS.length ? mode : PatchDarkMode.MODE_OFF;
    }

    void refreshLabel() {
        valueView.setText(LABELS[currentMode(ctx)]);
    }

    @Override
    public void onClick(View v) {
        new AlertDialog.Builder(ctx)
                .setTitle("ダークモード")
                .setSingleChoiceItems(LABELS, currentMode(ctx), this)
                .setNegativeButton("キャンセル", null)
                .show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which >= 0 && which < LABELS.length) {
            PatchPrefs.setInt(ctx, PatchPrefs.KEY_DARK_MODE, which);
            refreshLabel();
            PatchDarkMode.onModeChanged();
        }
        dialog.dismiss();
    }
}
