import subprocess
import tempfile
import unittest
from pathlib import Path

from generate_changelog import collect_entries, git, parse_message, render_changelog, select_base


def message(title, kind):
    return f"feat(app): {title}\n\nChangelog: {kind}\n"


class MessageTests(unittest.TestCase):
    def test_explicit_classification_is_independent_of_type(self):
        self.assertEqual(parse_message(message("Improve layout", "changed")), ("changed", "Improve layout"))
        self.assertIsNone(parse_message(message("Internal work", "none")))
        self.assertIsNone(parse_message("fix(app): Legacy message\n"))

    def test_body_is_not_public_and_footer_must_be_real(self):
        text = "fix(app): Correct display\n\nPrivate details\n\nChangelog: fixed\nCloses: #1\n"
        self.assertEqual(parse_message(text), ("fixed", "Correct display"))
        self.assertIsNone(parse_message("feat(app): Example\n\nChangelog: added\n\nNot a footer.\n"))

    def test_duplicate_and_invalid_markers_fail(self):
        for footer in (
            "Changelog: added\nChangelog: fixed\n",
            "Changelog: <added|changed|fixed|none>\n",
            "Changelog: unknown\n",
        ):
            with self.subTest(footer=footer), self.assertRaises(ValueError):
                parse_message("feat(app): Visible change\n\n" + footer)
        with self.assertRaises(ValueError):
            parse_message("Not a conventional subject\n\nChangelog: added\n")

    def test_markdown_is_literal_and_no_empty_groups(self):
        result = render_changelog([("changed", "[text](url) <b>*value*</b>")])
        self.assertIn(r"\[text\]\(url\)", result)
        self.assertIn(r"&lt;b&gt;\*value\*&lt;/b&gt;", result)
        self.assertNotIn("### \u65b0\u589e", result)
        self.assertIn("\u6682\u65e0\u66f4\u65b0\u8bf4\u660e", render_changelog([]))


class HistoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        git("init", "-b", "main", cwd=self.repo)
        git("config", "user.name", "Changelog Test", cwd=self.repo)
        git("config", "user.email", "changelog@example.invalid", cwd=self.repo)
        git("config", "commit.gpgsign", "false", cwd=self.repo)
        self.initial = self.commit(message("Original feature", "added"))

    def commit(self, text):
        git("commit", "--allow-empty", "-F", "-", cwd=self.repo, input_text=text)
        return git("rev-parse", "HEAD", cwd=self.repo).strip()

    def tag(self, name):
        git("tag", name, cwd=self.repo)

    def test_first_release_has_whole_history(self):
        self.tag("v1.0.0")
        target, base = select_base("HEAD", "release", "v1.0.0", cwd=self.repo)
        self.assertIsNone(base)
        self.assertEqual(collect_entries(target, base, cwd=self.repo), [("added", "Original feature")])

    def test_ranges_exclude_prerelease_snapshot_and_unreachable_tags(self):
        self.tag("v1.0.0")
        git("switch", "-c", "other", cwd=self.repo)
        self.commit(message("Unrelated", "added"))
        self.tag("v99.0.0")
        git("switch", "main", cwd=self.repo)
        self.commit(message("New feature", "added"))
        self.tag("snapshot")
        self.tag("v2.0.0-rc.1")
        self.tag("v2.0.0")
        target, base = select_base("HEAD", "release", "v2.0.0", cwd=self.repo)
        self.assertEqual(base, "v1.0.0")
        self.assertEqual(collect_entries(target, base, cwd=self.repo), [("added", "New feature")])
        self.assertEqual(select_base("HEAD", "snapshot", cwd=self.repo)[1], "snapshot")
        self.commit(message("After release", "changed"))
        target, base = select_base("HEAD", "snapshot", cwd=self.repo)
        self.assertEqual(collect_entries(target, base, cwd=self.repo), [("changed", "After release")])

    def test_semantic_order_and_alias_tags(self):
        self.tag("v1.9.0")
        self.commit(message("Next release", "added"))
        self.tag("v1.10.0")
        self.tag("v1.10.1")
        target, base = select_base("HEAD", "release", "v1.10.1", cwd=self.repo)
        self.assertEqual(base, "v1.10.0")
        self.assertEqual(collect_entries(target, base, cwd=self.repo), [])
        self.commit(message("After ten", "fixed"))
        self.assertEqual(select_base("HEAD", "snapshot", cwd=self.repo)[1], "v1.10.1")

    def test_snapshot_is_incremental_even_when_a_newer_stable_tag_exists(self):
        self.tag("v1.0.0")
        self.commit(message("Previous snapshot feature", "added"))
        self.tag("snapshot")
        self.commit(message("Since snapshot", "changed"))
        self.tag("v2.0.0")
        self.commit(message("Latest fix", "fixed"))
        target, base = select_base("HEAD", "snapshot", cwd=self.repo)
        self.assertEqual(base, "snapshot")
        self.assertEqual(
            collect_entries(target, base, cwd=self.repo),
            [("changed", "Since snapshot"), ("fixed", "Latest fix")],
        )

    def test_snapshot_rerun_has_no_changes(self):
        self.tag("snapshot")
        target, base = select_base("HEAD", "snapshot", cwd=self.repo)
        self.assertEqual(base, "snapshot")
        self.assertEqual(collect_entries(target, base, cwd=self.repo), [])

    def test_unreachable_snapshot_falls_back_to_stable_release(self):
        self.tag("v1.0.0")
        git("switch", "-c", "old-history", cwd=self.repo)
        self.commit(message("Other history", "added"))
        self.tag("snapshot")
        git("switch", "main", cwd=self.repo)
        self.commit(message("Current history", "fixed"))
        target, base = select_base("HEAD", "snapshot", cwd=self.repo)
        self.assertEqual(base, "v1.0.0")
        self.assertEqual(collect_entries(target, base, cwd=self.repo), [("fixed", "Current history")])

    def test_merged_branch_entries_are_included_but_merge_summary_is_not(self):
        self.tag("v1.0.0")
        git("switch", "-c", "feature", cwd=self.repo)
        self.commit(message("Feature branch", "added"))
        git("switch", "main", cwd=self.repo)
        self.commit(message("Main fix", "fixed"))
        git("merge", "--no-ff", "feature", "-m", message("Merge summary", "added"), cwd=self.repo)
        target, base = select_base("HEAD", "snapshot", cwd=self.repo)
        self.assertCountEqual(
            collect_entries(target, base, cwd=self.repo),
            [("added", "Feature branch"), ("fixed", "Main fix")],
        )

    def test_filtering_deduplication_and_chinese(self):
        self.tag("v1.0.0")
        self.commit(message("\u4f18\u5316\u65e5\u5fd7\u9605\u8bfb\u4f53\u9a8c", "changed"))
        self.commit(message("\u4f18\u5316\u65e5\u5fd7\u9605\u8bfb\u4f53\u9a8c", "changed"))
        self.commit(message("Hidden detail", "none"))
        self.commit("fix(app): Unmarked legacy\n")
        target, base = select_base("HEAD", "snapshot", cwd=self.repo)
        self.assertEqual(
            collect_entries(target, base, cwd=self.repo),
            [("changed", "\u4f18\u5316\u65e5\u5fd7\u9605\u8bfb\u4f53\u9a8c")],
        )

    def test_mismatched_or_nonstable_release_tag_fails(self):
        self.tag("v1.0.0")
        self.commit(message("Later", "added"))
        for tag in ("v1.0.0", "snapshot", "v2.0.0-rc.1"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                select_base("HEAD", "release", tag, cwd=self.repo)
        with self.assertRaises(subprocess.CalledProcessError):
            select_base("missing-ref", "snapshot", cwd=self.repo)


if __name__ == "__main__":
    unittest.main()
