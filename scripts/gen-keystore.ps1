# 署名用 keystore を生成する (ローカル/初回のみ)。
# 生成物 (*.keystore) と base64 は .gitignore 済み。コミットしないこと。
#
# 使い方:
#   pwsh scripts/gen-keystore.ps1 -Out work/domico.keystore -StorePass <pass> -Alias domico
param(
  [string]$Out = "work/domico.keystore",
  [string]$StorePass = "domico123",
  [string]$KeyPass = "",
  [string]$Alias = "domico"
)
if ($KeyPass -eq "") { $KeyPass = $StorePass }
$keytool = "$env:JAVA_HOME\bin\keytool.exe"
if (-not (Test-Path $keytool)) { throw "keytool not found; set JAVA_HOME" }
New-Item -ItemType Directory -Force (Split-Path $Out) | Out-Null
& $keytool -genkeypair -v -keystore $Out -storepass $StorePass -keypass $KeyPass `
  -alias $Alias -keyalg RSA -keysize 2048 -validity 10000 `
  -dname "CN=Domico Patch, OU=Dev, O=Personal, L=NA, ST=NA, C=JP"
Write-Host ""
Write-Host "keystore: $Out"
Write-Host "GitHub Secret 用 base64 (KEYSTORE_BASE64):"
[Convert]::ToBase64String([IO.File]::ReadAllBytes($Out)) | Write-Host
Write-Host ""
Write-Host "登録する Secrets: KEYSTORE_BASE64 / KEYSTORE_PASSWORD=$StorePass / KEY_PASSWORD=$KeyPass / KEY_ALIAS=$Alias"
