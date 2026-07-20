#!/usr/bin/env python3
"""Crop each direction to its own content (shared across its 10 frames, so that
direction's walk cycle doesn't jitter), then pad every direction into the SAME square
canvas and resize all four to the same final square size. Square + identical across
directions matters here: PIXI's AnimatedSprite forces width=size;height=size regardless
of the source texture's aspect ratio (see newAnimatedSprite in GameApp.scala), so a
non-square or per-direction-different-aspect source would visibly stretch/squish
differently depending on which way the zombie is facing. Goblin's directional frames
are all exactly 96x96 for the same reason — this matches that convention.
"""
import os
from PIL import Image

DIRS = ["front", "back", "left", "right"]
FRAMES = 10
PAD = 14
FINAL = 96

def main():
    here = os.path.dirname(os.path.abspath(__file__))
    boxes = {}
    frame_sets = {}
    for d in DIRS:
        frames = [Image.open(os.path.join(here, f"{d}-walk-{i:02d}.png")).convert("RGBA") for i in range(FRAMES)]
        frame_sets[d] = frames
        bs = [f.getbbox() for f in frames]
        l = min(b[0] for b in bs); t = min(b[1] for b in bs)
        r = max(b[2] for b in bs); b = max(b[3] for b in bs)
        l, t = max(0, l - PAD), max(0, t - PAD)
        r, b = min(frames[0].width, r + PAD), min(frames[0].height, b + PAD)
        boxes[d] = (l, t, r, b)
        print(d, "content box", boxes[d], "size", (r - l, b - t))

    for d in DIRS:
        l, t, r, b = boxes[d]
        w, h = r - l, b - t
        side = max(w, h)
        # center the (possibly non-square) content box within a `side`x`side` square,
        # clamped so it doesn't run off the source canvas edge
        img_w, img_h = frame_sets[d][0].size
        new_l = max(0, min(img_w - side, l - (side - w) // 2))
        new_t = max(0, min(img_h - side, t - (side - h) // 2))
        for i, f in enumerate(frame_sets[d]):
            cropped = f.crop((new_l, new_t, new_l + side, new_t + side))
            resized = cropped.resize((FINAL, FINAL), Image.LANCZOS)
            out = os.path.join(here, "final", f"{d}-walk-{i:02d}.png")
            os.makedirs(os.path.dirname(out), exist_ok=True)
            resized.save(out)
    print(f"wrote {len(DIRS)*FRAMES} frames at {FINAL}x{FINAL} to final/")

if __name__ == "__main__":
    main()
