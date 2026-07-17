# ADR 0009 - Watchtower-Aware Scoring, Resource Diminishing Returns, Elo Rating, resource-maze

## Context

Follow-up to ADR 0008: after that round of tournament-driven fixes and the ladder
re-measurement, the user asked to review fresh match transcripts and suggest further
improvements, then said "try all of these" for the resulting four suggestions. Also
requested, mid-work, a simple Elo/MMR-style rating for the tournament standings.

## Decisions

### `CompositeStrategy.dangerScore` now credits Watchtower's ranged damage

Maze-weighted strategies previously only credited Forest/Jungle aura adjacency when
scoring a candidate placement — a Watchtower candidate scored identically to a harmless
Cave, even though Watchtower deals `Balance.WatchtowerDamagePerSec` (10, five times
Forest's aura) to anything within `WatchtowerRangeCells` (2, Chebyshev distance) and
needs no upgrade chain. `dangerScore` gained an `isRangedCandidate` flag (mirroring the
existing `isAuraCandidate`) and `pathDangerScore` now sums both aura and tower hits along
the routed path, reusing `CombatEngine.chebyshevDistance` (widened to `private[domain]`
for this) — the same distance metric Watchtower's own targeting uses. A regression test
proves `maze-only`'s `maybeBuild` now prefers a Watchtower over a Grove at the same cell.

### `CompositeStrategy.resourceScore` now has diminishing returns

A real transcript showed `resource-only` building Cave after Cave the entire match:
its affordability margin barely moves build to build (production keeps every currency
topped up), so the single best-margin kind wins forever. Fixed by dividing the raw
margin by `1 + count of that kind already built` — the first building of any kind still
scores at full margin, only repeats are discounted.

### `TemplateStrategy`'s non-Grove fallback is margin-aware, honestly limited

The fixed `Church > Labyrinth > Watchtower > Cave` fallback order is now chosen by the
same affordability-margin math instead, via a new `fallbackMarginScore`. Documented
honestly: since every fallback kind costs wood 10-40 and Grove (tried first) costs a
flat wood 10 with no other resource requirement, Grove wins outright whenever wood >= 10
— this fallback competition can currently only ever be reached with a single affordable
candidate (Cave, wood 5) under today's Balance numbers. The math is correct and
unit-tested; it just doesn't have a live scenario to differ from the old order yet.

### Weight-blend experiment: `resource-maze`, a new strongest ladder entry

`sim/runMain towerdefense.sim.tune resource-only 1 0.25 3000 100` swept every
`CompositeStrategy` weight combination on a 0.25 grid against `resource-only` (then the
strongest ladder entry) — 1 match/point sufficing given the simulation's determinism
(ADR 0008). 50 of 125 combinations beat `resource-only` outright, including 6 pure
resource+maze blends (`counter = 0`). `Weights(resource = 0.5, counter = 0.0, maze =
0.25)` was checked head to head against the full existing ladder and went 6-0-1 (a draw
only against `maze-only`, wins everywhere else including `resource-only`) — added as
`"resource-maze"`, the new strongest entry. Resource and maze scoring appear to compound:
maze routes the enemy path past Watchtower/Forest damage the same way `maze-only` does,
while the (now diminishing-returns) resource component keeps the economy diversified
enough to actually afford building that maze, instead of stalling on one currency pair
the way pure `resource-only` can. Appended at the end of `AiStrategy.ladder` — safe for
existing saves, since `Persistence` resolves by strategy name, not raw index.

### `EloRating` — a simple rating alongside win-rate standings

Win rate alone can't distinguish an unbeaten record against weak opposition from one
earned against the strongest strategies on the ladder. `tournamentStandings` now tracks
a per-strategy Elo rating (standard formula, 1500 starting rating, K-factor 32), updated
match by match in round-robin order, shown as an extra column in the `tournament` CLI's
output. Standings still rank by win rate, not Elo — Elo rides along as additional
context rather than replacing the existing ranking. Documented as a *simple* rating, not
a statistically rigorous one: `matchesPerPairing` repeats an identical deterministic
match, so the K-factor compounds across those repeats the same way a real repeated-game
Elo system would.

## Verification

`sbt "coreJVM/test" "sim/test"` (174 tests) and `sbt "coreJS/Test/compile"
"js/Compile/compile"` all green after every change in this ADR. Tests added first per
CLAUDE.md convention throughout.
