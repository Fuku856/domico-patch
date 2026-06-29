#!/usr/bin/env python3
"""
Domico パッチ ビルドオーケストレータ（ローカル/CI 共通・クロスプラットフォーム）

入力(XAPK/APKS/分割APKフォルダ) -> base 抽出 -> classes4.dex のみ差し替え(patch_apk.py)
-> 全 split を zipalign + 同一鍵で署名 -> 署名済み個別 .apk を出力。
(apktool 全体リビルドは一部端末で Invalid apk になるため不使用)

依存ツール（自動検出 + 環境変数/引数で上書き可）:
  - Java        : $JAVA_HOME/bin/java もしくは PATH の java
  - baksmali/smali : tools/baksmali.jar, tools/smali.jar (patch_apk.py が使用)
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


def log(msg):
    print(f"[build] {msg}", flush=True)


def run(cmd, **kw):
    log("$ " + " ".join(str(c) for c in cmd))
    p = subprocess.run(cmd, **kw)
    if p.returncode != 0:
        raise SystemExit(f"command failed ({p.returncode}): {cmd[0]}")
    return p


def compute_patch_version(channel, app_version):
    """Conventional Commits からパッチ版表示文字列を組み立てる(version.py に委譲)。

    例: release -> 'v0.3.0 / base 1.5.4' / dev -> 'v0.3.0-dev+g02322bf / base 1.5.4'
    git 履歴やタグが読めない等で version.py 解決に失敗した場合のみ
    'domico-patch dev' にフォールバックする。
    """
    try:
        sys.path.insert(0, os.path.join(ROOT, "scripts"))
        import version as _version  # scripts/version.py
        return _version.format_version(channel, app_version)
    except Exception as e:  # noqa: BLE001
        log(f"WARNING: version.py 解決に失敗 ({e}); フォールバック 'domico-patch dev' を使用")
        return "domico-patch dev"


def find_java():
    jh = os.environ.get("JAVA_HOME")
    if jh:
        cand = os.path.join(jh, "bin", "java.exe" if os.name == "nt" else "java")
        if os.path.isfile(cand):
            return cand
    return shutil.which("java") or "java"


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

    # base 判定はファイル名ではなく「classes*.dex を含む apk = base」で行う。
    # (config split は dex を持たない。apk-pure/端末pull/google-play で命名が
    #  異なっても確実に base を特定できる)
    def has_dex(apk):
        try:
            with zipfile.ZipFile(apk) as z:
                return any(re.fullmatch(r"classes\d*\.dex", n) for n in z.namelist())
        except zipfile.BadZipFile:
            return False

    base = [a for a in apks if has_dex(a)]
    configs = [a for a in apks if a not in base]
    if len(base) != 1:
        # 念のためのフォールバック: 最大サイズを base とする
        apks.sort(key=os.path.getsize, reverse=True)
        base = [apks[0]]
        configs = apks[1:]
    return base[0], configs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="XAPK/APKS/zip/フォルダ/base.apk")
    ap.add_argument("--out", default=os.path.join(ROOT, "work", "out"))
    ap.add_argument("--workdir", default=os.path.join(ROOT, "work"))
    ap.add_argument("--build-tools")
    ap.add_argument("--keystore", required=True)
    ap.add_argument("--ks-pass", required=True)
    ap.add_argument("--key-pass")
    ap.add_argument("--alias", required=True)
    ap.add_argument("--min-sdk", default="26")
    ap.add_argument("--app-version", help="versionName。.apks 同梱物の命名 + パッチ版表示の base に使用")
    ap.add_argument("--patch-version", help="設定画面に表示するパッチ版。未指定なら Conventional Commits から自動算出。")
    ap.add_argument("--channel", choices=["release", "dev"], default="release",
                    help="パッチ版の表示チャンネル。dev は -dev+g<sha> を付与。")
    ap.add_argument("--install", action="store_true", help="adb install-multiple まで実行")
    args = ap.parse_args()

    patch_version = args.patch_version or compute_patch_version(args.channel, args.app_version)

    java = find_java()
    bt = find_build_tools(args.build_tools)
    ext = ".bat" if os.name == "nt" else ""
    aexe = ".exe" if os.name == "nt" else ""
    zipalign = os.path.join(bt, "zipalign" + aexe)
    apksigner = os.path.join(bt, "apksigner" + ext)
    key_pass = args.key_pass or args.ks_pass

    log(f"java={java}")
    log(f"build-tools={bt}")

    # 前回の残骸を除去（base が複数混ざる "Split null defined multiple times" 防止）
    extracted = os.path.join(args.workdir, "extracted")
    if os.path.isdir(extracted):
        shutil.rmtree(extracted)
    os.makedirs(args.out, exist_ok=True)
    for old in glob.glob(os.path.join(args.out, "*.apk")) + \
               glob.glob(os.path.join(args.out, "*.idsig")) + \
               glob.glob(os.path.join(args.out, "*.apks")) + \
               glob.glob(os.path.join(args.out, "*.zip")):
        os.remove(old)

    base_apk, config_apks = collect_splits(args.input, args.workdir)
    log(f"base={os.path.basename(base_apk)} configs={[os.path.basename(c) for c in config_apks]}")

    # 外科的 dex パッチ: 元 base はバイト維持で classesN.dex だけ差し替える
    # (apktool 全体リビルドは一部端末で Invalid apk になるため不使用)
    base_unsigned = os.path.join(args.out, "base.unsigned.apk")
    log(f"patch-version={patch_version}")
    run([sys.executable, os.path.join(ROOT, "scripts", "patch_apk.py"),
         "--in", base_apk, "--out", base_unsigned,
         "--patch-version", patch_version])

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

    # 個別 .apk はローカルの adb install-multiple 用に work/out へ残す。
    log("signed splits: " + ", ".join(os.path.basename(p) for p in signed_paths))
    log("install (adb): adb install-multiple --no-incremental -r " +
        " ".join(os.path.basename(p) for p in signed_paths))

    # 配布物: 全スプリットをバイト維持のまま 1 つの .apks(zip) に同梱する。
    # resources.arsc を作り直さないので HyperOS 等の INSTALL_FAILED_USER_RESTRICTED を
    # 回避できる。インストールは SAI / Shizuku 等の分割対応インストーラでこの 1 ファイルを選ぶ。
    ver = args.app_version.strip() if args.app_version else None
    archive_name = f"domico-{ver}-patch.apks" if ver else "domico-patch.apks"
    archive = os.path.join(args.out, archive_name)
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_STORED) as z:
        for p in signed_paths:
            z.write(p, os.path.basename(p))
    log(f"bundled .apks: {archive_name} ({len(signed_paths)} splits)")

    if args.install:
        adb = shutil.which("adb") or "adb"
        run([adb, "install-multiple", "-r"] + signed_paths)
        log("installed via adb")


if __name__ == "__main__":
    main()
