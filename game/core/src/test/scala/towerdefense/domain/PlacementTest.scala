package towerdefense.domain

class PlacementTest extends munit.FunSuite:

  private val emptyCell = (5, 5)

  test("rejects placement on the spawn cell") {
    val (col, row) = GridConfig.spawnCell
    assertEquals(Placement.tryPlaceTower(GameState.initial, col, row).isLeft, true)
  }

  test("rejects placement on the goal cell") {
    val (col, row) = GridConfig.goalCell
    assertEquals(Placement.tryPlaceTower(GameState.initial, col, row).isLeft, true)
  }

  test("rejects placement on an already occupied cell") {
    val (col, row) = emptyCell
    val afterFirst = Placement.tryPlaceTower(GameState.initial, col, row).toOption.get
    assertEquals(Placement.tryPlaceTower(afterFirst, col, row).isLeft, true)
  }

  test("rejects placement without enough gold") {
    val (col, row) = emptyCell
    val poor = GameState.initial.copy(gold = 0)
    assertEquals(Placement.tryPlaceTower(poor, col, row).isLeft, true)
  }

  test("places a tower and deducts gold") {
    val (col, row) = emptyCell
    val result = Placement.tryPlaceTower(GameState.initial, col, row).toOption.get
    assertEquals(result.towers.size, 1)
    assertEquals(result.gold, GameState.initial.gold - Balance.TowerCost)
  }

  test("rejects placement that would seal off the only route to the goal") {
    val corridor = for row <- 0 until GridConfig.rows yield (1, row)
    val withWall = corridor.init.foldLeft(GameState.initial.copy(gold = 10_000)) { (state, cell) =>
      Placement.tryPlaceTower(state, cell._1, cell._2).toOption.get
    }
    val (lastCol, lastRow) = corridor.last
    assertEquals(Placement.tryPlaceTower(withWall, lastCol, lastRow).isLeft, true)
  }
