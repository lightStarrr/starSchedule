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
    r"^(feat|fix|docs|style|refactor|perf|test|chore)\([^()\r\n]+\)!?:[ \t]+(.+)$",
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


def is_ancestor(ancestor, descendant, cwd=None):
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", ancestor, descendant],
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode == 0:
        return True
    if result.returncode == 1:
        return False
    raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))


def merge_base(first, second, cwd=None):
    result = subprocess.run(
        ["git", "merge-base", first, second],
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        encoding="utf-8",
    )
    if result.returncode == 0:
        return result.stdout.strip()
    if result.returncode == 1:
        return None
    raise RuntimeError(result.stderr)


def select_base(ref, mode, tag=None, cwd=None):
    target = git("rev-parse", "--verify", "--end-of-options", f"{ref}^{{commit}}", cwd=cwd).strip()
    limit = None
    has_snapshot = False
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
        has_snapshot = True
        if is_ancestor("refs/tags/snapshot", target, cwd=cwd):
            return target, "snapshot"

    candidates = []
    for name in git("tag", "--list", cwd=cwd).splitlines():
        match = VERSION.fullmatch(name)
        if not match:
            continue
        version = tuple(map(int, match.groups()))
        if limit is not None and version >= limit:
            continue
        if is_ancestor(f"refs/tags/{name}", target, cwd=cwd):
            candidates.append((version, name))
    stable_base = max(candidates)[1] if candidates else None

    if has_snapshot:
        common = merge_base("refs/tags/snapshot", target, cwd=cwd)
        if common is not None:
            if stable_base is None:
                print(
                    "Previous snapshot diverged (force-push/rebase); using patch-aware incremental diff from snapshot.",
                    file=sys.stderr,
                )
                return target, "snapshot"
            stable_commit = git(
                "rev-parse", "--verify", "--end-of-options", f"refs/tags/{stable_base}^{{commit}}",
                cwd=cwd,
            ).strip()
            # 只要旧 snapshot 与当前分支的共同祖先严格晚于最近正式版标签，说明是当前开发周期内的强推/改写历史，
            # 继续以 snapshot 为基线配合 --cherry-pick 增量对比，避免退回正式版把所有历史提交全列出来。
            if common != stable_commit and is_ancestor(stable_commit, common, cwd=cwd):
                print(
                    "Previous snapshot diverged after latest release (force-push/rebase); using patch-aware incremental diff from snapshot.",
                    file=sys.stderr,
                )
                return target, "snapshot"
        print(
            "Previous snapshot is not an ancestor; falling back to a stable release baseline.",
            file=sys.stderr,
        )
    return target, stable_base


def collect_entries(target, base=None, cwd=None):
    if base:
        base_ref = f"refs/tags/{base}"
        if is_ancestor(base_ref, target, cwd=cwd):
            args = [
                "log", "--no-merges", "--reverse", "--topo-order", "--encoding=UTF-8",
                "--format=%B%x00", f"{base_ref}..{target}", "--",
            ]
        else:
            # 强推/rebase 后用对称差 + --cherry-pick --right-only，只提取当前分支新增或补丁发生变化的提交。
            args = [
                "log", "--no-merges", "--reverse", "--topo-order", "--cherry-pick",
                "--right-only", "--encoding=UTF-8",
                "--format=%B%x00", f"{base_ref}...{target}", "--",
            ]
    else:
        args = [
            "log", "--no-merges", "--reverse", "--topo-order", "--encoding=UTF-8",
            "--format=%B%x00", target, "--",
        ]
    messages = git(*args, cwd=cwd)
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
