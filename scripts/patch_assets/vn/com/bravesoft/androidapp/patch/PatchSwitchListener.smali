.class public final Lvn/com/bravesoft/androidapp/patch/PatchSwitchListener;
.super Ljava/lang/Object;
.source "PatchSwitchListener.java"

# domico-patch: persists a single patch toggle when its Switch changes.

# interfaces
.implements Landroid/widget/CompoundButton$OnCheckedChangeListener;


# instance fields
.field final ctx:Landroid/content/Context;

.field final key:Ljava/lang/String;


# direct methods
.method constructor <init>(Landroid/content/Context;Ljava/lang/String;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lvn/com/bravesoft/androidapp/patch/PatchSwitchListener;->ctx:Landroid/content/Context;

    iput-object p2, p0, Lvn/com/bravesoft/androidapp/patch/PatchSwitchListener;->key:Ljava/lang/String;

    return-void
.end method


# virtual methods
.method public onCheckedChanged(Landroid/widget/CompoundButton;Z)V
    .locals 2

    iget-object v0, p0, Lvn/com/bravesoft/androidapp/patch/PatchSwitchListener;->ctx:Landroid/content/Context;

    iget-object v1, p0, Lvn/com/bravesoft/androidapp/patch/PatchSwitchListener;->key:Ljava/lang/String;

    invoke-static {v0, v1, p2}, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->set(Landroid/content/Context;Ljava/lang/String;Z)V

    return-void
.end method
