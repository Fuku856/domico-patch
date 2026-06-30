.class public final Lvn/com/bravesoft/androidapp/patch/PatchCheckInConfirm;
.super Ljava/lang/Object;
.source "PatchCheckInConfirm.java"

# domico-patch: 時間外チェックイン確認ダイアログ (AlertUtils.showAlertDialogCancel) の
# OK ボタンコールバック。CallbackAlertDialog を実装し、actionDoneClick() で公式
# CheckInDialog を開く。actionCancelClick() はインタフェースのデフォルト実装 (何もしない)
# をそのまま継承する。CheckInDialog 側が和食/洋食/夕食 表示と実チェックインを行う。

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
    .locals 3

    :try_start_0
    iget-object v0, p0, Lvn/com/bravesoft/androidapp/patch/PatchCheckInConfirm;->dto:Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;

    if-eqz v0, :ret

    iget-object v1, p0, Lvn/com/bravesoft/androidapp/patch/PatchCheckInConfirm;->frag:Landroidx/fragment/app/Fragment;

    if-eqz v1, :ret

    sget-object v2, Lvn/com/bravesoft/androidapp/ui/CheckInDialog;->Companion:Lvn/com/bravesoft/androidapp/ui/CheckInDialog$Companion;

    invoke-virtual {v2, v0}, Lvn/com/bravesoft/androidapp/ui/CheckInDialog$Companion;->newInstance(Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;)Lvn/com/bravesoft/androidapp/ui/CheckInDialog;

    move-result-object v0

    invoke-virtual {v1}, Landroidx/fragment/app/Fragment;->getChildFragmentManager()Landroidx/fragment/app/FragmentManager;

    move-result-object v1

    const-string v2, "CheckInDialog"

    invoke-virtual {v0, v1, v2}, Lvn/com/bravesoft/androidapp/ui/CheckInDialog;->show(Landroidx/fragment/app/FragmentManager;Ljava/lang/String;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0

    :ret
    return-void

    :catch_0
    move-exception v0

    return-void
.end method
