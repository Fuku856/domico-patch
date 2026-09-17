#!/usr/bin/env python3
"""
外科的 dex パッチ: 元の base.apk を「ほぼバイト維持」のまま、

  1. パッチ対象クラスを含む classesN.dex だけを baksmali->patch->smali で差し替え、
  2. パッチ本体を載せた新しい classesN.dex を **追加** する。

パッチ本体(module/src の Java)は公式 dex には混ぜない。公式 dex 側に入るのは
パッチクラスを呼び出すだけの短い trampoline であり、公式コードの改変量を最小に
保つ。ART は classes.dex から連番で dex を読むため、追加 dex は manifest を
触らずに読み込まれる。

apktool の全体リビルド(resources.arsc / AndroidManifest の再エンコード)は
一部端末(例: Xiaomi/HyperOS)で INSTALL_FAILED_USER_RESTRICTED: Invalid apk を
誘発するため、リソースや他 dex は一切触らずに据え置く。

出力は「未署名」の base.apk。署名/zipalign は呼び出し側(build.py)が行う。

使い方:
  python scripts/patch_apk.py --in <orig_base.apk> --out <patched_base.apk> \
      [--baksmali tools/baksmali.jar] [--smali tools/smali.jar] [--work work/dexpatch] \
      [--api 26] [--patch-version <str>] [--build-tools <dir>] [--android-jar <path>]
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


def next_dex_name(dexes):
    """パッチモジュールを載せる新しい classesN.dex の名前を返す。

    ART は classes.dex, classes2.dex ... を **連番で** 読み、欠番が出た時点で
    打ち切る。したがって追加先は「最大 + 1」でなければならない(空き番号では
    ないことに注意)。公式が将来 classes5.dex を持ち込んでも衝突しないよう、
    番号はハードコードせずここで決める。
    """
    idx = []
    for n in dexes:
        m = re.fullmatch(r"classes(\d*)\.dex", n)
        idx.append(int(m.group(1)) if m.group(1) else 1)
    idx.sort()
    if idx != list(range(1, len(idx) + 1)):
        raise SystemExit(
            f"dex の連番が期待と異なります: {dexes}。"
            "ART は欠番で読み込みを打ち切るため、追加先を決定できません。"
        )
    return f"classes{len(idx) + 1}.dex"


def build_module_dex(out_path, patch_version, build_tools, android_jar, work):
    """パッチ本体(module/src の Java)を単一 dex にビルドする。"""
    cmd = [
        sys.executable,
        os.path.join(ROOT, "scripts", "build_module.py"),
        "--out", out_path,
        "--work", work,
    ]
    if patch_version:
        cmd += ["--patch-version", patch_version]
    if build_tools:
        cmd += ["--build-tools", build_tools]
    if android_jar:
        cmd += ["--android-jar", android_jar]
    run(cmd)
    return open(out_path, "rb").read()


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
    ap.add_argument(
        "--build-tools",
        help="d8 を含む Android build-tools ディレクトリ(build_module.py へ委譲)。",
    )
    ap.add_argument(
        "--android-jar",
        help="コンパイルに使う android.jar のパス(build_module.py へ委譲)。",
    )
    args = ap.parse_args()
    if not args.check and not args.out:
        ap.error("--out is required unless --check")

    java = find_java()
    # --check もアセンブルまで行い、参照数上限や api レベルの不整合を
    # 実ビルド前に検出する。よって smali.jar は常に必要。
    for jar in (args.baksmali, args.smali):
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

    patch_smali = os.path.join(ROOT, "scripts", "patch_smali.py")

    # --check: dry-run でパッチ可否だけ判定し、dex 差し替えはしない。
    # パッチ 3-6 は AlertUtils とは別 dex 内のクラスを対象にするため、
    # 全 dex を展開して patch_smali が全パッチを検査・適用できるようにする。
    with zipfile.ZipFile(args.inp) as z:
        for dex_name in dexes:
            tmp = os.path.join(work, dex_name)
            with open(tmp, "wb") as f:
                f.write(z.read(dex_name))
            n = re.match(r"classes(\d*)\.dex", dex_name).group(1)
            sdir = os.path.join(work, f"smali_classes{n}" if n else "smali")
            run([java, "-jar", args.baksmali, "d", tmp, "-o", sdir])

    if args.check:
        # 1段目: アンカー検査。公式更新でパッチ対象コードが変化していれば
        # ここで FAIL 行付きで落ちる(どのパッチが壊れたかが分かる)。
        p = subprocess.run([sys.executable, patch_smali, "--check", work])
        if p.returncode != 0:
            raise SystemExit(
                "patch dry-run FAILED: パッチ対象コードが変化しています。"
                "上記の FAIL 行で該当クラスを確認し、smali を手動修正してください。"
            )
        log("patch dry-run: アンカー検査 OK。続けて実アセンブルを検証します")

    # 既存の patch_smali.py を流用(冪等・アンカー基準)。
    # 実際に変更した shard 名を changed_out に書き出させ、再アセンブル対象を絞る。
    changed_out = os.path.join(work, ".changed_shards")
    run([sys.executable, patch_smali, work, "--changed-out", changed_out])

    # patch_smali が実際に書き換えた shard だけを再アセンブルする。未変更 dex は
    # 元バイトのまま維持: baksmali->smali の往復はバイト同一を保証せず、無関係な
    # dex を作り直すと一部端末で Invalid apk を誘発しうるため(冒頭の設計方針)。
    changed_shards = set()
    if os.path.isfile(changed_out):
        with open(changed_out, encoding="utf-8") as f:
            changed_shards = {ln.strip() for ln in f if ln.strip()}
    log(f"changed shards = {sorted(changed_shards) or '(none)'}")

    new_dex_bytes_map = {}
    for dex_name in dexes:
        n = re.match(r"classes(\d*)\.dex", dex_name).group(1)
        shard = f"smali_classes{n}" if n else "smali"
        if shard not in changed_shards:
            continue  # 未変更 dex はバイト維持
        sdir = os.path.join(work, shard)
        dex_out = os.path.join(work, f"patched_{dex_name}")
        run([java, "-jar", args.smali, "a", "-a", args.api, "-o", dex_out, sdir])
        new_dex_bytes_map[dex_name] = open(dex_out, "rb").read()

    # パッチ本体は公式 dex に混ぜず、独立した dex として **追加** する。
    module_dex_name = next_dex_name(dexes)
    module_bytes = build_module_dex(
        os.path.join(work, module_dex_name),
        args.patch_version,
        args.build_tools,
        args.android_jar,
        os.path.join(work, "module"),
    )
    log(f"module dex = {module_dex_name} ({len(module_bytes):,} bytes)")

    if args.check:
        log(
            "patch dry-run OK: アセンブル・モジュールビルドとも成功 "
            f"(再アセンブル {sorted(new_dex_bytes_map) or '(none)'}, 追加 {module_dex_name})"
        )
        return

    # 元 APK をコピーし、変更 dex だけ差し替え(他エントリは圧縮種別を維持)。
    # パッチ dex は最後の classesN.dex の直後に、公式 dex と同じ格納方式で挿む。
    if os.path.exists(args.out):
        os.remove(args.out)
    last_dex = dexes[-1]
    with zipfile.ZipFile(args.inp) as zin, zipfile.ZipFile(args.out, "w") as zout:
        for item in zin.infolist():
            if item.filename in new_dex_bytes_map:
                data = new_dex_bytes_map[item.filename]
            else:
                data = zin.read(item.filename)
            zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
            zi.compress_type = item.compress_type
            zi.external_attr = item.external_attr
            zi.internal_attr = item.internal_attr
            zi.create_system = item.create_system
            zout.writestr(zi, data)
            if item.filename == last_dex:
                mi = zipfile.ZipInfo(module_dex_name, date_time=item.date_time)
                mi.compress_type = item.compress_type
                mi.external_attr = item.external_attr
                mi.internal_attr = item.internal_attr
                mi.create_system = item.create_system
                zout.writestr(mi, module_bytes)
    log(f"wrote {args.out} (replaced {len(new_dex_bytes_map)} dex(es): "
        f"{sorted(new_dex_bytes_map) or '(none)'}; added {module_dex_name}; "
        "others byte-preserved by type)")

if __name__ == "__main__":
    main()
