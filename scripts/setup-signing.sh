#!/usr/bin/env bash
# 署名鍵の作成 と GitHub Secrets 登録 をまとめて行う対話スクリプト (Linux/mac/Git-Bash/WSL)
#
# やること:
#   1) 署名用 keystore を作成 (既にあれば再利用)
#   2) keystore を base64 化
#   3) GitHub Secrets を登録: KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_PASSWORD / KEY_ALIAS
#
# 必要: JDK(keytool), GitHub CLI `gh`(認証済み: `gh auth login`)
#
# 実行例 (リポジトリ直下で):
#   bash scripts/setup-signing.sh
#   （パスワードとエイリアスだけ入力すれば完了します）
#
# 任意の環境変数で上書き可: KEYSTORE / ALIAS / REPO
set -euo pipefail

KEYSTORE="${KEYSTORE:-work/domico.keystore}"
ALIAS="${ALIAS:-}"
REPO="${REPO:-}"

KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
command -v "$KEYTOOL" >/dev/null 2>&1 || { echo "keytool が見つかりません。JAVA_HOME を設定してください。" >&2; exit 1; }
command -v gh >/dev/null 2>&1 || { echo "GitHub CLI 'gh' が見つかりません。https://cli.github.com からインストールしてください。" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh が未認証です。先に 'gh auth login' を実行してください。" >&2; exit 1; }

if [ -z "$REPO" ]; then
  REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)" || { echo "リポジトリを自動判定できません。REPO=owner/repo を指定してください。" >&2; exit 1; }
fi
echo "対象リポジトリ: $REPO"

if [ -z "$ALIAS" ]; then
  read -r -p "鍵エイリアス (Enter で既定 'domico'): " ALIAS
  ALIAS="${ALIAS:-domico}"
fi

mkdir -p "$(dirname "$KEYSTORE")"

if [ -f "$KEYSTORE" ]; then
  echo "既存 keystore を再利用します: $KEYSTORE"
  read -r -s -p "既存 keystore のパスワード: " PW; echo
  "$KEYTOOL" -list -keystore "$KEYSTORE" -storepass "$PW" -alias "$ALIAS" >/dev/null 2>&1 \
    || { echo "パスワードまたはエイリアス '$ALIAS' が一致しません。" >&2; exit 1; }
else
  echo "新規 keystore を作成します: $KEYSTORE"
  read -r -s -p "新しい keystore パスワード (6文字以上): " PW; echo
  read -r -s -p "確認のためもう一度: " PW2; echo
  [ "$PW" = "$PW2" ] || { echo "パスワードが一致しません。" >&2; exit 1; }
  [ "${#PW}" -ge 6 ] || { echo "パスワードは6文字以上にしてください。" >&2; exit 1; }
  "$KEYTOOL" -genkeypair -v -keystore "$KEYSTORE" -storepass "$PW" -keypass "$PW" \
    -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Domico Patch, OU=Dev, O=Personal, L=NA, ST=NA, C=JP"
fi

if base64 --help 2>&1 | grep -q -- "-w"; then
  B64="$(base64 -w0 "$KEYSTORE")"
else
  B64="$(base64 "$KEYSTORE" | tr -d '\n')"   # macOS
fi

echo "GitHub Secrets を登録中..."
printf '%s' "$B64"    | gh secret set KEYSTORE_BASE64   --repo "$REPO"
printf '%s' "$PW"     | gh secret set KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$PW"     | gh secret set KEY_PASSWORD      --repo "$REPO"
printf '%s' "$ALIAS"  | gh secret set KEY_ALIAS         --repo "$REPO"

unset PW PW2 B64 || true

echo
echo "完了しました。"
echo "  keystore : $KEYSTORE  (※ .gitignore 済み。バックアップ推奨・紛失すると同一署名で更新版を出せません)"
echo "  Secrets  : KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_PASSWORD / KEY_ALIAS ($REPO)"
echo "確認: gh secret list --repo $REPO"
