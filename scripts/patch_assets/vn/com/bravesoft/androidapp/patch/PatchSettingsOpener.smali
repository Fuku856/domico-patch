.class public final Lvn/com/bravesoft/androidapp/patch/PatchSettingsOpener;
.super Ljava/lang/Object;
.source "PatchSettingsOpener.java"

# domico-patch: opens the settings dialog on click or long-press. Stateless
# singleton; resolves the themed Context from the clicked view.

# interfaces
.implements Landroid/view/View$OnClickListener;
.implements Landroid/view/View$OnLongClickListener;


# static fields
.field public static final INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchSettingsOpener;


# direct methods
.method static constructor <clinit>()V
    .locals 1

    new-instance v0, Lvn/com/bravesoft/androidapp/patch/PatchSettingsOpener;

    invoke-direct {v0}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsOpener;-><init>()V

    sput-object v0, Lvn/com/bravesoft/androidapp/patch/PatchSettingsOpener;->INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchSettingsOpener;

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public onClick(Landroid/view/View;)V
    .locals 1

    invoke-virtual {p1}, Landroid/view/View;->getContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->show(Landroid/content/Context;)V

    return-void
.end method

.method public onLongClick(Landroid/view/View;)Z
    .locals 1

    invoke-virtual {p1}, Landroid/view/View;->getContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsDialog;->show(Landroid/content/Context;)V

    const/4 v0, 0x1

    return v0
.end method
