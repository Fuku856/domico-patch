.class public final Lvn/com/bravesoft/androidapp/patch/PatchCheckInInfo;
.super Ljava/lang/Object;
.source "PatchCheckInInfo.java"

# domico-patch: 時間外チェックインの「受け取り画面」(CheckInCompletedDialog) を端末側で再現する
# ためのデータホルダー。
#
# 背景: CheckInCompletedDialog は表示時に自分で GET v1/reservations/{id}/checkin を叩いて
#   アバター/氏名/部屋番号/食事種別 を取得するが、時間外チェックインではサーバーが当該予約を
#   「すでにキャンセル」として弾くため、この GET がエラーになり画面が空になる。
#
# 対策: PatchCheckInCallback.onCheckInSuccess() がダイアログを表示する直前に prepare() を呼び、
#   端末内の UserDTO(アバター/氏名/部屋番号) と MenuForDayDTO(朝食/夕食・和食/洋食) から
#   CheckInInformationResponse 相当の JSON を合成して保持する。PatchCheckInBypass が
#   上記 GET のエラー応答を受けたとき consume() で取り出し、HTTP 200 + この JSON に差し替える。
#   サーバーにチェックイン記録は残らない(画面表示は端末側合成)。
#
# JSON は CheckInInformationResponse の Gson 形:
#   {"code":"","message":"","data":{"avatar":..,"date":"yyyy-MM-dd","name":..,
#     "room_number":..,"type_of_food":N,"type_of_meal":M}}
#   type_of_meal==1 → 朝食(和食/洋食あり: type_of_food==1 和食 / それ以外 洋食)
#   type_of_meal!=1 → 夕食(和洋食なし)  ← CheckInCompletedDialog.setUpData の判定に一致


# static fields
.field private static volatile json:Ljava/lang/String;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

# 文字列を JSON 値として安全化(null→空文字、\ と " をエスケープ)。
.method private static esc(Ljava/lang/String;)Ljava/lang/String;
    .locals 2

    if-nez p0, :cond_0

    const-string p0, ""

    return-object p0

    :cond_0
    const-string v0, "\\"

    const-string v1, "\\\\"

    invoke-virtual {p0, v0, v1}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;

    move-result-object p0

    const-string v0, "\""

    const-string v1, "\\\""

    invoke-virtual {p0, v0, v1}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;

    move-result-object p0

    return-object p0
.end method

# 保持中の合成 JSON を返してクリア(消費一回)。無ければ null。
.method public static consume()Ljava/lang/String;
    .locals 2

    sget-object v0, Lvn/com/bravesoft/androidapp/patch/PatchCheckInInfo;->json:Ljava/lang/String;

    const/4 v1, 0x0

    sput-object v1, Lvn/com/bravesoft/androidapp/patch/PatchCheckInInfo;->json:Ljava/lang/String;

    return-object v0
.end method

# 端末内データから受け取り画面用 JSON を合成して保持する。
# p0 = HomeFragment(getUserCtrl で UserDTO を得る), p1 = MenuForDayDTO(食事種別)
.method public static prepare(Lvn/com/bravesoft/androidapp/ui/HomeFragment;Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;)V
    .locals 5

    :try_start_0
    if-eqz p0, :ret

    if-eqz p1, :ret

    invoke-virtual {p0}, Lvn/com/bravesoft/androidapp/ui/HomeFragment;->getUserCtrl()Lvn/com/bravesoft/androidapp/helper/UserCtrl;

    move-result-object v0

    if-eqz v0, :ret

    invoke-virtual {v0}, Lvn/com/bravesoft/androidapp/helper/UserCtrl;->getUser()Lvn/com/bravesoft/androidapp/model/UserDTO;

    move-result-object v0

    if-eqz v0, :ret

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "{\"code\":\"\",\"message\":\"\",\"data\":{\"avatar\":\""

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    # avatar
    invoke-virtual {v0}, Lvn/com/bravesoft/androidapp/model/UserDTO;->getAvatarPath()Ljava/lang/String;

    move-result-object v2

    invoke-static {v2}, Lvn/com/bravesoft/androidapp/patch/PatchCheckInInfo;->esc(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v2, "\",\"date\":\""

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    # date = 端末現在日付 yyyy-MM-dd (parseTimeServerToDate が解釈できる形式)
    new-instance v2, Ljava/text/SimpleDateFormat;

    const-string v3, "yyyy-MM-dd"

    invoke-static {}, Ljava/util/Locale;->getDefault()Ljava/util/Locale;

    move-result-object v4

    invoke-direct {v2, v3, v4}, Ljava/text/SimpleDateFormat;-><init>(Ljava/lang/String;Ljava/util/Locale;)V

    new-instance v3, Ljava/util/Date;

    invoke-direct {v3}, Ljava/util/Date;-><init>()V

    invoke-virtual {v2, v3}, Ljava/text/SimpleDateFormat;->format(Ljava/util/Date;)Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v2, "\",\"name\":\""

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    # name
    invoke-virtual {v0}, Lvn/com/bravesoft/androidapp/model/UserDTO;->getName()Ljava/lang/String;

    move-result-object v2

    invoke-static {v2}, Lvn/com/bravesoft/androidapp/patch/PatchCheckInInfo;->esc(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v2, "\",\"room_number\":\""

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    # room_number
    invoke-virtual {v0}, Lvn/com/bravesoft/androidapp/model/UserDTO;->getRoomNumber()Ljava/lang/String;

    move-result-object v2

    invoke-static {v2}, Lvn/com/bravesoft/androidapp/patch/PatchCheckInInfo;->esc(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v2, "\",\"type_of_food\":"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    # type_of_food: 朝食かつ和食→1 / 朝食かつ洋食→2 / 夕食→2(無視される)
    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->isBreakfast()Z

    move-result v2

    if-eqz v2, :food_dinner

    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->isJapanFoodReserved()Z

    move-result v2

    if-eqz v2, :food_western

    const-string v2, "1"

    goto :food_append

    :food_western
    const-string v2, "2"

    goto :food_append

    :food_dinner
    const-string v2, "2"

    :food_append
    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v2, ",\"type_of_meal\":"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    # type_of_meal: 朝食→1 / 夕食→2
    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;->isBreakfast()Z

    move-result v2

    if-eqz v2, :meal_dinner

    const-string v2, "1"

    goto :meal_append

    :meal_dinner
    const-string v2, "2"

    :meal_append
    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v2, "}}"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    sput-object v2, Lvn/com/bravesoft/androidapp/patch/PatchCheckInInfo;->json:Ljava/lang/String;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0

    :ret
    return-void

    :catch_0
    move-exception v0

    return-void
.end method
