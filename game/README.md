# tower-defense-poc

Browser-based, symmetric 1v1: you vs a simple AI, each defending a 12×12 maze while
launching units into the other's. Both sides have the exact same building menu —
implements the vault's Nature and Chaos faction definitions (`../Resources/Nature/`,
`../Resources/Chaos/`) and the matching victory conditions from `../Victoire.md` —
ScalaJS + PixiJS v8, works on desktop and mobile (touch).

See `CLAUDE.md` for the two standing project rules (symmetry, tests-first).

## Usage

```sh
make dev      # compile + watch + serve on http://localhost:8082
make test     # run unit tests on the JVM (fast — for iterating on AI strategies)
make test-js  # run unit tests under Node.js (parity check for the browser build)
make build    # production JS build → public/
```

**Play:** pick "Build Forêt" or "Build Cave" in the toolbar (hover either button to see
its cost), then tap/click an empty cell in *your* maze (left) to place one. A Forêt
produces wood over time, damages any enemy on an adjacent cell, and every 10s sends an
Elf into the opponent's maze. A Cave costs wood + fire, produces fire, and every 5s sends
a Goblin. Either unit reaching a goal plunders 1 wood + 1 fire from that maze, credited
to whoever sent it. Hover any building or unit on the board to see its live stats.

**Winning:** build 10 Forêts (Nature) or plunder 20 resources total (Chaos) — either
side can win via either condition, whichever they reach first.

## Implementation details

**Stack:** ScalaJS 1.16. `core` cross-compiles to the JVM (fast local iteration, e.g. on
AI strategies — `sbt coreJVM/console` or a quick JVM main, no Node/browser needed) and
to JS (the actual browser app). No npm, no Vite — sbt with an in-process Java HTTP dev
server (same as `text-maps`). PixiJS v8 is loaded from a CDN `<script>` tag and wrapped
in a minimal `js.native` facade.

**Project layout:**
- `core/` — pure game logic (grid, BFS pathfinding, combat, Forêt/Cave placement, the
  AI, the two-maze battle orchestration, victory conditions), no Pixi/DOM dependency.
  `munit` tests run on both the JVM and under Node.js.
- `js/` — PixiJS rendering + input wiring only; reads `core`'s `BattleState` each tick
  and draws both mazes.

**Symmetric by construction** (see `CLAUDE.md`): both mazes can build either Forêt or
Cave, `AiController.maybeBuild` tries either, `VictoryConditions.evaluate` checks either
maze against either condition, and `Balance.StartingWood`/`StartingFire` are equal.
Elf and Goblin are mechanically identical (both plunder on arrival) — they differ only
in which building spawns them, at what rate, and their sprite.

**Two mazes, one battle** (`core/domain/BattleEngine.scala`): `BattleState` holds a
`MazeState` per player, identical rules for both. Each maze's Forêts send Elf, and each
Cave sends Goblin, into the *other* maze's `MazeState` — that's the entire cross-maze
mechanic. Because wood/fire production compounds (more buildings → more resources/sec →
more buildings), the AI is throttled to at most one build per `Balance.AiBuildCooldownMs`
(3s) — without that cap it tiles its whole maze in seconds, discovered by actually
running it.

**Faction definitions implemented** (`core/domain/Balance.scala` cites the source for
each number): a Forêt costs 10 wood, produces wood/sec (tuned down after playtesting the
compounding economy), deals 2 dmg/sec to adjacent enemies, and sends an Elf every 10s.
A Cave costs wood + fire, produces fire/sec, and sends a Goblin every 5s — Cave has no
combat ability, unlike Forêt's aura. All straight from
`Forest.md`/`Elf.md`/`Cave.md`/`Goblin.md`. Where the vault doesn't specify a number
(unit speed, starting resources, AI pacing, the two victory targets — Victoire.md leaves
both as an unfilled "XX"), `Balance.scala` calls it out as a "POC default" instead of
silently guessing.

**Plunder** (`CombatEngine.moveEnemies`, `BattleEngine.creditPlunder`): any unit reaching
its maze's goal steals up to 1 wood + 1 fire from that maze, clamped to what's actually
there. `CombatEngine` (single-maze, pure) only reports how much was stolen;
`BattleEngine` is what actually transfers it to the opponent's economy and plunder
tally, since crediting the *other* maze is a cross-maze concern the same way unit
delivery is.

**Maze pathfinding** (`core/domain/Pathfinding.scala`, `CombatEngine.scala`): units
don't follow a fixed path. Every tick, a grid BFS finds the shortest route from each
unit's current cell to its maze's goal, treating that maze's Forêt *and* Cave cells as
obstacles. Placing a building re-routes everyone in flight automatically, since the
path is never cached. `Placement` rejects any placement that would fully block the only
route from spawn to goal.

**Rendering** (`js/main/GameApp.scala`): grid cells are `PIXI.Graphics` rects; Forêts,
Caves, Elf and Goblin are `PIXI.Sprite`s using real art (`assets/`, Kenney's CC0
"Tower Defense Top-Down" pack) — the Cave tile is recolored with `Sprite.tint` since the
source art is a cool gray rock and Chaos reads better warm. Both mazes are scaled
together to fit the window every tick against a fixed logical design size. Only the
left (player) maze reacts to taps.

**Hover tooltips:** one custom tooltip div serves both the build buttons (cost, on
`mouseenter`/`mousemove`/`mouseleave`) and every building/unit sprite (`pointerover`/
`pointerout` via `wireHover`); for sprites, its text is refreshed every tick from the
live `BattleState` (`updateTooltip`), so HP and spawn
countdowns shown in it stay current without needing to re-hover.

**Animation:** enemies rotate to face their next path step (reusing the same
`Pathfinding` BFS the domain uses to move them). A short `PIXI.AnimatedSprite` flame
effect (`assets/flame1-4.png`) plays once at a building when it launches a unit, and
once at an enemy's last position when it dies or reaches the goal.

**Game over:** the battle freezes (`BattleEngine.tick` short-circuits) the instant
`VictoryConditions.evaluate` returns a result, and the UI shows who won and why. There
is no "lives"/overrun fallback — that was never a vault concept, so a match currently
runs until one of the two real conditions is met.

**Known gaps, intentionally deferred:** the AI never diversifies beyond
Forêt-then-Cave-by-availability; only Nature vs Chaos is wired up (the other three
factions and their victory conditions in `../Victoire.md` are still just narrative
text); no research/corruption mechanics.

Decisions are recorded as ADRs in `docs/adr/`.
