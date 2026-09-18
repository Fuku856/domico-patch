package vn.com.bravesoft.androidapp.patch;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 送信系(非GET)通信の実行中だけ、透明なクリック吸収オーバーレイを現在の
 * Activity に被せて二重送信を防ぐ。
 *
 * <p>画面遷移・取得ロードのスクリムは FrameLayoutLoading 側でクリックスルー化
 * されるため、ここでは扱わない。{@code enter}/{@code exit} は classes4 に残る
 * PatchTrafficInterceptor から呼ばれる。
 */
public final class PatchLoadingState implements Runnable {

    static final PatchLoadingState INSTANCE = new PatchLoadingState();

    static final AtomicInteger count = new AtomicInteger();
    static volatile Handler handler;
    static WeakReference<Activity> currentActivity;
    static View blocker;

    private static Handler handler() {
        Handler h = handler;
        if (h != null) {
            return h;
        }
        h = new Handler(Looper.getMainLooper());
        handler = h;
        return h;
    }

    private static void postSync() {
        Handler h = handler();
        h.removeCallbacks(INSTANCE);
        h.post(INSTANCE);
    }

    public static void enter() {
        count.incrementAndGet();
        postSync();
    }

    public static void exit() {
        if (count.decrementAndGet() < 0) {
            count.set(0);
        }
        postSync();
    }

    public static void setActivity(Activity activity) {
        currentActivity = new WeakReference<Activity>(activity);
    }

    @Override
    public void run() {
        try {
            boolean want = false;
            if (count.get() > 0) {
                want = PatchPrefs.loadingEnabled;
            }
            View b = blocker;
            if (want) {
                if (b == null) {
                    WeakReference<Activity> ref = currentActivity;
                    if (ref != null) {
                        Activity a = ref.get();
                        if (a != null) {
                            View v = new View(a);
                            v.setClickable(true);
                            a.addContentView(v, new FrameLayout.LayoutParams(-1, -1));
                            blocker = v;
                        }
                    }
                }
            } else if (b != null) {
                ViewParent parent = b.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(b);
                }
                blocker = null;
            }
        } catch (Throwable ignored) {
            // 画面状態に依存するため、失敗してもアプリ側には影響させない。
        }
    }
}
