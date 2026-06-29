.class public final Lvn/com/bravesoft/androidapp/patch/PatchInit;
.super Ljava/lang/Object;
.source "PatchInit.java"

# domico-patch single entry point, called from MyApplication.onCreate.
# Loads prefs, applies the telemetry kill-switch, and registers the activity
# tracker used by the loading input-guard. Fully guarded so it cannot break
# app startup.


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static onAppCreate(Landroid/app/Application;)V
    .locals 2

    if-nez p0, :cond_0

    return-void

    :cond_0
    const-string v0, "domico-patch"

    const-string v1, "PatchInit.onAppCreate: enter"

    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    :try_start_0
    invoke-virtual {p0}, Landroid/app/Application;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0}, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->load(Landroid/content/Context;)V

    invoke-static {v0}, Lvn/com/bravesoft/androidapp/patch/PatchTelemetry;->apply(Landroid/content/Context;)V

    new-instance v0, Lvn/com/bravesoft/androidapp/patch/PatchActivityTracker;

    invoke-direct {v0}, Lvn/com/bravesoft/androidapp/patch/PatchActivityTracker;-><init>()V

    invoke-virtual {p0, v0}, Landroid/app/Application;->registerActivityLifecycleCallbacks(Landroid/app/Application$ActivityLifecycleCallbacks;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    return-void
.end method
