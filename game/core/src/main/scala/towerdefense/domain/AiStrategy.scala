package towerdefense.domain

// A build-decision maker for one maze. Side-agnostic by design (see CLAUDE.md's
// symmetry rule): anything that can drive the `ai` slot in BattleState must be equally
// usable to drive the `player` slot, so this takes both mazes as plain MazeState and
// returns the (possibly unchanged) state for the side it's deciding for. `opponent` is
// unused by strategies that don't react to the other side (LinearStrategy).
trait AiStrategy:
  def maybeBuild(state: MazeState, opponent: MazeState): MazeState

  // Default no-op: every strategy shipped so far only ever adds buildings, never tears
  // one down. A future strategy could override this to reshape its own maze (e.g.
  // relocating a Forest into a better chokepoint — see CompositeStrategy's dangerScore),
  // using Demolition. Symmetric with maybeBuild and driven the same way by
  // BattleEngine.tick; unlike maybeBuild it isn't cooldown-throttled, since Demolition
  // itself has none either (see Demolition's doc).
  def maybeDestroy(state: MazeState, opponent: MazeState): MazeState = state

  // Default no-op: every strategy shipped so far only builds new cells, never upgrades
  // an existing Grove/Forest into the next tier (see BuildingSpecs.upgradesTo). Driven
  // the same way as maybeBuild by BattleEngine.tick, sharing its cooldown (upgrading
  // compounds the economy just like building does, so it gets paced the same way).
  def maybeUpgrade(state: MazeState, opponent: MazeState): MazeState = state

object AiStrategy:
  // Shared "first that works" maybeUpgrade body: try each of the strategy's own buildings
  // in order, upgrade the first one that's both eligible (BuildingSpecs.upgradesTo) and
  // affordable, leave state untouched if none qualify. No attempt to pick the "best" one
  // to upgrade — matches maybeBuild's own simplicity in the strategies that use this.
  // Was duplicated verbatim across LinearStrategy, TemplateStrategy, and CompositeStrategy
  // before being pulled out here.
  def upgradeAnyAffordable(state: MazeState): MazeState =
    state.buildings.iterator
      .flatMap(b => Placement.tryUpgradeBuilding(state, b.col, b.row).toOption)
      .nextOption()
      .getOrElse(state)

  // Ordered weakest to strongest by measured head-to-head win rate: a full round-robin
  // across every entry below (`sim/runMain towerdefense.sim.tournament`). Re-measured
  // here after two CompositeStrategy fixes (a missing maybeUpgrade override, then Grove
  // being undervalued against non-upgradeable kinds — see CompositeStrategy's doc) landed
  // in the same session, since both directly change how the maze-weighted entries
  // (maze-only, balanced) actually play.
  //
  // Match count turned out not to matter: there is no randomness anywhere in `core`/`sim`
  // (no `scala.util.Random`, nothing seeded) — every match between the same two
  // strategies at the same maxTicks/deltaMs has exactly one deterministic outcome. A
  // 3-matches-per-pairing run and a 15-matches-per-pairing run produced numbers that were
  // exact 5x multiples of each other across every strategy, confirming this — "matches
  // per pairing" beyond 1 buys no statistical confidence today, it just repeats the same
  // game. Left the CLI's default above 1 anyway (useful once/if any real randomness gets
  // added later), but this ladder didn't need it.
  //
  // resource-only is now the outright strongest, not maze-only: its affordability-margin
  // heuristic stumbles into cheap Watchtowers (good margin off abundant Light), and
  // Watchtower deals real ranged damage every tick with no upgrade chain required, unlike
  // Forest's aura — see a real transcript via `sim/run resource-only maze-only 1 --log`.
  // comb and comb-vertical tie on win rate but comb never lost a single match across
  // either round-robin (0 losses out of 90 games played), while comb-vertical did lose
  // some — comb ranks above it on that tiebreak. maze-only and balanced both improved
  // substantially from the two fixes above but still trail comb/comb-vertical/
  // resource-only — pure maze-weighting (maze-only) or a three-way blend that includes it
  // (balanced) is a genuinely weaker archetype here than a fixed, disciplined
  // full-width-wall template, not just a bug away from "outright strongest" as this
  // comment used to claim. counter-only and balanced tie on win rate too; balanced ranks
  // above it for the same fewer-losses/more-draws tiebreak reason as comb over
  // comb-vertical. Drives both the simulator's named presets (`all`) and the browser's
  // difficulty selector / auto-advance-on-win, so both walk this measured order.
  val ladder: Seq[(String, AiStrategy)] = Seq(
    "linear" -> LinearStrategy,
    "counter-only" -> CompositeStrategy(Weights(resource = 0.0, counter = 1.0, maze = 0.0)),
    "balanced" -> CompositeStrategy(Weights(resource = 1.0, counter = 1.0, maze = 1.0)),
    "maze-only" -> CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0)),
    "comb-vertical" -> TemplateStrategy(MazeTemplate.combVertical),
    "comb" -> TemplateStrategy(MazeTemplate.comb),
    "resource-only" -> CompositeStrategy(Weights(resource = 1.0, counter = 0.0, maze = 0.0))
  )

  val all: Map[String, AiStrategy] = ladder.toMap
