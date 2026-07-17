package towerdefense.domain

// Each component of CompositeStrategy is tested in isolation via a weight vector that
// zeroes out the other two, then combined to prove more than one signal can drive the
// final pick together. See the plan's "Component scores" section for what each measures.
class CompositeStrategyTest extends munit.FunSuite:

  private val noOpponent = MazeState.initial

  private def withResources(wood: Double = 0.0, fire: Double = 0.0, light: Double = 0.0): MazeState =
    MazeState.initial.copy(
      resources = Map(Resource.Wood -> wood, Resource.Fire -> fire, Resource.Light -> light)
    )

  private def building(id: Long, col: Int, row: Int, kind: BuildingKind): Building =
    Building(id, col, row, kind, spawnCountdownMs = 0.0)

  private def count(state: MazeState, kind: BuildingKind): Int = state.buildings.count(_.kind == kind)

  test("resource-only prefers the building that leaves the largest affordability margin") {
    // Flush enough to afford a Cave (5 wood / 10 fire) or a Grove (10 wood), but a
    // Grove at this wood level spends the same fraction of a much smaller pool, so a
    // pure resource-margin strategy should prefer the Cave here.
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    val strategy = CompositeStrategy(Weights(resource = 1.0, counter = 0.0, maze = 0.0))
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Cave), 1)
    assertEquals(count(result, BuildingKind.Grove), 0)
  }

  test("resourceScore divides the raw margin by one plus how many of that kind are already built") {
    val strategy = CompositeStrategy(Weights(resource = 1.0, counter = 0.0, maze = 0.0))
    val bare = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    val threeCaves = bare.copy(buildings =
      List(building(1, 1, 1, BuildingKind.Cave), building(2, 1, 2, BuildingKind.Cave), building(3, 1, 3, BuildingKind.Cave))
    )
    assertEquals(strategy.resourceScore(bare, BuildingKind.Cave), strategy.resourceScore(threeCaves, BuildingKind.Cave) * 4.0)
  }

  test("resource-only avoids piling into a single already-overbuilt kind once a fresher one scores better") {
    // Same currencies as the "prefers the building that leaves the largest affordability
    // margin" test above, where a bare board picks Cave over Grove — but this board
    // already has three Caves, so diminishing returns should flip the pick to Grove
    // instead of a fourth Cave. Regression for resource-only's observed single-resource
    // spam trap (a real transcript showed it building Cave after Cave while Grove/Church/
    // Watchtower sat untouched, stalling its own economy on one currency pair).
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0).copy(buildings =
      List(building(1, 1, 1, BuildingKind.Cave), building(2, 1, 2, BuildingKind.Cave), building(3, 1, 3, BuildingKind.Cave))
    )
    val strategy = CompositeStrategy(Weights(resource = 1.0, counter = 0.0, maze = 0.0))
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Grove), 1)
    assertEquals(count(result, BuildingKind.Cave), 3)
  }

  test("counter-only mirrors the opponent's dominant faction") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 100.0)
    val chaosHeavyOpponent = MazeState.initial.copy(
      buildings = List(building(1, 5, 5, BuildingKind.Cave), building(2, 6, 6, BuildingKind.Cave))
    )
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 1.0, maze = 0.0))
    val result = strategy.maybeBuild(state, chaosHeavyOpponent)
    assert(
      count(result, BuildingKind.Cave) == 1 || count(result, BuildingKind.Labyrinth) == 1,
      "expected a Chaos building"
    )
    assertEquals(count(result, BuildingKind.Grove), 0)
    assertEquals(count(result, BuildingKind.Church), 0)
  }

  test("counter-only ignores Watchtower/Loi investment too, same as Eglise") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 100.0)
    val loiHeavyOpponent = MazeState.initial.copy(
      buildings = List(
        building(1, 5, 5, BuildingKind.Watchtower),
        building(2, 6, 6, BuildingKind.Watchtower),
        building(3, 7, 7, BuildingKind.Watchtower),
        building(4, 2, 2, BuildingKind.Forest)
      )
    )
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 1.0, maze = 0.0))
    val result = strategy.maybeBuild(state, loiHeavyOpponent)
    assertEquals(
      count(result, BuildingKind.Watchtower),
      0,
      "must not mirror Watchtower even though it's the opponent's largest count"
    )
    assertEquals(
      count(result, BuildingKind.Grove),
      1,
      "Nature (1, from the opponent's Forest) leads over Chaos (0); Grove is the only " +
        "Nature-family kind this strategy can actually build directly"
    )
  }

  test("resource-only can pick a watchtower over a grove when light is abundant") {
    // Grove's only margin is on wood (0.9). Watchtower averages a wood margin (0.9) with
    // an even better light margin (0.95, since light is flush here), edging Grove out.
    val state = withResources(wood = 100.0, fire = 0.0, light = 100.0)
    val strategy = CompositeStrategy(Weights(resource = 1.0, counter = 0.0, maze = 0.0))
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Watchtower), 1)
    assertEquals(count(result, BuildingKind.Grove), 0)
  }

  test("counter-only ignores Eglise/Loi investment since it never feeds a victory condition") {
    // Opponent's largest single count is Eglises, but Eglise doesn't count toward either
    // VictoryConditions target (forests or resourcesPlundered) — mirroring it would trap
    // counter-only in a permanent stalemate against an opponent that spams Eglises. It
    // must compare Nature vs Chaos investment only, ignoring Loi entirely.
    val state = withResources(wood = 100.0, fire = 100.0, light = 100.0)
    val loiHeavyOpponent = MazeState.initial.copy(
      buildings = List(
        building(1, 5, 5, BuildingKind.Church),
        building(2, 6, 6, BuildingKind.Church),
        building(3, 7, 7, BuildingKind.Church),
        building(4, 2, 2, BuildingKind.Forest)
      )
    )
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 1.0, maze = 0.0))
    val result = strategy.maybeBuild(state, loiHeavyOpponent)
    assertEquals(
      count(result, BuildingKind.Church),
      0,
      "must not mirror Eglise even though it's the opponent's largest count"
    )
    assertEquals(
      count(result, BuildingKind.Grove),
      1,
      "Nature (1, from the opponent's Forest) leads over Chaos (0)"
    )
  }

  test("maze-only picks the cell that maximizes the resulting enemy path length") {
    val state = withResources(wood = 100.0, fire = 0.0, light = 0.0)
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Grove), 1)
    val built = result.buildings.find(_.kind == BuildingKind.Grove).get
    val chosenLength = Pathfinding
      .shortestPath(GridConfig.spawnCell, GridConfig.goalCell, Set((built.col, built.row)))
      .map(_.length)
      .getOrElse(0)
    val bestPossible = GridConfig.allCells
      .filterNot(c => Set(GridConfig.spawnCell, GridConfig.goalCell).contains(c))
      .flatMap(c => Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, Set(c)).map(_.length))
      .max
    assertEquals(chosenLength, bestPossible)
  }

  test("dangerScore combines path length with aura-damage exposure from existing forests") {
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val noForests = MazeState.initial
    val isolatedPath =
      Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, noForests.buildingCells + ((6, 6))).get
    assertEquals(
      strategy.dangerScore(noForests, (6, 6), isAuraCandidate = false),
      isolatedPath.length.toDouble,
      "with no forests anywhere, danger score is plain path length"
    )

    val withForest = MazeState.initial.copy(buildings = List(building(1, 6, 5, BuildingKind.Forest)))
    val exposedPath =
      Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, withForest.buildingCells + ((6, 6))).get
    val adjacentCount = exposedPath.count(c => Pathfinding.neighbors(c).contains((6, 5)))
    assertEquals(
      strategy.dangerScore(withForest, (6, 6), isAuraCandidate = false),
      exposedPath.length.toDouble + Balance.AuraDamagePerSec * adjacentCount,
      "each path cell adjacent to an existing forest adds one AuraDamagePerSec hit"
    )
  }

  test(
    "dangerScore sums damage from every adjacent forest a path cell borders, not just whether any exist"
  ) {
    // CombatEngine.applyDamageSources sums damagePerHit from EVERY forest adjacent to an
    // enemy, so a corridor flanked by forests on both sides deals double the damage per
    // second of a single-walled one. dangerScore must reflect that, or the AI has no
    // reason to prefer flanking corridors over one-sided walls despite them killing
    // roughly twice as fast.
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val onePath = List((5, 5))
    val oneFlank = Set((5, 4))
    val twoFlanks = Set((5, 4), (5, 6))
    val oneFlankScore = strategy.pathDangerScore(onePath, oneFlank)
    val twoFlankScore = strategy.pathDangerScore(onePath, twoFlanks)
    assertEquals(oneFlankScore, 1.0 + Balance.AuraDamagePerSec)
    assertEquals(twoFlankScore, 1.0 + 2 * Balance.AuraDamagePerSec)
  }

  test("dangerScore combines path length with ranged damage exposure from an existing watchtower") {
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val withTower = MazeState.initial.copy(buildings = List(building(1, 6, 5, BuildingKind.Watchtower)))
    val exposedPath =
      Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, withTower.buildingCells + ((6, 6))).get
    val inRangeCount = exposedPath.count(c => CombatEngine.chebyshevDistance(c, (6, 5)) <= Balance.WatchtowerRangeCells)
    assertEquals(
      strategy.dangerScore(withTower, (6, 6), isAuraCandidate = false),
      exposedPath.length.toDouble + Balance.WatchtowerDamagePerSec * inRangeCount,
      "each path cell within WatchtowerRangeCells of an existing watchtower adds one WatchtowerDamagePerSec hit"
    )
  }

  test("dangerScore credits a new Watchtower candidate for its own range only when scored as ranged") {
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val state = MazeState.initial
    val path = Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, state.buildingCells + ((6, 6))).get
    val selfRangeCount = path.count(c => CombatEngine.chebyshevDistance(c, (6, 6)) <= Balance.WatchtowerRangeCells)
    assertEquals(
      strategy.dangerScore(state, (6, 6), isAuraCandidate = false, isRangedCandidate = true),
      path.length.toDouble + Balance.WatchtowerDamagePerSec * selfRangeCount,
      "a candidate scored isRangedCandidate=true credits its own range, since it auras once built (same idea as isAuraCandidate for Grove)"
    )
    assertEquals(
      strategy.dangerScore(state, (6, 6), isAuraCandidate = false, isRangedCandidate = false),
      path.length.toDouble,
      "without isRangedCandidate, a Watchtower candidate doesn't credit itself for its own range"
    )
  }

  test("pathDangerScore sums watchtower damage for every tower a path cell is within range of") {
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val onePath = List((5, 5))
    val oneTower = Set((5, 4))
    val twoTowers = Set((5, 4), (6, 6))
    val oneTowerScore = strategy.pathDangerScore(onePath, Set.empty, oneTower)
    val twoTowerScore = strategy.pathDangerScore(onePath, Set.empty, twoTowers)
    assertEquals(oneTowerScore, 1.0 + Balance.WatchtowerDamagePerSec)
    assertEquals(twoTowerScore, 1.0 + 2 * Balance.WatchtowerDamagePerSec)
  }

  test("pathDangerScore combines aura and watchtower damage together when both are present") {
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val onePath = List((5, 5))
    val forest = Set((5, 4))
    val tower = Set((6, 6))
    val score = strategy.pathDangerScore(onePath, forest, tower)
    assertEquals(score, 1.0 + Balance.AuraDamagePerSec + Balance.WatchtowerDamagePerSec)
  }

  test(
    "maze-only prefers a Watchtower over a Grove at the same cell, since ranged damage now outscores future aura"
  ) {
    // WatchtowerDamagePerSec (10.0) over WatchtowerRangeCells (2) beats AuraDamagePerSec
    // (2.0) over adjacency (1) even before accounting for range covering more path
    // cells — Watchtower simply deals far more damage per wood/light spent, and unlike
    // Forest's aura it doesn't need any upgrade chain to start working (see
    // isMazeAuraCandidate's doc and ADR 0008 for why maze-weighted strategies used to
    // have zero reason to ever want one).
    val state = withResources(wood = 100.0, fire = 0.0, light = 100.0)
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val (col, row) = GridConfig.allCells
      .filterNot(Set(GridConfig.spawnCell, GridConfig.goalCell).contains)
      .maxBy(c => strategy.dangerScore(state, c, isAuraCandidate = false))
    val watchtowerScore = strategy.dangerScore(state, (col, row), isAuraCandidate = false, isRangedCandidate = true)
    val groveScore = strategy.dangerScore(state, (col, row), isAuraCandidate = true)
    assert(
      watchtowerScore > groveScore,
      "expected Watchtower's own ranged-damage credit to outscore Grove's future-aura credit at the same cell"
    )
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Watchtower), 1)
    assertEquals(count(result, BuildingKind.Grove), 0)
  }

  test(
    "maze-only picks the new Grove candidate maximizing length + an existing forest's exposure together"
  ) {
    // A new Grove candidate is scored isAuraCandidate = true (see isMazeAuraCandidate's
    // doc: it will aura the moment maybeUpgrade grows it into a Forest), same as the
    // existing Forest fixture already contributes — that's what gives the maze score
    // something to differentiate cells by beyond raw path length.
    val state = withResources(wood = 100.0, fire = 0.0, light = 0.0).copy(
      buildings = List(building(1, 6, 5, BuildingKind.Forest)),
      nextId = 2L
    )
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val result = strategy.maybeBuild(state, MazeState.initial)
    val built = result.buildings.find(_.kind == BuildingKind.Grove).get
    val bestPossibleScore = GridConfig.allCells
      .filterNot(Set(GridConfig.spawnCell, GridConfig.goalCell, (6, 5)).contains)
      .map(c => strategy.dangerScore(state, c, isAuraCandidate = true))
      .max
    assertEquals(strategy.dangerScore(state, (built.col, built.row), isAuraCandidate = true), bestPossibleScore)
  }

  // Regression test for the "wasted Cave" pattern found via `make sim` (see
  // isMazeAuraCandidate's doc): a Grove and a Cave tied on raw path length at the same
  // cell used to tie outright (neither auras), so maze-only's pick depended on incidental
  // affordability rather than long-term value. Now Grove's own future-aura credit must
  // make it win outright, not just tie.
  test("maze-only prefers a Grove over an equally-well-placed Cave, since only the Grove can ever aura") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val (col, row) = GridConfig.allCells
      .filterNot(Set(GridConfig.spawnCell, GridConfig.goalCell).contains)
      .maxBy(c => strategy.dangerScore(state, c, isAuraCandidate = false))
    val groveScore = strategy.dangerScore(state, (col, row), isAuraCandidate = true)
    val caveScore = strategy.dangerScore(state, (col, row), isAuraCandidate = false)
    assert(groveScore > caveScore, s"expected Grove's own future-aura credit to score higher at the same cell")
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Grove), 1)
    assertEquals(count(result, BuildingKind.Cave), 0)
  }

  test(
    "a combined weight vector can pick a building neither resource ranking alone would predict"
  ) {
    // Fire is scarce enough that Cave's fire margin is worse than Grove's wood margin, so
    // resource-only alone prefers Grove. The opponent is Chaos-heavy though, so
    // counter-only alone prefers Cave. A vector with both weights active follows the
    // counter pick even though it's the worse resource margin — a result you can't
    // predict from resourceScore ranking alone, only by also accounting for counterScore.
    val state = withResources(wood = 100.0, fire = 15.0, light = 0.0)
    val chaosHeavyOpponent = MazeState.initial.copy(
      buildings = List(building(1, 5, 5, BuildingKind.Cave), building(2, 6, 6, BuildingKind.Cave))
    )
    val resourceOnly = CompositeStrategy(Weights(resource = 1.0, counter = 0.0, maze = 0.0))
    val combined = CompositeStrategy(Weights(resource = 1.0, counter = 1.0, maze = 0.0))
    val resourceOnlyBuilt = resourceOnly.maybeBuild(state, chaosHeavyOpponent).buildings.head
    val combinedBuilt = combined.maybeBuild(state, chaosHeavyOpponent).buildings.head
    assertEquals(resourceOnlyBuilt.kind, BuildingKind.Grove)
    assertEquals(combinedBuilt.kind, BuildingKind.Cave)
  }

  test("does nothing without enough resources for any building, like LinearStrategy") {
    val state = withResources(wood = 0.0, fire = 0.0, light = 0.0)
    val strategy = CompositeStrategy(Weights(resource = 1.0, counter = 1.0, maze = 1.0))
    assertEquals(strategy.maybeBuild(state, noOpponent), state)
  }

  // Regression test: caught via `make sim` — a tournament across the whole AiStrategy
  // ladder showed maze-only (a CompositeStrategy(maze=1.0) instance) never landing a
  // single kill across two full matches, because it never upgraded a single Grove into a
  // Forest, so its entire dangerScore premise (routing the enemy path past an
  // aura-dealing building) was building harmless Groves the whole game — Grove isn't in
  // CombatEngine.auraBuildingKinds, only Forest/Jungle are. Every other AiStrategy
  // implementation (LinearStrategy, TemplateStrategy) already overrides maybeUpgrade;
  // CompositeStrategy simply never got one.
  test("maybeUpgrade grows an affordable Grove into a Forest") {
    val grove = building(1, 5, 5, BuildingKind.Grove)
    val state = withResources(wood = 1_000.0).copy(buildings = List(grove))
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val result = strategy.maybeUpgrade(state, noOpponent)
    assertEquals(count(result, BuildingKind.Forest), 1)
    assertEquals(count(result, BuildingKind.Grove), 0)
  }

  test("maybeUpgrade does nothing when there is no upgradeable building") {
    val state = withResources(wood = 1_000.0)
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    assertEquals(strategy.maybeUpgrade(state, noOpponent), state)
  }

  test("maybeUpgrade does nothing when the only Grove can't afford the Forest tier") {
    val grove = building(1, 5, 5, BuildingKind.Grove)
    val poor = withResources(wood = Balance.GroveCostWood).copy(buildings = List(grove))
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    assertEquals(strategy.maybeUpgrade(poor, noOpponent), poor)
  }
