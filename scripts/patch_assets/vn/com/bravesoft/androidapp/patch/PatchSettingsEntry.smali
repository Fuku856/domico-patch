.class public final Lvn/com/bravesoft/androidapp/patch/PatchSettingsEntry;
.super Ljava/lang/Object;
.source "PatchSettingsEntry.java"

# domico-patch: wires the settings entry points into the Menu screen.
# Primary entry is a visible, tappable "パッチ設定" row inserted right after the
# version row (versionContain) INSIDE the scrollable menu list. The container is
# resolved as versionContain.getParent() rather than the binding's containerTop,
# because containerTop is the screen-root LinearLayout (toolbar + full-height
# NestedScrollView) — appending there pushes the row below the scroll view and
# off-screen. The secondary entry is a long-press on the bottom-nav "メニュー"
# tab (see installNav), replacing the old version-row long-press.
# A divider line (1dp / #F0F0F0 / 10dp start margin) is inserted above the row to
# match the other menu items' ButtonView.lineBottom separators. The row text is
# 14sp (matching ButtonView.content) with a left settings-gear compound drawable
# (framework android.R.drawable.ic_menu_manage, tinted black); no app resource is
# added because patch_apk only replaces dex and leaves resources.arsc untouched.
# Idempotent via a view tag. Emits Log.i("domico-patch", ...) for diagnostics.


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static install(Lvn/com/bravesoft/androidapp/databinding/MenuLayoutBinding;)V
    .locals 10

    const-string v7, "domico-patch"

    if-nez p0, :cond_have_binding

    const-string v0, "PatchSettingsEntry.install: binding is null"

    invoke-static {v7, v0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    return-void

    :cond_have_binding
    const-string v0, "PatchSettingsEntry.install: enter"

    invoke-static {v7, v0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    sget-object v0, Lvn/com/bravesoft/androidapp/patch/PatchSettingsOpener;->INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchSettingsOpener;

    # versionContain は表示中のバージョン行。行の挿入位置アンカーとしてのみ使う。
    # (バージョン長押しの導線は廃止。長押しは下部ナビ「メニュー」へ移動: installNav)
    iget-object v1, p0, Lvn/com/bravesoft/androidapp/databinding/MenuLayoutBinding;->versionContain:Lvn/com/bravesoft/androidapp/views/ButtonView;

    if-nez v1, :cond_have_version

    # fallback: versionContain が無ければ containerTop を直接の追加先にする。
    iget-object v2, p0, Lvn/com/bravesoft/androidapp/databinding/MenuLayoutBinding;->containerTop:Landroid/widget/LinearLayout;

    const-string v1, "PatchSettingsEntry.install: versionContain null, fallback containerTop"

    invoke-static {v7, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    if-eqz v2, :ret

    goto :have_parent

    :cond_have_version
    # versionContain の親 = スクロール内の実メニューリスト。ここへ行を足す。
    invoke-virtual {v1}, Landroid/view/View;->getParent()Landroid/view/ViewParent;

    move-result-object v2

    instance-of v3, v2, Landroid/view/ViewGroup;

    if-nez v3, :cond_cast_parent

    const-string v0, "PatchSettingsEntry.install: version parent is not a ViewGroup"

    invoke-static {v7, v0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    return-void

    :cond_cast_parent
    check-cast v2, Landroid/view/ViewGroup;

    :have_parent
    # 冪等性: 既に追加済みなら何もしない。
    const-string v3, "domico_patch_row"

    invoke-virtual {v2, v3}, Landroid/view/View;->findViewWithTag(Ljava/lang/Object;)Landroid/view/View;

    move-result-object v4

    if-eqz v4, :cond_create_row

    const-string v0, "PatchSettingsEntry.install: row already present"

    invoke-static {v7, v0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    return-void

    :cond_create_row
    invoke-virtual {v2}, Landroid/view/View;->getContext()Landroid/content/Context;

    move-result-object v4

    new-instance v5, Landroid/widget/TextView;

    invoke-direct {v5, v4}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    const-string v6, "パッチ設定"

    invoke-virtual {v5, v6}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    # 他のメニュー項目と同じ黒テキストに揃える (0xFF000000)
    const v6, -0x1000000

    invoke-virtual {v5, v6}, Landroid/widget/TextView;->setTextColor(I)V

    const/4 v6, 0x1

    invoke-virtual {v5, v6}, Landroid/view/View;->setClickable(Z)V

    invoke-virtual {v5, v3}, Landroid/view/View;->setTag(Ljava/lang/Object;)V

    invoke-virtual {v5, v0}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    invoke-virtual {v5, v0}, Landroid/view/View;->setOnLongClickListener(Landroid/view/View$OnLongClickListener;)V

    # padding = 16dp (density スケール)
    invoke-virtual {v4}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v4

    invoke-virtual {v4}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;

    move-result-object v4

    iget v4, v4, Landroid/util/DisplayMetrics;->density:F

    const/high16 v6, 0x41800000    # 16.0f

    mul-float/2addr v6, v4

    float-to-int v6, v6

    invoke-virtual {v5, v6, v6, v6, v6}, Landroid/view/View;->setPadding(IIII)V

    const/4 v4, 0x2

    const/high16 v6, 0x41600000    # 14.0f sp

    invoke-virtual {v5, v4, v6}, Landroid/widget/TextView;->setTextSize(IF)V

    # domico-patch: 左端に設定アイコンを付与。アプリ内に歯車 drawable が無く、
    # リソース追加は arsc 非改変方針(patch_apk は dex のみ差替)に反するため、
    # 端末フレームワークの android.R.drawable.ic_menu_manage を参照する。
    invoke-virtual {v5}, Landroid/view/View;->getContext()Landroid/content/Context;

    move-result-object v8

    sget v9, Landroid/R$drawable;->ic_menu_manage:I

    invoke-virtual {v8, v9}, Landroid/content/Context;->getDrawable(I)Landroid/graphics/drawable/Drawable;

    move-result-object v8

    if-eqz v8, :domico_gear_skip

    invoke-virtual {v5}, Landroid/widget/TextView;->getResources()Landroid/content/res/Resources;

    move-result-object v9

    invoke-virtual {v9}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;

    move-result-object v9

    iget v9, v9, Landroid/util/DisplayMetrics;->density:F

    # アイコンを 20dp 四方に収め、テキストと同じ黒(0xFF000000)でティント
    const/high16 v0, 0x41a00000    # 20.0f

    mul-float/2addr v0, v9

    float-to-int v0, v0

    const/4 v6, 0x0

    invoke-virtual {v8, v6, v6, v0, v0}, Landroid/graphics/drawable/Drawable;->setBounds(IIII)V

    const v0, -0x1000000    # 0xFF000000

    invoke-virtual {v8, v0}, Landroid/graphics/drawable/Drawable;->setTint(I)V

    const/4 v6, 0x0

    invoke-virtual {v5, v8, v6, v6, v6}, Landroid/widget/TextView;->setCompoundDrawables(Landroid/graphics/drawable/Drawable;Landroid/graphics/drawable/Drawable;Landroid/graphics/drawable/Drawable;Landroid/graphics/drawable/Drawable;)V

    # アイコンとテキストの間隔 8dp
    const/high16 v0, 0x41000000    # 8.0f

    mul-float/2addr v0, v9

    float-to-int v0, v0

    invoke-virtual {v5, v0}, Landroid/widget/TextView;->setCompoundDrawablePadding(I)V

    :domico_gear_skip

    # layout params: 横 match_parent / 縦 wrap_content
    new-instance v4, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v6, -0x1

    const/4 v1, -0x2

    invoke-direct {v4, v6, v1}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    invoke-virtual {v5, v4}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    # domico-patch: 他のメニュー行(ButtonView.lineBottom)と同じ区切り線を
    # パッチ設定行の上に追加する。versionContain は最終行として下線を隠している
    # ため、線が無いとバージョン行と地続きに見える。1dp / #F0F0F0 / 左マージン10dp
    # で他行の下線に合わせる。冪等ガード(findViewWithTag)が線の二重追加も防ぐ。
    new-instance v1, Landroid/view/View;

    invoke-virtual {v2}, Landroid/view/View;->getContext()Landroid/content/Context;

    move-result-object v8

    invoke-direct {v1, v8}, Landroid/view/View;-><init>(Landroid/content/Context;)V

    const v9, -0xf0f10    # 0xFFF0F0F0 (@color/whiteF0)

    invoke-virtual {v1, v9}, Landroid/view/View;->setBackgroundColor(I)V

    # density スケール(1dp / 10dp)を算出
    invoke-virtual {v8}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v8

    invoke-virtual {v8}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;

    move-result-object v8

    iget v8, v8, Landroid/util/DisplayMetrics;->density:F

    # 高さ = max(1, round(1dp * density)) で 0px つぶれを回避
    const/high16 v9, 0x3f800000    # 1.0f

    mul-float/2addr v9, v8

    float-to-int v9, v9

    const/4 v6, 0x1

    invoke-static {v9, v6}, Ljava/lang/Math;->max(II)I

    move-result v9

    new-instance v6, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v3, -0x1

    invoke-direct {v6, v3, v9}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V

    # 左マージン 10dp で他行 lineBottom の layout_marginStart に合わせる
    const/high16 v9, 0x41200000    # 10.0f

    mul-float/2addr v9, v8

    float-to-int v9, v9

    iput v9, v6, Landroid/view/ViewGroup$MarginLayoutParams;->leftMargin:I

    invoke-virtual {v1, v6}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    invoke-virtual {v2, v1}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    invoke-virtual {v2, v5}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    const-string v0, "PatchSettingsEntry.install: row added"

    invoke-static {v7, v0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    :ret
    return-void
.end method

.method public static installNav(Lvn/com/bravesoft/androidapp/databinding/MainTabHostLayoutBinding;)V
    .locals 3

    # 下部ナビ「メニュー」タブ(index 4)を長押しで設定を開く。
    # OnLongClickListener が true を返すため、Material 標準のツールチップ
    # (項目名 "メニュー" の表示)は抑止される。MainTabHostFragment.init から呼ぶ。
    const-string v2, "domico-patch"

    if-nez p0, :cond_0

    return-void

    :cond_0
    iget-object v0, p0, Lvn/com/bravesoft/androidapp/databinding/MainTabHostLayoutBinding;->bottomNavigation:Lcom/ittianyu/bottomnavigationviewex/BottomNavigationViewEx;

    if-nez v0, :cond_1

    const-string v1, "PatchSettingsEntry.installNav: bottomNavigation null"

    invoke-static {v2, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    return-void

    :cond_1
    const/4 v1, 0x4

    invoke-virtual {v0, v1}, Lcom/ittianyu/bottomnavigationviewex/BottomNavigationViewEx;->getBottomNavigationItemView(I)Lcom/google/android/material/bottomnavigation/BottomNavigationItemView;

    move-result-object v0

    if-nez v0, :cond_2

    const-string v1, "PatchSettingsEntry.installNav: menu item view null"

    invoke-static {v2, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    return-void

    :cond_2
    sget-object v1, Lvn/com/bravesoft/androidapp/patch/PatchSettingsOpener;->INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchSettingsOpener;

    invoke-virtual {v0, v1}, Landroid/view/View;->setOnLongClickListener(Landroid/view/View$OnLongClickListener;)V

    const-string v1, "PatchSettingsEntry.installNav: long-press wired on menu tab"

    invoke-static {v2, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    return-void
.end method
