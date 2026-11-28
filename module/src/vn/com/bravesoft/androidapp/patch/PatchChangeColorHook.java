package vn.com.bravesoft.androidapp.patch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import vn.com.bravesoft.androidapp.databinding.DialogChangeThemeColorBinding;
import vn.com.bravesoft.androidapp.helper.UserCtrl;
import vn.com.bravesoft.androidapp.ui.ChangeColorDialog;
import vn.com.bravesoft.androidapp.utils.ThemeUtils;

/**
 * 公式の「カラー変更」ダイアログ ({@code ChangeColorDialog}) へ 5つ目の
 * 「カスタムカラー」スウォッチを統合し、プリセット4色と双方向連動させるフック。
 */
public final class PatchChangeColorHook {

    private static final String TAG = "domico-patch";
    private static final String TAG_CUSTOM_SWATCH = "domico_custom_color_swatch";

    private PatchChangeColorHook() {
    }

    static int dp(Context ctx, float value) {
        return Math.round(ctx.getResources().getDisplayMetrics().density * value);
    }

    public static void install(
            final ChangeColorDialog dialog,
            final DialogChangeThemeColorBinding binding) {
        if (dialog == null || binding == null || binding.containerColor == null) {
            return;
        }
        try {
            final Context ctx = dialog.getContext();
            if (ctx == null) {
                return;
            }
            if (binding.containerColor.findViewWithTag(TAG_CUSTOM_SWATCH) != null) {
                return;
            }

            final UserCtrl userCtrl = dialog.getUserCtrl();
            if (userCtrl == null) {
                return;
            }

            // 公式スウォッチの実寸を取得（40dp 相当）。5個横並びでも小画面にはみ出さないよう
            // 水平マージンを 10dp -> 7dp に整える。
            int swatchW = dp(ctx, 40);
            int swatchH = dp(ctx, 40);
            Drawable refBg = binding.btnYellow != null ? binding.btnYellow.getBackground() : null;
            if (refBg != null && refBg.getIntrinsicWidth() > 0 && refBg.getIntrinsicHeight() > 0) {
                swatchW = refBg.getIntrinsicWidth();
                swatchH = refBg.getIntrinsicHeight();
            }

            int hMargin = dp(ctx, 7);
            FrameLayout[] presets = {
                    binding.btnYellow, binding.btnPink, binding.btnGreen, binding.btnBlue
            };
            for (FrameLayout btn : presets) {
                if (btn != null && btn.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) btn.getLayoutParams();
                    lp.leftMargin = hMargin;
                    lp.rightMargin = hMargin;
                    btn.setLayoutParams(lp);
                }
            }

            // --- 5つ目のカスタムカラースウォッチ ---
            final FrameLayout btnCustom = new FrameLayout(ctx);
            btnCustom.setTag(TAG_CUSTOM_SWATCH);
            PatchColorPickerView.disableForceDark(btnCustom);
            LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(swatchW, swatchH);
            customLp.leftMargin = hMargin;
            customLp.rightMargin = hMargin;

            // 未設定時の「＋」ラベル
            final TextView plusLabel = new TextView(ctx);
            plusLabel.setText("+");
            plusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
            plusLabel.setTypeface(null, Typeface.BOLD);
            plusLabel.setTextColor(0xFFFFFFFF);
            plusLabel.setShadowLayer(dp(ctx, 2), 0f, dp(ctx, 1), 0x66000000);
            plusLabel.setGravity(Gravity.CENTER);
            btnCustom.addView(plusLabel, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 選択中チェックマーク（公式 cbYellow の Drawable を複製してデザインを統一）
            final ImageView cbCustom = new ImageView(ctx);
            Drawable refCheck = (binding.cbYellow != null && binding.cbYellow.getDrawable() != null)
                    ? binding.cbYellow.getDrawable()
                    : null;
            if (refCheck != null && refCheck.getConstantState() != null) {
                cbCustom.setImageDrawable(refCheck.getConstantState().newDrawable().mutate());
            }
            cbCustom.setVisibility(View.GONE);
            FrameLayout.LayoutParams cbLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cbLp.gravity = Gravity.CENTER;
            btnCustom.addView(cbCustom, cbLp);

            // フォールバック用チェック文字（万一 Drawable が複製できない端末向け）
            final TextView checkFallback = new TextView(ctx);
            checkFallback.setText("✓");
            checkFallback.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            checkFallback.setTypeface(null, Typeface.BOLD);
            checkFallback.setGravity(Gravity.CENTER);
            checkFallback.setVisibility(View.GONE);
            btnCustom.addView(checkFallback, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            binding.containerColor.addView(btnCustom, customLp);

            // --- 選択状態管理 ---
            final int[] selectedThemeId = {
                    PatchPrefs.customThemeEnabled
                            ? PatchThemeColor.THEME_ID_CUSTOM
                            : userCtrl.getThemeId()
            };
            final int[] pendingCustomColor = {PatchPrefs.customThemeColor};
            final boolean[] hasCustomColor = {
                    PatchPrefs.customThemeEnabled || PatchPrefs.hasSavedCustomColor
            };
            PatchThemeColor.setPreviewColor(pendingCustomColor[0]);

            final PatchColorPickerView.RainbowCircleDrawable rainbowDrawable =
                    new PatchColorPickerView.RainbowCircleDrawable(ctx);

            final Runnable refreshUi = new Runnable() {
                @Override
                public void run() {
                    int id = selectedThemeId[0];
                    setVisible(binding.cbYellow, id == 0);
                    setVisible(binding.cbPink, id == 1);
                    setVisible(binding.cbGreen, id == 2);
                    setVisible(binding.cbBlue, id == 3);

                    boolean isCustomSelected = (id == PatchThemeColor.THEME_ID_CUSTOM);
                    if (isCustomSelected || hasCustomColor[0]) {
                        int c = 0xFF000000 | pendingCustomColor[0];
                        GradientDrawable solid = new GradientDrawable();
                        solid.setShape(GradientDrawable.OVAL);
                        solid.setColor(c);
                        solid.setStroke(dp(ctx, 1.5f), 0x26000000);
                        btnCustom.setBackground(solid);
                        plusLabel.setVisibility(isCustomSelected ? View.GONE : View.VISIBLE);
                        int onC = PatchThemeColor.onPrimaryColor(c);
                        plusLabel.setTextColor(onC);
                        plusLabel.setShadowLayer(0f, 0f, 0f, 0);
                        if (isCustomSelected) {
                            if (cbCustom.getDrawable() != null) {
                                cbCustom.getDrawable().setColorFilter(onC, PorterDuff.Mode.SRC_IN);
                                cbCustom.setVisibility(View.VISIBLE);
                                checkFallback.setVisibility(View.GONE);
                            } else {
                                cbCustom.setVisibility(View.GONE);
                                checkFallback.setTextColor(onC);
                                checkFallback.setVisibility(View.VISIBLE);
                            }
                        } else {
                            cbCustom.setVisibility(View.GONE);
                            checkFallback.setVisibility(View.GONE);
                        }
                    } else {
                        btnCustom.setBackground(rainbowDrawable);
                        plusLabel.setTextColor(0xFFFFFFFF);
                        plusLabel.setShadowLayer(dp(ctx, 2), 0f, dp(ctx, 1), 0x66000000);
                        plusLabel.setVisibility(View.VISIBLE);
                        cbCustom.setVisibility(View.GONE);
                        checkFallback.setVisibility(View.GONE);
                    }

                    // 保存ボタンのプレビュー色を更新
                    if (binding.btnSave != null) {
                        int btnBg = ThemeUtils.INSTANCE.getBtnBackground(ctx, id);
                        int btnFg = ThemeUtils.INSTANCE.buttonTextColorV2(ctx, id);
                        binding.btnSave.setBackgroundTintList(ColorStateList.valueOf(btnBg));
                        binding.btnSave.setTextColor(btnFg);
                        PatchColorPickerView.disableForceDark(binding.btnSave);
                    }
                }
            };

            // 公式4色スウォッチのクリックを連動
            final int[] presetIds = {0, 1, 2, 3};
            for (int i = 0; i < presets.length; i++) {
                final FrameLayout pBtn = presets[i];
                final int pid = presetIds[i];
                if (pBtn != null) {
                    pBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectedThemeId[0] = pid;
                            refreshUi.run();
                        }
                    });
                }
            }

            // 5つ目のカスタムスウォッチ: タップでカラーピッカーを開く
            btnCustom.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PatchColorPickerDialog.show(
                            ctx,
                            pendingCustomColor[0],
                            false,
                            "選択",
                            new PatchColorPickerDialog.Callback() {
                                @Override
                                public void onColorSelected(int color) {
                                    pendingCustomColor[0] = color;
                                    hasCustomColor[0] = true;
                                    selectedThemeId[0] = PatchThemeColor.THEME_ID_CUSTOM;
                                    PatchThemeColor.setPreviewColor(color);
                                    refreshUi.run();
                                }

                                @Override
                                public void onResetToPreset() {
                                }
                            });
                }
            });

            // 保存ボタン: プリセット保存とカスタム保存の双方を統合処理
            if (binding.btnSave != null) {
                binding.btnSave.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int chosen = selectedThemeId[0];
                        if (chosen == PatchThemeColor.THEME_ID_CUSTOM) {
                            PatchPrefs.setCustomThemeColor(ctx, pendingCustomColor[0]);
                            PatchThemeColor.broadcastColorChanged();
                        } else {
                            boolean wasCustom = PatchPrefs.customThemeEnabled;
                            int prevPreset = userCtrl.getThemeId();
                            if (wasCustom) {
                                PatchPrefs.setCustomThemeEnabled(ctx, false);
                            }
                            if (chosen != prevPreset) {
                                userCtrl.setThemeId(chosen);
                            }
                            if (wasCustom || chosen != prevPreset) {
                                PatchThemeColor.broadcastColorChanged();
                            }
                        }
                        dialog.dismiss();
                    }
                });
            }

            refreshUi.run();
        } catch (Throwable t) {
            Log.w(TAG, "PatchChangeColorHook.install failed", t);
        }
    }

    static void setVisible(View v, boolean visible) {
        if (v != null) {
            v.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
}
