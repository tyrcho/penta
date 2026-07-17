# ADR 0010 - Split AiStrategy into LayoutPolicy x SpendingPolicy, add plunder archetypes

## Context

The user asked for every `AiStrategy` to be explicitly a combination of two independent
concerns: **spending** (how resources are turned into a building *kind*) and **layout**
(*where* on the grid that kind goes). Before this change only `CompositeStrategy` blended
these at all, and only partially — `resource`/`counter`/`maze` were three weights on one
class, while `TemplateStrategy`'s wall shape and its Grove-first/margin-fallback kind
choice were hardwired together in a separate class entirely. The user also asked for
selectable ladder entries to be named combinations (e.g. `comb-plunder`), for more
combinations to be explored, and for two specific scoring changes: resource spending
should account for a resource's production rate (not just its current stock, to avoid
permanently locking a strategy out of a currency with no way to replenish it), and ties
between equally-scored candidates should be broken randomly instead of always picking the
first one generated.

## Decisions

### `SpendingPolicy` and `LayoutPolicy`, combined by `ComposedStrategy`

New `SpendingPolicy` trait (`score(state, opponent, kind): Double`): `WeightedSpending`
(resource/counter blend, subsumes old resource-only/counter-only/balanced),
`PlunderSpending` (new — flat bonus for Chaos kinds regardless of the opponent's own
faction mix, racing the Chaos/plunder victory condition), `GrovePriority` (moved from
`TemplateStrategy` — Grove-first, margin-fallback otherwise), `FixedOrderSpending`.

New `LayoutPolicy` trait (`score(state, kind, cell): Double`, `Double.NegativeInfinity`
meaning "never here"): `NoLayoutPreference`, `FreeformLayout` (moved verbatim from
`CompositeStrategy`'s `dangerScore`/`pathDangerScore`), `TemplateLayout` (wraps
`MazeTemplate.comb`/`combVertical` as a score — earliest not-yet-built template cell
scores highest, everything else scores `-Infinity`).

`ComposedStrategy(layout, spending, layoutWeight, spendingWeight)` replaces both
`CompositeStrategy` and `TemplateStrategy`: scores every `(kind, cell)` candidate jointly
(`spendingWeight * spending.score(...) + layoutWeight * normalize(layout.score(...))`),
drops any candidate a `LayoutPolicy` vetoes (`-Infinity`) whenever at least one candidate
survives, and no-ops if every candidate is vetoed — this is what keeps `TemplateLayout`
combinations strictly on-template, exactly like the old `TemplateStrategy`.

### Growth-aware resourceScore, and the lockout it didn't fix on its own

`SpendingPolicy.resourceScore` now penalizes spending a resource with zero active
production (`CombatEngine.productionPerSec` — nothing on the board replenishes it, so the
spend is one-way) and rewards a candidate that would *establish* production of a
currently-unproduced resource (`growthBonus`).

The penalty alone wasn't enough: a first tournament run left `maze-only` and
`resource-maze` both stuck at 0 wins / 0 losses (every match a draw). `sim/run
resource-maze linear 1 --log` showed why — both built exactly 5 Watchtowers then froze
forever, Wood pinned at 0. Watchtower and Grove both cost Wood 10; the margin penalty
alone is identical for both, but Watchtower's *average* still wins because it also spends
Light (abundant, unpenalized once other Watchtowers already produce it), pulling its
score up, while Grove's only term is Wood's and sinks with it — the kind that would
actually fix the shortage kept losing to one that merely shares its cost. `growthBonus`
fixes this by crediting a candidate directly for producing a resource that currently has
no producer at all, independent of the margin math. `resource-maze` (which weighs
resource spending) now wins normally; `maze-only` (whose weight is `(resource=0,
counter=0)`) still hits the freeze, since its own weight zeroes `growthBonus` out along
with everything else resource-aware — a real, reproducible property of "zero
resource-awareness" as an archetype, not a leftover bug (see `AiStrategy.ladder`'s doc).

### Randomized tie-breaking

`ComposedStrategy` takes a `random: scala.util.Random` (default unseeded for real play;
tests/tournament runs can inject a seeded one for reproducibility) and picks uniformly at
random among every candidate tied at the max score, instead of always taking the first one
in generation order. `LinearStrategy` is unaffected — it has no scored ties.

This retires the "no randomness anywhere in `core`/`sim`" invariant the ladder used to
lean on (see ADR 0008/0009): repeated matches between the same two `ComposedStrategy`
entries can now produce different outcomes, so ranking the ladder needs averaging over
several matches per pairing, not one.

### New spending/layout combinations, ladder re-measured

Added `maze-counter` (`FreeformLayout` + pure counter, previously unreachable inside one
`CompositeStrategy` instance since `maze-only`'s weights were fixed), `comb-resource` /
`comb-vertical-resource` (wall template + resource-margin spending instead of Grove-first),
and `maze-plunder` / `comb-plunder` / `comb-vertical-plunder` (any layout + the new
`PlunderSpending`).

Full round-robin (`sim/runMain towerdefense.sim.tournament 3`, 91 pairings, 3
matches/pairing to average out the new randomized ties) re-ordered the ladder weakest to
strongest:

```
linear, maze-only, comb, comb-vertical, counter-only, resource-only, maze-counter,
comb-vertical-resource, comb-resource, balanced, comb-plunder, comb-vertical-plunder,
maze-plunder, resource-maze
```

Top tier (`comb-plunder`, `comb-vertical-plunder`, `maze-plunder`, `resource-maze`) all
went 27-12-0 — no losses at all — ranked among themselves by Elo. Racing Chaos buildings
(`PlunderSpending`) and a diversified, growth-aware resource economy both clearly beat
every fixed-Grove-first wall and every non-plunder blend.

## Verification

`sbt "coreJVM/test" "sim/test"` (150 `core` tests, all green; `sim`'s 2 pre-existing
`MatchLogTest` failures are unrelated locale-dependent number formatting in code this
change never touched) and `sbt "coreJS/Test/compile" "js/Compile/compile"` both green.
Tests written first per CLAUDE.md convention throughout (`SpendingPolicyTest`,
`LayoutPolicyTest`, `ComposedStrategyTest`).
