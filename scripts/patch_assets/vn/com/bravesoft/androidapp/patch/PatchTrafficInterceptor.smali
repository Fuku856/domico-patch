.class public final Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;
.super Ljava/lang/Object;
.source "PatchTrafficInterceptor.java"

# domico-patch: marks the window of *user-submitting* (mutating) requests as
# in-flight so PatchLoadingState can block re-submission for exactly their
# duration. Classification is PATH-based, not merely "method != GET": this app
# issues many read/validation calls over POST (e.g. */check-*, */validate,
# menus/*) which must stay click-through while "ロード中の操作を許可" is on.
#
# ベースアプリ更新への耐性:
#   - 判定はバージョン接頭辞(v1/v1.5..)や {id} を含まない「安定パスセグメント」の
#     部分一致で行う。番号や ID が変わっても影響しない。
#   - WRITE_MARKERS = 送信(mutation)が起きるリソース群。READ_MARKERS = その配下
#     でも遮断しない読取/検証パス(READ を優先評価するため checkin 等は誤除外しない)。
#   - 既知 WRITE 群配下の新エンドポイントは自動的に対象。未知の新リソースは既定で
#     素通り(過剰遮断を起こさない)。送信群が増えたら WRITE_MARKERS に1語足すだけ。

# interfaces
.implements Lokhttp3/Interceptor;


# static fields
.field static final READ_MARKERS:[Ljava/lang/String;

.field static final WRITE_MARKERS:[Ljava/lang/String;


# direct methods
.method static constructor <clinit>()V
    .locals 3

    # READ_MARKERS = {"check-", "validate"}
    # "check-" はハイフン必須: "checkin"(送信)を誤って除外しないため。
    const/4 v0, 0x2

    new-array v0, v0, [Ljava/lang/String;

    const/4 v1, 0x0

    const-string v2, "check-"

    aput-object v2, v0, v1

    const/4 v1, 0x1

    const-string v2, "validate"

    aput-object v2, v0, v1

    sput-object v0, Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;->READ_MARKERS:[Ljava/lang/String;

    # WRITE_MARKERS = 送信が起きるリソース群(menus/* は意図的に除外=取得系)
    const/4 v0, 0x7

    new-array v0, v0, [Ljava/lang/String;

    const/4 v1, 0x0

    const-string v2, "users"

    aput-object v2, v0, v1

    const/4 v1, 0x1

    const-string v2, "reservations"

    aput-object v2, v0, v1

    const/4 v1, 0x2

    const-string v2, "contracts"

    aput-object v2, v0, v1

    const/4 v1, 0x3

    const-string v2, "overnight-report"

    aput-object v2, v0, v1

    const/4 v1, 0x4

    const-string v2, "notices"

    aput-object v2, v0, v1

    const/4 v1, 0x5

    const-string v2, "messages"

    aput-object v2, v0, v1

    const/4 v1, 0x6

    const-string v2, "survey"

    aput-object v2, v0, v1

    sput-object v0, Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;->WRITE_MARKERS:[Ljava/lang/String;

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static add(Lokhttp3/OkHttpClient$Builder;)Lokhttp3/OkHttpClient$Builder;
    .locals 1

    new-instance v0, Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;

    invoke-direct {v0}, Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;-><init>()V

    invoke-virtual {p0, v0}, Lokhttp3/OkHttpClient$Builder;->addInterceptor(Lokhttp3/Interceptor;)Lokhttp3/OkHttpClient$Builder;

    move-result-object v0

    return-object v0
.end method

.method private static anyContains(Ljava/lang/String;[Ljava/lang/String;)Z
    .locals 4

    # p0 = haystack (lower-cased), p1 = needles. true if any needle is a substring.
    array-length v0, p1

    const/4 v1, 0x0

    :goto_0
    if-ge v1, v0, :cond_1

    aget-object v2, p1, v1

    invoke-virtual {p0, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v3

    if-eqz v3, :cond_0

    const/4 v0, 0x1

    return v0

    :cond_0
    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_1
    const/4 v0, 0x0

    return v0
.end method

.method static isMutation(Ljava/lang/String;)Z
    .locals 2

    # p0 = encoded path (nullable). 読取/検証パスは READ を優先して非対象に。
    if-nez p0, :cond_0

    const/4 v0, 0x0

    return v0

    :cond_0
    invoke-virtual {p0}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object p0

    sget-object v0, Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;->READ_MARKERS:[Ljava/lang/String;

    invoke-static {p0, v0}, Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;->anyContains(Ljava/lang/String;[Ljava/lang/String;)Z

    move-result v1

    if-eqz v1, :cond_1

    const/4 v0, 0x0

    return v0

    :cond_1
    sget-object v0, Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;->WRITE_MARKERS:[Ljava/lang/String;

    invoke-static {p0, v0}, Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;->anyContains(Ljava/lang/String;[Ljava/lang/String;)Z

    move-result v0

    return v0
.end method


# virtual methods
.method public intercept(Lokhttp3/Interceptor$Chain;)Lokhttp3/Response;
    .locals 3
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    invoke-interface {p1}, Lokhttp3/Interceptor$Chain;->request()Lokhttp3/Request;

    move-result-object v0

    # guard = (method != GET) && isMutation(url path)
    invoke-virtual {v0}, Lokhttp3/Request;->method()Ljava/lang/String;

    move-result-object v1

    const-string v2, "GET"

    invoke-virtual {v2, v1}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v1

    if-nez v1, :cond_0

    invoke-virtual {v0}, Lokhttp3/Request;->url()Lokhttp3/HttpUrl;

    move-result-object v1

    invoke-virtual {v1}, Lokhttp3/HttpUrl;->encodedPath()Ljava/lang/String;

    move-result-object v1

    invoke-static {v1}, Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;->isMutation(Ljava/lang/String;)Z

    move-result v1

    goto :goto_0

    :cond_0
    const/4 v1, 0x0

    :goto_0
    if-eqz v1, :cond_1

    invoke-static {}, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->enter()V

    :cond_1
    :try_start_0
    invoke-interface {p1, v0}, Lokhttp3/Interceptor$Chain;->proceed(Lokhttp3/Request;)Lokhttp3/Response;

    move-result-object v2
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0

    if-eqz v1, :cond_2

    invoke-static {}, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->exit()V

    :cond_2
    return-object v2

    :catch_0
    move-exception v2

    if-eqz v1, :cond_3

    invoke-static {}, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->exit()V

    :cond_3
    throw v2
.end method
