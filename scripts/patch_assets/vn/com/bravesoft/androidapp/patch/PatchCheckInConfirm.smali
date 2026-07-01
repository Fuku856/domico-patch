.class public final Lvn/com/bravesoft/androidapp/patch/PatchCheckInConfirm;
.super Ljava/lang/Object;
.source "PatchCheckInConfirm.java"

# domico-patch: 時間外チェックイン確認ダイアログ (AlertUtils.showAlertDialogCancel) の
# OK ボタンコールバック。CallbackAlertDialog を実装し、actionDoneClick() で:
#   - autoCheckinEnabled ON かつ isCheckInTime==false → PatchAutoCheckin.setPending + Toast
#   - それ以外 → 公式 CheckInDialog を開く
# actionCancelClick() はインタフェースのデフォルト実装 (何もしない) をそのまま継承する。

.implements Lvn/com/bravesoft/androidapp/event/CallbackAlertDialog;


# instance fields
.field private final dto:Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;

.field private final frag:Landroidx/fragment/app/Fragment;


# direct methods
.method public constructor <init>(Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;Landroidx/fragment/app/Fragment;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lvn/com/bravesoft/androidapp/patch/PatchCheckInConfirm;->dto:Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;

    iput-object p2, p0, Lvn/com/bravesoft/androidapp/patch/PatchCheckInConfirm;->frag:Landroidx/fragment/app/Fragment;

    return-void

.end method


# virtual methods
.method public actionDoneClick()V
    .locals 5

    :try_start_0
    iget-object v0, p0, Lvn/com/bravesoft/androidapp/patch/PatchCheckInConfirm;->dto:Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;

    if-eqz v0, :ret

    iget-object v1, p0, Lvn/com/bravesoft/androidapp/patch/PatchCheckInConfirm;->frag:Landroidx/fragment/app/Fragment;

    if-eqz v1, :ret

    # autoCheckinEnabled かつ isCheckInTime==false のとき自動チェックインをスケジュール
    sget-boolean v2, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->autoCheckinEnabled:Z

    if-eqz v2, :normal_path

    invoke-virtual {v0}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->isCheckInTime()Z

    move-result v2

    if-nez v2, :normal_path

    # 予約 ID を取得
    invoke-virtual {v0}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->getReservationId()Ljava/lang/Integer;

    move-result-object v2

    if-eqz v2, :ret

    invoke-virtual {v2}, Ljava/lang/Integer;->intValue()I

    move-result v2

    # PatchAutoCheckin.setPending(frag, reservationId)
    invoke-static {v1, v2}, Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;->setPending(Landroidx/fragment/app/Fragment;I)V

    # Toast でユーザーに通知
    invoke-virtual {v1}, Landroidx/fragment/app/Fragment;->getContext()Landroid/content/Context;

    move-result-object v3

    if-eqz v3, :ret

    const-string v4, "チェックイン時間になったら自動送信します"

    const/4 v2, 0x1

    invoke-static {v3, v4, v2}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;

    move-result-object v3

    invoke-virtual {v3}, Landroid/widget/Toast;->show()V

    goto :ret

    # 通常パス: CheckInDialog を開く (callbackCheckIn をセットして成功後に完了ダイアログへ遷移)
    :normal_path
    # v1 を Fragment → HomeFragment へキャスト（access$getViewModel 呼び出しのため）
    check-cast v1, Lvn/com/bravesoft/androidapp/ui/HomeFragment;

    # PatchCheckInCallback(homeFragment, dto) を生成
    new-instance v2, Lvn/com/bravesoft/androidapp/patch/PatchCheckInCallback;

    invoke-direct {v2, v1, v0}, Lvn/com/bravesoft/androidapp/patch/PatchCheckInCallback;-><init>(Lvn/com/bravesoft/androidapp/ui/HomeFragment;Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;)V

    # CheckInDialog を生成してコールバックをセット
    sget-object v3, Lvn/com/bravesoft/androidapp/ui/CheckInDialog;->Companion:Lvn/com/bravesoft/androidapp/ui/CheckInDialog$Companion;

    invoke-virtual {v3, v0}, Lvn/com/bravesoft/androidapp/ui/CheckInDialog$Companion;->newInstance(Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;)Lvn/com/bravesoft/androidapp/ui/CheckInDialog;

    move-result-object v3

    invoke-virtual {v3, v2}, Lvn/com/bravesoft/androidapp/ui/CheckInDialog;->setCallbackCheckIn(Lvn/com/bravesoft/androidapp/event/CallbackCheckIn;)V

    invoke-virtual {v1}, Landroidx/fragment/app/Fragment;->getChildFragmentManager()Landroidx/fragment/app/FragmentManager;

    move-result-object v1

    const-string v4, "CheckInDialog"

    invoke-virtual {v3, v1, v4}, Lvn/com/bravesoft/androidapp/ui/CheckInDialog;->show(Landroidx/fragment/app/FragmentManager;Ljava/lang/String;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0

    :ret
    return-void

    :catch_0
    move-exception v0

    return-void

.end method
