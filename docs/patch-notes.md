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

## 自動適用 (公式更新時の再適用しやすさ)
パッチは [`scripts/patch_smali.py`](../scripts/patch_smali.py) が行う。版ズレに強くするため:
- 行番号ではなく **クラス+メソッド+命令パターン** をアンカーにする。
- `displayToastContract` 内の `Landroid/view/Window;->clearFlags(I)V` 呼び出しから
  ウィンドウ用レジスタ/一時レジスタを **動的に取得** して挿入(レジスタ名・個数の変化に耐性)。
- **冪等**: 既にマーカーがあればスキップ。
- アンカーが見つからない場合は **非0終了** し、CI が版変更を検知して失敗(=要手動確認)。

## A案 (任意・不採用)
表示自体を消す場合は、`MainTabHostFragment#displayToastContractInfo()` の呼び出しを早期 return にするか、
`AlertUtils#displayToastContract` の先頭で `return-void` する。ただしユーザ方針により B案を採用。

## 関連
- 調査の詳細は [findings.md](findings.md)。
- ビルド/署名/配布手順は [install.md](install.md)。
