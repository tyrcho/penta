package towerdefense.domain

class AiControllerTest extends munit.FunSuite:

  test("does nothing without enough resources for either building") {
    val state = MazeState.initial.copy(wood = 0.0, fire = 0.0)
    assertEquals(AiController.maybeBuild(state), state)
  }

  test("builds a forest when it can only afford a forest") {
    val state = MazeState.initial.copy(wood = Balance.ForestCostWood, fire = 0.0)
    val result = AiController.maybeBuild(state)
    assertEquals(result.forests.size, 1)
    assertEquals(result.caves.size, 0)
  }

  test("builds a cave when it can only afford a cave") {
    val state = MazeState.initial.copy(wood = Balance.CaveCostWood, fire = Balance.CaveCostFire)
    val result = AiController.maybeBuild(state)
    assertEquals(result.caves.size, 1)
  }

  test("skips the spawn and goal cells when picking a spot") {
    val state = MazeState.initial.copy(wood = Balance.ForestCostWood)
    val result = AiController.maybeBuild(state)
    val built = result.forests.head
    assertNotEquals((built.col, built.row), GridConfig.spawnCell)
    assertNotEquals((built.col, built.row), GridConfig.goalCell)
  }
