#!/usr/bin/env python3
"""version.py のバージョン算出ロジックの単体テスト。

git に依存しない純粋関数(_commit_level / _apply_bumps)と、タグ正規表現
(_TAG_RE による -dev 除外)を検証する。標準 unittest のみ。

  python scripts/test_version.py            # 直接実行
  python -m unittest scripts.test_version   # 発見実行
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import version  # noqa: E402


class CommitLevelTests(unittest.TestCase):
    def test_feat_is_minor(self):
        self.assertEqual(version._commit_level("feat: add row"), "minor")

    def test_fix_perf_refactor_are_patch(self):
        for t in ("fix", "perf", "refactor"):
            self.assertEqual(version._commit_level(f"{t}: tweak"), "patch")

    def test_scope_is_ignored(self):
        self.assertEqual(version._commit_level("feat(ui): scoped"), "minor")

    def test_bang_is_major(self):
        self.assertEqual(version._commit_level("feat!: drop api"), "major")
        self.assertEqual(version._commit_level("fix(core)!: break"), "major")

    def test_breaking_change_footer_is_major(self):
        msg = "feat: x\n\nBREAKING CHANGE: removed y"
        self.assertEqual(version._commit_level(msg), "major")
        msg2 = "fix: x\n\nBREAKING-CHANGE: removed y"
        self.assertEqual(version._commit_level(msg2), "major")

    def test_non_release_types_are_none(self):
        for t in ("docs", "ci", "chore", "test", "build", "style"):
            self.assertIsNone(version._commit_level(f"{t}: noise"))

    def test_non_conventional_is_none(self):
        self.assertIsNone(version._commit_level("Merge branch 'dev'"))
        self.assertIsNone(version._commit_level(""))

    def test_type_is_case_insensitive(self):
        self.assertEqual(version._commit_level("FEAT: caps"), "minor")


class ApplyBumpsTests(unittest.TestCase):
    def _apply(self, base, newest_first):
        return version._apply_bumps(base, newest_first)

    def test_no_release_commits_stays_at_base(self):
        nxt, top = self._apply((0, 3, 0), ["docs: readme", "chore: deps"])
        self.assertEqual(nxt, (0, 3, 0))
        self.assertIsNone(top)

    def test_single_fix_bumps_patch_once(self):
        nxt, top = self._apply((0, 3, 0), ["fix: a"])
        self.assertEqual(nxt, (0, 3, 1))
        self.assertEqual(top, "patch")

    def test_cumulative_patch_per_commit(self):
        # 新しい順で 3 本の patch -> +3
        nxt, top = self._apply((0, 3, 0), ["fix: c", "fix: b", "fix: a"])
        self.assertEqual(nxt, (0, 3, 3))
        self.assertEqual(top, "patch")

    def test_patch_does_not_carry_across_ten(self):
        base = (0, 3, 9)
        nxt, _ = self._apply(base, ["fix: bump to ten"])
        self.assertEqual(nxt, (0, 3, 10))

    def test_feat_resets_patch_and_bumps_minor(self):
        # 古い順で fix, fix, feat -> patch 2 回のあと minor で patch リセット
        newest_first = ["feat: new", "fix: b", "fix: a"]
        nxt, top = self._apply((0, 3, 0), newest_first)
        self.assertEqual(nxt, (0, 4, 0))
        self.assertEqual(top, "minor")

    def test_feat_then_fixes_accumulate_on_new_minor(self):
        # 古い順: feat(->0.4.0), fix(->0.4.1), fix(->0.4.2)
        newest_first = ["fix: last", "fix: mid", "feat: first"]
        nxt, top = self._apply((0, 3, 0), newest_first)
        self.assertEqual(nxt, (0, 4, 2))
        self.assertEqual(top, "minor")

    def test_major_resets_minor_and_patch(self):
        # 古い順: fix(->0.3.1), feat!(->1.0.0), fix(->1.0.1)
        newest_first = ["fix: after", "feat!: break", "fix: before"]
        nxt, top = self._apply((0, 3, 0), newest_first)
        self.assertEqual(nxt, (1, 0, 1))
        self.assertEqual(top, "major")

    def test_top_is_highest_level_seen(self):
        # patch のみでは top=patch、feat が混ざれば minor
        _, top = self._apply((0, 0, 0), ["feat: x", "fix: y"])
        self.assertEqual(top, "minor")


class TagRegexTests(unittest.TestCase):
    def test_official_tag_matches(self):
        self.assertIsNotNone(version._TAG_RE.match("patch-v1.2.3"))

    def test_dev_prerelease_tag_is_excluded(self):
        # -dev サフィックス付きは基準にならない(累積 bump の収束前提)
        self.assertIsNone(version._TAG_RE.match("patch-v1.2.3-dev"))

    def test_arbitrary_suffix_excluded(self):
        self.assertIsNone(version._TAG_RE.match("patch-v1.2.3-rc1"))
        self.assertIsNone(version._TAG_RE.match("patch-v1.2"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
