# domico-patch

**Domico**（共立メンテナンス 寮生向けアプリ, `jp.co.kyoritsu.domico`）の UX を改善するパッチ。

起動して自動ログインするたびに画面下部へ約2秒表示される「ログインしました」通知トーストが、
その間ほかの操作を遮断する問題を解消する。

## 何をするか
複数のパッチを `classes4.dex` のみ差し替えで適用する。各パッチはアプリ内設定画面から個別に ON/OFF でき、既定は全て有効。

### 1. ログイントースト クリックスルー
通知バーの実体は `AlertUtils#displayToastContract` が出す **下部 `android.app.Dialog`**。
Dialog ウィンドウは既定でタッチモーダルのため表示中は画面全体のタッチを奪う。
そこでウィンドウへ `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE` を付与し、
**通知は残したままクリックスルー化**する（下の UI をそのまま操作できる）。

### 2. ロード表示クリックスルー（選択的）
画面遷移・取得（GET）ロード中の中央スピナー（`FrameLayoutLoading` の全画面スクリム）を
クリックスルー化し操作可能にする。一方、送信系（POST/PUT/DELETE）の通信中だけは
`PatchTrafficInterceptor` が検知して入力を一時遮断し、二重送信を防ぐ。

### 3. テレメトリ送信停止 + Ad-ID 遮断
`MyApplication.onCreate` で Firebase Analytics / Crashlytics / Performance の収集を停止し、
広告同意（AD_STORAGE / AD_USER_DATA / AD_PERSONALIZATION）を DENIED にする。
FCM（プッシュ通知）と RemoteConfig は維持。動作の軽量化にも寄与。

### 4. アプリ内パッチ設定画面
メニュー画面の「パッチ設定」行、またはバージョン表示の長押しで開く。
各パッチを Switch で個別 ON/OFF（名前＋グレーの説明付き）。フッターにパッチ版とクレジット。
新規 Activity/リソースは使わずプログラム生成 `Dialog` で実現（設定は `domico_patch` SharedPreferences）。

詳細:
- 調査メモ: [docs/findings.md](docs/findings.md)
- 差分メモ: [docs/patch-notes.md](docs/patch-notes.md)
- ビルド/署名/インストール: [docs/install.md](docs/install.md)
- CI セットアップ(apkeep + Secrets): [docs/ci-setup.md](docs/ci-setup.md)
- Dev プレリリース手順: [docs/dev-prerelease.md](docs/dev-prerelease.md)
- パッチバージョン仕様: [docs/patch-versioning.md](docs/patch-versioning.md)

## パッチ方式の要点
- パッチは **`classes4.dex` のみ差し替え**（resources/manifest は公式とバイト一致）。
  apktool 全体リビルドは Xiaomi/HyperOS 等が `INSTALL_FAILED_USER_RESTRICTED` で弾くため不使用。
- 日本語は `config.ja` **スプリット**または**apkeep**による取得が必要（base には無し）。ローカルは端末 pull、CI は apkeepでGoogle Play(ja) から取得。

## 構成
```
scripts/
  patch_smali.py    # 全パッチの smali 適用（冪等・アンカー基準・版ズレで失敗）
  patch_assets/     # 追加する patch/*.smali（PatchPrefs/設定画面/インターセプタ等）
  patch_apk.py      # classes4.dex のみ baksmali→patch→smali で差し替え（リソース据置）
  build.py          # 入力(xapk/フォルダ)→dexパッチ→zipalign→全split署名→個別apk出力
  pull-splits.{ps1,sh}   # 端末の公式版から全スプリット(config.ja含む)を吸い出す
  setup-signing.{ps1,sh} # 署名鍵作成 + GitHub Secrets 登録（ワンショット）
  gen-keystore.{sh,ps1}  # 署名鍵生成のみ
.github/workflows/
  patch.yml          # 公式更新検知→Google Play(ja)取得→dexパッチ→再署名→Release(.apk添付)
  release.yml        # main push 時にパッチ版確定→CHANGELOG→patch-v* タグ+Release+APK添付
  dev-prerelease.yml # 手動実行・プレリリース作成（dev テスト用）
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
