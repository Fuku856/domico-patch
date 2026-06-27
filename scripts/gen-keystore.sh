#!/usr/bin/env bash
# 署名用 keystore を生成する (ローカル/初回のみ)。
# 生成物 (*.keystore) と base64 は .gitignore 済み。コミットしないこと。
#
# 使い方:
#   scripts/gen-keystore.sh [out] [storepass] [keypass] [alias]
set -euo pipefail
OUT="${1:-work/domico.keystore}"
STOREPASS="${2:-domico123}"
KEYPASS="${3:-$STOREPASS}"
ALIAS="${4:-domico}"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
mkdir -p "$(dirname "$OUT")"
"$KEYTOOL" -genkeypair -v -keystore "$OUT" -storepass "$STOREPASS" -keypass "$KEYPASS" \
  -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Domico Patch, OU=Dev, O=Personal, L=NA, ST=NA, C=JP"
echo
echo "keystore: $OUT"
echo "GitHub Secret 用 base64 (KEYSTORE_BASE64):"
base64 -w0 "$OUT"; echo
echo
echo "登録する Secrets: KEYSTORE_BASE64 / KEYSTORE_PASSWORD=$STOREPASS / KEY_PASSWORD=$KEYPASS / KEY_ALIAS=$ALIAS"
