.class public final Lvn/com/bravesoft/androidapp/patch/PatchDialogBackListener;
.super Ljava/lang/Object;
.source "PatchDialogBackListener.java"

# domico-patch: OnClickListener that dismisses the settings full-screen dialog.
# Wired to the "←" back button in PatchSettingsDialog.

# interfaces
.implements Landroid/view/View$OnClickListener;


# instance fields
.field private final dialog:Landroid/app/Dialog;


# direct methods
.method public constructor <init>(Landroid/app/Dialog;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lvn/com/bravesoft/androidapp/patch/PatchDialogBackListener;->dialog:Landroid/app/Dialog;

    return-void
.end method


# virtual methods
.method public onClick(Landroid/view/View;)V
    .locals 1

    iget-object v0, p0, Lvn/com/bravesoft/androidapp/patch/PatchDialogBackListener;->dialog:Landroid/app/Dialog;

    invoke-virtual {v0}, Landroid/app/Dialog;->dismiss()V

    return-void
.end method
