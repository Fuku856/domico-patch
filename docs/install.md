# ビルド・署名・インストール手順

## 方式の要点
- パッチは `classes4.dex` のみ差し替え（`resources.arsc` / `AndroidManifest.xml` は公式のまま）。理由は [patch-notes.md](patch-notes.md) を参照。
- 再署名版は公式と署名が異なるため、初回は **公式版をアンインストール** してから入れる（データはサーバ側、再ログインで復元）。Play の自動更新対象外。
- 分割APK（base + config 群）なので **まとめてインストール** する。
- 日本語は `config.ja` スプリットが必要。ローカルは端末から pull、CI は Google Play（`locale=ja_JP`）で取得する。

## A. ローカルでビルド（推奨）

### 必要ツール
- JDK 17+（`JAVA_HOME` 設定）
- Android SDK build-tools（`zipalign` / `apksigner`）、platform-tools（`adb`）
- `tools/baksmali.jar`、`tools/smali.jar`、Python 3

### 手順
```powershell
# 1) 署名鍵 + GitHub Secrets を用意（初回のみ）
powershell -ExecutionPolicy Bypass -File scripts/setup-signing.ps1

# 2) 端末の公式版（日本語表示状態）から全スプリットを吸い出す
powershell -ExecutionPolicy Bypass -File scripts/pull-splits.ps1
#   → work/device_splits/ に base.apk + split_config.ja.apk 等が落ちる

# 3) dex 差し替えパッチ + 全スプリット再署名
python scripts/build.py --input work/device_splits `
  --keystore work/domico.keystore --ks-pass "<pass>" --alias domico
#   → work/out/ に署名済みの base.apk と split_config.*.apk
```

### インストール
```cmd
set "adb=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
"%adb%" uninstall jp.co.kyoritsu.domico
"%adb%" install-multiple --no-incremental -r work\out\*.apk
```
- ファイル名は `dir work\out` の実出力に合わせる。
- `DELETE_FAILED_INTERNAL_ERROR` が出たら、端末でアイコン長押し→削除してから再実行。
- adb が弾かれる端末は Shizuku 対応インストーラで `work/out` の apk 群を入れてもよい。

## B. GitHub Actions（更新検知 → 自動パッチ → Release）

[`.github/workflows/patch.yml`](../.github/workflows/patch.yml):
- 毎日 公式更新を検知し、Google Play（`locale=ja_JP`）で取得する。
- dex 差し替えパッチ + 再署名し、全スプリットを1つにまとめた `domico-<version>-patch.apks` を Release に添付する。
- `workflow_dispatch` で手動実行も可（`force=true` で同一版でも再ビルド）。

### 初期設定（Secrets / apkeep）
必要な Secrets（`GOOGLE_EMAIL` / `GOOGLE_AAS_TOKEN` / keystore 系 4 つ）の登録と、
AAS トークンの取得手順・ハマりどころは **[ci-setup.md](ci-setup.md)** にまとめている。

> 取得できる密度・ABI は apkeep の既定デバイスプロファイル依存。端末に完全一致させたい場合は A（端末 pull）を使う。

### 取得・インストール
Releases の `domico-<version>-patch.apks`（分割APKを1つにまとめたもの）を取得し、SAI / Shizuku 等の分割対応インストーラでこの 1 ファイルを選んで入れる。日本語（`config.ja`）も同梱済み。
- adb で入れる場合は `.apks` を unzip し、出てきた `*.apk` を `adb install-multiple --no-incremental -r` する。
- 公式版とは署名が異なるため、初回は公式版のアンインストールが必要。

## 注意（ToS / 法務）
- ミラー / Play からの自動ダウンロードは各サービスの規約上グレー。利用は自己判断。
- 本改変は私的利用の UX 改善目的。サーバ通信・本来機能は変更していない（Dialog ウィンドウのフラグと、それを含む `classes4.dex` のみ変更）。
