#!/usr/bin/env python3
"""生成 app 用的表情分组资产 emoji-groups-15.1.txt。

用法：
    python tools/emoji/generate_emoji_groups.py [emoji-test.txt 本地路径]

不带参数时从 Unicode 官方下载 15.1 的 emoji-test.txt。

过滤规则（与输出文件头注释一致）：
- 只取 fully-qualified 行；
- 跳过 Component 组；
- 排除任何含肤色修饰符 U+1F3FB–U+1F3FF 的序列（黑白字体不区分肤色）；
- 保持文件原有顺序。

输出格式：每组一行头 ``@<id>``，之后每行一个表情字符串；UTF-8、LF。
"""

import sys
import urllib.request
from pathlib import Path

EMOJI_TEST_URL = "https://unicode.org/Public/emoji/15.1/emoji-test.txt"
UNICODE_VERSION = "15.1"
OUTPUT = Path("app/src/main/assets/emoji/emoji-groups-15.1.txt")

GROUP_IDS = {
    "Smileys & Emotion": "smileys",
    "People & Body": "people",
    "Animals & Nature": "animals",
    "Food & Drink": "food",
    "Travel & Places": "travel",
    "Activities": "activities",
    "Objects": "objects",
    "Symbols": "symbols",
    "Flags": "flags",
}

# 肤色修饰符：黑白字体不区分肤色，带肤色的序列统统排除。
SKIN_TONES = set(range(0x1F3FB, 0x1F400))


def load_source(local_path: str | None) -> str:
    if local_path:
        return Path(local_path).read_text(encoding="utf-8")
    with urllib.request.urlopen(EMOJI_TEST_URL, timeout=60) as response:
        return response.read().decode("utf-8")


def parse(text: str) -> dict[str, list[str]]:
    groups: dict[str, list[str]] = {group_id: [] for group_id in GROUP_IDS.values()}
    current: str | None = None
    in_component = False

    for line in text.splitlines():
        line = line.strip()
        if line.startswith("# group:"):
            group_name = line.split(":", 1)[1].strip()
            in_component = group_name == "Component"
            current = GROUP_IDS.get(group_name)
            continue
        if not line or line.startswith("#") or current is None or in_component:
            continue

        fields = line.split("#", 1)[0].strip().split(";")
        if len(fields) != 2 or fields[1].strip() != "fully-qualified":
            continue
        codepoints = [int(part, 16) for part in fields[0].split()]
        if any(cp in SKIN_TONES for cp in codepoints):
            continue
        groups[current].append("".join(chr(cp) for cp in codepoints))

    return groups


def render(groups: dict[str, list[str]]) -> str:
    lines = [
        "# Source: " + EMOJI_TEST_URL,
        "# Unicode Emoji " + UNICODE_VERSION,
        "# Only fully-qualified sequences; Component group skipped;",
        "# sequences containing skin-tone modifiers U+1F3FB-U+1F3FF excluded.",
        "# Group order and emoji order follow the source file.",
    ]
    for group_id, emojis in groups.items():
        lines.append(f"@{group_id}")
        lines.extend(emojis)
    return "\n".join(lines) + "\n"


def main() -> None:
    local_path = sys.argv[1] if len(sys.argv) > 1 else None
    groups = parse(load_source(local_path))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(render(groups), encoding="utf-8", newline="\n")
    total = 0
    for group_id, emojis in groups.items():
        total += len(emojis)
        print(f"{group_id}: {len(emojis)}")
    print(f"total: {total}")
    print(f"written: {OUTPUT} ({OUTPUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
