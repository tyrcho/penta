package towerdefense.domain

class PlacementTest extends munit.FunSuite:

  private val emptyCell = (5, 5)
  private val richState = MazeState.initial.copy(bois = 1_000.0)

  test("rejects placement on the spawn cell") {
    val (col, row) = GridConfig.spawnCell
    assertEquals(Placement.tryPlaceForet(richState, col, row).isLeft, true)
  }

  test("rejects placement on the goal cell") {
    val (col, row) = GridConfig.goalCell
    assertEquals(Placement.tryPlaceForet(richState, col, row).isLeft, true)
  }

  test("rejects placement on an already occupied cell") {
    val (col, row) = emptyCell
    val afterFirst = Placement.tryPlaceForet(richState, col, row).toOption.get
    assertEquals(Placement.tryPlaceForet(afterFirst, col, row).isLeft, true)
  }

  test("rejects placement without enough bois") {
    val (col, row) = emptyCell
    val poor = MazeState.initial.copy(bois = 0.0)
    assertEquals(Placement.tryPlaceForet(poor, col, row).isLeft, true)
  }

  test("places a foret and deducts bois") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceForet(richState, col, row).toOption.get
    assertEquals(result.forets.size, 1)
    assertEquals(result.bois, richState.bois - Balance.ForetCostBois)
  }

  test("rejects placement that would seal off the only route to the goal") {
    val corridor = for row <- 0 until GridConfig.rows yield (1, row)
    val withWall = corridor.init.foldLeft(richState) { (state, cell) =>
      Placement.tryPlaceForet(state, cell._1, cell._2).toOption.get
    }
    val (lastCol, lastRow) = corridor.last
    assertEquals(Placement.tryPlaceForet(withWall, lastCol, lastRow).isLeft, true)
  }
