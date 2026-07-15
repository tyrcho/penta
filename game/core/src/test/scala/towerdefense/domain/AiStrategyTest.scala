package towerdefense.domain

// LinearStrategy must reproduce AiController's exact historical behavior (see
// AiControllerTest, which this mirrors) now that build decisions go through the
// AiStrategy trait instead of a single hardcoded object.
class AiStrategyTest extends munit.FunSuite:

  private val noOpponent = MazeState.initial

  private def withResources(wood: Double = 0.0, fire: Double = 0.0, light: Double = 0.0): MazeState =
    MazeState.initial.copy(
      resources = Map(Resource.Wood -> wood, Resource.Fire -> fire, Resource.Light -> light)
    )

  private def count(state: MazeState, kind: BuildingKind): Int = state.buildings.count(_.kind == kind)

  test("does nothing without enough resources for either building") {
    val state = withResources(wood = 0.0, fire = 0.0)
    assertEquals(LinearStrategy.maybeBuild(state, noOpponent), state)
  }

  test("builds a forest when it can only afford a forest") {
    // light = 0.0 also rules out a Watchtower (wood10+light5, tied with Forest on wood).
    val state = withResources(wood = Balance.ForestCostWood, fire = 0.0, light = 0.0)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Forest), 1)
    assertEquals(count(result, BuildingKind.Cave), 0)
  }

  test("builds a cave when it can only afford a cave") {
    val state = withResources(wood = Balance.CaveCostWood, fire = Balance.CaveCostFire)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Cave), 1)
  }

  test("builds a labyrinthe when it can only afford a labyrinthe") {
    val state = withResources(wood = Balance.LabyrintheCostWood, fire = Balance.LabyrintheCostFire)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Labyrinth), 1)
    assertEquals(count(result, BuildingKind.Forest), 0)
    assertEquals(count(result, BuildingKind.Cave), 0)
  }

  test("builds an eglise over any cheaper building once it can afford one") {
    val state = withResources(wood = Balance.EgliseCostWood, light = Balance.EgliseCostLight)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Eglise), 1)
    assertEquals(count(result, BuildingKind.Labyrinth), 0)
    assertEquals(count(result, BuildingKind.Forest), 0)
    assertEquals(count(result, BuildingKind.Cave), 0)
  }

  test("builds a watchtower over a forest when it can afford either (tied wood cost)") {
    val state = withResources(
      wood = Balance.WatchtowerCostWood,
      fire = 0.0,
      light = Balance.WatchtowerCostLight
    )
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Watchtower), 1)
    assertEquals(count(result, BuildingKind.Forest), 0)
  }

  test("skips the spawn and goal cells when picking a spot") {
    val state = withResources(wood = Balance.ForestCostWood, light = 0.0)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    val built = result.buildings.find(_.kind == BuildingKind.Forest).get
    assertNotEquals((built.col, built.row), GridConfig.spawnCell)
    assertNotEquals((built.col, built.row), GridConfig.goalCell)
  }

  test("opponent's state does not influence Linear's decision") {
    val state = withResources(wood = Balance.ForestCostWood, fire = 0.0)
    val busyOpponent = MazeState.initial.copy(
      buildings = List(Building(1, 5, 5, BuildingKind.Forest, 0.0))
    )
    assertEquals(
      LinearStrategy.maybeBuild(state, noOpponent),
      LinearStrategy.maybeBuild(state, busyOpponent)
    )
  }

  // Order measured via headless AI-vs-AI simulation (sim/run round-robin), re-measured
  // after CompositeStrategy's maze component started weighting aura-damage exposure
  // (dangerScore): linear loses to every other tier; counter-only and resource-only both
  // lose outright to maze-only (which routes the enemy path past Forests to kill units,
  // not just delay them); maze-only only stalemates balanced. The browser's difficulty
  // selector and auto-advance-on-win both walk this exact sequence.
  test("the ladder is ordered weakest to strongest by measured win rate, linear first") {
    assertEquals(
      AiStrategy.ladder.map(_._1),
      Seq("linear", "counter-only", "resource-only", "balanced", "maze-only")
    )
  }

  test("all is exactly the ladder's entries, so both stay in sync") {
    assertEquals(AiStrategy.all, AiStrategy.ladder.toMap)
  }
