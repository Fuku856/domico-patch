# 端末にインストール済みの公式 Domico から、全スプリット(base + config.ja 等)を吸い出す。
# 取得した一式を build.py に --input で渡せば、日本語入り・端末最適密度のままパッチ＆再署名できる。
#
# 前提: 公式 Domico を Google Play から端末にインストール済み・日本語表示状態。USBデバッグ有効。
#
# 使い方:
#   powershell -ExecutionPolicy Bypass -File scripts/pull-splits.ps1
#   → work/device_splits/ に *.apk が落ちる
param(
  [string]$Package = "jp.co.kyoritsu.domico",
  [string]$Out = "work/device_splits"
)
$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb が見つかりません: $adb" }

# 端末確認
$devs = & $adb devices
if (-not ($devs -match "\sdevice$")) { throw "端末が 'device' 状態ではありません。USBデバッグ許可を確認。`n$devs" }

# 出力先
if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force $Out | Out-Null

# pm path で全スプリットのパスを取得
$paths = & $adb shell pm path $Package
if (-not $paths) { throw "公式 $Package が端末に見つかりません。Google Play からインストールしてください。" }

$count = 0
foreach ($line in $paths) {
  if ($line -match "^package:(.+)$") {
    $remote = $Matches[1].Trim()
    $name = Split-Path $remote -Leaf       # 例: base.apk / split_config.ja.apk
    & $adb pull $remote "$Out/$name" | Out-Null
    Write-Host "pulled: $name"
    $count++
  }
}
Write-Host ""
Write-Host "$count 個のスプリットを $Out に取得しました。"
Get-ChildItem $Out | Select-Object Name,Length
Write-Host ""
Write-Host "次: 本番鍵でパッチ＆再署名"
Write-Host "  python scripts/build.py --input $Out --keystore work/domico.keystore --ks-pass <pass> --alias domico"
