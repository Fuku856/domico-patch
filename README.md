# domico-patch

**Domico**（共立メンテナンス 寮生向けアプリ, `jp.co.kyoritsu.domico`）の UX を改善するパッチ。

起動して自動ログインするたびに画面下部へ約2秒表示される「ログインしました」通知トーストが、
その間ほかの操作を遮断する問題を解消する。

## パッチについて
通知トーストの実体は `AlertUtils#displayToastContract` が出す **下部 `android.app.Dialog`**。
Dialog ウィンドウは既定でタッチモーダルのため表示中は画面全体のタッチを奪う。
そこでウィンドウへ `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE` を付与し、
**通知は残したままクリックスルー化**する（下の UI をそのまま操作できる）。

詳細:
- 調査メモ: [docs/findings.md](docs/findings.md)
- 差分メモ: [docs/patch-notes.md](docs/patch-notes.md)
- ビルド/署名/インストール: [docs/install.md](docs/install.md)
- CI セットアップ(apkeep + Secrets): [docs/ci-setup.md](docs/ci-setup.md)

## パッチ方式の要点
- パッチは **`classes4.dex` のみ差し替え**（resources/manifest は公式とバイト一致）。
  apktool 全体リビルドは Xiaomi/HyperOS 等が `INSTALL_FAILED_USER_RESTRICTED` で弾くため不使用。
- 日本語は `config.ja` **スプリット**または**apkeep**による取得が必要（base には無し）。ローカルは端末 pull、CI は apkeepでGoogle Play(ja) から取得。

## 構成
```
scripts/
  patch_smali.py    # smali パッチ（冪等・アンカー基準・版ズレで失敗）
  patch_apk.py      # classes4.dex のみ baksmali→patch→smali で差し替え（リソース据置）
  build.py          # 入力(xapk/フォルダ)→dexパッチ→zipalign→全split署名→個別apk出力
  pull-splits.{ps1,sh}   # 端末の公式版から全スプリット(config.ja含む)を吸い出す
  setup-signing.{ps1,sh} # 署名鍵作成 + GitHub Secrets 登録（ワンショット）
  gen-keystore.{sh,ps1}  # 署名鍵生成のみ
.github/workflows/
  patch.yml         # 公式更新検知→Google Play(ja)取得→dexパッチ→再署名→Release(.apk添付)
docs/               # 調査/差分/手順
```

## クイックスタート（ローカル・日本語入り）
```powershell
# 署名鍵+Secrets 用意（初回）
powershell -ExecutionPolicy Bypass -File scripts/setup-signing.ps1
# 端末の公式版から全スプリット(config.ja含む)を取得
powershell -ExecutionPolicy Bypass -File scripts/pull-splits.ps1
# dex差し替えパッチ＋再署名
python scripts/build.py --input work/device_splits --keystore work/domico.keystore --ks-pass <pass> --alias domico
# 出力: work/out/*.apk → adb install-multiple --no-incremental -r work\out\*.apk
```
詳細は [docs/install.md](docs/install.md)。

## 注意
- 再署名版は公式と署名が異なるため、初回は公式版のアンインストールが必要（データはサーバ側）。
- Play 自動更新の対象外。更新は GitHub Release から取得。
- 私的・学習目的の UX 改善パッチ。サーバ通信・本来機能は変更なし。ミラー取得は配布元 ToS 上グレーで自己責任。
