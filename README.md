# domico-patch

公式 **Domico**（寮生向けアプリ, `jp.co.kyoritsu.domico`）の UX を改善する非公式パッチ。

起動して自動ログインするたびに画面下部へ約2秒表示される「ログインしました」通知バーが、
その間ほかの操作を遮断する問題を解消する。

## 何をするか（B案: タッチ遮断のみ解除）
通知バーの実体は `AlertUtils#displayToastContract` が出す **下部 `android.app.Dialog`**。
Dialog ウィンドウは既定でタッチモーダルのため表示中は画面全体のタッチを奪う。
そこでウィンドウへ `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE` を付与し、
**通知は残したままクリックスルー化**する（下の UI をそのまま操作できる）。

詳細:
- 調査メモ: [docs/findings.md](docs/findings.md)
- 差分メモ: [docs/patch-notes.md](docs/patch-notes.md)
- ビルド/署名/インストール: [docs/install.md](docs/install.md)

## 構成
```
scripts/
  patch_smali.py   # B案 smali パッチ（冪等・アンカー基準・版ズレで失敗）
  build.py         # 抽出→decode→patch→build→zipalign→全split署名→.apks 出力
  gen-keystore.{sh,ps1}  # 署名鍵生成 + base64(Secret用)出力
.github/workflows/
  patch.yml        # 公式更新検知→自動パッチ→再署名→GitHub Release
docs/              # 調査/差分/手順
```

## クイックスタート（ローカル）
```bash
# tools/apktool.jar を用意し、署名鍵を作成後:
python scripts/build.py --input Domico_1.5.4.xapk \
  --keystore work/domico.keystore --ks-pass <pass> --alias domico
# 出力: work/out/*.apk と work/out/Domico-patched.apks
```

## 注意
- 再署名版は公式と署名が異なるため、初回は公式版のアンインストールが必要（データはサーバ側）。
- Play 自動更新の対象外。更新は GitHub Release から取得。
- 私的な UX 改善目的。サーバ通信・本来機能は変更していない。ミラー取得は配布元 ToS 上グレーで自己責任。
