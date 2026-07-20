#!/usr/bin/env python3
"""Crop walk (6 frames) and summon (8 frames) each to their own shared content box
(so neither set jitters internally), then pad both sets into the SAME square canvas
and resize to the same final size. Necessary because GameApp.scala's
applyNecromancerAnimation only swaps `anim.textures` on the same AnimatedSprite —
width/height are set once at creation and never revisited — so if walk and summon had
different source aspect ratios, the character would visibly stretch differently
depending on which set is currently showing.
"""
import os
from PIL import Image

PAD = 14
FINAL = 112

def main():
    here = os.path.dirname(os.path.abspath(__file__))
    sets = {"walk": 6, "summon": 8}
    boxes = {}
    frame_sets = {}
    for name, n in sets.items():
        frames = [Image.open(os.path.join(here, f"{name}-{i:02d}.png")).convert("RGBA") for i in range(n)]
        frame_sets[name] = frames
        bs = [f.getbbox() for f in frames]
        l = min(b[0] for b in bs); t = min(b[1] for b in bs)
        r = max(b[2] for b in bs); b = max(b[3] for b in bs)
        l, t = max(0, l - PAD), max(0, t - PAD)
        r, b = min(frames[0].width, r + PAD), min(frames[0].height, b + PAD)
        boxes[name] = (l, t, r, b)
        print(name, "content box", boxes[name], "size", (r - l, b - t))

    for name, n in sets.items():
        l, t, r, b = boxes[name]
        w, h = r - l, b - t
        side = max(w, h)
        img_w, img_h = frame_sets[name][0].size
        new_l = max(0, min(img_w - side, l - (side - w) // 2))
        new_t = max(0, min(img_h - side, t - (side - h) // 2))
        for i, f in enumerate(frame_sets[name]):
            cropped = f.crop((new_l, new_t, new_l + side, new_t + side))
            resized = cropped.resize((FINAL, FINAL), Image.LANCZOS)
            out = os.path.join(here, "final", f"{name}-{i:02d}.png")
            os.makedirs(os.path.dirname(out), exist_ok=True)
            resized.save(out)
    print(f"wrote {sum(sets.values())} frames at {FINAL}x{FINAL} to final/")

if __name__ == "__main__":
    main()
