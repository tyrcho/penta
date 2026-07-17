# ADR 0008 - Tournament Tool, CompositeStrategy Fixes, ETA Reporting, Ladder Re-measurement

## Context

The user asked to run a mini tournament across the AI strategies and suggest
improvements based on the logs. What followed was a chain of concrete, evidence-driven
fixes rather than speculative tuning: each finding came from an actual match transcript
(`MatchLog`, ADR 0007), not a hunch.

## Decisions

### `sim/runMain towerdefense.sim.tournament` — new CLI command

Round-robins every entry in `AiStrategy.ladder` (each pairing once, `matchesPerPairing`
matches within it) and prints aggregate win/draw/loss standings. `tournamentStandings`
is the pure function; the `@main` wraps it with argument parsing and — see below — an
ETA reporter. Kept as a permanent addition (not a throwaway script) since this kind of
AI-experimentation loop is clearly going to get reused.

### Found via the first tournament: `CompositeStrategy` was missing `maybeUpgrade`

`resource-only` went a perfect 48-0 against every other strategy, including `maze-only`
(documented as "the outright strongest"). A logged transcript showed why: `maze-only`
built 7 Groves across a 709-tick match and upgraded **zero** of them, because
`CompositeStrategy` — unlike `LinearStrategy` and `TemplateStrategy`, both fixed earlier
this session for the same reason — never got a `maybeUpgrade` override. Its entire
`dangerScore` premise (routing the enemy path past an aura-dealing building) was
building harmless Groves the whole game; Grove has no combat aura, only Forest/Jungle
do, and those are upgrade-only. Fixed by adding the override; a fresh transcript
confirmed `maze-only` upgrades afterward.

### DRY: `AiStrategy.upgradeAnyAffordable`

The "upgrade any of my own buildings, first affordable" body was now duplicated
verbatim in three places (`LinearStrategy`, `TemplateStrategy`, and the just-fixed
`CompositeStrategy`). Pulled into a shared `AiStrategy.upgradeAnyAffordable`; all three
now delegate. Purely a refactor — no behavior change, existing tests untouched.

### Found via a second transcript: Grove was undervalued against non-upgradeable kinds

Even after the `maybeUpgrade` fix, `maze-only` still built 9 walls but only 3 Grove (one
upgraded) — 6 were `Cave`, which can never reach an aura tier. Cause: `dangerScore`
scored a new Grove candidate identically to a same-cell Cave/Labyrinth/Church/Watchtower
candidate (no credit for the aura it'll have the moment `maybeUpgrade` grows it), so
whichever was momentarily affordable won — often Cave, right after wood got spent on an
earlier upgrade. `isMazeAuraCandidate` now credits a new Grove candidate the same as an
already-aura-dealing building, so it wins outright on a tie instead of losing to
incidental affordability. This narrows the pattern but doesn't fully eliminate it —
genuinely reserving wood for a future Grove instead of spending it the moment something
affordable comes along would need lookahead this scoring doesn't have.

Watchtower's own strength (cheap, ranged, reliable, no upgrade chain needed) was
deliberately left alone — that's a design choice, not a bug: Forest is also a path to a
victory condition (Nature's forest count), so the two aren't meant to be directly
comparable on damage-per-wood alone.

### `ProgressReporter` — CLAUDE.md's new "long-running jobs report an ETA" rule

The first tournament ran for ~15 minutes with zero output; a later 15-matches-per-pairing
re-measurement ran for ~38 minutes, same silence. Recorded the general policy in
CLAUDE.md and implemented it: `run`/`tournament`/`tune` all wire a `ProgressReporter`
(prints to stderr, throttled to ~once per 2s, always fires on the first and last unit of
work so a job never looks hung nor spams) via an `onProgress`-style callback added to
`runMatches`/`tournamentStandings`/`searchWeights` — the same I/O-at-the-boundary
pattern `runLoggedMatch`'s `writeLine` already established, so the underlying functions
stay pure/testable by default (no-op callback) and only the CLI layer does I/O.

### Discovered while re-measuring: the simulation has no randomness at all

A 3-matches-per-pairing tournament and a 15-matches-per-pairing one, run at different
points in this session, produced win/draw/loss counts that were **exact 5x multiples**
of each other for every single strategy. Confirmed by grep: zero uses of
`scala.util.Random` (or any RNG) anywhere in `core`/`sim`. Every match between the same
two strategies at the same `maxTicks`/`deltaMs` has exactly one deterministic outcome —
"matches per pairing" beyond 1 buys no statistical confidence today, it just repeats the
identical game. The ladder's old comment claiming "15-20 matches per pairing" for
confidence was based on a misconception this session corrected.

### Ladder re-measured and reordered

Full round-robin (`sim/runMain towerdefense.sim.tournament`) after all the fixes above:

```
linear (0.00) < counter-only ≈ balanced (0.17, balanced ahead on fewer losses)
  < maze-only (0.33)
  < comb-vertical ≈ comb (0.67, comb ahead: 0 losses across 90 games)
  < resource-only (0.83, strongest)
```

`resource-only` overtook `maze-only` — its cheap-Watchtower economy deals real damage
with no upgrade chain needed, while pure maze-weighting (or a blend that includes it,
`balanced`) turns out to be a genuinely weaker archetype than a fixed wall template or
that Watchtower economy, not just a bug away from "outright strongest" as the old
comment claimed. `AiStrategy.ladder`'s order and doc comment, and the corresponding
`AiStrategyTest` assertion, were updated to match — this is player-facing (drives the
browser's difficulty selector and auto-advance-on-win), verified safe for existing saves
since `Persistence` resolves by strategy *name*, not raw index.
