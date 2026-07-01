.class public final Lvn/com/bravesoft/androidapp/patch/PatchCheckInBypass;
.super Ljava/lang/Object;
.source "PatchCheckInBypass.java"

# domico-patch: checkin 時間外エラーをネットワーク層でフェイク成功に差し替え。
# PatchPrefs.checkinEnabled が ON のとき、checkin 系の 2 つの通信に介入する:
#   (1) POST */checkin が 4xx かつボディに "E1015" を含む → HTTP 200 + 空 BaseResponse を返し
#       アプリ側を成功扱い(onCheckInSuccess→CheckInCompletedDialog 表示)にする。
#   (2) GET */{id}/checkin (受け取り情報) が 4xx (例: 「すでにキャンセル」) → PatchCheckInInfo に
#       端末側で合成された受け取り画面データ(JSON)があれば HTTP 200 + その JSON に差し替える。
#       これにより受け取り画面のアバター/氏名/部屋番号/和洋食/朝夕食が本来通り表示される。
# サーバー側のチェックイン記録は行われない(表示は端末側合成)。

# interfaces
.implements Lokhttp3/Interceptor;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static add(Lokhttp3/OkHttpClient$Builder;)Lokhttp3/OkHttpClient$Builder;
    .locals 1

    new-instance v0, Lvn/com/bravesoft/androidapp/patch/PatchCheckInBypass;

    invoke-direct {v0}, Lvn/com/bravesoft/androidapp/patch/PatchCheckInBypass;-><init>()V

    invoke-virtual {p0, v0}, Lokhttp3/OkHttpClient$Builder;->addInterceptor(Lokhttp3/Interceptor;)Lokhttp3/OkHttpClient$Builder;

    move-result-object p0

    return-object p0
.end method


# virtual methods
.method public intercept(Lokhttp3/Interceptor$Chain;)Lokhttp3/Response;
    .locals 6
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    # NOTE: 本メソッドは全リクエストに介入する。以下は対象2エンドポイント以外へ
    # 副作用を及ぼさないための条件: (a) checkinEnabled ON (b) パスが
    # */reservations/*checkin に一致 (c) 4xx (isSuccessful==false)。
    # 機能が OFF なら素通り
    sget-boolean v0, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->checkinEnabled:Z

    invoke-interface {p1}, Lokhttp3/Interceptor$Chain;->request()Lokhttp3/Request;

    move-result-object v1

    invoke-interface {p1, v1}, Lokhttp3/Interceptor$Chain;->proceed(Lokhttp3/Request;)Lokhttp3/Response;

    move-result-object v2

    if-eqz v0, :ret

    :try_start_0
    # 対象は v1/reservations/checkin (POST) と v1/reservations/{id}/checkin (GET) のみ。
    # "/reservations/" を含み "/checkin" で終わるパスに厳密一致させ、無関係な
    # エンドポイント(例: "recheckin" 等の部分一致)を誤って差し替えないようにする。
    invoke-virtual {v1}, Lokhttp3/Request;->url()Lokhttp3/HttpUrl;

    move-result-object v3

    invoke-virtual {v3}, Lokhttp3/HttpUrl;->encodedPath()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object v3

    const-string v4, "/reservations/"

    invoke-virtual {v3, v4}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v4

    if-eqz v4, :ret

    const-string v4, "/checkin"

    invoke-virtual {v3, v4}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :ret

    # メソッド判定: GET=受け取り情報 / それ以外(POST)=チェックイン送信
    invoke-virtual {v1}, Lokhttp3/Request;->method()Ljava/lang/String;

    move-result-object v3

    const-string v4, "GET"

    invoke-virtual {v4, v3}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :post_checkin

    # ---- (2) GET 受け取り情報: 合成 JSON で差し替え ----
    # サーバーが実データを返したなら素通り(合成データは消費せず保持したままにする。
    # 先に consume() すると、無関係な checkin GET がここを通っただけで
    # 保留中の合成データが失われてしまうため、失敗時のみ消費する)
    invoke-virtual {v2}, Lokhttp3/Response;->isSuccessful()Z

    move-result v4

    if-nez v4, :ret

    # 合成データを消費。無ければエラーのまま素通り
    invoke-static {}, Lvn/com/bravesoft/androidapp/patch/PatchCheckInInfo;->consume()Ljava/lang/String;

    move-result-object v3

    if-eqz v3, :ret

    const-string v4, "application/json; charset=utf-8"

    invoke-static {v4}, Lokhttp3/MediaType;->parse(Ljava/lang/String;)Lokhttp3/MediaType;

    move-result-object v4

    invoke-static {v4, v3}, Lokhttp3/ResponseBody;->create(Lokhttp3/MediaType;Ljava/lang/String;)Lokhttp3/ResponseBody;

    move-result-object v3

    invoke-virtual {v2}, Lokhttp3/Response;->newBuilder()Lokhttp3/Response$Builder;

    move-result-object v4

    const/16 v5, 0xc8

    invoke-virtual {v4, v5}, Lokhttp3/Response$Builder;->code(I)Lokhttp3/Response$Builder;

    move-result-object v4

    const-string v5, "OK"

    invoke-virtual {v4, v5}, Lokhttp3/Response$Builder;->message(Ljava/lang/String;)Lokhttp3/Response$Builder;

    move-result-object v4

    invoke-virtual {v4, v3}, Lokhttp3/Response$Builder;->body(Lokhttp3/ResponseBody;)Lokhttp3/Response$Builder;

    move-result-object v4

    invoke-virtual {v4}, Lokhttp3/Response$Builder;->build()Lokhttp3/Response;

    move-result-object v2

    goto :ret

    # ---- (1) POST チェックイン送信: E1015 を成功に差し替え ----
    :post_checkin
    # 成功レスポンスなら素通り
    invoke-virtual {v2}, Lokhttp3/Response;->isSuccessful()Z

    move-result v3

    if-nez v3, :ret

    # ボディ取得
    invoke-virtual {v2}, Lokhttp3/Response;->body()Lokhttp3/ResponseBody;

    move-result-object v3

    if-eqz v3, :ret

    # ボディを文字列として読む（一度しか読めないので消費）
    invoke-virtual {v3}, Lokhttp3/ResponseBody;->string()Ljava/lang/String;

    move-result-object v4

    # "E1015" を含むか
    const-string v5, "E1015"

    invoke-virtual {v4, v5}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v5

    if-eqz v5, :rebuild_with_body

    # ---- E1015 を成功に差し替え ----
    const-string v3, "application/json; charset=utf-8"

    invoke-static {v3}, Lokhttp3/MediaType;->parse(Ljava/lang/String;)Lokhttp3/MediaType;

    move-result-object v3

    # BaseResponse 成功形式: code="" message=""
    const-string v4, "{\"code\":\"\",\"message\":\"\"}"

    invoke-static {v3, v4}, Lokhttp3/ResponseBody;->create(Lokhttp3/MediaType;Ljava/lang/String;)Lokhttp3/ResponseBody;

    move-result-object v4

    invoke-virtual {v2}, Lokhttp3/Response;->newBuilder()Lokhttp3/Response$Builder;

    move-result-object v5

    const/16 v3, 0xc8

    invoke-virtual {v5, v3}, Lokhttp3/Response$Builder;->code(I)Lokhttp3/Response$Builder;

    move-result-object v5

    const-string v3, "OK"

    invoke-virtual {v5, v3}, Lokhttp3/Response$Builder;->message(Ljava/lang/String;)Lokhttp3/Response$Builder;

    move-result-object v5

    invoke-virtual {v5, v4}, Lokhttp3/Response$Builder;->body(Lokhttp3/ResponseBody;)Lokhttp3/Response$Builder;

    move-result-object v5

    invoke-virtual {v5}, Lokhttp3/Response$Builder;->build()Lokhttp3/Response;

    move-result-object v2

    goto :ret

    # ---- E1015 以外のエラー: 消費したボディを再包んで返す ----
    :rebuild_with_body
    invoke-virtual {v3}, Lokhttp3/ResponseBody;->contentType()Lokhttp3/MediaType;

    move-result-object v5

    invoke-static {v5, v4}, Lokhttp3/ResponseBody;->create(Lokhttp3/MediaType;Ljava/lang/String;)Lokhttp3/ResponseBody;

    move-result-object v5

    invoke-virtual {v2}, Lokhttp3/Response;->newBuilder()Lokhttp3/Response$Builder;

    move-result-object v3

    invoke-virtual {v3, v5}, Lokhttp3/Response$Builder;->body(Lokhttp3/ResponseBody;)Lokhttp3/Response$Builder;

    move-result-object v3

    invoke-virtual {v3}, Lokhttp3/Response$Builder;->build()Lokhttp3/Response;

    move-result-object v2

    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0

    :ret
    return-object v2

    :catch_0
    move-exception v0

    return-object v2
.end method
