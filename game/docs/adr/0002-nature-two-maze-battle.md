# ADR 0002 - Nature Faction, Two-Maze Battle

## Context

[ADR 0001](0001-architecture.md) built a generic single-maze tower-defense tech demo, not tied to the game design vault. This iteration implements the vault's actual Nature faction (`../../Resources/Nature/`) and turns the POC into a 1v1: you vs a simple AI, each defending their own maze while sending units into the other's.

## Decisions

### Forest replaces the generic Tower; wood replaces gold

`Forest` (`core/domain/model.scala`) uses the numbers from `Forest.md` verbatim, and `Balance.scala` cites the source file next to each constant: costs 10 wood, produces 1 wood/sec, deals 2 dmg/sec to any enemy on an adjacent cell (no targeting, no projectiles — it's a passive aura, not a ranged shooter), and sends an Elf every 10s. The old ranged Tower/Projectile/cooldown/targeting mechanic is gone entirely; `CombatEngine` lost its projectile-stepping code as a result.

Where the vault doesn't specify a number, `Balance.scala` says so explicitly ("POC default") rather than inventing a value silently: Elf's own attack (it has none — see below), Elf's movement speed, and the starting wood stipend (10, exactly one Forest's cost — otherwise neither side could ever afford their first building).

### Elf is a walking HP pool, not a fighter — clarified with the user mid-implementation

`Elf.md` only said "produced by the Forest"; the user clarified it has 10 HP and is "sent to the opponent's maze" (not, as first suggested, back into its own maze). It reuses the existing `Enemy` case class unchanged — from a maze's own point of view, any unit walking it is hostile, whether it's a generic intruder or an opponent's Elf. Elf has no attack of its own: it only dies to that maze's Forest auras or reaches the goal and costs a life, exactly like the original POC's generic enemies. Giving Elf its own attack stat wasn't specified anywhere and would need combat numbers invented from nothing.

### Two MazeStates, one BattleState — Elf cross to the *opponent's* maze

`BattleState` (`core/domain/BattleEngine.scala`) holds one `MazeState` per player, identical rules for both. `BattleEngine.tick` runs `CombatEngine.tick` on each maze independently, then delivers each maze's spawned-Elf count into the *other* maze at its spawn cell. This is the entire cross-maze mechanic — no shared mutable state, no message queue, just "count of Elf launched this tick" passed sideways between two otherwise-independent pure updates.

### Grid is 12×12 (was 16×9), two side by side in the renderer

Per the user's request. `GridConfig` is grid-size-agnostic (`Pathfinding` and `cellOf` derive everything from `cols`/`rows`), so this was a one-line change on the domain side. The renderer (`js/main/GameApp.scala`) lays both mazes out in one logical design area (`2 × width + gap`) that scales to fit the screen together; only the left (player) maze accepts taps.

### AiController: dumbest opponent that works, deterministic

Scans cells in row-major order and places a Forest on the first one `Placement.tryPlaceForest` accepts, as soon as it can afford one. No randomness (keeps it trivially testable), no strategy beyond "build when possible" — a placeholder to make the battle playable, not a claim that it's a good opponent.

### AI build-rate cap — found by actually running it, not specified upfront

Wood production compounds (more Forests → more wood/sec → more Forests). Running the game surfaced that this lets the AI tile its entire maze within seconds, since it (unlike a human) can attempt a build every single frame. `BattleState.aiBuildCooldownMs` caps it to one build per `Balance.AiBuildCooldownMs` (3s, a POC default), verified with a regression test (`BattleEngineTest`, "the AI cannot build a second forest before its cooldown elapses, even with excess wood") after the bug was observed live in the browser. The cooldown lives on `BattleState`, not `MazeState`, since it's specifically about pacing the AI opponent, not a rule of the maze itself — keeps `MazeState` symmetric between player and AI.

### Deferred, not forgotten

Lives can go negative (no game-over state). The AI never diversifies past Forests. Chaos units, a plunder/plunder mechanic, and the actual Nature/Chaos victory conditions from `../../Victoire.md` are explicitly the next planned increment, not attempted here.
