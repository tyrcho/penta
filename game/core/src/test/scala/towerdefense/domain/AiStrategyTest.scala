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

  test("builds a grove when it can only afford a grove") {
    // light = 0.0 also rules out a Watchtower (wood10+light5, tied with Grove on wood).
    val state = withResources(wood = Balance.GroveCostWood, fire = 0.0, light = 0.0)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Grove), 1)
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
    assertEquals(count(result, BuildingKind.Church), 1)
    assertEquals(count(result, BuildingKind.Grove), 0)
    assertEquals(count(result, BuildingKind.Cave), 0)
  }

  test("builds a watchtower over a grove when it can afford either (tied wood cost)") {
    val state = withResources(
      wood = Balance.WatchtowerCostWood,
      fire = 0.0,
      light = Balance.WatchtowerCostLight
    )
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Watchtower), 1)
    assertEquals(count(result, BuildingKind.Grove), 0)
  }

  test("skips the spawn and goal cells when picking a spot") {
    val state = withResources(wood = Balance.GroveCostWood, light = 0.0)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    val built = result.buildings.find(_.kind == BuildingKind.Grove).get
    assertNotEquals((built.col, built.row), GridConfig.spawnCell)
    assertNotEquals((built.col, built.row), GridConfig.goalCell)
  }

  test("opponent's state does not influence Linear's decision") {
    val state = withResources(wood = Balance.GroveCostWood, fire = 0.0)
    val busyOpponent = MazeState.initial.copy(
      buildings = List(Building(1, 5, 5, BuildingKind.Grove, 0.0))
    )
    assertEquals(
      LinearStrategy.maybeBuild(state, noOpponent),
      LinearStrategy.maybeBuild(state, busyOpponent)
    )
  }

  test("maybeUpgrade does nothing when there is no upgradeable building") {
    val state = withResources(wood = 1_000.0)
    assertEquals(LinearStrategy.maybeUpgrade(state, noOpponent), state)
  }

  test("maybeUpgrade does nothing when the only Grove can't afford the Forest tier") {
    val poor = withResources(wood = Balance.GroveCostWood)
    val withGrove = Placement.tryPlaceBuilding(poor, BuildingKind.Grove, 5, 5).toOption.get
    assertEquals(LinearStrategy.maybeUpgrade(withGrove, noOpponent), withGrove)
  }

  test("maybeUpgrade upgrades the first affordable Grove into a Forest") {
    val rich = withResources(wood = 1_000.0)
    val withGrove = Placement.tryPlaceBuilding(rich, BuildingKind.Grove, 5, 5).toOption.get
    val result = LinearStrategy.maybeUpgrade(withGrove, noOpponent)
    assertEquals(count(result, BuildingKind.Forest), 1)
    assertEquals(count(result, BuildingKind.Grove), 0)
  }

  // Order measured via a full round-robin tournament (sim/runMain
  // towerdefense.sim.tournament), re-measured after fixing CompositeStrategy's missing
  // maybeUpgrade override and its Grove-vs-non-upgradeable-kind scoring gap — see
  // AiStrategy.ladder's doc for the full story, including why match count above 1 turned
  // out not to matter (the simulation has no randomness anywhere). resource-only was the
  // outright strongest until resource-maze (see below) overtook it — pure maze-weighting
  // is a weaker archetype than a fixed wall template (comb/comb-vertical) or a blend that
  // includes it (balanced).
  test("the ladder is ordered weakest to strongest by measured win rate, linear first") {
    assertEquals(
      AiStrategy.ladder.map(_._1),
      Seq(
        "linear",
        "counter-only",
        "balanced",
        "maze-only",
        "comb-vertical",
        "comb",
        "resource-only",
        "resource-maze"
      )
    )
  }

  test("all is exactly the ladder's entries, so both stay in sync") {
    assertEquals(AiStrategy.all, AiStrategy.ladder.toMap)
  }
