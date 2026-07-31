package towerdefense.domain.ai

import towerdefense.domain.*
import towerdefense.domain.combat.*
import towerdefense.domain.economy.*
import towerdefense.domain.grid.*

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
// cooldown constructor parameter — see AiStrategy.buildCooldownMs's doc.
case class RateLimited(inner: AiStrategy, override val buildCooldownMs: Double) extends AiStrategy:
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

  // Every entry is now a ComposedStrategy: a LayoutPolicy ("where" — NoLayoutPreference,
  // FreeformLayout's danger-maximizing scan, or a fixed MazeTemplate wall) combined with a
  // SpendingPolicy ("what" — WeightedSpending's resource/counter blend, PlunderSpending's
  // Chaos rush, or GrovePriority's Grove-first/margin-fallback), replacing the old
  // CompositeStrategy(weights)/TemplateStrategy(template) split where only "maze" was ever
  // pluggable independently of "resource"/"counter". See docs/adr/0010 for the full
  // before/after.
  //
  // ComposedStrategy now breaks ties between equally-scored candidates at random (see its
  // doc) instead of always taking the first in generation order, so — unlike the old,
  // fully deterministic ladder — repeated matches between the same two entries can now
  // produce different outcomes. Ordered weakest to strongest by win rate averaged over a
  // round-robin (`sim/runMain towerdefense.sim.tournament <n>`), not a single deterministic
  // game per pairing.
  //
  // maze-only is the one entry with 0 wins that still ranks fairly high: it has very few
  // losses too — a genuine, reproducible defensive lockout, not matchmaking luck. Its
  // SpendingPolicy weight is (resource=0, counter=0), so it never once considers whether a
  // spend is sustainable — it builds Watchtowers as long as FreeformLayout's ranged-damage
  // credit outscores everything else, drains Wood to exactly 0 with no Grove ever built to
  // replenish it, and then freezes permanently with a small, apparently-effective
  // Watchtower cluster that denies every opponent a win within maxTicks without ever
  // mounting a real offense of its own. `growthBonus` (see SpendingPolicy) exists to keep
  // resource-aware strategies (resource-maze, balanced, comb-resource, ...) from falling
  // into the same freeze: Watchtower (Wood+Light) would otherwise keep outscoring Grove
  // (Wood only, Wood's only producer) because Watchtower's margin gets pulled up by
  // averaging in Light, which Grove has nothing to average against — growthBonus credits
  // *fixing* the shortage directly, flipping Grove ahead of Watchtower once Wood
  // production actually hits zero. maze-only keeps the freeze on purpose: its own weight
  // zeroes growthBonus out along with everything else resource-aware, an accurate
  // reflection of "pure maze scoring, zero resource-awareness" as an archetype.
  //
  // Re-measured (`sim/runMain towerdefense.sim.tournament 2`, all 16 entries) after a
  // direct manual rebalance of Balance.scala (commit "balance"): Grove/Forest/Jungle/
  // Cave/Labyrinth/Eglise all got cheaper, Watchtower's Light cost roughly quadrupled, and
  // StartingShadow/StartingCrystal both doubled (10 -> 20). This reshuffled the ladder far
  // more violently than the Death/Science addition did — the previous champion
  // (resource-maze, 0.80 winRate) fell all the way to a 0.43 winRate mid-table tie.
  //
  // The throughline: buildings across every faction got cheaper at once, so ANY strategy
  // now ramps its economy fast enough to reach Chaos's plunder target (a flat 50,
  // Balance.ChaosVictoryPlunderTarget, untouched by this rebalance) well before slower
  // victory conditions (Forest count, corruption, research) come into range — plunder
  // races, not sustained economic advantage, now decide most matches. Spot-checked via
  // `sim/run maze-corruption comb 1 --log` (win in 524 ticks, 0 CORRUPT lines — the win
  // was a stray Cave's Goblins hitting Chaos's target) and `sim/run resource-maze
  // maze-corruption 1 --log` (b wins the same way, at 1538 ticks, with a's own stray Cave
  // reaching only 45 of the 55 plunder needed first) — even resource-maze's own losses are
  // now decided by this same race, not by being out-teched.
  //
  // maze-corruption (0.97) and comb-corruption (0.93) top the table, but — as before this
  // rebalance — NOT via Death's own mechanic: still 0 CORRUPT events across every spot-
  // checked transcript. CorruptionSpending's 0.25*resourceScore fallback term happens to
  // grab a cheap Cave early (everything's cheap now) and that Cave's Goblins win the race
  // to Chaos's plunder target before the opponent mounts any defense. Treat these two
  // entries as "CorruptionSpending's fallback economy, sped up by cheaper buildings", not
  // "corruption is viable" — Mort's own mechanic (Corruption.md's 1%/2% per second
  // adjacency rate) still needs a design revisit before a strategy can race it on purpose.
  //
  // linear, comb, and comb-vertical now sit at the bottom with unusually high draw counts
  // (6, 5, and 4 draws out of 30 matches respectively) — a separate, LinearStrategy-rooted
  // issue (see its own doc): GroveCostWood now equals TombCostWood exactly and Grove costs
  // nothing else, so LinearStrategy (fixed priority, never reconsiders) tiles Grove forever
  // instead of ever reaching Tomb/LaboNaturel, and often never crosses ANY victory
  // condition within maxTicks against a slow-enough opponent — it just times out. comb/
  // comb-vertical (GrovePriority spending atop a fixed wall) share the same Grove-hoarding
  // shape, which is why their placement here dropped as hard as linear's did.
  //
  // No longer exposed as the AI difficulty ladder (see `ladder` below, which now serves
  // that role with a finer-grained, Elo-measured progression) — kept as `catalog` purely
  // as a pool of distinct LayoutPolicy x SpendingPolicy combinations that CLI tools
  // (sim/run, sim/tournament, sim/rateTournament's baseName arg, ...) can still resolve by
  // name via `all`.
  // Forced openings (see ForcedOpeningLayout's own doc) for each single-faction rush
  // strategy below — explicit, auditable control over how each strategy's starting Gold
  // gets spent, instead of leaving it to emerge from the ordinary candidate-scoring
  // competition. Found by reading real `sim/run <a> <b> --log` transcripts: chaosOpening
  // avoids the exact bug where PlunderSpending once spent its entire starting Gold on a
  // zero-return DragonsLair turn 1; scienceOpening avoids ScienceSpending incidentally
  // building a 13-Forest wall chasing Wood income instead of its own intended producers.
  private val chaosOpening = Seq(BuildingKind.Cave, BuildingKind.WarCamp)
  private val mortOpening = Seq(BuildingKind.Tomb)
  // Shortened from an initial [Cave, Church, Tomb] (rock-paper-scissors tuning pass): a
  // 3-item forced opening made Science's own economy dramatically slower to get going
  // than every other faction's 1-2 item opening, losing almost every match on pure
  // opening speed before its labs/defense ever mattered — confirmed via `rockPaperScissors
  // 9` (Science lost 0-9 to both Chaos and Nature). One forced item (Cave, cheapest
  // producer) is now enough to kick off Fire income; ScienceSpending's own
  // missingProducerBonus tier already prioritizes Church/Tomb dynamically right after,
  // just without forcing a specific (possibly not cost-optimal) order for them.
  private val scienceOpening = Seq(BuildingKind.Cave)
  private val loiOpening = Seq(BuildingKind.Barracks, BuildingKind.Watchtower)
  private val natureOpening = Seq(BuildingKind.Grove)
  // See HealClusterLayout's own doc: rewards placing one of these kinds next to more of
  // this maze's own existing buildings, since a healer covers *any* building (not just
  // other healers) within Chebyshev distance 1 of it and multiple nearby healers stack —
  // added per the project owner's explicit direction (alongside Balance's own
  // corruption/heal-speed tuning) to give Nature a real defense against Death's
  // corruption-sabotage of its own forestCount win condition, confirmed by transcript to
  // otherwise go undefended (see maze-corruption's doc for the sabotage mechanism).
  //
  // Watchtower is included even though it isn't itself a healer (CombatEngine.
  // healBuildingCorruption's own healer map has no Watchtower entry): a transcript
  // (`sim/run maze-nature maze-corruption --log`) showed all 12 of Death's corruption
  // wins landing on Watchtowers specifically, every one built off on its own via plain
  // FreeformLayout danger-scoring, nowhere near the Grove/Forest cluster — Nature kept
  // rebuilding them (NatureSpending's own watchtowerDefenseCap priority) as an
  // undefended, disposable corruption target instead of the intended defense. Clustering
  // Watchtower alongside the healers it doesn't itself provide, but can still receive, was
  // the actual fix; raising heal rates alone couldn't touch a mechanism this doesn't heal
  // participate in at all.
  //
  // Raised from an initial 3.0 (comparable to one AuraDamagePerSec hit): confirmed via
  // transcript that 3.0 was too weak to move Watchtower off a single stand-out chokepoint
  // cell that dominates FreeformLayout's own path-danger score by a wide margin — Death
  // just camped a corrupting unit there and picked off every Watchtower Nature rebuilt on
  // that exact cell, one after another (6 in a row, in one measured match). Strong enough
  // now to reliably outweigh a chokepoint's raw path-danger lead, so a healer-adjacent (but
  // slightly less path-optimal) cell wins instead.
  private val natureClusterKinds =
    Set(BuildingKind.Grove, BuildingKind.Forest, BuildingKind.Jungle, BuildingKind.Watchtower)
  private val natureHealClusterBonus = 15.0

  // Every kind a Science-lab building can ever be (LaboFondamental itself, before its
  // first upgrade, plus the 5 kinds it upgrades into — same set LawSpending/
  // ScienceSpending build privately for their own labCount checks) — used here to give
  // CountCapLayout the full "counts as" set for LawSpending's own one-lab cap: see
  // CountCapLayout's own doc for why a layout-level veto (not just a SpendingPolicy
  // penalty) was needed to make this cap actually hold under a genuine resource drought.
  private val labKinds: Set[BuildingKind] = ResearchSpecs.all.keySet + BuildingKind.LaboFondamental

  val catalog: Seq[(String, AiStrategy)] = Seq(
    "comb-vertical" -> ComposedStrategy(TemplateLayout(MazeTemplate.combVertical), GrovePriority),
    "comb" -> ComposedStrategy(TemplateLayout(MazeTemplate.comb), GrovePriority),
    "linear" -> LinearStrategy,
    "counter-only" -> ComposedStrategy(
      NoLayoutPreference,
      WeightedSpending(resourceWeight = 0.0, counterWeight = 1.0)
    ),
    "resource-only" -> ComposedStrategy(
      NoLayoutPreference,
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0)
    ),
    "maze-counter" -> ComposedStrategy(
      FreeformLayout,
      WeightedSpending(resourceWeight = 0.0, counterWeight = 1.0)
    ),
    "resource-maze" -> ComposedStrategy(
      FreeformLayout,
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0),
      layoutWeight = 0.25,
      spendingWeight = 0.5
    ),
    "balanced" -> ComposedStrategy(
      FreeformLayout,
      WeightedSpending(resourceWeight = 1.0, counterWeight = 1.0)
    ),
    "maze-plunder" -> ComposedStrategy(
      ForcedOpeningLayout(chaosOpening, FreeformLayout),
      PlunderSpending
    ),
    "comb-plunder" -> ComposedStrategy(
      ForcedOpeningLayout(chaosOpening, TemplateLayout(MazeTemplate.comb)),
      PlunderSpending
    ),
    "comb-vertical-plunder" -> ComposedStrategy(
      ForcedOpeningLayout(chaosOpening, TemplateLayout(MazeTemplate.combVertical)),
      PlunderSpending
    ),
    "maze-only" -> ComposedStrategy(
      FreeformLayout,
      WeightedSpending(resourceWeight = 0.0, counterWeight = 0.0)
    ),
    "comb-resource" -> ComposedStrategy(
      TemplateLayout(MazeTemplate.comb),
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0)
    ),
    "comb-vertical-resource" -> ComposedStrategy(
      TemplateLayout(MazeTemplate.combVertical),
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0)
    ),
    // Mort's Tomb/BlackCastle-racing counterparts to comb-plunder/maze-plunder above — see
    // this doc's maze-corruption paragraph for why their win rate doesn't mean what the
    // name implies.
    "comb-corruption" -> ComposedStrategy(
      ForcedOpeningLayout(mortOpening, TemplateLayout(MazeTemplate.comb)),
      CorruptionSpending
    ),
    "maze-corruption" -> ComposedStrategy(
      ForcedOpeningLayout(mortOpening, FreeformLayout),
      CorruptionSpending
    ),
    // Science's own racing counterpart to maze-plunder/maze-corruption above — added after
    // a full-ladder tournament (`sim/runMain towerdefense.sim.tournament`) turned up 0
    // Science victories out of 118 decisive matches (Chaos plunder alone took 60%): every
    // existing strategy either ignores the 5-lab-building requirement entirely or only ever
    // researches opportunistically, never on purpose (see ScienceSpending's doc for why no
    // prior policy could win this way).
    "comb-science" -> ComposedStrategy(
      ForcedOpeningLayout(scienceOpening, TemplateLayout(MazeTemplate.comb)),
      ScienceSpending
    ),
    "maze-science" -> ComposedStrategy(
      ForcedOpeningLayout(scienceOpening, FreeformLayout),
      ScienceSpending
    ),
    // Loi's and Nature's own racing counterparts, completing one dedicated rush strategy
    // per faction (Chaos/Mort/Science already had one) — added to verify the claimed
    // 5-faction rock-paper-scissors cycle (Chaos > Science > Nature > Mort > Loi > Chaos)
    // via `sim/runMain towerdefense.sim.rockPaperScissors`. NatureSpending is genuinely
    // new (GrovePriority only ever chases Grove itself, a different shape — see its own
    // doc); LawSpending's racing kinds already include Watchtower, giving it built-in
    // defense the same mechanism ScienceSpending's own Watchtower bonus provides.
    "comb-law" -> ComposedStrategy(
      ForcedOpeningLayout(
        loiOpening,
        CountCapLayout(BuildingKind.LaboFondamental, labKinds, 1, TemplateLayout(MazeTemplate.comb))
      ),
      LawSpending
    ),
    "maze-law" -> ComposedStrategy(
      ForcedOpeningLayout(
        loiOpening,
        CountCapLayout(BuildingKind.LaboFondamental, labKinds, 1, FreeformLayout)
      ),
      LawSpending
    ),
    "comb-nature" -> ComposedStrategy(
      ForcedOpeningLayout(
        natureOpening,
        HealClusterLayout(
          natureClusterKinds,
          natureHealClusterBonus,
          TemplateLayout(MazeTemplate.comb)
        )
      ),
      NatureSpending
    ),
    "maze-nature" -> ComposedStrategy(
      ForcedOpeningLayout(
        natureOpening,
        HealClusterLayout(natureClusterKinds, natureHealClusterBonus, FreeformLayout)
      ),
      NatureSpending
    )
  )

  // The AI difficulty ladder GameApp actually drives players/spectators through (see
  // GameApp.aiLevelIndex): 25 levels built by crossing 5 of catalog's strongest/most
  // distinct base strategies with 5 build-speed periods (1/2/3/5/8 seconds per build, via
  // RateLimited — see AiStrategy.buildCooldownMs's doc), then ordering all 25 combinations
  // by measured Elo rating. Build speed turned out to dominate strategy choice at every
  // tier (each base strategy's own @1s beats its @2s beats its @3s, ..., monotonically),
  // so the ladder interleaves strategies and speeds rather than grouping by either alone.
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
  private val catalogByName: Map[String, AiStrategy] = catalog.toMap

  private def rateLimited(baseName: String, periodSec: Int): (String, AiStrategy) =
    s"$baseName@${periodSec}s" -> RateLimited(
      catalogByName(baseName),
      buildCooldownMs = periodSec * 1_000.0
    )

  // maze-science joins the other 5 base strategies at all 5 speeds (30 entries total, up
  // from 25) after ScienceSpending was added specifically to make Science's victory
  // condition actually reachable — see catalog's own doc. Re-measured via
  // `sim/runMain towerdefense.sim.tournament` after ScienceSpending also learned to secure
  // a Watchtower or two before over-investing in labs (see ScienceSpending's own doc for the
  // plunder-race losses that fixed): maze-science@1s now leads the entire ladder, 5-0 in
  // the Swiss phase and into the top-8 playoff bracket — see AiStrategyTest's ladder-order
  // test for the full ranking.
  val ladder: Seq[(String, AiStrategy)] = Seq(
    rateLimited("maze-corruption", 8),
    rateLimited("comb-corruption", 3),
    rateLimited("comb-corruption", 5),
    rateLimited("linear", 8),
    rateLimited("balanced", 8),
    rateLimited("comb-corruption", 2),
    rateLimited("balanced", 5),
    rateLimited("maze-corruption", 2),
    rateLimited("comb-corruption", 8),
    rateLimited("resource-maze", 8),
    rateLimited("maze-science", 2),
    rateLimited("maze-corruption", 1),
    rateLimited("comb-corruption", 1),
    rateLimited("linear", 3),
    rateLimited("balanced", 3),
    rateLimited("resource-maze", 3),
    rateLimited("maze-corruption", 5),
    rateLimited("resource-maze", 5),
    rateLimited("linear", 5),
    rateLimited("maze-science", 3),
    rateLimited("maze-science", 8),
    rateLimited("maze-science", 5),
    rateLimited("balanced", 2),
    rateLimited("maze-corruption", 3),
    rateLimited("resource-maze", 2),
    rateLimited("linear", 2),
    rateLimited("linear", 1),
    rateLimited("resource-maze", 1),
    rateLimited("balanced", 1),
    rateLimited("maze-science", 1)
  )

  // Both catalog (named base combinations, for CLI experiments) and ladder (the 25
  // Elo-ranked difficulty levels built on top of 5 of them) resolve through the same name
  // -> strategy map, so `sim/run <name>`, `sim/tournament`, and rateTournament's baseName
  // argument can all still address catalog entries by their plain name (e.g. "linear")
  // alongside the ladder's own "linear@1s".."linear@8s" names.
  val all: Map[String, AiStrategy] = (catalog ++ ladder).toMap
