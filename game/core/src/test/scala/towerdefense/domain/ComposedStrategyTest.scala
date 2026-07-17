package towerdefense.domain

// ComposedStrategy is the machinery that combines a LayoutPolicy and a SpendingPolicy
// into one AiStrategy. SpendingPolicyTest/LayoutPolicyTest cover the policies themselves;
// these tests cover the combination: candidate filtering, the "no finite layout score ->
// no-op" rule, weight blending, and the random tie-break.
class ComposedStrategyTest extends munit.FunSuite:

  private val noOpponent = MazeState.initial

  private def withResources(wood: Double = 0.0, fire: Double = 0.0, light: Double = 0.0): MazeState =
    MazeState.initial.copy(
      resources = Map(Resource.Wood -> wood, Resource.Fire -> fire, Resource.Light -> light)
    )

  private def building(id: Long, col: Int, row: Int, kind: BuildingKind): Building =
    Building(id, col, row, kind, spawnCountdownMs = 0.0)

  private def count(state: MazeState, kind: BuildingKind): Int = state.buildings.count(_.kind == kind)

  private def fixedSeed(seed: Long): scala.util.Random = new scala.util.Random(seed)

  test("does nothing without enough resources for any building") {
    val state = withResources(wood = 0.0, fire = 0.0, light = 0.0)
    val strategy = ComposedStrategy(NoLayoutPreference, WeightedSpending(1.0, 1.0), random = fixedSeed(1))
    assertEquals(strategy.maybeBuild(state, noOpponent), state)
  }

  test("resource-only-equivalent (NoLayoutPreference + WeightedSpending(1,0)) prefers the largest margin") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    val strategy = ComposedStrategy(NoLayoutPreference, WeightedSpending(1.0, 0.0), random = fixedSeed(1))
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Cave), 1)
    assertEquals(count(result, BuildingKind.Grove), 0)
  }

  test("maze-only-equivalent (FreeformLayout + WeightedSpending(0,0)) maximizes the resulting enemy path length") {
    val state = withResources(wood = 100.0, fire = 0.0, light = 0.0)
    val strategy = ComposedStrategy(FreeformLayout, WeightedSpending(0.0, 0.0), random = fixedSeed(1))
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Grove), 1)
    val built = result.buildings.find(_.kind == BuildingKind.Grove).get
    val chosenLength = Pathfinding
      .shortestPath(GridConfig.spawnCell, GridConfig.goalCell, Set((built.col, built.row)))
      .map(_.length)
      .getOrElse(0)
    val bestPossible = GridConfig.allCells
      .filterNot(Set(GridConfig.spawnCell, GridConfig.goalCell).contains)
      .flatMap(c => Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, Set(c)).map(_.length))
      .max
    assertEquals(chosenLength, bestPossible)
  }

  test("a weight blend can pick a kind neither component alone would predict") {
    val state = withResources(wood = 100.0, fire = 15.0, light = 0.0)
    val chaosHeavyOpponent = MazeState.initial.copy(
      buildings = List(building(1, 5, 5, BuildingKind.Cave), building(2, 6, 6, BuildingKind.Cave))
    )
    val resourceOnly = ComposedStrategy(NoLayoutPreference, WeightedSpending(1.0, 0.0), random = fixedSeed(1))
    val combined = ComposedStrategy(NoLayoutPreference, WeightedSpending(1.0, 1.0), random = fixedSeed(1))
    val resourceOnlyBuilt = resourceOnly.maybeBuild(state, chaosHeavyOpponent).buildings.head
    val combinedBuilt = combined.maybeBuild(state, chaosHeavyOpponent).buildings.head
    assertEquals(resourceOnlyBuilt.kind, BuildingKind.Grove)
    assertEquals(combinedBuilt.kind, BuildingKind.Cave)
  }

  test("maybeUpgrade grows an affordable Grove into a Forest, same shared behavior as every AiStrategy") {
    val grove = building(1, 5, 5, BuildingKind.Grove)
    val state = withResources(wood = 1_000.0).copy(buildings = List(grove))
    val strategy = ComposedStrategy(FreeformLayout, WeightedSpending(0.0, 0.0), random = fixedSeed(1))
    val result = strategy.maybeUpgrade(state, noOpponent)
    assertEquals(count(result, BuildingKind.Forest), 1)
    assertEquals(count(result, BuildingKind.Grove), 0)
  }

  // ── TemplateLayout integration: never off-template ────────────────────

  test("a TemplateLayout combination places exactly one building, on a template cell") {
    val state = withResources(wood = 100.0)
    val strategy = ComposedStrategy(TemplateLayout(MazeTemplate.comb), GrovePriority, random = fixedSeed(1))
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(result.buildings.size, 1)
    val built = result.buildings.head
    val targetSet = MazeTemplate.comb(GridConfig.cols, GridConfig.rows).toSet
    assert(targetSet.contains((built.col, built.row)))
  }

  test("a TemplateLayout combination never builds a cell outside the template") {
    val strategy = ComposedStrategy(TemplateLayout(MazeTemplate.comb), GrovePriority, random = fixedSeed(1))
    val targetSet = MazeTemplate.comb(GridConfig.cols, GridConfig.rows).toSet
    var state = withResources(wood = 10_000.0, fire = 10_000.0, light = 10_000.0)
    for _ <- 1 to targetSet.size do state = strategy.maybeBuild(state, noOpponent)
    assert(state.buildings.forall(b => targetSet.contains((b.col, b.row))))
  }

  test("a TemplateLayout combination does nothing once every template cell is built, instead of building off-template") {
    val strategy = ComposedStrategy(TemplateLayout(MazeTemplate.comb), GrovePriority, random = fixedSeed(1))
    val targetSet = MazeTemplate.comb(GridConfig.cols, GridConfig.rows).toSet
    var state = withResources(wood = 10_000.0, fire = 10_000.0, light = 10_000.0)
    for _ <- 1 to targetSet.size do state = strategy.maybeBuild(state, noOpponent)
    assertEquals(state.buildingCells, targetSet)
    val afterFull = strategy.maybeBuild(state, noOpponent)
    assertEquals(afterFull, state, "once the template is fully built, further ticks are no-ops, never spilling off-template")
  }

  test("a TemplateLayout + GrovePriority combination builds row 1 completely before touching row 3") {
    val strategy = ComposedStrategy(TemplateLayout(MazeTemplate.comb), GrovePriority, random = fixedSeed(1))
    val target = MazeTemplate.comb(GridConfig.cols, GridConfig.rows)
    val row1Cells = target.count(_._2 == 1)
    var state = withResources(wood = 10_000.0)
    for _ <- 1 to row1Cells do state = strategy.maybeBuild(state, noOpponent)
    assert(state.buildings.forall(_.row == 1), "some non-row-1 cell was built before row 1 finished")
    assertEquals(state.buildings.size, row1Cells)
  }

  // ── Random tie-break ───────────────────────────────────────────────────

  test("ties are broken randomly: repeated runs with different seeds don't all pick the same cell") {
    // NoLayoutPreference + a spending policy indifferent between two equally-affordable
    // kinds at every cell ties every candidate at the same total score, so which cell
    // wins is entirely down to the random tie-break.
    val state = withResources(wood = 1_000.0, fire = 0.0, light = 0.0)
    val strategy = WeightedSpending(0.0, 0.0)
    val chosenCells = (0 until 30).map { seed =>
      val result = ComposedStrategy(NoLayoutPreference, strategy, random = fixedSeed(seed.toLong)).maybeBuild(state, noOpponent)
      val built = result.buildings.find(_.kind == BuildingKind.Grove)
      built.map(b => (b.col, b.row))
    }.flatten.toSet
    assert(chosenCells.size > 1, s"expected more than one distinct cell across 30 seeds, got $chosenCells")
  }

  test("a fixed seed is reproducible: the same seed always picks the same tied candidate") {
    val state = withResources(wood = 1_000.0, fire = 0.0, light = 0.0)
    val strategy = WeightedSpending(0.0, 0.0)
    val first = ComposedStrategy(NoLayoutPreference, strategy, random = fixedSeed(42L)).maybeBuild(state, noOpponent)
    val second = ComposedStrategy(NoLayoutPreference, strategy, random = fixedSeed(42L)).maybeBuild(state, noOpponent)
    assertEquals(first, second)
  }
