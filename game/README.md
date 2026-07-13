# tower-defense-poc

Browser-based 1v1 maze battle: you vs a simple AI, each defending a 12×12 maze while
launching units into the other's. Implements the vault's Nature faction
(`../Resources/Nature/`) — ScalaJS + PixiJS v8, works on desktop and mobile (touch).

## Usage

```sh
make dev     # compile + watch + serve on http://localhost:8082
make test    # run unit tests (domain logic, under Node.js)
make build   # production JS build → public/
```

**Play:** tap/click an empty cell in *your* maze (left) to place a Forêt (costs bois).
A Forêt produces bois over time, damages any enemy on an adjacent cell, and every 10s
sends an Elfe into the **opponent's** maze — not your own. The AI does the same on its
maze (right). An Elfe that reaches a maze's goal cell costs that maze's owner a life.

## Implementation details

**Stack:** ScalaJS 1.16, browser-only (no JVM target). No npm, no Vite — sbt with an
in-process Java HTTP dev server (same as `text-maps`). PixiJS v8 is loaded from a CDN
`<script>` tag and wrapped in a minimal `js.native` facade.

**Project layout:**
- `core/` — pure game logic (grid, BFS pathfinding, combat, Forêt placement, the AI,
  and the two-maze battle orchestration), no Pixi/DOM dependency. `munit` tests run
  under Node.js.
- `js/` — PixiJS rendering + input wiring only; reads `core`'s `BattleState` each tick
  and draws both mazes.

**Two mazes, one battle** (`core/domain/BattleEngine.scala`): `BattleState` holds a
`MazeState` per player (identical rules for both). Each maze's Forêts send their Elfe
into the *other* maze's `MazeState`, not their own — that's the entire game loop.
`AiController` is the dumbest opponent that works: as soon as it can afford a Forêt, it
places one on the first free, path-preserving cell. Because bois production compounds
(more Forêts → more bois/sec → more Forêts), the AI is throttled to at most one build
per `Balance.AiBuildCooldownMs` (3s) — without that cap it tiles its whole maze in
seconds, discovered by actually running it.

**Nature definitions implemented** (`core/domain/Balance.scala` cites the source for
each number): a Forêt costs 10 bois, produces 1 bois/sec, deals 2 dmg/sec to adjacent
enemies, and sends an Elfe (10 HP) every 10s — straight from `Foret.md`/`Elfe.md`. Where
the vault doesn't specify a number (Elfe's own attack, movement speed, starting bois,
AI pacing), `Balance.scala` calls it out as a "POC default" instead of silently guessing.

**Maze pathfinding** (`core/domain/Pathfinding.scala`, `CombatEngine.scala`): units
don't follow a fixed path. Every tick, a grid BFS finds the shortest route from each
unit's current cell to its maze's goal, treating that maze's Forêt cells as obstacles.
Placing a Forêt re-routes everyone in flight automatically, since the path is never
cached. `Placement.tryPlaceForet` rejects any Forêt that would fully block the only
route from spawn to goal.

**Rendering** (`js/main/GameApp.scala`): grid cells are `PIXI.Graphics` rects; Forêts
and enemies are `PIXI.Sprite`s using real art (`assets/`, Kenney's CC0 "Tower Defense
Top-Down" pack). Both mazes are scaled together to fit the window every tick against a
fixed logical design size, so the same build works on any viewport. Only the left
(player) maze reacts to taps.

**Animation:** enemies rotate to face their next path step (reusing the same
`Pathfinding` BFS the domain uses to move them). A short `PIXI.AnimatedSprite` flame
effect (`assets/flame1-4.png`) plays once at a Forêt when it sends an Elfe, and once at
an enemy's last position when it dies or reaches the goal.

**Known gaps, intentionally deferred:** lives can go negative (no game-over state yet);
the AI never diversifies beyond spawning Forêts; Chaos units, pillage/plunder, and the
actual Nature/Chaos victory conditions from `../Victoire.md` are next steps, not yet
implemented.

Decisions are recorded as ADRs in `docs/adr/`.
