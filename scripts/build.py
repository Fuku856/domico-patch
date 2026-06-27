#!/usr/bin/env python3
"""
Domico パッチ ビルドオーケストレータ（ローカル/CI 共通・クロスプラットフォーム）

入力(XAPK/APKS/分割APKフォルダ) -> base 抽出 -> apktool decode ->
B案 smali パッチ -> apktool build -> 全 split を zipalign + 同一鍵で署名 ->
署名済み分割APK群 と SAI 用 .apks(zip) を出力。

依存ツール（自動検出 + 環境変数/引数で上書き可）:
  - Java        : $JAVA_HOME/bin/java もしくは PATH の java
  - apktool.jar : --apktool（既定 tools/apktool.jar、無ければ $APKTOOL_JAR）
  - zipalign/apksigner : Android SDK build-tools
      --build-tools <dir> / $ANDROID_BUILD_TOOLS / $ANDROID_SDK_ROOT(最新build-tools自動選択)

使用例(ローカル/Windows):
  python scripts/build.py --input Domico_1.5.4.xapk \
      --keystore work/domico.keystore --ks-pass domico123 --key-pass domico123 --alias domico

使用例(CI/Linux): 同上（パスは環境変数で解決）
"""
import argparse
import glob
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
CONFIG_RE = re.compile(r"(^|[/\\])(config|split)[._]", re.IGNORECASE)


def log(msg):
    print(f"[build] {msg}", flush=True)


def run(cmd, **kw):
    log("$ " + " ".join(str(c) for c in cmd))
    p = subprocess.run(cmd, **kw)
    if p.returncode != 0:
        raise SystemExit(f"command failed ({p.returncode}): {cmd[0]}")
    return p


def find_java():
    jh = os.environ.get("JAVA_HOME")
    if jh:
        cand = os.path.join(jh, "bin", "java.exe" if os.name == "nt" else "java")
        if os.path.isfile(cand):
            return cand
    return shutil.which("java") or "java"


def find_apktool(arg):
    for c in (arg, os.environ.get("APKTOOL_JAR"), os.path.join(ROOT, "tools", "apktool.jar")):
        if c and os.path.isfile(c):
            return c
    raise SystemExit("apktool.jar not found (use --apktool or place tools/apktool.jar)")


def find_build_tools(arg):
    cands = []
    if arg:
        cands.append(arg)
    if os.environ.get("ANDROID_BUILD_TOOLS"):
        cands.append(os.environ["ANDROID_BUILD_TOOLS"])
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk and os.name == "nt":
        sdk = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk")
    if sdk and os.path.isdir(os.path.join(sdk, "build-tools")):
        vers = sorted(os.listdir(os.path.join(sdk, "build-tools")))
        if vers:
            cands.append(os.path.join(sdk, "build-tools", vers[-1]))
    ext = ".bat" if os.name == "nt" else ""
    aexe = ".exe" if os.name == "nt" else ""
    for c in cands:
        if c and os.path.isfile(os.path.join(c, "apksigner" + ext)) and os.path.isfile(
            os.path.join(c, "zipalign" + aexe)
        ):
            return c
    raise SystemExit("Android build-tools (zipalign/apksigner) not found")


def collect_splits(input_path, workdir):
    """入力(zip系ファイル / 単一apk / フォルダ) から base と config 群を集める。"""
    extracted = os.path.join(workdir, "extracted")
    os.makedirs(extracted, exist_ok=True)
    if os.path.isdir(input_path):
        for f in glob.glob(os.path.join(input_path, "*.apk")):
            shutil.copy2(f, extracted)
    elif zipfile.is_zipfile(input_path):
        with zipfile.ZipFile(input_path) as z:
            for n in z.namelist():
                if n.lower().endswith(".apk"):
                    z.extract(n, extracted)
        # フラットに集める
        for f in glob.glob(os.path.join(extracted, "**", "*.apk"), recursive=True):
            if os.path.dirname(f) != extracted:
                shutil.move(f, os.path.join(extracted, os.path.basename(f)))
    elif input_path.lower().endswith(".apk"):
        shutil.copy2(input_path, extracted)
    else:
        raise SystemExit(f"unsupported input: {input_path}")

    apks = glob.glob(os.path.join(extracted, "*.apk"))
    if not apks:
        raise SystemExit("no .apk found in input")
    base = [a for a in apks if not CONFIG_RE.search(os.path.basename(a))]
    configs = [a for a in apks if CONFIG_RE.search(os.path.basename(a))]
    if len(base) != 1:
        # base 判定がつかない場合は最大サイズを base とする
        apks.sort(key=os.path.getsize, reverse=True)
        base = [apks[0]]
        configs = apks[1:]
    return base[0], configs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="XAPK/APKS/zip/フォルダ/base.apk")
    ap.add_argument("--out", default=os.path.join(ROOT, "work", "out"))
    ap.add_argument("--workdir", default=os.path.join(ROOT, "work"))
    ap.add_argument("--apktool")
    ap.add_argument("--build-tools")
    ap.add_argument("--keystore", required=True)
    ap.add_argument("--ks-pass", required=True)
    ap.add_argument("--key-pass")
    ap.add_argument("--alias", required=True)
    ap.add_argument("--min-sdk", default="26")
    ap.add_argument("--install", action="store_true", help="adb install-multiple まで実行")
    args = ap.parse_args()

    java = find_java()
    apktool = find_apktool(args.apktool)
    bt = find_build_tools(args.build_tools)
    ext = ".bat" if os.name == "nt" else ""
    aexe = ".exe" if os.name == "nt" else ""
    zipalign = os.path.join(bt, "zipalign" + aexe)
    apksigner = os.path.join(bt, "apksigner" + ext)
    key_pass = args.key_pass or args.ks_pass

    log(f"java={java}")
    log(f"apktool={apktool}")
    log(f"build-tools={bt}")

    decoded = os.path.join(args.workdir, "base")
    os.makedirs(args.out, exist_ok=True)
    if os.path.isdir(decoded):
        shutil.rmtree(decoded)

    base_apk, config_apks = collect_splits(args.input, args.workdir)
    log(f"base={os.path.basename(base_apk)} configs={[os.path.basename(c) for c in config_apks]}")

    # decode -> patch -> build
    run([java, "-jar", apktool, "d", "-f", "-o", decoded, base_apk])
    run([sys.executable, os.path.join(ROOT, "scripts", "patch_smali.py"), decoded])
    base_unsigned = os.path.join(args.out, "base.unsigned.apk")
    run([java, "-jar", apktool, "b", "-o", base_unsigned, decoded])

    # 署名対象: patch済 base + 元 config 群（全て同一鍵）
    signed_paths = []
    todo = [("base.apk", base_unsigned)] + [
        (os.path.basename(c), c) for c in config_apks
    ]
    for name, src in todo:
        aligned = os.path.join(args.out, "aligned_" + name)
        signed = os.path.join(args.out, name)
        run([zipalign, "-f", "-p", "4", src, aligned])
        run([
            apksigner, "sign", "--ks", args.keystore,
            "--ks-pass", "pass:" + args.ks_pass,
            "--key-pass", "pass:" + key_pass,
            "--ks-key-alias", args.alias,
            "--min-sdk-version", args.min_sdk,
            "--out", signed, aligned,
        ])
        os.remove(aligned)
        signed_paths.append(signed)
    if os.path.exists(base_unsigned):
        os.remove(base_unsigned)

    run([apksigner, "verify", "--min-sdk-version", args.min_sdk, signed_paths[0]])

    # SAI 用 .apks(zip)
    apks_zip = os.path.join(args.out, "Domico-patched.apks")
    with zipfile.ZipFile(apks_zip, "w", zipfile.ZIP_STORED) as z:
        for p in signed_paths:
            z.write(p, os.path.basename(p))
    log("signed splits: " + ", ".join(os.path.basename(p) for p in signed_paths))
    log("bundle: " + apks_zip)

    if args.install:
        adb = shutil.which("adb") or "adb"
        run([adb, "install-multiple", "-r"] + signed_paths)
        log("installed via adb")


if __name__ == "__main__":
    main()
