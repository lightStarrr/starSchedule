"""Generate public release notes from explicit Git commit trailers."""

import argparse
import html
import json
import re
import subprocess
import sys
from pathlib import Path


VERSION = re.compile(r"v(\d+)\.(\d+)\.(\d+)")
SUBJECT = re.compile(
    r"^(feat|fix|docs|style|refactor|test|chore)\([^()\r\n]+\)!?:[ \t]+(.+)$",
)
GROUPS = (
    ("added", "\u65b0\u589e"),
    ("changed", "\u4f18\u5316"),
    ("fixed", "\u4fee\u590d"),
)


def git(*args, cwd=None, input_text=None):
    return subprocess.run(
        ["git", *args],
        cwd=cwd,
        input=input_text,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        encoding="utf-8",
        check=True,
    ).stdout


def parse_message(message, cwd=None):
    trailers = git("interpret-trailers", "--parse", cwd=cwd, input_text=message)
    markers = [
        line.partition(":")[2].strip()
        for line in trailers.splitlines()
        if line.startswith("Changelog:")
    ]
    if not markers:
        return None
    if len(markers) != 1 or markers[0] not in {"added", "changed", "fixed", "none"}:
        raise ValueError("Expected exactly one Changelog: added|changed|fixed|none trailer")
    if markers[0] == "none":
        return None
    match = SUBJECT.fullmatch(message.splitlines()[0])
    if not match:
        raise ValueError("Public changelog entries require a type(scope): description subject")
    return markers[0], match.group(2).strip()


def select_base(ref, mode, tag=None, cwd=None):
    target = git("rev-parse", "--verify", "--end-of-options", f"{ref}^{{commit}}", cwd=cwd).strip()
    limit = None
    if mode == "release":
        match = VERSION.fullmatch(tag or "")
        if not match:
            raise ValueError("Formal releases require a vMAJOR.MINOR.PATCH tag")
        limit = tuple(map(int, match.groups()))
        tagged = git(
            "rev-parse", "--verify", "--end-of-options", f"refs/tags/{tag}^{{commit}}", cwd=cwd,
        ).strip()
        if tagged != target:
            raise ValueError("Release tag does not point to the requested commit")
    elif "snapshot" in git("tag", "--list", "snapshot", cwd=cwd).splitlines():
        result = subprocess.run(
            ["git", "merge-base", "--is-ancestor", "refs/tags/snapshot", target],
            cwd=cwd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if result.returncode == 0:
            return target, "snapshot"
        if result.returncode != 1:
            raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
        print(
            "Previous snapshot is not an ancestor; falling back to a stable release baseline.",
            file=sys.stderr,
        )
    candidates = []
    for name in git("tag", "--list", cwd=cwd).splitlines():
        match = VERSION.fullmatch(name)
        if not match:
            continue
        version = tuple(map(int, match.groups()))
        if limit is not None and version >= limit:
            continue
        result = subprocess.run(
            ["git", "merge-base", "--is-ancestor", f"refs/tags/{name}", target],
            cwd=cwd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if result.returncode == 0:
            candidates.append((version, name))
        elif result.returncode != 1:
            raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    return target, max(candidates)[1] if candidates else None


def collect_entries(target, base=None, cwd=None):
    revision = f"refs/tags/{base}..{target}" if base else target
    messages = git(
        "log", "--no-merges", "--reverse", "--topo-order", "--encoding=UTF-8",
        "--format=%B%x00", revision, "--", cwd=cwd,
    )
    entries = []
    seen = set()
    for record in messages.split("\0"):
        message = record.strip("\r\n")
        if not message:
            continue
        try:
            entry = parse_message(message, cwd=cwd)
        except ValueError as error:
            raise ValueError(f"{message.splitlines()[0]}: {error}") from error
        if entry is not None and entry not in seen:
            entries.append(entry)
            seen.add(entry)
    return entries


def markdown_text(text):
    return re.sub(r"([\\`*_{}\[\]()#+.!|~-])", r"\\\1", html.escape(text, quote=False))


def structured_changelog(entries):
    return {
        kind: [text for category, text in entries if category == kind]
        for kind, _ in GROUPS
    }


def render_changelog(entries):
    lines = ["## \u66f4\u65b0\u65e5\u5fd7", ""]
    for kind, title in GROUPS:
        descriptions = [text for category, text in entries if category == kind]
        if descriptions:
            lines.extend([f"### {title}", ""])
            lines.extend(f"- {markdown_text(text)}" for text in descriptions)
            lines.append("")
    if not entries:
        lines.extend(["\u672c\u7248\u672c\u6682\u65e0\u66f4\u65b0\u8bf4\u660e\u3002", ""])
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("release", "snapshot"), required=True)
    parser.add_argument("--ref", default="HEAD")
    parser.add_argument("--tag")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--json-output", type=Path)
    args = parser.parse_args()
    try:
        target, base = select_base(args.ref, args.mode, args.tag)
        entries = collect_entries(target, base)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(render_changelog(entries), encoding="utf-8")
        if args.json_output is not None:
            args.json_output.parent.mkdir(parents=True, exist_ok=True)
            args.json_output.write_text(
                json.dumps(structured_changelog(entries), ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
        print(f"Changelog range: {base or '(initial commit)'}..{target}", file=sys.stderr)
    except (ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Changelog generation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
