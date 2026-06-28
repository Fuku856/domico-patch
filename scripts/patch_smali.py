#!/usr/bin/env python3
"""
Domico 非公式パッチ群の smali 適用スクリプト。

適用する内容:
  1. 共通基盤クラス(scripts/patch_assets 配下の patch/*.smali)を classes4 へ配置。
  2. ログイントースト クリックスルー(AlertUtils): 通知ダイアログを
     PatchPrefs.toastEnabled のときだけクリックスルー化(既存パッチをフラグ化)。
  3. テレメトリ停止 + Activity トラッカ + prefs ロード(MyApplication.onCreate)。
  4. ロード表示クリックスルー(FrameLayoutLoading): 全画面スクリムの
     setClickable を PatchPrefs.loadingEnabled に応じて切替。
  5. 送信系の選択的遮断(AppModule.provideRetrofit): 非GET通信中だけ入力を
     遮断する PatchTrafficInterceptor を OkHttp クライアントへ追加。
  6. 設定画面導線(MenuFragment.init): メニュー行追加 + バージョン長押し。

設計方針(公式更新で再適用しやすくする):
  - 行番号ではなく「クラス + メソッド + 命令パターン」をアンカーにする。
  - 冪等: 既にマーカーがあるパッチはスキップ。
  - アンカーが見つからなければ非0で終了し、CI で版変更を検知できるようにする。

使い方:
  python scripts/patch_smali.py [--check] [--patch-version <str>] <decoded_base_dir>
    例) python scripts/patch_smali.py work/base
"""

import os
import re
import shutil
import sys

# Windows コンソール(cp932)でも安全に出力できるよう UTF-8 に固定
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8")
    except Exception:
        pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET_DIR = os.path.join(ROOT, "scripts", "patch_assets")
PATCH_PKG = "Lvn/com/bravesoft/androidapp/patch/"

# 各クラスの相対 smali パス(smali*/ 配下)
REL_ALERTUTILS = os.path.join("vn", "com", "bravesoft", "androidapp", "utils", "AlertUtils.smali")
REL_MYAPP = os.path.join("vn", "com", "bravesoft", "androidapp", "MyApplication.smali")
REL_LOADING = os.path.join("vn", "com", "bravesoft", "androidapp", "views", "FrameLayoutLoading.smali")
REL_APPMODULE = os.path.join("vn", "com", "bravesoft", "androidapp", "di", "AppModule.smali")
REL_MENU = os.path.join("vn", "com", "bravesoft", "androidapp", "ui", "MenuFragment.smali")

# マーカー(冪等判定)
M_TOAST = "# domico-patch: gated login-toast click-through"
M_INIT = "# domico-patch: init privacy/loading patches"
M_LOADING = "# domico-patch: gated loading-overlay click-through"
M_INTERCEPTOR = "# domico-patch: register mutating-request input guard"
M_MENU = "# domico-patch: settings entry (menu row + version long-press)"


def log(m):
    print(f"[patch_smali] {m}", flush=True)


# ---- smali helper utilities ------------------------------------------------

def find_smali_dir(base_dir, rel_path):
    """smali, smali_classes2.. を横断して rel_path を含む smali ルートを返す。"""
    for entry in sorted(os.listdir(base_dir)):
        if entry == "smali" or entry.startswith("smali_classes"):
            cand = os.path.join(base_dir, entry, rel_path)
            if os.path.isfile(cand):
                return os.path.join(base_dir, entry), cand
    return None, None


def read_lines(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.readlines()


def write_lines(path, lines):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.writelines(lines)


def method_bounds(lines, sig_prefix):
    start = None
    for i, ln in enumerate(lines):
        if ln.startswith(sig_prefix):
            start = i
            break
    if start is None:
        return None, None
    for j in range(start + 1, len(lines)):
        if lines[j].startswith(".end method"):
            return start, j
    return start, None


# ---- patch 1: assets -------------------------------------------------------

def patch_assets(base_dir, check_only, patch_version):
    if not os.path.isdir(ASSET_DIR):
        return False, False, f"asset dir missing: {ASSET_DIR}"
    smali_root, _ = find_smali_dir(base_dir, REL_ALERTUTILS)
    if not smali_root:
        return False, False, "classes4 smali root (AlertUtils) not found for asset placement"
    if check_only:
        n = sum(1 for _r, _d, fs in os.walk(ASSET_DIR) for f in fs if f.endswith(".smali"))
        return True, False, f"would place {n} helper class(es) into {os.path.basename(smali_root)} [dry-run]"

    count = 0
    for r, _d, files in os.walk(ASSET_DIR):
        for f in files:
            if not f.endswith(".smali"):
                continue
            src = os.path.join(r, f)
            rel = os.path.relpath(src, ASSET_DIR)
            dst = os.path.join(smali_root, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(src, dst)
            if patch_version and f == "PatchInfo.smali":
                pl = read_lines(dst)
                for k, ln in enumerate(pl):
                    if "VERSION:Ljava/lang/String; =" in ln:
                        pl[k] = (
                            "    .field public static final VERSION:Ljava/lang/String; = "
                            f'"{patch_version}"\n'
                        )
                write_lines(dst, pl)
            count += 1
    return True, True, f"placed {count} helper class(es) into {os.path.basename(smali_root)}"


# ---- patch 2: AlertUtils gated toast --------------------------------------

CLEARFLAGS_RE = re.compile(
    r"^(\s*)invoke-virtual\s*\{(v\d+),\s*(v\d+)\}\,\s*Landroid/view/Window;->clearFlags\(I\)V\s*$"
)


def patch_alertutils(base_dir, check_only):
    _root, path = find_smali_dir(base_dir, REL_ALERTUTILS)
    if not path:
        return False, False, "AlertUtils.smali not found"
    lines = read_lines(path)
    start, end = method_bounds(lines, ".method public final displayToastContract(")
    if start is None or end is None:
        return False, False, "displayToastContract(...) not found"
    seg = lines[start : end + 1]
    if M_TOAST in "".join(seg):
        return True, False, "already patched - skipped"
    anchor = None
    indent = win = scratch = None
    for j, ln in enumerate(seg):
        m = CLEARFLAGS_RE.match(ln)
        if m:
            anchor, indent, win, scratch = j, m.group(1), m.group(2), m.group(3)
            break
    if anchor is None:
        return False, False, "anchor Window->clearFlags(I)V not found in displayToastContract"
    if check_only:
        return True, False, f"would gate toast (window={win}, scratch={scratch}) [dry-run]"
    inj = [
        f"{indent}{M_TOAST}\n",
        f"{indent}sget-boolean {scratch}, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->toastEnabled:Z\n",
        f"{indent}if-eqz {scratch}, :domico_toast_skip\n",
        f"{indent}const/16 {scratch}, 0x18\n",
        f"{indent}invoke-virtual {{{win}, {scratch}}}, Landroid/view/Window;->addFlags(I)V\n",
        f"{indent}:domico_toast_skip\n",
    ]
    seg[anchor + 1 : anchor + 1] = inj
    lines[start : end + 1] = seg
    write_lines(path, lines)
    return True, True, f"gated toast click-through (window={win}, scratch={scratch})"


# ---- patch 3: MyApplication init ------------------------------------------

SUPER_ONCREATE_RE = re.compile(
    r"^(\s*)invoke-super\s*\{p0\}\,\s*Landroid/app/Application;->onCreate\(\)V\s*$"
)


def patch_myapplication(base_dir, check_only):
    _root, path = find_smali_dir(base_dir, REL_MYAPP)
    if not path:
        return False, False, "MyApplication.smali not found"
    lines = read_lines(path)
    start, end = method_bounds(lines, ".method public onCreate()V")
    if start is None or end is None:
        return False, False, "MyApplication.onCreate() not found"
    seg = lines[start : end + 1]
    if M_INIT in "".join(seg):
        return True, False, "already patched - skipped"
    anchor = indent = None
    for j, ln in enumerate(seg):
        m = SUPER_ONCREATE_RE.match(ln)
        if m:
            anchor, indent = j, m.group(1)
            break
    if anchor is None:
        return False, False, "anchor invoke-super onCreate not found"
    if check_only:
        return True, False, "would inject PatchInit.onAppCreate [dry-run]"
    inj = [
        f"{indent}{M_INIT}\n",
        f"{indent}invoke-static {{p0}}, Lvn/com/bravesoft/androidapp/patch/PatchInit;->onAppCreate(Landroid/app/Application;)V\n",
    ]
    seg[anchor + 1 : anchor + 1] = inj
    lines[start : end + 1] = seg
    write_lines(path, lines)
    return True, True, "injected PatchInit.onAppCreate"


# ---- patch 4: FrameLayoutLoading click-through ----------------------------

SETCLICKABLE_RE = re.compile(
    r"^(\s*)invoke-virtual\s*\{(v\d+),\s*(v\d+)\}\,\s*Landroid/widget/RelativeLayout;->setClickable\(Z\)V\s*$"
)


def patch_frameloading(base_dir, check_only):
    _root, path = find_smali_dir(base_dir, REL_LOADING)
    if not path:
        return False, False, "FrameLayoutLoading.smali not found"
    lines = read_lines(path)
    if M_LOADING in "".join(lines):
        return True, False, "already patched - skipped"
    anchor = indent = val = None
    for j, ln in enumerate(lines):
        m = SETCLICKABLE_RE.match(ln)
        if m:
            anchor, indent, val = j, m.group(1), m.group(3)
            break
    if anchor is None:
        return False, False, "anchor RelativeLayout->setClickable(Z)V not found"
    if check_only:
        return True, False, f"would gate loading overlay (value={val}) [dry-run]"
    inj = [
        f"{indent}{M_LOADING}\n",
        f"{indent}sget-boolean {val}, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->loadingEnabled:Z\n",
        f"{indent}if-nez {val}, :domico_ct_on\n",
        f"{indent}const/4 {val}, 0x1\n",
        f"{indent}goto :domico_ct_done\n",
        f"{indent}:domico_ct_on\n",
        f"{indent}const/4 {val}, 0x0\n",
        f"{indent}:domico_ct_done\n",
    ]
    lines[anchor:anchor] = inj
    write_lines(path, lines)
    return True, True, f"gated loading overlay click-through (value={val})"


# ---- patch 5: AppModule traffic interceptor -------------------------------

OKHTTP_BUILD_RE = re.compile(
    r"^(\s*)invoke-virtual\s*\{(v\d+|p\d+)\}\,\s*Lokhttp3/OkHttpClient\$Builder;->build\(\)Lokhttp3/OkHttpClient;\s*$"
)


def patch_appmodule(base_dir, check_only):
    _root, path = find_smali_dir(base_dir, REL_APPMODULE)
    if not path:
        return False, False, "AppModule.smali not found"
    lines = read_lines(path)
    start, end = method_bounds(lines, ".method public final provideRetrofit(")
    if start is None or end is None:
        return False, False, "provideRetrofit(...) not found"
    if M_INTERCEPTOR in "".join(lines[start : end + 1]):
        return True, False, "already patched - skipped"
    anchor = indent = builder = None
    for j in range(start, end + 1):
        m = OKHTTP_BUILD_RE.match(lines[j])
        if m:
            anchor, indent, builder = j, m.group(1), m.group(2)
            break
    if anchor is None:
        return False, False, "anchor OkHttpClient$Builder->build() not found in provideRetrofit"
    if check_only:
        return True, False, f"would add traffic interceptor (builder={builder}) [dry-run]"
    # スクラッチ register 不要(静的ヘルパが内部で生成)。builder のみ再利用。
    inj = [
        f"{indent}{M_INTERCEPTOR}\n",
        f"{indent}invoke-static {{{builder}}}, Lvn/com/bravesoft/androidapp/patch/PatchTrafficInterceptor;->add(Lokhttp3/OkHttpClient$Builder;)Lokhttp3/OkHttpClient$Builder;\n",
        f"{indent}move-result-object {builder}\n",
    ]
    lines[anchor:anchor] = inj
    write_lines(path, lines)
    return True, True, f"added mutating-request interceptor (builder={builder})"


# ---- patch 6: MenuFragment settings entry ---------------------------------

def patch_menufragment(base_dir, check_only):
    _root, path = find_smali_dir(base_dir, REL_MENU)
    if not path:
        return False, False, "MenuFragment.smali not found"
    lines = read_lines(path)
    start, end = method_bounds(lines, ".method protected init(Landroid/view/View;)V")
    if start is None or end is None:
        return False, False, "MenuFragment.init(View) not found"
    if M_MENU in "".join(lines[start : end + 1]):
        return True, False, "already patched - skipped"
    # init 内の最後の return-void を探す
    ret_idx = None
    indent = "    "
    for j in range(end, start, -1):
        m = re.match(r"^(\s*)return-void\s*$", lines[j])
        if m:
            ret_idx, indent = j, m.group(1)
            break
    if ret_idx is None:
        return False, False, "return-void not found in MenuFragment.init"
    if check_only:
        return True, False, "would inject settings entry into MenuFragment.init [dry-run]"
    # メソッド末尾(return-void 直前)では全 register が dead のため v0 を再利用できる。
    inj = [
        f"{indent}{M_MENU}\n",
        f"{indent}iget-object v0, p0, Lvn/com/bravesoft/androidapp/ui/MenuFragment;->binding:Lvn/com/bravesoft/androidapp/databinding/MenuLayoutBinding;\n",
        f"{indent}invoke-static {{v0}}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsEntry;->install(Lvn/com/bravesoft/androidapp/databinding/MenuLayoutBinding;)V\n",
    ]
    lines[ret_idx:ret_idx] = inj
    write_lines(path, lines)
    return True, True, "injected settings entry (menu row + version long-press)"


# ---- driver ----------------------------------------------------------------

PATCHES = [
    ("assets", patch_assets),
    ("toast", patch_alertutils),
    ("init", patch_myapplication),
    ("loading", patch_frameloading),
    ("interceptor", patch_appmodule),
    ("menu", patch_menufragment),
]


def main():
    argv = sys.argv[1:]
    check_only = False
    patch_version = None
    if "--check" in argv:
        check_only = True
        argv.remove("--check")
    if "--patch-version" in argv:
        i = argv.index("--patch-version")
        if i + 1 >= len(argv):
            print("ERROR: --patch-version requires a value", file=sys.stderr)
            sys.exit(2)
        patch_version = argv[i + 1]
        del argv[i : i + 2]
    if len(argv) != 1:
        print(__doc__)
        sys.exit(2)
    base_dir = argv[0]
    mode = "CHECK" if check_only else "PATCH"
    if not os.path.isdir(base_dir):
        print(f"ERROR: not a directory: {base_dir}", file=sys.stderr)
        sys.exit(2)

    failed = False
    for name, fn in PATCHES:
        try:
            if name == "assets":
                ok, _changed, msg = fn(base_dir, check_only, patch_version)
            else:
                ok, _changed, msg = fn(base_dir, check_only)
        except Exception as e:  # noqa: BLE001
            ok, msg = False, f"exception: {e}"
        tag = "OK " if ok else "FAIL"
        stream = sys.stdout if ok else sys.stderr
        print(f"{tag} [{mode}] [{name}] {msg}", file=stream)
        if not ok:
            failed = True

    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
