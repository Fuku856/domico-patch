# CI セットアップ手順（apkeep + GitHub Secrets）

GitHub Actions（[`.github/workflows/patch.yml`](../.github/workflows/patch.yml)）で、公式 Domico の
更新検知 → Google Play から取得 → dex 差し替えパッチ → 再署名 → `.apks` を Release 添付、までを
自動化するための初期設定。**一度設定すれば以後は毎日自動実行**される。

CI が APK 取得に使うのが [apkeep](https://github.com/EFForg/apkeep)。apkeep のインストール自体は
ワークフロー内で行われるので、こちらで用意するのは **Google 認証用の AAS トークン**と
**署名鍵**を GitHub Secrets に登録することだけ。

> ⚠️ Play の非公式プロトコル利用はアカウント凍結リスクがあるため、**専用/サブの Google アカウント**を使う。

---

## 必要な Secrets 一覧

| Secret | 用途 | 登録方法 |
|--------|------|----------|
| `GOOGLE_EMAIL` | Play 取得に使う Google アカウントのメール | 本書 §2 |
| `GOOGLE_AAS_TOKEN` | 上記アカウントの AAS トークン | 本書 §1〜§2 |
| `KEYSTORE_BASE64` | 署名用 keystore を base64 化したもの | `scripts/setup-signing.*` |
| `KEYSTORE_PASSWORD` | keystore のパスワード(storepass) | 〃 |
| `KEY_PASSWORD` | 鍵のパスワード（省略時は KEYSTORE_PASSWORD を流用） | 〃 |
| `KEY_ALIAS` | 鍵のエイリアス | 〃 |

keystore 系 4 つは [`scripts/setup-signing.ps1`](../scripts/setup-signing.ps1) でまとめて生成・登録できる。
本書では Google 認証用の 2 つ（`GOOGLE_EMAIL` / `GOOGLE_AAS_TOKEN`）を中心に説明する。

---

## 1. AAS トークンを取得する（手元の PC で一度だけ）

AAS トークンは「oauth_token（使い捨て）」から変換して得る。**この作業はローカル（Windows のターミナル）**で行う。
CI 上ではない。

### 1-1. apkeep をローカルに入れる

PowerShell（このリポジトリのフォルダ内）で Windows 版バイナリを取得:

```powershell
curl.exe -L -o apkeep.exe "https://github.com/EFForg/apkeep/releases/latest/download/apkeep-x86_64-pc-windows-msvc.exe"
```

> `curl.exe` と明示するのは PowerShell の `curl` エイリアス（Invoke-WebRequest）を避けるため。
> `apkeep.exe` は `.gitignore` 済みなのでコミットされない。

### 1-2. oauth_token を取得する（Network タブから）

1. ブラウザの**シークレットウィンドウ**でサブ垢を `https://accounts.google.com/EmbeddedSetup` にログイン
2. ログイン前に **F12 → Network タブ**を開いておく
3. ログインを進め、**利用規約(ToS)ポップアップが出たら「同意」まで完了**する
4. Network タブで **`accounts.google.com` への最後のリクエスト**を選ぶ
5. **Cookies → Response Cookies** の中の **`oauth_token`** の `value`（`oauth2_4/` で始まる）を**末尾まで全部**コピー

> ハマりどころ:
> - **`oauth_token` は使い捨て＋数分で失効**。取得したら**すぐ**次のコマンドを実行する。失敗したら取り直す。
> - コピー元は Application→Cookies ではなく **Network タブの最後のリクエストの Response Cookies**。
> - 同意画面を最後まで進めてから取る。

### 1-3. oauth_token → AAS トークンに変換

```powershell
mkdir work\_t 2>$null
.\apkeep.exe -a jp.co.kyoritsu.domico -d google-play -e 'sub-account@gmail.com' --oauth-token 'oauth2_4/0Ad...（全部）' --accept-tos work\_t
```

> ハマりどころ:
> - **PowerShell では単一引用符 `'...'`** で囲む。二重引用符 `"..."` は中の `$` などを変数展開してトークンを壊す。
> - `oauth2_4/` の**プレフィックス込み**で渡す。
> - 出力先 `work\_t` は**事前に存在している必要**がある（`apkeep` は既存ディレクトリを要求）。

実行ログに表示される **AAS トークン（`aas_et/...`）** を控える。これが `GOOGLE_AAS_TOKEN` に登録する値。

---

## 2. GitHub Secrets に登録する

このリポジトリのフォルダで（`gh` CLI ログイン済み前提）:

```powershell
# メール（チャット等に貼らず自分で実行）
gh secret set GOOGLE_EMAIL --body 'sub-account@gmail.com'

# AAS トークン。--body を付けず標準入力で渡すと履歴に残りにくい
#   実行 → 値を1行貼り付け → Enter → Ctrl+Z → Enter
gh secret set GOOGLE_AAS_TOKEN
```

> ハマりどころ:
> - AAS トークンは**約340文字と長い**。`--body` で渡すときも**途中で切れない**よう注意。
>   過去に**途中切れで登録されて CI だけ取得失敗**した実績あり（ローカルでは同じトークンで成功するのに、
>   CI のログに `downloaded successfully!` が出ず無言で 0 件終了する症状）。
> - 末尾に余計な改行や引用符を含めない。

登録確認:

```powershell
gh secret list
```

`GOOGLE_EMAIL` / `GOOGLE_AAS_TOKEN` と keystore 系 4 つが並べば OK。

---

## 3. 動作確認（手動実行）

1. GitHub の **Actions** タブ → `Patch Domico (auto)` を開く
2. **Run workflow** → ブランチは `main`、`force` を `true` にして実行
3. ログの確認ポイント:
   - **Download latest Domico** ステップに **`jp.co.kyoritsu.domico downloaded successfully!`** が出ること
     （これが出ず `Downloading...` だけで終わるならトークン/Secret の問題。§2 のハマりどころ参照）
   - 末尾の `ls -la download/` に `base.apk` 系と `*config.ja*.apk` が並ぶこと
4. 成功すると Release に **`domico-<version>-patch.apks`（1ファイル）** が添付される

その後は `schedule`（毎日 00:00 UTC）で自動実行される。

### インストール
Release の `domico-<version>-patch.apks` を取得し、**SAI / Shizuku 等の分割対応インストーラ**で
この 1 ファイルを選んで入れる。日本語（`config.ja`）も同梱済み。
詳細は [install.md](install.md)。

---

## 4. AAS トークンのローテーション / セキュリティ

- AAS トークンは長命。**漏洩したら第三者がそのアカウントで Play 取得操作をできてしまう**ため、
  チャット・スクショ・ログ等に出してしまったら**作り直す**こと。
- 作り直しは §1-2〜§1-3 をやり直し、新しい `aas_et/...` を §2 で Secret に上書き登録するだけ。
  旧トークンは置き換われば使われなくなる。
- ローカルの `apkeep.exe` / `work/_t` / `work/_dl` 等は `.gitignore` 済み（コミットされない）。

---

## 5. トラブルシュート

| 症状 | 原因 | 対処 |
|------|------|------|
| `was not able to retrieve AAS token` | oauth_token が失効/使用済み、二重引用符でトークン破損 | §1-2 で取り直し、PowerShell は `'...'` で囲む |
| CI の Download で `downloaded successfully!` が出ず 0 件 | `GOOGLE_AAS_TOKEN` の値ミス（途中切れ等） | ローカルで同コマンドが通るか確認 → §2 で入れ直し |
| `OUTPATH is not a valid directory` | 出力先ディレクトリが未作成 | `mkdir` してから実行 |
| `unknown flag: --clobber`（Release 作成時） | `gh release create` は `--clobber` 非対応 | 既存 Release を `gh release delete` してから作成（現行ワークフローは対応済み） |
| Actions で run が 0 秒失敗・手動実行ボタンが消える | ワークフロー YAML が無効（`run:` 内に生の複数行文字列等） | YAML を修正。複数行 notes は `--notes-file` で渡す（現行は対応済み） |
| Xiaomi/HyperOS で `INSTALL_FAILED_USER_RESTRICTED` | resources.arsc 再構築を伴う apk | dex 差し替え＋`.apks` 同梱方式を使う（現行は対応済み）。[patch-notes.md](patch-notes.md) |

---

## 関連
- ビルド/署名/インストール: [install.md](install.md)
- 差分メモ（方式の理由）: [patch-notes.md](patch-notes.md)
- ワークフロー本体: [`.github/workflows/patch.yml`](../.github/workflows/patch.yml)
- Dev プレリリース（手動実行・実機テスト用）: [dev-prerelease.md](dev-prerelease.md)
