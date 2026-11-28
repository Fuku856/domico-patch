#!/usr/bin/env python3
"""patch_smali.patch_forceupdate の単体テスト。

公式 MainActivity.smali の構造を模した最小フィクスチャを使い、
  - ラムダ連番($lambda$4 -> $lambda$9)やレジスタ番号が変化しても注入できること
  - --check (dry-run) がファイルを書き換えないこと
  - 2回目実行が冪等にスキップすること
  - アンカー欠落時に確実に FAIL (ok=False) になること
を標準 unittest のみで検証する。
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import patch_smali  # noqa: E402

SAMPLE_MAINACTIVITY = """\
.class public final Lvn/com/bravesoft/androidapp/MainActivity;
.super Landroidx/appcompat/app/AppCompatActivity;

.method public static synthetic $r8$lambda$abc(Lvn/com/bravesoft/androidapp/MainActivity;Lvn/com/bravesoft/androidapp/model/ForceUpdateDTO;)V
    .registers 2
    invoke-static {p0, p1}, Lvn/com/bravesoft/androidapp/MainActivity;->observeViewModel$lambda$9(Lvn/com/bravesoft/androidapp/MainActivity;Lvn/com/bravesoft/androidapp/model/ForceUpdateDTO;)V
    return-void
.end method

.method private static final observeViewModel$lambda$9(Lvn/com/bravesoft/androidapp/MainActivity;Lvn/com/bravesoft/androidapp/model/ForceUpdateDTO;)V
    .registers 13

    if-eqz p1, :cond_end

    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/ForceUpdateDTO;->isForceUpdate()Ljava/lang/Boolean;
    move-result-object v3
    const/4 v1, 0x1
    invoke-static {v1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
    move-result-object v4
    invoke-static {v3, v4}, Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z
    move-result v3
    if-eqz v3, :cond_not_force

    invoke-virtual {p1}, Lvn/com/bravesoft/androidapp/model/ForceUpdateDTO;->getAndroidVersion()Ljava/lang/String;
    move-result-object v3
    invoke-static {v3}, Lvn/com/bravesoft/androidapp/ext/StringExtensionKt;->toVersionApp(Ljava/lang/String;)I
    move-result v3

    const-string v4, "1.5.6"
    invoke-static {v4}, Lvn/com/bravesoft/androidapp/ext/StringExtensionKt;->toVersionApp(Ljava/lang/String;)I
    move-result v4

    if-le v3, v4, :cond_pass_1

    const-string v3, "Force Update"
    return-void

    :cond_pass_1
    return-void

    :cond_not_force
    return-void

    :cond_end
    return-void
.end method
"""


class PatchForceUpdateTests(unittest.TestCase):
    def _write_fixture(self, root, content=SAMPLE_MAINACTIVITY):
        path = os.path.join(root, "smali_classes4", patch_smali.REL_MAINACTIVITY)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(content)
        return path

    def test_check_only_does_not_modify_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = self._write_fixture(tmp)
            ok, changed, msg = patch_smali.patch_forceupdate(tmp, check_only=True)
            self.assertTrue(ok, msg)
            self.assertFalse(changed)
            self.assertIn("scratch=v3", msg)
            self.assertIn("pass=:cond_pass_1", msg)
            with open(path, encoding="utf-8") as f:
                self.assertEqual(f.read(), SAMPLE_MAINACTIVITY)

    def test_injects_gate_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = self._write_fixture(tmp)
            ok, changed, msg = patch_smali.patch_forceupdate(tmp, check_only=False)
            self.assertTrue(ok, msg)
            self.assertTrue(changed)
            with open(path, encoding="utf-8") as f:
                patched = f.read()
            self.assertIn(patch_smali.M_FORCEUPDATE, patched)
            self.assertIn(
                "invoke-static/range {p0 .. p1}, "
                "Lvn/com/bravesoft/androidapp/patch/PatchForceUpdate;->"
                "shouldSkip(Landroid/app/Activity;"
                "Lvn/com/bravesoft/androidapp/model/ForceUpdateDTO;)Z",
                patched,
            )
            self.assertIn("move-result v3\n    if-nez v3, :cond_pass_1\n", patched)

            # 2回目は冪等スキップ
            ok2, changed2, msg2 = patch_smali.patch_forceupdate(tmp, check_only=False)
            self.assertTrue(ok2, msg2)
            self.assertFalse(changed2)
            with open(path, encoding="utf-8") as f:
                self.assertEqual(f.read(), patched)

    def test_fails_when_mainactivity_missing(self):
        with tempfile.TemporaryDirectory() as tmp:
            ok, changed, _msg = patch_smali.patch_forceupdate(tmp, check_only=False)
            self.assertFalse(ok)
            self.assertFalse(changed)

    def test_fails_when_if_le_anchor_missing(self):
        broken = SAMPLE_MAINACTIVITY.replace("if-le v3, v4, :cond_pass_1", "nop")
        with tempfile.TemporaryDirectory() as tmp:
            self._write_fixture(tmp, broken)
            ok, changed, msg = patch_smali.patch_forceupdate(tmp, check_only=False)
            self.assertFalse(ok)
            self.assertFalse(changed)
            self.assertIn("if-le", msg)

    def test_fails_when_isforceupdate_missing(self):
        broken = SAMPLE_MAINACTIVITY.replace("isForceUpdate()Ljava/lang/Boolean;", "other()V")
        with tempfile.TemporaryDirectory() as tmp:
            self._write_fixture(tmp, broken)
            ok, changed, msg = patch_smali.patch_forceupdate(tmp, check_only=False)
            self.assertFalse(ok)
            self.assertFalse(changed)
            self.assertIn("ForceUpdateDTO observer", msg)

    def test_ignores_later_toversionapp_in_other_branch(self):
        # 非強制分岐にも toVersionApp 比較が増えた版を想定。先頭の比較にだけ注入する。
        extra = SAMPLE_MAINACTIVITY.replace(
            "    :cond_not_force\n    return-void\n",
            "    :cond_not_force\n"
            "    invoke-static {v3}, Lvn/com/bravesoft/androidapp/ext/StringExtensionKt;"
            "->toVersionApp(Ljava/lang/String;)I\n"
            "    move-result v5\n"
            "    if-le v5, v4, :cond_end\n"
            "    return-void\n",
        )
        with tempfile.TemporaryDirectory() as tmp:
            path = self._write_fixture(tmp, extra)
            ok, changed, msg = patch_smali.patch_forceupdate(tmp, check_only=False)
            self.assertTrue(ok, msg)
            self.assertTrue(changed)
            self.assertIn("pass=:cond_pass_1", msg)
            with open(path, encoding="utf-8") as f:
                patched = f.read()
            self.assertIn(
                "if-le v3, v4, :cond_pass_1\n    " + patch_smali.M_FORCEUPDATE, patched
            )
            self.assertEqual(patched.count(patch_smali.M_FORCEUPDATE), 1)

    def test_fails_when_if_le_operands_mismatch(self):
        broken = SAMPLE_MAINACTIVITY.replace(
            "if-le v3, v4, :cond_pass_1", "if-le v4, v3, :cond_pass_1"
        )
        with tempfile.TemporaryDirectory() as tmp:
            self._write_fixture(tmp, broken)
            ok, changed, msg = patch_smali.patch_forceupdate(tmp, check_only=False)
            self.assertFalse(ok)
            self.assertFalse(changed)
            self.assertIn("operands", msg)

    def test_fails_when_param_register_clobbered(self):
        # R8 が p1 をスクラッチに再利用した版では DTO を渡せないので FAIL させる。
        broken = SAMPLE_MAINACTIVITY.replace(
            '    const-string v4, "1.5.6"',
            '    const/4 p1, 0x0\n    const-string v4, "1.5.6"',
        )
        with tempfile.TemporaryDirectory() as tmp:
            self._write_fixture(tmp, broken)
            ok, changed, msg = patch_smali.patch_forceupdate(tmp, check_only=False)
            self.assertFalse(ok)
            self.assertFalse(changed)
            self.assertIn("p1 is overwritten", msg)


if __name__ == "__main__":
    unittest.main(verbosity=2)
