.class public final Lvn/com/bravesoft/androidapp/patch/PatchCheckIn;
.super Ljava/lang/Object;
.source "PatchCheckIn.java"

# domico-patch: 時間外チェックイン (裏機能)。
# 公式は受付時間外 (MenuForDayDTO.isCheckInTime()==false) のときホーム画面の
# チェックインボタンを stateButton で setEnabled(false)+alpha 0.5 にして無効化する。
# このヘルパは PatchPrefs.checkinEnabled が ON のときだけ:
#   1. enableOutOfTime: グレー見た目 (alpha 0.5) は残したままボタンを再有効化し、
#      公式のクリック (reactiveClick -> HomeFragment.checkInAction) を発火可能にする。
#   2. confirmOutOfTime: HomeFragment.checkInAction の冒頭から呼ばれ、時間外かつ
#      未チェックインなら純正の確認ダイアログ (AlertUtils.showAlertDialogCancel) を出し
#      true を返して公式処理を中断する。OK 押下時のみ PatchCheckInConfirm が公式
#      CheckInDialog を開く (和食/洋食/夕食 表示・実チェックインは公式ロジックを再利用)。
# どちらも try/catchall で囲み、失敗しても公式動作を壊さない。既定 OFF。


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

# 時間外 (isCheckInTime==false) かつ未チェックイン (getCheckIn()==0) のとき
# グレーアウトされたボタンを再有効化する。alpha は stateButton が設定した 0.5 のまま。
.method public static enableOutOfTime(Landroid/view/View;Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;Z)V
    .locals 1

    sget-boolean v0, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->checkinEnabled:Z

    if-eqz v0, :ret

    if-nez p2, :ret

    if-eqz p0, :ret

    if-eqz p1, :ret

    :try_start_0
    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->getCheckIn()Ljava/lang/Integer;

    move-result-object v0

    if-eqz v0, :ret

    invoke-virtual {v0}, Ljava/lang/Integer;->intValue()I

    move-result v0

    if-nez v0, :ret

    const/4 v0, 0x1

    invoke-virtual {p0, v0}, Landroid/view/View;->setEnabled(Z)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0

    :ret
    return-void

    :catch_0
    move-exception v0

    return-void
.end method

# HomeFragment.checkInAction の冒頭から呼ぶゲート。
# 介入した (確認ダイアログを出した) ときだけ true を返し、呼び出し側は return-void で
# 公式処理を中断する。それ以外 (機能 OFF / 時間内 / 既にチェックイン済 / 例外) は false。
.method public static confirmOutOfTime(Landroidx/fragment/app/Fragment;Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;)Z
    .locals 10

    sget-boolean v0, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->checkinEnabled:Z

    if-eqz v0, :ret_false

    if-eqz p0, :ret_false

    if-eqz p1, :ret_false

    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->isCheckInTime()Z

    move-result v0

    if-nez v0, :ret_false

    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->getCheckIn()Ljava/lang/Integer;

    move-result-object v0

    if-eqz v0, :ret_false

    invoke-virtual {v0}, Ljava/lang/Integer;->intValue()I

    move-result v0

    if-nez v0, :ret_false

    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getActivity()Landroidx/fragment/app/FragmentActivity;

    move-result-object v1

    if-eqz v1, :ret_false

    :try_start_0
    # ---- 予約食事名を v9 へ ----
    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->isBreakfast()Z

    move-result v8

    if-eqz v8, :dinner

    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->isJapanFoodReserved()Z

    move-result v8

    if-eqz v8, :chk_west

    const-string v9, "和食"

    goto :meal_done

    :chk_west
    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->isWesternFoodReserved()Z

    move-result v8

    if-eqz v8, :brk_other

    const-string v9, "洋食"

    goto :meal_done

    :brk_other
    const-string v9, "朝食"

    goto :meal_done

    :dinner
    const-string v9, "夕食"

    :meal_done
    # ---- メッセージを v3 へ ----
    new-instance v8, Ljava/lang/StringBuilder;

    invoke-direct {v8}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "予約："

    invoke-virtual {v8, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v3, "\n受付時間外ですが、チェックインしますか？"

    invoke-virtual {v8, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v8}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    # ---- OK 時コールバックを v7 へ ----
    new-instance v7, Lvn/com/bravesoft/androidapp/patch/PatchCheckInConfirm;

    invoke-direct {v7, p1, p0}, Lvn/com/bravesoft/androidapp/patch/PatchCheckInConfirm;-><init>(Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;Landroidx/fragment/app/Fragment;)V

    # ---- showAlertDialogCancel(ctx, cancelable, msg, okLabel, cancelLabel, title, cb) ----
    sget-object v0, Lvn/com/bravesoft/androidapp/utils/AlertUtils;->INSTANCE:Lvn/com/bravesoft/androidapp/utils/AlertUtils;

    const/4 v2, 0x1

    const/4 v4, 0x0

    const/4 v5, 0x0

    const-string v6, "時間外チェックイン"

    invoke-virtual/range {v0 .. v7}, Lvn/com/bravesoft/androidapp/utils/AlertUtils;->showAlertDialogCancel(Landroid/content/Context;ZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lvn/com/bravesoft/androidapp/event/CallbackAlertDialog;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0

    const/4 v0, 0x1

    return v0

    :catch_0
    move-exception v0

    :ret_false
    const/4 v0, 0x0

    return v0
.end method
