package vn.com.bravesoft.androidapp.patch;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * 現在表示中の Activity を {@link PatchLoadingState} に伝えるだけのトラッカ。
 * 送信中の入力ガードを正しいウィンドウへ貼るために必要。
 */
public final class PatchActivityTracker implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
        PatchLoadingState.setActivity(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
