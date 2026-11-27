# 同梱ライブラリ

`scripts/build_module.py` はこのフォルダの `*.jar` を javac の classpath に載せ、
d8 の入力にも渡してパッチ本体と同じ dex に入れる。

| ファイル | 由来 | ライセンス | 用途 |
|---|---|---|---|
| `hiddenapibypass-6.1.jar` | [LSPosed/AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) `org.lsposed.hiddenapibypass:hiddenapibypass:6.1`（Maven Central の aar 内 `classes.jar`） | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) | ダークモード（`PatchDarkMode`）が使う隠し API の除外 |

更新するときは aar から `classes.jar` を取り出してファイル名に版を付けて置き換える。
