# 調査メモ: 起動時ログイン通知バーの正体

対象: 公式 Domico (appId `jp.co.kyoritsu.domico` / 実コードパッケージ `vn.com.bravesoft.androidapp`、Bravesoft 製) v1.5.4 (versionCode 53)

## パッケージ構成
- `Domico_1.5.4.xapk` は分割APK(App Bundle)。`jp.co.kyoritsu.domico.apk`(base) + `config.arm64_v8a.apk`(native) + `config.en.apk` + `config.mdpi.apk`。
- minSdk 26 / targetSdk 35。
- フレームワーク: ネイティブ Kotlin (Jetpack Compose ではない)。View + DataBinding + Dagger + Material Components + OkHttp + Firebase(FCM)。native lib は pdfium 系(PDF表示)。
- アプリコードは `vn.com.bravesoft.androidapp` 配下でクラス名・メソッド名が残存(実質非難読化)。→ 版を跨いでアンカーが安定。

## 通知バーの正体
- **実装**: `vn.com.bravesoft.androidapp.utils.AlertUtils#displayToastContract(Activity, String)`
- 中身は **`android.app.Dialog`** で、次の特徴を持つ:
  - `requestWindowFeature(FEATURE_NO_TITLE)`
  - window の `gravity = 0x50` (= `Gravity.BOTTOM`) → 画面最下部に配置
  - `clearFlags(FLAG_DIM_BEHIND)` + 透明 `ColorDrawable` 背景
  - `setCancelable(false)`
  - `setContentView(R.layout.custom_toast_layout)` (= `res/layout/custom_toast_layout.xml`、単一 `TextView`、角丸ダーク背景 `@drawable/bg_toast`、白文字、`layout_marginBottom=@dimen/_24sdp`)
  - テキスト `R.id.text` にメッセージを `setText`
  - `show()` 後、`Handler.postDelayed(dismiss, 0x7d0=2000ms)` で約2秒後に自動 dismiss
- 備考: 生成済み ViewBinding `CustomToastLayoutBinding` は存在するが未使用(デッドコード)。表示は上記 Dialog 経由。

## 表示トリガー
- `vn.com.bravesoft.androidapp.ui.MainTabHostFragment#displayToastContractInfo()`
  - `UserCtrl.getUser()` から `divisionKanjiName`(施設/部署名) と `roomNumber`(部屋番号) を取得
  - 文字列リソース `R.string.msg_active_contract_success` を `[division, room]` でフォーマット
    - base(既定)値は英語テンプレート: `"「%1$s %2$s」logged in!"`(端末ロケールにより「〜でログインしました。」)
  - `AlertUtils.displayToastContract(activity, msg)` を呼ぶ
  - 直後に `MyApplication.Companion.setDisplayToastContract(true)` を立てる(セッション内の重複表示制御フラグ)
- もう1か所 `ManagerContractFragment` からも `displayToastContract` を呼ぶ(契約系画面)。今回の B 案パッチは表示元を問わず効く(共通の Dialog 生成箇所を直すため)。

## なぜ「タッチ遮断」が起きるか
- `android.app.Dialog` のウィンドウは **既定でタッチモーダル**(`FLAG_NOT_TOUCH_MODAL` 無し)。
  そのため Dialog 表示中(約2秒)は、バーの外側を含む画面全体のタッチがこの Dialog ウィンドウに吸われ、
  下の Activity(下部ナビ等)を操作しづらくなる。
- 文言はサーバではなくローカル文字列+ユーザ属性で生成されるが、いずれにせよ「タッチ遮断」は
  Dialog ウィンドウのフラグ仕様が原因。

## 結論(採用方針)
- **B案(タッチ遮断のみ解除)** が最小かつ安全。表示は残し、Dialog ウィンドウへ
  `FLAG_NOT_FOCUSABLE(0x8) | FLAG_NOT_TOUCHABLE(0x10) = 0x18` を `addFlags` して
  クリックスルー化する。詳細は [patch-notes.md](patch-notes.md)。
