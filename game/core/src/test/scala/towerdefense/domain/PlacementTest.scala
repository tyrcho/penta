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

  test("rejects a cave without enough fire") {
    val (col, row) = emptyCell
    assertEquals(
      Placement.tryPlaceBuilding(withResources(wood = 1_000.0, fire = 0.0), BuildingKind.Cave, col, row).isLeft,
      true
    )
  }

  test("a cave costs no wood at all, so zero wood never blocks it") {
    val (col, row) = emptyCell
    assertEquals(
      Placement.tryPlaceBuilding(withResources(wood = 0.0, fire = 1_000.0), BuildingKind.Cave, col, row).isRight,
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

  test("rejects an angel without enough light") {
    val (col, row) = emptyCell
    assertEquals(
      Placement.tryPlaceBuilding(withResources(light = 0.0), BuildingKind.Angel, col, row).isLeft,
      true
    )
  }

  test("places an angel and deducts light only (no wood cost)") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.Angel, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Angel), 1)
    assertEquals(result.resources(Resource.Light), richState.resources(Resource.Light) - Balance.AngelCostLight)
    assertEquals(result.resources(Resource.Wood), richState.resources(Resource.Wood))
  }

  test("rejects a stonehenge without enough wood") {
    val (col, row) = emptyCell
    assertEquals(
      Placement.tryPlaceBuilding(withResources(wood = Balance.StonehengeCostWood - 1.0), BuildingKind.Stonehenge, col, row).isLeft,
      true
    )
  }

  test("places a stonehenge and deducts its wood cost") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.Stonehenge, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.Stonehenge), 1)
    assertEquals(result.resources(Resource.Wood), richState.resources(Resource.Wood) - Balance.StonehengeCostWood)
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

  // Locks in tryPlaceBuilding's check ordering (see its doc): affordability is checked
  // before the expensive wouldBlockPath BFS, so a candidate failing both reports
  // InsufficientResources, not WouldBlockPath — a strategy scanning every (kind, cell)
  // candidate each tick rejects most of them on cost alone, so that BFS shouldn't run
  // for a cell that was never going to be affordable regardless of its reachability.
  test("a candidate that's both unaffordable and would block the path reports InsufficientResources") {
    val corridor = for row <- 0 until GridConfig.rows yield (1, row)
    val withWall = corridor.init.foldLeft(richState) { (state, cell) =>
      Placement.tryPlaceBuilding(state, BuildingKind.Grove, cell._1, cell._2).toOption.get
    }
    val (lastCol, lastRow) = corridor.last
    val broke = withWall.copy(resources = Map.empty)
    assertEquals(
      Placement.tryPlaceBuilding(broke, BuildingKind.Grove, lastCol, lastRow),
      Left(PlacementError.InsufficientResources)
    )
  }

  test("nonBlockingCells excludes exactly the cells that would seal the only route to the goal") {
    val corridor = for row <- 0 until GridConfig.rows yield (1, row)
    val withWall = corridor.init.foldLeft(richState) { (state, cell) =>
      Placement.tryPlaceBuilding(state, BuildingKind.Grove, cell._1, cell._2).toOption.get
    }
    val (lastCol, lastRow) = corridor.last
    assertEquals(Placement.nonBlockingCells(withWall).contains((lastCol, lastRow)), false)
    assertEquals(Placement.nonBlockingCells(withWall).contains((5, 5)), true)
  }

  test("tryPlaceBuildingCached agrees with tryPlaceBuilding for both a blocking and a non-blocking cell") {
    val corridor = for row <- 0 until GridConfig.rows yield (1, row)
    val withWall = corridor.init.foldLeft(richState) { (state, cell) =>
      Placement.tryPlaceBuilding(state, BuildingKind.Grove, cell._1, cell._2).toOption.get
    }
    val (lastCol, lastRow) = corridor.last
    val nonBlocking = Placement.nonBlockingCells(withWall)
    assertEquals(
      Placement.tryPlaceBuildingCached(withWall, BuildingKind.Cave, lastCol, lastRow, nonBlocking),
      Placement.tryPlaceBuilding(withWall, BuildingKind.Cave, lastCol, lastRow)
    )
    assertEquals(
      Placement.tryPlaceBuildingCached(withWall, BuildingKind.Cave, 5, 5, nonBlocking).isRight,
      Placement.tryPlaceBuilding(withWall, BuildingKind.Cave, 5, 5).isRight
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

  test("rejects a death house without enough wood or shadow") {
    val (col, row) = emptyCell
    assertEquals(
      Placement.tryPlaceBuilding(withResources(wood = 0.0, shadow = 1_000.0), BuildingKind.DeathHouse, col, row).isLeft,
      true
    )
    assertEquals(
      Placement.tryPlaceBuilding(withResources(wood = 1_000.0, shadow = 0.0), BuildingKind.DeathHouse, col, row).isLeft,
      true
    )
  }

  test("places a death house and deducts both wood and shadow") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.DeathHouse, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.DeathHouse), 1)
    assertEquals(result.resources(Resource.Wood), richState.resources(Resource.Wood) - Balance.DeathHouseCostWood)
    assertEquals(
      result.resources(Resource.Shadow),
      richState.resources(Resource.Shadow) - Balance.DeathHouseCostShadow
    )
  }

  test("rejects a passing gate without enough shadow or light") {
    val (col, row) = emptyCell
    assertEquals(
      Placement.tryPlaceBuilding(withResources(shadow = 0.0, light = 1_000.0), BuildingKind.PassingGate, col, row).isLeft,
      true
    )
    assertEquals(
      Placement.tryPlaceBuilding(withResources(shadow = 1_000.0, light = 0.0), BuildingKind.PassingGate, col, row).isLeft,
      true
    )
  }

  test("places a passing gate and deducts both shadow and light") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceBuilding(richState, BuildingKind.PassingGate, col, row).toOption.get
    assertEquals(result.buildings.count(_.kind == BuildingKind.PassingGate), 1)
    assertEquals(
      result.resources(Resource.Shadow),
      richState.resources(Resource.Shadow) - Balance.PassingGateCostShadow
    )
    assertEquals(result.resources(Resource.Light), richState.resources(Resource.Light) - Balance.PassingGateCostLight)
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

  // ── tryResearch ──────────────────────────────────────────────────────────

  test("rejects researching a lab this maze doesn't own") {
    assertEquals(Placement.tryResearch(richState, BuildingKind.LaboNaturel), Left(PlacementError.LabNotOwned))
  }

  test("rejects researching without enough resources") {
    val poor = Placement.tryPlaceBuilding(richState, BuildingKind.LaboNaturel, 1, 1).toOption.get
      .copy(resources = Map.empty)
    assertEquals(Placement.tryResearch(poor, BuildingKind.LaboNaturel), Left(PlacementError.InsufficientResources))
  }

  test("researches level 1 of an owned lab, deducting the base cost") {
    val withLab = Placement.tryPlaceBuilding(richState, BuildingKind.LaboNaturel, 1, 1).toOption.get
    val result = Placement.tryResearch(withLab, BuildingKind.LaboNaturel).toOption.get
    assertEquals(result.researchLevels(BuildingKind.LaboNaturel), 1)
    val spec = ResearchSpecs.all(BuildingKind.LaboNaturel)
    assertEquals(
      result.resources(Resource.Wood),
      withLab.resources(Resource.Wood) - spec.baseCost(Resource.Wood)
    )
    assertEquals(
      result.resources(Resource.Crystal),
      withLab.resources(Resource.Crystal) - spec.baseCost(Resource.Crystal)
    )
  }

  test("each further research level costs double the previous level") {
    val withLab = Placement.tryPlaceBuilding(richState, BuildingKind.LaboNaturel, 1, 1).toOption.get
    val level1 = Placement.tryResearch(withLab, BuildingKind.LaboNaturel).toOption.get
    val level2 = Placement.tryResearch(level1, BuildingKind.LaboNaturel).toOption.get
    assertEquals(level2.researchLevels(BuildingKind.LaboNaturel), 2)
    val spec = ResearchSpecs.all(BuildingKind.LaboNaturel)
    assertEquals(
      level1.resources(Resource.Crystal) - level2.resources(Resource.Crystal),
      spec.baseCost(Resource.Crystal) * 2.0
    )
  }

  test("rejects researching past the max level") {
    val withLab = Placement.tryPlaceBuilding(richState, BuildingKind.LaboNaturel, 1, 1).toOption.get
    val maxed = (1 to Balance.MaxResearchLevel).foldLeft(withLab) { (state, _) =>
      Placement.tryResearch(state, BuildingKind.LaboNaturel).toOption.get
    }
    assertEquals(maxed.researchLevels(BuildingKind.LaboNaturel), Balance.MaxResearchLevel)
    assertEquals(
      Placement.tryResearch(maxed, BuildingKind.LaboNaturel),
      Left(PlacementError.MaxResearchLevelReached)
    )
  }

  test("a researched level survives losing the lab, but researching further requires owning it again") {
    val withLab = Placement.tryPlaceBuilding(richState, BuildingKind.LaboNaturel, 1, 1).toOption.get
    val researched = Placement.tryResearch(withLab, BuildingKind.LaboNaturel).toOption.get
    val demolished = Demolition.tryDestroy(researched, 1, 1).toOption.get
    assertEquals(demolished.researchLevels(BuildingKind.LaboNaturel), 1)
    assertEquals(Placement.tryResearch(demolished, BuildingKind.LaboNaturel), Left(PlacementError.LabNotOwned))
  }

  // ── Naturelles cost reduction ────────────────────────────────────────────

  test("Naturelles research reduces the cost of every other building this maze places") {
    val withLab = Placement.tryPlaceBuilding(richState, BuildingKind.LaboNaturel, 1, 1).toOption.get
    val researched = Placement.tryResearch(withLab, BuildingKind.LaboNaturel).toOption.get
    val before = researched.resources(Resource.Wood)
    val result = Placement.tryPlaceBuilding(researched, BuildingKind.Grove, 5, 5).toOption.get
    val reduction = Balance.NaturellesCostReductionByLevel.head
    assertEquals(before - result.resources(Resource.Wood), Balance.GroveCostWood * (1.0 - reduction))
  }

  test("Naturelles does not reduce another maze's building costs (only its own researchLevels apply)") {
    val withLab = Placement.tryPlaceBuilding(richState, BuildingKind.LaboNaturel, 1, 1).toOption.get
    val researched = Placement.tryResearch(withLab, BuildingKind.LaboNaturel).toOption.get
    val unresearchedOpponent = richState
    val result = Placement.tryPlaceBuilding(unresearchedOpponent, BuildingKind.Grove, 5, 5).toOption.get
    assertEquals(
      unresearchedOpponent.resources(Resource.Wood) - result.resources(Resource.Wood),
      Balance.GroveCostWood
    )
  }

  test("Naturelles does not reduce upgrade costs any differently — same effectiveCost applies") {
    val withLab = Placement.tryPlaceBuilding(richState, BuildingKind.LaboNaturel, 1, 1).toOption.get
    val researched = Placement.tryResearch(withLab, BuildingKind.LaboNaturel).toOption.get
    val withGrove = Placement.tryPlaceBuilding(researched, BuildingKind.Grove, 5, 5).toOption.get
    val before = withGrove.resources(Resource.Wood)
    val result = Placement.tryUpgradeBuilding(withGrove, 5, 5).toOption.get
    val reduction = Balance.NaturellesCostReductionByLevel.head
    assertEquals(before - result.resources(Resource.Wood), Balance.ForestUpgradeCostWood * (1.0 - reduction))
  }
