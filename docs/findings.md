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
- `ManagerContractFragment` からも同じ `displayToastContract` を呼ぶ。B案パッチは Dialog 生成箇所を直すため、表示元を問わず効く。

## なぜタッチ遮断が起きるか
`android.app.Dialog` のウィンドウは既定でタッチモーダル（`FLAG_NOT_TOUCH_MODAL` なし）。そのため表示中の約2秒間、バーの外側を含む画面全体のタッチが Dialog に吸われ、下の Activity を操作できなくなる。

## 結論
**B案（タッチ遮断のみ解除）** を採用。表示は残し、Dialog ウィンドウへ `FLAG_NOT_FOCUSABLE(0x8) | FLAG_NOT_TOUCHABLE(0x10) = 0x18` を `addFlags` してクリックスルー化する。詳細は [patch-notes.md](patch-notes.md)。

## 実地知見（Xiaomi POCO F7 Pro / HyperOS）
- **apktool 全体リビルドは不可**: `resources.arsc` / `AndroidManifest` を再エンコードした base は `INSTALL_FAILED_USER_RESTRICTED: Invalid apk` で弾かれた。無改変で再署名しただけのセットは入るため、原因は再署名でも端末ポリシーでもなく apktool のリソース再構築と判明。→ `classes4.dex` のみ差し替える方式（[patch_apk.py](../scripts/patch_apk.py)）で解決。
- **日本語**: 文言は base になく `config.ja` スプリット側にある。base の既定は英語。
- **密度**: POCO F7 Pro は `xxxhdpi`。base に複数密度のフォールバックがあるため致命的ではないが、端末から pull するのが最も正確。
- **インストール**: `adb install-multiple --no-incremental -r <全apk>` で成功。SAI は `.apks` 拡張子を弾くため、使うなら個別 apk。Shizuku 対応インストーラでも可。
