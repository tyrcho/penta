#!/usr/bin/env python3
"""Generate 10 SVG frames of a rigged cartoon zombie walk-shamble cycle."""
import math
import os

OUT = os.path.dirname(os.path.abspath(__file__))

# Darker, more horrific Death-faction palette: desaturated rot-green skin (still keys
# off the same green as the zombie hand accent in tomb.png) with purple-gray bruising
# (echoing tomb.png's stone and the --color-shadow lavender), near-black outline,
# dried-blood reds instead of a clean shirt-red, and grimy off-white bandage/shroud
# tatters instead of clean sneakers.
OUTLINE = "#0f130d"
SKIN = "#6a8462"
SKIN_SHADOW = "#4a6247"
SKIN_ROT = "#5c5470"
SKIN_HILITE = "#8aa480"
SHIRT = "#4a3038"
SHIRT_SHADOW = "#2e1c22"
SHIRT_HILITE = "#66424c"
BLOOD = "#5a1418"
BLOOD_DARK = "#3a0c10"
PANTS = "#3a3a42"
PANTS_SHADOW = "#232329"
HAIR = "#1c1a1a"
BANDAGE = "#cabfa8"
BANDAGE_SHADOW = "#a89c82"
BONE = "#ded2b8"

def leg(cx, hip, angle, mirror=1):
    # tattered trouser leg, torn hem, grimy bandage-wrapped foot instead of a clean shoe
    return f'''
  <g transform="translate({hip[0]},{hip[1]}) rotate({angle})">
    <path d="M -18,0 Q -23,58 -17,104 L -8,112 L -2,100 L 4,112 L 12,100 L 16,110 Q 23,58 18,0 Z"
          fill="{PANTS}" stroke="{OUTLINE}" stroke-width="7" stroke-linejoin="round"/>
    <path d="M -18,0 Q -23,58 -17,104 L -6,108 Q -8,54 -6,0 Z" fill="{PANTS_SHADOW}" opacity="0.6"/>
    <path d="M -10,40 L -16,52 M 8,60 L 15,70" stroke="{OUTLINE}" stroke-width="3.5" stroke-linecap="round" opacity="0.6"/>
    <ellipse cx="0" cy="120" rx="24" ry="13" fill="{BANDAGE}" stroke="{OUTLINE}" stroke-width="7"/>
    <path d="M -20,116 Q 0,124 20,116" fill="none" stroke="{BANDAGE_SHADOW}" stroke-width="4" opacity="0.8"/>
    <ellipse cx="0" cy="126" rx="24" ry="6" fill="{BLOOD_DARK}" opacity="0.55"/>
  </g>'''

def arm(shoulder, angle, clawed=True):
    # withered forearm with a jagged torn sleeve-cuff and clawed skeletal fingers
    return f'''
  <g transform="translate({shoulder[0]},{shoulder[1]}) rotate({angle})">
    <path d="M -15,0 Q -19,42 -13,80 L 13,80 Q 19,42 15,0 Z"
          fill="{SKIN}" stroke="{OUTLINE}" stroke-width="7" stroke-linejoin="round"/>
    <path d="M -15,0 Q -19,42 -13,80 L 0,80 Q -3,42 -3,0 Z" fill="{SKIN_SHADOW}" opacity="0.55"/>
    <path d="M -14,18 L -6,24 M 10,34 L 17,40" stroke="{OUTLINE}" stroke-width="3" stroke-linecap="round" opacity="0.55"/>
    <path d="M -15,4 L -22,-2 L -14,10 L -19,-6 L -11,12" fill="none" stroke="{OUTLINE}" stroke-width="4" stroke-linecap="round" opacity="0.7"/>
    <g transform="translate(0,84)">
      <circle cx="0" cy="0" r="13" fill="{SKIN_SHADOW}" stroke="{OUTLINE}" stroke-width="7"/>
      <path d="M -10,4 L -18,16 M -2,10 L -4,22 M 6,9 L 10,20 M 12,2 L 22,10"
            fill="none" stroke="{OUTLINE}" stroke-width="8" stroke-linecap="round"/>
      <path d="M -10,4 L -18,16 M -2,10 L -4,22 M 6,9 L 10,20 M 12,2 L 22,10"
            fill="none" stroke="{BONE}" stroke-width="4" stroke-linecap="round"/>
    </g>
  </g>'''

def frame(leg_l_a, leg_r_a, arm_l_a, arm_r_a, bob, tilt, glow=0.0):
    hip_l = (178, 248)
    hip_r = (222, 248)
    sh_l = (163, 172)
    sh_r = (237, 172)
    return f'''<svg id="art" viewBox="0 0 400 400" xmlns="http://www.w3.org/2000/svg">
  <ellipse cx="200" cy="366" rx="86" ry="14" fill="#000000" opacity="0.22"/>
  <g transform="translate(0,{bob}) rotate({tilt} 200 250)">
    {leg(200, hip_l, leg_l_a)}
    {leg(200, hip_r, leg_r_a)}
    <!-- torso: tattered burial shroud, jagged hem, not a clean garment -->
    <path d="M 158,150 Q 152,212 160,262 L 172,270 L 182,258 L 192,270 L 200,256
             L 208,270 L 218,258 L 228,270 L 240,262 Q 248,210 242,150 Q 200,130 158,150 Z"
          fill="{SHIRT}" stroke="{OUTLINE}" stroke-width="8" stroke-linejoin="round"/>
    <path d="M 158,150 Q 152,212 160,262 L 172,270 L 182,258 L 192,270 L 200,256 Q 194,200 190,150 Z"
          fill="{SHIRT_HILITE}" opacity="0.35"/>
    <path d="M 222,150 Q 248,210 240,150 L 242,150 Q 248,210 240,262 L 228,270 L 218,258
             L 208,270 L 200,256 Q 210,200 222,150 Z" fill="{SHIRT_SHADOW}" opacity="0.55"/>
    <!-- gaping torso wound: torn shroud edge revealing dark flesh and ribs -->
    <path d="M 196,168 Q 178,178 182,204 Q 186,224 208,230 Q 224,224 220,200 Q 216,176 196,168 Z"
          fill="{BLOOD_DARK}" stroke="{OUTLINE}" stroke-width="5" stroke-linejoin="round"/>
    <path d="M 190,186 L 212,182 M 189,198 L 213,196 M 192,212 L 210,212"
          fill="none" stroke="{BONE}" stroke-width="4" stroke-linecap="round" opacity="0.85"/>
    <path d="M 196,168 Q 178,178 182,204" fill="none" stroke="{BLOOD}" stroke-width="4" opacity="0.7"/>
    <!-- dried blood spatter down the shroud -->
    <circle cx="172" cy="240" r="5" fill="{BLOOD}" opacity="0.7"/>
    <circle cx="225" cy="248" r="4" fill="{BLOOD}" opacity="0.6"/>
    <path d="M 208,232 Q 206,246 210,258" stroke="{BLOOD}" stroke-width="4" fill="none" opacity="0.65" stroke-linecap="round"/>
    <!-- neck: withered, sinewy -->
    <path d="M 186,126 Q 184,142 188,154 L 212,154 Q 216,142 214,126 Z"
          fill="{SKIN_SHADOW}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
    <!-- head: gaunt but legible rounded silhouette (the hollowness reads through shading
         overlays below, not by deforming the outer outline into something unreadable) -->
    <g transform="translate(200,93)">
      <path d="M 0,-58 Q 40,-56 52,-18 Q 58,10 40,34 Q 20,52 0,52 Q -20,52 -40,34
               Q -58,10 -52,-18 Q -40,-56 0,-58 Z"
            fill="{SKIN}" stroke="{OUTLINE}" stroke-width="8" stroke-linejoin="round"/>
      <!-- gaunt cheek hollow + jaw shadow -->
      <path d="M -42,-4 Q -50,18 -32,28 Q -44,6 -42,-4 Z" fill="{SKIN_SHADOW}" opacity="0.55"/>
      <path d="M -18,28 Q 0,46 24,32 Q 20,50 -4,48 Q -22,44 -18,28 Z" fill="{SKIN_SHADOW}" opacity="0.5"/>
      <!-- purplish rot bruising patches -->
      <path d="M 26,-28 Q 42,-24 40,-8 Q 32,-4 24,-14 Q 20,-24 26,-28 Z" fill="{SKIN_ROT}" opacity="0.55"/>
      <path d="M -4,-40 Q 8,-42 10,-32 Q 2,-28 -6,-34 Z" fill="{SKIN_ROT}" opacity="0.4"/>
      <circle cx="20" cy="-24" r="16" fill="{SKIN_HILITE}" opacity="0.2"/>
      <!-- ear (behind hair) -->
      <circle cx="-48" cy="10" r="10" fill="{SKIN}" stroke="{OUTLINE}" stroke-width="6"/>
      <circle cx="-48" cy="10" r="4" fill="{SKIN_SHADOW}"/>
      <!-- hair: a handful of separate matted tufts along the crown, NOT one solid cap —
           each tuft is its own small closed shape so bare scalp shows between them. -->
      <path d="M -46,-30 Q -50,-48 -30,-52 Q -38,-38 -30,-30 Q -42,-30 -46,-30 Z"
            fill="{HAIR}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
      <path d="M -22,-48 Q -20,-60 -2,-58 Q -8,-46 -2,-38 Q -16,-40 -22,-48 Z"
            fill="{HAIR}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
      <path d="M 14,-54 Q 26,-58 36,-46 Q 26,-42 20,-46 Q 10,-46 14,-54 Z"
            fill="{HAIR}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
      <path d="M 34,-32 Q 46,-30 44,-16 Q 34,-16 30,-24 Q 28,-32 34,-32 Z"
            fill="{HAIR}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
      <!-- bald scalp gash between the tufts -->
      <path d="M -6,-44 Q 4,-40 2,-30" fill="none" stroke="{BLOOD}" stroke-width="3" stroke-linecap="round" opacity="0.85"/>
      <!-- eyes: one empty sunken socket with a faint dead glint, one bloodshot and wide -->
      <path d="M -30,-2 Q -20,-14 -8,-4 Q -14,8 -26,10 Q -32,4 -30,-2 Z"
            fill="{BLOOD_DARK}" stroke="{OUTLINE}" stroke-width="5" stroke-linejoin="round"/>
      <circle cx="-19" cy="1" r="3" fill="#8fae86" opacity="0.8"/>
      <ellipse cx="20" cy="2" rx="12" ry="14" fill="#e8ddc0" stroke="{OUTLINE}" stroke-width="5"/>
      <path d="M 10,-4 Q 14,4 10,10 M 30,-4 Q 26,4 30,10 M 14,-8 Q 20,-2 26,-8"
            fill="none" stroke="{BLOOD}" stroke-width="1.6" opacity="0.85"/>
      <circle cx="21" cy="3" r="5" fill="#241a0c"/>
      <circle cx="19" cy="1" r="1.6" fill="#f4ecd8"/>
      <!-- brow scar -->
      <path d="M -34,-18 L -6,-22" stroke="{OUTLINE}" stroke-width="5" stroke-linecap="round"/>
      <!-- mouth: slack jaw hanging open, jagged broken teeth -->
      <path d="M -22,28 Q -4,52 22,26 Q 18,44 -2,46 Q -18,44 -22,28 Z"
            fill="{BLOOD_DARK}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
      <path d="M -16,29 L -13,37 L -9,29 L -5,38 L 0,29 L 5,38 L 9,29 L 13,36 L 17,28"
            fill="{BONE}" stroke="{OUTLINE}" stroke-width="2.5" stroke-linejoin="round"/>
      <!-- exposed cheek wound (opposite side from the socket for asymmetry) -->
      <path d="M 30,14 Q 42,10 46,20 Q 42,28 32,26 Q 26,20 30,14 Z"
            fill="{BLOOD_DARK}" stroke="{OUTLINE}" stroke-width="4" stroke-linejoin="round"/>
      <path d="M 34,17 L 42,19" stroke="{BONE}" stroke-width="2.5" opacity="0.85"/>
    </g>
    {arm(sh_l, arm_l_a)}
    {arm(sh_r, arm_r_a)}
  </g>
</svg>'''

def main():
    n = 10
    # A pure sine sampled at exactly n evenly-spaced points is mirror-symmetric around
    # each extremum, so two samples straddling a peak land on the identical value —
    # frame k and frame (period - k) render pixel-identical. A small phase offset
    # (still a full 2*pi sweep over the loop, so it still loops seamlessly at frame 0)
    # desyncs the sampling grid from that symmetry so all 10 frames are genuinely
    # distinct poses instead of 5 poses shown twice each.
    phase_eps = 0.17
    for i in range(n):
        t = i / n
        theta = 2 * math.pi * t + phase_eps
        # rigid-leg shamble: legs swing forward/back, amplitude tuned for a stiff gait
        leg_l = 30 * math.sin(theta)
        leg_r = 30 * math.sin(theta + math.pi)
        # arms raised and reaching outward/forward, mirrored L/R (rotate() is clockwise,
        # so +angle swings the left arm from hanging-down toward 9-o'clock/outward, and
        # -angle mirrors the right arm toward 3-o'clock/outward), plus a small
        # counter-swing vs. the same-side leg for a bit of shamble life.
        arm_reach = 62
        arm_l = arm_reach + 8 * math.sin(theta + math.pi)
        arm_r = -(arm_reach + 8 * math.sin(theta))
        # body bob: two bobs per cycle (lowest when legs spread, highest when passing)
        bob = 10 * (abs(math.sin(theta)) - 0.5) * -1 * 2  # range roughly [-10,10], peak when sin~0
        bob = 10 - 20 * abs(math.sin(theta))
        # slight side-to-side stagger tilt
        tilt = 4 * math.sin(theta)
        svg = frame(leg_l, leg_r, arm_l, arm_r, round(bob, 2), round(tilt, 2))
        path = os.path.join(OUT, f"walk-{i:02d}.svg")
        with open(path, "w") as f:
            f.write(svg)
        print("wrote", path)

if __name__ == "__main__":
    main()
