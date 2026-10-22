package com.google.firebase.messaging;

import java.util.Map;

/**
 * コンパイル用スタブ。実体は公式 APK 内の Firebase Messaging クラス。
 * d8 には渡さず classpath にだけ載せる(build_module.py)ため dex には入らない。
 * PatchPushDump が使う API だけを最小限で宣言する。
 */
public class RemoteMessage {

    public Map<String, String> getData() {
        return null;
    }

    public Notification getNotification() {
        return null;
    }

    public static class Notification {
        public String getTitle() {
            return null;
        }

        public String getBody() {
            return null;
        }
    }
}
