#!/usr/bin/env python3
"""
パッチ自身のセムバーを Conventional Commits から自動算出する。

このプロジェクトの GitHub リリース(v{ベース版}-patch)は「公式 Domico の更新追従」
であり、パッチ自身のバージョンとは別軸。そこでパッチ版は git 履歴から独立に算出する:

  - 基準(baseline): `patch-v*` タグのうち最大セムバー(無ければ 0.0.0)。
  - 増分(bump): 基準タグ以降のコミットメッセージ種別で決定。
      `type!:` もしくは本文に `BREAKING CHANGE` -> major
      `feat:`                                   -> minor
      `fix:` / `perf:` / `refactor:`            -> patch
      それ以外のみ(docs/ci/chore 等)            -> 据え置き(基準そのまま)
  - 表示(display): `v{X.Y.Z}[-dev+g{sha}[.dirty]][ / base {app}]`

タグ作成(リリース確定)は CI(.github/workflows/release.yml の git-cliff)が行い、
ここはタグを「読む」だけ。タグが無くても次版を算出して表示できる。

CLI:
  python scripts/version.py                          -> 表示文字列(release)
  python scripts/version.py --channel dev            -> dev 表示(`-dev+g<sha>`)
  python scripts/version.py --app-version 1.5.4      -> ` / base 1.5.4` を付与
  python scripts/version.py --number-only            -> `X.Y.Z` のみ(タグ/CHANGELOG 用)
"""
import argparse
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

TAG_PREFIX = "patch-v"
_TAG_RE = re.compile(r"^patch-v(\d+)\.(\d+)\.(\d+)$")
# Conventional Commits ヘッダ: `type(scope)!: subject`
_HEADER_RE = re.compile(r"^(?P<type>[a-zA-Z]+)(?:\([^)]*\))?(?P<bang>!)?:")


def _git(args, default=None):
    """git をリポジトリルートで実行し stdout を返す。失敗時は default。

    コミットメッセージは UTF-8(日本語含む)。Windows の既定ロケール(cp932)で
    デコードすると壊れるため、明示的に UTF-8 + errors='replace' で読む。
    """
    try:
        p = subprocess.run(
            ["git", "-C", ROOT, *args],
            capture_output=True, text=True,
            encoding="utf-8", errors="replace",
        )
        if p.returncode == 0:
            return p.stdout
    except Exception:
        pass
    return default


def latest_tag():
    """`patch-v*` タグの最大セムバーを (tuple, tag_str) で返す。無ければ ((0,0,0), None)。"""
    out = _git(["tag", "--list", f"{TAG_PREFIX}*"], default="") or ""
    best, best_tag = (0, 0, 0), None
    for line in out.splitlines():
        m = _TAG_RE.match(line.strip())
        if not m:
            continue
        v = (int(m.group(1)), int(m.group(2)), int(m.group(3)))
        if v > best:
            best, best_tag = v, line.strip()
    return best, best_tag


def _commit_messages_since(tag):
    """tag(無ければ全履歴)以降のコミット全文を新しい順のリストで返す。"""
    rng = f"{tag}..HEAD" if tag else "HEAD"
    # %B(本文)を区切り文字 \x1e で連結し、各コミットを分割する。
    out = _git(["log", rng, "--format=%B%x1e"], default="")
    if out is None:
        out = ""
    return [c.strip() for c in out.split("\x1e") if c.strip()]


def _bump_level(messages):
    """コミット群から必要な増分レベルを返す: 'major'>'minor'>'patch'>None。"""
    level = None
    rank = {"patch": 1, "minor": 2, "major": 3}

    def raise_to(new):
        nonlocal level
        if level is None or rank[new] > rank[level]:
            level = new

    for msg in messages:
        header = msg.splitlines()[0] if msg else ""
        m = _HEADER_RE.match(header)
        breaking = "BREAKING CHANGE" in msg or "BREAKING-CHANGE" in msg
        if m and m.group("bang"):
            breaking = True
        if breaking:
            raise_to("major")
            continue
        if not m:
            continue
        t = m.group("type").lower()
        if t == "feat":
            raise_to("minor")
        elif t in ("fix", "perf", "refactor"):
            raise_to("patch")
    return level


def next_version():
    """
    次版を算出して dict で返す:
      base    : (M,m,p)   基準タグの版
      tag     : str|None  基準タグ
      version : (M,m,p)   算出した次版(増分なしなら base と同じ)
      level   : str|None  増分レベル
      bumped  : bool      base から進んだか
    """
    base, tag = latest_tag()
    msgs = _commit_messages_since(tag)
    level = _bump_level(msgs)
    M, m, p = base
    if level == "major":
        nxt = (M + 1, 0, 0)
    elif level == "minor":
        nxt = (M, m + 1, 0)
    elif level == "patch":
        nxt = (M, m, p + 1)
    else:
        nxt = base
    return {
        "base": base, "tag": tag, "version": nxt,
        "level": level, "bumped": nxt != base,
    }


def short_sha():
    return (_git(["rev-parse", "--short=8", "HEAD"], default="") or "").strip() or "0000000"


def is_dirty():
    out = _git(["status", "--porcelain"], default="")
    return bool((out or "").strip())


def vstr(v):
    return f"{v[0]}.{v[1]}.{v[2]}"


def format_version(channel="release", app_version=None):
    """設定画面に埋め込む表示文字列を組み立てる。"""
    info = next_version()
    core = f"v{vstr(info['version'])}"
    if channel == "dev":
        suffix = f"-dev+g{short_sha()}"
        if is_dirty():
            suffix += ".dirty"
        core += suffix
    if app_version:
        core += f" / base {app_version}"
    return core


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--channel", choices=["release", "dev"], default="release")
    ap.add_argument("--app-version", help="ベースアプリの versionName(表示に付与)")
    ap.add_argument("--number-only", action="store_true",
                    help="算出した次版を X.Y.Z 形式でのみ出力(タグ/CHANGELOG 用)")
    ap.add_argument("--print-bumped", action="store_true",
                    help="基準タグから増分があれば 1、無ければ 0 を最後に出力")
    args = ap.parse_args()

    if args.number_only:
        info = next_version()
        sys.stdout.write(vstr(info["version"]) + "\n")
        if args.print_bumped:
            sys.stdout.write(("1" if info["bumped"] else "0") + "\n")
        return

    sys.stdout.write(format_version(args.channel, args.app_version) + "\n")


if __name__ == "__main__":
    main()
