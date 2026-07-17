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

  // Default no-op, same shape as maybeUpgrade — a strategy that never builds a Science lab
  // has nothing to research anyway. Driven the same way, sharing the build cooldown
  // (research compounds a maze's economy/defense the same way building/upgrading does).
  def maybeResearch(state: MazeState, opponent: MazeState): MazeState = state

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
  // Re-measured (`sim/runMain towerdefense.sim.tournament 3`, all 16 entries incl. the two
  // Mort-racing ones below) after three changes landed together: Death (Tomb/BlackCastle,
  // corruption) and Science (5 labs + full leveled research, every strategy now researches
  // opportunistically via maybeResearch) were added, and BuildingKind's count roughly
  // doubled (7 → 14), which reshuffles every LayoutPolicy/SpendingPolicy score's relative
  // weighting even for strategies that never touch a Mort/Science building directly — so
  // this order isn't just "the old order plus two new rows", several older entries moved.
  //
  // resource-maze stays champion (0.80 winRate, elo 1797, 0 losses). The zero-loss tier is
  // now resource-maze, maze-only, maze-plunder, AND maze-corruption (25-20-0, elo 1705) —
  // FreeformLayout-based strategies broadly hold up better than any fixed TemplateLayout
  // wall once there are 14 kinds of candidate to weigh instead of 7.
  //
  // maze-corruption's 0.56 winRate is real and reproducible, but NOT mostly earned through
  // Death's own mechanic: `sim/run maze-corruption linear 1 --log` and `sim/run
  // maze-corruption comb 1 --log` both show it winning via Chaos plunder (a stray Cave,
  // built through CorruptionSpending's 0.25*resourceScore fallback term, sends Goblins
  // that a Cave-focused opponent never bothers defending against) — corruption itself never
  // landed a single building-destroying hit in either transcript. This matches Corruption.md's
  // own math: a Zombie needs ~100 continuous seconds of adjacency to one building (1%/sec)
  // to destroy it, but a creature only grazes past buildings for a few seconds while pathing
  // to the goal, even against a densely-walled `comb` defender — the rate is the vault's own
  // explicit number, not a POC default, so it's left untouched rather than silently buffed.
  // comb-corruption (TemplateLayout(comb) instead of FreeformLayout) does much worse
  // (0.38 winRate) — the fixed wall doesn't prioritize the same fallback economy maze-
  // corruption stumbles into. Treat "maze-corruption" as measuring "CorruptionSpending's
  // resourceScore fallback with a Chaos-adjacent economy", not "corruption is viable" —
  // Mort's own mechanic needs a design revisit (denser required adjacency, or units that
  // linger rather than path straight through) before a strategy can race it on purpose.
  //
  // maze-only is the one entry with 0 wins that still ranks above linear: it has 0 losses
  // too — a genuine, reproducible defensive lockout, not matchmaking luck. Its SpendingPolicy
  // weight is (resource=0, counter=0), so it never once considers whether a spend is
  // sustainable — it builds Watchtowers as long as FreeformLayout's ranged-damage credit
  // outscores everything else, drains Wood to exactly 0 with no Grove ever built to
  // replenish it, and then freezes permanently with a small, apparently-effective Watchtower
  // cluster that denies every opponent a win without ever mounting a real offense of its own.
  //
  // That same freeze was originally hitting maze-only AND resource-maze, which is what
  // motivated growthBonus (see SpendingPolicy): a spend-margin penalty alone discourages
  // draining a resource with no producer, but doesn't credit *fixing* the shortage —
  // Watchtower (Wood+Light) kept outscoring Grove (Wood only, Wood's only producer)
  // because Watchtower's margin got pulled up by averaging in Light, which Grove has
  // nothing to average against. resource-maze (whose weight does include resource=1.0) now
  // wins normally, since growthBonus flips Grove ahead of Watchtower once Wood's production
  // has actually hit zero. maze-only keeps the freeze, since its own weight zeroes
  // growthBonus out along with everything else resource-aware — an accurate reflection of
  // "pure maze scoring, zero resource-awareness" as an archetype, not a leftover bug.
  val ladder: Seq[(String, AiStrategy)] = Seq(
    "linear" -> LinearStrategy,
    "counter-only" -> ComposedStrategy(NoLayoutPreference, WeightedSpending(resourceWeight = 0.0, counterWeight = 1.0)),
    "comb-vertical" -> ComposedStrategy(TemplateLayout(MazeTemplate.combVertical), GrovePriority),
    "comb" -> ComposedStrategy(TemplateLayout(MazeTemplate.comb), GrovePriority),
    "maze-counter" -> ComposedStrategy(FreeformLayout, WeightedSpending(resourceWeight = 0.0, counterWeight = 1.0)),
    "comb-resource" -> ComposedStrategy(
      TemplateLayout(MazeTemplate.comb),
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0)
    ),
    // Mort's Tomb/BlackCastle-racing counterparts to comb-plunder/maze-plunder below — see
    // this doc's maze-corruption paragraph for why their win rate doesn't mean what the name
    // implies.
    "comb-corruption" -> ComposedStrategy(TemplateLayout(MazeTemplate.comb), CorruptionSpending),
    "comb-vertical-resource" -> ComposedStrategy(
      TemplateLayout(MazeTemplate.combVertical),
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0)
    ),
    "resource-only" -> ComposedStrategy(NoLayoutPreference, WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0)),
    "maze-corruption" -> ComposedStrategy(FreeformLayout, CorruptionSpending),
    "balanced" -> ComposedStrategy(FreeformLayout, WeightedSpending(resourceWeight = 1.0, counterWeight = 1.0)),
    "maze-plunder" -> ComposedStrategy(FreeformLayout, PlunderSpending),
    "maze-only" -> ComposedStrategy(FreeformLayout, WeightedSpending(resourceWeight = 0.0, counterWeight = 0.0)),
    "comb-vertical-plunder" -> ComposedStrategy(TemplateLayout(MazeTemplate.combVertical), PlunderSpending),
    "comb-plunder" -> ComposedStrategy(TemplateLayout(MazeTemplate.comb), PlunderSpending),
    "resource-maze" -> ComposedStrategy(
      FreeformLayout,
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0),
      layoutWeight = 0.25,
      spendingWeight = 0.5
    )
  )

  val all: Map[String, AiStrategy] = ladder.toMap
