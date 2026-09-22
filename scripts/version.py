#!/usr/bin/env python3
"""
パッチ自身のセムバーを Conventional Commits から自動算出する。

このプロジェクトの GitHub リリース(v{ベース版}-patch)は「公式 Domico の更新追従」
であり、パッチ自身のバージョンとは別軸。そこでパッチ版は git 履歴から独立に算出する:

  - 基準(baseline): `patch-v{X.Y.Z}` タグのうち最大セムバー(無ければ 0.0.0)。
      dev プレリリースのタグ(`patch-v{X.Y.Z}-dev.{N}`)は `_TAG_RE` が弾くため
      基準にならない。
  - 増分(bump): 基準タグ以降のリリース対象コミットのうち **最も強いレベルを1回だけ**
      適用する。main へのマージ 1 回 = 1 リリース = 1 bump。
        `type!:` もしくは本文に `BREAKING CHANGE` -> major (M+1,0,0)
        `feat:`                                   -> minor (M,m+1,0)
        `fix:` / `perf:` / `refactor:`            -> patch (M,m,p+1)
        それ以外(docs/ci/chore 等)                -> リリース対象外
      セムバーの番号は「そのリリースが含む変更の最大の重さ」を表すので、feat が
      何本入っていてもリリースとしては minor 1 回(0.5.3 -> 0.6.0)。コミット本数
      では刻まない。
  - dev プレリリース: 同じ次版に対して `-dev.{N}` の連番を振る(N は既存の
      `patch-v{X.Y.Z}-dev.*` タグの最大 + 1、無ければ 1)。
        0.6.0-dev.1 < 0.6.0-dev.2 < 0.6.0
      とセムバーのプレリリース順序が保たれ、本リリースは必ず 0.6.0 に収束する。
      「dev ビルドごとに別タグを残す」要件は版番号ではなくこの連番が担う。
  - 表示(display): `v{X.Y.Z}[-dev.{N}+g{sha}[.dirty]][ / base v{app}]`

bump はコミット本数ではなく最高レベル 1 回なので、dev->main のマージを squash しても
最終的な版はずれない(squash 後のメッセージに `feat:`/`fix:` 等の種別が残っていればよい)。

タグ作成(リリース確定)は CI(.github/workflows/release.yml)が行い、ここはタグを
「読む」だけ。タグが無くても次版を算出して表示できる。

CLI:
  python scripts/version.py                          -> 表示文字列(release)
  python scripts/version.py --channel dev            -> dev 表示(`-dev.N+g<sha>`)
  python scripts/version.py --app-version 1.5.4      -> ` / base v1.5.4` を付与
  python scripts/version.py --number-only            -> `X.Y.Z` のみ(タグ/CHANGELOG 用)
  python scripts/version.py --dev-seq                -> dev プレリリース連番 N のみ
"""
import argparse
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

TAG_PREFIX = "patch-v"
_TAG_RE = re.compile(r"^patch-v(\d+)\.(\d+)\.(\d+)$")
# dev プレリリースタグ。`.N` 無しは旧形式(`patch-v0.6.0-dev`)で、連番 0 とみなす。
_DEV_TAG_RE = re.compile(r"^patch-v(\d+)\.(\d+)\.(\d+)-dev(?:\.(\d+))?$")
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


_TYPE_LEVEL = {"feat": "minor", "fix": "patch", "perf": "patch", "refactor": "patch"}
_RANK = {"patch": 1, "minor": 2, "major": 3}


def _commit_level(msg):
    """1 コミットの増分レベルを返す: 'major'|'minor'|'patch'|None。"""
    header = msg.splitlines()[0] if msg else ""
    m = _HEADER_RE.match(header)
    breaking = "BREAKING CHANGE" in msg or "BREAKING-CHANGE" in msg
    if m and m.group("bang"):
        breaking = True
    if breaking:
        return "major"
    if not m:
        return None
    return _TYPE_LEVEL.get(m.group("type").lower())


def _highest_level(messages):
    """コミット群の中で最も強い増分レベルを返す。対象が無ければ None。"""
    top = None
    for msg in messages:
        lvl = _commit_level(msg)
        if lvl is None:
            continue
        if top is None or _RANK[lvl] > _RANK[top]:
            top = lvl
    return top


def _apply_bump(base, level):
    """基準版に増分を **1 回だけ** 適用する。上位を上げたら下位はリセット。

    リリース 1 回 = bump 1 回。リリース対象コミットが何本あっても、版は最も強い
    レベルの分だけ進む(feat 3 本 + fix 2 本 -> minor 1 回 -> 0.5.3 -> 0.6.0)。
    セムバーの番号は変更の「重さ」を表すものであって、コミット数ではないため。
    """
    M, m, p = base
    if level == "major":
        return (M + 1, 0, 0)
    if level == "minor":
        return (M, m + 1, 0)
    if level == "patch":
        return (M, m, p + 1)
    return base


def next_version():
    """
    次版を算出して dict で返す:
      base    : (M,m,p)   基準タグの版
      tag     : str|None  基準タグ
      version : (M,m,p)   算出した次版(増分なしなら base と同じ)
      level   : str|None  適用した増分のレベル
      bumped  : bool      base から進んだか
    """
    base, tag = latest_tag()
    msgs = _commit_messages_since(tag)
    level = _highest_level(msgs)
    nxt = _apply_bump(base, level)
    return {
        "base": base, "tag": tag, "version": nxt,
        "level": level, "bumped": nxt != base,
    }


def dev_seq(version):
    """version に対する次の dev プレリリース連番 N を返す(既存タグの最大 + 1)。

    `patch-v{X.Y.Z}-dev.{N}` タグを走査するだけなので、CI では checkout 時に
    `fetch-tags: true` が必要。旧形式の `patch-v{X.Y.Z}-dev`(連番なし)は 0 と
    みなすため、混在していても次は 1 以上になる。
    """
    out = _git(["tag", "--list", f"{TAG_PREFIX}{vstr(version)}-dev*"], default="") or ""
    best = 0
    for line in out.splitlines():
        m = _DEV_TAG_RE.match(line.strip())
        if not m:
            continue
        if (int(m.group(1)), int(m.group(2)), int(m.group(3))) != tuple(version):
            continue
        n = int(m.group(4)) if m.group(4) else 0
        best = max(best, n)
    return best + 1


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
        # `-dev.N` はセムバーのプレリリース識別子、`+g<sha>` はビルドメタデータ。
        # 0.6.0-dev.2 < 0.6.0 の順序が保たれる。
        suffix = f"-dev.{dev_seq(info['version'])}+g{short_sha()}"
        if is_dirty():
            suffix += ".dirty"
        core += suffix
    if app_version:
        core += f" / base v{app_version}"
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
    ap.add_argument("--dev-seq", action="store_true",
                    help="次版に対する dev プレリリース連番 N のみ出力")
    args = ap.parse_args()

    if args.dev_seq:
        sys.stdout.write(str(dev_seq(next_version()["version"])) + "\n")
        return

    if args.number_only:
        info = next_version()
        sys.stdout.write(vstr(info["version"]) + "\n")
        if args.print_bumped:
            sys.stdout.write(("1" if info["bumped"] else "0") + "\n")
        return

    sys.stdout.write(format_version(args.channel, args.app_version) + "\n")


if __name__ == "__main__":
    main()
