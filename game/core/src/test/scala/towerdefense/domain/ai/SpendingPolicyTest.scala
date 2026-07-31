package towerdefense.domain.ai

import towerdefense.domain.*
import towerdefense.domain.ai.chaos.PlunderSpending
import towerdefense.domain.ai.loi.LawSpending
import towerdefense.domain.ai.mort.CorruptionSpending
import towerdefense.domain.ai.nature.NatureSpending
import towerdefense.domain.ai.science.ScienceSpending
import towerdefense.domain.combat.*
import towerdefense.domain.economy.*
import towerdefense.domain.grid.*

// SpendingPolicy is the "which building kind" half of an AiStrategy — the counterpart to
// LayoutPolicy's "which cell". These tests exercise SpendingPolicy.resourceScore/
// counterScore/rawMargin directly (moved verbatim from CompositeStrategy, now with a
// growth-awareness term — see below), plus the concrete policies built on top of them.
class SpendingPolicyTest extends munit.FunSuite:

  private val noOpponent = MazeState.initial

  private def withResources(
      wood: Double = 0.0,
      fire: Double = 0.0,
      light: Double = 0.0
  ): MazeState =
    MazeState.initial.copy(
      resources = Map(Resource.Wood -> wood, Resource.Fire -> fire, Resource.Light -> light)
    )

  private def building(id: Long, col: Int, row: Int, kind: BuildingKind): Building =
    Building(id, col, row, kind, spawnCountdownMs = 0.0)

  // ── resourceScore / rawMargin ──────────────────────────────────────────

  test("resourceScore prefers the building that leaves the largest affordability margin") {
    // Cave costs no Wood at all (Balance.CaveCostWood = 0), so with Wood scarce and Fire
    // abundant, Grove's Wood-only cost eats most of the scarce pool while Cave's Fire-only
    // cost barely dents the abundant one — a much larger margin for Cave.
    val state = withResources(wood = 10.0, fire = 1_000.0, light = 0.0)
    assert(
      SpendingPolicy.resourceScore(state, BuildingKind.Cave) > SpendingPolicy
        .resourceScore(state, BuildingKind.Grove),
      "Cave spends a smaller fraction of the pool than Grove at this resource level"
    )
  }

  test("resourceScore divides the raw margin by one plus how many of that kind are already built") {
    // Labyrinth produces nothing (BuildingKind.Labyrinth.produces is empty), so
    // building more of them never changes Wood/Fire's production rate — the only thing
    // that should differ between these two states is the diminishing-returns divisor.
    val bare = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    val threeLabyrinths = bare.copy(buildings =
      List(
        building(1, 1, 1, BuildingKind.Labyrinth),
        building(2, 1, 2, BuildingKind.Labyrinth),
        building(3, 1, 3, BuildingKind.Labyrinth)
      )
    )
    assertEqualsDouble(
      SpendingPolicy.resourceScore(bare, BuildingKind.Labyrinth),
      SpendingPolicy.resourceScore(threeLabyrinths, BuildingKind.Labyrinth) * 4.0,
      1e-9
    )

  }

  test(
    "resourceScore rewards a kind that would establish production of a currently-unproduced resource"
  ) {
    // Regression for a real lockout found via `sim/run`: two Watchtowers already stand
    // (so Light already has production) but Wood never has, since no Grove was ever
    // built — a pure-margin score alone still favored a third Watchtower (whose Wood term
    // is averaged against Light's now-unpenalized margin) over Grove (whose only term is
    // Wood's, sinking with the growth penalty) — the exact kind that would fix the Wood
    // shortage kept losing to one that only shares its cost. growthBonus is what must flip
    // this, not the margin math alone.
    val woodNeverProduced = withResources(wood = 12.0, fire = 0.0, light = 100.0).copy(
      buildings =
        List(building(1, 1, 1, BuildingKind.Watchtower), building(2, 2, 2, BuildingKind.Watchtower))
    )
    assert(
      SpendingPolicy.resourceScore(woodNeverProduced, BuildingKind.Grove) >
        SpendingPolicy.resourceScore(woodNeverProduced, BuildingKind.Watchtower),
      "Grove would establish Wood production (currently zero); a third Watchtower would not, and Light no longer needs fixing"
    )
  }

  test("growthBonus contributes nothing once a resource already has some production") {
    val alreadyProducingWood = withResources(wood = 12.0, fire = 0.0, light = 100.0).copy(
      buildings = List(building(1, 1, 1, BuildingKind.Grove))
    )
    // With Wood already produced (by the standing Grove), a second Grove candidate's own
    // margin term (still divided by 1+existingCount=2) is what decides, since growthBonus
    // no longer has a shortage to fix.
    assertEqualsDouble(
      SpendingPolicy.rawMargin(alreadyProducingWood, BuildingKind.Grove) / 2.0,
      SpendingPolicy.resourceScore(alreadyProducingWood, BuildingKind.Grove),
      1e-9,
      "growthBonus is 0 since Wood already has production; only the existing-count divisor differs from rawMargin"
    )
  }

  test(
    "rawMargin matches the plain affordability margin once every spent resource has some production"
  ) {
    val state = withResources(wood = 100.0, fire = 100.0).copy(
      buildings = List(building(1, 1, 1, BuildingKind.Grove), building(2, 2, 2, BuildingKind.Cave))
    )
    val plainCaveMargin =
      ((100.0 - Balance.CaveCostWood) / 100.0 + (100.0 - Balance.CaveCostFire) / 100.0) / 2.0
    assertEqualsDouble(SpendingPolicy.rawMargin(state, BuildingKind.Cave), plainCaveMargin, 1e-9)
  }

  test(
    "rawMargin is strictly worse than the plain margin when a spent resource has zero production"
  ) {
    val bare = withResources(wood = 100.0, fire = 100.0)
    val plainCaveMargin =
      ((100.0 - Balance.CaveCostWood) / 100.0 + (100.0 - Balance.CaveCostFire) / 100.0) / 2.0
    assert(SpendingPolicy.rawMargin(bare, BuildingKind.Cave) < plainCaveMargin)
  }

  test(
    "rawMargin never returns NaN when a zero-cost resource's stock has also dropped to exactly zero"
  ) {
    // Regression for a crash found via sim/rateTournament: Cave's cost lists Wood at
    // exactly 0.0 (Balance.CaveCostWood). If the maze's actual Wood stock also happens to
    // be exactly 0.0, the naive (available - amount) / available margin formula divides
    // 0.0 by 0.0 = NaN, which then poisons resourceScore/ComposedStrategy's own max/tie
    // search (NaN == NaN is false, so the "tied" candidate set silently becomes empty and
    // random.nextInt(0) throws). Spending nothing of a resource should never be penalized
    // regardless of how depleted that resource is.
    val depleted = withResources(wood = 0.0, fire = 100.0, light = 0.0)
    val margin = SpendingPolicy.rawMargin(depleted, BuildingKind.Cave)
    assert(!margin.isNaN, s"rawMargin must never be NaN, got $margin")
    val score = SpendingPolicy.resourceScore(depleted, BuildingKind.Cave)
    assert(!score.isNaN, s"resourceScore must never be NaN, got $score")
  }

  // Regression for a real lockout found via a full sim/tournament run's per-match logs
  // (see AiStrategy.ladder's doc): every maze now starts at 0 of every named resource
  // (Balance.StartingResources), so the UnaffordableMarginFloor above fired on turn one
  // for almost everything — Placement.canAfford already treats Gold as a 1-for-1 joker
  // for any shortfall, but marginFor didn't know that, so a candidate Gold could easily
  // pay for still scored the same catastrophic floor as one nothing on the board could
  // ever afford. Cave was the one exception (its Wood cost is exactly 0.0), which is why
  // every strategy on the ladder got stuck building only Cave forever.
  test(
    "marginFor treats a zero-stock resource as affordable, not the unaffordable floor, when Gold covers it"
  ) {
    val goldCoversIt =
      MazeState.initial.copy(resources = Map(Resource.Wood -> 0.0, Resource.Gold -> 100.0))
    val margin = SpendingPolicy.rawMargin(goldCoversIt, BuildingKind.Grove)
    assert(
      margin > 0.0,
      s"Grove's Wood cost (${Balance.GroveCostWood}) is comfortably covered by 100 Gold; rawMargin should not read as unaffordable, got $margin"
    )
  }

  test(
    "marginFor still applies the unaffordable floor when Gold can't cover the shortfall either"
  ) {
    val goldTooLow =
      MazeState.initial.copy(resources = Map(Resource.Wood -> 0.0, Resource.Gold -> 1.0))
    val margin = SpendingPolicy.rawMargin(goldTooLow, BuildingKind.Grove)
    assert(
      margin < -1.0,
      s"Grove's Wood cost (${Balance.GroveCostWood}) exceeds the 1.0 Gold on hand; rawMargin should still read as unaffordable, got $margin"
    )
  }

  test(
    "once Fire has some production, Grove clearly outscores a second Cave — the observed lockout is broken"
  ) {
    // Mirrors the exact shape of the transcript that surfaced this bug: a Cave already
    // stands (so Fire now has production), Gold is plentiful, but Wood has never been
    // produced. Before marginFor became Gold-aware, Grove's Wood term scored the
    // catastrophic floor regardless of Gold, so a second (third, fourth, ...) Cave kept
    // winning forever. Now Grove's Wood term reads as affordable (Gold-covered), and
    // growthBonus (Wood still unproduced, unlike Fire) tips it ahead of a Cave repeat.
    val afterOneCave = MazeState.initial.copy(
      resources = Map(Resource.Gold -> 80.0, Resource.Fire -> 5.0),
      buildings = List(building(1, 0, 1, BuildingKind.Cave))
    )
    assert(
      SpendingPolicy.resourceScore(afterOneCave, BuildingKind.Grove) >
        SpendingPolicy.resourceScore(afterOneCave, BuildingKind.Cave),
      "Grove (still establishing Wood) should now beat a repeat Cave (Fire already flowing, discounted by existingCount)"
    )
  }

  test(
    "rawMargin is a finite (not infinite) number when a NONZERO-cost resource's stock is exactly zero"
  ) {
    // Every maze now starts with 0 of the 5 named resources (only Gold — see
    // Balance.StartingResources), so this is no longer a rare edge case but the literal
    // starting state. Grove costs Wood > 0; with Wood at 0.0, the naive
    // (available - amount) / available formula divides a negative number by 0.0, giving
    // -Infinity, not NaN — but that's just as poisonous one level up: ComposedStrategy
    // multiplies this by spendingWeight (its own doc), and searchWeights/tournamentStandings
    // both grid-search down to spendingWeight = 0.0, where 0.0 * -Infinity = NaN, silently
    // emptying the "tied" candidate set the same way the sibling test above describes.
    val empty = withResources(wood = 0.0, fire = 0.0, light = 0.0)
    val margin = SpendingPolicy.rawMargin(empty, BuildingKind.Grove)
    assert(margin.isFinite, s"rawMargin must never be infinite, got $margin")
    assertEquals(
      0.0 * margin,
      0.0,
      s"a zero weight must still zero out a finite margin, not produce NaN"
    )
  }

  // ── counterScore ───────────────────────────────────────────────────────

  test("counterScore mirrors the opponent's dominant faction") {
    val chaosHeavyOpponent = MazeState.initial.copy(
      buildings = List(building(1, 5, 5, BuildingKind.Cave), building(2, 6, 6, BuildingKind.Cave))
    )
    assertEquals(SpendingPolicy.counterScore(chaosHeavyOpponent, BuildingKind.Cave), 1.0)
    assertEquals(SpendingPolicy.counterScore(chaosHeavyOpponent, BuildingKind.Grove), 0.0)
  }

  test(
    "counterScore ignores Loi (Church/Watchtower) investment since it feeds no victory condition"
  ) {
    val loiHeavyOpponent = MazeState.initial.copy(
      buildings = List(
        building(1, 5, 5, BuildingKind.Watchtower),
        building(2, 6, 6, BuildingKind.Watchtower),
        building(3, 7, 7, BuildingKind.Watchtower),
        building(4, 2, 2, BuildingKind.Forest)
      )
    )
    assertEquals(SpendingPolicy.counterScore(loiHeavyOpponent, BuildingKind.Watchtower), 0.0)
    assertEquals(SpendingPolicy.counterScore(loiHeavyOpponent, BuildingKind.Grove), 1.0)
  }

  test("counterScore also mirrors Mort (Tomb/BlackCastle) investment, alongside Nature/Chaos") {
    val mortHeavyOpponent = MazeState.initial.copy(
      buildings = List(
        building(1, 5, 5, BuildingKind.Tomb),
        building(2, 6, 6, BuildingKind.Tomb),
        building(3, 7, 7, BuildingKind.Tomb),
        building(4, 2, 2, BuildingKind.Cave)
      )
    )
    assertEquals(SpendingPolicy.counterScore(mortHeavyOpponent, BuildingKind.Tomb), 1.0)
    assertEquals(SpendingPolicy.counterScore(mortHeavyOpponent, BuildingKind.Cave), 0.0)
  }

  test(
    "counterScore ignores Science (the five Labo* kinds) investment since it feeds no victory condition"
  ) {
    val scienceHeavyOpponent = MazeState.initial.copy(
      buildings = List(
        building(1, 5, 5, BuildingKind.LaboNaturel),
        building(2, 6, 6, BuildingKind.LaboSombre),
        building(3, 2, 2, BuildingKind.Cave)
      )
    )
    assertEquals(SpendingPolicy.counterScore(scienceHeavyOpponent, BuildingKind.LaboNaturel), 0.0)
    assertEquals(SpendingPolicy.counterScore(scienceHeavyOpponent, BuildingKind.Cave), 1.0)
  }

  // ── WeightedSpending ───────────────────────────────────────────────────

  test("WeightedSpending(1,0) ranks kinds by resourceScore alone") {
    // Same wood-scarce/fire-abundant setup as the resourceScore test above — Cave's zero
    // Wood cost gives it the clear edge here.
    val state = withResources(wood = 10.0, fire = 1_000.0, light = 0.0)
    val policy = WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0)
    assert(
      policy.score(state, noOpponent, BuildingKind.Cave) > policy.score(
        state,
        noOpponent,
        BuildingKind.Grove
      )
    )
  }

  test("WeightedSpending(0,1) ranks kinds by counterScore alone") {
    val chaosHeavyOpponent = MazeState.initial.copy(
      buildings = List(building(1, 5, 5, BuildingKind.Cave), building(2, 6, 6, BuildingKind.Cave))
    )
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    val policy = WeightedSpending(resourceWeight = 0.0, counterWeight = 1.0)
    assertEquals(policy.score(state, chaosHeavyOpponent, BuildingKind.Cave), 1.0)
    assertEquals(policy.score(state, chaosHeavyOpponent, BuildingKind.Grove), 0.0)
  }

  test("a combined weight vector can pick a kind neither ranking alone would predict") {
    val state = withResources(wood = 100.0, fire = 15.0, light = 0.0)
    val chaosHeavyOpponent = MazeState.initial.copy(
      buildings = List(building(1, 5, 5, BuildingKind.Cave), building(2, 6, 6, BuildingKind.Cave))
    )
    val resourceOnly = WeightedSpending(1.0, 0.0)
    val combined = WeightedSpending(1.0, 1.0)
    assert(
      resourceOnly.score(state, chaosHeavyOpponent, BuildingKind.Grove) > resourceOnly.score(
        state,
        chaosHeavyOpponent,
        BuildingKind.Cave
      )
    )
    assert(
      combined.score(state, chaosHeavyOpponent, BuildingKind.Cave) > combined.score(
        state,
        chaosHeavyOpponent,
        BuildingKind.Grove
      )
    )
  }

  // ── PlunderSpending ────────────────────────────────────────────────────

  test("PlunderSpending always favors Chaos kinds, regardless of the opponent's faction mix") {
    val natureHeavyOpponent = MazeState.initial.copy(
      buildings =
        List(building(1, 5, 5, BuildingKind.Forest), building(2, 6, 6, BuildingKind.Forest))
    )
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    assert(
      PlunderSpending.score(state, natureHeavyOpponent, BuildingKind.Cave) > PlunderSpending.score(
        state,
        natureHeavyOpponent,
        BuildingKind.Grove
      )
    )
    assert(
      PlunderSpending.score(state, natureHeavyOpponent, BuildingKind.Labyrinth) > PlunderSpending
        .score(state, natureHeavyOpponent, BuildingKind.Church)
    )
  }

  // Every Chaos building should be a candidate this policy actually considers — DragonsLair
  // was missing here (rock-paper-scissors tuning pass, auditing each faction's building set
  // against every policy that races for it): its Dragon alone (40 Gold plunder) nearly
  // secures the whole plunder victory (target 50) in one successful raid, a tool maze-plunder
  // never used at all until this fix.
  test("PlunderSpending also favors DragonsLair, not just Cave/Labyrinth/WarCamp") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    assert(
      PlunderSpending.score(state, noOpponent, BuildingKind.DragonsLair) >
        PlunderSpending.score(state, noOpponent, BuildingKind.Grove)
    )
  }

  // A flat, equal bonus for every Chaos kind alone isn't enough to ever get DragonsLair
  // actually built: Cave's near-zero cost (0 Wood) always wins the fallback margin
  // tie-break, so a real match kept spamming Cave forever and never touched DragonsLair —
  // confirmed via `sim/run maze-plunder maze-science --log`. DragonsLair needs its own
  // priority tier (mirroring ScienceSpending's tiered bonuses) so at least one gets built
  // once affordable, the same way ScienceSpending prioritizes LaboFondamental.
  // Gated on already owning a Cave/WarCamp (a real transcript confirmed why: with no such
  // gate, PlunderSpending spent its ENTIRE starting Gold on a turn-1 DragonsLair — a
  // building with zero economic return — starving itself completely, the exact
  // Gold-starvation lockout ScienceSpending's own producer bonuses were built to avoid).
  test("PlunderSpending prioritizes DragonsLair over Cave once it already has some Chaos economy") {
    val withCave = withResources(wood = 100.0, fire = 100.0, light = 0.0).copy(
      buildings = List(building(1, 1, 1, BuildingKind.Cave))
    )
    assert(
      PlunderSpending.score(withCave, noOpponent, BuildingKind.DragonsLair) >
        PlunderSpending.score(withCave, noOpponent, BuildingKind.Cave),
      "DragonsLair's priority tier should outrank Cave's better fallback margin, once some Chaos economy exists"
    )
  }

  test("PlunderSpending does NOT prioritize DragonsLair before any Chaos economy exists") {
    val bare = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    assert(
      PlunderSpending.score(bare, noOpponent, BuildingKind.DragonsLair) <
        PlunderSpending.score(bare, noOpponent, BuildingKind.Cave),
      "with no Cave/WarCamp built yet, Cave's better fallback margin should still win"
    )
  }

  test("PlunderSpending stops prioritizing DragonsLair once one already exists") {
    val oneDragonsLair = withResources(wood = 100.0, fire = 100.0, light = 0.0).copy(
      buildings =
        List(building(1, 1, 1, BuildingKind.Cave), building(2, 1, 2, BuildingKind.DragonsLair))
    )
    assertEqualsDouble(
      PlunderSpending.score(oneDragonsLair, noOpponent, BuildingKind.DragonsLair),
      1.0 + 0.25 * SpendingPolicy.resourceScore(oneDragonsLair, BuildingKind.DragonsLair),
      1e-9,
      "a second DragonsLair should score via the plain chaos-flat-bonus + fallback, no priority tier"
    )
  }

  test("PlunderSpending breaks ties between Cave and Labyrinth by affordability margin") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    assert(
      PlunderSpending.score(state, noOpponent, BuildingKind.Cave) > PlunderSpending
        .score(state, noOpponent, BuildingKind.Labyrinth),
      "Cave (wood5/fire10) leaves a much larger margin than Labyrinth (wood20/fire40)"
    )
  }

  // ── CorruptionSpending ─────────────────────────────────────────────────

  test("CorruptionSpending always favors Mort kinds, regardless of the opponent's faction mix") {
    val natureHeavyOpponent = MazeState.initial.copy(
      buildings =
        List(building(1, 5, 5, BuildingKind.Forest), building(2, 6, 6, BuildingKind.Forest))
    )
    val state = withResources(wood = 100.0, fire = 0.0, light = 0.0).copy(
      resources = Map(Resource.Wood -> 100.0, Resource.Shadow -> 100.0)
    )
    assert(
      CorruptionSpending.score(state, natureHeavyOpponent, BuildingKind.Tomb) >
        CorruptionSpending.score(state, natureHeavyOpponent, BuildingKind.Grove)
    )
    assert(
      CorruptionSpending.score(state, natureHeavyOpponent, BuildingKind.BlackCastle) >
        CorruptionSpending.score(state, natureHeavyOpponent, BuildingKind.Church)
    )
  }

  test("CorruptionSpending breaks ties between Tomb and BlackCastle by affordability margin") {
    val state =
      MazeState.initial.copy(resources = Map(Resource.Wood -> 100.0, Resource.Shadow -> 100.0))
    assert(
      CorruptionSpending.score(state, noOpponent, BuildingKind.Tomb) >
        CorruptionSpending.score(state, noOpponent, BuildingKind.BlackCastle),
      "Tomb (wood5/shadow10) leaves a much larger margin than BlackCastle (wood20/shadow40)"
    )
  }

  // ── LawSpending ────────────────────────────────────────────────────────
  // Races Loi's own "Paix Éternelle" victory condition (VictoryConditions.hasWonViaLoi) —
  // same shape as PlunderSpending/CorruptionSpending: flat bonus for the racing kinds
  // (Church/Watchtower/Angel/Barracks), 0.25*resourceScore fallback otherwise. Watchtower
  // additionally gets a capped defense-priority tier on top (same pattern
  // NatureSpending/ScienceSpending use for themselves) — diagnosed via transcript: without
  // it, margin-based tie-breaking among 4 equally-scored Loi kinds let Law drift toward
  // spamming cheap Barracks for volume, leaving too few Watchtowers up to actually slow
  // Chaos's raiders, so its own plunder race kept resolving before Law's building lead
  // ever got the chance to matter.

  test("LawSpending always favors Loi kinds, regardless of the opponent's faction mix") {
    val natureHeavyOpponent = MazeState.initial.copy(
      buildings =
        List(building(1, 5, 5, BuildingKind.Forest), building(2, 6, 6, BuildingKind.Forest))
    )
    val state = withResources(wood = 100.0, fire = 0.0, light = 100.0)
    assert(
      LawSpending.score(state, natureHeavyOpponent, BuildingKind.Church) >
        LawSpending.score(state, natureHeavyOpponent, BuildingKind.Grove)
    )
    assert(
      LawSpending.score(state, natureHeavyOpponent, BuildingKind.Watchtower) >
        LawSpending.score(state, natureHeavyOpponent, BuildingKind.Cave)
    )
  }

  test("LawSpending breaks ties between Barracks and Angel by affordability margin") {
    val state = withResources(wood = 100.0, fire = 0.0, light = 100.0)
    assert(
      LawSpending.score(state, noOpponent, BuildingKind.Barracks) > LawSpending
        .score(state, noOpponent, BuildingKind.Angel),
      "Barracks (wood5/light10) leaves a much larger margin than Angel (light50)"
    )
  }

  // Diagnosed via a real transcript (rock-paper-scissors tuning pass): LawSpending's
  // fallback happily kept building Grove past its forced opening — pure waste for a
  // strategy whose victory condition is "count of Loi buildings", since Grove never
  // counts toward that tally. Same fix ScienceSpending already uses for its own
  // incidental Forest wall — a capped penalty, not a hard ban, since Law still needs
  // *some* Wood income to keep affording more Loi buildings (none of its own 4 kinds
  // produce Wood).
  test("LawSpending penalizes Grove/Forest/Jungle once 2 Nature buildings already exist") {
    val twoGroves = MazeState.initial.copy(
      resources = Map(Resource.Wood -> 100.0, Resource.Light -> 100.0),
      buildings = List(building(1, 1, 1, BuildingKind.Grove), building(2, 1, 2, BuildingKind.Grove))
    )
    assert(
      LawSpending.score(twoGroves, noOpponent, BuildingKind.Grove) <
        0.25 * SpendingPolicy.resourceScore(twoGroves, BuildingKind.Grove),
      "a third Grove should score BELOW the plain fallback once 2 Nature buildings already exist"
    )
  }

  test("LawSpending does not penalize a Grove/Forest/Jungle candidate below the cap") {
    val noNatureYet =
      MazeState.initial.copy(resources = Map(Resource.Wood -> 100.0, Resource.Light -> 100.0))
    assertEqualsDouble(
      LawSpending.score(noNatureYet, noOpponent, BuildingKind.Grove),
      0.25 * SpendingPolicy.resourceScore(noNatureYet, BuildingKind.Grove),
      1e-9,
      "the first Grove should score via the plain fallback, no penalty yet"
    )
  }

  test(
    "LawSpending favors Watchtower over Barracks regardless of how many Watchtowers already exist"
  ) {
    val noWatchtowers = withResources(wood = 100.0, fire = 0.0, light = 100.0)
    val manyWatchtowers = noWatchtowers.copy(
      buildings = (1 to 20)
        .map(id =>
          building(id, id % GridConfig.cols, 1 + id / GridConfig.cols, BuildingKind.Watchtower)
        )
        .toList
    )
    Seq(noWatchtowers, manyWatchtowers).foreach { state =>
      assert(
        LawSpending.score(state, noOpponent, BuildingKind.Watchtower) > LawSpending
          .score(state, noOpponent, BuildingKind.Barracks),
        s"Watchtower's uncapped defense priority should always outrank Barracks' better fallback margin: $state"
      )
    }
  }

  // Recherches loyales.md: LaboDeLaLoi's research speeds up Watchtower/Angel's own attack
  // rate (a fixed-building-count multiplier, not more towers) — added at the project
  // owner's explicit direction after Watchtower's single-target-per-second throughput was
  // diagnosed as the real bottleneck against a scaling Chaos raid.

  test("LawSpending favors LaboFondamental over Barracks once 2+ Watchtowers exist") {
    // Includes Gold, unlike withResources' bare Wood/Fire/Light: LaboFondamental costs
    // pure Crystal, which every maze starts at 0 stock/0 production — Placement.canAfford
    // (and this policy's own rawMargin, see SpendingPolicy.marginFor) lets Gold cover that
    // shortfall 1-for-1, same as a real match's starting position (Balance.StartingGold),
    // rather than reading as flatly unaffordable.
    val twoWatchtowers = MazeState.initial.copy(
      resources = Map(Resource.Wood -> 100.0, Resource.Light -> 100.0, Resource.Gold -> 100.0),
      buildings =
        List(building(1, 1, 1, BuildingKind.Watchtower), building(2, 1, 2, BuildingKind.Watchtower))
    )
    assert(
      LawSpending.score(twoWatchtowers, noOpponent, BuildingKind.LaboFondamental) >
        LawSpending.score(twoWatchtowers, noOpponent, BuildingKind.Barracks),
      "LaboFondamental's lab-rush bonus should outrank Barracks' better fallback margin once early defense is secured"
    )
  }

  test("LawSpending does not favor LaboFondamental before 2 Watchtowers exist") {
    val noWatchtowers = withResources(wood = 100.0, fire = 0.0, light = 100.0)
    assertEqualsDouble(
      LawSpending.score(noWatchtowers, noOpponent, BuildingKind.LaboFondamental),
      0.25 * SpendingPolicy.resourceScore(noWatchtowers, BuildingKind.LaboFondamental),
      1e-9,
      "no lab-rush bonus yet — Law should secure defense first"
    )
  }

  test(
    "LawSpending stops favoring LaboFondamental once it already owns one (or its upgraded form)"
  ) {
    // The hard cap itself is enforced by AiStrategy's CountCapLayout wrapping (a layout
    // veto, immune to margin dilution — see its own doc for why a SpendingPolicy penalty
    // alone couldn't reliably stop this); this policy's own job is just to stop ADDING a
    // bonus once the one lab-rush slot is spent, same as every other capped tier here.
    val alreadyHasLab = withResources(wood = 100.0, fire = 0.0, light = 100.0).copy(
      buildings = List(
        building(1, 1, 1, BuildingKind.Watchtower),
        building(2, 1, 2, BuildingKind.Watchtower),
        building(3, 1, 3, BuildingKind.LaboDeLaLoi)
      )
    )
    assertEqualsDouble(
      LawSpending.score(alreadyHasLab, noOpponent, BuildingKind.LaboFondamental),
      0.25 * SpendingPolicy.resourceScore(alreadyHasLab, BuildingKind.LaboFondamental),
      1e-9,
      "a second LaboFondamental should score via the plain fallback, the one lab-rush slot is already spent"
    )
  }

  // ── NatureSpending ─────────────────────────────────────────────────────
  // Races Nature's own forest-count victory condition (VictoryConditions.forestCount) —
  // same shape as PlunderSpending/CorruptionSpending/LawSpending, targeting the whole
  // Grove/Forest/Jungle upgrade chain plus Stonehenge. Unlike GrovePriority (which only
  // ever chases Grove itself via a very different flat-1000/rawMargin shape — see its own
  // doc), this is a fair "pure Nature rush" comparable to the other three racing policies.

  test("NatureSpending always favors Nature kinds, regardless of the opponent's faction mix") {
    val chaosHeavyOpponent = MazeState.initial.copy(
      buildings = List(building(1, 5, 5, BuildingKind.Cave), building(2, 6, 6, BuildingKind.Cave))
    )
    val state = withResources(wood = 100.0, fire = 0.0, light = 0.0)
    assert(
      NatureSpending.score(state, chaosHeavyOpponent, BuildingKind.Grove) >
        NatureSpending.score(state, chaosHeavyOpponent, BuildingKind.Cave)
    )
    assert(
      NatureSpending.score(state, chaosHeavyOpponent, BuildingKind.Stonehenge) >
        NatureSpending.score(state, chaosHeavyOpponent, BuildingKind.Church)
    )
  }

  test("NatureSpending breaks ties between Grove and Stonehenge by affordability margin") {
    val state = withResources(wood = 200.0, fire = 0.0, light = 0.0)
    assert(
      NatureSpending.score(state, noOpponent, BuildingKind.Grove) > NatureSpending
        .score(state, noOpponent, BuildingKind.Stonehenge),
      "Grove (wood5) leaves a much larger margin than Stonehenge (wood150)"
    )
  }

  // Diagnosed via a real transcript (rock-paper-scissors tuning pass): NatureSpending
  // built zero defense at all, and unlike Chaos/Science/Law's win conditions (cumulative
  // stats that don't undo), a corrupted-to-death Forest is REMOVED from Nature's own
  // forestCount — corruption doesn't just race Mort's own target, it actively reverses
  // Nature's progress. Forest/Jungle's own passive corruption self-heal (0.4-1.8%/sec,
  // Balance.ForestCorruptionHealPercentPerSec's doc) is close to breakeven against a lone
  // Zombie/Vampire (1.0-2.5%/sec) alone, and clustering (see HealClusterLayout) tips it
  // decisively — but Watchtower defense (killing raiders before they ever reach a
  // building) is still the more robust first line. Same fix
  // ScienceSpending/LawSpending already use for themselves — a capped Watchtower priority
  // tier, borrowed from Loi the same way ScienceSpending borrows Cave/Church/Tomb.
  test("NatureSpending favors Watchtower over Grove while fewer than 8 Watchtowers exist") {
    val state = withResources(wood = 100.0, fire = 0.0, light = 100.0)
    assert(
      NatureSpending.score(state, noOpponent, BuildingKind.Watchtower) >
        NatureSpending.score(state, noOpponent, BuildingKind.Grove),
      "Watchtower's defense priority should outrank Grove's better fallback margin while defense isn't secured yet"
    )
  }

  test("NatureSpending stops favoring Watchtower once 8 already exist") {
    val eightWatchtowers = withResources(wood = 100.0, fire = 0.0, light = 100.0).copy(
      buildings = (1 to 8).map(id => building(id, id, 1, BuildingKind.Watchtower)).toList
    )
    assertEqualsDouble(
      NatureSpending.score(eightWatchtowers, noOpponent, BuildingKind.Watchtower),
      0.25 * SpendingPolicy.resourceScore(eightWatchtowers, BuildingKind.Watchtower),
      1e-9,
      "a ninth Watchtower should score via the plain fallback once defense is already secured"
    )
  }

  // ── ScienceSpending ────────────────────────────────────────────────────
  // Mirrors PlunderSpending/CorruptionSpending's shape (flat bonuses for the racing kinds,
  // 0.25*resourceScore fallback for everything else) but for Science's win condition
  // (VictoryConditions.hasWonViaFondamentale), which needs exactly 5 Science labs
  // (LaboFondamental, then upgraded into LaboDeRecherche + the 4 "other" kinds, each
  // maxPerMaze: Some(1)) AND, since every "other" lab's research also costs its own
  // currency (Shadow/Fire/Light — see ResearchSpecs.all), a Tomb/Cave/Church to actually
  // produce them (see ScienceSpending's own doc for the Gold-starvation lockout this avoids).
  // Priority order: an unproduced currency's producer outranks LaboFondamental itself
  // (securing the economy first), which outranks the plain fallback every other kind uses.

  test(
    "ScienceSpending favors Tomb/Cave/Church over LaboFondamental while their currency has no producer yet"
  ) {
    // Ample stock of every currency (so no candidate hits marginFor's unaffordable floor),
    // but no buildings at all yet, so productionPerSec is 0 for Fire/Light/Shadow alike —
    // isolates the missing-producer bonus from a plain affordability difference.
    val state = MazeState.initial.copy(
      resources = Map(
        Resource.Wood -> 100.0,
        Resource.Fire -> 100.0,
        Resource.Light -> 100.0,
        Resource.Shadow -> 100.0,
        Resource.Crystal -> 100.0
      )
    )
    val fondamentalScore = ScienceSpending.score(state, noOpponent, BuildingKind.LaboFondamental)
    assert(
      ScienceSpending.score(state, noOpponent, BuildingKind.Cave) > fondamentalScore,
      "Cave should outrank LaboFondamental while Fire has no producer yet"
    )
    assert(
      ScienceSpending.score(state, noOpponent, BuildingKind.Church) > fondamentalScore,
      "Church should outrank LaboFondamental while Light has no producer yet"
    )
    assert(
      ScienceSpending.score(state, noOpponent, BuildingKind.Tomb) > fondamentalScore,
      "Tomb should outrank LaboFondamental while Shadow has no producer yet"
    )
  }

  test(
    "ScienceSpending stops favoring Cave/Church/Tomb once their currency already has a producer"
  ) {
    val alreadyProducing = MazeState.initial.copy(
      resources = Map(Resource.Wood -> 100.0, Resource.Crystal -> 100.0),
      buildings = List(
        building(1, 1, 1, BuildingKind.Cave),
        building(2, 1, 2, BuildingKind.Church),
        building(3, 1, 3, BuildingKind.Tomb)
      )
    )
    assertEqualsDouble(
      ScienceSpending.score(alreadyProducing, noOpponent, BuildingKind.Cave),
      0.25 * SpendingPolicy.resourceScore(alreadyProducing, BuildingKind.Cave),
      1e-9,
      "a second Cave should score via the plain fallback once Fire already has a producer"
    )
  }

  test(
    "ScienceSpending favors LaboFondamental over a generic kind, once every currency already has a producer"
  ) {
    val producersBuilt = MazeState.initial.copy(
      resources = Map(Resource.Wood -> 100.0, Resource.Crystal -> 100.0),
      buildings = List(
        building(1, 1, 1, BuildingKind.Cave),
        building(2, 1, 2, BuildingKind.Church),
        building(3, 1, 3, BuildingKind.Tomb)
      )
    )
    assert(
      ScienceSpending.score(producersBuilt, noOpponent, BuildingKind.LaboFondamental) >
        ScienceSpending.score(producersBuilt, noOpponent, BuildingKind.Grove),
      "LaboFondamental should outscore a non-Science kind while the lab count is still under 5"
    )
  }

  test("ScienceSpending stops favoring LaboFondamental once 5 Science labs already exist") {
    val fiveLabs = MazeState.initial.copy(
      resources = Map(
        Resource.Wood -> 100.0,
        Resource.Fire -> 100.0,
        Resource.Light -> 100.0,
        Resource.Crystal -> 100.0
      ),
      buildings = List(
        building(1, 1, 1, BuildingKind.LaboNaturel),
        building(2, 1, 2, BuildingKind.LaboSombre),
        building(3, 1, 3, BuildingKind.LaboDeRecherche),
        building(4, 1, 4, BuildingKind.LaboDeLaLoi),
        building(5, 1, 5, BuildingKind.LaboDuChaos)
      )
    )
    assertEqualsDouble(
      ScienceSpending.score(fiveLabs, noOpponent, BuildingKind.LaboFondamental),
      0.25 * SpendingPolicy.resourceScore(fiveLabs, BuildingKind.LaboFondamental),
      1e-9,
      "with every Science lab slot already filled, LaboFondamental should score via the plain fallback, not the flat bonus"
    )
  }

  // A pure economic rush (labs + producers, no defense) loses most matches on the clock:
  // Fondamentale's cheapest win takes far longer than a Chaos plunder race, and this
  // policy built no defense against the Goblin/Minotaur/Elf raiders that plunder race
  // relies on (confirmed via a full-ladder `sim/runMain towerdefense.sim.tournament` run:
  // maze-science lost almost every match to Chaos plunder specifically). Watchtower
  // (10 Wood/20 Light, 10 dmg/sec at 2-cell range — one-shots an Elf/Goblin, kills a
  // Minotaur in ~5s) is the fix: cheap, doubles as a Light producer, and FreeformLayout
  // already scores ranged candidates by path-danger overlap (see LayoutPolicy's own doc),
  // so pairing it with FreeformLayout places it on the enemy's path with no extra logic
  // needed here — this policy only has to make Watchtower worth building at all.
  test(
    "ScienceSpending favors Watchtower over LaboFondamental while fewer than 2 Watchtowers exist"
  ) {
    val state = MazeState.initial.copy(
      resources = Map(Resource.Wood -> 100.0, Resource.Light -> 100.0, Resource.Crystal -> 100.0)
    )
    assert(
      ScienceSpending.score(state, noOpponent, BuildingKind.Watchtower) >
        ScienceSpending.score(state, noOpponent, BuildingKind.LaboFondamental),
      "Watchtower should outrank LaboFondamental while defense isn't secured yet"
    )
  }

  test("ScienceSpending penalizes a third Watchtower once 2 already exist") {
    val twoWatchtowers = MazeState.initial.copy(
      resources = Map(Resource.Wood -> 100.0, Resource.Light -> 100.0, Resource.Crystal -> 100.0),
      buildings =
        List(building(1, 1, 1, BuildingKind.Watchtower), building(2, 1, 2, BuildingKind.Watchtower))
    )
    assertEqualsDouble(
      ScienceSpending.score(twoWatchtowers, noOpponent, BuildingKind.Watchtower),
      -2.0 + 0.25 * SpendingPolicy.resourceScore(twoWatchtowers, BuildingKind.Watchtower),
      1e-9,
      "a third Watchtower should score BELOW the plain fallback, not just lose its bonus — diagnosed via " +
        "transcript: without a real penalty, an abundant Wood/Light income kept the fallback alone building " +
        "Watchtower long past the cap (23 in one measured match), starving Chaos's entire raid as a side effect"
    )
  }

  // Diagnosed via a real `sim/run maze-plunder maze-science --log` transcript
  // (rock-paper-scissors tuning pass): with nothing discouraging it, ScienceSpending's
  // plain fallback (0.25*resourceScore) happily kept building/upgrading Grove into Forest
  // for Wood income — 13+ Forests in one observed match — which incidentally gave it a
  // full Nature-tier aura wall for free, on top of its own Watchtower defense. Forest's
  // aura is untouched (Nature's own vs-Death balance needs it as-is); instead this policy
  // now penalizes Grove/Forest/Jungle specifically once a small cap is cleared, since
  // Science's own economy never needed more than that much Wood income to begin with.
  test("ScienceSpending penalizes Grove/Forest/Jungle once 2 Nature buildings already exist") {
    val twoGroves = MazeState.initial.copy(
      resources = Map(Resource.Wood -> 100.0, Resource.Crystal -> 100.0),
      buildings = List(building(1, 1, 1, BuildingKind.Grove), building(2, 1, 2, BuildingKind.Grove))
    )
    assert(
      ScienceSpending.score(twoGroves, noOpponent, BuildingKind.Grove) <
        0.25 * SpendingPolicy.resourceScore(twoGroves, BuildingKind.Grove),
      "a third Grove should score BELOW the plain fallback once 2 Nature buildings already exist"
    )
  }

  test("ScienceSpending does not penalize a Grove/Forest/Jungle candidate below the cap") {
    val noNatureYet =
      MazeState.initial.copy(resources = Map(Resource.Wood -> 100.0, Resource.Crystal -> 100.0))
    assertEqualsDouble(
      ScienceSpending.score(noNatureYet, noOpponent, BuildingKind.Grove),
      0.25 * SpendingPolicy.resourceScore(noNatureYet, BuildingKind.Grove),
      1e-9,
      "the first Grove should score via the plain fallback, no penalty yet"
    )
  }

  test(
    "ScienceSpending's flat bonus counts a LaboFondamental not yet upgraded toward the 5-lab cap"
  ) {
    // 4 unupgraded LaboFondamental + this candidate would be the 5th Science-lab
    // building overall, so the flat bonus should still apply here (4 < 5).
    val fourFondamentals = MazeState.initial.copy(
      resources = Map(Resource.Crystal -> 100.0),
      buildings = List(
        building(1, 1, 1, BuildingKind.LaboFondamental),
        building(2, 1, 2, BuildingKind.LaboFondamental),
        building(3, 1, 3, BuildingKind.LaboFondamental),
        building(4, 1, 4, BuildingKind.LaboFondamental)
      )
    )
    assertEqualsDouble(
      ScienceSpending.score(fourFondamentals, noOpponent, BuildingKind.LaboFondamental),
      1.0 + 0.25 * SpendingPolicy.resourceScore(fourFondamentals, BuildingKind.LaboFondamental),
      1e-9,
      "the flat bonus (not just the fallback margin) should still apply for the 5th Science-lab building"
    )
  }

  // ── GrovePriority ──────────────────────────────────────────────────────

  test("GrovePriority scores Grove far above every fallback kind") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 100.0)
    val groveScore = GrovePriority.score(state, noOpponent, BuildingKind.Grove)
    BuildingKind.values.filterNot(_ == BuildingKind.Grove).foreach { kind =>
      assert(
        groveScore > GrovePriority.score(state, noOpponent, kind),
        s"Grove should outscore $kind"
      )
    }
  }

  test("GrovePriority ranks non-Grove fallback kinds by affordability margin") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    assert(
      GrovePriority.score(state, noOpponent, BuildingKind.Cave) > GrovePriority
        .score(state, noOpponent, BuildingKind.Labyrinth),
      "Cave spends a smaller fraction of the pool than Labyrinth"
    )
  }
