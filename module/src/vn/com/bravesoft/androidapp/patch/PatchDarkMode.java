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
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
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
 * 設定変更・Activity 復帰のたびに再適用する。さらにウィンドウごとに描画前リスナーを付け、
 * 毎フレーム「暗くする」指定を保ち直す (Dialog やボトムシートは表示直後の構成変更で
 * OS 側に戻されることがあるため)。色の見え方の補正は {@link PatchDarkTint}。
 *
 * <p>食堂で係の人に見せるチェックイン完了画面 ({@code CheckInCompletedDialog}) は
 * 常に公式どおりのライト表示にする。
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
    static boolean sPrepared;
    private static boolean sFailed;

    private static Field sRootAttachInfo;   // ViewRootImpl#mAttachInfo
    static Field sRootView;                 // ViewRootImpl#mView
    private static Field sAttachRenderer;   // View$AttachInfo#mThreadedRenderer
    private static Field sAttachObserver;   // View$AttachInfo#mTreeObserver
    private static Method sSetForceDark;    // HardwareRenderer#setForceDark(Z|I)
    private static boolean sSetForceDarkInt;
    private static Method sUpdateForceDark; // ViewRootImpl#updateForceDarkMode()
    private static Object sWmGlobal;        // WindowManagerGlobal
    private static Field sWmRoots;          // WindowManagerGlobal#mRoots
    private static Object sWmLock;          // WindowManagerGlobal#mLock

    /** システムバーの見た目をこのパッチが書き換えたウィンドウと、その元の値。 */
    private static final WeakHashMap<Window, Integer> sOrigBarAppearance = new WeakHashMap<>();

    /** 描画前リスナーを付けたウィンドウ (ViewRootImpl)。 */
    static final WeakHashMap<Object, RootHook> sHooks = new WeakHashMap<>();

    /** ダークにしない画面 (DialogFragment のクラス名)。 */
    static final String[] LIGHT_ONLY_FRAGMENTS = {
            "vn.com.bravesoft.androidapp.ui.CheckInCompletedDialog",
    };
    private static Method sFindFragment;    // androidx FragmentManager.findFragment(View)
    private static boolean sFindFragmentTried;

    private PatchDarkMode() {
    }

    /** {@link PatchInit#onAppCreate} から。{@link PatchPrefs#load} の後に呼ぶこと。 */
    public static void init(Application app) {
        if (app == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        sApp = app;
        PatchDarkTint.sStats = app.getPackageName().endsWith(".debug");
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
        WeakReference<Activity> ref = sTopActivity;
        Activity top = ref == null ? null : ref.get();
        if (top != null) {
            updateSplashTheme(top);
        }
    }

    /** Activity 復帰時。設定変更で上書きされた分を戻し、システムバーも合わせる。 */
    static void onActivityResumed(Activity activity) {
        if (activity == null) {
            return;
        }
        sTopActivity = new WeakReference<Activity>(activity);
        updateSplashTheme(activity);
        if (!sPrepared) {
            return;
        }
        reapplyAll();
    }

    /** 0 = 未設定、1 = 黒、2 = 既定 に設定済み (毎回の書き込みを避ける)。 */
    private static int sSplashState;

    /**
     * 起動直後に OS が出す画面 (スプラッシュ) は、アプリのコードが動く前に manifest の
     * テーマ (白地) で描かれるので、Force Dark では暗くできない。Android 13+ の
     * {@code SplashScreen#setSplashScreenTheme} で「次回以降の起動」に使うテーマを
     * 黒地のフレームワーク標準テーマへ切り替える。ダークをやめたら既定に戻す。
     */
    static void updateSplashTheme(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || activity == null) {
            return;
        }
        boolean dark = PatchPrefs.darkMode != MODE_OFF && shouldDark();
        int want = dark ? 1 : 2;
        if (sSplashState == want) {
            return;
        }
        try {
            activity.getSplashScreen().setSplashScreenTheme(
                    dark ? android.R.style.Theme_Black_NoTitleBar : 0);
            sSplashState = want;
        } catch (Throwable t) {
            Log.w(TAG, "PatchDarkMode: setSplashScreenTheme failed", t);
        }
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
                        "Landroid/view/", "Landroid/graphics/HardwareRenderer;",
                        "Landroid/graphics/drawable/");
            }

            Class<?> vri = Class.forName("android.view.ViewRootImpl");
            sRootAttachInfo = accessible(vri.getDeclaredField("mAttachInfo"));
            sRootView = accessible(vri.getDeclaredField("mView"));
            sUpdateForceDark = accessible(vri.getDeclaredMethod("updateForceDarkMode"));

            Class<?> attachInfo = Class.forName("android.view.View$AttachInfo");
            sAttachRenderer = accessible(attachInfo.getDeclaredField("mThreadedRenderer"));
            sAttachObserver = accessible(attachInfo.getDeclaredField("mTreeObserver"));

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
            // setView() の最初の描画より前に描画前リスナーを付けておくと、
            // 表示1フレーム目から暗くなる (白く光ってから暗くなるのを防ぐ)。
            if (Looper.myLooper() == Looper.getMainLooper()) {
                ensureHook(root);
            }
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
        if (!dark) {
            PatchDarkTint.restoreAll();
        }
        for (Object root : roots) {
            applyToRoot(root, dark);
        }
    }

    static void applyToRoot(Object root, boolean dark) {
        if (!sPrepared || root == null) {
            return;
        }
        try {
            ensureHook(root);
            View view = (View) sRootView.get(root);
            if (dark) {
                RootHook hook = sHooks.get(root);
                if (hook == null) {
                    hook = new RootHook(root); // リスナーを付けられなくても、この時点の適用はする
                }
                hook.enforce(view);
                return;
            }
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
            setForceDark(renderer, false, view);
            updateSystemBars(view, false);
        } catch (Throwable t) {
            Log.w(TAG, "PatchDarkMode: apply failed", t);
        }
    }

    static Object rendererOf(Object root) throws IllegalAccessException {
        Object attachInfo = sRootAttachInfo.get(root);
        return attachInfo == null ? null : sAttachRenderer.get(attachInfo);
    }

    /** 変化したときだけ表示リストを録り直させる。 */
    static void setForceDark(Object renderer, boolean dark, View view) throws Exception {
        Object arg = sSetForceDarkInt
                ? (Object) Integer.valueOf(dark ? FORCE_DARK_ON : FORCE_DARK_NONE)
                : (Object) Boolean.valueOf(dark);
        Object changed = sSetForceDark.invoke(renderer, arg);
        if (Boolean.TRUE.equals(changed) && view != null) {
            invalidateWorld(view);
        }
    }

    /** ウィンドウに描画前リスナーを1つだけ付ける。 */
    static void ensureHook(Object root) {
        if (!sPrepared || root == null || sHooks.containsKey(root)) {
            return;
        }
        try {
            Object attachInfo = sRootAttachInfo.get(root);
            ViewTreeObserver vto = attachInfo == null
                    ? null : (ViewTreeObserver) sAttachObserver.get(attachInfo);
            if (vto == null || !vto.isAlive()) {
                return;
            }
            RootHook hook = new RootHook(root);
            vto.addOnPreDrawListener(hook);
            sHooks.put(root, hook);
        } catch (Throwable t) {
            Log.w(TAG, "PatchDarkMode: hook failed", t);
        }
    }

    /**
     * 毎フレーム描画前に「暗くする」指定・色補正・システムバーを保ち直す。
     * ViewRootImpl は構成変更のたびに自前の判定 (このアプリでは常にライト) で
     * 上書きするので、イベント駆動の再適用だけでは Dialog などが戻ってしまう。
     */
    static final class RootHook implements ViewTreeObserver.OnPreDrawListener {

        final WeakReference<Object> root;
        /** null = 未判定。ライト固定の画面なら TRUE。 */
        Boolean lightOnly;

        RootHook(Object root) {
            this.root = new WeakReference<Object>(root);
        }

        @Override
        public boolean onPreDraw() {
            try {
                if (sPrepared && PatchPrefs.darkMode != MODE_OFF && shouldDark()) {
                    Object r = root.get();
                    if (r != null) {
                        enforce((View) sRootView.get(r));
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "PatchDarkMode: preDraw failed", t);
            }
            return true;
        }

        void enforce(View view) throws Exception {
            Object r = root.get();
            if (r == null || view == null) {
                return;
            }
            Object renderer = rendererOf(r);
            if (renderer == null) {
                return;
            }
            boolean light = isLightOnly(view);
            setForceDark(renderer, !light, view);
            if (!light) {
                PatchDarkTint.adjustWindow(view, isPopup(view));
            }
            updateSystemBars(view, !light);
        }

        /** Activity 本体以外 (Dialog・ボトムシート・PopupWindow) のウィンドウか。 */
        static boolean isPopup(View decor) {
            ViewGroup.LayoutParams lp = decor.getLayoutParams();
            return lp instanceof WindowManager.LayoutParams
                    && ((WindowManager.LayoutParams) lp).type
                    != WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
        }

        boolean isLightOnly(View decor) {
            Boolean cached = lightOnly;
            if (cached != null) {
                return cached;
            }
            if (!isPopup(decor)) {
                lightOnly = Boolean.FALSE; // Activity 本体の画面は対象外
                return false;
            }
            View content = decor.findViewById(android.R.id.content);
            if (content == null) {
                lightOnly = Boolean.FALSE; // PopupWindow など Dialog 以外
                return false;
            }
            if (!(content instanceof ViewGroup) || ((ViewGroup) content).getChildCount() == 0) {
                return false; // 中身がまだ無い。次のフレームで判定し直す
            }
            View leaf = content;
            while (leaf instanceof ViewGroup && ((ViewGroup) leaf).getChildCount() > 0) {
                leaf = ((ViewGroup) leaf).getChildAt(0);
            }
            String name = fragmentClassOf(leaf);
            boolean light = false;
            if (name != null) {
                for (String n : LIGHT_ONLY_FRAGMENTS) {
                    if (n.equals(name)) {
                        light = true;
                        break;
                    }
                }
            }
            lightOnly = light;
            return light;
        }
    }

    /** View を含む androidx Fragment のクラス名。Fragment の外なら null。 */
    static String fragmentClassOf(View view) {
        if (!sFindFragmentTried) {
            sFindFragmentTried = true;
            try {
                sFindFragment = Class.forName("androidx.fragment.app.FragmentManager")
                        .getMethod("findFragment", View.class);
            } catch (Throwable t) {
                Log.w(TAG, "PatchDarkMode: FragmentManager.findFragment unavailable", t);
            }
        }
        if (sFindFragment == null) {
            return null;
        }
        try {
            Object f = sFindFragment.invoke(null, view);
            return f == null ? null : f.getClass().getName();
        } catch (Throwable t) {
            return null; // Fragment の View ではない
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
    static void updateSystemBars(View decor, boolean dark) {
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
            int cur = ic.getSystemBarsAppearance() & mask;
            if (orig == null || cur != 0) {
                // アプリが後から黒アイコン指定をし直したら、それを戻す先として覚え直す
                sOrigBarAppearance.put(window, cur);
            }
            if (cur != 0) {
                ic.setSystemBarsAppearance(0, mask);
            }
        } else if (orig != null) {
            ic.setSystemBarsAppearance(orig, mask);
            sOrigBarAppearance.remove(window);
        }
    }

    /** decor が前面 Activity のウィンドウのものならそのウィンドウ。Dialog 等は null。 */
    static Window windowOf(View decor) {
        WeakReference<Activity> ref = sTopActivity;
        Activity top = ref == null ? null : ref.get();
        if (top == null) {
            return null;
        }
        Window window = top.getWindow();
        return window != null && window.peekDecorView() == decor ? window : null;
    }
}
