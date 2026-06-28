# 差分メモ (B案: タッチ遮断解除)

## 目的
起動時ログイン通知バー(下部に約2秒表示される `android.app.Dialog`)の **タッチ遮断のみ** 解除する。
通知の表示自体は残す(B案)。下部ナビ等を表示中でも操作できるようにする。

## 改変点 (1か所のみ)
ファイル: `smali*/vn/com/bravesoft/androidapp/utils/AlertUtils.smali`
メソッド: `displayToastContract(Landroid/app/Activity;Ljava/lang/String;)V`

既存のウィンドウ設定ブロック (gravity=BOTTOM を設定し `clearFlags` する箇所) の直後に、
Dialog ウィンドウへ `FLAG_NOT_FOCUSABLE(0x8) | FLAG_NOT_TOUCHABLE(0x10) = 0x18` を `addFlags` する2命令を挿入。

### before
```smali
    invoke-virtual {v1, v3}, Landroid/view/Window;->clearFlags(I)V
    .line 442
    new-instance v3, Landroid/graphics/drawable/ColorDrawable;
```

### after
```smali
    invoke-virtual {v1, v3}, Landroid/view/Window;->clearFlags(I)V
    # domico-patch: make login toast dialog click-through (NOT_FOCUSABLE|NOT_TOUCHABLE)
    const/16 v3, 0x18
    invoke-virtual {v1, v3}, Landroid/view/Window;->addFlags(I)V
    .line 442
    new-instance v3, Landroid/graphics/drawable/ColorDrawable;
```

- `v1` = Dialog の `Window`、`v3` = `clearFlags` 直後で不要になる一時レジスタ(再利用)。
- `FLAG_NOT_TOUCHABLE` によりこのウィンドウはタッチを一切受け取らず、全タッチが下の Activity へ通過する。
- `FLAG_NOT_FOCUSABLE` で入力フォーカス/IME も奪わない。
- 表示と2秒後の自動 dismiss はそのまま(Handler.postDelayed)。

## ビルド方式（重要: apktool 全体リビルドは使わない）
当初 apktool でデコード→全体リビルドしていたが、`resources.arsc` / `AndroidManifest.xml` を
再エンコードすると一部端末(Xiaomi/HyperOS 等)が `INSTALL_FAILED_USER_RESTRICTED: Invalid apk`
で弾くことが判明。そこで **外科的 dex 差し替え**に変更:
- [`scripts/patch_apk.py`](../scripts/patch_apk.py) が、`AlertUtils` を含む `classes4.dex` のみを
  baksmali → patch → smali で作り直し、**元 base の他エントリ(resources/manifest/他dex)は
  バイト維持**で再パッケージする。
- 検証済み: 元 base と比べ `classes4.dex` だけが変化、`resources.arsc`/`AndroidManifest.xml`/
  他 dex は SHA 一致。Xiaomi POCO F7 Pro (HyperOS) で adb install-multiple 成功。

## 自動適用 (公式更新時の再適用しやすさ)
smali 編集は [`scripts/patch_smali.py`](../scripts/patch_smali.py) が行う。版ズレに強くするため:
- 行番号ではなく **クラス+メソッド+命令パターン** をアンカーにする。
- `displayToastContract` 内の `Landroid/view/Window;->clearFlags(I)V` 呼び出しから
  ウィンドウ用レジスタ/一時レジスタを **動的に取得** して挿入(レジスタ名・個数の変化に耐性)。
- **冪等**: 既にマーカーがあればスキップ。
- アンカーが見つからない場合は **非0終了** し、CI が版変更を検知して失敗(=要手動確認)。

## 言語(日本語)について
日本語文言は base ではなく **`config.ja` スプリット**側。apk-pure はロケール別配信で英語に
なりがちなので、ローカルは端末から `pull-splits` で吸い出し、CI は Google Play(locale=ja_JP)で
取得して同梱する。詳細 [install.md](install.md)。

## A案 (任意・不採用)
表示自体を消す場合は、`MainTabHostFragment#displayToastContractInfo()` の呼び出しを早期 return にするか、
`AlertUtils#displayToastContract` の先頭で `return-void` する。ただしユーザ方針により B案を採用。

## 関連
- 調査の詳細は [findings.md](findings.md)。
- ビルド/署名/配布手順は [install.md](install.md)。
