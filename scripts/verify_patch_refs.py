#!/usr/bin/env python3
"""
パッチクラスへのクロス dex 参照が全て解決できることを検証する。

パッチ本体を classes5 の Java へ移していくと、classes4 に残る smali
(公式コードへ注入した trampoline と、未移行のパッチ smali) から
classes5 のクラスを参照する形になる。この参照は smali のアセンブル時には
検査されず、**実行時に初めて** 解決される。つまりフィールド名を1文字
変えただけでも、ビルドは通るのに実機で NoSuchFieldError になる。

逆向き (classes5 の Java から未移行の smali クラスを呼ぶ) も同じ危うさがある。
こちらは module/stubs の手書きスタブ越しにコンパイルするため、スタブと smali の
実体がずれても javac も d8 も成功してしまう。しかも呼び出し元の PatchInit は
Throwable を握り潰すので、失敗しても無言で機能が止まる。

このスクリプトは classes4 の smali ツリーとモジュール dex の両方から
  Lvn/com/bravesoft/androidapp/patch/X;->member
という参照を全て集め、smali 側の定義とモジュール dex 側の定義を合わせた
集合で解決できるかを確認する。解決できないものがあれば非0で終了する。

使い方:
  python scripts/verify_patch_refs.py --smali-dir work/dexpatch --module-dex work/dexpatch/classes5.dex
"""

import argparse
import glob
import os
import re
import shutil
import subprocess
import sys

for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8")
    except Exception:
        pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_PKG = "vn/com/bravesoft/androidapp"
PKG = APP_PKG + "/patch"

# Lvn/com/bravesoft/androidapp/patch/X;->member  (member はフィールドかメソッド)
REF_RE = re.compile(r"L(" + PKG + r"/[A-Za-z0-9_$]+);->([^\s,;]+(?:\([^)]*\)[^\s,]+|:[^\s,]+))")
# 型としてだけ現れる参照 (new-instance / check-cast / .implements / 引数型 など)
TYPE_RE = re.compile(r"L(" + PKG + r"/[A-Za-z0-9_$]+);")

CLASS_RE = re.compile(r"^\.class\s+.*?L([^;]+);")
FIELD_RE = re.compile(r"^\.field\s+(?:[\w-]+\s+)*([A-Za-z0-9_$<>]+:\S+)")
METHOD_RE = re.compile(r"^\.method\s+(?:[\w-]+\s+)*([A-Za-z0-9_$<>]+\([^)]*\)\S+)")


def log(m):
    print(f"[verify_patch_refs] {m}", flush=True)


def find_java():
    jh = os.environ.get("JAVA_HOME")
    if jh:
        c = os.path.join(jh, "bin", "java.exe" if os.name == "nt" else "java")
        if os.path.isfile(c):
            return c
    return shutil.which("java") or "java"


def parse_definitions(smali_files):
    """smali ファイル群から {クラス名: set(メンバ)} を作る。"""
    defs = {}
    for path in smali_files:
        with open(path, "r", encoding="utf-8") as f:
            lines = f.readlines()
        cls = None
        members = set()
        for ln in lines:
            if cls is None:
                m = CLASS_RE.match(ln)
                if m:
                    cls = m.group(1)
                continue
            m = FIELD_RE.match(ln) or METHOD_RE.match(ln)
            if m:
                members.add(m.group(1))
        if cls:
            defs.setdefault(cls, set()).update(members)
    return defs


def collect_references(smali_files):
    """{(クラス, メンバ or None): set(参照元ファイル)} を作る。

    公式アプリ全体では smali が 2 万件を超えるので、パッチパッケージ名を
    含まないファイルはバイト列の部分一致で先に落としてから正規表現にかける。
    """
    needle = PKG.encode("utf-8")
    refs = {}
    for path in smali_files:
        with open(path, "rb") as f:
            raw = f.read()
        if needle not in raw:
            continue
        text = raw.decode("utf-8", "replace")
        for cls, member in REF_RE.findall(text):
            refs.setdefault((cls, member), set()).add(path)
        for cls in TYPE_RE.findall(text):
            refs.setdefault((cls, None), set()).add(path)
    return refs


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("--smali-dir", required=True, help="パッチ適用後の smali ツリー(work/dexpatch 等)")
    ap.add_argument("--module-dex", required=True, help="build_module.py が出力した dex")
    ap.add_argument("--baksmali", default=os.path.join(ROOT, "tools", "baksmali.jar"))
    ap.add_argument("--work", help="モジュール dex の逆アセンブル先 (既定: <smali-dir>/.verify)")
    args = ap.parse_args()

    if not os.path.isdir(args.smali_dir):
        ap.error(f"not a directory: {args.smali_dir}")
    if not os.path.isfile(args.module_dex):
        ap.error(f"not a file: {args.module_dex}")

    # モジュール dex を逆アセンブルして「classes5 側の定義」を得る
    work = args.work or os.path.join(args.smali_dir, ".verify")
    shutil.rmtree(work, ignore_errors=True)
    os.makedirs(work, exist_ok=True)
    p = subprocess.run(
        [find_java(), "-jar", args.baksmali, "d", args.module_dex, "-o", work],
        stdout=subprocess.DEVNULL,
    )
    if p.returncode != 0:
        raise SystemExit(f"baksmali failed on {args.module_dex}")

    module_files = sorted(glob.glob(os.path.join(work, "**", "*.smali"), recursive=True))
    # パッチクラスを参照しうるのは「アプリ自身のコード」だけ。公式 APK 全体では
    # smali が 2 万件を超え、その大半は androidx/kotlin/okhttp 等のライブラリで
    # パッチとは無関係なので、アプリのパッケージ配下だけを走査する
    # (trampoline の注入先も、パッチクラス本体もここに入る)。
    tree_files = [
        f
        for f in glob.glob(
            os.path.join(args.smali_dir, "*", APP_PKG.replace("/", os.sep), "**", "*.smali"),
            recursive=True,
        )
        if os.path.abspath(work) not in os.path.abspath(f)
    ]

    # 解決先として必要なのはパッチパッケージのクラスだけなので、
    # 定義の収集は patch/ 配下に絞る(公式クラスは参照先にならない)。
    patch_dir = os.sep + PKG.replace("/", os.sep) + os.sep
    module_defs = parse_definitions(module_files)
    tree_defs = parse_definitions([f for f in tree_files if patch_dir in f])

    defs = {}
    for src in (tree_defs, module_defs):
        for cls, members in src.items():
            defs.setdefault(cls, set()).update(members)

    where = {}
    for cls in module_defs:
        where[cls] = "module dex"
    for cls in tree_defs:
        where[cls] = "smali (classes4)" if cls not in module_defs else "両方(重複!)"

    # 参照は両方向とも集める:
    #   classes4 の smali -> classes5 の Java (trampoline / 未移行クラスからの呼び出し)
    #   classes5 の Java  -> classes4 の smali (未移行クラスを呼ぶ。module/stubs の
    #                       スタブ越しにコンパイルするので、ここがずれても javac も
    #                       d8 も気付けない)
    module_set = {os.path.abspath(f) for f in module_files}
    refs = collect_references(tree_files + module_files)

    def where_from(path):
        prefix = "module dex" if os.path.abspath(path) in module_set else "classes4"
        return f"{prefix}:{os.path.basename(path)}"

    log(
        f"smali ファイル: classes4 {len(tree_files)} + モジュール dex {len(module_files)}"
        f" / モジュール dex のクラス: {len(module_defs)}"
    )
    log(f"パッチクラスへの参照: {len(refs)} 件")

    missing = []
    # member は型だけの参照のとき None になるのでソートキーで潰す
    for (cls, member), sources in sorted(refs.items(), key=lambda kv: (kv[0][0], kv[0][1] or "")):
        if cls not in defs:
            missing.append((cls, member, sources, "クラスが見つからない"))
        elif member is not None and member not in defs[cls]:
            missing.append((cls, member, sources, "メンバが見つからない"))

    # 参照されているクラスごとの所在を出す(移行状況の可視化も兼ねる)
    referenced = sorted({cls for cls, _ in refs})
    for cls in referenced:
        short = cls.rsplit("/", 1)[-1]
        n = sum(1 for (c, m) in refs if c == cls and m is not None)
        log(f"  {short:24s} -> {where.get(cls, '???'):18s} (メンバ参照 {n} 件)")

    dup = [c for c in module_defs if c in tree_defs]
    if dup:
        for c in dup:
            print(
                f"FAIL 同じクラスが classes4 と モジュール dex の両方に定義されています: {c}",
                file=sys.stderr,
            )

    if missing:
        print("", file=sys.stderr)
        for cls, member, sources, why in missing:
            src = ", ".join(sorted(where_from(s) for s in sources))
            print(f"FAIL {why}: L{cls};->{member}  (参照元: {src})", file=sys.stderr)
        raise SystemExit(f"未解決のパッチ参照が {len(missing)} 件あります")

    if dup:
        raise SystemExit("クラス定義が重複しています")

    log("OK  パッチクラスへの参照はすべて解決できます")


if __name__ == "__main__":
    main()
