.class public final Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;
.super Ljava/lang/Object;
.source "PatchLoadingState.java"

# domico-patch: tracks in-flight mutating (non-GET) requests and, while any are
# active, places a transparent click-consuming overlay over the current Activity
# so the user cannot re-trigger a submit. GET/navigation loads are unaffected
# (those overlays are made click-through in FrameLayoutLoading).

# interfaces
.implements Ljava/lang/Runnable;


# static fields
.field static blocker:Landroid/view/View;

.field static count:Ljava/util/concurrent/atomic/AtomicInteger;

.field static currentActivity:Ljava/lang/ref/WeakReference;

.field static handler:Landroid/os/Handler;

.field static final INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;


# direct methods
.method static constructor <clinit>()V
    .locals 1

    new-instance v0, Ljava/util/concurrent/atomic/AtomicInteger;

    invoke-direct {v0}, Ljava/util/concurrent/atomic/AtomicInteger;-><init>()V

    sput-object v0, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->count:Ljava/util/concurrent/atomic/AtomicInteger;

    new-instance v0, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;

    invoke-direct {v0}, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;-><init>()V

    sput-object v0, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method private static handler()Landroid/os/Handler;
    .locals 2

    sget-object v0, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->handler:Landroid/os/Handler;

    if-eqz v0, :cond_0

    return-object v0

    :cond_0
    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v0

    new-instance v1, Landroid/os/Handler;

    invoke-direct {v1, v0}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V

    sput-object v1, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->handler:Landroid/os/Handler;

    return-object v1
.end method

.method private static postSync()V
    .locals 2

    invoke-static {}, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->handler()Landroid/os/Handler;

    move-result-object v0

    sget-object v1, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->INSTANCE:Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;

    invoke-virtual {v0, v1}, Landroid/os/Handler;->removeCallbacks(Ljava/lang/Runnable;)V

    invoke-virtual {v0, v1}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    return-void
.end method

.method public static enter()V
    .locals 1

    sget-object v0, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->count:Ljava/util/concurrent/atomic/AtomicInteger;

    invoke-virtual {v0}, Ljava/util/concurrent/atomic/AtomicInteger;->incrementAndGet()I

    invoke-static {}, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->postSync()V

    return-void
.end method

.method public static exit()V
    .locals 2

    sget-object v0, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->count:Ljava/util/concurrent/atomic/AtomicInteger;

    invoke-virtual {v0}, Ljava/util/concurrent/atomic/AtomicInteger;->decrementAndGet()I

    move-result v1

    if-gez v1, :cond_0

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Ljava/util/concurrent/atomic/AtomicInteger;->set(I)V

    :cond_0
    invoke-static {}, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->postSync()V

    return-void
.end method

.method public static setActivity(Landroid/app/Activity;)V
    .locals 1

    new-instance v0, Ljava/lang/ref/WeakReference;

    invoke-direct {v0, p0}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V

    sput-object v0, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->currentActivity:Ljava/lang/ref/WeakReference;

    return-void
.end method


# virtual methods
.method public run()V
    .locals 6

    :try_start_0
    sget-object v0, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->count:Ljava/util/concurrent/atomic/AtomicInteger;

    invoke-virtual {v0}, Ljava/util/concurrent/atomic/AtomicInteger;->get()I

    move-result v0

    const/4 v1, 0x0

    if-lez v0, :cond_0

    sget-boolean v1, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->loadingEnabled:Z

    :cond_0
    sget-object v2, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->blocker:Landroid/view/View;

    if-eqz v1, :cond_4

    if-nez v2, :cond_3

    sget-object v3, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->currentActivity:Ljava/lang/ref/WeakReference;

    if-eqz v3, :cond_3

    invoke-virtual {v3}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;

    move-result-object v3

    if-eqz v3, :cond_3

    check-cast v3, Landroid/app/Activity;

    new-instance v4, Landroid/view/View;

    invoke-direct {v4, v3}, Landroid/view/View;-><init>(Landroid/content/Context;)V

    const/4 v5, 0x1

    invoke-virtual {v4, v5}, Landroid/view/View;->setClickable(Z)V

    new-instance v5, Landroid/widget/FrameLayout$LayoutParams;

    const/4 v0, -0x1

    invoke-direct {v5, v0, v0}, Landroid/widget/FrameLayout$LayoutParams;-><init>(II)V

    invoke-virtual {v3, v4, v5}, Landroid/app/Activity;->addContentView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V

    sput-object v4, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->blocker:Landroid/view/View;

    goto :cond_3

    :cond_4
    if-eqz v2, :cond_3

    invoke-virtual {v2}, Landroid/view/View;->getParent()Landroid/view/ViewParent;

    move-result-object v3

    instance-of v4, v3, Landroid/view/ViewGroup;

    if-eqz v4, :cond_2

    check-cast v3, Landroid/view/ViewGroup;

    invoke-virtual {v3, v2}, Landroid/view/ViewGroup;->removeView(Landroid/view/View;)V

    :cond_2
    const/4 v3, 0x0

    sput-object v3, Lvn/com/bravesoft/androidapp/patch/PatchLoadingState;->blocker:Landroid/view/View;

    :cond_3
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    return-void
.end method
