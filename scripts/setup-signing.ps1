# 署名鍵の作成 と GitHub Secrets 登録 をまとめて行う対話スクリプト (Windows / PowerShell)
#
# やること:
#   1) 署名用 keystore を作成 (既にあれば再利用)
#   2) keystore を base64 化
#   3) GitHub Secrets を登録: KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_PASSWORD / KEY_ALIAS
#
# 必要: JAVA_HOME(JDK), GitHub CLI `gh`(認証済み: `gh auth login`)
#
# 実行例 (リポジトリ直下で):
#   powershell -ExecutionPolicy Bypass -File scripts/setup-signing.ps1
#   （パスワードとエイリアスだけ入力すれば完了します）
#
# 任意パラメータ:
#   -Keystore <path>  既定 work/domico.keystore
#   -Alias <name>     既定 domico
#   -Repo <owner/repo> 既定: カレントの git リモートから自動判定

param(
  [string]$Keystore = "work/domico.keystore",
  [string]$Alias = "",
  [string]$Repo = ""
)

$ErrorActionPreference = "Stop"

function Read-Plain([System.Security.SecureString]$sec) {
  $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
  try { [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr) }
  finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

# --- 前提チェック ---
$keytool = "$env:JAVA_HOME\bin\keytool.exe"
if (-not (Test-Path $keytool)) { throw "keytool が見つかりません。JAVA_HOME を設定してください (現在: '$env:JAVA_HOME')" }

$gh = (Get-Command gh -ErrorAction SilentlyContinue)
if (-not $gh) { throw "GitHub CLI `gh` が見つかりません。https://cli.github.com からインストールしてください。" }
& gh auth status 1>$null 2>$null
if ($LASTEXITCODE -ne 0) { throw "gh が未認証です。先に `gh auth login` を実行してください。" }

if ([string]::IsNullOrWhiteSpace($Repo)) {
  $Repo = (& gh repo view --json nameWithOwner -q .nameWithOwner) 2>$null
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($Repo)) {
    throw "リポジトリを自動判定できませんでした。-Repo owner/repo を指定してください。"
  }
}
Write-Host "対象リポジトリ: $Repo" -ForegroundColor Cyan

# --- エイリアス ---
if ([string]::IsNullOrWhiteSpace($Alias)) {
  $inp = Read-Host "鍵エイリアス (Enter で既定 'domico')"
  $Alias = if ([string]::IsNullOrWhiteSpace($inp)) { "domico" } else { $inp.Trim() }
}

# --- keystore 準備 ---
New-Item -ItemType Directory -Force (Split-Path $Keystore) | Out-Null

if (Test-Path $Keystore) {
  Write-Host "既存 keystore を再利用します: $Keystore" -ForegroundColor Yellow
  $sec = Read-Host "既存 keystore のパスワードを入力" -AsSecureString
  $pw = Read-Plain $sec
  # 検証
  & $keytool -list -keystore $Keystore -storepass $pw -alias $Alias 1>$null 2>$null
  if ($LASTEXITCODE -ne 0) {
    throw "keystore のパスワードまたはエイリアス '$Alias' が一致しません。"
  }
} else {
  Write-Host "新規 keystore を作成します: $Keystore" -ForegroundColor Green
  $sec1 = Read-Host "新しい keystore パスワードを入力 (6文字以上)" -AsSecureString
  $sec2 = Read-Host "確認のためもう一度入力" -AsSecureString
  $pw = Read-Plain $sec1
  if ($pw -ne (Read-Plain $sec2)) { throw "パスワードが一致しません。" }
  if ($pw.Length -lt 6) { throw "パスワードは6文字以上にしてください。" }
  & $keytool -genkeypair -v -keystore $Keystore -storepass $pw -keypass $pw `
    -alias $Alias -keyalg RSA -keysize 2048 -validity 10000 `
    -dname "CN=Domico Patch, OU=Dev, O=Personal, L=NA, ST=NA, C=JP"
  if ($LASTEXITCODE -ne 0) { throw "keytool による鍵生成に失敗しました。" }
}

# --- base64 ---
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($Keystore))

# --- Secrets 登録 (store と key のパスワードは同一前提) ---
Write-Host "GitHub Secrets を登録中..." -ForegroundColor Cyan
& gh secret set KEYSTORE_BASE64   --repo $Repo --body $b64
& gh secret set KEYSTORE_PASSWORD --repo $Repo --body $pw
& gh secret set KEY_PASSWORD      --repo $Repo --body $pw
& gh secret set KEY_ALIAS         --repo $Repo --body $Alias

# 後始末
$pw = $null; $b64 = $null
[GC]::Collect()

Write-Host ""
Write-Host "完了しました。" -ForegroundColor Green
Write-Host "  keystore : $Keystore  (※ .gitignore 済み。バックアップ推奨・紛失すると更新版を同一署名で出せません)"
Write-Host "  Secrets  : KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_PASSWORD / KEY_ALIAS ($Repo)"
Write-Host "確認: gh secret list --repo $Repo"
