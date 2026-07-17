package towerdefense.domain

class PlacementTest extends munit.FunSuite:

  private val emptyCell = (5, 5)
  private val richState =
    withResources(wood = 1_000.0, fire = 1_000.0, light = 1_000.0, shadow = 1_000.0, crystal = 1_000.0)

  private def withResources(
      wood: Double = 0.0,
      fire: Double = 0.0,
      light: Double = 0.0,
      shadow: Double = 0.0,
      crystal: Double = 0.0
  ): MazeState =
    MazeState.initial.copy(
      resources = Map(
        Resource.Wood -> wood,
        Resource.Fire -> fire,
        Resource.Light -> light,
        Resource.Shadow -> shadow,
        Resource.Crystal -> crystal
      )
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

  test("rejects a tomb without enough wood or shadow") {
    val (col, row) = emptyCell
    assertEquals(
      Placement.tryPlaceBuilding(withResources(wood = 0.0, shadow = 1_000.0), BuildingKind.Tomb, col, row).isLeft,
      true
    )
    assertEquals(
      Placement.tryPlaceBuilding(withResources(wood = 1_000.0, shadow = 0.0), BuildingKind.Tomb, col, row).isLeft,
      true
    )
  }

  test("places a tomb and deducts both wood and shadow") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.Tomb, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Tomb), 1)
    assertEquals(result.resources(Resource.Wood), richState.resources(Resource.Wood) - Balance.TombCostWood)
    assertEquals(result.resources(Resource.Shadow), richState.resources(Resource.Shadow) - Balance.TombCostShadow)
  }

  test("places a black castle and deducts both wood and shadow") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.BlackCastle, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.BlackCastle), 1)
    assertEquals(
      result.resources(Resource.Wood),
      richState.resources(Resource.Wood) - Balance.BlackCastleCostWood
    )
    assertEquals(
      result.resources(Resource.Shadow),
      richState.resources(Resource.Shadow) - Balance.BlackCastleCostShadow
    )
  }

  test("a tomb and a black castle can coexist and stack freely, unlike the Science labs") {
    val withTomb = Placement.tryPlaceBuilding(richState, BuildingKind.Tomb, 3, 3).toOption.get
    val withSecondTomb = Placement.tryPlaceBuilding(withTomb, BuildingKind.Tomb, 4, 4).toOption.get
    assertEquals(withSecondTomb.buildings.count(_.kind == BuildingKind.Tomb), 2)
  }

  test("places each Science lab, each deducting its own resource mix plus crystal") {
    val naturel = Placement.tryPlaceBuilding(richState, BuildingKind.LaboNaturel, 1, 1).toOption.get
    assertEquals(
      naturel.resources(Resource.Wood),
      richState.resources(Resource.Wood) - Balance.LaboNaturelCostWood
    )
    assertEquals(
      naturel.resources(Resource.Crystal),
      richState.resources(Resource.Crystal) - Balance.LaboNaturelCostCrystal
    )

    val sombre = Placement.tryPlaceBuilding(richState, BuildingKind.LaboSombre, 1, 2).toOption.get
    assertEquals(
      sombre.resources(Resource.Shadow),
      richState.resources(Resource.Shadow) - Balance.LaboSombreCostShadow
    )

    val recherche = Placement.tryPlaceBuilding(richState, BuildingKind.LaboDeRecherche, 1, 3).toOption.get
    assertEquals(
      recherche.resources(Resource.Crystal),
      richState.resources(Resource.Crystal) - Balance.LaboDeRechercheCostCrystal
    )

    val loi = Placement.tryPlaceBuilding(richState, BuildingKind.LaboDeLaLoi, 1, 4).toOption.get
    assertEquals(loi.resources(Resource.Light), richState.resources(Resource.Light) - Balance.LaboDeLaLoiCostLight)

    val chaos = Placement.tryPlaceBuilding(richState, BuildingKind.LaboDuChaos, 1, 5).toOption.get
    assertEquals(chaos.resources(Resource.Fire), richState.resources(Resource.Fire) - Balance.LaboDuChaosCostFire)
  }

  test("rejects placing a second lab of the same kind (only one of each type)") {
    val (col, row) = emptyCell
    val withFirst = Placement.tryPlaceBuilding(richState, BuildingKind.LaboNaturel, col, row).toOption.get
    assertEquals(
      Placement.tryPlaceBuilding(withFirst, BuildingKind.LaboNaturel, 6, 6),
      Left(PlacementError.MaxCountReached)
    )
  }

  test("a different Science lab kind is unaffected by another lab kind's max count") {
    val withNaturel = Placement.tryPlaceBuilding(richState, BuildingKind.LaboNaturel, 1, 1).toOption.get
    val result = Placement.tryPlaceBuilding(withNaturel, BuildingKind.LaboSombre, 2, 2)
    assertEquals(result.isRight, true)
  }
