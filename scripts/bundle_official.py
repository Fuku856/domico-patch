#!/usr/bin/env python3
"""
公式ミラー用バンドラ: Google Play 等から取得した「未加工の公式スプリット」を
そのまま 1 つの .apks(zip) に同梱する。再署名/zipalign/パッチは一切しない
(公式署名をバイト維持)。インストールは SAI / Shizuku 等の分割対応インストーラで行う。

build.py が patched バンドルを ZIP_STORED で作るのと同じ方式だが、こちらは
署名・パッチを行わない単一責務スクリプト。

使い方:
  python scripts/bundle_official.py --input download \
      --out work/official/domico-1.5.4-official.apks --app-version 1.5.4
"""
import argparse
import glob
import os
import re
import sys
import zipfile

for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8")
    except Exception:
        pass


def log(m):
    print(f"[bundle_official] {m}", flush=True)


def has_dex(apk):
    """classes*.dex を含む = base APK。config split は dex を持たない。"""
    try:
        with zipfile.ZipFile(apk) as z:
            return any(re.fullmatch(r"classes\d*\.dex", n) for n in z.namelist())
    except zipfile.BadZipFile:
        return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="公式スプリットが入ったフォルダ")
    ap.add_argument("--out", required=True, help="出力する .apks のパス")
    ap.add_argument("--app-version", help="versionName(ログ表示用のみ)")
    args = ap.parse_args()

    apks = sorted(glob.glob(os.path.join(args.input, "*.apk")))
    if not apks:
        raise SystemExit(f"no .apk found in input: {args.input}")
    # base(dex を含む apk)は分割セットにつき 1 つのはず。0 個や複数個は不正な
    # 取得の可能性があるため警告する(build.py の base 判定と対称)。
    bases = [a for a in apks if has_dex(a)]
    if len(bases) == 0:
        log(f"::warning::base(classes*.dex を含む apk) が見つかりません: {args.input}")
    elif len(bases) > 1:
        names = ", ".join(os.path.basename(b) for b in bases)
        log(f"::warning::base(classes*.dex を含む apk) が複数あります: {names}"
            " — 不正な分割の可能性。全て同梱しますが取得元を確認してください。")

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    # 公式署名をバイト維持するため再圧縮しない(ZIP_STORED)。
    # split 名はインストーラが manifest で判定するためエントリ名は basename のままでよい。
    with zipfile.ZipFile(args.out, "w", zipfile.ZIP_STORED) as z:
        for p in apks:
            z.write(p, os.path.basename(p))
    ver = (args.app_version or "").strip() or "?"
    log(f"bundled official .apks: {os.path.basename(args.out)} "
        f"(version={ver}, {len(apks)} splits)")


if __name__ == "__main__":
    main()
