package vn.com.bravesoft.androidapp.patch;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.lang.ref.WeakReference;

/**
 * 自動チェックインの結果をシステム通知で伝える。
 *
 * <p>自動発火経路は {@code CheckInDialog}/{@code CheckInCompletedDialog} を開かないため、
 * 送信したことも成功したことも画面には出ない。発火自体はアプリが前面にいる間しか起きない
 * (公式の LiveData 観測者が Fragment を LifecycleOwner にしているため) が、ユーザーが
 * 別タブ・別画面にいることはあるし、Toast は数秒で消えて履歴も残らない。さらに成否が
 * 分かるのは送信から数十秒後で、その頃にはアプリを離れていることも多い。そこで:
 *
 * <ol>
 *   <li>発火時に「送信しました」を通知し、確認待ちに入る。</li>
 *   <li>既存の 30 秒ポーリングが返す {@code MenuForDayDTO} がチェックイン済みに
 *       変われば、<b>同じ通知 ID</b> を「完了しました」に更新する。</li>
 *   <li>{@link #CONFIRM_TIMEOUT_MS} 経っても確認できなければ「確認できませんでした」に更新する。</li>
 * </ol>
 *
 * <p>成否の判定に公式の {@code HomeModelView.getOnCheckInSuccess()} は使わない。
 * あれは単一消費の {@code SingleLiveEvent} で、観測者は {@code CheckInDialog} だけ。
 * 手動チェックイン経路と横取り合戦になるため触らない。
 *
 * <p>状態(確認待ちの予約 ID)はこのクラスだけが持つ。classes4 に残る
 * {@code PatchAutoCheckin.smali} からは {@link #onAutoCheckinFired(int)} /
 * {@link #onMenuUpdate(int, boolean)} / {@link #isAwaiting()} を呼ぶだけ。
 * メソッド名・シグネチャを変えると実行時に解決できなくなるので注意。
 */
public final class PatchNotify implements Runnable {

    /** 確認待ちのタイムアウトを実行する Runnable (このクラス自身)。 */
    static final PatchNotify TIMEOUT = new PatchNotify();

    static final String CHANNEL_ID = "domico_patch_checkin";
    static final String CHANNEL_NAME = "自動チェックイン";

    /**
     * 通知 ID。公式 FCM は {@code System.currentTimeMillis()} 由来の大きな int を使うため
     * (MyFirebaseMessagingService)、小さい固定値なら衝突しない。同じ ID で notify し直すことで
     * 「送信しました」の通知がそのまま結果表示に差し替わる。
     */
    static final int NOTIFY_ID = 1015;

    /** 確認待ちの上限。ポーリングが止まってもここで必ず決着させる。 */
    static final long CONFIRM_TIMEOUT_MS = 180000L;

    /** 「確認待ちなし」を表す予約 ID (予約 ID 0 と衝突させない)。 */
    static final int NO_RESERVATION = -1;

    private static final String TITLE = "自動チェックイン";
    private static final String TEXT_SENT = "チェックインを送信しました。結果を確認しています…";
    private static final String TEXT_DONE = "チェックインが完了しました。";
    private static final String TEXT_UNCONFIRMED =
            "送信しましたが完了を確認できませんでした。アプリで状態をご確認ください。";

    /**
     * 小アイコンに使う drawable 名の候補。リソースを追加できないので実行時に解決する。
     *
     * <p>{@code ic_push_notification} は公式が FCM 通知の小アイコンに使っている白抜き PNG
     * (MyFirebaseMessagingService の {@code 0x7f080193})。ただし実体は密度スプリット側に
     * あるので、スプリット構成によっては解決できず {@code applicationInfo.icon} に落ちる。
     */
    private static final String[] ICON_NAMES = {"ic_push_notification", "ic_notification"};

    static volatile Context appContext;
    static volatile Handler handler;
    static volatile int awaitingReservationId = NO_RESERVATION;

    /**
     * タイムアウトで「確認できませんでした」と出した予約 ID。
     *
     * <p>公式の LiveData 観測者は Fragment 自身を LifecycleOwner にしているため
     * (HomeFragment.setupObserveModelView)、アプリが背面にいる間は {@code showUICheckIn} が
     * 呼ばれず {@link #onMenuUpdate} も届かない。送信直後に背面へ回されると成功していても
     * 必ずタイムアウトしてしまうので、復帰後の更新でチェックイン済みと分かったら
     * 通知を「完了しました」へ訂正する。
     */
    static volatile int unconfirmedReservationId = NO_RESERVATION;

    /** {@link PatchInit#onAppCreate} から呼ばれ、以後 Context を渡さずに通知できるようにする。 */
    public static void init(Context ctx) {
        if (ctx == null) {
            return;
        }
        appContext = ctx.getApplicationContext();
    }

    /**
     * 確認待ちかどうか。PatchAutoCheckin.run() が、保留が無くなった後もポーリングを
     * 続けるべきかの判断に使う。
     */
    public static boolean isAwaiting() {
        return awaitingReservationId != NO_RESERVATION;
    }

    /**
     * {@link #onMenuUpdate} を呼ぶ価値があるか。確認待ちに加えて、タイムアウトで
     * 打ち切った予約の訂正待ちも含む。ポーリング継続の判断 ({@link #isAwaiting()}) とは
     * 別物: 訂正は次にホームが更新されたときに拾えればよく、そのためにポーリングは続けない。
     */
    public static boolean wantsMenuUpdate() {
        return awaitingReservationId != NO_RESERVATION || unconfirmedReservationId != NO_RESERVATION;
    }

    /** 自動チェックインを送信した直後に呼ばれる。通知を出して確認待ちに入る。 */
    public static void onAutoCheckinFired(int reservationId) {
        try {
            awaitingReservationId = reservationId;
            unconfirmedReservationId = NO_RESERVATION;
            // タイムアウトを先に仕込む。post() が投げても確認待ちが残り続けないように
            // (残ると PatchAutoCheckin.run() が 30 秒ポーリングを延々と続けてしまう)。
            Handler h = handler();
            h.removeCallbacks(TIMEOUT);
            h.postDelayed(TIMEOUT, CONFIRM_TIMEOUT_MS);
            post(TEXT_SENT);
        } catch (Throwable ignored) {
            // 通知は付随機能。失敗してもチェックイン本体には影響させない。
            // ただし確認待ちだけは必ず畳む (ポーリングが止まらなくなるため)。
            awaitingReservationId = NO_RESERVATION;
        }
    }

    /**
     * ホーム画面のデータが更新されるたびに呼ばれる。確認待ちの予約がチェックイン済みに
     * なっていれば成功として通知を差し替える。
     */
    public static void onMenuUpdate(int reservationId, boolean checkedIn) {
        try {
            if (!checkedIn) {
                return;
            }
            if (reservationId == NO_RESERVATION) {
                return;
            }
            if (awaitingReservationId == reservationId) {
                awaitingReservationId = NO_RESERVATION;
                handler().removeCallbacks(TIMEOUT);
                post(TEXT_DONE);
                return;
            }
            // 背面にいる間に確認できず打ち切った予約。復帰後に済みと分かったので訂正する。
            if (unconfirmedReservationId == reservationId) {
                unconfirmedReservationId = NO_RESERVATION;
                post(TEXT_DONE);
            }
        } catch (Throwable ignored) {
            // 同上。
        }
    }

    /** 確認待ちのタイムアウト。まだ決着していなければ「確認できませんでした」に更新する。 */
    @Override
    public void run() {
        try {
            int awaiting = awaitingReservationId;
            if (awaiting == NO_RESERVATION) {
                return;
            }
            awaitingReservationId = NO_RESERVATION;
            unconfirmedReservationId = awaiting;
            post(TEXT_UNCONFIRMED);
        } catch (Throwable ignored) {
            // 同上。
        }
    }

    private static Handler handler() {
        Handler h = handler;
        if (h != null) {
            return h;
        }
        h = new Handler(Looper.getMainLooper());
        handler = h;
        return h;
    }

    /** 通知を出す。通知が無効化されていれば Toast へフォールバックする。 */
    private static void post(String text) {
        Context ctx = appContext;
        if (ctx == null) {
            return;
        }
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || !nm.areNotificationsEnabled()) {
            toast(text);
            return;
        }
        // 同じ設定での作り直しは無害 (既存チャンネルのユーザー設定は上書きされない)。
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT));

        Notification.Builder builder = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(smallIcon(ctx))
                .setContentTitle(TITLE)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true);
        PendingIntent tap = launchIntent(ctx);
        if (tap != null) {
            builder.setContentIntent(tap);
        }
        nm.notify(NOTIFY_ID, builder.build());
    }

    /** アプリのリソースから小アイコンを探す。見つからなければアプリアイコンで代用する。 */
    private static int smallIcon(Context ctx) {
        String pkg = ctx.getPackageName();
        for (String name : ICON_NAMES) {
            int id = ctx.getResources().getIdentifier(name, "drawable", pkg);
            if (id != 0) {
                return id;
            }
        }
        int appIcon = ctx.getApplicationInfo().icon;
        return appIcon != 0 ? appIcon : android.R.drawable.ic_dialog_info;
    }

    /** タップでアプリを開く PendingIntent。Activity 名はハードコードせず Launcher から引く。 */
    private static PendingIntent launchIntent(Context ctx) {
        Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (intent == null) {
            return null;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, NOTIFY_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 通知権限が無いときの代替。前面に Activity があるときだけ出せる。 */
    private static void toast(String text) {
        WeakReference<Activity> ref = PatchLoadingState.currentActivity;
        if (ref == null) {
            return;
        }
        Activity activity = ref.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }
}
