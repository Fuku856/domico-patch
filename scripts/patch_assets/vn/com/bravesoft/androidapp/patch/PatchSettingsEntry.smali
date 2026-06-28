.class public final Lvn/com/bravesoft/androidapp/patch/PatchSettingsEntry;
.super Ljava/lang/Object;
.source "PatchSettingsEntry.java"

# domico-patch: wires the settings entry points into the Menu screen — a
# long-press on the version row plus a visible "パッチ設定" row. Idempotent
# (guards against adding the row twice via a view tag).


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static install(Lvn/com/bravesoft/androidapp/databinding/MenuLayoutBinding;)V
    .locals 6

    if-nez p0, :cond_0

    return-void

    :cond_0
    sget-object v0, Lvn/com/bravesoft/androidapp/patch/PatchSettingsOpener;->INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchSettingsOpener;

    iget-object v1, p0, Lvn/com/bravesoft/androidapp/databinding/MenuLayoutBinding;->versionContain:Lvn/com/bravesoft/androidapp/views/ButtonView;

    if-eqz v1, :cond_1

    invoke-virtual {v1, v0}, Landroid/view/View;->setOnLongClickListener(Landroid/view/View$OnLongClickListener;)V

    :cond_1
    iget-object v1, p0, Lvn/com/bravesoft/androidapp/databinding/MenuLayoutBinding;->containerTop:Landroid/widget/LinearLayout;

    if-eqz v1, :cond_2

    const-string v2, "domico_patch_row"

    invoke-virtual {v1, v2}, Landroid/view/View;->findViewWithTag(Ljava/lang/Object;)Landroid/view/View;

    move-result-object v3

    if-nez v3, :cond_2

    invoke-virtual {v1}, Landroid/view/View;->getContext()Landroid/content/Context;

    move-result-object v3

    new-instance v4, Landroid/widget/TextView;

    invoke-direct {v4, v3}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    const-string v5, "パッチ設定"

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    const/4 v5, 0x1

    invoke-virtual {v4, v5}, Landroid/view/View;->setClickable(Z)V

    invoke-virtual {v4, v2}, Landroid/view/View;->setTag(Ljava/lang/Object;)V

    invoke-virtual {v4, v0}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    invoke-virtual {v3}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v3

    invoke-virtual {v3}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;

    move-result-object v3

    iget v3, v3, Landroid/util/DisplayMetrics;->density:F

    const/high16 v5, 0x41800000    # 16.0f

    mul-float/2addr v5, v3

    float-to-int v5, v5

    invoke-virtual {v4, v5, v5, v5, v5}, Landroid/view/View;->setPadding(IIII)V

    const/4 v3, 0x2

    const/high16 v5, 0x41800000    # 16.0f sp

    invoke-virtual {v4, v3, v5}, Landroid/widget/TextView;->setTextSize(IF)V

    invoke-virtual {v1, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    :cond_2
    return-void
.end method
