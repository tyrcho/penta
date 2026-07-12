# ADR 0001 - Core Architecture

## Context

Tech-focused POC to prove out a tower-defense-like game with ScalaJS + PixiJS v8, deployable to GitLab Pages, mirroring the sibling project `text-maps` (sbt + ScalaJS, no npm/Vite, CDN-loaded JS library wrapped in a thin `js.native` facade, in-process Java dev server with live reload). Scope is intentionally minimal: one tower type, one enemy type, maze-style pathfinding. Not tied to this repo's 5-theme game design vault.

## Decisions

### ScalaJS + PixiJS v8 via a CDN facade (not npm/Vite/React)

Same reasoning as `text-maps`: no npm toolchain, no build step beyond sbt. PixiJS v8 is loaded from a CDN `<script>` tag in `index.html` (with SRI) and wrapped in a minimal `js.native` facade (`js/pixi/Pixi.scala`) covering only the API surface this POC needs (`Application`, `Container`, `Graphics`, `Sprite`, `AnimatedSprite`, `Assets`, `Ticker`, pointer events) — same pattern as the `LZString` facade in `text-maps/js/.../App.scala`.

### Browser-only build — no JVM target

Unlike `text-maps` (which has a JVM target for its native CLI's tests and a Scala Native CLI), this POC has no non-browser consumer. Both `core` (domain) and `js` (Pixi wiring) are plain ScalaJS projects; `core`'s `munit` tests run under Node.js via sbt-scalajs' default JS test environment (`sbt core/test`). No `sbt-crossproject` machinery is needed.

### Domain/render split (light hexagonal architecture)

`core/` holds all game rules — grid, pathfinding, combat, tower placement — as pure, immutable case classes and functions with no Pixi or DOM dependency. `js/` only wires PixiJS: it reads `GameState` each tick and draws it, and translates pointer input into calls to `core`'s `Placement`. This keeps the game logic unit-testable under Node without a browser or canvas.

### Live BFS pathfinding, not a fixed path (maze tower defense)

Enemies do not follow a hand-authored waypoint path. Each tick, `CombatEngine` re-runs a grid BFS (`Pathfinding.scala`) from each enemy's current cell to a fixed goal cell (bottom-right), treating every placed tower's cell as an obstacle, and steps the enemy one cell towards the result. Placing a tower re-routes all enemies in flight automatically — there is no cached path to invalidate, since the route is recomputed from scratch every tick.

This is why `Placement.tryPlaceTower` checks `Pathfinding.isReachable` before allowing a tower: it must reject any placement that would fully wall off the spawn cell (top-left) from the goal cell, the classic "maze tower defense" constraint (see e.g. Desktop Tower Defense / Kingdom Rush's blocking towers).

BFS is recomputed fresh every tick rather than cached, because the grid is small (16×9 = 144 cells) and towers change the maze at arbitrary times — caching would need explicit invalidation on every placement, which is more complexity than just recomputing.

### Real sprites (Kenney CC0), procedural rotation instead of hand-drawn walk cycles

Superseded the initial "Graphics primitives only" decision: towers, enemies and projectiles are now `PIXI.Sprite`s using individual PNGs from Kenney's "Tower Defense Top-Down" pack (`assets/`, CC0 — `assets/LICENSE-kenney.txt`), loaded once via `PIXI.Assets.load` before the game starts. The grid itself stays `PIXI.Graphics` rects; only the moving/interactive entities got real art.

The pack ships single static icons per unit, not multi-frame walk cycles, so "animation" for towers/enemies/projectiles is procedural rotation rather than frame-by-frame: enemies rotate to face their next path step (reusing `Pathfinding.shortestPath`, the same BFS the domain already runs to move them — no duplicated direction logic), towers rotate to aim at their nearest in-range target, and projectiles rotate to face the enemy they're chasing. This is standard for top-down vehicle/turret sprites and needed no extra art.

Genuine frame-based animation is used for hit/muzzle effects: `assets/flame1-4.png` are played once through a `PIXI.AnimatedSprite` whenever a tower fires (at the tower) or a projectile is removed (at its last position, whether from a hit or a vanished target), then the sprite removes itself via `onComplete`.

No textures beyond these ~8 small PNGs; no atlas/spritesheet parsing since the pack ships pre-cut individual tiles.

### Responsive scale-to-fit for mobile

The playfield is a fixed logical design size (800×450, i.e. the 16×9 grid at 50px cells). A root `Container` is scaled and centered to fit `app.screen` (which itself tracks `window` via Pixi's `resizeTo`) every tick. This makes the same build work on any phone/tablet/desktop viewport without separate layouts. Pointer input (not mouse-specific) unifies touch and mouse taps for free via Pixi's federated event system.

### GitLab Pages deploy

`.gitlab-ci.yml` at the repo root (required by GitLab Pages) runs `sbt js/fullLinkJS` inside `game/`, copies the linked JS + `index.html` into `public/`, and publishes that as the Pages artifact. Scoped to this subfolder so it doesn't interfere with the rest of the repo (an Obsidian vault).
