#!/usr/bin/env python3
"""Assemble rendered zombie walk-cycle frames into the final game sprite set + a demo GIF.

Unlike a single static icon (see the game-icon-art skill's postprocess.py, which trims
each PNG independently to its own alpha bounding box), animation frames must share ONE
crop rectangle and ONE final size — trimming each frame to its own content box would
shift the character around and make the sprite jitter in place instead of walking
smoothly. This script computes the union of all frames' bounding boxes, crops every
frame to that same rectangle, and resizes them all identically.

Usage (run after `generate.py` + rendering each walk-NN.svg with the game-icon-art
skill's render.js):
  python3 assemble.py <rendered_dir> <out_dir> [--frames 10] [--target-height 128]

<rendered_dir> should contain walk-00.png .. walk-NN.png at a large render resolution
(e.g. 600px tall, per render.js's usual invocation). Writes walk-00.png..walk-NN.png at
the final game resolution to <out_dir>, plus a walk.gif preview (3x upscaled, dark
background) in <out_dir> for reviewing the animation and for embedding in lore docs.
"""
import argparse
import os
from PIL import Image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("rendered_dir")
    parser.add_argument("out_dir")
    parser.add_argument("--frames", type=int, default=10)
    parser.add_argument("--target-height", type=int, default=128,
                         help="Final frame height in px. Match sibling animated units "
                              "(goblin/wolf/zombie are all in the 64-128px range) rather "
                              "than the much larger building-icon sizes.")
    parser.add_argument("--pad", type=int, default=14)
    parser.add_argument("--gif-scale", type=int, default=3)
    parser.add_argument("--gif-duration-ms", type=int, default=90)
    args = parser.parse_args()

    frames = [
        Image.open(os.path.join(args.rendered_dir, f"walk-{i:02d}.png")).convert("RGBA")
        for i in range(args.frames)
    ]
    boxes = [f.getbbox() for f in frames]
    l = max(0, min(b[0] for b in boxes) - args.pad)
    t = max(0, min(b[1] for b in boxes) - args.pad)
    r = min(frames[0].width, max(b[2] for b in boxes) + args.pad)
    b = min(frames[0].height, max(b[3] for b in boxes) + args.pad)

    os.makedirs(args.out_dir, exist_ok=True)
    finals = []
    for i, f in enumerate(frames):
        cropped = f.crop((l, t, r, b))
        scale = args.target_height / cropped.height
        resized = cropped.resize(
            (round(cropped.width * scale), round(cropped.height * scale)), Image.LANCZOS
        )
        resized.save(os.path.join(args.out_dir, f"walk-{i:02d}.png"))
        finals.append(resized)
    print(f"wrote {len(finals)} frames at {finals[0].size} to {args.out_dir}")

    big = [f.resize((f.width * args.gif_scale, f.height * args.gif_scale), Image.LANCZOS) for f in finals]
    bg = Image.new("RGBA", big[0].size, (30, 30, 40, 255))
    composited = []
    for f in big:
        c = bg.copy()
        c.paste(f, (0, 0), f)
        composited.append(c.convert("RGB"))
    gif_path = os.path.join(args.out_dir, "walk.gif")
    composited[0].save(
        gif_path, save_all=True, append_images=composited[1:],
        duration=args.gif_duration_ms, loop=0, disposal=2,
    )
    print(f"wrote {gif_path} ({len(composited)} frames)")


if __name__ == "__main__":
    main()
