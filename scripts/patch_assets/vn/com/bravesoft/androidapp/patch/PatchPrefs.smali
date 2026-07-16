.class public final Lvn/com/bravesoft/androidapp/patch/PatchPrefs;
.super Ljava/lang/Object;
.source "PatchPrefs.java"

# domico-patch helper: SharedPreferences-backed on/off flags for each patch.
# Defaults are true (= patch enabled) so behaviour matches the un-toggled state
# even before load() runs.

# static fields
.field public static volatile autoCheckinEnabled:Z

.field public static volatile checkinEnabled:Z

.field public static volatile loadingEnabled:Z

.field public static volatile telemetryOff:Z

.field public static volatile toastEnabled:Z


# direct methods
.method static constructor <clinit>()V
    .locals 1

    const/4 v0, 0x1

    sput-boolean v0, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->toastEnabled:Z

    sput-boolean v0, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->loadingEnabled:Z

    sput-boolean v0, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->telemetryOff:Z

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static get(Landroid/content/Context;Ljava/lang/String;Z)Z
    .locals 2

    if-nez p0, :cond_0

    return p2

    :cond_0
    const-string v0, "domico_patch"

    const/4 v1, 0x0

    invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;

    move-result-object v0

    invoke-interface {v0, p1, p2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v0

    return v0
.end method

.method public static defaultOf(Ljava/lang/String;)Z
    .locals 1

    # 裏機能 checkin_outoftime / checkin_autocheckin と デバッグ push_debug_log は既定 OFF。他フラグは既定 ON。
    const-string v0, "checkin_outoftime"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :off

    const-string v0, "checkin_autocheckin"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :off

    const-string v0, "push_debug_log"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :on

    :off
    const/4 v0, 0x0

    return v0

    :on
    const/4 v0, 0x1

    return v0
.end method

.method public static load(Landroid/content/Context;)V
    .locals 3

    if-nez p0, :cond_0

    return-void

    :cond_0
    const-string v0, "domico_patch"

    const/4 v1, 0x0

    invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;

    move-result-object v0

    const-string v1, "toast_clickthrough"

    const/4 v2, 0x1

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v1

    sput-boolean v1, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->toastEnabled:Z

    const-string v1, "loading_clickthrough"

    const/4 v2, 0x1

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v1

    sput-boolean v1, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->loadingEnabled:Z

    const-string v1, "telemetry_off"

    const/4 v2, 0x1

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v1

    sput-boolean v1, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->telemetryOff:Z

    const-string v1, "checkin_outoftime"

    const/4 v2, 0x0

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v1

    sput-boolean v1, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->checkinEnabled:Z

    const-string v1, "checkin_autocheckin"

    const/4 v2, 0x0

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v1

    sput-boolean v1, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->autoCheckinEnabled:Z

    return-void
.end method

.method public static set(Landroid/content/Context;Ljava/lang/String;Z)V
    .locals 2

    if-nez p0, :cond_0

    return-void

    :cond_0
    const-string v0, "domico_patch"

    const/4 v1, 0x0

    invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    invoke-interface {v0, p1, p2}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p0}, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->load(Landroid/content/Context;)V

    return-void
.end method
