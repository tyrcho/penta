package towerdefense.domain

class DemolitionTest extends munit.FunSuite:

  test("rejects destroying an empty cell") {
    assertEquals(Demolition.tryDestroy(MazeState.initial, 5, 5).isLeft, true)
  }

  test("destroys a grove and refunds half its wood cost") {
    val (col, row) = (5, 5)
    val withGrove =
      Placement.tryPlaceBuilding(MazeState.initial, BuildingKind.Grove, col, row).toOption.get
    val result = Demolition.tryDestroy(withGrove, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Grove), 0)
    assertEquals(
      result.resources(Resource.Wood),
      withGrove.resources(Resource.Wood) + Balance.GroveCostWood * Balance.DemolishRefundFraction
    )
  }

  test("destroys a cave and refunds half its wood and fire cost") {
    val (col, row) = (5, 5)
    val withCave = Placement.tryPlaceBuilding(MazeState.initial, BuildingKind.Cave, col, row).toOption.get
    val result = Demolition.tryDestroy(withCave, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Cave), 0)
    assertEquals(
      result.resources(Resource.Wood),
      withCave.resources(Resource.Wood) + Balance.CaveCostWood * Balance.DemolishRefundFraction
    )
    assertEquals(
      result.resources(Resource.Fire),
      withCave.resources(Resource.Fire) + Balance.CaveCostFire * Balance.DemolishRefundFraction
    )
  }

  test("destroys a labyrinthe and refunds half its wood and fire cost") {
    val (col, row) = (5, 5)
    val rich = MazeState.initial.copy(resources = Map(Resource.Wood -> 1_000.0, Resource.Fire -> 1_000.0))
    val withLabyrinthe = Placement.tryPlaceBuilding(rich, BuildingKind.Labyrinth, col, row).toOption.get
    val result = Demolition.tryDestroy(withLabyrinthe, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Labyrinth), 0)
    assertEquals(
      result.resources(Resource.Wood),
      withLabyrinthe.resources(Resource.Wood) + Balance.LabyrintheCostWood * Balance.DemolishRefundFraction
    )
    assertEquals(
      result.resources(Resource.Fire),
      withLabyrinthe.resources(Resource.Fire) + Balance.LabyrintheCostFire * Balance.DemolishRefundFraction
    )
  }

  test("destroys an eglise and refunds half its wood and light cost") {
    val (col, row) = (5, 5)
    val rich = MazeState.initial.copy(resources = Map(Resource.Wood -> 1_000.0, Resource.Light -> 1_000.0))
    val withEglise =
      Placement.tryPlaceBuilding(rich, BuildingKind.Church, col, row).toOption.get
    val result = Demolition.tryDestroy(withEglise, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Church), 0)
    assertEquals(
      result.resources(Resource.Wood),
      withEglise.resources(Resource.Wood) + Balance.EgliseCostWood * Balance.DemolishRefundFraction
    )
    assertEquals(
      result.resources(Resource.Light),
      withEglise.resources(Resource.Light) + Balance.EgliseCostLight * Balance.DemolishRefundFraction
    )
  }

  test("destroys a watchtower and refunds half its wood and light cost") {
    val (col, row) = (5, 5)
    val withWatchtower =
      Placement.tryPlaceBuilding(MazeState.initial, BuildingKind.Watchtower, col, row).toOption.get
    val result = Demolition.tryDestroy(withWatchtower, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Watchtower), 0)
    assertEquals(
      result.resources(Resource.Wood),
      withWatchtower.resources(Resource.Wood) + Balance.WatchtowerCostWood * Balance.DemolishRefundFraction
    )
    assertEquals(
      result.resources(Resource.Light),
      withWatchtower.resources(Resource.Light) + Balance.WatchtowerCostLight * Balance.DemolishRefundFraction
    )
  }

  test("destroys a tomb and refunds half its wood and shadow cost") {
    val (col, row) = (5, 5)
    val withTomb = Placement.tryPlaceBuilding(MazeState.initial, BuildingKind.Tomb, col, row).toOption.get
    val result = Demolition.tryDestroy(withTomb, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Tomb), 0)
    assertEquals(
      result.resources(Resource.Wood),
      withTomb.resources(Resource.Wood) + Balance.TombCostWood * Balance.DemolishRefundFraction
    )
    assertEquals(
      result.resources(Resource.Shadow),
      withTomb.resources(Resource.Shadow) + Balance.TombCostShadow * Balance.DemolishRefundFraction
    )
  }

  test("destroys a Science lab and refunds half its upgrade cost, freeing up the max-one-per-kind slot") {
    val (col, row) = (5, 5)
    val rich = MazeState.initial.copy(resources = Map(Resource.Wood -> 1_000.0, Resource.Crystal -> 1_000.0))
    val withBase = Placement.tryPlaceBuilding(rich, BuildingKind.LaboFondamental, col, row).toOption.get
    val withLab = Placement.tryUpgradeBuilding(withBase, col, row, Some(BuildingKind.LaboNaturel)).toOption.get
    val result = Demolition.tryDestroy(withLab, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.LaboNaturel), 0)
    assertEquals(
      result.resources(Resource.Crystal),
      withLab.resources(Resource.Crystal) + Balance.LaboNaturelCostCrystal * Balance.DemolishRefundFraction
    )
    // Rebuilding after demolishing is allowed — maxPerMaze counts current buildings only.
    val withNewBase = Placement.tryPlaceBuilding(result, BuildingKind.LaboFondamental, col, row).toOption.get
    assertEquals(
      Placement.tryUpgradeBuilding(withNewBase, col, row, Some(BuildingKind.LaboNaturel)).isRight,
      true
    )
  }

  test("destroying the only wall of a corridor is allowed (removing an obstacle can't seal a path)") {
    val corridor = for row <- 0 until GridConfig.rows yield (1, row)
    val rich = MazeState.initial.copy(resources = Map(Resource.Wood -> 1_000.0))
    val withWall = corridor.foldLeft(rich) { (state, cell) =>
      Placement.tryPlaceBuilding(state, BuildingKind.Grove, cell._1, cell._2).toOption.getOrElse(state)
    }
    val (someCol, someRow) = corridor.head
    assertEquals(Demolition.tryDestroy(withWall, someCol, someRow).isRight, true)
  }
