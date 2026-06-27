# ビルド・署名・インストール手順

## 重要な前提
- 再署名版は公式とは **署名が異なる** ため、初回は **公式版をアンインストール** してから入れる。
  - 食事予約・メッセージ等のデータはサーバ側にあり、再ログインで復元される(ローカル設定は消える)。
- 再署名版は **Google Play の自動更新対象外**。更新は本リポジトリの GitHub Release から取得する。
- 分割APK(base + config×3)なので、**4つまとめてインストール**する(install-multiple / SAI)。

## A. ローカルでビルドする

### 必要ツール
- JDK 17+ (例: Eclipse Adoptium。`JAVA_HOME` 設定)
- Android SDK build-tools (`zipalign`/`apksigner`)、platform-tools (`adb`)
- `tools/apktool.jar`(下記スクリプトで取得)、Python 3

### 手順 (Windows / PowerShell)
```powershell
# 1) ツール取得 (apktool / jadx)
#    既に tools/apktool.jar があれば不要
Invoke-WebRequest "https://github.com/iBotPeaches/Apktool/releases/download/v3.0.2/apktool_3.0.2.jar" -OutFile tools\apktool.jar

# 2) 署名鍵を作成 (初回のみ)
pwsh scripts/gen-keystore.ps1 -Out work/domico.keystore -StorePass <pass> -Alias domico

# 3) ビルド (入力は公式 XAPK)
python scripts/build.py --input Domico_1.5.4.xapk `
  --keystore work/domico.keystore --ks-pass <pass> --alias domico
```
出力: `work/out/base.apk`, `work/out/config.*.apk`, まとめ `work/out/Domico-patched.apks`

### インストール
- **adb (USB)**: `adb install-multiple -r work/out/base.apk work/out/config.arm64_v8a.apk work/out/config.en.apk work/out/config.mdpi.apk`
  - もしくは `python scripts/build.py ... --install`
- **SAI (Split APKs Installer)**: `Domico-patched.apks` を端末に転送して SAI で開く。

## B. GitHub Actions で自動化する (公式更新検知 → 自動パッチ → Release)

### 1) Secrets を登録
`gen-keystore` の出力に従い、リポジトリの Settings → Secrets に登録:
- `KEYSTORE_BASE64` : keystore を base64 化した文字列
- `KEYSTORE_PASSWORD` : storepass
- `KEY_PASSWORD` : keypass(storepass と同じなら省略可)
- `KEY_ALIAS` : 例 `domico`

### 2) ワークフロー
[`.github/workflows/patch.yml`](../.github/workflows/patch.yml):
- 毎日 00:00 UTC に `apkeep`(APKPure ソース)で最新 Domico を取得
- versionName からタグ `v<ver>-patch` を作り、既存 Release が無ければビルド
- `scripts/build.py` でパッチ・再署名し、署名済み分割APKと `.apks` を **GitHub Release** に公開
- 手動実行 (`workflow_dispatch`) も可。`force=true` で同一版でも再ビルド

### 3) 取得・インストール
Releases から `*.apk` 4点(または `Domico-patched.apks`)を取得し、上記「インストール」と同様に導入。

## 注意 (ToS / 法務)
- ミラー(APKPure)からの自動ダウンロードは配布元の規約上グレー。利用は自己判断。
- 本改変は私的利用の UX 改善目的。サーバとの通信・本来機能は変更していない(UI ウィンドウのフラグのみ変更)。
