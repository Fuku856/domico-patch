# 差分メモ（B案: タッチ遮断解除）

## 目的
起動時ログイン通知バー（下部に約2秒表示される `android.app.Dialog`）の **タッチ遮断のみ** を解除する。表示自体は残し、表示中も下部ナビ等を操作できるようにする。

## 改変点（1か所のみ）
- ファイル: `vn/com/bravesoft/androidapp/utils/AlertUtils.smali`
- メソッド: `displayToastContract(Landroid/app/Activity;Ljava/lang/String;)V`

ウィンドウ設定ブロック（gravity を設定し `clearFlags` する箇所）の直後に、Dialog ウィンドウへ `FLAG_NOT_FOCUSABLE(0x8) | FLAG_NOT_TOUCHABLE(0x10) = 0x18` を `addFlags` する2命令を挿入する。

```smali
    invoke-virtual {v1, v3}, Landroid/view/Window;->clearFlags(I)V
    # domico-patch: make login toast dialog click-through (NOT_FOCUSABLE|NOT_TOUCHABLE)
    const/16 v3, 0x18
    invoke-virtual {v1, v3}, Landroid/view/Window;->addFlags(I)V
```

- `v1` = Dialog の `Window`、`v3` = `clearFlags` 直後で不要になる一時レジスタの再利用。
- `FLAG_NOT_TOUCHABLE` でウィンドウがタッチを受け取らず、全タッチが下の Activity へ通過する。`FLAG_NOT_FOCUSABLE` で入力フォーカスも奪わない。
- 表示と2秒後の自動 dismiss はそのまま。

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

## A案（不採用）
表示自体を消すなら、`displayToastContractInfo()` の呼び出しを早期 return にするか、`displayToastContract` の先頭で `return-void` する。ユーザ方針により B案を採用。

## 関連
- 調査の詳細: [findings.md](findings.md)
- ビルド・署名・配布手順: [install.md](install.md)
