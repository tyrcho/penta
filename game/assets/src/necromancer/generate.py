#!/usr/bin/env python3
"""Generate the Necromancer's walk cycle (6 frames, single-facing, rotate-to-face like
Wolf/Zombie's old treatment) and summon animation (8 frames, looped while frozen)."""
import math
import os

OUT = os.path.dirname(os.path.abspath(__file__))

OUTLINE = "#150f1e"
ROBE = "#2b2038"
ROBE_SHADOW = "#1a1424"
ROBE_HILITE = "#453768"
TRIM = "#8a6a3a"
TRIM_DARK = "#5c471f"
SKIN_SHADOW = "#0c0810"  # hood interior — near-black, no face
EYE = "#7dffb0"
EYE_GLOW = "#a8ffce"
STAFF = "#6b4a2e"
STAFF_DARK = "#4a3220"
BONE = "#ded2b8"
SKULL = "#f2efe4"
SOUL = "#7de8c8"
SOUL_DARK = "#3fae8e"
CIRCLE = "#b24bff"


def robe_body(sway, bob):
    # Bell-shaped robe with no visible legs/feet — floats/glides rather than walks,
    # matching the lore's "moves slowly" without needing a leg rig at all.
    return f'''
  <g transform="translate(0,{bob})">
    <path d="M 165,150 Q {150+sway},260 {158+sway},310 Q 200,326 {242-sway},310
             Q {250-sway},260 235,150 Q 200,128 165,150 Z"
          fill="{ROBE}" stroke="{OUTLINE}" stroke-width="9" stroke-linejoin="round"/>
    <path d="M 165,150 Q {150+sway},260 {158+sway},310 Q 180,320 190,300 Q 178,230 182,150 Z"
          fill="{ROBE_HILITE}" opacity="0.3"/>
    <path d="M 218,150 Q 222,230 210,300 Q 220,320 {242-sway},310 Q {250-sway},260 235,150 Z"
          fill="{ROBE_SHADOW}" opacity="0.5"/>
    <!-- tattered hem -->
    <path d="M {158+sway},308 L {168+sway},322 L {180+sway},306 L 192,324 L 200,306
             L 208,324 L {220-sway},306 L {232-sway},322 L {242-sway},308"
          fill="none" stroke="{OUTLINE}" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"/>
    <!-- bone trim across the chest -->
    <path d="M 178,172 Q 200,182 222,172" fill="none" stroke="{TRIM}" stroke-width="6" stroke-linecap="round"/>
    <circle cx="200" cy="180" r="7" fill="{TRIM}" stroke="{TRIM_DARK}" stroke-width="2"/>
  </g>'''


def hood_head(tilt):
    # Deep hood, no visible face — just two glowing eyes. This is a DELIBERATE
    # featureless silhouette (unlike the old asset's accidental one): the hood's bold
    # outline, cloth-fold shading, and glow give it shape and mood instead of just being
    # a flat blob.
    return f'''
    <g transform="translate(200,110) rotate({tilt})">
      <path d="M -46,20 Q -54,-30 0,-46 Q 54,-30 46,20 Q 40,44 0,50 Q -40,44 -46,20 Z"
            fill="{ROBE}" stroke="{OUTLINE}" stroke-width="9" stroke-linejoin="round"/>
      <path d="M -46,20 Q -54,-30 0,-46 Q -20,-30 -22,4 Q -24,26 -14,40 Q -34,32 -46,20 Z"
            fill="{ROBE_HILITE}" opacity="0.25"/>
      <path d="M 46,20 Q 54,-30 0,-46 Q 20,-30 22,6 Q 24,28 14,42 Q 34,32 46,20 Z"
            fill="{ROBE_SHADOW}" opacity="0.5"/>
      <!-- hood interior shadow -->
      <ellipse cx="0" cy="6" rx="30" ry="26" fill="{SKIN_SHADOW}"/>
      <!-- glowing eyes -->
      <ellipse cx="-12" cy="4" rx="7" ry="5" fill="{EYE}"/>
      <ellipse cx="12" cy="4" rx="7" ry="5" fill="{EYE}"/>
      <ellipse cx="-12" cy="4" rx="11" ry="8" fill="{EYE_GLOW}" opacity="0.35"/>
      <ellipse cx="12" cy="4" rx="11" ry="8" fill="{EYE_GLOW}" opacity="0.35"/>
      <!-- hood peak -->
      <path d="M -8,-44 L 0,-56 L 8,-44" fill="none" stroke="{OUTLINE}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
    </g>'''


def arm_staff(angle, bob):
    # Right arm: gripping a tall bone-topped staff, angled slightly by `angle` for the
    # walk-cycle's idle sway.
    return f'''
  <g transform="translate(236,180) rotate({angle})">
    <path d="M -12,-10 Q -16,20 -10,54 L 10,54 Q 16,20 12,-10 Z"
          fill="{ROBE}" stroke="{OUTLINE}" stroke-width="7" stroke-linejoin="round"/>
    <path d="M -12,-10 Q -16,20 -10,54 L 0,54 Q -4,20 -2,-10 Z" fill="{ROBE_SHADOW}" opacity="0.5"/>
    <circle cx="0" cy="58" r="12" fill="{SKIN_SHADOW}" stroke="{OUTLINE}" stroke-width="6"/>
    <rect x="-5" y="-6" width="10" height="106" rx="4" fill="{STAFF}" stroke="{OUTLINE}" stroke-width="6"/>
    <rect x="-5" y="30" width="10" height="70" fill="{STAFF_DARK}" opacity="0.4"/>
    <!-- white skull mounted atop the staff -->
    <g transform="translate(0,-30)">
      <path d="M -17,4 Q -20,-20 0,-24 Q 20,-20 17,4 Q 16,14 8,16 L 8,24 L 3,24 L 3,17
               L -3,17 L -3,24 L -8,24 L -8,16 Q -16,14 -17,4 Z"
            fill="{SKULL}" stroke="{OUTLINE}" stroke-width="5" stroke-linejoin="round"/>
      <ellipse cx="-8" cy="-2" rx="5.5" ry="7" fill="{OUTLINE}"/>
      <ellipse cx="8" cy="-2" rx="5.5" ry="7" fill="{OUTLINE}"/>
      <ellipse cx="-8" cy="-3" rx="2.4" ry="3" fill="{EYE}" opacity="0.85"/>
      <ellipse cx="8" cy="-3" rx="2.4" ry="3" fill="{EYE}" opacity="0.85"/>
      <path d="M 0,4 L -2.5,10 L 2.5,10 Z" fill="{OUTLINE}"/>
      <path d="M -5,17 L -3,17 M 3,17 L 5,17" stroke="{OUTLINE}" stroke-width="2"/>
    </g>
  </g>'''


def arm_free(angle, bob):
    return f'''
  <g transform="translate(164,180) rotate({angle})">
    <path d="M 12,-10 Q 16,20 10,54 L -10,54 Q -16,20 -12,-10 Z"
          fill="{ROBE}" stroke="{OUTLINE}" stroke-width="7" stroke-linejoin="round"/>
    <path d="M 12,-10 Q 16,20 10,54 L 0,54 Q 4,20 2,-10 Z" fill="{ROBE_HILITE}" opacity="0.3"/>
    <circle cx="0" cy="58" r="12" fill="{SKIN_SHADOW}" stroke="{OUTLINE}" stroke-width="6"/>
    <path d="M -8,52 L -14,64 M -1,56 L -3,68 M 6,55 L 10,66" stroke="{BONE}" stroke-width="4" stroke-linecap="round"/>
  </g>'''


def ground_glow(radius, opacity):
    return f'''
  <ellipse cx="200" cy="330" rx="{radius}" ry="{radius*0.28}" fill="{CIRCLE}" opacity="{opacity}"/>
  <ellipse cx="200" cy="330" rx="{radius*0.6}" ry="{radius*0.17}" fill="{EYE}" opacity="{opacity*0.6}"/>'''


def walk_frame(sway, bob, arm_a, tilt):
    return f'''<svg id="art" viewBox="0 0 400 400" xmlns="http://www.w3.org/2000/svg">
  <ellipse cx="200" cy="336" rx="80" ry="14" fill="#000000" opacity="0.22"/>
  {robe_body(sway, bob)}
  {arm_free(-arm_a*0.6, bob)}
  {arm_staff(arm_a, bob)}
  {hood_head(tilt)}
</svg>'''


def summon_frame(raise_amt, circle_r, circle_op, soul_y, soul_op, bob):
    soul = ""
    if soul_op > 0:
        soul = f'''
  <g transform="translate(200,{330+soul_y})" opacity="{soul_op}">
    <circle cx="0" cy="0" r="20" fill="{SOUL}" stroke="{OUTLINE}" stroke-width="5"/>
    <circle cx="-6" cy="-4" r="10" fill="{SOUL_DARK}" opacity="0.5"/>
    <circle cx="-5" cy="-2" r="3.5" fill="{OUTLINE}"/>
    <circle cx="6" cy="-2" r="3.5" fill="{OUTLINE}"/>
  </g>'''
    return f'''<svg id="art" viewBox="0 0 400 400" xmlns="http://www.w3.org/2000/svg">
  <ellipse cx="200" cy="336" rx="80" ry="14" fill="#000000" opacity="0.22"/>
  {ground_glow(circle_r, circle_op)}
  {robe_body(0, bob)}
  {arm_free(-14, bob)}
  {arm_staff(-2.3 * raise_amt, bob)}
  {hood_head(-4 - raise_amt*0.15)}
  {soul}
</svg>'''


def main():
    n_walk = 6
    for i in range(n_walk):
        t = i / n_walk
        theta = 2 * math.pi * t
        sway = 10 * math.sin(theta)
        bob = 6 - 12 * abs(math.sin(theta))
        arm_a = 8 * math.sin(theta + math.pi / 2)
        tilt = 3 * math.sin(theta)
        svg = walk_frame(round(sway, 2), round(bob, 2), round(arm_a, 2), round(tilt, 2))
        with open(os.path.join(OUT, f"walk-{i:02d}.svg"), "w") as f:
            f.write(svg)

    # Summon: staff-raise builds over the first half, a ground rune grows and pulses,
    # a soul rises out of it and drifts up/fades in the second half, then resets — this
    # loops (the game plays it on repeat for the ~1s frozen window), so frame 7 eases
    # back toward frame 0 rather than cutting hard.
    n_summon = 8
    raise_by_frame = [0, 8, 18, 26, 30, 26, 14, 4]
    circle_r_by_frame = [20, 34, 52, 62, 66, 60, 46, 28]
    circle_op_by_frame = [0.15, 0.35, 0.55, 0.7, 0.75, 0.6, 0.4, 0.2]
    soul_y_by_frame = [0, -2, -8, -20, -34, -48, -60, -70]
    soul_op_by_frame = [0, 0.15, 0.35, 0.6, 0.85, 0.85, 0.7, 0.45]
    bob_by_frame = [0, -1, -2, -2, -1, 0, 1, 0]
    for i in range(n_summon):
        svg = summon_frame(
            raise_by_frame[i], circle_r_by_frame[i], circle_op_by_frame[i],
            soul_y_by_frame[i], soul_op_by_frame[i], bob_by_frame[i],
        )
        with open(os.path.join(OUT, f"summon-{i:02d}.svg"), "w") as f:
            f.write(svg)

    print("wrote", n_walk, "walk frames and", n_summon, "summon frames")


if __name__ == "__main__":
    main()
