package vn.com.bravesoft.androidapp.patch;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.google.firebase.messaging.RemoteMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * 受信した FCM push の全ペイロード(通知 title/body + data 全キー)を端末内へ保存する。
 * メッセージ内容プレビュー実装の前段調査(Phase 0)用のデバッグ機能。
 *
 * <p>{@code "push_debug_log"} プレフが true のときだけ動作(既定オフ)。呼び出しは
 * classes4 に残る {@code MyFirebaseMessagingService.onMessageReceived} へ patch_smali.py が
 * 注入する({@code invoke-super} 直後)。メソッド名・シグネチャを変えると解決できなくなる。
 *
 * <p>保存先は端末の「ダウンロード」({@code Download/domico-patch/})。Android 10+ の
 * スコープドストレージでも権限不要で書け、標準の「ファイル」アプリからそのまま見える
 * (ColorOS 等で {@code Android/data} が隠される端末対策)。受信 1 件につき 1 ファイル。
 * MediaStore が使えない古い端末はアプリ外部データ領域へフォールバックする。
 *
 * <p>データメッセージ(notification ペイロード無し)の push は背面/kill 状態でも
 * onMessageReceived が呼ばれるため、前面に張り付かなくても捕捉できる。I/O 例外等は
 * 全て握りつぶし、本来の通知処理を妨げない。
 */
public final class PatchPushDump {

    private PatchPushDump() {
    }

    public static void dump(Context ctx, RemoteMessage msg) {
        try {
            if (ctx == null || msg == null) {
                return;
            }
            if (!PatchPrefs.get(ctx, PatchPrefs.KEY_PUSH_DEBUG, false)) {
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("==== ")
                    .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
                    .append(" ====\n");

            RemoteMessage.Notification n = msg.getNotification();
            if (n != null) {
                sb.append("Title: ").append(n.getTitle()).append("\n");
                sb.append("Body: ").append(n.getBody()).append("\n");
            } else {
                sb.append("(no notification payload = data-only)\n");
            }

            sb.append("Data:\n");
            Map<String, String> data = msg.getData();
            if (data != null) {
                for (Map.Entry<String, String> e : data.entrySet()) {
                    sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                }
            }
            sb.append("\n");

            write(ctx, sb.toString());
        } catch (Throwable ignored) {
            // デバッグ機能。失敗しても本来の通知処理には影響させない。
        }
    }

    /** 端末の「ダウンロード」へ保存。不可なら外部データ領域へフォールバック。 */
    private static void write(Context ctx, String text) {
        if (Build.VERSION.SDK_INT >= 29 && writeToDownloads(ctx, text)) {
            return;
        }
        writeToExternalFiles(ctx, text);
    }

    /** MediaStore で Download/domico-patch/ に 1 ファイル作成する(ファイルアプリから見える)。 */
    private static boolean writeToDownloads(Context ctx, String text) {
        try {
            String name = "domico-push-"
                    + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date())
                    + ".txt";
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            cv.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/domico-patch");
            ContentResolver cr = ctx.getContentResolver();
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) {
                return false;
            }
            OutputStream os = cr.openOutputStream(uri);
            if (os == null) {
                return false;
            }
            try {
                os.write(text.getBytes(StandardCharsets.UTF_8));
            } finally {
                os.close();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** フォールバック: アプリ外部データ領域の push-capture.log へ追記(adb pull で取得)。 */
    private static void writeToExternalFiles(Context ctx, String text) {
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) {
                dir = ctx.getFilesDir();
            }
            FileOutputStream fos = new FileOutputStream(new File(dir, "push-capture.log"), true);
            try {
                fos.write(text.getBytes(StandardCharsets.UTF_8));
            } finally {
                fos.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
