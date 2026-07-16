# ADR 0006 - Comb Maze Templates for AI Strategies

## Context

The user asked to "teach some of the AIs to make more efficient mazes" after seeing a
match where the human-built maze (a dense, winding, hand-designed layout) forced a much
longer enemy path than the AI's sparser one. `CompositeStrategy`'s `maze-only` weighting
(see ADR 0004, [CompositeStrategyTest]) already scores candidates by resulting path
length plus aura-damage exposure, but it's a one-cell-at-a-time greedy heuristic, not a
plan for the maze's overall shape.

## Decisions

### Tried a lookahead heuristic first, reverted it

The first attempt extended `CompositeStrategy.dangerScore` with a bounded one-ply
lookahead (score a candidate by the best *second* building reachable after it, not just
its own immediate path length), to stop the greedy picker from scattering buildings
across locally-attractive but strategically dead-end cells. Empirically (via hand-built
fixtures exercising the real BFS/grid), it never actually changed the picked cell versus
plain `dangerScore` in scenarios that came up naturally, and constructing a fixture where
it would provably diverge required contrived, hard-to-justify wall geometry. The user
asked for a simpler approach instead — predefined maze layouts — so this was reverted
rather than shipped as untested complexity.

### Predefined templates, not a scoring heuristic

`MazeTemplate.comb`/`combVertical` are fixed wall layouts an `AiStrategy` builds toward
one cell per tick, rather than picking each cell by a live score. A concentric-ring
spiral — the shape that visually matches "efficient winding maze" best — was tried and
does **not** work on this grid: since `GridConfig.spawnCell` and `goalCell` both sit on
the grid's outer boundary, `Pathfinding.shortestPath` can always route around any inner
ring by walking the outer boundary alone, so inner rings never land on the shortest path
regardless of gap placement (verified with several ring/gap arrangements, including a
minimal-cut carve and a turtle-style shrinking-frame spiral — every variant either got
bypassed, at the unobstructed baseline length of 23, or ended up disconnected). Only
walls that fully span the grid, leaving exactly one gap, force a detour — a "comb" of
alternating full-width teeth is the simplest shape with that property; `combVertical` is
the same shape rotated 90°. On the 12×12 grid this takes the shortest path from 23 cells
(open board) to 67.

### Any build order is *safe*, but not any order is *effective* — found via `make sim`

`TemplateStrategy` builds whichever of the template's not-yet-built cells it can afford
next. Building cells in any order can never disconnect spawn from goal: adding walls only
removes passable cells, so if the *fully built* template leaves spawn and goal connected,
every partial subset of it (any cells built so far, in any order) is a strict superset of
open cells relative to the finished maze, hence at least as connected —
`MazeTemplateTest`'s "every subset of a reachable template's walls is also reachable"
exercises this directly (20 random subsets) rather than just asserting it in a comment.
`Placement.tryPlaceBuilding`'s `WouldBlockPath` check can therefore never reject a
template cell.

Safety isn't effectiveness, though: an early version built cells sorted as plain
`(col, row)` tuples, which sorts by column first — that scatters builds evenly across all
5 tooth rows instead of finishing row 1 before starting row 3, and a tooth row with *any*
extra opening beyond its one designated gap blocks nothing at all (BFS just uses the
nearer gap). Only a *fully completed* row starts forcing anything. Caught by running
`make sim maze-only comb 40`: comb lost 0-40 despite the shape being structurally correct
and approval-tested. Fixed by having `MazeTemplate.comb`/`combVertical` return an ordered
`List[(Int,Int)]` (row-major / column-major respectively — row 1's cells in full before
row 3's) instead of an unordered `Set`, with `TemplateStrategy` walking that order as
given rather than re-sorting it.

### Forest first, not cost order — also found via `make sim`

`TemplateStrategy`'s per-cell building-kind choice initially copied `LinearStrategy`'s
descending-wood-cost order verbatim. That's wrong for a wall-building strategy
specifically: `Forest` is the only `BuildingKind` with a combat aura
(`CombatEngine.applyDamageSources`), so a wall built from `Forest` doesn't just lengthen
the enemy's path, it kills them outright, same as why `CompositeStrategy`'s `maze-only`
always prefers `Forest` (see its `dangerScore` doc, ADR 0004). With cost order, comb was
building whatever `Church`/`Labyrinth`/`Watchtower` it could afford instead — forcing a
detour but landing zero damage. `buildOrder` now tries `Forest` first, falling back to
the original cost order only when wood specifically is scarce.

### Approval tests, not just coordinate assertions

`MazeTemplateTest` checks in the exact ASCII render of each template at the game's real
12×12 dimensions (`'x'` wall, `'.'` open, `'S'`/`'G'` spawn/goal) as the human-reviewed
source of truth for the shape, per the user's explicit request for approval tests —
diffing a raw `Set[(Int,Int)]` on a future change wouldn't be reviewable at a glance the
way the picture is. Structural assertions (reachable, path length more than double the
open-board baseline) sit alongside the render so a shape change that breaks the maze
fails even if nobody re-reads the picture.

### Appended to the ladder, spot-checked rather than fully measured

`"comb"` and `"comb-vertical"` are appended after `"maze-only"` in `AiStrategy.ladder`
rather than inserted at a specific rank. Post-fix, `make sim` spot checks (30 matches per
pairing, not the full 15-20-per-pairing round robin the rest of the ladder was measured
with) show comb beats `linear` 30-0 and stalemates `maze-only` 30-30 — roughly
`maze-only`'s own tier. Appending rather than reordering the existing entries reflects
that: those entries' positions come from the full round robin, comb's from a narrower
spot check.
