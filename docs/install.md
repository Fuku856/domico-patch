# ビルド・署名・インストール手順

## 方式の要点（重要）
- パッチは **apktool 全体リビルドではなく、`classes4.dex` のみ差し替え**（`resources.arsc` /
  `AndroidManifest.xml` は公式とバイト一致）。apktool で作り直すと一部端末
  (Xiaomi/HyperOS 等) が `INSTALL_FAILED_USER_RESTRICTED: Invalid apk` で弾くため。
- 再署名版は公式と署名が異なるため、初回は **公式版をアンインストール** してから入れる
  （データはサーバ側、再ログインで復元）。Play 自動更新の対象外。
- 分割APK（base + config 群）なので **まとめてインストール**（adb install-multiple / SAI）。
- **日本語**は `config.ja` スプリットが必要。apk-pure はロケール別配信で英語しか出ない
  ことが多いので、ローカルは「端末から吸い出す」、CI は「Google Play から ja 取得」を使う。

## A. ローカルでビルド（日本語入り・推奨）

### 必要ツール
- JDK 17+（`JAVA_HOME` 設定）
- Android SDK build-tools（`zipalign`/`apksigner`）, platform-tools（`adb`）
- `tools/baksmali.jar`, `tools/smali.jar`, Python 3

### 手順
```powershell
# 1) 署名鍵 + GitHub Secrets をまとめて用意（初回のみ）
powershell -ExecutionPolicy Bypass -File scripts/setup-signing.ps1

# 2) 端末に公式 Domico を Play から入れ、日本語表示の状態で全スプリットを吸い出す
powershell -ExecutionPolicy Bypass -File scripts/pull-splits.ps1
#   → work/device_splits/ に base.apk + split_config.ja.apk 等が落ちる
#   （split_config.ja.apk が含まれることを確認）

# 3) classes4.dex 差し替えパッチ＋全スプリット再署名
python scripts/build.py --input work/device_splits `
  --keystore work/domico.keystore --ks-pass "<pass>" --alias domico
#   → work/out/ に署名済みの base.apk と split_config.*.apk
```

### インストール（adb / Xiaomi でも可）
```cmd
set "adb=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
"%adb%" uninstall jp.co.kyoritsu.domico
"%adb%" install-multiple --no-incremental -r work\out\base.apk work\out\split_config.arm64_v8a.apk work\out\split_config.ja.apk work\out\split_config.xxxhdpi.apk
```
- ファイル名は `work\out` の実際の出力に合わせる（`dir work\out`）。
- `--no-incremental` 推奨。`DELETE_FAILED_INTERNAL_ERROR` 時は端末でアイコン長押し→削除。
- Xiaomi/HyperOS で `USER_RESTRICTED` が出る場合でも、本方式(dex差し替え/リソース据置)なら
  通る。どうしても adb が弾かれる端末は **Shizuku 対応インストーラ** で `work/out` の
  apk 群を入れてもよい（USB起点の制限を回避）。

## B. GitHub Actions（更新検知→自動パッチ→Release、日本語入り）

`.github/workflows/patch.yml`:
- 毎日 公式更新を検知し、**Google Play から locale=ja_JP で取得**（config.ja を含む）
- `classes4.dex` 差し替えパッチ＋再署名し、**個別 .apk を Release に添付**
- 手動実行 `workflow_dispatch`（`force=true` で同一版でも再ビルド）も可

### 必要な Secrets
keystore 系（`setup-signing.*` で登録可）:
- `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_PASSWORD` / `KEY_ALIAS`

Google Play 取得用（無料）:
- `GOOGLE_EMAIL` … Google アカウントのメール
- `GOOGLE_AAS_TOKEN` … そのアカウントの AAS トークン

> 料金はかかりません（自分のアカウント権利で無料アプリを取得するだけ）。
> ただし Play の非公式プロトコル利用は**アカウント凍結リスク**があるため、
> メインではなく**専用/サブの Google アカウント**を使ってください。

### AAS トークンの作り方（一度だけ）
1. ブラウザで `https://accounts.google.com/EmbeddedSetup` にサブ垢でログインし、
   `oauth_token`（`oauth2_4/...` で始まる値）を取得（Cookie / 同意画面から）。
2. ローカルで apkeep に渡して長期 AAS トークンを取得:
   ```bash
   apkeep -a jp.co.kyoritsu.domico -d google-play \
     -e "<email>" --oauth-token "<oauth_token>" --accept-tos work/_t
   ```
   実行時に表示/利用される **AAS トークン**を控える（`-i` ini や標準出力に出る）。
3. `GOOGLE_EMAIL` と `GOOGLE_AAS_TOKEN` を GitHub Secrets に登録。

> 取得できる密度/ABI は apkeep の既定デバイスプロファイル依存。端末に完全一致させたい
> 場合は A（端末 pull）を使う。

### 取得・インストール
Releases の `*.apk` を全部取得し、上記「インストール」と同様に install-multiple。

## 注意（ToS / 法務）
- ミラー/Play からの自動ダウンロードは各サービスの規約上グレー。利用は自己判断。
- 本改変は私的利用の UX 改善目的。サーバ通信・本来機能は変更していない
  （Dialog ウィンドウのフラグと、それを含む classes4.dex のみ変更）。
