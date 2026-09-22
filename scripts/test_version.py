#!/usr/bin/env python3
"""version.py のバージョン算出ロジックの単体テスト。

git に依存しない純粋関数(_commit_level / _highest_level / _apply_bump)、
dev プレリリース連番(dev_seq、git 呼び出しはスタブ化)、タグ正規表現
(_TAG_RE / _DEV_TAG_RE)、および cliff.toml の tag_pattern を検証する。
標準 unittest のみ。

  python scripts/test_version.py            # 直接実行
  python -m unittest scripts.test_version   # 発見実行
"""
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import version  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


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


class HighestLevelTests(unittest.TestCase):
    """コミット群から採用する増分レベル(最大1つ)を選ぶ。"""

    def test_no_release_commits_is_none(self):
        self.assertIsNone(version._highest_level(["docs: readme", "chore: deps"]))

    def test_empty_is_none(self):
        self.assertIsNone(version._highest_level([]))

    def test_fix_only_is_patch(self):
        self.assertEqual(version._highest_level(["fix: c", "fix: b", "fix: a"]), "patch")

    def test_feat_wins_over_fix(self):
        self.assertEqual(version._highest_level(["fix: b", "feat: a"]), "minor")

    def test_breaking_wins_over_feat(self):
        self.assertEqual(
            version._highest_level(["fix: c", "feat!: break", "feat: a"]), "major")

    def test_noise_does_not_mask_release_commits(self):
        msgs = ["docs: readme", "ci: tweak", "fix: real", "chore: deps"]
        self.assertEqual(version._highest_level(msgs), "patch")


class ApplyBumpTests(unittest.TestCase):
    """基準版に増分を **1 回だけ** 適用する(コミット本数では刻まない)。"""

    def test_none_stays_at_base(self):
        self.assertEqual(version._apply_bump((0, 3, 0), None), (0, 3, 0))

    def test_patch_bumps_patch(self):
        self.assertEqual(version._apply_bump((0, 3, 0), "patch"), (0, 3, 1))

    def test_minor_resets_patch(self):
        self.assertEqual(version._apply_bump((0, 3, 7), "minor"), (0, 4, 0))

    def test_major_resets_minor_and_patch(self):
        self.assertEqual(version._apply_bump((0, 3, 7), "major"), (1, 0, 0))

    def test_patch_does_not_carry_across_ten(self):
        self.assertEqual(version._apply_bump((0, 3, 9), "patch"), (0, 3, 10))


class OneReleaseOneBumpTests(unittest.TestCase):
    """回帰防止: 1 リリース = 1 bump。コミット本数分は刻まない。

    以前の実装はリリース対象コミットを1本ずつ累積適用していたため、feat が3本
    入ったリリースで minor が3段飛んでいた(patch-v0.5.3 の次が patch-v0.8.2)。
    """

    def _release(self, base, newest_first):
        return version._apply_bump(base, version._highest_level(newest_first))

    def test_three_feats_bump_minor_once(self):
        # patch-v0.5.3 -> patch-v0.8.2 を生んだ実際のコミット構成(新しい順)
        msgs = [
            "Merge pull request #35 from Fuku856/dev",
            "fix: モジュール dex から smali クラスへの参照も検証対象に含める",
            "fix: 時間外チェックインの切り替えを子行の表示へ即時反映する",
            "feat: 純粋な9クラスを Java モジュールへ移行し、参照検証を追加",
            "ci: dry-run と実ビルドで同じ build-tools を使うよう固定",
            "feat: パッチ本体を別dexモジュール化する土台を追加",
            "feat: 受信push全ペイロードを端末内ファイルへ保存するデバッグパッチを追加",
        ]
        self.assertEqual(self._release((0, 5, 3), msgs), (0, 6, 0))

    def test_many_fixes_bump_patch_once(self):
        msgs = ["fix: c", "fix: b", "fix: a"]
        self.assertEqual(self._release((0, 3, 0), msgs), (0, 3, 1))

    def test_docs_only_release_is_skipped(self):
        msgs = ["docs: readme", "ci: workflow"]
        self.assertEqual(self._release((0, 3, 0), msgs), (0, 3, 0))


class DevSeqTests(unittest.TestCase):
    """dev プレリリース連番は既存 patch-v{X.Y.Z}-dev.* タグの最大 + 1。"""

    def setUp(self):
        self._real_git = version._git

    def tearDown(self):
        version._git = self._real_git

    def _stub_tags(self, tags):
        def fake(args, default=None):
            if args and args[0] == "tag":
                return tags
            return self._real_git(args, default)
        version._git = fake

    def test_no_existing_tags_starts_at_one(self):
        self._stub_tags("")
        self.assertEqual(version.dev_seq((0, 6, 0)), 1)

    def test_legacy_suffixless_tag_counts_as_zero(self):
        # 旧形式 patch-v0.6.0-dev(連番なし)が残っていても次は 1
        self._stub_tags("patch-v0.6.0-dev\n")
        self.assertEqual(version.dev_seq((0, 6, 0)), 1)

    def test_next_is_max_plus_one(self):
        self._stub_tags("patch-v0.6.0-dev\npatch-v0.6.0-dev.1\npatch-v0.6.0-dev.2\n")
        self.assertEqual(version.dev_seq((0, 6, 0)), 3)

    def test_out_of_order_tags(self):
        self._stub_tags("patch-v0.6.0-dev.3\npatch-v0.6.0-dev.1\n")
        self.assertEqual(version.dev_seq((0, 6, 0)), 4)

    def test_other_versions_are_ignored(self):
        # glob が緩くても、別版のタグは連番に影響しない
        self._stub_tags("patch-v0.7.0-dev.9\npatch-v0.6.0-dev.1\n")
        self.assertEqual(version.dev_seq((0, 6, 0)), 2)

    def test_malformed_tags_are_ignored(self):
        self._stub_tags("patch-v0.6.0-dev.x\npatch-v0.6.0-devfoo\nrandom\n")
        self.assertEqual(version.dev_seq((0, 6, 0)), 1)


class TagRegexTests(unittest.TestCase):
    def test_official_tag_matches(self):
        self.assertIsNotNone(version._TAG_RE.match("patch-v1.2.3"))

    def test_dev_prerelease_tag_is_excluded(self):
        # -dev サフィックス付きは基準にならない(本リリース版を汚さない)
        self.assertIsNone(version._TAG_RE.match("patch-v1.2.3-dev"))
        self.assertIsNone(version._TAG_RE.match("patch-v1.2.3-dev.2"))

    def test_arbitrary_suffix_excluded(self):
        self.assertIsNone(version._TAG_RE.match("patch-v1.2.3-rc1"))
        self.assertIsNone(version._TAG_RE.match("patch-v1.2"))

    def test_base_following_tag_excluded(self):
        self.assertIsNone(version._TAG_RE.match("v1.5.4-patch"))

    def test_dev_tag_regex_captures_seq(self):
        m = version._DEV_TAG_RE.match("patch-v0.6.0-dev.12")
        self.assertIsNotNone(m)
        self.assertEqual(m.group(4), "12")

    def test_dev_tag_regex_allows_missing_seq(self):
        m = version._DEV_TAG_RE.match("patch-v0.6.0-dev")
        self.assertIsNotNone(m)
        self.assertIsNone(m.group(4))


class CliffTagPatternTests(unittest.TestCase):
    """cliff.toml の tag_pattern が version.py の _TAG_RE と同じ集合を選ぶか。

    git-cliff 2.x の tag_pattern は glob ではなく **部分一致の正規表現** なので、
    アンカーが無いと patch-v0.6.0-dev.1 のような dev タグにも前方一致し、
    CHANGELOG に dev 節が混入する(実際に混入していた)。
    """

    @classmethod
    def setUpClass(cls):
        try:
            import tomllib
        except ImportError:  # Python 3.10 以下
            raise unittest.SkipTest("tomllib(Python 3.11+)が無いためスキップ")
        with open(os.path.join(ROOT, "cliff.toml"), "rb") as f:
            cls.pattern = re.compile(tomllib.load(f)["git"]["tag_pattern"])

    def test_release_tag_is_a_boundary(self):
        self.assertIsNotNone(self.pattern.search("patch-v0.9.2"))

    def test_dev_tags_are_not_boundaries(self):
        for t in ("patch-v0.6.0-dev", "patch-v0.6.0-dev.1", "patch-v0.10.0-dev.12"):
            with self.subTest(tag=t):
                self.assertIsNone(self.pattern.search(t))

    def test_base_following_tags_are_not_boundaries(self):
        for t in ("v1.5.4-patch", "v1.5.4-official", "v1.5.4-dev"):
            with self.subTest(tag=t):
                self.assertIsNone(self.pattern.search(t))

    def test_agrees_with_version_py_tag_regex(self):
        tags = [
            "patch-v0.9.2", "patch-v1.2.3", "patch-v0.6.0-dev", "patch-v0.6.0-dev.2",
            "patch-v1.2.3-rc1", "patch-v1.2", "v1.5.4-patch", "v1.5.4-official",
        ]
        for t in tags:
            with self.subTest(tag=t):
                self.assertEqual(
                    bool(self.pattern.search(t)),
                    bool(version._TAG_RE.match(t)),
                    f"cliff.toml と version.py で {t} の扱いが食い違っている",
                )


if __name__ == "__main__":
    unittest.main(verbosity=2)
