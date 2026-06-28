#!/usr/bin/env bash
# 端末の公式 Domico から全スプリット(base + config.ja 等)を吸い出す。
# 取得物を build.py に --input で渡せば日本語入りのままパッチ&再署名できる。
# 前提: 公式 Domico を Play から導入済み・日本語表示・USBデバッグ有効。
set -euo pipefail
PKG="${1:-jp.co.kyoritsu.domico}"
OUT="${2:-work/device_splits}"
ADB="${ANDROID_SDK_ROOT:-$LOCALAPPDATA/Android/Sdk}/platform-tools/adb"
command -v adb >/dev/null 2>&1 && ADB=adb
rm -rf "$OUT"; mkdir -p "$OUT"
"$ADB" get-state >/dev/null
n=0
while read -r line; do
  p="${line#package:}"; p="$(echo "$p" | tr -d '\r')"
  [ -z "$p" ] && continue
  name="$(basename "$p")"
  "$ADB" pull "$p" "$OUT/$name" >/dev/null
  echo "pulled: $name"; n=$((n+1))
done < <("$ADB" shell pm path "$PKG")
echo; echo "$n splits -> $OUT"; ls -l "$OUT"
echo "次: python scripts/build.py --input $OUT --keystore work/domico.keystore --ks-pass <pass> --alias domico"
