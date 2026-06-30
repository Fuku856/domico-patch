.class public final Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;
.super Ljava/lang/Object;
.source "PatchAutoCheckin.java"

# domico-patch: 自動チェックイン。
# PatchPrefs.autoCheckinEnabled が ON かつ PatchPrefs.checkinEnabled が ON のとき、
# 時間外確認ダイアログで OK した予約 ID を保持し、isCheckInTime が true になった
# 瞬間に HomeModelView.checkIn() を自動呼び出しする。
# Handler(mainLooper) で 30 秒ごとに getMenuForDay() をポーリングし、
# フラグメントが GC された / 機能が OFF になったときは自動停止する。

.implements Ljava/lang/Runnable;


# static fields
.field public static final INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;

.field static handler:Landroid/os/Handler;

.field static pendingFragment:Ljava/lang/ref/WeakReference;

.field static pendingReservationId:I


# direct methods

.method static constructor <clinit>()V
    .locals 2

    new-instance v0, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;

    invoke-direct {v0}, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;-><init>()V

    sput-object v0, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;

    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v1

    new-instance v0, Landroid/os/Handler;

    invoke-direct {v0, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V

    sput-object v0, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->handler:Landroid/os/Handler;

    return-void

.end method

.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void

.end method

# 自動チェックインをスケジュール。fragment = HomeFragment、id = reservationId。
.method public static setPending(Landroidx/fragment/app/Fragment;I)V
    .locals 4

    sput p1, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->pendingReservationId:I

    new-instance v0, Ljava/lang/ref/WeakReference;

    invoke-direct {v0, p0}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V

    sput-object v0, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->pendingFragment:Ljava/lang/ref/WeakReference;

    # 既存コールバックをキャンセルしてから再登録
    sget-object v0, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->handler:Landroid/os/Handler;

    sget-object v1, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;

    invoke-virtual {v0, v1}, Landroid/os/Handler;->removeCallbacks(Ljava/lang/Runnable;)V

    sget-object v0, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->handler:Landroid/os/Handler;

    sget-object v1, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;

    # 30 秒 = 30000ms = 0x7530
    const-wide/32 v2, 0x7530

    invoke-virtual {v0, v1, v2, v3}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z

    return-void

.end method

# 保留をクリアし、ポーリングを停止する。
.method public static clearPending()V
    .locals 2

    const/4 v0, 0x0

    sput v0, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->pendingReservationId:I

    sput-object v0, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->pendingFragment:Ljava/lang/ref/WeakReference;

    sget-object v0, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->handler:Landroid/os/Handler;

    if-eqz v0, :ret

    sget-object v1, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;

    invoke-virtual {v0, v1}, Landroid/os/Handler;->removeCallbacks(Ljava/lang/Runnable;)V

    :ret
    return-void

.end method


# virtual methods

# Handler が 30 秒ごとに呼び出すポーリング。getMenuForDay() を叩いてデータを更新する。
# showUICheckIn が呼ばれると checkAndFire が isCheckInTime を確認して実チェックインを送信。
.method public run()V
    .locals 6

    sget-boolean v0, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->autoCheckinEnabled:Z

    if-eqz v0, :done

    sget v0, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->pendingReservationId:I

    if-eqz v0, :done

    sget-object v1, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->pendingFragment:Ljava/lang/ref/WeakReference;

    if-eqz v1, :done

    invoke-virtual {v1}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;

    move-result-object v1

    if-eqz v1, :done

    check-cast v1, Lvn/com/bravesoft/androidapp/ui/HomeFragment;

    invoke-virtual {v1}, Landroidx/fragment/app/Fragment;->isAdded()Z

    move-result v2

    if-eqz v2, :reschedule

    invoke-static {v1}, Lvn/com/bravesoft/androidapp/ui/HomeFragment;->access$getViewModel(Lvn/com/bravesoft/androidapp/ui/HomeFragment;)Lvn/com/bravesoft/androidapp/modelview/HomeModelView;

    move-result-object v2

    if-eqz v2, :reschedule

    invoke-virtual {v2}, Lvn/com/bravesoft/androidapp/modelview/HomeModelView;->getMenuForDay()V

    :reschedule
    sget-object v2, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->handler:Landroid/os/Handler;

    sget-object v3, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;

    const-wide/32 v4, 0x7530

    invoke-virtual {v2, v3, v4, v5}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z

    :done
    return-void

.end method

# showUICheckIn から毎回呼ばれる。isCheckInTime が true になった瞬間に checkIn() を発火。
.method public static checkAndFire(Lvn/com/bravesoft/androidapp/ui/HomeFragment;Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;)V
    .locals 3

    if-eqz p0, :ret

    if-eqz p1, :ret

    sget-boolean v0, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->autoCheckinEnabled:Z

    if-eqz v0, :ret

    sget v0, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->pendingReservationId:I

    if-eqz v0, :ret

    # v0 = pending reservation id

    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->isCheckInTime()Z

    move-result v1

    if-eqz v1, :ret

    # 既チェックイン済みなら保留をクリアして終了
    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->getCheckIn()Ljava/lang/Integer;

    move-result-object v1

    if-eqz v1, :check_res

    invoke-virtual {v1}, Ljava/lang/Integer;->intValue()I

    move-result v1

    if-nez v1, :clear_done

    :check_res
    # 予約 ID が一致するか
    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->getReservationId()Ljava/lang/Integer;

    move-result-object v1

    if-eqz v1, :ret

    invoke-virtual {v1}, Ljava/lang/Integer;->intValue()I

    move-result v1

    if-ne v1, v0, :ret

    # 発火: clearPending → checkIn(reservationId)
    invoke-static {}, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->clearPending()V

    invoke-static {p0}, Lvn/com/bravesoft/androidapp/ui/HomeFragment;->access$getViewModel(Lvn/com/bravesoft/androidapp/ui/HomeFragment;)Lvn/com/bravesoft/androidapp/modelview/HomeModelView;

    move-result-object v2

    if-eqz v2, :ret

    invoke-virtual {v2, v1}, Lvn/com/bravesoft/androidapp/modelview/HomeModelView;->checkIn(I)V

    goto :ret

    :clear_done
    invoke-static {}, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->clearPending()V

    :ret
    return-void

.end method
