# ADR 0007 - Match-Log Flag for the Headless Sim

## Context

`sim/run` drives headless AI-vs-AI matches for comparing/tuning `AiStrategy`
implementations but only ever reported a final win/loss/draw tally — there was no record
of *how* a match played out. The user asked for a flag that writes a per-match transcript
detailed enough for a human or an LLM agent to review afterward and spot strategic
mistakes (slow builds, hoarded resources, missed upgrades, lopsided plunder, units dying
for nothing). The log is raw material for that review, not an auto-critique —
interpretation stays with whoever reads it.

## Decisions

### Plain text, one line per event — not JSON

The project has zero JSON libraries anywhere (`core`/`sim` depend only on `munit`), and a
line-based transcript is just as readable to a human or an LLM agent as JSON, arguably
more so since it needs no parsing step. It also matches the existing
`formatTallyTable`/`formatWeightTable` string-formatting style already in
`Simulator.scala`.

### Deaths/arrivals needed an additive `CombatEngine` change; everything else is a pure diff

Most of the transcript (`BUILD`/`UPGRADE`/`DESTROY`/`PLUNDER`) is reconstructed by
diffing two consecutive `BattleState` snapshots from the outside, in the new
`MatchLog.diff` (`sim/src/main/scala/towerdefense/sim/MatchLog.scala`) — no `core`
changes needed for those:
- Builds/upgrades/destroys come from diffing each maze's `buildings` list by `id`.
  `Placement.tryUpgradeBuilding` keeps the same `id` across a kind change, so a same-id
  kind change is unambiguously an upgrade, never confused with a destroy+rebuild.
- Plunder comes from diffing the already-cumulative `resourcesPlundered` field.

Creature deaths (Forest/Jungle aura, Watchtower) and arrivals with no plunder ability
(Paladin, Wolf) are different: both cases just silently vanish from `MazeState.creatures`
either way, indistinguishable from outside `CombatEngine`. Reporting them precisely
needed `CombatEngine.TickResult` to gain `deaths: List[Death]` (with a `DeathCause` —
Aura, Watchtower, or both, attributed by keeping the forest-only and watchtower-only
damage maps in `applyDamageSources` separate instead of merging them before computing
cause) and `arrivals: List[UnitKind]` (previously `moveCreatures` only reported plunder,
silently dropping any non-plundering arrival). Both fields are purely additive — every
existing `TickResult` field/caller is untouched.

### `BattleEngine.tick`'s signature never changes; `tickDetailed` is new

`BattleEngine.tick` is called every frame by the live browser game
(`js/src/main/scala/towerdefense/main/GameApp.scala`), so its signature and behavior
couldn't change. Instead, its body moved into a new `tickDetailed`, which returns
`(BattleState, TickEvents)` — the same `BattleState` `tick` always produced, plus the
per-side deaths/arrivals `CombatEngine.tick` already computed but the old `tick` threw
away. `tick` is now defined as `tickDetailed(...)._1`, so the two can never drift apart,
and every existing caller (`GameApp.scala`, `Simulator.runMatch`, `BattleEngineTest`)
needed zero changes.

### `runLoggedMatch` is a new function, not a parameter on `runMatch`

`Simulator.runMatch` (used by `runMatches`/`searchWeights`, which back `tune` and every
batch comparison) is untouched. `runLoggedMatch` is a separate function that drives
`tickDetailed` and formats every event through `MatchLog`, taking a single `String =>
Unit` I/O seam (`writeLine`) — testable with an in-memory buffer instead of a real file.
`run`'s `--log <path>` flag is what wires that seam to a `java.io.PrintWriter`.

### `--log`/`--log-every` are scanned out of `args` before the existing positional parsing

`run`'s arguments were already hand-parsed (not plain `@main` defaults) because sbt's
`runMain` doesn't fill in trailing omitted `@main` args — see the comment already there.
The two new flags are stripped out of `args` first, wherever they appear, so today's
`run linear balanced 100` keeps working unchanged. `--log` only ever logs the batch's
*first* match (a per-tick transcript of a 100-match run would be enormous and useless);
pair it with a small `matches` count, typically 1.

### Caught along the way: `TemplateStrategy` (ADR 0006) broke against the Grove/Forest/Jungle upgrade chain

An unrelated upstream pull (merged mid-session) introduced Nature's Grove→Forest→Jungle
upgrade chain — `Forest` is no longer directly buildable, only reachable via
`Placement.tryUpgradeBuilding`. `TemplateStrategy`'s `buildOrder` still listed `Forest`
first, which now always failed (`CannotBuildDirectly`), and it had no `maybeUpgrade`
override, so its comb/comb-vertical templates silently stopped building anything past
whatever `Church`/`Labyrinth`/`Watchtower`/`Cave` a match's resources happened to allow.
Fixed the same way `LinearStrategy` already had: build `Grove` (the only directly-buildable
Nature tier), and add a `maybeUpgrade` override that grows any of its own buildings —
matches are guaranteed to only be *its own* template cells — into the next tier when
affordable, so a template wall still becomes aura-dealing Forest over time.
