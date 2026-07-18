package towerdefense.domain

// SpendingPolicy is the "which building kind" half of an AiStrategy — the counterpart to
// LayoutPolicy's "which cell". These tests exercise SpendingPolicy.resourceScore/
// counterScore/rawMargin directly (moved verbatim from CompositeStrategy, now with a
// growth-awareness term — see below), plus the concrete policies built on top of them.
class SpendingPolicyTest extends munit.FunSuite:

  private val noOpponent = MazeState.initial

  private def withResources(wood: Double = 0.0, fire: Double = 0.0, light: Double = 0.0): MazeState =
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
      SpendingPolicy.resourceScore(state, BuildingKind.Cave) > SpendingPolicy.resourceScore(state, BuildingKind.Grove),
      "Cave spends a smaller fraction of the pool than Grove at this resource level"
    )
  }

  test("resourceScore divides the raw margin by one plus how many of that kind are already built") {
    // Labyrinth produces nothing (BuildingSpecs.all(Labyrinth).produces is empty), so
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

  test("resourceScore rewards a kind that would establish production of a currently-unproduced resource") {
    // Regression for a real lockout found via `sim/run`: two Watchtowers already stand
    // (so Light already has production) but Wood never has, since no Grove was ever
    // built — a pure-margin score alone still favored a third Watchtower (whose Wood term
    // is averaged against Light's now-unpenalized margin) over Grove (whose only term is
    // Wood's, sinking with the growth penalty) — the exact kind that would fix the Wood
    // shortage kept losing to one that only shares its cost. growthBonus is what must flip
    // this, not the margin math alone.
    val woodNeverProduced = withResources(wood = 12.0, fire = 0.0, light = 100.0).copy(
      buildings = List(building(1, 1, 1, BuildingKind.Watchtower), building(2, 2, 2, BuildingKind.Watchtower))
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

  test("rawMargin matches the plain affordability margin once every spent resource has some production") {
    val state = withResources(wood = 100.0, fire = 100.0).copy(
      buildings = List(building(1, 1, 1, BuildingKind.Grove), building(2, 2, 2, BuildingKind.Cave))
    )
    val plainCaveMargin = ((100.0 - Balance.CaveCostWood) / 100.0 + (100.0 - Balance.CaveCostFire) / 100.0) / 2.0
    assertEqualsDouble(SpendingPolicy.rawMargin(state, BuildingKind.Cave), plainCaveMargin, 1e-9)
  }

  test("rawMargin is strictly worse than the plain margin when a spent resource has zero production") {
    val bare = withResources(wood = 100.0, fire = 100.0)
    val plainCaveMargin = ((100.0 - Balance.CaveCostWood) / 100.0 + (100.0 - Balance.CaveCostFire) / 100.0) / 2.0
    assert(SpendingPolicy.rawMargin(bare, BuildingKind.Cave) < plainCaveMargin)
  }

  test("rawMargin never returns NaN when a zero-cost resource's stock has also dropped to exactly zero") {
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

  // ── counterScore ───────────────────────────────────────────────────────

  test("counterScore mirrors the opponent's dominant faction") {
    val chaosHeavyOpponent = MazeState.initial.copy(
      buildings = List(building(1, 5, 5, BuildingKind.Cave), building(2, 6, 6, BuildingKind.Cave))
    )
    assertEquals(SpendingPolicy.counterScore(chaosHeavyOpponent, BuildingKind.Cave), 1.0)
    assertEquals(SpendingPolicy.counterScore(chaosHeavyOpponent, BuildingKind.Grove), 0.0)
  }

  test("counterScore ignores Loi (Church/Watchtower) investment since it feeds no victory condition") {
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

  test("counterScore ignores Science (the five Labo* kinds) investment since it feeds no victory condition") {
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
    assert(policy.score(state, noOpponent, BuildingKind.Cave) > policy.score(state, noOpponent, BuildingKind.Grove))
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
    assert(resourceOnly.score(state, chaosHeavyOpponent, BuildingKind.Grove) > resourceOnly.score(state, chaosHeavyOpponent, BuildingKind.Cave))
    assert(combined.score(state, chaosHeavyOpponent, BuildingKind.Cave) > combined.score(state, chaosHeavyOpponent, BuildingKind.Grove))
  }

  // ── PlunderSpending ────────────────────────────────────────────────────

  test("PlunderSpending always favors Chaos kinds, regardless of the opponent's faction mix") {
    val natureHeavyOpponent = MazeState.initial.copy(
      buildings = List(building(1, 5, 5, BuildingKind.Forest), building(2, 6, 6, BuildingKind.Forest))
    )
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    assert(PlunderSpending.score(state, natureHeavyOpponent, BuildingKind.Cave) > PlunderSpending.score(state, natureHeavyOpponent, BuildingKind.Grove))
    assert(PlunderSpending.score(state, natureHeavyOpponent, BuildingKind.Labyrinth) > PlunderSpending.score(state, natureHeavyOpponent, BuildingKind.Church))
  }

  test("PlunderSpending breaks ties between Cave and Labyrinth by affordability margin") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    assert(
      PlunderSpending.score(state, noOpponent, BuildingKind.Cave) > PlunderSpending.score(state, noOpponent, BuildingKind.Labyrinth),
      "Cave (wood5/fire10) leaves a much larger margin than Labyrinth (wood20/fire40)"
    )
  }

  // ── CorruptionSpending ─────────────────────────────────────────────────

  test("CorruptionSpending always favors Mort kinds, regardless of the opponent's faction mix") {
    val natureHeavyOpponent = MazeState.initial.copy(
      buildings = List(building(1, 5, 5, BuildingKind.Forest), building(2, 6, 6, BuildingKind.Forest))
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
    val state = MazeState.initial.copy(resources = Map(Resource.Wood -> 100.0, Resource.Shadow -> 100.0))
    assert(
      CorruptionSpending.score(state, noOpponent, BuildingKind.Tomb) >
        CorruptionSpending.score(state, noOpponent, BuildingKind.BlackCastle),
      "Tomb (wood5/shadow10) leaves a much larger margin than BlackCastle (wood20/shadow40)"
    )
  }

  // ── GrovePriority ──────────────────────────────────────────────────────

  test("GrovePriority scores Grove far above every fallback kind") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 100.0)
    val groveScore = GrovePriority.score(state, noOpponent, BuildingKind.Grove)
    BuildingKind.values.filterNot(_ == BuildingKind.Grove).foreach { kind =>
      assert(groveScore > GrovePriority.score(state, noOpponent, kind), s"Grove should outscore $kind")
    }
  }

  test("GrovePriority ranks non-Grove fallback kinds by affordability margin") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    assert(
      GrovePriority.score(state, noOpponent, BuildingKind.Cave) > GrovePriority.score(state, noOpponent, BuildingKind.Labyrinth),
      "Cave spends a smaller fraction of the pool than Labyrinth"
    )
  }
