package towerdefense.domain

class PlacementTest extends munit.FunSuite:

  private val emptyCell = (5, 5)
  private val richState = withResources(wood = 1_000.0, fire = 1_000.0, light = 1_000.0)

  private def withResources(wood: Double = 0.0, fire: Double = 0.0, light: Double = 0.0): MazeState =
    MazeState.initial.copy(
      resources = Map(Resource.Wood -> wood, Resource.Fire -> fire, Resource.Light -> light)
    )

  test("rejects placement on the spawn cell") {
    val (col, row) = GridConfig.spawnCell
    assertEquals(Placement.tryPlaceBuilding(richState, BuildingKind.Forest, col, row).isLeft, true)
  }

  test("rejects placement on the goal cell") {
    val (col, row) = GridConfig.goalCell
    assertEquals(Placement.tryPlaceBuilding(richState, BuildingKind.Forest, col, row).isLeft, true)
  }

  test("rejects placement on an already occupied cell") {
    val (col, row) = emptyCell
    val afterFirst = Placement.tryPlaceBuilding(richState, BuildingKind.Forest, col, row).toOption.get
    assertEquals(
      Placement.tryPlaceBuilding(afterFirst, BuildingKind.Forest, col, row).isLeft,
      true
    )
  }

  test("a cave cannot be placed on a cell already occupied by a forest, and vice versa") {
    val (col, row) = emptyCell
    val withForest = Placement.tryPlaceBuilding(richState, BuildingKind.Forest, col, row).toOption.get
    assertEquals(Placement.tryPlaceBuilding(withForest, BuildingKind.Cave, col, row).isLeft, true)

    val (col2, row2) = (6, 6)
    val withCave = Placement.tryPlaceBuilding(richState, BuildingKind.Cave, col2, row2).toOption.get
    assertEquals(
      Placement.tryPlaceBuilding(withCave, BuildingKind.Forest, col2, row2).isLeft,
      true
    )
  }

  test("rejects placement without enough wood") {
    val (col, row) = emptyCell
    val poor = withResources(wood = 0.0)
    assertEquals(Placement.tryPlaceBuilding(poor, BuildingKind.Forest, col, row).isLeft, true)
  }

  test("rejects a cave without enough wood or fire") {
    val (col, row) = emptyCell
    assertEquals(
      Placement.tryPlaceBuilding(withResources(wood = 0.0, fire = 1_000.0), BuildingKind.Cave, col, row).isLeft,
      true
    )
    assertEquals(
      Placement.tryPlaceBuilding(withResources(wood = 1_000.0, fire = 0.0), BuildingKind.Cave, col, row).isLeft,
      true
    )
  }

  test("places a forest and deducts wood") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.Forest, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Forest), 1)
    assertEquals(result.resources(Resource.Wood), richState.resources(Resource.Wood) - Balance.ForestCostWood)
  }

  test("places a cave and deducts both wood and fire") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.Cave, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Cave), 1)
    assertEquals(result.resources(Resource.Wood), richState.resources(Resource.Wood) - Balance.CaveCostWood)
    assertEquals(result.resources(Resource.Fire), richState.resources(Resource.Fire) - Balance.CaveCostFire)
  }

  test("rejects a labyrinthe without enough wood or fire") {
    val (col, row) = emptyCell
    assertEquals(
      Placement
        .tryPlaceBuilding(withResources(wood = 0.0, fire = 1_000.0), BuildingKind.Labyrinth, col, row)
        .isLeft,
      true
    )
    assertEquals(
      Placement
        .tryPlaceBuilding(withResources(wood = 1_000.0, fire = 0.0), BuildingKind.Labyrinth, col, row)
        .isLeft,
      true
    )
  }

  test("places a labyrinthe and deducts both wood and fire") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.Labyrinth, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Labyrinth), 1)
    assertEquals(
      result.resources(Resource.Wood),
      richState.resources(Resource.Wood) - Balance.LabyrintheCostWood
    )
    assertEquals(
      result.resources(Resource.Fire),
      richState.resources(Resource.Fire) - Balance.LabyrintheCostFire
    )
  }

  test("rejects an eglise without enough wood or light") {
    val (col, row) = emptyCell
    assertEquals(
      Placement
        .tryPlaceBuilding(withResources(wood = 0.0, light = 1_000.0), BuildingKind.Eglise, col, row)
        .isLeft,
      true
    )
    assertEquals(
      Placement
        .tryPlaceBuilding(withResources(wood = 1_000.0, light = 0.0), BuildingKind.Eglise, col, row)
        .isLeft,
      true
    )
  }

  test("places an eglise and deducts both wood and light") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.Eglise, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Eglise), 1)
    assertEquals(result.resources(Resource.Wood), richState.resources(Resource.Wood) - Balance.EgliseCostWood)
    assertEquals(
      result.resources(Resource.Light),
      richState.resources(Resource.Light) - Balance.EgliseCostLight
    )
  }

  test("rejects a watchtower without enough wood or light") {
    val (col, row) = emptyCell
    assertEquals(
      Placement
        .tryPlaceBuilding(withResources(wood = 0.0, light = 1_000.0), BuildingKind.Watchtower, col, row)
        .isLeft,
      true
    )
    assertEquals(
      Placement
        .tryPlaceBuilding(withResources(wood = 1_000.0, light = 0.0), BuildingKind.Watchtower, col, row)
        .isLeft,
      true
    )
  }

  test("places a watchtower and deducts both wood and light") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.Watchtower, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Watchtower), 1)
    assertEquals(
      result.resources(Resource.Wood),
      richState.resources(Resource.Wood) - Balance.WatchtowerCostWood
    )
    assertEquals(
      result.resources(Resource.Light),
      richState.resources(Resource.Light) - Balance.WatchtowerCostLight
    )
  }

  test("rejects placement that would seal off the only route to the goal") {
    val corridor = for row <- 0 until GridConfig.rows yield (1, row)
    val withWall = corridor.init.foldLeft(richState) { (state, cell) =>
      Placement.tryPlaceBuilding(state, BuildingKind.Forest, cell._1, cell._2).toOption.get
    }
    val (lastCol, lastRow) = corridor.last
    assertEquals(
      Placement.tryPlaceBuilding(withWall, BuildingKind.Forest, lastCol, lastRow).isLeft,
      true
    )
  }
