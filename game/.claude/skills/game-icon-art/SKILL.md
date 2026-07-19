---
name: game-icon-art
description: Draws or redraws game/assets/*.png art for this tower-defense game — building/unit/resource icons AND multi-frame animated unit sprites (walk cycles like Zombie/Goblin/Wolf) — in the established hand-drawn cartoon style (bold dark outlines, flat cel-shaded colors, transparent background), and wires it into GameApp.scala/index.html/EntityNames.scala correctly. Use this whenever the user asks to create, replace, redraw, improve, or "make better" any in-game icon, sprite, or animation, asks for art that's "more recognizable", "more cartoon", "darker/more horrific", "more thematic", or ties an icon more clearly to its faction (Chaos/Nature/Undead/etc.) — even if they just say "the image for X looks bad" or "can we get a nicer icon for Y" or "animate X". No external image-generation tool is available in this environment; this skill produces art by hand-authoring SVG (rigged with per-limb joints for animated sprites) and rendering it locally.
---

# Game icon art

This captures the workflow used to redraw `game/assets/cave.png` into a reusable
process for any other building/unit/resource icon in this repo. The hard part isn't
the tooling (SVG → PNG is mechanical) — it's (a) matching the house style closely
enough that the new icon doesn't stick out next to its siblings, and (b) making sure
every place that referenced the old asset's quirks (tints, filters, doc embeds) gets
updated to match, not just the PNG file.

## 0. Pin down the target

Confirm which file you're producing: `game/assets/<name>.png`. If the user named a
building/unit loosely ("the goblin", "the tower"), find the exact asset path and
`BuildingKind`/unit enum case first — Grep `game/js/src/main/scala/towerdefense/main/GameApp.scala`
for the entity's name before doing anything else. Don't guess a filename.

## 1. Find every place the current asset is wired in

An icon isn't just a PNG — it's referenced from several places, and some of those
references compensate for quirks of the *current* art (a flat placeholder recolored
via tint, a specific aspect ratio a layout depends on). Grep before you draw anything:

- `game/js/src/main/scala/towerdefense/main/GameApp.scala` — the `AssetPaths` object
  (texture path constant) and the `BuildingVisuals`/equivalent map (render size
  multiplier, and critically, `tint: Option[Int]` — a PIXI multiply-tint applied at
  runtime). A `Some(tint)` next to an otherwise-plain-sounding comment
  ("cool-gray rock tile", "recolor for...") is a signal the *old* art was flat/gray
  and got color-hacked on at runtime. New hand-colored art shouldn't be re-tinted —
  plan to change it to `None` once your art has its own color baked in. Don't remove
  a tint that serves an active gameplay purpose (e.g. a hit-flash or kill-flash
  effect) — only ones compensating for placeholder art.
- `game/index.html` — search for `#build-<slug>` CSS rules. The same color-hack
  pattern shows up here too as a `filter:` on that button's `<img>` (e.g.
  `sepia(1) saturate(4) hue-rotate(...)`). Remove it for the same reason as the tint.
- `Resources/**/*.md` and `Resources-en/**/*.md` — these are the lore/doc pages and
  usually embed the PNG directly (`![Name](../../game/assets/x.png)`). No edits
  needed here unless you're renaming the file, but worth confirming the doc's
  description of the entity (cost, production, spawns) to inform the art — the cave
  produces Fire *and* spawns Goblins, which is why the final art needed both a fire
  glow and goblin references, not just rocks.
- Check `GridConfig.cellSize` (in `game/core/src/main/scala/towerdefense/domain/GridConfig.scala`)
  and the render-size multiplier from the `BuildingVisuals` map entry you found above
  — their product is the actual on-screen pixel size in the maze. You'll want this
  number in step 6.

## 2. Study 2-4 sibling icons before drawing anything

Read (the tool, not a thumbnail) 2-4 existing `game/assets/*.png` files that are
stylistically or thematically close to your target — a building icon if you're
drawing a building, a same-faction icon if one exists. Good general references:
`tomb.png` (mound + prop composition), `forest.png` (blobby cartoon cluster with
shading), `labo-du-chaos.png` (clean single-object icon), `minotaur.png` (creature).

Look specifically for:
- **Outline**: a single bold, dark, consistently-round-jointed stroke around every
  shape (roughly stroke-width 5-8 on a 400×400 viewBox — scale proportionally if you
  pick a different viewBox size). This is what makes the whole asset set read as one
  family — don't skip it or thin it out.
- **Shading**: flat fills, not gradients-everywhere — one darker overlay shape for
  shadow, one lighter overlay shape for highlight, both flat colors at partial
  opacity layered on the base fill. `tomb.png`'s gravestone and `forest.png`'s
  foliage both show this clearly.
- **Grounding**: props "planted in the world" (gravestones, trees, cave mounds) sit
  on a small dirt/rock mound with a soft blurred gray ground-shadow ellipse beneath —
  reuse that if your icon is a static ground building, skip it for creatures/items
  that float in the build-menu without a ground context.
- **Palette-by-theme**, inferred from what's already in `game/assets/`: Chaos reads
  through purple rune/crystal accents and jagged asymmetric shapes; goblins read
  through green (skin and glow); fire/production reads through warm orange glow
  gradients; nature reads through green/brown organic blobs; undead reads through
  purple-gray stone and sickly green. Don't invent a new palette convention when an
  existing one already signals the right faction — reuse it so the new icon slots in
  visually.

## 3. Author the art as hand-drawn SVG — and keep it

There's no image-generation tool in this environment — the cave icon and this skill
were both built by hand-authoring SVG shapes and rendering them, not by prompting a
generator. Write a standalone `.svg` file, `viewBox="0 0 400 400"`, with the root
`<svg>` element carrying `id="art"` (the render script looks for that id; Chromium
displays raw `.svg` files directly, so no HTML wrapper is needed).

Save this file at `game/assets/src/<name>.svg` and treat it as a real source file,
not scratch output — commit it alongside the PNG. The PNG is a compiled artifact;
the SVG is the only editable form. Without it, the next request to tweak this same
icon ("make the eyes bigger", "add a badge for the upgraded tier") means
reverse-engineering shapes from a raster image instead of opening a text file and
changing a few coordinates. If `game/assets/src/<name>.svg` already exists because
you're revising an existing icon, edit it in place rather than starting over.

Build shapes as blobby, overlapping rounded paths (quadratic Bezier `Q` segments read
as "hand-drawn cartoon" much better than straight polygon edges) rather than
importing external clip art. For a mascot/character piece, look at how `goblin/front-walk-00.png`
reads at a glance — silhouette clarity matters more than detail. Bake color in
directly rather than relying on a runtime tint (see step 1's note on why tints get
removed, not added).

Iterate in place: render frequently (next step), Read the PNG back, and adjust
coordinates. Don't try to get the SVG perfect before ever looking at it rendered —
two or three render/critique passes is normal and faster than reasoning about
coordinates in the abstract.

## 4. Render the SVG to a transparent PNG

Chromium is preinstalled and a global Playwright module is reachable, but it isn't on
the default Node resolution path, so `NODE_PATH` must be set explicitly. Use the
bundled script rather than re-deriving this invocation:

```bash
NODE_PATH=/opt/node22/lib/node_modules node \
  game/.claude/skills/game-icon-art/scripts/render.js \
  game/assets/src/<name>.svg <out.png> 800
```

Render large (800px) even though the final asset will be smaller — screenshotting an
SVG at a large size and downscaling in step 5 gives much cleaner anti-aliased edges
than rendering small directly. `omitBackground: true` is what makes the PNG
transparent instead of white; the script already does this.

## 5. Trim and resize

Pillow isn't preinstalled — `pip install pillow` first if `python3 -c "import PIL"`
fails. Then use the bundled script to trim to the alpha bounding box (with a little
padding so the outline doesn't touch the edge) and downscale to the final size:

```bash
python3 game/.claude/skills/game-icon-art/scripts/postprocess.py \
  <out.png> game/assets/<name>.png --max-dim 520 --previews 64,45,32
```

Don't guess `--max-dim` — check the dimensions of the sibling files you read in step
2 (`python3 -c "from PIL import Image; print(Image.open(p).size)"`) and land in the
same ballpark. A 64×64 placeholder replaced with a 4000px art file is as wrong a
mismatch as the reverse.

## 6. Sanity-check recognizability at real size

The postprocess script already wrote `..._preview_64.png`, `_preview_45.png`,
`_preview_32.png` next to the output — but the numbers that actually matter are the
real on-screen ones from step 1: `GridConfig.cellSize × the render-size multiplier`
for in-maze size, and roughly 32-40px for the build-menu button
(`.build-btn img` in `index.html`). Read the closest preview back and honestly ask:
does the silhouette and color read at a glance, or does it collapse into a blob? If
it's not working, that's a signal to simplify the SVG (fewer competing details,
bigger color-contrast regions, a clearer silhouette) rather than to just resize
differently — small icons live and die on silhouette, not detail.

## 7. Replace the asset and clean up compensations

Overwrite `game/assets/<name>.png` (the postprocess script already wrote directly
there if you pointed `--output` at it), and confirm `game/assets/src/<name>.svg`
from step 3 is saved and up to date with whatever you last rendered — the PNG and
the SVG should never drift apart. Then apply the step-1 findings:

- Flip any placeholder-compensating `tint: Some(...)` in `GameApp.scala`'s
  `BuildingVisuals` (or equivalent) map to `None` — leave gameplay-purpose tints
  (flash effects, etc.) untouched.
- Delete any matching `.build-btn#build-<slug> img { filter: ... }` rule in
  `index.html` that existed for the same reason.
- Do **not** add a `LICENSE-<name>.txt` in `game/assets/`. Those files exist only for
  art derived from third-party packs (Kenney, CraftPix, itch.io freebies — see the
  existing `LICENSE-*.txt` files for the pattern); hand-authored SVG art here is
  original and needs no attribution file.

## 8. Verify before wrapping up

- Confirm the alpha channel is really transparent, not white:
  `python3 -c "from PIL import Image; print(Image.open('game/assets/<name>.png').getpixel((0,0)))"`
  should show alpha `0` in a corner.
- `git diff --stat` — expect the PNG, the new/updated `game/assets/src/<name>.svg`,
  plus a small, surgical code diff (tint → None, one CSS rule removed). If the diff
  touches unrelated lines, you likely over-edited; back it out.
- Re-read the full-size PNG one more time as a final look before considering the
  task done.

## Animated multi-frame sprites (e.g. a unit's walk cycle)

Steps 0-8 above are for a single static icon. A unit like Zombie/Goblin/Wolf is instead
a numbered sequence (`walk-00.png`, `walk-01.png`, ...) played back as a loop — see
`AssetPaths.ZombieFrames`/`GoblinFrames`/`Wolf` in `GameApp.scala`. Hand-drawing each
frame independently is both slow and risky: tiny inconsistencies between frames (a
slightly different head size, a limb in the wrong place) show up as jitter the instant
the sequence plays. The fix is to build one **rigged** character and *pose* it per
frame programmatically, rather than redrawing it per frame:

1. Design the character as SVG body-part groups (head, torso, and one `<g>` per limb),
   each limb attached at a joint with `transform="translate(pivotX,pivotY)
   rotate(angle)"` so rotating that one group swings the whole limb around its
   shoulder/hip. Keep limbs as simple rigid capsules (a single rotating segment, no
   knee/elbow bend) unless the extra complexity earns its keep — for a stiff/shambling
   creature a rigid limb is often *more* on-character, not a shortcut.
2. Write a small Python generator (not 10 hand-authored SVGs) that computes each
   frame's joint angles from a walk-cycle function (e.g. `angle = amplitude *
   sin(2*pi*t + phase)`, with the two legs 180° out of phase, a vertical body bob at
   twice the leg frequency, and a small counter-swing on the arms) and emits
   `walk-00.svg` .. `walk-NN.svg` by formatting the same template with different
   angles. See `game/assets/src/zombie/generate.py` for a worked example.
   - **Watch for duplicate frames**: sampling a plain sine at N evenly-spaced points
     over one full period is mirror-symmetric around each peak, so two samples
     straddling a peak can land on the *exact* same angle — frame k and frame
     (period−k) render pixel-identical, quietly wasting frame slots. Add a small
     constant phase offset to the sampling grid (still a full sweep, so it still loops
     at the seam) to desync from that symmetry; `generate.py`'s `phase_eps` is the
     fix, and `assemble.py`/a quick `md5sum walk-*.png | sort` catches it if it
     recurs.
3. Render every frame through the usual `render.js` (large size, e.g. 600-800px, for
   clean edges), same as a static icon.
4. Crop and resize all frames **identically** — do NOT run the static-icon workflow's
   per-file independent alpha-trim on each frame, since trimming each to its own
   content box shifts the character around and makes it jitter in place instead of
   walking smoothly. Compute the union of all frames' bounding boxes once, crop every
   frame to that same rectangle, then resize all of them by the same factor. See
   `game/assets/src/zombie/assemble.py`, which also emits a `walk.gif` preview (frames
   composited onto an opaque background and upscaled — GIF doesn't do partial alpha
   well) in the same step.
5. Sanity-check the loop as an actual animation, not just a contact-sheet grid of
   frames — motion problems (a held/stuttering pose, a limb popping instead of
   swinging) are often invisible frame-by-frame but obvious once it plays. Send the
   `walk.gif` to the user with `SendUserFile` so they can actually see it move; a
   static Read of a GIF only shows one frame.
6. Match the target size to sibling *animated* units (goblin/wolf/zombie are all
   64-128px tall), not to the much larger building-icon sizes from step 5 above.
7. Same cleanup as a static icon: delete a stale `LICENSE-<name>.txt` if the old
   frames were third-party/unknown-provenance, fix any code comments that referenced
   it, and confirm no tint/CSS-filter hack needs removing.
8. If a lore doc embeds a representative frame (`![Zombie](.../zombie/walk-00.png)`),
   consider pointing it at the new `walk.gif` instead so the doc shows the actual
   motion — but check whether that doc is hand-written or **generated** first (grep
   for the asset path in `game/core/.../i18n/EntityNames.scala` and
   `game/sim/.../docgen/DocGenerator.scala`); if it's generated, edit the source table
   entry, not just the `.md` file, or the next doc-generation run will silently
   revert it.
