# 差分メモ（パッチ群）

すべて `scripts/patch_smali.py` がアンカー基準・冪等で適用し、対象クラスは全て classes4。
新規ヘルパは `scripts/patch_assets/vn/com/bravesoft/androidapp/patch/*.smali` を classes4 へ配置する。
各パッチは `PatchPrefs` のフラグ（既定 true）でゲートされ、設定画面から個別に切り替えられる。

## 1. ログイントースト クリックスルー
- ファイル: `vn/com/bravesoft/androidapp/utils/AlertUtils.smali`
- メソッド: `displayToastContract(Landroid/app/Activity;Ljava/lang/String;)V`

`Landroid/view/Window;->clearFlags(I)V` をアンカーにし、その直後へ `toastEnabled` ゲート付きで
`FLAG_NOT_FOCUSABLE(0x8) | FLAG_NOT_TOUCHABLE(0x10) = 0x18` を `addFlags` する。

```smali
    invoke-virtual {v1, v3}, Landroid/view/Window;->clearFlags(I)V
    # domico-patch: gated login-toast click-through
    sget-boolean v3, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->toastEnabled:Z
    if-eqz v3, :domico_toast_skip
    const/16 v3, 0x18
    invoke-virtual {v1, v3}, Landroid/view/Window;->addFlags(I)V
    :domico_toast_skip
```

- `v1` = Dialog の `Window`、`v3` = `clearFlags` 直後で不要になる一時レジスタの再利用。
- 表示と2秒後の自動 dismiss はそのまま。

## 2. ロード表示クリックスルー（選択的）
- `views/FrameLayoutLoading.smali`: 内側全画面 `relativeLayout` の `setClickable(Z)` を
  `loadingEnabled` に応じて切替（有効時はクリックスルー＝非クリッカブル）。
- `di/AppModule.smali` `provideRetrofit`: OkHttp ビルダの `build()` 直前に
  `PatchTrafficInterceptor.add(builder)` を挿入（スクラッチ register 不要の静的ヘルパ）。
- インターセプタは非GET（POST/PUT/DELETE）通信の前後で `PatchLoadingState.enter()/exit()` を呼び、
  進行中はメインスレッドで現在 Activity に透明なクリック吸収オーバーレイを乗せて二重送信を防ぐ。
- GET は素通りなので取得・遷移ロードは完全に操作可能。

## 3. テレメトリ停止 + Ad-ID 遮断
- `MyApplication.smali` `onCreate`: `invoke-super onCreate` 直後に
  `PatchInit.onAppCreate(this)` を 1 命令注入。
- `PatchInit` が prefs ロード → `PatchTelemetry.apply()`（`telemetryOff` 時に Analytics/Crashlytics/
  Performance 停止＋広告同意 DENIED、全体 try/catch で起動を壊さない）→ Activity トラッカ登録。

## 4. 設定画面の導線
- `ui/MenuFragment.smali` `init(View)`: 末尾の `return-void` 直前に
  `PatchSettingsEntry.install(binding)` を注入（`binding` は MenuFragment のフィールドから取得）。
- `PatchSettingsEntry` が `containerTop` に「パッチ設定」行を追加（タグで冪等）し、
  `versionContain` に長押しリスナを付与。どちらも `PatchSettingsOpener` → `PatchSettingsDialog.show()`。

## ビルド方式: dex 差し替え（apktool 全体リビルドは使わない）
apktool で `resources.arsc` / `AndroidManifest.xml` を再エンコードすると、一部端末（Xiaomi/HyperOS 等）が `INSTALL_FAILED_USER_RESTRICTED: Invalid apk` で弾く。そこで **外科的 dex 差し替え** に変更した。

- [`scripts/patch_apk.py`](../scripts/patch_apk.py) が `AlertUtils` を含む `classes4.dex` のみを baksmali → patch → smali で作り直し、他エントリ（resources / manifest / 他 dex）はそのまま再パッケージする。
- 検証: 元 base と比べ `classes4.dex` だけが変化し、他エントリの内容は一致。Xiaomi POCO F7 Pro（HyperOS）で `adb install-multiple` 成功。

## 自動適用（公式更新への追従）
smali 編集は [`scripts/patch_smali.py`](../scripts/patch_smali.py) が行う。版ズレに強くするため:

- 行番号ではなく **クラス + メソッド + 命令パターン** をアンカーにする。
- `clearFlags(I)V` 呼び出しからウィンドウ用・一時レジスタを動的に取得して挿入（レジスタ名・個数の変化に耐性）。
- **冪等**: 既にマーカーがあればスキップ。
- アンカーが見つからなければ非0終了し、CI が版変更を検知して失敗する（要手動確認）。

## 言語（日本語）
日本語文言は base になく `config.ja` スプリット側にある。ローカルは端末から pull、CI は Google Play（`locale=ja_JP`）で取得して同梱する。詳細は [install.md](install.md)。

## トースト: 非表示化（不採用）
通知の表示自体を消す案（`displayToastContractInfo()` を早期 return、または `displayToastContract` 先頭で `return-void`）もあるが、通知は残したいというユーザ方針によりクリックスルー化を採用。

## 関連
- 調査の詳細: [findings.md](findings.md)
- ビルド・署名・配布手順: [install.md](install.md)
