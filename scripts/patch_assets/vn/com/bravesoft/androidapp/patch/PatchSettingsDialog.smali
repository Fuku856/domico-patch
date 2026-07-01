.class public final Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;
.super Ljava/lang/Object;
.source "PatchSettingsDialog.java"

# domico-patch: full-screen settings screen (no XML / no new Activity).
# Header: yellow (#FFDD00) bar with "←" back button and "パッチ設定" title.
# Body: one row per patch (Switch + title + description) with 16dp padding and
# a thin separator line between rows. Footer shows the patch version and credit.
# Persists toggles through PatchPrefs. Physical back key dismisses via Dialog default.


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method private static addRow(Landroid/content/Context;Landroid/widget/LinearLayout;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    .locals 8

    # 外枠: 横並び (HORIZONTAL), 中央揃え, 16dp 均等パディング
    new-instance v0, Landroid/widget/LinearLayout;

    invoke-direct {v0, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroid/widget/LinearLayout;->setOrientation(I)V

    const/16 v2, 0x10    # Gravity.CENTER_VERTICAL = 16

    invoke-virtual {v0, v2}, Landroid/widget/LinearLayout;->setGravity(I)V

    const/16 v2, 0x10    # 16dp パディング

    invoke-static {p0, v2}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->dp(Landroid/content/Context;I)I

    move-result v2

    invoke-virtual {v0, v2, v2, v2, v2}, Landroid/view/View;->setPadding(IIII)V

    # テキスト列 (VERTICAL, weight=1)
    new-instance v3, Landroid/widget/LinearLayout;

    invoke-direct {v3, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V

    const/4 v4, 0x1

    invoke-virtual {v3, v4}, Landroid/widget/LinearLayout;->setOrientation(I)V

    new-instance v4, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v5, -0x2

    const/high16 v6, 0x3f800000    # 1.0f weight

    invoke-direct {v4, v1, v5, v6}, Landroid/widget/LinearLayout$LayoutParams;-><init>(IIF)V

    invoke-virtual {v3, v4}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    # パッチ名 (16sp, 黒)
    new-instance v4, Landroid/widget/TextView;

    invoke-direct {v4, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    invoke-virtual {v4, p2}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    const v6, -0x1000000    # 0xFF000000 black

    invoke-virtual {v4, v6}, Landroid/widget/TextView;->setTextColor(I)V

    const/4 v5, 0x2

    const/high16 v6, 0x41800000    # 16.0f

    invoke-virtual {v4, v5, v6}, Landroid/widget/TextView;->setTextSize(IF)V

    invoke-virtual {v3, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    # 説明文 (12sp, グレー)
    new-instance v4, Landroid/widget/TextView;

    invoke-direct {v4, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    invoke-virtual {v4, p3}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    const v6, -0x777778    # 0xFF888888 gray

    invoke-virtual {v4, v6}, Landroid/widget/TextView;->setTextColor(I)V

    const v6, 0x41400000    # 12.0f

    invoke-virtual {v4, v5, v6}, Landroid/widget/TextView;->setTextSize(IF)V

    invoke-virtual {v3, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    # スイッチ: キーごとの既定値を PatchPrefs.defaultOf で取得
    new-instance v4, Landroid/widget/Switch;

    invoke-direct {v4, p0}, Landroid/widget/Switch;-><init>(Landroid/content/Context;)V

    invoke-static {p4}, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->defaultOf(Ljava/lang/String;)Z

    move-result v6

    invoke-static {p0, p4, v6}, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->get(Landroid/content/Context;Ljava/lang/String;Z)Z

    move-result v6

    invoke-virtual {v4, v6}, Landroid/widget/CompoundButton;->setChecked(Z)V

    new-instance v6, Lvn/com/bravesoft/androidapp/patch/PatchSwitchListener;

    invoke-direct {v6, p0, p4}, Lvn/com/bravesoft/androidapp/patch/PatchSwitchListener;-><init>(Landroid/content/Context;Ljava/lang/String;)V

    invoke-virtual {v4, v6}, Landroid/widget/CompoundButton;->setOnCheckedChangeListener(Landroid/widget/CompoundButton$OnCheckedChangeListener;)V

    invoke-virtual {v0, v3}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    invoke-virtual {v0, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    # 行を親コンテナへ追加
    new-instance v1, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v5, -0x1

    const/4 v6, -0x2

    invoke-direct {v1, v5, v6}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    invoke-virtual {p1, v0, v1}, Landroid/view/ViewGroup;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V

    # --- 行区切り線 (1px, #E8E8E8, 左マージン 16dp) ---
    new-instance v0, Landroid/view/View;

    invoke-direct {v0, p0}, Landroid/view/View;-><init>(Landroid/content/Context;)V

    const v1, 0xFFE8E8E8

    invoke-virtual {v0, v1}, Landroid/view/View;->setBackgroundColor(I)V

    new-instance v1, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v2, -0x1    # MATCH_PARENT

    const/4 v3, 0x1     # 1px

    invoke-direct {v1, v2, v3}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    const/16 v2, 0x10   # 16

    invoke-static {p0, v2}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->dp(Landroid/content/Context;I)I

    move-result v2

    iput v2, v1, Landroid/view/ViewGroup$MarginLayoutParams;->leftMargin:I

    invoke-virtual {v0, v1}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    invoke-virtual {p1, v0}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    return-void
.end method

.method private static dp(Landroid/content/Context;I)I
    .locals 2

    invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    invoke-virtual {v0}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;

    move-result-object v0

    iget v0, v0, Landroid/util/DisplayMetrics;->density:F

    int-to-float v1, p1

    mul-float/2addr v0, v1

    float-to-int v0, v0

    return v0
.end method

.method public static show(Landroid/content/Context;)V
    .locals 12

    if-nez p0, :cond_0

    return-void

    :cond_0

    # --- Dialog 生成 + タイトルバー非表示 (Window.FEATURE_NO_TITLE = 1) ---
    new-instance v0, Landroid/app/Dialog;

    invoke-direct {v0, p0}, Landroid/app/Dialog;-><init>(Landroid/content/Context;)V

    const/4 v2, 0x1

    invoke-virtual {v0, v2}, Landroid/app/Dialog;->requestWindowFeature(I)Z

    move-result v2    # boolean 戻り値は破棄

    # --- ルートレイアウト (VERTICAL, 白背景) ---
    new-instance v1, Landroid/widget/LinearLayout;

    invoke-direct {v1, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V

    const/4 v2, 0x1

    invoke-virtual {v1, v2}, Landroid/widget/LinearLayout;->setOrientation(I)V

    const v2, 0xFFFFFFFF

    invoke-virtual {v1, v2}, Landroid/view/View;->setBackgroundColor(I)V

    # --- ヘッダーバー (HORIZONTAL, 高さ 56dp, 背景 #FFDD00) ---
    new-instance v2, Landroid/widget/LinearLayout;

    invoke-direct {v2, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V

    const/4 v3, 0x0

    invoke-virtual {v2, v3}, Landroid/widget/LinearLayout;->setOrientation(I)V

    const v3, 0x10    # Gravity.CENTER_VERTICAL

    invoke-virtual {v2, v3}, Landroid/widget/LinearLayout;->setGravity(I)V

    const v3, 0xFFFFDD00    # themeYellow

    invoke-virtual {v2, v3}, Landroid/view/View;->setBackgroundColor(I)V

    # ヘッダー高さ 56dp
    const/16 v3, 0x38

    invoke-static {p0, v3}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->dp(Landroid/content/Context;I)I

    move-result v3    # v3 = 56dp px

    # ヘッダー layout params: MATCH_PARENT × 56dp
    new-instance v4, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v5, -0x1

    invoke-direct {v4, v5, v3}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    invoke-virtual {v2, v4}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    # 「←」ボタン (56×56dp, 24sp, クリックで dismiss)
    new-instance v4, Landroid/widget/TextView;

    invoke-direct {v4, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    const-string v5, "←"

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    const v5, 0xFF232A37    # blackWood

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setTextColor(I)V

    const/4 v5, 0x2    # TypedValue.COMPLEX_UNIT_SP

    const/high16 v6, 0x41C00000    # 24.0f

    invoke-virtual {v4, v5, v6}, Landroid/widget/TextView;->setTextSize(IF)V

    const/4 v5, 0x1

    invoke-virtual {v4, v5}, Landroid/view/View;->setClickable(Z)V

    const v5, 0x11    # Gravity.CENTER

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setGravity(I)V

    # back button layout params: 56×56dp
    new-instance v5, Landroid/widget/LinearLayout$LayoutParams;

    invoke-direct {v5, v3, v3}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    invoke-virtual {v4, v5}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    # PatchDialogBackListener でダイアログを閉じる
    new-instance v5, Lvn/com/bravesoft/androidapp/patch/PatchDialogBackListener;

    invoke-direct {v5, v0}, Lvn/com/bravesoft/androidapp/patch/PatchDialogBackListener;-><init>(Landroid/app/Dialog;)V

    invoke-virtual {v4, v5}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    # タイトル「パッチ設定」(weight=1, 中央揃え, 18sp bold)
    new-instance v5, Landroid/widget/TextView;

    invoke-direct {v5, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    const-string v6, "パッチ設定"

    invoke-virtual {v5, v6}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    const v6, 0xFF232A37

    invoke-virtual {v5, v6}, Landroid/widget/TextView;->setTextColor(I)V

    const/4 v6, 0x2

    const/high16 v7, 0x41900000    # 18.0f

    invoke-virtual {v5, v6, v7}, Landroid/widget/TextView;->setTextSize(IF)V

    const/4 v6, 0x0    # null Typeface

    const/4 v7, 0x1    # BOLD

    invoke-virtual {v5, v6, v7}, Landroid/widget/TextView;->setTypeface(Landroid/graphics/Typeface;I)V

    const v6, 0x11    # Gravity.CENTER

    invoke-virtual {v5, v6}, Landroid/widget/TextView;->setGravity(I)V

    # タイトル layout params: width=0, height=MATCH_PARENT, weight=1.0f
    new-instance v6, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v7, 0x0

    const/4 v8, -0x1

    const/high16 v9, 0x3f800000    # 1.0f

    invoke-direct {v6, v7, v8, v9}, Landroid/widget/LinearLayout$LayoutParams;-><init>(IIF)V

    invoke-virtual {v5, v6}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    # 右側スペーサー (56×56dp, 左右対称)
    new-instance v6, Landroid/view/View;

    invoke-direct {v6, p0}, Landroid/view/View;-><init>(Landroid/content/Context;)V

    new-instance v7, Landroid/widget/LinearLayout$LayoutParams;

    invoke-direct {v7, v3, v3}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    invoke-virtual {v6, v7}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    # ヘッダーへ追加: ← | タイトル | スペーサー
    invoke-virtual {v2, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    invoke-virtual {v2, v5}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    invoke-virtual {v2, v6}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    # root へ追加
    invoke-virtual {v1, v2}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    # --- ヘッダー下区切り線 (1px, #E0E0E0) ---
    new-instance v2, Landroid/view/View;

    invoke-direct {v2, p0}, Landroid/view/View;-><init>(Landroid/content/Context;)V

    const v3, 0xFFE0E0E0

    invoke-virtual {v2, v3}, Landroid/view/View;->setBackgroundColor(I)V

    new-instance v3, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v4, -0x1

    const/4 v5, 0x1

    invoke-direct {v3, v4, v5}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    invoke-virtual {v2, v3}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    invoke-virtual {v1, v2}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    # --- コンテンツ LinearLayout (8dp パディング) ---
    new-instance v2, Landroid/widget/LinearLayout;

    invoke-direct {v2, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V

    const/4 v3, 0x1

    invoke-virtual {v2, v3}, Landroid/widget/LinearLayout;->setOrientation(I)V

    const/16 v3, 0x8

    invoke-static {p0, v3}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->dp(Landroid/content/Context;I)I

    move-result v3

    invoke-virtual {v2, v3, v3, v3, v3}, Landroid/view/View;->setPadding(IIII)V

    # --- 4 つの設定行 ---
    const-string v4, "ログイントースト クリックスルー"

    const-string v5, "アプリ起動後の、自動ログイントースト表示中も画面を操作できるように。"

    const-string v6, "toast_clickthrough"

    invoke-static {p0, v2, v4, v5, v6}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->addRow(Landroid/content/Context;Landroid/widget/LinearLayout;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

    const-string v4, "ロード中の操作を許可"

    const-string v5, "画面遷移・取得ロード中も操作を可能に。（送信中は二重送信防止のため遮断）"

    const-string v6, "loading_clickthrough"

    invoke-static {p0, v2, v4, v5, v6}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->addRow(Landroid/content/Context;Landroid/widget/LinearLayout;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

    const-string v4, "テレメトリ送信をブロック"

    const-string v5, "Firebase Analytics / Crashlytics / Performance と広告 ID の送信をブロック。（アプリ通知は維持）"

    const-string v6, "telemetry_off"

    invoke-static {p0, v2, v4, v5, v6}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->addRow(Landroid/content/Context;Landroid/widget/LinearLayout;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

    const-string v4, "時間外チェックイン"

    const-string v5, "受付時間外でもチェックインボタンを押せるようにし、確認後にチェックイン。（既定オフ）"

    const-string v6, "checkin_outoftime"

    invoke-static {p0, v2, v4, v5, v6}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->addRow(Landroid/content/Context;Landroid/widget/LinearLayout;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

    # --- 5 行目: 開始時間に自動チェックイン (時間外チェックインの子機能, 既定オフ) ---
    const-string v4, "開始時間に自動チェックイン (ハイリスク)"

    const-string v5, "時間外確認後、チェックイン開始時間になったら自動送信。ボーリングするため、サーバー側に発覚するリスクがあります。（既定オフ）"

    const-string v6, "checkin_autocheckin"

    invoke-static {p0, v2, v4, v5, v6}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->addRow(Landroid/content/Context;Landroid/widget/LinearLayout;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

    # 親 (時間外チェックイン) が OFF なら子スイッチをグレーアウト
    # content layout の 8 番目 (0-indexed) の child = 5th row container
    sget-boolean v3, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->checkinEnabled:Z

    if-nez v3, :domico_autocheckin_enabled

    const/16 v3, 0x8

    invoke-virtual {v2, v3}, Landroid/view/ViewGroup;->getChildAt(I)Landroid/view/View;

    move-result-object v3

    if-eqz v3, :domico_autocheckin_enabled

    # container.setAlpha(0.5f): 0.5f = 0x3F000000
    const v4, 0x3F000000

    invoke-virtual {v3, v4}, Landroid/view/View;->setAlpha(F)V

    # switch = container.getChildAt(1)
    # v3 is View (from previous getChildAt return) — cast to ViewGroup before dispatch
    check-cast v3, Landroid/view/ViewGroup;

    const/4 v4, 0x1

    invoke-virtual {v3, v4}, Landroid/view/ViewGroup;->getChildAt(I)Landroid/view/View;

    move-result-object v3

    if-eqz v3, :domico_autocheckin_enabled

    const/4 v4, 0x0

    invoke-virtual {v3, v4}, Landroid/view/View;->setEnabled(Z)V

    :domico_autocheckin_enabled

    # --- フッター区切り線 (1px, #E0E0E0, topMargin 12dp) ---
    new-instance v4, Landroid/view/View;

    invoke-direct {v4, p0}, Landroid/view/View;-><init>(Landroid/content/Context;)V

    const v5, 0xFFE0E0E0

    invoke-virtual {v4, v5}, Landroid/view/View;->setBackgroundColor(I)V

    new-instance v5, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v6, -0x1

    const/4 v7, 0x1

    invoke-direct {v5, v6, v7}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    const/16 v6, 0xc

    invoke-static {p0, v6}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->dp(Landroid/content/Context;I)I

    move-result v6

    iput v6, v5, Landroid/view/ViewGroup$MarginLayoutParams;->topMargin:I

    invoke-virtual {v4, v5}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    invoke-virtual {v2, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    # --- クレジットタイトル (太字・中央揃え・13sp・グレー) ---
    new-instance v4, Landroid/widget/TextView;

    invoke-direct {v4, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    sget-object v5, Lvn/com/bravesoft/androidapp/patch/PatchInfo;->CREDIT:Ljava/lang/String;

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    const v5, -0x777778    # 0xFF888888 gray

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setTextColor(I)V

    const/4 v5, 0x2

    const/high16 v6, 0x41500000    # 13.0f

    invoke-virtual {v4, v5, v6}, Landroid/widget/TextView;->setTextSize(IF)V

    const/4 v5, 0x0

    const/4 v6, 0x1    # BOLD

    invoke-virtual {v4, v5, v6}, Landroid/widget/TextView;->setTypeface(Landroid/graphics/Typeface;I)V

    const/4 v5, 0x1    # CENTER_HORIZONTAL

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setGravity(I)V

    new-instance v5, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v6, -0x1

    const/4 v7, -0x2

    invoke-direct {v5, v6, v7}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    const/16 v6, 0xc

    invoke-static {p0, v6}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->dp(Landroid/content/Context;I)I

    move-result v6

    iput v6, v5, Landroid/view/ViewGroup$MarginLayoutParams;->topMargin:I

    invoke-virtual {v4, v5}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    invoke-virtual {v2, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    # --- 著作権 + バージョン (11sp・グレー・中央揃え) ---
    new-instance v4, Landroid/widget/TextView;

    invoke-direct {v4, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    new-instance v5, Ljava/lang/StringBuilder;

    invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V

    sget-object v6, Lvn/com/bravesoft/androidapp/patch/PatchInfo;->COPYRIGHT:Ljava/lang/String;

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v5

    const-string v6, "\n"

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v5

    sget-object v6, Lvn/com/bravesoft/androidapp/patch/PatchInfo;->VERSION:Ljava/lang/String;

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v5

    invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v5

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    const v5, -0x777778    # gray

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setTextColor(I)V

    const/4 v5, 0x2

    const/high16 v6, 0x41300000    # 11.0f

    invoke-virtual {v4, v5, v6}, Landroid/widget/TextView;->setTextSize(IF)V

    const/4 v5, 0x1

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setGravity(I)V

    new-instance v5, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v6, -0x1

    const/4 v7, -0x2

    invoke-direct {v5, v6, v7}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    const/4 v6, 0x4

    invoke-static {p0, v6}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->dp(Landroid/content/Context;I)I

    move-result v6

    iput v6, v5, Landroid/view/ViewGroup$MarginLayoutParams;->topMargin:I

    invoke-virtual {v4, v5}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    invoke-virtual {v2, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    # --- 底スペーサー (24dp) ---
    new-instance v4, Landroid/view/View;

    invoke-direct {v4, p0}, Landroid/view/View;-><init>(Landroid/content/Context;)V

    new-instance v5, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v6, -0x1

    const/16 v7, 0x18    # 24

    invoke-static {p0, v7}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->dp(Landroid/content/Context;I)I

    move-result v7

    invoke-direct {v5, v6, v7}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    invoke-virtual {v4, v5}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    invoke-virtual {v2, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    # --- ScrollView (weight=1 で残り高さを全て使う) ---
    new-instance v4, Landroid/widget/ScrollView;

    invoke-direct {v4, p0}, Landroid/widget/ScrollView;-><init>(Landroid/content/Context;)V

    invoke-virtual {v4, v2}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    new-instance v5, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v6, -0x1

    const/4 v7, 0x0

    const/high16 v8, 0x3f800000    # 1.0f

    invoke-direct {v5, v6, v7, v8}, Landroid/widget/LinearLayout$LayoutParams;-><init>(IIF)V

    invoke-virtual {v4, v5}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    invoke-virtual {v1, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    # --- コンテンツビュー設定 → 表示 ---
    invoke-virtual {v0, v1}, Landroid/app/Dialog;->setContentView(Landroid/view/View;)V

    invoke-virtual {v0}, Landroid/app/Dialog;->show()V

    # --- 全画面化: 白背景 + MATCH_PARENT × MATCH_PARENT ---
    invoke-virtual {v0}, Landroid/app/Dialog;->getWindow()Landroid/view/Window;

    move-result-object v2

    new-instance v3, Landroid/graphics/drawable/ColorDrawable;

    const v4, 0xFFFFFFFF

    invoke-direct {v3, v4}, Landroid/graphics/drawable/ColorDrawable;-><init>(I)V

    invoke-virtual {v2, v3}, Landroid/view/Window;->setBackgroundDrawable(Landroid/graphics/drawable/Drawable;)V

    const/4 v3, -0x1    # MATCH_PARENT

    invoke-virtual {v2, v3, v3}, Landroid/view/Window;->setLayout(II)V

    return-void
.end method
