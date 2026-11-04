"""Combine APK facts and the structured changelog into a schema v1 update.json."""

import argparse
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path


STABLE_VERSION = re.compile(r"\d+\.\d+\.\d+")
SNAPSHOT_VERSION = re.compile(r"\d+\.\d+\.\d+-snapshot\.([0-9a-f]+)")
FULL_SHA = re.compile(r"[0-9a-f]{40}")
VERSION_CODE_TEXT = re.compile(r"[0-9]+")
MAX_VERSION_CODE = 2_100_000_000
CHANGELOG_GROUPS = ("added", "changed", "fixed")


def parse_version_code(text):
    if not VERSION_CODE_TEXT.fullmatch(text):
        raise ValueError("versionCode must be a plain decimal integer")
    value = int(text)
    if not 1 <= value <= MAX_VERSION_CODE:
        raise ValueError(f"versionCode must be between 1 and {MAX_VERSION_CODE}")
    return value


def check_version_code(value):
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError("versionCode must be an integer")
    if not 1 <= value <= MAX_VERSION_CODE:
        raise ValueError(f"versionCode must be between 1 and {MAX_VERSION_CODE}")
    return value


def check_version_name(channel, version_name, commit_sha):
    if channel == "stable":
        if not STABLE_VERSION.fullmatch(version_name):
            raise ValueError("stable releases require a MAJOR.MINOR.PATCH versionName")
    elif channel == "snapshot":
        match = SNAPSHOT_VERSION.fullmatch(version_name)
        if match is None or not commit_sha.startswith(match.group(1)):
            raise ValueError(
                "snapshot releases require a MAJOR.MINOR.PATCH-snapshot.<sha> versionName "
                "whose sha is a prefix of commitSha"
            )
    else:
        raise ValueError("channel must be 'stable' or 'snapshot'")


def check_changelog(data):
    if not isinstance(data, dict) or set(data) != set(CHANGELOG_GROUPS):
        raise ValueError("changelog must contain exactly the added, changed and fixed groups")
    for group in CHANGELOG_GROUPS:
        entries = data[group]
        if not isinstance(entries, list) or not all(
            isinstance(item, str) for item in entries
        ):
            raise ValueError(f"changelog group '{group}' must be an array of plain strings")
    return {group: list(data[group]) for group in CHANGELOG_GROUPS}


def load_changelog(path):
    try:
        text = Path(path).read_text(encoding="utf-8")
    except OSError as error:
        raise ValueError(f"cannot read changelog: {error}") from error
    return check_changelog(json.loads(text))


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def utc_now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def build_metadata(
    channel,
    version_name,
    version_code,
    commit_sha,
    apk_path,
    changelog,
    generated_at=None,
):
    if not FULL_SHA.fullmatch(commit_sha):
        raise ValueError("commitSha must be the full 40 lowercase hex characters")
    check_version_name(channel, version_name, commit_sha)
    check_version_code(version_code)
    changelog = check_changelog(changelog)
    apk = Path(apk_path)
    if not apk.is_file():
        raise ValueError(f"APK is missing or not a regular file: {apk}")
    if apk.name == ".apk" or not apk.name.endswith(".apk"):
        raise ValueError(f"APK file name must end with .apk: {apk.name}")
    size = apk.stat().st_size
    if size == 0:
        raise ValueError(f"APK is empty: {apk}")
    return {
        "schemaVersion": 1,
        "channel": channel,
        "versionName": version_name,
        "versionCode": version_code,
        "commitSha": commit_sha,
        "generatedAt": generated_at if generated_at is not None else utc_now(),
        "apk": {
            "name": apk.name,
            "size": size,
            "sha256": sha256_file(apk),
        },
        "changelog": changelog,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--channel", choices=("stable", "snapshot"), required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True)
    parser.add_argument("--commit-sha", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--changelog", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        metadata = build_metadata(
            channel=args.channel,
            version_name=args.version_name,
            version_code=parse_version_code(args.version_code),
            commit_sha=args.commit_sha,
            apk_path=args.apk,
            changelog=load_changelog(args.changelog),
        )
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(
            f"Update metadata: {metadata['channel']} {metadata['versionName']} "
            f"({metadata['versionCode']}) -> {args.output}",
            file=sys.stderr,
        )
    except (ValueError, OSError) as error:
        print(f"Update metadata generation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
