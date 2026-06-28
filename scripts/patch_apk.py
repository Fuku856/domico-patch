#!/usr/bin/env python3
"""
外科的 dex パッチ: 元の base.apk を「ほぼバイト維持」のまま、AlertUtils を含む
単一の classesN.dex だけを baksmali->patch->smali で差し替える。

apktool の全体リビルド(resources.arsc / AndroidManifest の再エンコード)は
一部端末(例: Xiaomi/HyperOS)で INSTALL_FAILED_USER_RESTRICTED: Invalid apk を
誘発するため、リソースや他 dex は一切触らずに据え置く。

出力は「未署名」の base.apk。署名/zipalign は呼び出し側(build.py)が行う。

使い方:
  python scripts/patch_apk.py --in <orig_base.apk> --out <patched_base.apk> \
      [--baksmali tools/baksmali.jar] [--smali tools/smali.jar] [--work work/dexpatch] \
      [--api 26]
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import zipfile

for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8")
    except Exception:
        pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGET_DESCRIPTOR = b"Lvn/com/bravesoft/androidapp/utils/AlertUtils;"


def log(m):
    print(f"[patch_apk] {m}", flush=True)


def run(cmd):
    log("$ " + " ".join(str(c) for c in cmd))
    p = subprocess.run(cmd)
    if p.returncode != 0:
        raise SystemExit(f"command failed ({p.returncode}): {cmd[0]}")


def find_java():
    jh = os.environ.get("JAVA_HOME")
    if jh:
        c = os.path.join(jh, "bin", "java.exe" if os.name == "nt" else "java")
        if os.path.isfile(c):
            return c
    return shutil.which("java") or "java"


def find_target_dex(apk):
    """AlertUtils を含む classesN.dex の名前を返す。"""
    with zipfile.ZipFile(apk) as z:
        dexes = sorted(n for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n))
        for n in dexes:
            if TARGET_DESCRIPTOR in z.read(n):
                return n, dexes
    return None, dexes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="inp", required=True)
    ap.add_argument("--out")
    ap.add_argument("--baksmali", default=os.path.join(ROOT, "tools", "baksmali.jar"))
    ap.add_argument("--smali", default=os.path.join(ROOT, "tools", "smali.jar"))
    ap.add_argument("--work", default=os.path.join(ROOT, "work", "dexpatch"))
    ap.add_argument("--api", default="26")
    ap.add_argument(
        "--check",
        action="store_true",
        help="パッチ可否のみ dry-run 検査(dex差し替え/出力はしない)。"
        "公式更新でパッチ対象が変化したかを実ビルド前に検知する。",
    )
    ap.add_argument(
        "--patch-version",
        help="設定画面フッターに埋め込むパッチバージョン文字列。",
    )
    args = ap.parse_args()
    if not args.check and not args.out:
        ap.error("--out is required unless --check")

    java = find_java()
    # --check は baksmali での復号までしか使わないため smali.jar は不要
    needed_jars = (args.baksmali,) if args.check else (args.baksmali, args.smali)
    for jar in needed_jars:
        if not os.path.isfile(jar):
            raise SystemExit(f"not found: {jar} (run setup; tools/baksmali.jar, tools/smali.jar)")

    target_dex, dexes = find_target_dex(args.inp)
    if not target_dex:
        # AlertUtils がどの dex にも無い = クラス削除/改名/移動。パッチ対象が変化。
        raise SystemExit(
            "AlertUtils を含む dex が見つかりません。APK 構造が変わった可能性があります。"
        )
    log(f"target dex = {target_dex} (of {dexes})")

    work = args.work
    if os.path.isdir(work):
        shutil.rmtree(work)
    os.makedirs(work)
    dex_in = os.path.join(work, target_dex)
    with zipfile.ZipFile(args.inp) as z:
        with open(dex_in, "wb") as f:
            f.write(z.read(target_dex))

    smali_dir = os.path.join(work, "smali")
    run([java, "-jar", args.baksmali, "d", dex_in, "-o", smali_dir])

    patch_smali = os.path.join(ROOT, "scripts", "patch_smali.py")

    # --check: dry-run でパッチ可否だけ判定し、dex 差し替えはしない。
    # パッチ 3-6 は AlertUtils とは別 dex 内のクラスを対象にするため、
    # --check モードでは全 dex を展開して patch_smali が全パッチを検査できるようにする。
    if args.check:
        with zipfile.ZipFile(args.inp) as z:
            for dex_name in dexes:
                if dex_name == target_dex:
                    continue  # 既に smali_dir へ展開済み
                tmp = os.path.join(work, dex_name)
                with open(tmp, "wb") as f:
                    f.write(z.read(dex_name))
                n = re.match(r"classes(\d*)\.dex", dex_name).group(1)
                sdir = os.path.join(work, f"smali_classes{n}" if n else "smali")
                run([java, "-jar", args.baksmali, "d", tmp, "-o", sdir])
        p = subprocess.run([sys.executable, patch_smali, "--check", work])
        if p.returncode != 0:
            raise SystemExit(
                "patch dry-run FAILED: パッチ対象コードが変化しています。"
                "上記の FAIL 行で該当クラスを確認し、smali を手動修正してください。"
            )
        log("patch dry-run OK: 現行パッチは適用可能です(コード変化なし)")
        return

    # 既存の patch_smali.py を流用(冪等・アンカー基準)
    patch_cmd = [sys.executable, patch_smali, work]
    if args.patch_version:
        patch_cmd[2:2] = ["--patch-version", args.patch_version]
    run(patch_cmd)

    dex_out = os.path.join(work, "patched.dex")
    run([java, "-jar", args.smali, "a", "-a", args.api, "-o", dex_out, smali_dir])

    # 元 APK をコピーし、target dex だけ差し替え(他エントリは圧縮種別を維持)
    new_dex_bytes = open(dex_out, "rb").read()
    if os.path.exists(args.out):
        os.remove(args.out)
    with zipfile.ZipFile(args.inp) as zin, zipfile.ZipFile(args.out, "w") as zout:
        for item in zin.infolist():
            data = new_dex_bytes if item.filename == target_dex else zin.read(item.filename)
            zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
            zi.compress_type = item.compress_type
            zi.external_attr = item.external_attr
            zi.internal_attr = item.internal_attr
            zi.create_system = item.create_system
            zout.writestr(zi, data)
    log(f"wrote {args.out} (replaced {target_dex}, others byte-preserved by type)")


if __name__ == "__main__":
    main()
