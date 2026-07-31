package towerdefense.domain.ai

import towerdefense.domain.*
import towerdefense.domain.ai.chaos.{CombPlunder, CombVerticalPlunder, MazePlunder}
import towerdefense.domain.ai.loi.{CombLaw, MazeLaw}
import towerdefense.domain.ai.mort.{CombCorruption, MazeCorruption}
import towerdefense.domain.ai.nature.{CombNature, MazeNature}
import towerdefense.domain.ai.science.{CombScience, MazeScience}
import towerdefense.domain.combat.*
import towerdefense.domain.economy.*
import towerdefense.domain.grid.*

// A build-decision maker for one maze. Side-agnostic by design (see CLAUDE.md's
// symmetry rule): anything that can drive the `ai` slot in BattleState must be equally
// usable to drive the `player` slot, so this takes both mazes as plain MazeState and
// returns the (possibly unchanged) state for the side it's deciding for. `opponent` is
// unused by strategies that don't react to the other side (LinearStrategy).
trait AiStrategy:
  // The catalog/ladder key this strategy resolves by everywhere one is looked up by string
  // (AiStrategy.all, sim/run, sim/tournament, rateTournament's baseName, GameApp's
  // difficulty-select, Persistence's save/load) — carried as a field on the strategy
  // itself rather than a separate name-to-strategy table entry, so identity travels with
  // the value through composition (RateLimited) and derivation (ComposedStrategy.reseed's
  // own copy, which keeps the name field along with every other unchanged field).
  def name: String

  def maybeBuild(state: MazeState, opponent: MazeState): MazeState

  // Default no-op: every strategy shipped so far only ever adds buildings, never tears
  // one down. A future strategy could override this to reshape its own maze (e.g.
  // relocating a Forest into a better chokepoint — see CompositeStrategy's dangerScore),
  // using Demolition. Symmetric with maybeBuild and driven the same way by
  // BattleEngine.tick; unlike maybeBuild it isn't cooldown-throttled, since Demolition
  // itself has none either (see Demolition's doc).
  def maybeDestroy(state: MazeState, opponent: MazeState): MazeState = state

  // Default no-op: every strategy shipped so far only builds new cells, never upgrades
  // an existing Grove/Forest into the next tier, or a LaboFondamental into a specific lab
  // (see BuildingSpecs.upgradeOptions). Driven the same way as maybeBuild by
  // BattleEngine.tick, sharing its cooldown (upgrading compounds the economy just like
  // building does, so it gets paced the same way).
  def maybeUpgrade(state: MazeState, opponent: MazeState): MazeState = state

  // Default no-op, same shape as maybeUpgrade — a strategy that never builds a Science lab
  // has nothing to research anyway. Driven the same way, sharing the build cooldown
  // (research compounds a maze's economy/defense the same way building/upgrading does).
  def maybeResearch(state: MazeState, opponent: MazeState): MazeState = state

  // How long (ms) this strategy waits between build/upgrade/research attempts — see
  // BattleEngine.maybeActThrottled, which resets its cooldown to this value after every
  // attempt (successful or not). Defaults to the shared Balance.AiBuildCooldownMs so every
  // existing strategy keeps today's exact pacing with no code changes; wrap any strategy in
  // RateLimited to tune "how fast can this one build" independently of what/where it builds
  // (see the sim tournament's own use of it for comparing build speed across strategies).
  def buildCooldownMs: Double = Balance.AiBuildCooldownMs

  // Returns a copy of this strategy with its internal tie-break randomness (if any)
  // reseeded — default no-op identity, since most strategies (LinearStrategy, every plain
  // SpendingPolicy/LayoutPolicy combination that never ties) have no randomness of their
  // own to reseed. Only ComposedStrategy overrides this for real. Exists so a caller that
  // wants reproducible measurements (Simulator's own tuning/regression use, most
  // obviously — see its own doc) can pin down "which tied candidate gets picked" per
  // match without giving every AiStrategy implementation a seed constructor parameter.
  def reseed(seed: Long): AiStrategy = this

// Wraps any AiStrategy to override only how fast it may act (buildCooldownMs), delegating
// every actual decision — what to build, where, whether to upgrade/research/destroy — to
// `inner` unchanged. Lets "how fast" be tuned independently of "what"/"where" for any
// existing strategy without giving each one (ComposedStrategy, LinearStrategy, ...) its own
// cooldown constructor parameter — see AiStrategy.buildCooldownMs's doc. name is derived
// from inner's own name plus the speed, matching the catalog's historical "<base>@<n>s"
// format (e.g. "maze-corruption@8s") that AiStrategy.all/GameApp/Persistence resolve by.
case class RateLimited(inner: AiStrategy, override val buildCooldownMs: Double) extends AiStrategy:
  val name: String = s"${inner.name}@${(buildCooldownMs / 1_000.0).toInt}s"
  def maybeBuild(state: MazeState, opponent: MazeState): MazeState =
    inner.maybeBuild(state, opponent)
  override def maybeDestroy(state: MazeState, opponent: MazeState): MazeState =
    inner.maybeDestroy(state, opponent)
  override def maybeUpgrade(state: MazeState, opponent: MazeState): MazeState =
    inner.maybeUpgrade(state, opponent)
  override def maybeResearch(state: MazeState, opponent: MazeState): MazeState =
    inner.maybeResearch(state, opponent)
  override def reseed(seed: Long): AiStrategy = copy(inner = inner.reseed(seed))

object AiStrategy:
  // Shared "first that works" maybeUpgrade body: try each of the strategy's own buildings
  // in order, and for each, try each of its upgradeOptions in listed order (Grove has just
  // the one — Forest; LaboFondamental has 5) — upgrade at the first (building, option) pair
  // that's both eligible and affordable, leave state untouched if none qualify anywhere. No
  // attempt to pick the "best" building or option — matches maybeBuild's own simplicity in
  // the strategies that use this. Was duplicated verbatim across LinearStrategy,
  // TemplateStrategy, and CompositeStrategy before being pulled out here.
  def upgradeAnyAffordable(state: MazeState): MazeState =
    state.buildings.iterator
      .flatMap { b =>
        BuildingSpecs.upgradeOptions
          .getOrElse(b.kind, Nil)
          .iterator
          .flatMap(target =>
            Placement.tryUpgradeBuilding(state, b.col, b.row, Some(target)).toOption
          )
      }
      .nextOption()
      .getOrElse(state)

  // Mirrors upgradeAnyAffordable for Science's research instead of Nature's upgrade chain:
  // try each lab line in a fixed order, research the first affordable next level, leave
  // state untouched if none qualify (no lab owned, every owned lab maxed, or none
  // affordable). Shared unconditionally by ComposedStrategy/LinearStrategy the same way
  // upgrading already is — opportunistic, not weighed against building a new candidate.
  def researchAnyAffordable(state: MazeState): MazeState =
    ResearchSpecs.orderedLabs.iterator
      .flatMap(lab => Placement.tryResearch(state, lab).toOption)
      .nextOption()
      .getOrElse(state)

  // Every entry is a named object extending ComposedStrategy — a LayoutPolicy ("where" —
  // NoLayoutPreference, FreeformLayout's danger-maximizing scan, or a fixed MazeTemplate
  // wall) combined with a SpendingPolicy ("what" — WeightedSpending's resource/counter
  // blend, GrovePriority's Grove-first/margin-fallback, or one of the five faction rush
  // policies in chaos/mort/science/loi/nature). The five faction-specific rush strategies
  // (MazePlunder/MazeCorruption/MazeScience/MazeLaw/MazeNature and their comb/
  // comb-vertical siblings) live in a subpackage per faction alongside their own
  // SpendingPolicy and shared opening/tuning values — see e.g. chaos.ChaosShared. Every
  // other entry (generic LayoutPolicy x SpendingPolicy combinations, not tied to a single
  // faction's own victory condition) lives directly in this package.
  //
  // Ordered weakest to strongest by win rate averaged over a round-robin
  // (`sim/runMain towerdefense.sim.tournament <n>`), not a single deterministic game per
  // pairing — ComposedStrategy breaks ties between equally-scored candidates at random, so
  // repeated matches between the same two entries can produce different outcomes.
  //
  // maze-only is the one entry with 0 wins that still ranks fairly high: it has very few
  // losses too — a genuine, reproducible defensive lockout, not matchmaking luck (see its
  // own doc). No longer exposed as the AI difficulty ladder (see `ladder` below, which now
  // serves that role with a finer-grained, Elo-measured progression) — kept as `catalog`
  // purely as a pool of distinct LayoutPolicy x SpendingPolicy combinations that CLI tools
  // (sim/run, sim/tournament, rateTournament's baseName arg, ...) can still resolve by name
  // via `all`.
  //
  // Re-measured (`sim/runMain towerdefense.sim.tournament 2`, all 16 entries) after a
  // direct manual rebalance of Balance.scala (commit "balance"): Grove/Forest/Jungle/
  // Cave/Labyrinth/Eglise all got cheaper, Watchtower's Light cost roughly quadrupled, and
  // StartingShadow/StartingCrystal both doubled (10 -> 20). Buildings across every faction
  // got cheaper at once, so ANY strategy now ramps its economy fast enough to reach
  // Chaos's plunder target well before slower victory conditions (Forest count,
  // corruption, research) come into range — plunder races, not sustained economic
  // advantage, now decide most matches.
  //
  // linear, comb, and comb-vertical sit at the bottom with unusually high draw counts — a
  // separate, LinearStrategy-rooted issue (see its own doc): GroveCostWood now equals
  // TombCostWood exactly and Grove costs nothing else, so LinearStrategy (fixed priority,
  // never reconsiders) tiles Grove forever instead of ever reaching Tomb/LaboNaturel, and
  // often never crosses ANY victory condition within maxTicks against a slow-enough
  // opponent — it just times out. comb/comb-vertical (GrovePriority spending atop a fixed
  // wall) share the same Grove-hoarding shape.
  val catalog: Seq[AiStrategy] = Seq(
    CombVertical,
    Comb,
    LinearStrategy,
    CounterOnly,
    ResourceOnly,
    MazeCounter,
    ResourceMaze,
    Balanced,
    MazePlunder,
    CombPlunder,
    CombVerticalPlunder,
    MazeOnly,
    CombResource,
    CombVerticalResource,
    CombCorruption,
    MazeCorruption,
    CombScience,
    MazeScience,
    CombLaw,
    MazeLaw,
    CombNature,
    MazeNature
  )

  // The AI difficulty ladder GameApp actually drives players/spectators through (see
  // GameApp.aiLevelIndex): 25 levels built by crossing 5 of catalog's strongest/most
  // distinct base strategies with 5 build-speed periods (1/2/3/5/8 seconds per build, via
  // RateLimited — see AiStrategy.buildCooldownMs's doc), then ordering all 25 combinations
  // by measured Elo rating. Build speed turned out to dominate strategy choice at every
  // tier (each base strategy's own @1s beats its @2s beats its @3s, ..., monotonically),
  // so the ladder interleaves strategies and speeds rather than grouping by either alone.
  // Each base strategy is referenced by its own object (not looked up by name), so a
  // rename of a catalog entry's `name` can't silently break the ladder's own wiring.
  //
  // Re-measured via `sim/runMain towerdefense.sim.tournament 2` (Swiss rounds, not a full
  // round-robin — see Simulator.swissStandings) after fixing SpendingPolicy.marginFor to
  // treat a zero-stock resource as affordable when Gold covers it. The *previous*
  // measurement (comb-corruption leading every speed tier) turned out to be an artifact of
  // a real lockout, not real strategic strength: every maze now starts at 0 of every named
  // resource, and the old Gold-blind marginFor penalized any building needing one of them
  // as catastrophically unaffordable regardless of Gold on hand — Cave was the sole
  // exception (its Wood cost happens to be exactly 0.0), so every strategy on the ladder
  // got stuck building only Cave forever, and comb-corruption/maze-corruption's flat
  // Mort-kind scoring bonus was the only thing left differentiating anyone. With
  // diversifying into Grove/Tomb/labs actually possible again, resource-aware strategies
  // (resource-maze, then linear, which never touched Science/labs either way) now
  // dominate the top of the ladder at every speed, and comb-corruption fell to the bottom
  // tier at every speed except @8s. See AiStrategyTest's ladder-order test for the exact
  // ranking this produced.
  //
  // maze-science joins the other 5 base strategies at all 5 speeds (30 entries total, up
  // from 25) after ScienceSpending was added specifically to make Science's victory
  // condition actually reachable — see science.MazeScience's own doc. Re-measured via
  // `sim/runMain towerdefense.sim.tournament` after ScienceSpending also learned to secure
  // a Watchtower or two before over-investing in labs: maze-science@1s now leads the
  // entire ladder, 5-0 in the Swiss phase and into the top-8 playoff bracket.
  private def rateLimited(strategy: AiStrategy, periodSec: Int): AiStrategy =
    RateLimited(strategy, buildCooldownMs = periodSec * 1_000.0)

  val ladder: Seq[AiStrategy] = Seq(
    rateLimited(MazeCorruption, 8),
    rateLimited(CombCorruption, 3),
    rateLimited(CombCorruption, 5),
    rateLimited(LinearStrategy, 8),
    rateLimited(Balanced, 8),
    rateLimited(CombCorruption, 2),
    rateLimited(Balanced, 5),
    rateLimited(MazeCorruption, 2),
    rateLimited(CombCorruption, 8),
    rateLimited(ResourceMaze, 8),
    rateLimited(MazeScience, 2),
    rateLimited(MazeCorruption, 1),
    rateLimited(CombCorruption, 1),
    rateLimited(LinearStrategy, 3),
    rateLimited(Balanced, 3),
    rateLimited(ResourceMaze, 3),
    rateLimited(MazeCorruption, 5),
    rateLimited(ResourceMaze, 5),
    rateLimited(LinearStrategy, 5),
    rateLimited(MazeScience, 3),
    rateLimited(MazeScience, 8),
    rateLimited(MazeScience, 5),
    rateLimited(Balanced, 2),
    rateLimited(MazeCorruption, 3),
    rateLimited(ResourceMaze, 2),
    rateLimited(LinearStrategy, 2),
    rateLimited(LinearStrategy, 1),
    rateLimited(ResourceMaze, 1),
    rateLimited(Balanced, 1),
    rateLimited(MazeScience, 1)
  )

  // Both catalog (named base combinations, for CLI experiments) and ladder (the 25
  // Elo-ranked difficulty levels built on top of 5 of them) resolve through the same name
  // -> strategy map, so `sim/run <name>`, `sim/tournament`, and rateTournament's baseName
  // argument can all still address catalog entries by their plain name (e.g. "linear")
  // alongside the ladder's own "linear@1s".."linear@8s" names.
  val all: Map[String, AiStrategy] = (catalog ++ ladder).map(s => s.name -> s).toMap
