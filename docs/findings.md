# 調査メモ: 起動時ログイン通知バーの正体

対象: 公式 Domico（appId `jp.co.kyoritsu.domico` / 実コードパッケージ `vn.com.bravesoft.androidapp`）v1.5.4（versionCode 53）

## パッケージ構成
- 分割APK（App Bundle）。`base` + `config.arm64_v8a`（native）+ `config.<locale>` + `config.<density>`。
- minSdk 26 / targetSdk 35。
- ネイティブ Kotlin（View + DataBinding + Dagger + OkHttp + Firebase）。実質非難読化のため、クラス名・メソッド名が版を跨いで安定し、アンカーにできる。

## 通知バーの正体
- 実装: `vn.com.bravesoft.androidapp.utils.AlertUtils#displayToastContract(Activity, String)`
- 中身は `android.app.Dialog`。主な設定:
  - `gravity = Gravity.BOTTOM`（画面最下部に配置）
  - `clearFlags(FLAG_DIM_BEHIND)` + 透明背景、`setCancelable(false)`
  - `setContentView(R.layout.custom_toast_layout)`（単一 `TextView`）
  - `show()` 後、`Handler.postDelayed(dismiss, 2000ms)` で約2秒後に自動 dismiss

## 表示トリガー
- `vn.com.bravesoft.androidapp.ui.MainTabHostFragment#displayToastContractInfo()`
  - `UserCtrl.getUser()` の施設名・部屋番号で `R.string.msg_active_contract_success` をフォーマットして表示。
  - base の既定値は英語（`"「%1$s %2$s」logged in!"`）。
- `ManagerContractFragment` からも同じ `displayToastContract` を呼ぶ。クリックスルーパッチは Dialog 生成箇所を直すため、表示元を問わず効く。

## なぜタッチ遮断が起きるか
`android.app.Dialog` のウィンドウは既定でタッチモーダル（`FLAG_NOT_TOUCH_MODAL` なし）。そのため表示中の約2秒間、バーの外側を含む画面全体のタッチが Dialog に吸われ、下の Activity を操作できなくなる。

## 結論
**クリックスルー化（タッチ遮断のみ解除）** を採用。表示は残し、Dialog ウィンドウへ `FLAG_NOT_FOCUSABLE(0x8) | FLAG_NOT_TOUCHABLE(0x10) = 0x18` を `addFlags` する。詳細は [patch-notes.md](patch-notes.md)。

## 追加パッチの調査（v0.2）
- **テレメトリ**: Firebase Analytics(AppMeasurement)/Crashlytics/Performance/Ad-ID/Install Referrer を同梱。FCM はプッシュ通知の本来機能。`MyApplication`(classes4) から classes2 の Firebase API を呼んで収集停止できる。
- **ロード表示**: `vn...views/FrameLayoutLoading`（各 Fragment ルート）の内側全画面 `relativeLayout` が `setClickable(true)` で全タッチを奪う。GET 35 / 更新系(POST/PUT/DELETE) 50。HTTP メソッドが見えるのは OkHttp の `AppModule.provideRetrofit` のクライアントのみ。
- **二重送信**: 既存 `OnSingleClickListener` は 400ms のクリックデバウンス（速い二度押しのみ抑止）。送信中の意図的な再タップは別途遮断が必要 → `PatchTrafficInterceptor` + `PatchLoadingState` の入力ガードで対応。
- **設定画面**: 新規 Activity はマニフェスト登録が要るため不可。プログラム生成 `Dialog` で実現。導線は `MenuFragment.init`（`MenuLayoutBinding` の `containerTop` / `versionContain`）に注入。
- **対象クラスは全て classes4**: AlertUtils / MyApplication / FrameLayoutLoading / AppModule / MenuFragment。新規 `patch/*` も classes4 に同梱するため、現行の単一 dex 差し替え方式のままで完結する。

## 時間外チェックインの調査（裏機能）
- **グレーアウトの実体**: `ui/HomeFragment` `showUICheckIn` が
  `ViewOrderFoodCheckInBinding.btnCheckIn` に対し `stateButton(btn, dto.isCheckInTime())` を呼ぶ。
  `stateButton`(private final) は引数 false で `setEnabled(false)`＋`setAlpha(0.5f)`＝無効＋半透明。
- **時間判定はサーバー側**: `MenuForDayDTO` の `isCheckInTime` / `isBeforeCheckIn` は JSON
  (`is_checkin_time` / `before_check_in`) でサーバーが返すフラグ。クライアントは計算せず受け取るだけ。
- **クリック導線**: btnCheckIn → `reactiveClick` → `checkInAction(MenuForDayDTO)`。
  `getCheckIn()==0`（未チェックイン）で `CheckInDialog`、`==1`（済）で `CheckInCompletedDialog` を開く。
- **CheckInDialog が表示と実行を両方持つ**: `setUpData` が `isBreakfast()` /
  `isJapanFoodReserved()`(和食) / `isWesternFoodReserved()`(洋食) で食事種別アイコン・画像を出し、
  `btnCheckIn` で `HomeModelView.checkIn(reservationId)` を実行（時間ゲートなし）。
  → 「予約食事を正しく表示」要件はこのダイアログ再利用で満たせる。
- **チェックイン API**: `ApiStores.checkIn` = `POST v1/reservations/checkin`、
  ボディ `CheckInRequest { reservation_id:int }` のみ。クライアント時刻・時間窓は送らない。
- **純正確認ダイアログ**: `AlertUtils.showAlertDialogCancel(Context, Z, message, ok?, cancel?, title, CallbackAlertDialog)`。
  OK(-1)→`actionDoneClick()`、キャンセル(-2)→dismiss。`CallbackAlertDialog` はデフォルト実装ありの
  Kotlin インタフェース（`actionDoneClick`/`actionCancelClick`）。
- **方針**: 時間外＋未チェックイン時のみボタンを再有効化（見た目はグレー維持）し、タップで純正確認
  ダイアログ→OK で公式 `CheckInDialog` を開く。表示・通信ロジックは新規に書かず公式を再利用。既定オフ。

## メッセージ通知への本文プレビュー調査（**実現不可**・2026-09-22）
「受信メッセージの通知に本文が出ない」を改善しようとしたが、**dex-only パッチでは実現できない**と結論。
再挑戦する前に本節を読むこと。

- **通知の「メッセージが届きました」は公式仕様＝サーバが送る `notification.body`**。
  `MyFirebaseMessagingService.sendNotification` は本文を `notification.getBody()` からしか取らず、
  data から本文を読むコードは無い（data で読むのは `type` / `id` / `dormitory_code` のみ）。
  固定文言が表示される＝**通知メッセージ型 push** である証拠。
- **本文は push に載っていない**。body は汎用文言固定で、実際のメッセージ内容は認証付き
  API (`v1/messages`) の奥にある。
- **背面では `onMessageReceived` が呼ばれない**。通知メッセージ型は、アプリが背面/kill のとき
  Android/FCM が直接通知を表示し、アプリのコードを一切通さない（FCM の仕様）。
  → **パッチが割り込める場所が存在しない**。
- **決定的証拠: その通知を出したのはアプリではなく FCM SDK**（端末ログ 2026-09-22 20:02 / LogFox）。
  通知キーが `0|jp.co.kyoritsu.domico|0|FCM-Notification:322915822|10410`
  （形式は `userId|pkg|通知ID|タグ|uid`）＝ **タグ `FCM-Notification:<uptime>` + 通知ID `0`**。
  これは Firebase Messaging SDK が通知メッセージを自動表示するときの固有形式。公式の
  `sendNotification` は `notify(int id, Notification)` を**タグ無し**・id=`currentTimeMillis()/1000`
  で呼ぶため、この形には決してならない。
  さらにタップ時の起動が `START ... act=android.intent.action.MAIN cat=[LAUNCHER] ... (has extras)`
  ＝ FCM SDK の既定動作（アプリ自身の経路なら `new Intent(this, MainActivity.class)` の明示 Intent に
  `type`/`id` を putExtra したものになる）。
  → **アプリのコードを一度も通さずに通知が表示されている**ことが直接確認できた。
- **傍証**: デバッグ用ダンプ `PatchPushDump`（`push_debug_log` トグル）入りビルドを入れ、トグル ON で
  メッセージを受信しても `Android/data/jp.co.kyoritsu.domico/files` が作られない
  ＝ `getExternalFilesDir` が一度も呼ばれない＝ハンドラ未実行。
  インストール済み APK を `adb pull` して dex 内に `PatchPushDump` / `push_debug_log` が
  含まれることは確認済みなので、「ビルドに入っていなかった」説は否定済み。
  push 接続自体は正常（`GmsGcmMcsSvc` のハートビート疎通あり）。
- **代替案もいずれも不可**:
  - `NotificationListenerService` で横取り → 新規サービスの**マニフェスト登録が必須**。本プロジェクトは
    dex-only（マニフェスト再エンコードは `INSTALL_FAILED_USER_RESTRICTED` を誘発。下記「実地知見」参照）。
  - バックグラウンド API ポーリング → 背面/kill でプロセスが動かず（通知メッセージは起こさない）、
    ColorOS 等は積極的に kill する。
  - 別コンパニオンアプリ → 本文取得に Domico のログインセッションが必要で root 無しでは取得不可。
- **唯一の未確定点**は「前面受信時の data payload の中身」。ただし**前面限定のプレビューは実用価値が
  ほぼ無い**（アプリを開いているなら本文は読める）ため、確認しても結論は変わらない。
- **残置物**: `PatchPushDump`（module/src の Java・`Download/domico-patch/` へ保存）と
  `push_debug_log` トグルは**既定オフ**で dev に残してある。将来 push の中身を覗きたくなったら使える。

## 実地知見（Xiaomi POCO F7 Pro / HyperOS）
- **apktool 全体リビルドは不可**: `resources.arsc` / `AndroidManifest` を再エンコードした base は `INSTALL_FAILED_USER_RESTRICTED: Invalid apk` で弾かれた。無改変で再署名しただけのセットは入るため、原因は再署名でも端末ポリシーでもなく apktool のリソース再構築と判明。→ `classes4.dex` のみ差し替える方式（[patch_apk.py](../scripts/patch_apk.py)）で解決。
- **日本語**: 文言は base になく `config.ja` スプリット側にある。base の既定は英語。
- **密度**: POCO F7 Pro は `xxxhdpi`。base に複数密度のフォールバックがあるため致命的ではないが、端末から pull するのが最も正確。
- **インストール**: `adb install-multiple --no-incremental -r <全apk>` で成功。SAI は `.apks` 拡張子を弾くため、使うなら個別 apk。Shizuku 対応インストーラでも可。
