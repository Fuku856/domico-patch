#!/usr/bin/env python3
"""
domico-patch モジュール(パッチ本体)を単一 dex にビルドする。

パッチ本体は公式アプリの dex を触らずに済むよう、通常の Java として書かれ
(module/src)、javac -> d8 で独立した dex になる。生成された dex は
patch_apk.py が公式 APK へ **追加** する(classes5.dex 等)。

コンパイル用スタブ(module/stubs)は classpath にだけ載せ、d8 には渡さない。
よって dex には入らず、実行時は公式 APK 内の本物のクラスが解決される。

依存ツール(自動検出 + 環境変数/引数で上書き可):
  - javac       : $JAVA_HOME/bin/javac もしくは PATH
  - android.jar : --android-jar / $ANDROID_SDK_ROOT/platforms/android-*/android.jar
  - d8          : --build-tools / $ANDROID_BUILD_TOOLS / $ANDROID_SDK_ROOT の最新 build-tools

使い方:
  python scripts/build_module.py --out work/out/patch.dex [--patch-version "v0.2.0 / base v1.5.4"]
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
MODULE_DIR = os.path.join(ROOT, "module")
SRC_DIR = os.path.join(MODULE_DIR, "src")
STUB_DIR = os.path.join(MODULE_DIR, "stubs")

# パッチ本体が想定する最低 API レベル。公式 APK の minSdkVersion と一致させる。
MIN_API = 26
# javac の --release。Android の java.* 互換性を踏まえて 11 に固定する。
JAVA_RELEASE = "11"
# 版文字列を解決できなかったときの既定値(従来の smali 既定値と同じ)。
DEFAULT_VERSION = "domico-patch dev"

PKG_REL = os.path.join("vn", "com", "bravesoft", "androidapp", "patch")


def log(msg):
    print(f"[build_module] {msg}", flush=True)


def run(cmd):
    log("$ " + " ".join(str(c) for c in cmd))
    p = subprocess.run(cmd)
    if p.returncode != 0:
        raise SystemExit(f"command failed ({p.returncode}): {os.path.basename(str(cmd[0]))}")


def java_string(s):
    """文字列を Java の文字列リテラル(両端の " 含む)に安全に変換する。"""
    out = (
        s.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )
    return f'"{out}"'


# ---- toolchain discovery ---------------------------------------------------

def find_javac():
    jh = os.environ.get("JAVA_HOME")
    if jh:
        cand = os.path.join(jh, "bin", "javac.exe" if os.name == "nt" else "javac")
        if os.path.isfile(cand):
            return cand
    found = shutil.which("javac")
    if not found:
        raise SystemExit("javac not found ($JAVA_HOME/bin/javac も PATH にもない)")
    return found


def _sdk_root():
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk and os.name == "nt":
        cand = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk")
        if os.path.isdir(cand):
            sdk = cand
    return sdk


def _api_of(name):
    m = re.match(r"^android-(\d+)$", name)
    return int(m.group(1)) if m else -1


def find_android_jar(arg):
    cands = []
    if arg:
        cands.append(arg)
    if os.environ.get("ANDROID_JAR"):
        cands.append(os.environ["ANDROID_JAR"])
    sdk = _sdk_root()
    if sdk:
        plat = os.path.join(sdk, "platforms")
        if os.path.isdir(plat):
            # android-35 -> 35 でソートし、最も新しい platform を選ぶ。
            for name in sorted(os.listdir(plat), key=_api_of, reverse=True):
                if _api_of(name) >= MIN_API:
                    cands.append(os.path.join(plat, name, "android.jar"))
    for c in cands:
        if c and os.path.isfile(c):
            return c
    raise SystemExit(
        "android.jar not found. --android-jar か $ANDROID_SDK_ROOT/platforms/android-<api>/ "
        "を用意してください (CI では sdkmanager platforms;android-35)"
    )


def find_d8(arg):
    ext = ".bat" if os.name == "nt" else ""
    cands = []
    if arg:
        cands.append(os.path.join(arg, "d8" + ext))
    if os.environ.get("ANDROID_BUILD_TOOLS"):
        cands.append(os.path.join(os.environ["ANDROID_BUILD_TOOLS"], "d8" + ext))
    sdk = _sdk_root()
    if sdk:
        bt = os.path.join(sdk, "build-tools")
        if os.path.isdir(bt):
            for v in sorted(os.listdir(bt), reverse=True):
                cands.append(os.path.join(bt, v, "d8" + ext))
    for c in cands:
        if c and os.path.isfile(c):
            return c
    raise SystemExit("d8 not found (Android build-tools を用意してください)")


# ---- build steps -----------------------------------------------------------

def gen_build_info(gen_dir, patch_version):
    """パッチ版文字列を持つ PatchBuildInfo.java を生成する。

    従来は patch_smali.py が PatchInfo.smali の .field 行を書き換えていたが、
    生成ソースに切り出して「ビルド時に変わる値」を1箇所へ閉じ込める。
    """
    out_dir = os.path.join(gen_dir, PKG_REL)
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "PatchBuildInfo.java")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(
            "package vn.com.bravesoft.androidapp.patch;\n"
            "\n"
            "/** ビルド時生成。手で編集しない (scripts/build_module.py が出力する)。 */\n"
            "public final class PatchBuildInfo {\n"
            "\n"
            f"    public static final String VERSION = {java_string(patch_version)};\n"
            "\n"
            "    private PatchBuildInfo() {\n"
            "    }\n"
            "}\n"
        )
    return path


def java_sources(*dirs):
    out = []
    for d in dirs:
        if d and os.path.isdir(d):
            out.extend(sorted(glob.glob(os.path.join(d, "**", "*.java"), recursive=True)))
    return out


def class_files(d):
    return sorted(glob.glob(os.path.join(d, "**", "*.class"), recursive=True))


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("--out", required=True, help="出力する dex ファイルのパス")
    ap.add_argument(
        "--patch-version",
        default=DEFAULT_VERSION,
        help=f"PatchBuildInfo.VERSION に埋め込む版文字列 (既定: {DEFAULT_VERSION})",
    )
    ap.add_argument("--work", help="中間生成物の置き場 (既定: work/module)")
    ap.add_argument("--android-jar", help="android.jar のパス")
    ap.add_argument("--build-tools", help="d8 を含む build-tools ディレクトリ")
    args = ap.parse_args()

    work = args.work or os.path.join(ROOT, "work", "module")
    gen_dir = os.path.join(work, "gen")
    stub_classes = os.path.join(work, "stub-classes")
    out_classes = os.path.join(work, "classes")
    dex_dir = os.path.join(work, "dex")
    for d in (gen_dir, stub_classes, out_classes, dex_dir):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d, exist_ok=True)

    javac = find_javac()
    android_jar = find_android_jar(args.android_jar)
    d8 = find_d8(args.build_tools)
    log(f"javac       = {javac}")
    log(f"android.jar = {android_jar}")
    log(f"d8          = {d8}")

    gen_build_info(gen_dir, args.patch_version)
    log(f"patch version = {args.patch_version}")

    # 1) スタブをコンパイル (dex には入れない。classpath 専用)
    stub_srcs = java_sources(STUB_DIR)
    cp = [android_jar]
    if stub_srcs:
        run([
            javac, "--release", JAVA_RELEASE, "-encoding", "UTF-8",
            "-nowarn", "-Xlint:-options",
            "-classpath", android_jar,
            "-d", stub_classes, *stub_srcs,
        ])
        cp.append(stub_classes)
        log(f"stubs: {len(stub_srcs)} source(s) -> {len(class_files(stub_classes))} class(es)")
    else:
        log("stubs: なし (android.jar のみで足りるクラスだけ)")

    # 2) パッチ本体をコンパイル
    srcs = java_sources(SRC_DIR, gen_dir)
    if not srcs:
        raise SystemExit(f"no java sources under {SRC_DIR}")
    run([
        javac, "--release", JAVA_RELEASE, "-encoding", "UTF-8",
        "-nowarn", "-Xlint:-options",
        "-classpath", os.pathsep.join(cp),
        "-d", out_classes, *srcs,
    ])
    classes = class_files(out_classes)
    log(f"module: {len(srcs)} source(s) -> {len(classes)} class(es)")

    # 3) dex へ変換。スタブは --classpath 側なので dex には含まれない。
    d8_cmd = [d8, "--release", "--min-api", str(MIN_API), "--lib", android_jar]
    if stub_srcs:
        d8_cmd += ["--classpath", stub_classes]
    d8_cmd += ["--output", dex_dir, *classes]
    run(d8_cmd)

    produced = sorted(glob.glob(os.path.join(dex_dir, "*.dex")))
    if len(produced) != 1:
        raise SystemExit(
            f"d8 が dex を {len(produced)} 個生成しました "
            f"({[os.path.basename(p) for p in produced]})。"
            " patch_apk.py は現状 1 個だけを追加する前提です。"
        )

    os.makedirs(os.path.dirname(os.path.abspath(args.out)) or ".", exist_ok=True)
    shutil.copyfile(produced[0], args.out)
    size = os.path.getsize(args.out)
    log(f"OK  {args.out}  ({size:,} bytes, {len(classes)} class(es))")


if __name__ == "__main__":
    main()
