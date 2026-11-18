#!/usr/bin/env python3
"""比较同机官方截图与 EmojiWallpaperRenderTest 的 125 张输出。

需要 Pillow、numpy。仅比较截图中无控件遮挡的绿色壁纸区域，不比较颜色。
官方文件名 official-{pattern}-{00..24}.png；输出文件名 {pattern}-{00..24}.png。
"""

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

PATTERNS = ("mosaic", "lotus", "stack", "scatter", "prism")


def dilate(mask: np.ndarray) -> np.ndarray:
    # 5×5 膨胀表示水平、竖直各允许 2px 偏差（Chebyshev 距离）。
    return np.asarray(Image.fromarray(mask).convert("L").filter(ImageFilter.MaxFilter(5))) > 0


def compare(reference: Path, rendered: Path, output: Path, top: int, bottom: int) -> None:
    output.mkdir(parents=True, exist_ok=True)
    results = {}
    for pattern in PATTERNS:
        scores = []
        for step in range(25):
            with Image.open(reference / f"official-{pattern}-{step:02}.png") as image:
                official = np.array(image.convert("RGB"))
            with Image.open(rendered / f"{pattern}-{step:02}.png") as image:
                actual = np.array(image.convert("RGB"))
            if official.shape != actual.shape or not 0 <= top < bottom <= actual.shape[0]:
                raise ValueError(f"{pattern}/{step}: 图片尺寸或比较区域不匹配")
            official, actual = official[top:bottom], actual[top:bottom]
            # 此参考配色的轮廓比背景更亮；逐行中位数抵消背景轻微渐变。
            ref = official[:, :, 2].astype(float) > np.median(official[:, :, 2], axis=1)[:, None] + 15
            test = actual[:, :, 0] > 64
            if not ref.any() or not test.any():
                raise ValueError(f"{pattern}/{step}: 空轮廓，请检查配色和截图区域")
            scores.append({
                "step": step,
                "iou": float((ref & test).sum() / (ref | test).sum()),
                "recall2px": float((ref & dilate(test)).sum() / ref.sum()),
                "precision2px": float((test & dilate(ref)).sum() / test.sum()),
            })
            if step in (0, 12, 24):
                height, width = official.shape[:2]
                pair = Image.new("RGB", (width, height * 2))
                pair.paste(Image.fromarray(official), (0, 0))
                pair.paste(Image.fromarray(actual), (0, height))
                pair.save(output / f"compare-{pattern}-{step:02}.png")
        results[pattern] = scores
        ranges = {
            key: [round(min(s[key] for s in scores), 6), round(max(s[key] for s in scores), 6)]
            for key in ("iou", "recall2px", "precision2px")
        }
        print(pattern, ranges, flush=True)
    (output / "comparison.json").write_text(json.dumps(results, indent=2), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("rendered", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--top", type=int, default=400)
    parser.add_argument("--bottom", type=int, default=1230)
    args = parser.parse_args()
    compare(args.reference, args.rendered, args.output, args.top, args.bottom)
