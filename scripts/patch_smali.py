#!/usr/bin/env python3
"""
Domico B案パッチ: 起動時ログイン通知バーのタッチ遮断を解除する。

対象: vn/com/bravesoft/androidapp/utils/AlertUtils.smali の displayToastContract(...)
内容: 下部に表示する android.app.Dialog のウィンドウへ
      FLAG_NOT_FOCUSABLE(0x8) | FLAG_NOT_TOUCHABLE(0x10) = 0x18 を addFlags し、
      通知バー表示中も下のUIを操作できる「クリックスルー」オーバーレイにする。
      （通知の表示自体は残す = B案）

設計方針（公式更新で再適用しやすくする）:
  - 行番号ではなく「クラス名 + メソッド名 + 命令パターン」をアンカーにする。
  - displayToastContract 内の `Landroid/view/Window;->clearFlags(I)V` 呼び出しから
    ウィンドウ用レジスタと一時レジスタを動的に取得して挿入するため、
    レジスタ名/個数の変化に強い。
  - 冪等: 既にパッチ済み(マーカー有)ならスキップ。
  - アンカーが見つからなければ非0で終了し、CIで版変更を検知できるようにする。

使い方:
  python scripts/patch_smali.py <decoded_base_dir>
    例) python scripts/patch_smali.py work/base
"""

import os
import re
import sys

# Windows コンソール(cp932)でも安全に出力できるよう UTF-8 に固定
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8")
    except Exception:
        pass

MARKER = "# domico-patch: make login toast dialog click-through (NOT_FOCUSABLE|NOT_TOUCHABLE)"
TARGET_CLASS_REL = os.path.join(
    "vn", "com", "bravesoft", "androidapp", "utils", "AlertUtils.smali"
)
METHOD_ANCHOR = ".method public final displayToastContract("
CLEARFLAGS_RE = re.compile(
    r"^\s*invoke-virtual\s*\{(v\d+),\s*(v\d+)\}\,\s*Landroid/view/Window;->clearFlags\(I\)V\s*$"
)


def find_target(base_dir):
    """smali, smali_classes2.. を横断して AlertUtils.smali を探す。"""
    for entry in sorted(os.listdir(base_dir)):
        if entry == "smali" or entry.startswith("smali_classes"):
            cand = os.path.join(base_dir, entry, TARGET_CLASS_REL)
            if os.path.isfile(cand):
                return cand
    return None


def patch_file(path, check_only=False):
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    # メソッド範囲を特定
    start = None
    for i, ln in enumerate(lines):
        if ln.startswith(METHOD_ANCHOR):
            start = i
            break
    if start is None:
        return False, "method displayToastContract(...) not found"

    end = None
    for i in range(start + 1, len(lines)):
        if lines[i].startswith(".end method"):
            end = i
            break
    if end is None:
        return False, "end of displayToastContract not found"

    method = lines[start : end + 1]

    # 冪等: 既にマーカー or addFlags(0x18) があればスキップ
    joined = "".join(method)
    if MARKER in joined:
        return True, "already patched (marker present) - skipped"

    # clearFlags 呼び出しをアンカーに、window/scratch レジスタを取得
    anchor_idx = None
    win_reg = None
    scratch_reg = None
    for j, ln in enumerate(method):
        m = CLEARFLAGS_RE.match(ln)
        if m:
            anchor_idx = j
            win_reg, scratch_reg = m.group(1), m.group(2)
            break
    if anchor_idx is None:
        return False, (
            "anchor `Landroid/view/Window;->clearFlags(I)V` not found inside "
            "displayToastContract — APK layout likely changed; refusing to patch"
        )

    # dry-run: アンカーが見つかった = パッチ適用可能。ファイルは書き換えない。
    if check_only:
        return True, (
            f"patch would apply (window={win_reg}, scratch={scratch_reg}) "
            "[dry-run, not written]"
        )

    indent = re.match(r"^(\s*)", method[anchor_idx]).group(1)
    # clearFlags の直後に挿入。clearFlags の引数レジスタ(0x2を保持)は直後で不要になるため
    # 一時レジスタとして再利用する。
    injection = [
        f"{indent}{MARKER}\n",
        f"{indent}const/16 {scratch_reg}, 0x18\n",
        f"{indent}invoke-virtual {{{win_reg}, {scratch_reg}}}, "
        f"Landroid/view/Window;->addFlags(I)V\n",
    ]
    method[anchor_idx + 1 : anchor_idx + 1] = injection

    lines[start : end + 1] = method
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.writelines(lines)
    return True, f"patched (window={win_reg}, scratch={scratch_reg})"


def main():
    # 使い方: patch_smali.py [--check] <decoded_base_dir>
    # --check: パッチ可否のみ検査して書き換えない(公式更新で対象が変化したかをCIで検知)
    argv = sys.argv[1:]
    check_only = False
    if "--check" in argv:
        check_only = True
        argv.remove("--check")
    if len(argv) != 1:
        print(__doc__)
        sys.exit(2)
    base_dir = argv[0]
    mode = "CHECK" if check_only else "PATCH"
    if not os.path.isdir(base_dir):
        print(f"ERROR: not a directory: {base_dir}", file=sys.stderr)
        sys.exit(2)

    target = find_target(base_dir)
    if not target:
        print(
            f"ERROR [{mode}]: {TARGET_CLASS_REL} not found under {base_dir} "
            "(smali*/...). APK structure changed?",
            file=sys.stderr,
        )
        sys.exit(1)

    ok, msg = patch_file(target, check_only=check_only)
    rel = os.path.relpath(target, base_dir)
    if ok:
        print(f"OK  [{mode}] [{rel}] {msg}")
        sys.exit(0)
    else:
        print(f"FAIL [{mode}] [{rel}] {msg}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
