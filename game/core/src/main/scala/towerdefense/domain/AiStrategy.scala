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
  // 3-matches-per-pairing round-robin (`sim/runMain towerdefense.sim.tournament 3`), not a
  // single deterministic game per pairing.
  //
  // maze-only is the one entry with 0 wins that still ranks above linear: it has 0 losses
  // too (39 draws out of 39 matches) — a genuine, reproducible defensive lockout, not
  // matchmaking luck. Its SpendingPolicy weight is (resource=0, counter=0), so it never
  // once considers whether a spend is sustainable — it builds Watchtowers as long as
  // FreeformLayout's ranged-damage credit outscores everything else, drains Wood to
  // exactly 0 with no Grove ever built to replenish it, and then freezes permanently with
  // a small, apparently-effective Watchtower cluster that denies every opponent a win
  // within 3000 ticks without ever mounting a real offense of its own.
  //
  // That same freeze was originally hitting maze-only AND resource-maze, which is what
  // motivated growthBonus (see SpendingPolicy): a spend-margin penalty alone discourages
  // draining a resource with no producer, but doesn't credit *fixing* the shortage —
  // Watchtower (Wood+Light) kept outscoring Grove (Wood only, Wood's only producer)
  // because Watchtower's margin got pulled up by averaging in Light, which Grove has
  // nothing to average against. `sim/run resource-maze linear 1 --log` showed the exact
  // same 5-Watchtowers-then-freeze pattern before the fix; resource-maze (whose weight
  // does include resource=1.0) now wins normally, since growthBonus flips Grove ahead of
  // Watchtower once Wood's production has actually hit zero. maze-only keeps the freeze,
  // since its own weight zeroes growthBonus out along with everything else resource-aware
  // — an accurate reflection of "pure maze scoring, zero resource-awareness" as an
  // archetype, not a leftover bug.
  //
  // The `-plunder` family (maze-plunder, comb-plunder, comb-vertical-plunder) and
  // resource-maze form a tight top tier, all 27-12-0 — no losses at all across the whole
  // ladder — differing only by Elo (resource-maze 1736 > maze-plunder 1721 >
  // comb-vertical-plunder 1712 > comb-plunder 1703), used as the tiebreak among otherwise
  // identical win/draw/loss records. Racing Chaos buildings (PlunderSpending) or a
  // diversified, growth-aware resource economy (WeightedSpending with growthBonus) both
  // beat every fixed-Grove-first wall (comb/comb-vertical) and every non-plunder blend
  // (balanced, the comb-resource pair) outright.
  val ladder: Seq[(String, AiStrategy)] = Seq(
    "linear" -> LinearStrategy,
    "maze-only" -> ComposedStrategy(FreeformLayout, WeightedSpending(resourceWeight = 0.0, counterWeight = 0.0)),
    "comb" -> ComposedStrategy(TemplateLayout(MazeTemplate.comb), GrovePriority),
    "comb-vertical" -> ComposedStrategy(TemplateLayout(MazeTemplate.combVertical), GrovePriority),
    "counter-only" -> ComposedStrategy(NoLayoutPreference, WeightedSpending(resourceWeight = 0.0, counterWeight = 1.0)),
    "resource-only" -> ComposedStrategy(NoLayoutPreference, WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0)),
    "maze-counter" -> ComposedStrategy(FreeformLayout, WeightedSpending(resourceWeight = 0.0, counterWeight = 1.0)),
    "comb-vertical-resource" -> ComposedStrategy(
      TemplateLayout(MazeTemplate.combVertical),
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0)
    ),
    "comb-resource" -> ComposedStrategy(
      TemplateLayout(MazeTemplate.comb),
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0)
    ),
    "balanced" -> ComposedStrategy(FreeformLayout, WeightedSpending(resourceWeight = 1.0, counterWeight = 1.0)),
    "comb-plunder" -> ComposedStrategy(TemplateLayout(MazeTemplate.comb), PlunderSpending),
    "comb-vertical-plunder" -> ComposedStrategy(TemplateLayout(MazeTemplate.combVertical), PlunderSpending),
    "maze-plunder" -> ComposedStrategy(FreeformLayout, PlunderSpending),
    "resource-maze" -> ComposedStrategy(
      FreeformLayout,
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0),
      layoutWeight = 0.25,
      spendingWeight = 0.5
    ),
    // Mort's Tomb/BlackCastle-racing counterparts to comb-plunder/maze-plunder above,
    // added alongside Death's corruption mechanic — NOT yet measured via a tournament
    // round-robin (unlike every entry above, whose position reflects actual win rates —
    // see this doc's history), so their position here is a provisional placeholder pending
    // that measurement, not a ranking claim.
    "comb-corruption" -> ComposedStrategy(TemplateLayout(MazeTemplate.comb), CorruptionSpending),
    "maze-corruption" -> ComposedStrategy(FreeformLayout, CorruptionSpending)
  )

  val all: Map[String, AiStrategy] = ladder.toMap
