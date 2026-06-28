.class public final Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;
.super Ljava/lang/Object;
.source "PatchTrafficInterceptor.java"

# domico-patch: marks the window of non-GET (mutating) requests as in-flight so
# PatchLoadingState can block re-submission for exactly their duration. GET
# requests pass through untouched.

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

    new-instance v0, Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;

    invoke-direct {v0}, Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;-><init>()V

    invoke-virtual {p0, v0}, Lokhttp3/OkHttpClient$Builder;->addInterceptor(Lokhttp3/Interceptor;)Lokhttp3/OkHttpClient$Builder;

    move-result-object v0

    return-object v0
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

    invoke-virtual {v0}, Lokhttp3/Request;->method()Ljava/lang/String;

    move-result-object v1

    const-string v2, "GET"

    invoke-virtual {v2, v1}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v1

    if-nez v1, :cond_0

    const/4 v1, 0x1

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
