#!/usr/bin/env python3
"""Generate 4-direction (front/back/left/right) x 10-frame SVG walk-cycle for the
darker/horrific zombie. front/back share one rig (symmetric mummy-arms + scissoring
legs, differing only in head/torso "skin"); left is a side-profile rig with a near/far
limb pair; right is left mirrored via an SVG transform, not redrawn."""
import math
import os

OUT = os.path.dirname(os.path.abspath(__file__))

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


def leg(hip, angle, scale=1.0, dim=False):
    o = 0.6 if dim else 1.0
    return f'''
  <g transform="translate({hip[0]},{hip[1]}) rotate({angle}) scale({scale})" opacity="{o}">
    <path d="M -18,0 Q -23,58 -17,104 L -8,112 L -2,100 L 4,112 L 12,100 L 16,110 Q 23,58 18,0 Z"
          fill="{PANTS}" stroke="{OUTLINE}" stroke-width="7" stroke-linejoin="round"/>
    <path d="M -18,0 Q -23,58 -17,104 L -6,108 Q -8,54 -6,0 Z" fill="{PANTS_SHADOW}" opacity="0.6"/>
    <path d="M -10,40 L -16,52 M 8,60 L 15,70" stroke="{OUTLINE}" stroke-width="3.5" stroke-linecap="round" opacity="0.6"/>
    <ellipse cx="0" cy="120" rx="24" ry="13" fill="{BANDAGE}" stroke="{OUTLINE}" stroke-width="7"/>
    <path d="M -20,116 Q 0,124 20,116" fill="none" stroke="{BANDAGE_SHADOW}" stroke-width="4" opacity="0.8"/>
    <ellipse cx="0" cy="126" rx="24" ry="6" fill="{BLOOD_DARK}" opacity="0.55"/>
  </g>'''


def arm(shoulder, angle, scale=1.0, dim=False):
    o = 0.6 if dim else 1.0
    return f'''
  <g transform="translate({shoulder[0]},{shoulder[1]}) rotate({angle}) scale({scale})" opacity="{o}">
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


def head_front():
    return f'''
    <g transform="translate(200,93)">
      <path d="M 0,-58 Q 40,-56 52,-18 Q 58,10 40,34 Q 20,52 0,52 Q -20,52 -40,34
               Q -58,10 -52,-18 Q -40,-56 0,-58 Z"
            fill="{SKIN}" stroke="{OUTLINE}" stroke-width="8" stroke-linejoin="round"/>
      <path d="M -42,-4 Q -50,18 -32,28 Q -44,6 -42,-4 Z" fill="{SKIN_SHADOW}" opacity="0.55"/>
      <path d="M -18,28 Q 0,46 24,32 Q 20,50 -4,48 Q -22,44 -18,28 Z" fill="{SKIN_SHADOW}" opacity="0.5"/>
      <path d="M 26,-28 Q 42,-24 40,-8 Q 32,-4 24,-14 Q 20,-24 26,-28 Z" fill="{SKIN_ROT}" opacity="0.55"/>
      <path d="M -4,-40 Q 8,-42 10,-32 Q 2,-28 -6,-34 Z" fill="{SKIN_ROT}" opacity="0.4"/>
      <circle cx="20" cy="-24" r="16" fill="{SKIN_HILITE}" opacity="0.2"/>
      <circle cx="-48" cy="10" r="10" fill="{SKIN}" stroke="{OUTLINE}" stroke-width="6"/>
      <circle cx="-48" cy="10" r="4" fill="{SKIN_SHADOW}"/>
      <path d="M -46,-30 Q -50,-48 -30,-52 Q -38,-38 -30,-30 Q -42,-30 -46,-30 Z"
            fill="{HAIR}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
      <path d="M -22,-48 Q -20,-60 -2,-58 Q -8,-46 -2,-38 Q -16,-40 -22,-48 Z"
            fill="{HAIR}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
      <path d="M 14,-54 Q 26,-58 36,-46 Q 26,-42 20,-46 Q 10,-46 14,-54 Z"
            fill="{HAIR}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
      <path d="M 34,-32 Q 46,-30 44,-16 Q 34,-16 30,-24 Q 28,-32 34,-32 Z"
            fill="{HAIR}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
      <path d="M -6,-44 Q 4,-40 2,-30" fill="none" stroke="{BLOOD}" stroke-width="3" stroke-linecap="round" opacity="0.85"/>
      <path d="M -30,-2 Q -20,-14 -8,-4 Q -14,8 -26,10 Q -32,4 -30,-2 Z"
            fill="{BLOOD_DARK}" stroke="{OUTLINE}" stroke-width="5" stroke-linejoin="round"/>
      <circle cx="-19" cy="1" r="3" fill="#8fae86" opacity="0.8"/>
      <ellipse cx="20" cy="2" rx="12" ry="14" fill="#e8ddc0" stroke="{OUTLINE}" stroke-width="5"/>
      <path d="M 10,-4 Q 14,4 10,10 M 30,-4 Q 26,4 30,10 M 14,-8 Q 20,-2 26,-8"
            fill="none" stroke="{BLOOD}" stroke-width="1.6" opacity="0.85"/>
      <circle cx="21" cy="3" r="5" fill="#241a0c"/>
      <circle cx="19" cy="1" r="1.6" fill="#f4ecd8"/>
      <path d="M -34,-18 L -6,-22" stroke="{OUTLINE}" stroke-width="5" stroke-linecap="round"/>
      <path d="M -22,28 Q -4,52 22,26 Q 18,44 -2,46 Q -18,44 -22,28 Z"
            fill="{BLOOD_DARK}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
      <path d="M -16,29 L -13,37 L -9,29 L -5,38 L 0,29 L 5,38 L 9,29 L 13,36 L 17,28"
            fill="{BONE}" stroke="{OUTLINE}" stroke-width="2.5" stroke-linejoin="round"/>
      <path d="M 30,14 Q 42,10 46,20 Q 42,28 32,26 Q 26,20 30,14 Z"
            fill="{BLOOD_DARK}" stroke="{OUTLINE}" stroke-width="4" stroke-linejoin="round"/>
      <path d="M 34,17 L 42,19" stroke="{BONE}" stroke-width="2.5" opacity="0.85"/>
    </g>'''


def torso_front():
    return f'''
    <path d="M 158,150 Q 152,212 160,262 L 172,270 L 182,258 L 192,270 L 200,256
             L 208,270 L 218,258 L 228,270 L 240,262 Q 248,210 242,150 Q 200,130 158,150 Z"
          fill="{SHIRT}" stroke="{OUTLINE}" stroke-width="8" stroke-linejoin="round"/>
    <path d="M 158,150 Q 152,212 160,262 L 172,270 L 182,258 L 192,270 L 200,256 Q 194,200 190,150 Z"
          fill="{SHIRT_HILITE}" opacity="0.35"/>
    <path d="M 222,150 Q 248,210 240,150 L 242,150 Q 248,210 240,262 L 228,270 L 218,258
             L 208,270 L 200,256 Q 210,200 222,150 Z" fill="{SHIRT_SHADOW}" opacity="0.55"/>
    <path d="M 196,168 Q 178,178 182,204 Q 186,224 208,230 Q 224,224 220,200 Q 216,176 196,168 Z"
          fill="{BLOOD_DARK}" stroke="{OUTLINE}" stroke-width="5" stroke-linejoin="round"/>
    <path d="M 190,186 L 212,182 M 189,198 L 213,196 M 192,212 L 210,212"
          fill="none" stroke="{BONE}" stroke-width="4" stroke-linecap="round" opacity="0.85"/>
    <path d="M 196,168 Q 178,178 182,204" fill="none" stroke="{BLOOD}" stroke-width="4" opacity="0.7"/>
    <circle cx="172" cy="240" r="5" fill="{BLOOD}" opacity="0.7"/>
    <circle cx="225" cy="248" r="4" fill="{BLOOD}" opacity="0.6"/>
    <path d="M 208,232 Q 206,246 210,258" stroke="{BLOOD}" stroke-width="4" fill="none" opacity="0.65" stroke-linecap="round"/>
    <path d="M 186,126 Q 184,142 188,154 L 212,154 Q 216,142 214,126 Z"
          fill="{SKIN_SHADOW}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>'''


def head_back():
    # Back of the skull: no face, mostly matted hair, a bald wound patch showing bare
    # scalp with a crack — the horror cue here is "what's showing through the hair",
    # not eyes/mouth (there are none to see from behind).
    return f'''
    <g transform="translate(200,93)">
      <path d="M 0,-58 Q 40,-56 52,-18 Q 58,10 40,34 Q 20,52 0,52 Q -20,52 -40,34
               Q -58,10 -52,-18 Q -40,-56 0,-58 Z"
            fill="{SKIN}" stroke="{OUTLINE}" stroke-width="8" stroke-linejoin="round"/>
      <path d="M -42,10 Q -52,30 -30,40 Q -46,20 -42,10 Z" fill="{SKIN_SHADOW}" opacity="0.5"/>
      <path d="M 40,10 Q 52,30 30,40 Q 46,20 40,10 Z" fill="{SKIN_SHADOW}" opacity="0.5"/>
      <circle cx="-48" cy="12" r="9" fill="{SKIN}" stroke="{OUTLINE}" stroke-width="6"/>
      <circle cx="48" cy="12" r="9" fill="{SKIN}" stroke="{OUTLINE}" stroke-width="6"/>
      <!-- big matted hair mass covering crown/back, with one torn bald wound showing through -->
      <path d="M -54,-4 Q -60,-52 0,-60 Q 60,-52 54,-4 Q 50,18 38,28
               Q 46,-2 30,-14 Q 40,10 22,10 Q 34,-10 14,-20 Q 26,4 6,0
               Q -6,-2 -18,-14 Q -10,4 -26,4 Q -12,-16 -30,-16 Q -46,-14 -50,4
               Q -54,-2 -54,-4 Z"
            fill="{HAIR}" stroke="{OUTLINE}" stroke-width="7" stroke-linejoin="round"/>
      <!-- bald scalp wound: bare skin patch with a single jagged gash (not a crossed
           "X" — one continuous crack reads as a wound, two crossing lines read as an
           icon) -->
      <path d="M -6,-30 Q 8,-34 14,-20 Q 8,-8 -8,-10 Q -18,-16 -6,-30 Z"
            fill="{SKIN_ROT}" stroke="{OUTLINE}" stroke-width="5" stroke-linejoin="round"/>
      <path d="M -10,-24 L -2,-22 L -4,-16 L 4,-15" stroke="{BLOOD}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" fill="none" opacity="0.9"/>
    </g>'''


def torso_back():
    return f'''
    <path d="M 158,150 Q 152,212 160,262 L 172,270 L 182,258 L 192,270 L 200,256
             L 208,270 L 218,258 L 228,270 L 240,262 Q 248,210 242,150 Q 200,130 158,150 Z"
          fill="{SHIRT}" stroke="{OUTLINE}" stroke-width="8" stroke-linejoin="round"/>
    <path d="M 158,150 Q 152,212 160,262 L 172,270 L 182,258 L 192,270 L 200,256 Q 194,200 190,150 Z"
          fill="{SHIRT_SHADOW}" opacity="0.4"/>
    <path d="M 222,150 Q 248,210 240,150 L 242,150 Q 248,210 240,262 L 228,270 L 218,258
             L 208,270 L 200,256 Q 210,200 222,150 Z" fill="{SHIRT_HILITE}" opacity="0.25"/>
    <!-- spine wound: a ragged torn opening (jagged edges, not a clean capsule, so it
         reads as ripped fabric/flesh rather than a seam) in bright blood-red so it
         pops against the dark shirt, with knobby offset-zigzag vertebrae instead of
         evenly-spaced circles (which read as buttons) -->
    <path d="M 190,154 L 182,166 L 187,180 L 180,196 L 186,214 L 181,232 L 188,250
             L 212,250 L 219,232 L 213,214 L 220,196 L 212,180 L 218,166 L 210,154
             Q 200,145 190,154 Z"
          fill="{BLOOD_DARK}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
    <path d="M 195,159 L 189,169 L 193,180 L 188,195 L 192,212 L 188,229 L 193,245
             L 207,245 L 212,229 L 208,212 L 212,195 L 207,180 L 211,169 L 205,159
             Q 200,153 195,159 Z"
          fill="{BLOOD}"/>
    <path d="M 200,168 L 205,175 L 200,183 L 195,175 Z" fill="{BONE}" stroke="{OUTLINE}" stroke-width="2.5" stroke-linejoin="round"/>
    <path d="M 203,190 L 209,196 L 203,204 L 198,197 Z" fill="{BONE}" stroke="{OUTLINE}" stroke-width="2.5" stroke-linejoin="round"/>
    <path d="M 197,211 L 202,218 L 196,225 L 191,218 Z" fill="{BONE}" stroke="{OUTLINE}" stroke-width="2.5" stroke-linejoin="round"/>
    <path d="M 203,231 L 208,237 L 202,244 L 197,237 Z" fill="{BONE}" stroke="{OUTLINE}" stroke-width="2.5" stroke-linejoin="round"/>
    <circle cx="176" cy="256" r="5" fill="{BLOOD}" opacity="0.7"/>
    <circle cx="224" cy="248" r="4" fill="{BLOOD}" opacity="0.6"/>
    <path d="M 186,126 Q 184,142 188,154 L 212,154 Q 216,142 214,126 Z"
          fill="{SKIN_SHADOW}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>'''


def frame_frontback(facing, leg_l_a, leg_r_a, arm_l_a, arm_r_a, bob, tilt):
    hip_l = (178, 248)
    hip_r = (222, 248)
    sh_l = (163, 172)
    sh_r = (237, 172)
    head = head_front() if facing == "front" else head_back()
    torso = torso_front() if facing == "front" else torso_back()
    return f'''<svg id="art" viewBox="0 0 400 400" xmlns="http://www.w3.org/2000/svg">
  <ellipse cx="200" cy="366" rx="86" ry="14" fill="#000000" opacity="0.22"/>
  <g transform="translate(0,{bob}) rotate({tilt} 200 250)">
    {leg(hip_l, leg_l_a)}
    {leg(hip_r, leg_r_a)}
    {torso}
    {head}
    {arm(sh_l, arm_l_a)}
    {arm(sh_r, arm_r_a)}
  </g>
</svg>'''


def head_side():
    # Profile, facing left (negative x = forward). One bloodshot eye, jaw open in
    # profile with a jagged tooth row, brow ridge, ear toward the back (right/positive
    # x side), hair tufts along the crown/back of the head.
    return f'''
    <g transform="translate(198,88)">
      <path d="M 20,-54 Q -18,-56 -40,-30 Q -52,-10 -44,14 Q -38,28 -46,34
               Q -30,36 -22,26 Q -14,40 4,42 Q 26,40 34,20 Q 44,6 40,-16 Q 36,-42 20,-54 Z"
            fill="{SKIN}" stroke="{OUTLINE}" stroke-width="8" stroke-linejoin="round"/>
      <!-- jaw/cheek hollow -->
      <path d="M -4,10 Q -14,26 -2,38 Q -18,30 -18,14 Q -12,4 -4,10 Z" fill="{SKIN_SHADOW}" opacity="0.5"/>
      <path d="M 30,-30 Q 42,-24 38,-6 Q 30,-4 26,-16 Q 24,-26 30,-30 Z" fill="{SKIN_ROT}" opacity="0.5"/>
      <circle cx="30" cy="4" r="10" fill="{SKIN}" stroke="{OUTLINE}" stroke-width="6"/>
      <circle cx="30" cy="4" r="4" fill="{SKIN_SHADOW}"/>
      <!-- hair: crown + back tufts only (front of face stays bare/gaunt) -->
      <path d="M 16,-50 Q -16,-52 -34,-32 Q -20,-40 -6,-40 Q -18,-32 -14,-24
               Q 0,-38 16,-36 Q 6,-28 12,-20 Q 22,-30 30,-24 Q 34,-14 24,-10
               Q 36,-14 38,-28 Q 38,-44 16,-50 Z"
            fill="{HAIR}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
      <path d="M 6,-40 Q 12,-34 6,-30" fill="none" stroke="{BLOOD}" stroke-width="2.5" opacity="0.8"/>
      <!-- brow -->
      <path d="M -32,-14 L -10,-18" stroke="{OUTLINE}" stroke-width="5" stroke-linecap="round"/>
      <!-- eye: bloodshot, forward-looking -->
      <ellipse cx="-18" cy="-2" rx="11" ry="10" fill="#e8ddc0" stroke="{OUTLINE}" stroke-width="5"/>
      <path d="M -26,-6 Q -22,0 -26,4 M -8,-6 Q -12,0 -8,4" fill="none" stroke="{BLOOD}" stroke-width="1.5" opacity="0.85"/>
      <circle cx="-18" cy="-1" r="4.5" fill="#241a0c"/>
      <circle cx="-19.5" cy="-2.5" r="1.4" fill="#f4ecd8"/>
      <!-- open jaw in profile, jagged teeth -->
      <path d="M -40,10 Q -48,24 -34,34 Q -16,42 0,36 Q -10,36 -20,28 Q -32,20 -40,10 Z"
            fill="{BLOOD_DARK}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>
      <path d="M -38,14 L -32,17 L -34,22 L -26,23 L -28,29 L -18,30"
            fill="{BONE}" stroke="{OUTLINE}" stroke-width="2.2" stroke-linejoin="round"/>
      <!-- cheek wound -->
      <path d="M 6,18 Q 18,16 20,26 Q 14,32 4,28 Q 0,22 6,18 Z"
            fill="{BLOOD_DARK}" stroke="{OUTLINE}" stroke-width="4" stroke-linejoin="round"/>
    </g>'''


def torso_side():
    return f'''
    <path d="M 176,152 Q 164,214 172,264 L 184,272 L 196,258 L 210,270 L 224,258
             Q 236,212 226,152 Q 200,132 176,152 Z"
          fill="{SHIRT}" stroke="{OUTLINE}" stroke-width="8" stroke-linejoin="round"/>
    <path d="M 176,152 Q 164,214 172,264 L 184,272 L 196,258 Q 190,204 188,152 Z"
          fill="{SHIRT_HILITE}" opacity="0.3"/>
    <path d="M 210,152 Q 236,212 226,152 L 226,152 Q 236,212 224,258 L 210,270 L 200,258
             Q 208,204 210,152 Z" fill="{SHIRT_SHADOW}" opacity="0.5"/>
    <path d="M 188,172 Q 174,182 178,206 Q 182,224 200,228 Q 214,222 210,200 Q 206,178 188,172 Z"
          fill="{BLOOD_DARK}" stroke="{OUTLINE}" stroke-width="5" stroke-linejoin="round"/>
    <path d="M 182,188 L 202,184 M 181,200 L 203,198"
          fill="none" stroke="{BONE}" stroke-width="4" stroke-linecap="round" opacity="0.85"/>
    <circle cx="180" cy="242" r="4.5" fill="{BLOOD}" opacity="0.65"/>
    <path d="M 200,230 Q 198,244 202,256" stroke="{BLOOD}" stroke-width="3.5" fill="none" opacity="0.6" stroke-linecap="round"/>
    <path d="M 190,128 Q 188,144 192,156 L 208,156 Q 212,144 210,128 Z"
          fill="{SKIN_SHADOW}" stroke="{OUTLINE}" stroke-width="6" stroke-linejoin="round"/>'''


def frame_left(leg_near_a, leg_far_a, arm_near_a, arm_far_a, bob, tilt):
    hip_near = (203, 250)
    hip_far = (193, 244)
    sh_near = (188, 178)
    sh_far = (198, 172)
    return f'''<svg id="art" viewBox="0 0 400 400" xmlns="http://www.w3.org/2000/svg">
  <ellipse cx="196" cy="366" rx="82" ry="14" fill="#000000" opacity="0.22"/>
  <g transform="translate(0,{bob}) rotate({8 + tilt} 200 260)">
    {leg(hip_far, leg_far_a, scale=0.88, dim=True)}
    {arm(sh_far, arm_far_a, scale=0.88, dim=True)}
    {torso_side()}
    {head_side()}
    {leg(hip_near, leg_near_a)}
    {arm(sh_near, arm_near_a)}
  </g>
</svg>'''


def mirror_right(svg_text):
    # Right = left, mirrored — reuse the exact same drawing rather than redrawing a
    # second profile by hand. Wrapping the whole <g>...</g> body in a horizontal flip
    # transform mirrors every nested rotate()/translate() correctly for free.
    open_tag = '<svg id="art" viewBox="0 0 400 400" xmlns="http://www.w3.org/2000/svg">'
    close_tag = '</svg>'
    assert svg_text.startswith(open_tag) and svg_text.rstrip().endswith(close_tag)
    body = svg_text[len(open_tag):-len(close_tag)]
    return f'{open_tag}\n  <g transform="translate(400,0) scale(-1,1)">{body}</g>\n{close_tag}'


def main():
    n = 10
    phase_eps = 0.17
    for i in range(n):
        t = i / n
        theta = 2 * math.pi * t + phase_eps

        # --- front/back: symmetric scissoring legs + mummy-reach arms (unchanged from
        # the single-facing version) ---
        leg_l = 30 * math.sin(theta)
        leg_r = 30 * math.sin(theta + math.pi)
        arm_reach = 62
        arm_l = arm_reach + 8 * math.sin(theta + math.pi)
        arm_r = -(arm_reach + 8 * math.sin(theta))
        bob = 10 - 20 * abs(math.sin(theta))
        tilt = 4 * math.sin(theta)
        for facing in ("front", "back"):
            svg = frame_frontback(facing, leg_l, leg_r, arm_l, arm_r, round(bob, 2), round(tilt, 2))
            with open(os.path.join(OUT, f"{facing}-walk-{i:02d}.svg"), "w") as f:
                f.write(svg)

        # --- left: side-profile walk, near/far leg and arm pairs swinging fore/aft
        # along the direction of travel (same rotate-at-hip math, now read as a proper
        # forward/back stride instead of a side-to-side scissor since the character
        # itself is rotated 90 degrees into profile) ---
        side_amp = 34
        leg_near = side_amp * math.sin(theta)
        leg_far = side_amp * math.sin(theta + math.pi)
        arm_near = -50 + 22 * math.sin(theta + math.pi)
        arm_far = -35 + 18 * math.sin(theta)
        side_bob = 8 - 16 * abs(math.sin(theta))
        side_tilt = 3 * math.sin(theta)
        left_svg = frame_left(leg_near, leg_far, arm_near, arm_far, round(side_bob, 2), round(side_tilt, 2))
        with open(os.path.join(OUT, f"left-walk-{i:02d}.svg"), "w") as f:
            f.write(left_svg)
        with open(os.path.join(OUT, f"right-walk-{i:02d}.svg"), "w") as f:
            f.write(mirror_right(left_svg))

        print("wrote frame", i)


if __name__ == "__main__":
    main()
