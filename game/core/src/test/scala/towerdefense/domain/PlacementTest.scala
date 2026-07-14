package towerdefense.domain

class PlacementTest extends munit.FunSuite:

  private val emptyCell = (5, 5)
  private val richState = MazeState.initial.copy(wood = 1_000.0, fire = 1_000.0)

  test("rejects placement on the spawn cell") {
    val (col, row) = GridConfig.spawnCell
    assertEquals(Placement.tryPlaceForest(richState, col, row).isLeft, true)
  }

  test("rejects placement on the goal cell") {
    val (col, row) = GridConfig.goalCell
    assertEquals(Placement.tryPlaceForest(richState, col, row).isLeft, true)
  }

  test("rejects placement on an already occupied cell") {
    val (col, row) = emptyCell
    val afterFirst = Placement.tryPlaceForest(richState, col, row).toOption.get
    assertEquals(Placement.tryPlaceForest(afterFirst, col, row).isLeft, true)
  }

  test("a cave cannot be placed on a cell already occupied by a forest, and vice versa") {
    val (col, row) = emptyCell
    val withForest = Placement.tryPlaceForest(richState, col, row).toOption.get
    assertEquals(Placement.tryPlaceCave(withForest, col, row).isLeft, true)

    val (col2, row2) = (6, 6)
    val withCave = Placement.tryPlaceCave(richState, col2, row2).toOption.get
    assertEquals(Placement.tryPlaceForest(withCave, col2, row2).isLeft, true)
  }

  test("rejects placement without enough wood") {
    val (col, row) = emptyCell
    val poor = MazeState.initial.copy(wood = 0.0)
    assertEquals(Placement.tryPlaceForest(poor, col, row).isLeft, true)
  }

  test("rejects a cave without enough wood or fire") {
    val (col, row) = emptyCell
    assertEquals(
      Placement.tryPlaceCave(MazeState.initial.copy(wood = 0.0, fire = 1_000.0), col, row).isLeft,
      true
    )
    assertEquals(
      Placement.tryPlaceCave(MazeState.initial.copy(wood = 1_000.0, fire = 0.0), col, row).isLeft,
      true
    )
  }

  test("places a forest and deducts wood") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceForest(richState, col, row).toOption.get
    assertEquals(result.forests.size, 1)
    assertEquals(result.wood, richState.wood - Balance.ForestCostWood)
  }

  test("places a cave and deducts both wood and fire") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceCave(richState, col, row).toOption.get
    assertEquals(result.caves.size, 1)
    assertEquals(result.wood, richState.wood - Balance.CaveCostWood)
    assertEquals(result.fire, richState.fire - Balance.CaveCostFire)
  }

  test("rejects placement that would seal off the only route to the goal") {
    val corridor = for row <- 0 until GridConfig.rows yield (1, row)
    val withWall = corridor.init.foldLeft(richState) { (state, cell) =>
      Placement.tryPlaceForest(state, cell._1, cell._2).toOption.get
    }
    val (lastCol, lastRow) = corridor.last
    assertEquals(Placement.tryPlaceForest(withWall, lastCol, lastRow).isLeft, true)
  }
