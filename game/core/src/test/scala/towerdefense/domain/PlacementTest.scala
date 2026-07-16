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
    assertEquals(Placement.tryPlaceBuilding(richState, BuildingKind.Grove, col, row).isLeft, true)
  }

  test("rejects placement on the goal cell") {
    val (col, row) = GridConfig.goalCell
    assertEquals(Placement.tryPlaceBuilding(richState, BuildingKind.Grove, col, row).isLeft, true)
  }

  test("rejects placement on an already occupied cell") {
    val (col, row) = emptyCell
    val afterFirst = Placement.tryPlaceBuilding(richState, BuildingKind.Grove, col, row).toOption.get
    assertEquals(
      Placement.tryPlaceBuilding(afterFirst, BuildingKind.Grove, col, row).isLeft,
      true
    )
  }

  test("a cave cannot be placed on a cell already occupied by a grove, and vice versa") {
    val (col, row) = emptyCell
    val withGrove = Placement.tryPlaceBuilding(richState, BuildingKind.Grove, col, row).toOption.get
    assertEquals(Placement.tryPlaceBuilding(withGrove, BuildingKind.Cave, col, row).isLeft, true)

    val (col2, row2) = (6, 6)
    val withCave = Placement.tryPlaceBuilding(richState, BuildingKind.Cave, col2, row2).toOption.get
    assertEquals(
      Placement.tryPlaceBuilding(withCave, BuildingKind.Grove, col2, row2).isLeft,
      true
    )
  }

  test("rejects placement without enough wood") {
    val (col, row) = emptyCell
    val poor = withResources(wood = 0.0)
    assertEquals(Placement.tryPlaceBuilding(poor, BuildingKind.Grove, col, row).isLeft, true)
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

  test("places a grove and deducts wood") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.Grove, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Grove), 1)
    assertEquals(result.resources(Resource.Wood), richState.resources(Resource.Wood) - Balance.GroveCostWood)
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
        .tryPlaceBuilding(withResources(wood = 0.0, light = 1_000.0), BuildingKind.Church, col, row)
        .isLeft,
      true
    )
    assertEquals(
      Placement
        .tryPlaceBuilding(withResources(wood = 1_000.0, light = 0.0), BuildingKind.Church, col, row)
        .isLeft,
      true
    )
  }

  test("places an eglise and deducts both wood and light") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.Church, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Church), 1)
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
      Placement.tryPlaceBuilding(state, BuildingKind.Grove, cell._1, cell._2).toOption.get
    }
    val (lastCol, lastRow) = corridor.last
    assertEquals(
      Placement.tryPlaceBuilding(withWall, BuildingKind.Grove, lastCol, lastRow).isLeft,
      true
    )
  }

  test("rejects upgrading a cell with no building") {
    assertEquals(Placement.tryUpgradeBuilding(richState, 5, 5).isLeft, true)
  }

  test("rejects upgrading a building with no further tier (e.g. a Cave)") {
    val withCave = Placement.tryPlaceBuilding(richState, BuildingKind.Cave, 5, 5).toOption.get
    assertEquals(Placement.tryUpgradeBuilding(withCave, 5, 5).isLeft, true)
  }

  test("rejects upgrading a Grove without enough wood for the Forest tier") {
    val poor = withResources(wood = Balance.GroveCostWood)
    val withGrove = Placement.tryPlaceBuilding(poor, BuildingKind.Grove, 5, 5).toOption.get
    assertEquals(Placement.tryUpgradeBuilding(withGrove, 5, 5).isLeft, true)
  }

  test("upgrades a Grove into a Forest in place, deducting wood and keeping the same cell/id") {
    val withGrove = Placement.tryPlaceBuilding(richState, BuildingKind.Grove, 5, 5).toOption.get
    val before = withGrove.buildings.head
    val result = Placement.tryUpgradeBuilding(withGrove, 5, 5).toOption.get
    val after = result.buildings.head
    assertEquals(after.id, before.id)
    assertEquals((after.col, after.row), (before.col, before.row))
    assertEquals(after.kind, BuildingKind.Forest)
    assertEquals(
      result.resources(Resource.Wood),
      withGrove.resources(Resource.Wood) - Balance.ForestUpgradeCostWood
    )
  }

  test("upgrades a Forest into a Jungle in place") {
    val rich = withResources(wood = 1_000.0)
    val withGrove = Placement.tryPlaceBuilding(rich, BuildingKind.Grove, 5, 5).toOption.get
    val withForest = Placement.tryUpgradeBuilding(withGrove, 5, 5).toOption.get
    val result = Placement.tryUpgradeBuilding(withForest, 5, 5).toOption.get
    assertEquals(result.buildings.head.kind, BuildingKind.Jungle)
  }

  test("rejects upgrading a Jungle further (top tier)") {
    val rich = withResources(wood = 1_000.0)
    val withGrove = Placement.tryPlaceBuilding(rich, BuildingKind.Grove, 5, 5).toOption.get
    val withForest = Placement.tryUpgradeBuilding(withGrove, 5, 5).toOption.get
    val withJungle = Placement.tryUpgradeBuilding(withForest, 5, 5).toOption.get
    assertEquals(Placement.tryUpgradeBuilding(withJungle, 5, 5).isLeft, true)
  }

  test("Forest and Jungle cannot be placed directly, only reached via upgrade") {
    assertEquals(
      Placement.tryPlaceBuilding(richState, BuildingKind.Forest, 5, 5),
      Left(PlacementError.CannotBuildDirectly)
    )
    assertEquals(
      Placement.tryPlaceBuilding(richState, BuildingKind.Jungle, 5, 5),
      Left(PlacementError.CannotBuildDirectly)
    )
  }
