package towerdefense.domain

// LayoutPolicy is the "which cell" half of an AiStrategy — the counterpart to
// SpendingPolicy's "which building kind". FreeformLayout's dangerScore/pathDangerScore
// are moved verbatim from CompositeStrategy; these tests are the same regression suite
// that used to live in CompositeStrategyTest.
class LayoutPolicyTest extends munit.FunSuite:

  private def withResources(wood: Double = 0.0, fire: Double = 0.0, light: Double = 0.0): MazeState =
    MazeState.initial.copy(
      resources = Map(Resource.Wood -> wood, Resource.Fire -> fire, Resource.Light -> light)
    )

  private def building(id: Long, col: Int, row: Int, kind: BuildingKind): Building =
    Building(id, col, row, kind, spawnCountdownMs = 0.0)

  // ── NoLayoutPreference ─────────────────────────────────────────────────

  test("NoLayoutPreference scores every cell and kind identically") {
    assertEquals(NoLayoutPreference.score(MazeState.initial, BuildingKind.Grove, (3, 3)), 0.0)
    assertEquals(NoLayoutPreference.score(MazeState.initial, BuildingKind.Cave, (7, 7)), 0.0)
  }

  // ── FreeformLayout ─────────────────────────────────────────────────────

  test("dangerScore combines path length with aura-damage exposure from existing forests") {
    val noForests = MazeState.initial
    val isolatedPath =
      Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, noForests.buildingCells + ((6, 6))).get
    assertEquals(
      FreeformLayout.dangerScore(noForests, (6, 6), isAuraCandidate = false),
      isolatedPath.length.toDouble,
      "with no forests anywhere, danger score is plain path length"
    )

    val withForest = MazeState.initial.copy(buildings = List(building(1, 6, 5, BuildingKind.Forest)))
    val exposedPath =
      Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, withForest.buildingCells + ((6, 6))).get
    val adjacentCount = exposedPath.count(c => Pathfinding.neighbors(c).contains((6, 5)))
    assertEquals(
      FreeformLayout.dangerScore(withForest, (6, 6), isAuraCandidate = false),
      exposedPath.length.toDouble + Balance.AuraDamagePerSec * adjacentCount,
      "each path cell adjacent to an existing forest adds one AuraDamagePerSec hit"
    )
  }

  test(
    "dangerScore sums damage from every adjacent forest a path cell borders, not just whether any exist"
  ) {
    val onePath = List((5, 5))
    val oneFlank = Set((5, 4))
    val twoFlanks = Set((5, 4), (5, 6))
    val oneFlankScore = FreeformLayout.pathDangerScore(onePath, oneFlank)
    val twoFlankScore = FreeformLayout.pathDangerScore(onePath, twoFlanks)
    assertEquals(oneFlankScore, 1.0 + Balance.AuraDamagePerSec)
    assertEquals(twoFlankScore, 1.0 + 2 * Balance.AuraDamagePerSec)
  }

  test("dangerScore combines path length with ranged damage exposure from an existing watchtower") {
    val withTower = MazeState.initial.copy(buildings = List(building(1, 6, 5, BuildingKind.Watchtower)))
    val exposedPath =
      Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, withTower.buildingCells + ((6, 6))).get
    val inRangeCount = exposedPath.count(c => CombatEngine.chebyshevDistance(c, (6, 5)) <= Balance.WatchtowerRangeCells)
    assertEquals(
      FreeformLayout.dangerScore(withTower, (6, 6), isAuraCandidate = false),
      exposedPath.length.toDouble + Balance.WatchtowerDamagePerSec * inRangeCount,
      "each path cell within WatchtowerRangeCells of an existing watchtower adds one WatchtowerDamagePerSec hit"
    )
  }

  test("dangerScore credits a new Watchtower candidate for its own range only when scored as ranged") {
    val state = MazeState.initial
    val path = Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, state.buildingCells + ((6, 6))).get
    val selfRangeCount = path.count(c => CombatEngine.chebyshevDistance(c, (6, 6)) <= Balance.WatchtowerRangeCells)
    assertEquals(
      FreeformLayout.dangerScore(state, (6, 6), isAuraCandidate = false, isRangedCandidate = true),
      path.length.toDouble + Balance.WatchtowerDamagePerSec * selfRangeCount,
      "a candidate scored isRangedCandidate=true credits its own range, since it auras once built (same idea as isAuraCandidate for Grove)"
    )
    assertEquals(
      FreeformLayout.dangerScore(state, (6, 6), isAuraCandidate = false, isRangedCandidate = false),
      path.length.toDouble,
      "without isRangedCandidate, a Watchtower candidate doesn't credit itself for its own range"
    )
  }

  test("pathDangerScore sums watchtower damage for every tower a path cell is within range of") {
    val onePath = List((5, 5))
    val oneTower = Set((5, 4))
    val twoTowers = Set((5, 4), (6, 6))
    val oneTowerScore = FreeformLayout.pathDangerScore(onePath, Set.empty, oneTower)
    val twoTowerScore = FreeformLayout.pathDangerScore(onePath, Set.empty, twoTowers)
    assertEquals(oneTowerScore, 1.0 + Balance.WatchtowerDamagePerSec)
    assertEquals(twoTowerScore, 1.0 + 2 * Balance.WatchtowerDamagePerSec)
  }

  test("pathDangerScore combines aura and watchtower damage together when both are present") {
    val onePath = List((5, 5))
    val forest = Set((5, 4))
    val tower = Set((6, 6))
    val score = FreeformLayout.pathDangerScore(onePath, forest, tower)
    assertEquals(score, 1.0 + Balance.AuraDamagePerSec + Balance.WatchtowerDamagePerSec)
  }

  test("score(kind, cell) credits Grove/Forest/Jungle/Watchtower as their own future danger source") {
    val state = withResources(wood = 100.0, fire = 0.0, light = 0.0)
    val (col, row) = GridConfig.allCells
      .filterNot(Set(GridConfig.spawnCell, GridConfig.goalCell).contains)
      .maxBy(c => FreeformLayout.dangerScore(state, c, isAuraCandidate = false))
    val groveScore = FreeformLayout.score(state, BuildingKind.Grove, (col, row))
    val caveScore = FreeformLayout.score(state, BuildingKind.Cave, (col, row))
    assert(groveScore > caveScore, "Grove will aura once upgraded, Cave never will")
  }

  test("score(kind, cell) prefers Watchtower over Grove at the same cell, ranged damage outscoring future aura") {
    val state = withResources(wood = 100.0, fire = 0.0, light = 100.0)
    val (col, row) = GridConfig.allCells
      .filterNot(Set(GridConfig.spawnCell, GridConfig.goalCell).contains)
      .maxBy(c => FreeformLayout.dangerScore(state, c, isAuraCandidate = false))
    assert(FreeformLayout.score(state, BuildingKind.Watchtower, (col, row)) > FreeformLayout.score(state, BuildingKind.Grove, (col, row)))
  }

  // ── TemplateLayout ─────────────────────────────────────────────────────

  test("TemplateLayout scores the earliest remaining template cell highest") {
    val layout = TemplateLayout(MazeTemplate.comb)
    val target = MazeTemplate.comb(GridConfig.cols, GridConfig.rows)
    val first = target.head
    val second = target(1)
    assert(
      layout.score(MazeState.initial, BuildingKind.Grove, first) >
        layout.score(MazeState.initial, BuildingKind.Grove, second)
    )
  }

  test("TemplateLayout scores a cell outside the template as negative infinity") {
    val layout = TemplateLayout(MazeTemplate.comb)
    val outside = GridConfig.allCells.find(c => !MazeTemplate.comb(GridConfig.cols, GridConfig.rows).contains(c)).get
    assertEquals(layout.score(MazeState.initial, BuildingKind.Grove, outside), Double.NegativeInfinity)
  }

  test("TemplateLayout scores an already-built template cell as negative infinity, so it's never picked again") {
    val layout = TemplateLayout(MazeTemplate.comb)
    val (col, row) = MazeTemplate.comb(GridConfig.cols, GridConfig.rows).head
    val built = Placement.tryPlaceBuilding(withResources(wood = 100.0), BuildingKind.Grove, col, row).toOption.get
    assertEquals(layout.score(built, BuildingKind.Grove, (col, row)), Double.NegativeInfinity)
  }

  test("TemplateLayout ignores which kind is being scored") {
    val layout = TemplateLayout(MazeTemplate.comb)
    val cell = MazeTemplate.comb(GridConfig.cols, GridConfig.rows).head
    assertEquals(
      layout.score(MazeState.initial, BuildingKind.Grove, cell),
      layout.score(MazeState.initial, BuildingKind.Cave, cell)
    )
  }
