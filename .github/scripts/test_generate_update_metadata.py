import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from generate_update_metadata import build_metadata, load_changelog, parse_version_code


FULL_SHA = "0123456789abcdef0123456789abcdef01234567"
GENERATED_AT = "2024-06-01T12:34:56Z"


class MetadataTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "app-release.apk"
        self.payload = bytes(range(256)) * 40
        self.apk.write_bytes(self.payload)
        self.changelog_path = self.root / "changelog.json"
        self.changelog = {
            "added": ["新功能 <b>*标记*</b>"],
            "changed": [],
            "fixed": ["修复崩溃"],
        }
        self.changelog_path.write_text(
            json.dumps(self.changelog, ensure_ascii=False), encoding="utf-8"
        )

    def metadata(self, **overrides):
        kwargs = {
            "channel": "stable",
            "version_name": "1.2.0",
            "version_code": 120,
            "commit_sha": FULL_SHA,
            "apk_path": self.apk,
            "changelog": self.changelog,
            "generated_at": GENERATED_AT,
        }
        kwargs.update(overrides)
        return build_metadata(**kwargs)

    def test_stable_metadata_matches_schema_and_apk_bytes(self):
        result = self.metadata()
        self.assertEqual(
            result,
            {
                "schemaVersion": 1,
                "channel": "stable",
                "versionName": "1.2.0",
                "versionCode": 120,
                "commitSha": FULL_SHA,
                "generatedAt": GENERATED_AT,
                "apk": {
                    "name": "app-release.apk",
                    "size": len(self.payload),
                    "sha256": hashlib.sha256(self.payload).hexdigest(),
                },
                "changelog": self.changelog,
            },
        )

    def test_snapshot_version_requires_commit_prefix(self):
        result = self.metadata(
            channel="snapshot", version_name=f"1.2.0-snapshot.{FULL_SHA[:7]}"
        )
        self.assertEqual(result["channel"], "snapshot")
        self.assertEqual(result["versionName"], f"1.2.0-snapshot.{FULL_SHA[:7]}")
        for name in (
            "1.2.0",
            "1.2.0-snapshot.",
            f"1.2.0-snapshot.{FULL_SHA[:7][::-1]}",
            f"1.2.0-snapshot.{'f' * 7}",
            "1.2.0-beta.1",
        ):
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.metadata(channel="snapshot", version_name=name)

    def test_stable_rejects_snapshot_or_malformed_names(self):
        for name in ("1.2", "1.2.0.4", "v1.2.0", f"1.2.0-snapshot.{FULL_SHA[:7]}", "1.2.x"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.metadata(version_name=name)

    def test_channel_is_limited_to_stable_and_snapshot(self):
        with self.assertRaises(ValueError):
            self.metadata(channel="beta")

    def test_version_code_must_be_positive_android_integer(self):
        for code in (0, -1, 2_100_000_001, "120", 1.5, True, None):
            with self.subTest(code=code), self.assertRaises(ValueError):
                self.metadata(version_code=code)
        self.assertEqual(self.metadata(version_code=2_100_000_000)["versionCode"], 2_100_000_000)

    def test_parse_version_code_accepts_decimal_text_only(self):
        self.assertEqual(parse_version_code("120"), 120)
        self.assertEqual(parse_version_code("2100000000"), 2_100_000_000)
        for text in ("", "abc", "12.0", "-1", "+1", "0", "2100000001", " 120", "120 "):
            with self.subTest(text=text), self.assertRaises(ValueError):
                parse_version_code(text)

    def test_commit_sha_must_be_full_lowercase_hex(self):
        for sha in (
            FULL_SHA[:7],
            FULL_SHA.upper(),
            FULL_SHA[:-1],
            FULL_SHA + "0",
            "g" * 40,
            "",
        ):
            with self.subTest(sha=sha), self.assertRaises(ValueError):
                self.metadata(commit_sha=sha)

    def test_apk_must_be_a_nonempty_apk_file(self):
        for path in (
            self.root / "missing.apk",
            self.root,
            self._write("notes.txt", b"payload"),
            self._write("empty.apk", b""),
            self._write(".apk", b"payload"),
        ):
            with self.subTest(path=path.name), self.assertRaises(ValueError):
                self.metadata(apk_path=path)

    def _write(self, name, content):
        path = self.root / name
        path.write_bytes(content)
        return path

    def test_changelog_requires_exact_groups_of_strings(self):
        bad = [
            {},
            {"added": [], "changed": []},
            {"added": [], "changed": [], "fixed": [], "other": []},
            {"added": "text", "changed": [], "fixed": []},
            {"added": [1], "changed": [], "fixed": []},
            {"added": [None], "changed": [], "fixed": []},
            {"added": [{"text": "x"}], "changed": [], "fixed": []},
            ["added"],
        ]
        for changelog in bad:
            with self.subTest(changelog=changelog), self.assertRaises(ValueError):
                self.metadata(changelog=changelog)

    def test_load_changelog_reads_utf8_and_rejects_bad_files(self):
        self.assertEqual(load_changelog(self.changelog_path), self.changelog)
        self.changelog_path.write_text("not json", encoding="utf-8")
        with self.assertRaises(ValueError):
            load_changelog(self.changelog_path)
        self.changelog_path.write_bytes(b"\xff\xfe{}")
        with self.assertRaises(ValueError):
            load_changelog(self.changelog_path)
        with self.assertRaises(ValueError):
            load_changelog(self.root / "missing.json")

    def test_generated_at_defaults_to_utc_iso8601(self):
        result = self.metadata(generated_at=None)
        stamp = result["generatedAt"]
        self.assertTrue(stamp.endswith("Z"), stamp)
        self.assertIn("T", stamp)

    def test_changelog_text_keeps_non_ascii_and_markdown_characters(self):
        text = "新增 *重要* <b>功能</b> & \"引号\""
        result = self.metadata(changelog={"added": [text], "changed": [], "fixed": []})
        self.assertEqual(result["changelog"]["added"], [text])


class CliTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.script = Path(__file__).resolve().parent / "generate_update_metadata.py"
        self.apk = self.root / "starSchedule-v1.2.0.apk"
        self.apk.write_bytes(b"signed bytes")
        changelog = self.root / "changelog.json"
        changelog.write_text(
            json.dumps({"added": ["新功能"], "changed": [], "fixed": []}, ensure_ascii=False),
            encoding="utf-8",
        )
        self.output = self.root / "update.json"

    def run_cli(self, *extra):
        return subprocess.run(
            [
                sys.executable, "-B", str(self.script),
                "--channel", "stable",
                "--version-name", "1.2.0",
                "--version-code", "120",
                "--commit-sha", FULL_SHA,
                "--apk", str(self.apk),
                "--changelog", str(self.root / "changelog.json"),
                "--output", str(self.output),
                *extra,
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            encoding="utf-8",
        )

    def test_cli_writes_schema_v1_update_json(self):
        result = self.run_cli()
        self.assertEqual(result.returncode, 0, result.stderr)
        metadata = json.loads(self.output.read_text(encoding="utf-8"))
        self.assertEqual(metadata["schemaVersion"], 1)
        self.assertEqual(metadata["channel"], "stable")
        self.assertEqual(metadata["apk"]["name"], "starSchedule-v1.2.0.apk")
        self.assertEqual(
            metadata["apk"]["sha256"], hashlib.sha256(b"signed bytes").hexdigest()
        )

    def test_cli_rejects_invalid_input_with_nonzero_exit(self):
        for extra in (
            ("--version-code", "abc"),
            ("--version-name", "1.2.0-snapshot.0123456"),
            ("--commit-sha", "0123456"),
        ):
            args = []
            skip = extra[0]
            base = [
                ("--channel", "stable"),
                ("--version-name", "1.2.0"),
                ("--version-code", "120"),
                ("--commit-sha", FULL_SHA),
            ]
            for flag, value in base:
                args.extend(extra if flag == skip else (flag, value))
            result = subprocess.run(
                [
                    sys.executable, "-B", str(self.script), *args,
                    "--apk", str(self.apk),
                    "--changelog", str(self.root / "changelog.json"),
                    "--output", str(self.output),
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                encoding="utf-8",
            )
            self.assertNotEqual(result.returncode, 0, extra)


if __name__ == "__main__":
    unittest.main()
