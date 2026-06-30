#!/usr/bin/env python3
"""
Domico 非公式パッチ群の smali 適用スクリプト。

適用する内容:
  1. 共通基盤クラス(scripts/patch_assets 配下の patch/*.smali)を classes4 へ配置。
  2. ログイントースト クリックスルー(AlertUtils): 通知ダイアログを
     PatchPrefs.toastEnabled のときだけクリックスルー化(既存パッチをフラグ化)。
  3. テレメトリ停止 + Activity トラッカ + prefs ロード(MyApplication.onCreate)。
  4. ロード表示クリックスルー(FrameLayoutLoading): initView 内の全 setClickable
     (外枠 + 内側スクリム)を PatchPrefs.loadingEnabled に応じて切替。
  5. 送信系の選択的遮断(AppModule.provideRetrofit): 送信(mutation)通信中だけ
     入力を遮断する PatchTrafficInterceptor を OkHttp クライアントへ追加。
     遮断対象は HTTP メソッドではなく URL パス(安定セグメントの部分一致)で
     判定する。取得/検証系の POST(*/check-*, */validate, menus/* 等)は通す。
  6. 設定画面導線(MenuFragment.init): メニューリストに「パッチ設定」行を追加
     (タップで設定ダイアログ)。
  7. 下部ナビ長押し導線(MainTabHostFragment.init): 「メニュー」タブ長押しで
     設定ダイアログを開く(PatchSettingsEntry.installNav)。

設計方針(公式更新で再適用しやすくする):
  - 行番号ではなく「クラス + メソッド + 命令パターン」をアンカーにする。
  - 冪等: 既にマーカーがあるパッチはスキップ。
  - アンカーが見つからなければ非0で終了し、CI で版変更を検知できるようにする。

使い方:
  python scripts/patch_smali.py [--check] [--patch-version <str>] <decoded_base_dir>
    例) python scripts/patch_smali.py work/base
"""

import argparse
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
REL_TABHOST = os.path.join("vn", "com", "bravesoft", "androidapp", "ui", "MainTabHostFragment.smali")
REL_HOME = os.path.join("vn", "com", "bravesoft", "androidapp", "ui", "HomeFragment.smali")

# マーカー(冪等判定)
M_TOAST = "# domico-patch: gated login-toast click-through"
M_INIT = "# domico-patch: init privacy/loading patches"
M_LOADING = "# domico-patch: gated loading-overlay click-through"
M_INTERCEPTOR = "# domico-patch: register mutating-request input guard"
M_MENU = "# domico-patch: settings entry (menu row)"
M_NAV = "# domico-patch: settings entry (bottom-nav menu long-press)"
M_CHECKIN_ENABLE = "# domico-patch: allow out-of-time check-in (gated)"
M_CHECKIN_CONFIRM = "# domico-patch: out-of-time check-in confirm gate"
M_CHECKIN_BYPASS = "# domico-patch: E1015 bypass interceptor"
M_AUTOCHECKIN_FIRE = "# domico-patch: auto check-in fire on showUICheckIn"


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


def smali_string(s):
    """文字列を smali の文字列リテラル(両端の " 含む)に安全に変換する。

    バックスラッシュ・二重引用符・制御文字をエスケープし、想定外の値
    (versionName 等に特殊文字が混入)でも壊れた .field 行を生まないようにする。
    """
    out = (
        s.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )
    return f'"{out}"'


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
    # AlertUtils と同じ dex シャードにヘルパーを配置する。
    # AlertUtils が別シャードへ移動しても ART はクロス dex 参照を解決できるが、
    # 予期せぬシャードへの配置はログで可視化する。
    smali_root, _ = find_smali_dir(base_dir, REL_ALERTUTILS)
    if not smali_root:
        return False, False, "classes4 smali root (AlertUtils) not found for asset placement"
    shard_name = os.path.basename(smali_root)
    if shard_name not in ("smali", "smali_classes4"):
        log(f"WARNING: placing patch assets into {shard_name} (expected smali_classes4); AlertUtils may have moved dex shards")
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
                            f"{smali_string(patch_version)}\n"
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

# initView 内の setClickable(Z)V を受け側クラスを問わず拾う。
# 外枠 FrameLayoutLoading 自身と内側 scrim(RelativeLayout 等)の両方が対象。
SETCLICKABLE_RE = re.compile(
    r"^(\s*)invoke-virtual\s*\{(v\d+|p\d+),\s*(v\d+)\}\,\s*L[^;]+;->setClickable\(Z\)V\s*$"
)


def patch_frameloading(base_dir, check_only):
    _root, path = find_smali_dir(base_dir, REL_LOADING)
    if not path:
        return False, False, "FrameLayoutLoading.smali not found"
    lines = read_lines(path)
    if M_LOADING in "".join(lines):
        return True, False, "already patched - skipped"
    # 最初に setClickable を含むメソッド(initView)を特定し、その中の
    # 全 setClickable(Z)V を gating する。外枠 FrameLayoutLoading 自身が
    # clickable=true のままだと、内側 scrim だけ通しても親がタッチを総取り
    # するため、両方を loadingEnabled で切り替える必要がある。
    ms = me = None
    i = 0
    while i < len(lines):
        if lines[i].startswith(".method"):
            s, e = i, None
            for j in range(s + 1, len(lines)):
                if lines[j].startswith(".end method"):
                    e = j
                    break
            if e is not None and any(
                SETCLICKABLE_RE.match(lines[k]) for k in range(s, e + 1)
            ):
                ms, me = s, e
                break
            i = (e + 1) if e is not None else (i + 1)
            continue
        i += 1
    if ms is None:
        return False, False, "anchor setClickable(Z)V not found in any method"
    anchors = [k for k in range(ms, me + 1) if SETCLICKABLE_RE.match(lines[k])]
    vals = [SETCLICKABLE_RE.match(lines[k]).group(3) for k in anchors]
    if check_only:
        return True, False, (
            f"would gate {len(anchors)} loading setClickable call(s) "
            f"(values={vals}) [dry-run]"
        )
    # 各呼び出しの直前で、その呼び出しが使う値レジスタを loadingEnabled から
    # 計算し直す(loadingEnabled=true→clickable=false でタッチを通す)。
    # 末尾側から挿入してインデックスを保つ。ラベルは呼び出しごとに一意化。
    for idx in range(len(anchors) - 1, -1, -1):
        k = anchors[idx]
        m = SETCLICKABLE_RE.match(lines[k])
        indent, val = m.group(1), m.group(3)
        # 冪等判定用マーカー M_LOADING は最初の呼び出し(idx==0)に必ず付与する。
        marker = M_LOADING if idx == 0 else f"{M_LOADING} ({idx})"
        inj = [
            f"{indent}{marker}\n",
            f"{indent}sget-boolean {val}, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->loadingEnabled:Z\n",
            f"{indent}if-nez {val}, :domico_ct_on_{idx}\n",
            f"{indent}const/4 {val}, 0x1\n",
            f"{indent}goto :domico_ct_done_{idx}\n",
            f"{indent}:domico_ct_on_{idx}\n",
            f"{indent}const/4 {val}, 0x0\n",
            f"{indent}:domico_ct_done_{idx}\n",
        ]
        lines[k:k] = inj
    write_lines(path, lines)
    return True, True, (
        f"gated {len(anchors)} loading-overlay setClickable call(s) (values={vals})"
    )


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


# ---- patch 9: AppModule E1015 bypass interceptor --------------------------

# interceptor パッチと同じ OkHttpClient$Builder->build() をアンカーにする。
# M_INTERCEPTOR マーカーへの依存を排除することで、フレッシュな smali
# (パッチ未適用) に対する --check でも FAIL にならない。


def patch_checkin_bypass(base_dir, check_only):
    _root, path = find_smali_dir(base_dir, REL_APPMODULE)
    if not path:
        return False, False, "AppModule.smali not found"
    lines = read_lines(path)
    start, end = method_bounds(lines, ".method public final provideRetrofit(")
    if start is None or end is None:
        return False, False, "provideRetrofit(...) not found"
    if M_CHECKIN_BYPASS in "".join(lines[start : end + 1]):
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
        return True, False, f"would add E1015 bypass interceptor (builder={builder}) [dry-run]"
    inj = [
        f"{indent}{M_CHECKIN_BYPASS}\n",
        f"{indent}invoke-static {{{builder}}}, Lvn/com/bravesoft/androidapp/patch/PatchCheckInBypass;->add(Lokhttp3/OkHttpClient$Builder;)Lokhttp3/OkHttpClient$Builder;\n",
        f"{indent}move-result-object {builder}\n",
    ]
    lines[anchor:anchor] = inj
    write_lines(path, lines)
    return True, True, f"added E1015 bypass interceptor (builder={builder})"


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
    # v0 が有効なレジスタであることを保証: .locals が 0 なら 1 に引き上げる。
    for j in range(start, end + 1):
        lm = re.match(r"^(\s*)\.locals\s+(\d+)\s*$", lines[j])
        if lm:
            if int(lm.group(2)) < 1:
                lines[j] = f"{lm.group(1)}.locals 1\n"
            break
    inj = [
        f"{indent}{M_MENU}\n",
        f"{indent}iget-object v0, p0, Lvn/com/bravesoft/androidapp/ui/MenuFragment;->binding:Lvn/com/bravesoft/androidapp/databinding/MenuLayoutBinding;\n",
        f"{indent}invoke-static {{v0}}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsEntry;->install(Lvn/com/bravesoft/androidapp/databinding/MenuLayoutBinding;)V\n",
    ]
    lines[ret_idx:ret_idx] = inj
    write_lines(path, lines)
    return True, True, "injected settings entry (menu row)"


# ---- patch 7: MainTabHostFragment bottom-nav long-press -------------------

def patch_maintabhost(base_dir, check_only):
    _root, path = find_smali_dir(base_dir, REL_TABHOST)
    if not path:
        return False, False, "MainTabHostFragment.smali not found"
    lines = read_lines(path)
    start, end = method_bounds(lines, ".method protected init(Landroid/view/View;)V")
    if start is None or end is None:
        return False, False, "MainTabHostFragment.init(View) not found"
    if M_NAV in "".join(lines[start : end + 1]):
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
        return False, False, "return-void not found in MainTabHostFragment.init"
    if check_only:
        return True, False, "would wire bottom-nav menu long-press [dry-run]"
    # v0 が有効なレジスタであることを保証: .locals が 0 なら 1 に引き上げる。
    for j in range(start, end + 1):
        lm = re.match(r"^(\s*)\.locals\s+(\d+)\s*$", lines[j])
        if lm:
            if int(lm.group(2)) < 1:
                lines[j] = f"{lm.group(1)}.locals 1\n"
            break
    inj = [
        f"{indent}{M_NAV}\n",
        f"{indent}iget-object v0, p0, Lvn/com/bravesoft/androidapp/ui/MainTabHostFragment;->binding:Lvn/com/bravesoft/androidapp/databinding/MainTabHostLayoutBinding;\n",
        f"{indent}invoke-static {{v0}}, Lvn/com/bravesoft/androidapp/patch/PatchSettingsEntry;->installNav(Lvn/com/bravesoft/androidapp/databinding/MainTabHostLayoutBinding;)V\n",
    ]
    lines[ret_idx:ret_idx] = inj
    write_lines(path, lines)
    return True, True, "wired bottom-nav menu long-press to settings"


# ---- patch 8: HomeFragment out-of-time check-in ---------------------------

# showUICheckIn 内で isCheckInTime() の結果を stateButton へ渡す箇所をアンカーにする。
# isCheckInTime()Z は HomeFragment 内で 1 箇所のみ(チェックインボタンの時間ゲート)。
ISCHECKINTIME_RE = re.compile(r"->isCheckInTime\(\)Z\s*$")
MOVERESULT_BOOL_RE = re.compile(r"^(\s*)move-result\s+(v\d+)\s*$")
STATEBUTTON_RE = re.compile(
    r"^(\s*)invoke-direct\s*\{(p\d+),\s*(v\d+),\s*(v\d+)\}\,\s*"
    r"L[^;]+;->stateButton\(Landroid/view/View;Z\)V\s*$"
)
# apktool は `.locals`、baksmali は `.registers` を出す。実ビルド(patch_apk)は baksmali 経由
# なので両方を受ける。`.registers` は params を含む総数(params は上位レジスタ)。
REGDIRECTIVE_RE = re.compile(r"^(\s*)\.(locals|registers)\s+(\d+)\s*$")
PKG_CHECKIN = "Lvn/com/bravesoft/androidapp/patch/PatchCheckIn;"
DTO_DESC = "Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;"


def patch_homefragment_checkin(base_dir, check_only):
    _root, path = find_smali_dir(base_dir, REL_HOME)
    if not path:
        return False, False, "HomeFragment.smali not found"
    lines = read_lines(path)
    joined = "".join(lines)
    have_enable = M_CHECKIN_ENABLE in joined
    have_confirm = M_CHECKIN_CONFIRM in joined
    if have_enable and have_confirm:
        return True, False, "already patched - skipped"

    msgs = []
    changed = False

    # --- 注入A: グレーアウト時にボタンを再有効化 -----------------------------
    if not have_enable:
        # isCheckInTime() -> move-result <bool> -> stateButton(p0, <view>, <bool>)
        ict_idx = next(
            (i for i, ln in enumerate(lines) if ISCHECKINTIME_RE.search(ln)), None
        )
        if ict_idx is None:
            return False, False, "anchor isCheckInTime()Z not found in HomeFragment"
        bool_reg = bm_idx = None
        for j in range(ict_idx + 1, min(ict_idx + 6, len(lines))):
            m = MOVERESULT_BOOL_RE.match(lines[j])
            if m:
                bm_idx, bool_reg = j, m.group(2)
                break
        if bool_reg is None:
            return False, False, "move-result after isCheckInTime() not found"
        anchor = indent = view_reg = None
        for k in range(bm_idx + 1, min(bm_idx + 20, len(lines))):
            m = STATEBUTTON_RE.match(lines[k])
            if m and m.group(4) == bool_reg:
                anchor, indent, view_reg = k, m.group(1), m.group(3)
                break
        if anchor is None:
            return False, False, "stateButton(isCheckInTime) anchor not found"
        if check_only:
            msgs.append(
                f"would re-enable check-in btn (view={view_reg}, time={bool_reg}) [dry-run]"
            )
        else:
            inj = [
                f"{indent}{M_CHECKIN_ENABLE}\n",
                f"{indent}invoke-static {{{view_reg}, p1, {bool_reg}}}, "
                f"{PKG_CHECKIN}->enableOutOfTime(Landroid/view/View;{DTO_DESC}Z)V\n",
            ]
            lines[anchor + 1 : anchor + 1] = inj
            changed = True
            msgs.append(f"re-enabled check-in btn (view={view_reg}, time={bool_reg})")

    # --- 注入B: checkInAction 冒頭の確認ゲート --------------------------------
    if not have_confirm:
        sig = ".method private final checkInAction(" + DTO_DESC + ")V"
        start, end = method_bounds(lines, sig)
        if start is None or end is None:
            return False, False, "checkInAction(MenuForDayDTO) not found"
        # checkInAction(MenuForDayDTO)V は非 static + 1 引数 = param レジスタ 2 個。
        # スクラッチ v0 を確保するため: .locals は >=1、.registers は >=3 (= 2 params + v0)。
        loc_idx = indent = None
        for j in range(start, end + 1):
            m = REGDIRECTIVE_RE.match(lines[j])
            if m:
                loc_idx, indent = j, m.group(1)
                kind, n = m.group(2), int(m.group(3))
                if kind == "locals" and n < 1:
                    lines[j] = f"{indent}.locals 1\n"
                elif kind == "registers" and n < 3:
                    lines[j] = f"{indent}.registers 3\n"
                break
        if loc_idx is None:
            return False, False, ".locals/.registers not found in checkInAction"
        if check_only:
            msgs.append("would inject confirm gate into checkInAction [dry-run]")
        else:
            inj = [
                f"{indent}{M_CHECKIN_CONFIRM}\n",
                f"{indent}invoke-static {{p0, p1}}, "
                f"{PKG_CHECKIN}->confirmOutOfTime(Landroidx/fragment/app/Fragment;{DTO_DESC})Z\n",
                f"{indent}move-result v0\n",
                f"{indent}if-eqz v0, :domico_checkin_continue\n",
                f"{indent}return-void\n",
                f"{indent}:domico_checkin_continue\n",
            ]
            lines[loc_idx + 1 : loc_idx + 1] = inj
            changed = True
            msgs.append("injected confirm gate into checkInAction")

    if changed:
        write_lines(path, lines)
    return True, changed, "; ".join(msgs) if msgs else "no-op"


# ---- patch 10: HomeFragment auto check-in fire ----------------------------

def patch_homefragment_autocheckin(base_dir, check_only):
    _root, path = find_smali_dir(base_dir, REL_HOME)
    if not path:
        return False, False, "HomeFragment.smali not found"
    lines = read_lines(path)
    if M_AUTOCHECKIN_FIRE in "".join(lines):
        return True, False, "already patched - skipped"

    sig = ".method private final showUICheckIn(" + DTO_DESC + ")V"
    start, end = method_bounds(lines, sig)
    if start is None or end is None:
        return False, False, "showUICheckIn(MenuForDayDTO) not found"

    # 最後の return-void に注入してすべての終了パスをカバーする
    ret_idx = indent = None
    for j in range(end, start, -1):
        m = re.match(r"^(\s*)return-void\s*$", lines[j])
        if m:
            ret_idx, indent = j, m.group(1)
            break
    if ret_idx is None:
        return False, False, "return-void not found in showUICheckIn"

    if check_only:
        return True, False, "would inject checkAndFire into showUICheckIn [dry-run]"

    PKG_AUTO = "Lvn/com/bravesoft/androidapp/patch/PatchAutoCheckin;"
    HOME_DESC = "Lvn/com/bravesoft/androidapp/ui/HomeFragment;"
    inj = [
        f"{indent}{M_AUTOCHECKIN_FIRE}\n",
        f"{indent}invoke-static {{p0, p1}}, {PKG_AUTO}->checkAndFire({HOME_DESC}{DTO_DESC})V\n",
    ]
    lines[ret_idx:ret_idx] = inj
    write_lines(path, lines)
    return True, True, "injected checkAndFire into showUICheckIn"


# ---- driver ----------------------------------------------------------------

# (名前, 関数, 対象クラスの相対 smali パス)。
# 3 要素目は「変更された shard を特定する」ために使う: パッチ適用後に
# find_smali_dir で解決した shard 名が、再アセンブルすべき dex を示す。
# assets はヘルパーを AlertUtils と同じ shard に置くため REL_ALERTUTILS を使う。
PATCHES = [
    ("assets", patch_assets, REL_ALERTUTILS),
    ("toast", patch_alertutils, REL_ALERTUTILS),
    ("init", patch_myapplication, REL_MYAPP),
    ("loading", patch_frameloading, REL_LOADING),
    ("interceptor", patch_appmodule, REL_APPMODULE),
    ("checkin-bypass", patch_checkin_bypass, REL_APPMODULE),
    ("menu", patch_menufragment, REL_MENU),
    ("nav", patch_maintabhost, REL_TABHOST),
    ("checkin", patch_homefragment_checkin, REL_HOME),
    ("checkin-auto", patch_homefragment_autocheckin, REL_HOME),
]


def main():
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("base_dir", help="baksmali 展開済みディレクトリ")
    ap.add_argument(
        "--check",
        action="store_true",
        help="dry-run: パッチ可否のみ判定、ファイルを書かない",
    )
    ap.add_argument(
        "--patch-version",
        help="PatchInfo.VERSION に埋め込むバージョン文字列",
    )
    ap.add_argument(
        "--changed-out",
        help="実際に変更した shard 名(改行区切り)を書き出すファイル。"
        "patch_apk が再アセンブル対象 dex を絞るために使う。",
    )
    args = ap.parse_args()
    check_only = args.check
    patch_version = args.patch_version
    base_dir = args.base_dir
    mode = "CHECK" if check_only else "PATCH"
    if not os.path.isdir(base_dir):
        ap.error(f"not a directory: {base_dir}")

    failed = False
    changed_shards = set()
    for name, fn, rel in PATCHES:
        try:
            if name == "assets":
                ok, changed, msg = fn(base_dir, check_only, patch_version)
            else:
                ok, changed, msg = fn(base_dir, check_only)
        except Exception as e:  # noqa: BLE001
            ok, changed, msg = False, False, f"exception: {e}"
        tag = "OK " if ok else "FAIL"
        stream = sys.stdout if ok else sys.stderr
        print(f"{tag} [{mode}] [{name}] {msg}", file=stream)
        if not ok:
            failed = True
            continue
        # 実際にファイルを書き換えたパッチの shard を再アセンブル対象に記録する。
        if changed and not check_only:
            root, _ = find_smali_dir(base_dir, rel)
            if root:
                changed_shards.add(os.path.basename(root))

    if args.changed_out and not check_only:
        with open(args.changed_out, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(sorted(changed_shards)))

    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
