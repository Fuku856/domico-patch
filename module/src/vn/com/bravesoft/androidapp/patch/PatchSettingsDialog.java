package vn.com.bravesoft.androidapp.patch;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * パッチ設定画面。XML も新規 Activity も使わず、コードだけで組み立てた
 * 全画面 Dialog。
 *
 * <p>ヘッダはアプリのテーマ色(#FFDD00)のバーに「←」と「パッチ設定」。
 * 本体はパッチ1件につき1行(スイッチ + 名前 + 説明)で、行間に細い区切り線。
 * フッタにパッチ版とクレジット。物理バックキーは Dialog 既定で閉じる。
 */
public final class PatchSettingsDialog {

    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_BLACK = 0xFF000000;
    private static final int COLOR_THEME_YELLOW = 0xFFFFDD00;
    private static final int COLOR_BLACK_WOOD = 0xFF232A37;
    private static final int COLOR_DESC_GRAY = 0xFF888888;
    private static final int COLOR_SEPARATOR = 0xFFE0E0E0;
    private static final int COLOR_ROW_SEPARATOR = 0xFFE8E8E8;

    private static int dp(Context ctx, int value) {
        return (int) (ctx.getResources().getDisplayMetrics().density * value);
    }

    /**
     * 設定行を1つ追加する。行コンテナと、その下の区切り線の2つが親に足される。
     *
     * @return 追加した行コンテナ (子スイッチは index 1)
     */
    private static LinearLayout addRow(
            Context ctx, LinearLayout parent, String title, String desc, String key) {
        // 外枠: 横並び, 中央揃え, 16dp 均等パディング
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(ctx, 16);
        row.setPadding(pad, pad, pad, pad);

        // テキスト列 (縦並び, weight=1)
        LinearLayout textColumn = new LinearLayout(ctx);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f));

        TextView name = new TextView(ctx);
        name.setText(title);
        name.setTextColor(COLOR_BLACK);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.0f);
        textColumn.addView(name);

        TextView description = new TextView(ctx);
        description.setText(desc);
        description.setTextColor(COLOR_DESC_GRAY);
        description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.0f);
        textColumn.addView(description);

        // スイッチ: キーごとの既定値は PatchPrefs.defaultOf で決まる
        Switch toggle = new Switch(ctx);
        toggle.setChecked(PatchPrefs.get(ctx, key, PatchPrefs.defaultOf(key)));
        toggle.setOnCheckedChangeListener(new PatchSwitchListener(ctx, key));

        row.addView(textColumn);
        row.addView(toggle);
        parent.addView(row, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // 行区切り線 (1px, 左マージン 16dp)
        View separator = new View(ctx);
        separator.setBackgroundColor(COLOR_ROW_SEPARATOR);
        LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(MATCH_PARENT, 1);
        sepParams.leftMargin = dp(ctx, 16);
        separator.setLayoutParams(sepParams);
        parent.addView(separator);

        return row;
    }

    public static void show(Context ctx) {
        if (ctx == null) {
            return;
        }

        Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_WHITE);

        // --- ヘッダーバー (高さ 56dp, テーマ色) ---
        int headerHeight = dp(ctx, 56);
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(COLOR_THEME_YELLOW);
        header.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, headerHeight));

        TextView back = new TextView(ctx);
        back.setText("←");
        back.setTextColor(COLOR_BLACK_WOOD);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24.0f);
        back.setClickable(true);
        back.setGravity(Gravity.CENTER);
        back.setLayoutParams(new LinearLayout.LayoutParams(headerHeight, headerHeight));
        back.setOnClickListener(new PatchDialogBackListener(dialog));

        TextView title = new TextView(ctx);
        title.setText("パッチ設定");
        title.setTextColor(COLOR_BLACK_WOOD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.0f);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH_PARENT, 1.0f));

        // 「←」と左右対称にするための右スペーサー
        View headerSpacer = new View(ctx);
        headerSpacer.setLayoutParams(new LinearLayout.LayoutParams(headerHeight, headerHeight));

        header.addView(back);
        header.addView(title);
        header.addView(headerSpacer);
        root.addView(header);

        View headerSeparator = new View(ctx);
        headerSeparator.setBackgroundColor(COLOR_SEPARATOR);
        headerSeparator.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, 1));
        root.addView(headerSeparator);

        // --- 設定行 ---
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        int contentPad = dp(ctx, 8);
        content.setPadding(contentPad, contentPad, contentPad, contentPad);

        addRow(ctx, content,
                "ログイントースト クリックスルー",
                "アプリ起動後の、自動ログイントースト表示中も画面を操作できるように。",
                PatchPrefs.KEY_TOAST);
        addRow(ctx, content,
                "ロード中の操作を許可",
                "画面遷移・取得ロード中も操作を可能に。（送信中は二重送信防止のため遮断）",
                PatchPrefs.KEY_LOADING);
        addRow(ctx, content,
                "テレメトリ送信をブロック",
                "Firebase Analytics / Crashlytics / Performance と広告 ID の送信をブロック。（アプリ通知は維持）",
                PatchPrefs.KEY_TELEMETRY);
        addRow(ctx, content,
                "時間外チェックイン",
                "受付時間外でもチェックインボタンを押せるようにし、確認後にチェックイン。（既定オフ）",
                PatchPrefs.KEY_CHECKIN);
        LinearLayout autoCheckinRow = addRow(ctx, content,
                "開始時間に自動チェックイン (ハイリスク)",
                "時間外確認後、チェックイン開始時間になったら自動送信。ボーリングするため、"
                        + "サーバー側に発覚するリスクがあります。（既定オフ）",
                PatchPrefs.KEY_AUTO_CHECKIN);

        // 親 (時間外チェックイン) が OFF なら子スイッチをグレーアウトして無効化
        if (!PatchPrefs.checkinEnabled) {
            autoCheckinRow.setAlpha(0.5f);
            View toggle = autoCheckinRow.getChildAt(1);
            if (toggle != null) {
                toggle.setEnabled(false);
            }
        }

        addRow(ctx, content,
                "受信メッセージをファイルに保存 (デバッグ)",
                "受信した通知の全内容を端末内ファイルに追記保存します(本文の調査用)。"
                        + "保存先はアプリの外部データ領域。（既定オフ）",
                PatchPrefs.KEY_PUSH_DEBUG);

        // --- フッター ---
        View footerSeparator = new View(ctx);
        footerSeparator.setBackgroundColor(COLOR_SEPARATOR);
        LinearLayout.LayoutParams footerSepParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, 1);
        footerSepParams.topMargin = dp(ctx, 12);
        footerSeparator.setLayoutParams(footerSepParams);
        content.addView(footerSeparator);

        TextView credit = new TextView(ctx);
        credit.setText(PatchInfo.CREDIT);
        credit.setTextColor(COLOR_BLACK);
        credit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.0f);
        credit.setTypeface(null, Typeface.BOLD);
        credit.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams creditParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        creditParams.topMargin = dp(ctx, 12);
        credit.setLayoutParams(creditParams);
        content.addView(credit);

        TextView copyright = new TextView(ctx);
        copyright.setText(PatchInfo.COPYRIGHT + "\n" + PatchInfo.VERSION);
        copyright.setTextColor(COLOR_BLACK);
        copyright.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.0f);
        copyright.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams copyrightParams =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        copyrightParams.topMargin = dp(ctx, 4);
        copyright.setLayoutParams(copyrightParams);
        content.addView(copyright);

        View bottomSpacer = new View(ctx);
        bottomSpacer.setLayoutParams(
                new LinearLayout.LayoutParams(MATCH_PARENT, dp(ctx, 24)));
        content.addView(bottomSpacer);

        // --- スクロール領域として残り高さを全て使う ---
        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(content);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1.0f));
        root.addView(scroll);

        dialog.setContentView(root);
        dialog.show();

        // 全画面化: 白背景 + MATCH_PARENT x MATCH_PARENT
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(COLOR_WHITE));
        window.setLayout(MATCH_PARENT, MATCH_PARENT);
    }
}
