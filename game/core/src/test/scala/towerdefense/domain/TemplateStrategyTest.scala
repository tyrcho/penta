package towerdefense.domain

class TemplateStrategyTest extends munit.FunSuite:

  private def withResources(wood: Double = 0.0, fire: Double = 0.0, light: Double = 0.0): MazeState =
    MazeState.initial.copy(
      resources = Map(Resource.Wood -> wood, Resource.Fire -> fire, Resource.Light -> light)
    )

  private val strategy = TemplateStrategy(MazeTemplate.comb)
  private val target = MazeTemplate.comb(GridConfig.cols, GridConfig.rows)
  private val targetSet = target.toSet

  test("places exactly one building, on a template cell") {
    val state = withResources(wood = 100.0)
    val result = strategy.maybeBuild(state, MazeState.initial)
    assertEquals(result.buildings.size, 1)
    val built = result.buildings.head
    assert(targetSet.contains((built.col, built.row)), s"(${built.col},${built.row}) is not a comb cell")
  }

  test("prefers Grove over any other affordable kind, so maybeUpgrade can later grow an aura-dealing Forest") {
    // Grove is Nature's only directly-buildable tier (Forest/Jungle are upgrade-only —
    // see TemplateStrategy's doc) — with every currency abundant, Church would win a
    // pure cost-order tie-break, but Grove must be picked instead.
    val state = withResources(wood = 100.0, fire = 100.0, light = 100.0)
    val result = strategy.maybeBuild(state, MazeState.initial)
    assertEquals(result.buildings.map(_.kind), List(BuildingKind.Grove))
  }

  test("with only wood available, the whole template still completes using Grove") {
    var state = withResources(wood = 10_000.0, fire = 0.0, light = 0.0)
    for _ <- 1 to target.size do state = strategy.maybeBuild(state, MazeState.initial)
    assertEquals(state.buildingCells, targetSet)
    assert(state.buildings.forall(_.kind == BuildingKind.Grove))
  }

  test("maybeUpgrade grows a template-cell Grove into an aura-dealing Forest") {
    val (col, row) = target.head
    val withGrove = Placement.tryPlaceBuilding(withResources(wood = 1_000.0), BuildingKind.Grove, col, row)
      .toOption
      .get
    val result = strategy.maybeUpgrade(withGrove, MazeState.initial)
    assertEquals(result.buildings.map(_.kind), List(BuildingKind.Forest))
  }

  test("maybeUpgrade does nothing when there is no upgradeable building yet") {
    val state = withResources(wood = 1_000.0)
    assertEquals(strategy.maybeUpgrade(state, MazeState.initial), state)
  }

  test("does nothing without enough resources for any building") {
    val state = withResources(wood = 0.0, fire = 0.0, light = 0.0)
    assertEquals(strategy.maybeBuild(state, MazeState.initial), state)
  }

  test("never builds a cell outside the template") {
    var state = withResources(wood = 10_000.0, fire = 10_000.0, light = 10_000.0)
    for _ <- 1 to target.size do state = strategy.maybeBuild(state, MazeState.initial)
    assert(state.buildings.forall(b => targetSet.contains((b.col, b.row))))
  }

  test("repeated ticks converge on building the entire template, then stop") {
    var state = withResources(wood = 10_000.0, fire = 10_000.0, light = 10_000.0)
    for _ <- 1 to target.size do state = strategy.maybeBuild(state, MazeState.initial)
    assertEquals(state.buildingCells, targetSet)

    val afterFull = strategy.maybeBuild(state, MazeState.initial)
    assertEquals(afterFull, state, "once the template is fully built, further ticks are no-ops")
  }

  test("stays reachable at every step while converging on the template") {
    var state = withResources(wood = 10_000.0, fire = 10_000.0, light = 10_000.0)
    for _ <- 1 to target.size do
      state = strategy.maybeBuild(state, MazeState.initial)
      assert(Pathfinding.isReachable(GridConfig.spawnCell, GridConfig.goalCell, state.buildingCells))
  }

  test("works for the vertical template too") {
    val vertical = TemplateStrategy(MazeTemplate.combVertical)
    val verticalTarget = MazeTemplate.combVertical(GridConfig.cols, GridConfig.rows).toSet
    var state = withResources(wood = 10_000.0, fire = 10_000.0, light = 10_000.0)
    for _ <- 1 to verticalTarget.size do state = vertical.maybeBuild(state, MazeState.initial)
    assertEquals(state.buildingCells, verticalTarget)
  }

  // The bug this pins: an earlier version sorted the template's cells as plain (col, row)
  // tuples, which sorts by column first — that scatters builds across all 5 tooth rows at
  // once instead of finishing row 1 before starting row 3, leaving every row simultaneously
  // unfinished (and therefore, per MazeTemplate's doc, simultaneously useless) for far
  // longer than completing one row at a time would. Caught via `make sim`: comb lost 40-0
  // to maze-only before this fix.
  test("builds row 1 (the first tooth) completely before touching row 3") {
    val row1Cells = target.count(_._2 == 1)
    var state = withResources(wood = 10_000.0)
    for _ <- 1 to row1Cells do state = strategy.maybeBuild(state, MazeState.initial)
    assert(state.buildings.forall(_.row == 1), "some non-row-1 cell was built before row 1 finished")
    assertEquals(state.buildings.size, row1Cells)
  }
