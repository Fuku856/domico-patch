.class public final Lvn/com/bravesoft/androidapp/patch/PatchCheckInCallback;
.super Ljava/lang/Object;
.source "PatchCheckInCallback.java"

# domico-patch: CheckInDialog の callbackCheckIn 実装。
# PatchCheckInConfirm から CheckInDialog を開く際にセットする。
# onCheckInSuccess() で HomeModelView.getMenuForDay() 更新と
# CheckInCompletedDialog 表示を行う — HomeFragment.checkInAction$1 と同等の動作。

.implements Lvn/com/bravesoft/androidapp/event/CallbackCheckIn;


# instance fields
.field private final frag:Lvn/com/bravesoft/androidapp/ui/HomeFragment;

.field private final dto:Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;


# direct methods
.method public constructor <init>(Lvn/com/bravesoft/androidapp/ui/HomeFragment;Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lvn/com/bravesoft/androidapp/patch/PatchCheckInCallback;->frag:Lvn/com/bravesoft/androidapp/ui/HomeFragment;

    iput-object p2, p0, Lvn/com/bravesoft/androidapp/patch/PatchCheckInCallback;->dto:Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;

    return-void

.end method


# virtual methods
.method public onCheckInSuccess()V
    .locals 4

    :try_start_0
    iget-object v0, p0, Lvn/com/bravesoft/androidapp/patch/PatchCheckInCallback;->frag:Lvn/com/bravesoft/androidapp/ui/HomeFragment;

    if-eqz v0, :ret

    # 1. メニュー情報を更新
    invoke-static {v0}, Lvn/com/bravesoft/androidapp/ui/HomeFragment;->access$getViewModel(Lvn/com/bravesoft/androidapp/ui/HomeFragment;)Lvn/com/bravesoft/androidapp/modelview/HomeModelView;

    move-result-object v1

    if-eqz v1, :show_completed

    invoke-virtual {v1}, Lvn/com/bravesoft/androidapp/modelview/HomeModelView;->getMenuForDay()V

    # 2. CheckInCompletedDialog を表示
    :show_completed
    iget-object v1, p0, Lvn/com/bravesoft/androidapp/patch/PatchCheckInCallback;->dto:Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;

    if-eqz v1, :ret

    invoke-virtual {v1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->getReservationId()Ljava/lang/Integer;

    move-result-object v1

    if-eqz v1, :ret

    invoke-virtual {v1}, Ljava/lang/Integer;->intValue()I

    move-result v1

    sget-object v2, Lvn/com/bravesoft/androidapp/ui/CheckInCompletedDialog;->Companion:Lvn/com/bravesoft/androidapp/ui/CheckInCompletedDialog$Companion;

    invoke-virtual {v2, v1}, Lvn/com/bravesoft/androidapp/ui/CheckInCompletedDialog$Companion;->newInstance(I)Lvn/com/bravesoft/androidapp/ui/CheckInCompletedDialog;

    move-result-object v1

    invoke-virtual {v0}, Lvn/com/bravesoft/androidapp/ui/HomeFragment;->getChildFragmentManager()Landroidx/fragment/app/FragmentManager;

    move-result-object v2

    const-string v3, "CheckInCompletedDialog"

    invoke-virtual {v1, v2, v3}, Lvn/com/bravesoft/androidapp/ui/CheckInCompletedDialog;->show(Landroidx/fragment/app/FragmentManager;Ljava/lang/String;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0

    :ret
    return-void

    :catch_0
    move-exception v0

    return-void

.end method
