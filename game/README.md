# tower-defense-poc

Browser-based maze tower defense POC. ScalaJS + PixiJS v8, works on desktop and mobile (touch).

## Usage

```sh
make dev     # compile + watch + serve on http://localhost:8082
make test    # run unit tests (domain logic, under Node.js)
make build   # production JS build → public/
```

**Play:** tap/click an empty grid cell to place a tower (costs gold). Enemies spawn top-left and path-find to the bottom-right goal, automatically re-routing around any towers you place — you can't seal off the only route. Killing an enemy grants gold; an enemy that reaches the goal costs a life.

## Implementation details

**Stack:** ScalaJS 1.16, browser-only (no JVM target). No npm, no Vite — sbt with an in-process Java HTTP dev server (same as `text-maps`). PixiJS v8 is loaded from a CDN `<script>` tag and wrapped in a minimal `js.native` facade.

**Project layout:**
- `core/` — pure game logic (grid, BFS pathfinding, combat, tower placement), no Pixi/DOM dependency. `munit` tests run under Node.js.
- `js/` — PixiJS rendering + input wiring only; reads `core`'s `GameState` each tick and draws it.

**Maze pathfinding** (`core/domain/Pathfinding.scala`, `CombatEngine.scala`): enemies don't follow a fixed path. Every tick, a grid BFS finds the shortest route from each enemy's current cell to the goal cell, treating tower cells as obstacles, and the enemy steps one cell towards that route. Placing a tower re-routes everyone in flight automatically, since the path is never cached. `Placement.tryPlaceTower` rejects any tower that would fully block the only route from spawn to goal.

**Rendering** (`js/main/GameApp.scala`): grid cells are `PIXI.Graphics` rects; towers, enemies and projectiles are `PIXI.Sprite`s using real art (`assets/`, Kenney's CC0 "Tower Defense Top-Down" pack). A root `Container` is scaled to fit the window every tick against a fixed 800×450 logical design size, so the same build works on any viewport.

**Animation:** towers rotate their turret to aim at their current target; enemies rotate to face their next path step (reusing the same `Pathfinding` BFS the domain uses to move them); projectiles rotate to face the enemy they're chasing. Firing a shot and a projectile hitting/expiring both spawn a short `PIXI.AnimatedSprite` flame effect (`assets/flame1-4.png`) that plays once and removes itself.

Decisions are recorded as ADRs in `docs/adr/`.
