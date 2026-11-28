package vn.com.bravesoft.androidapp.patch;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * 任意のテーマカラーを選択するカラーピッカーダイアログ。
 *
 * <p>2D 彩度×明度パレット・色相バー・厳選クイックスウォッチ・HEX双方向入力に加え、
 * 実際の画面背景色（極薄パステル）とプライマリボタン・自動文字色のリアルタイム
 * プレビューを上部に備える。
 */
public final class PatchColorPickerDialog {

    public interface Callback {
        void onColorSelected(int color);

        void onResetToPreset();
    }

    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;

    /** ワンタップで呼び出せる厳選アクセントカラー10色。 */
    private static final int[] QUICK_SWATCHES = {
            0xFFFFDD00, // Domico Yellow
            0xFFFF7043, // Coral Orange
            0xFFF9848A, // Domico Pink
            0xFFEC407A, // Rose
            0xFFAB47BC, // Violet
            0xFF5C6BC0, // Indigo
            0xFF1E88E5, // Ocean Blue
            0xFF26A69A, // Teal
            0xFF97C85B, // Domico Green
            0xFF546E7A, // Slate Blue-Grey
    };

    private PatchColorPickerDialog() {
    }

    static int dp(Context ctx, float value) {
        return Math.round(ctx.getResources().getDisplayMetrics().density * value);
    }

    static GradientDrawable roundedRect(int fillColor, float radiusDp,
            int strokePx, int strokeColor, Context ctx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(ctx.getResources().getDisplayMetrics().density * radiusDp);
        gd.setColor(fillColor);
        if (strokePx > 0) {
            gd.setStroke(strokePx, strokeColor);
        }
        return gd;
    }

    static GradientDrawable circleDrawable(int fillColor, int strokePx, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(fillColor);
        if (strokePx > 0) {
            gd.setStroke(strokePx, strokeColor);
        }
        return gd;
    }

    /**
     * カラーピッカーダイアログを表示する。
     *
     * @param ctx             表示元の Context (Activity Context)
     * @param initialColor    初期表示するカラー (0xFFRRGGBB)
     * @param showResetButton 「プリセットに戻す」ボタンを出すか
     * @param confirmLabel    確定ボタンの文言 (例: "適用" / "選択")
     * @param callback        結果コールバック
     */
    public static void show(
            final Context ctx,
            final int initialColor,
            final boolean showResetButton,
            final String confirmLabel,
            final Callback callback) {
        if (ctx == null || callback == null) {
            return;
        }

        final Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final int startColor = 0xFF000000 | initialColor;
        final float[] hsv = new float[3];
        Color.colorToHSV(startColor, hsv);
        final int[] currentColor = {startColor};
        final boolean[] suppressHexWatcher = {false};

        // --- 外枠カード (角丸 24dp) ---
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(ctx, 20);
        int padV = dp(ctx, 20);
        card.setPadding(padH, padV, padH, padV);
        card.setBackground(roundedRect(0xFFFFFFFF, 24f, dp(ctx, 1), 0x18000000, ctx));
        card.setClipChildren(false);
        card.setClipToPadding(false);

        // タイトル
        TextView title = new TextView(ctx);
        title.setText("テーマカラーを選択");
        title.setTextColor(PatchThemeColor.COLOR_DARK);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // --- リアルタイム UI プレビューカード ---
        final LinearLayout previewContainer = new LinearLayout(ctx);
        previewContainer.setOrientation(LinearLayout.HORIZONTAL);
        previewContainer.setGravity(Gravity.CENTER_VERTICAL);
        int prevPad = dp(ctx, 12);
        previewContainer.setPadding(prevPad, prevPad, prevPad, prevPad);
        PatchColorPickerView.disableForceDark(previewContainer);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        prevLp.topMargin = dp(ctx, 14);
        card.addView(previewContainer, prevLp);

        // 左: ボタン表示プレビュー
        final TextView previewBtn = new TextView(ctx);
        previewBtn.setText("チェックイン / 保存");
        previewBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        previewBtn.setTypeface(null, Typeface.BOLD);
        previewBtn.setGravity(Gravity.CENTER);
        previewBtn.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
        PatchColorPickerView.disableForceDark(previewBtn);
        LinearLayout.LayoutParams previewBtnLp =
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
        previewBtnLp.rightMargin = dp(ctx, 10);
        previewContainer.addView(previewBtn, previewBtnLp);

        // 右: 現在色 / 元の色 比較チップ (下段タップで元の色へ戻せる)
        LinearLayout compareCol = new LinearLayout(ctx);
        compareCol.setOrientation(LinearLayout.VERTICAL);
        compareCol.setGravity(Gravity.CENTER_HORIZONTAL);
        PatchColorPickerView.disableForceDark(compareCol);

        final View nowChip = new View(ctx);
        PatchColorPickerView.disableForceDark(nowChip);
        compareCol.addView(nowChip, new LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 18)));

        final View origChip = new View(ctx);
        PatchColorPickerView.disableForceDark(origChip);
        origChip.setBackground(roundedRect(startColor, 4f, dp(ctx, 1), 0x33000000, ctx));
        LinearLayout.LayoutParams origChipLp =
                new LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 18));
        origChipLp.topMargin = dp(ctx, 3);
        compareCol.addView(origChip, origChipLp);
        previewContainer.addView(compareCol,
                new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        // 暗すぎる色を選んだときの控えめなヒントラベル
        final TextView contrastHint = new TextView(ctx);
        contrastHint.setText("※真っ黒に近い色はダークモード時に背景と重なりやすくなります");
        contrastHint.setTextColor(0xFF888888);
        contrastHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        contrastHint.setVisibility(View.GONE);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        hintLp.topMargin = dp(ctx, 4);
        card.addView(contrastHint, hintLp);

        // --- 2D カラーパレット (S × V) ---
        final PatchColorPickerView.SvBoxView svBox = new PatchColorPickerView.SvBoxView(ctx);
        svBox.setHsv(hsv[0], hsv[1], hsv[2]);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(ctx, 176));
        svLp.topMargin = dp(ctx, 12);
        card.addView(svBox, svLp);

        // --- 色相 (Hue) スライダー ---
        final PatchColorPickerView.HueBarView hueBar = new PatchColorPickerView.HueBarView(ctx);
        hueBar.setHue(hsv[0]);
        LinearLayout.LayoutParams hueLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(ctx, 40));
        hueLp.topMargin = dp(ctx, 8);
        card.addView(hueBar, hueLp);

        // --- クイックスウォッチ行 ---
        HorizontalScrollView swatchScroll = new HorizontalScrollView(ctx);
        swatchScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams swatchScrollLp =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        swatchScrollLp.topMargin = dp(ctx, 8);

        LinearLayout swatchRow = new LinearLayout(ctx);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setGravity(Gravity.CENTER_VERTICAL);
        swatchRow.setPadding(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 2));
        swatchScroll.addView(swatchRow, new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        card.addView(swatchScroll, swatchScrollLp);

        // --- HEX 入力行 ---
        LinearLayout hexRow = new LinearLayout(ctx);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hexRowLp =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        hexRowLp.topMargin = dp(ctx, 12);
        card.addView(hexRow, hexRowLp);

        TextView hexLabel = new TextView(ctx);
        hexLabel.setText("HEX  #");
        hexLabel.setTextColor(PatchThemeColor.COLOR_DARK);
        hexLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        hexLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hexRow.addView(hexLabel, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        final EditText hexInput = new EditText(ctx);
        hexInput.setSingleLine(true);
        hexInput.setMaxLines(1);
        hexInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hexInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        hexInput.setTextColor(PatchThemeColor.COLOR_BLACK);
        hexInput.setHint("FFDD00");
        hexInput.setHintTextColor(0xFFAAAAAA);
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hexInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        hexInput.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6));
        hexInput.setBackground(roundedRect(0xFFF5F6F8, 8f, dp(ctx, 1), 0xFFD8DCE2, ctx));
        hexInput.setFilters(new InputFilter[] {new HexCharFilter()});
        LinearLayout.LayoutParams hexInputLp =
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
        hexInputLp.leftMargin = dp(ctx, 4);
        hexInputLp.rightMargin = dp(ctx, 8);
        hexRow.addView(hexInput, hexInputLp);

        TextView copyBtn = new TextView(ctx);
        copyBtn.setText("コピー");
        copyBtn.setTextColor(PatchThemeColor.COLOR_DARK);
        copyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        copyBtn.setTypeface(null, Typeface.BOLD);
        copyBtn.setGravity(Gravity.CENTER);
        copyBtn.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
        copyBtn.setBackground(roundedRect(0xFFEEEEEE, 8f, 0, 0, ctx));
        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String hex = PatchThemeColor.formatHex(currentColor[0]);
                ClipboardManager cm =
                        (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("HEX Color", hex));
                    Toast.makeText(ctx, hex + " をコピーしました", Toast.LENGTH_SHORT).show();
                }
            }
        });
        hexRow.addView(copyBtn, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        // --- 下部ボタン行 ---
        LinearLayout actionRow = new LinearLayout(ctx);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionLp =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        actionLp.topMargin = dp(ctx, 18);
        card.addView(actionRow, actionLp);

        if (showResetButton) {
            TextView resetBtn = new TextView(ctx);
            resetBtn.setText("公式色に戻す");
            resetBtn.setTextColor(0xFFD93025);
            resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            resetBtn.setTypeface(null, Typeface.BOLD);
            resetBtn.setPadding(dp(ctx, 8), dp(ctx, 10), dp(ctx, 8), dp(ctx, 10));
            resetBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    callback.onResetToPreset();
                }
            });
            actionRow.addView(resetBtn, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        }

        View actionSpacer = new View(ctx);
        actionRow.addView(actionSpacer, new LinearLayout.LayoutParams(0, 1, 1.0f));

        TextView cancelBtn = new TextView(ctx);
        cancelBtn.setText("キャンセル");
        cancelBtn.setTextColor(0xFF555555);
        cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        cancelBtn.setTypeface(null, Typeface.BOLD);
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10));
        cancelBtn.setOnClickListener(new PatchDialogBackListener(dialog));
        actionRow.addView(cancelBtn, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        final Button okBtn = new Button(ctx);
        okBtn.setAllCaps(false);
        okBtn.setText(confirmLabel != null ? confirmLabel : "適用");
        okBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        okBtn.setTypeface(null, Typeface.BOLD);
        okBtn.setMinHeight(0);
        okBtn.setMinimumHeight(0);
        okBtn.setMinWidth(0);
        okBtn.setMinimumWidth(0);
        okBtn.setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), dp(ctx, 10));
        PatchColorPickerView.disableForceDark(okBtn);
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        okLp.leftMargin = dp(ctx, 6);
        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                callback.onColorSelected(0xFF000000 | currentColor[0]);
            }
        });
        actionRow.addView(okBtn, okLp);

        // --- 色更新の同期処理 ---
        final Runnable syncPreviewAndButtons = new Runnable() {
            @Override
            public void run() {
                int c = 0xFF000000 | currentColor[0];
                int onC = PatchThemeColor.onPrimaryColor(c);
                int screenBg = PatchThemeColor.screenBackgroundColor(c);

                previewContainer.setBackground(
                        roundedRect(screenBg, 12f, dp(ctx, 1), 0x22000000, ctx));
                previewBtn.setBackground(roundedRect(c, 8f, dp(ctx, 1), 0x18000000, ctx));
                previewBtn.setTextColor(onC);
                nowChip.setBackground(roundedRect(c, 4f, dp(ctx, 1), 0x33000000, ctx));

                okBtn.setBackground(roundedRect(c, 10f, dp(ctx, 1), 0x22000000, ctx));
                okBtn.setTextColor(onC);

                double[] lab = PatchDarkTint.toLab(c);
                contrastHint.setVisibility(lab[0] < 12.0 ? View.VISIBLE : View.GONE);
            }
        };

        final Runnable syncHexText = new Runnable() {
            @Override
            public void run() {
                suppressHexWatcher[0] = true;
                String hex6 = String.format(Locale.US, "%06X", currentColor[0] & 0x00FFFFFF);
                hexInput.setText(hex6);
                hexInput.setSelection(hex6.length());
                suppressHexWatcher[0] = false;
            }
        };

        // クイックスウォッチ生成
        int swatchSize = dp(ctx, 28);
        int swatchMargin = dp(ctx, 5);
        for (int i = 0; i < QUICK_SWATCHES.length; i++) {
            final int sc = QUICK_SWATCHES[i];
            View sw = new View(ctx);
            PatchColorPickerView.disableForceDark(sw);
            sw.setBackground(circleDrawable(sc, dp(ctx, 1), 0x33000000));
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(swatchSize, swatchSize);
            if (i > 0) {
                swLp.leftMargin = swatchMargin;
            }
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentColor[0] = sc;
                    Color.colorToHSV(sc, hsv);
                    svBox.setHsv(hsv[0], hsv[1], hsv[2]);
                    hueBar.setHue(hsv[0]);
                    syncHexText.run();
                    syncPreviewAndButtons.run();
                }
            });
            swatchRow.addView(sw, swLp);
        }

        // 元の色チップをタップで初期色へ戻す
        origChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentColor[0] = startColor;
                Color.colorToHSV(startColor, hsv);
                svBox.setHsv(hsv[0], hsv[1], hsv[2]);
                hueBar.setHue(hsv[0]);
                syncHexText.run();
                syncPreviewAndButtons.run();
            }
        });

        svBox.setListener(new PatchColorPickerView.SvBoxView.Listener() {
            @Override
            public void onSvChanged(float sat, float val, boolean fromUser) {
                hsv[1] = sat;
                hsv[2] = val;
                currentColor[0] = Color.HSVToColor(hsv);
                syncHexText.run();
                syncPreviewAndButtons.run();
            }
        });

        hueBar.setListener(new PatchColorPickerView.HueBarView.Listener() {
            @Override
            public void onHueChanged(float hue, boolean fromUser) {
                hsv[0] = hue;
                svBox.setHue(hue);
                currentColor[0] = Color.HSVToColor(hsv);
                syncHexText.run();
                syncPreviewAndButtons.run();
            }
        });

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressHexWatcher[0] || s == null) {
                    return;
                }
                String raw = s.toString().trim();
                if (raw.length() == 6) {
                    try {
                        int parsed = 0xFF000000 | Integer.parseInt(raw, 16);
                        currentColor[0] = parsed;
                        Color.colorToHSV(parsed, hsv);
                        svBox.setHsv(hsv[0], hsv[1], hsv[2]);
                        hueBar.setHue(hsv[0]);
                        syncPreviewAndButtons.run();
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        });

        // 3桁短縮 HEX (例: "F80" -> "FF8800") で IME 確定された場合の展開対応
        hexInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                String raw = v.getText() != null ? v.getText().toString().trim() : "";
                if (raw.length() == 3) {
                    String expanded = "" + raw.charAt(0) + raw.charAt(0)
                            + raw.charAt(1) + raw.charAt(1)
                            + raw.charAt(2) + raw.charAt(2);
                    hexInput.setText(expanded.toUpperCase(Locale.US));
                    hexInput.setSelection(expanded.length());
                }
                return false;
            }
        });

        syncHexText.run();
        syncPreviewAndButtons.run();

        // 小画面・横向き・キーボード表示時も全体へアクセスできるよう ScrollView で包む
        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        int outerMargin = dp(ctx, 18);
        scroll.setPadding(outerMargin, outerMargin, outerMargin, outerMargin);
        scroll.setClipToPadding(false);
        scroll.addView(card, new ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        dialog.setContentView(scroll);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(MATCH_PARENT, WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    /**
     * 16進文字 (0-9, A-F) のみを受け入れ、"#FF5500" のようなペースト時は先頭 "#" や空白を
     * 自動除去して最大6文字に収める InputFilter。
     */
    static final class HexCharFilter implements InputFilter {
        HexCharFilter() {
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                Spanned dest, int dstart, int dend) {
            StringBuilder cleaned = new StringBuilder();
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                    cleaned.append(Character.toUpperCase(c));
                }
            }
            int keep = 6 - (dest.length() - (dend - dstart));
            if (keep <= 0) {
                return "";
            }
            if (keep >= cleaned.length()) {
                return cleaned.toString();
            }
            return cleaned.subSequence(0, keep).toString();
        }
    }
}
