package vn.com.bravesoft.androidapp.patch;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.WeakHashMap;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

/**
 * ダークモード (Android 10+ の HWUI Force Dark を実行時に有効化する)。
 *
 * <p>公式アプリはライトテーマ固定で、テーマに {@code android:forceDarkAllowed} も無い。
 * resources は公式とバイト一致に保つ方針なのでテーマ側は触れない。代わりに、
 * {@code ViewRootImpl#updateForceDarkMode()} が最終的に行う
 * {@code mAttachInfo.mThreadedRenderer.setForceDark(...)} を各ウィンドウへ直接呼ぶ。
 * 描画結果は OS が forceDarkAllowed なアプリに掛ける自動ダークと同じになる。
 *
 * <p>{@code setForceDark} と {@code AttachInfo#mThreadedRenderer} は隠し API の
 * blocklist 側なので、LSPosed の HiddenApiBypass で {@code android/view} と
 * {@code HardwareRenderer} だけを除外対象にする。
 *
 * <p>新しいウィンドウ (Activity・Dialog・PopupWindow) は
 * {@code WindowManagerGlobal#mRoots} を add() 監視付きリストへ差し替えて拾う。
 * OS の設定変更時は {@code ViewRootImpl} が自前の判定で上書きし直すため、
 * 設定変更・Activity 復帰のたびに再適用する。
 *
 * <p>失敗しても公式の動作には影響させない (全て catch して機能だけ止める)。
 * 隠し API 回避はネイティブで落ちる可能性をゼロにできないので、準備中に
 * プロセスが死んだら次回起動時に自動でオフへ戻すクラッシュガードを持つ。
 */
public final class PatchDarkMode {

    static final String TAG = "domico-patch";

    public static final int MODE_OFF = 0;
    public static final int MODE_SYSTEM = 1;
    public static final int MODE_ALWAYS = 2;

    /** 準備中なら true。次回起動時に残っていれば「準備中に落ちた」とみなす。 */
    private static final String KEY_GUARD = "dark_mode_guard";

    /** android.graphics.ForceDarkType (API 35+) の値。 */
    private static final int FORCE_DARK_NONE = 0;
    private static final int FORCE_DARK_ON = 1;

    static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Application sApp;
    private static volatile WeakReference<Activity> sTopActivity;
    private static boolean sPrepared;
    private static boolean sFailed;

    private static Field sRootAttachInfo;   // ViewRootImpl#mAttachInfo
    private static Field sRootView;         // ViewRootImpl#mView
    private static Field sAttachRenderer;   // View$AttachInfo#mThreadedRenderer
    private static Method sSetForceDark;    // HardwareRenderer#setForceDark(Z|I)
    private static boolean sSetForceDarkInt;
    private static Method sUpdateForceDark; // ViewRootImpl#updateForceDarkMode()
    private static Object sWmGlobal;        // WindowManagerGlobal
    private static Field sWmRoots;          // WindowManagerGlobal#mRoots
    private static Object sWmLock;          // WindowManagerGlobal#mLock

    /** システムバーの見た目をこのパッチが書き換えたウィンドウと、その元の値。 */
    private static final WeakHashMap<Window, Integer> sOrigBarAppearance = new WeakHashMap<>();

    private PatchDarkMode() {
    }

    /** {@link PatchInit#onAppCreate} から。{@link PatchPrefs#load} の後に呼ぶこと。 */
    public static void init(Application app) {
        if (app == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        sApp = app;
        try {
            SharedPreferences sp = app.getSharedPreferences(PatchPrefs.FILE, 0);
            if (sp.getBoolean(KEY_GUARD, false)) {
                // 前回、準備の途中でプロセスが死んだ。起動ループを防ぐためオフへ戻す。
                Log.w(TAG, "PatchDarkMode: previous prepare crashed, forcing mode OFF");
                sp.edit().putBoolean(KEY_GUARD, false).commit();
                PatchPrefs.setInt(app, PatchPrefs.KEY_DARK_MODE, MODE_OFF);
            }
            app.registerComponentCallbacks(new ComponentCallbacks() {
                @Override
                public void onConfigurationChanged(Configuration newConfig) {
                    // ViewRootImpl#updateConfiguration の updateForceDarkMode() より
                    // 後に走らせたいので、1フレーム程度遅らせる。
                    MAIN.postDelayed(PatchDarkMode::reapplyAll, 50);
                }

                @Override
                public void onLowMemory() {
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "PatchDarkMode.init failed", t);
        }
        if (PatchPrefs.darkMode != MODE_OFF) {
            prepare();
        }
    }

    /** 設定画面でモードを変えたあとに呼ぶ。再起動なしで全ウィンドウへ反映する。 */
    public static void onModeChanged() {
        if (sApp == null) {
            return;
        }
        if (PatchPrefs.darkMode != MODE_OFF) {
            prepare();
        }
        MAIN.post(PatchDarkMode::reapplyAll);
    }

    /** Activity 復帰時。設定変更で上書きされた分を戻し、システムバーも合わせる。 */
    static void onActivityResumed(Activity activity) {
        if (activity == null) {
            return;
        }
        sTopActivity = new WeakReference<Activity>(activity);
        if (!sPrepared) {
            return;
        }
        reapplyAll();
    }

    /** 今の設定で暗くするべきか。 */
    static boolean shouldDark() {
        int mode = PatchPrefs.darkMode;
        if (mode == MODE_ALWAYS) {
            return true;
        }
        if (mode == MODE_SYSTEM && sApp != null) {
            int night = sApp.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return night == Configuration.UI_MODE_NIGHT_YES;
        }
        return false;
    }

    // ---- 準備 (隠し API の解決とウィンドウ監視) --------------------------------

    private static synchronized void prepare() {
        if (sPrepared || sFailed || sApp == null) {
            return;
        }
        SharedPreferences sp = sApp.getSharedPreferences(PatchPrefs.FILE, 0);
        sp.edit().putBoolean(KEY_GUARD, true).commit();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions(
                        "Landroid/view/", "Landroid/graphics/HardwareRenderer;");
            }

            Class<?> vri = Class.forName("android.view.ViewRootImpl");
            sRootAttachInfo = accessible(vri.getDeclaredField("mAttachInfo"));
            sRootView = accessible(vri.getDeclaredField("mView"));
            sUpdateForceDark = accessible(vri.getDeclaredMethod("updateForceDarkMode"));

            Class<?> attachInfo = Class.forName("android.view.View$AttachInfo");
            sAttachRenderer = accessible(attachInfo.getDeclaredField("mThreadedRenderer"));

            Class<?> hwr = Class.forName("android.graphics.HardwareRenderer");
            try {
                sSetForceDark = accessible(hwr.getDeclaredMethod("setForceDark", int.class));
                sSetForceDarkInt = true;
            } catch (NoSuchMethodException e) {
                sSetForceDark = accessible(hwr.getDeclaredMethod("setForceDark", boolean.class));
                sSetForceDarkInt = false;
            }

            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            sWmGlobal = wmg.getDeclaredMethod("getInstance").invoke(null);
            sWmRoots = accessible(wmg.getDeclaredField("mRoots"));
            sWmLock = accessible(wmg.getDeclaredField("mLock")).get(sWmGlobal);

            installRootWatcher();
            sPrepared = true;
            Log.i(TAG, "PatchDarkMode: prepared (setForceDark("
                    + (sSetForceDarkInt ? "int" : "boolean") + "))");
        } catch (Throwable t) {
            sFailed = true;
            Log.w(TAG, "PatchDarkMode: prepare failed, dark mode disabled", t);
        } finally {
            sp.edit().putBoolean(KEY_GUARD, false).commit();
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T member) {
        member.setAccessible(true);
        return member;
    }

    /** mRoots を、add() されたら適用を予約するリストに差し替える。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void installRootWatcher() throws IllegalAccessException {
        synchronized (sWmLock) {
            Object cur = sWmRoots.get(sWmGlobal);
            if (cur instanceof RootWatcher) {
                return;
            }
            sWmRoots.set(sWmGlobal, new RootWatcher((Collection) cur));
        }
    }

    /**
     * {@code WindowManagerGlobal#addView} は mRoots.add() → setView() (この中で
     * renderer 生成と updateForceDarkMode) の順なので、add 時点ではまだ renderer が
     * 無い。メインスレッドへ post して setView の後に適用する。
     */
    static final class RootWatcher extends ArrayList<Object> {

        RootWatcher(Collection<Object> initial) {
            super(initial);
        }

        @Override
        public boolean add(Object root) {
            boolean r = super.add(root);
            MAIN.post(new ApplyRoot(root));
            return r;
        }
    }

    static final class ApplyRoot implements Runnable {

        private final Object root;

        ApplyRoot(Object root) {
            this.root = root;
        }

        @Override
        public void run() {
            applyToRoot(root, shouldDark());
        }
    }

    // ---- 適用 -------------------------------------------------------------------

    static void reapplyAll() {
        if (!sPrepared) {
            return;
        }
        Object[] roots;
        try {
            synchronized (sWmLock) {
                roots = ((ArrayList<?>) sWmRoots.get(sWmGlobal)).toArray();
            }
        } catch (Throwable t) {
            Log.w(TAG, "PatchDarkMode: cannot list roots", t);
            return;
        }
        boolean dark = shouldDark();
        for (Object root : roots) {
            applyToRoot(root, dark);
        }
    }

    static void applyToRoot(Object root, boolean dark) {
        if (!sPrepared || root == null) {
            return;
        }
        try {
            View view = (View) sRootView.get(root);
            if (PatchPrefs.darkMode == MODE_OFF) {
                // 公式の判定に戻す (開発者オプションの強制ダークなどはそのまま尊重される)。
                sUpdateForceDark.invoke(root);
                updateSystemBars(view, false);
                return;
            }
            Object attachInfo = sRootAttachInfo.get(root);
            Object renderer = attachInfo == null ? null : sAttachRenderer.get(attachInfo);
            if (renderer == null) {
                return;
            }
            Object arg = sSetForceDarkInt
                    ? (Object) Integer.valueOf(dark ? FORCE_DARK_ON : FORCE_DARK_NONE)
                    : (Object) Boolean.valueOf(dark);
            Object changed = sSetForceDark.invoke(renderer, arg);
            if (Boolean.TRUE.equals(changed) && view != null) {
                invalidateWorld(view);
            }
            updateSystemBars(view, dark);
        } catch (Throwable t) {
            Log.w(TAG, "PatchDarkMode: apply failed", t);
        }
    }

    /** ViewRootImpl#invalidateWorld と同じ。表示リストを全て録り直させる。 */
    private static void invalidateWorld(View view) {
        view.invalidate();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                invalidateWorld(group.getChildAt(i));
            }
        }
    }

    /**
     * Force Dark はシステムバーのアイコン色を変えないので、暗い背景の上で
     * 黒アイコンにならないよう「ライト用(黒アイコン)」指定を外す。オフに戻すときは元の値へ。
     */
    private static void updateSystemBars(View decor, boolean dark) {
        if (decor == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        Window window = windowOf(decor);
        if (window == null) {
            return;
        }
        WindowInsetsController ic = window.getInsetsController();
        if (ic == null) {
            return;
        }
        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        Integer orig = sOrigBarAppearance.get(window);
        if (dark) {
            if (orig == null) {
                sOrigBarAppearance.put(window, ic.getSystemBarsAppearance() & mask);
            }
            ic.setSystemBarsAppearance(0, mask);
        } else if (orig != null) {
            ic.setSystemBarsAppearance(orig, mask);
            sOrigBarAppearance.remove(window);
        }
    }

    /** decor が前面 Activity のウィンドウのものならそのウィンドウ。Dialog 等は null。 */
    private static Window windowOf(View decor) {
        WeakReference<Activity> ref = sTopActivity;
        Activity top = ref == null ? null : ref.get();
        if (top == null) {
            return null;
        }
        Window window = top.getWindow();
        return window != null && window.peekDecorView() == decor ? window : null;
    }
}
