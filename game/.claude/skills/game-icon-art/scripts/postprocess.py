#!/usr/bin/env python3
"""Trim a rendered SVG screenshot to its alpha bounding box, downscale it to a
target size, and emit small preview copies for the recognizability check.

Usage:
  python3 postprocess.py <input.png> <output.png> [--max-dim 520] [--pad 14] [--previews 64,45,32]

Requires Pillow (`pip install pillow` if missing — not preinstalled in this environment).
"""
import argparse
import os
from PIL import Image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input")
    parser.add_argument("output")
    parser.add_argument("--max-dim", type=int, default=520,
                         help="Long-edge size of the final asset. Check sibling files in "
                              "game/assets/ for the right ballpark instead of trusting the default.")
    parser.add_argument("--pad", type=int, default=14,
                         help="Transparent padding (px, at render resolution) kept around the trimmed art.")
    parser.add_argument("--previews", default="64,45,32",
                         help="Comma-separated preview sizes (long edge, px) for the recognizability check.")
    args = parser.parse_args()

    im = Image.open(args.input)
    if im.mode != "RGBA":
        im = im.convert("RGBA")

    bbox = im.getbbox()
    if bbox is None:
        raise SystemExit(f"{args.input} is fully transparent — nothing to trim")
    l, t, r, b = bbox
    l = max(0, l - args.pad)
    t = max(0, t - args.pad)
    r = min(im.width, r + args.pad)
    b = min(im.height, b + args.pad)
    cropped = im.crop((l, t, r, b))

    scale = args.max_dim / max(cropped.size)
    final = cropped.resize((max(1, round(cropped.width * scale)), max(1, round(cropped.height * scale))), Image.LANCZOS)
    final.save(args.output)
    print(f"wrote {args.output} {final.size}")

    stem, ext = os.path.splitext(args.output)
    for size in (int(s) for s in args.previews.split(",") if s.strip()):
        pscale = size / max(final.size)
        preview = final.resize((max(1, round(final.width * pscale)), max(1, round(final.height * pscale))), Image.LANCZOS)
        preview_path = f"{stem}_preview_{size}{ext}"
        preview.save(preview_path)
        print(f"wrote {preview_path} {preview.size}")


if __name__ == "__main__":
    main()
