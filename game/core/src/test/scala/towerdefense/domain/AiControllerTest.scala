package towerdefense.domain

class AiControllerTest extends munit.FunSuite:

  test("does nothing without enough bois") {
    val state = MazeState.initial.copy(bois = 0.0)
    assertEquals(AiController.maybeBuild(state), state)
  }

  test("places a foret on the first buildable cell once it can afford one") {
    val state = MazeState.initial.copy(bois = Balance.ForetCostBois)
    val result = AiController.maybeBuild(state)
    assertEquals(result.forets.size, 1)
    assertEquals(result.bois, 0.0)
  }

  test("skips the spawn and goal cells when picking a spot") {
    val state = MazeState.initial.copy(bois = Balance.ForetCostBois)
    val result = AiController.maybeBuild(state)
    val built = result.forets.head
    assertNotEquals((built.col, built.row), GridConfig.spawnCell)
    assertNotEquals((built.col, built.row), GridConfig.goalCell)
  }
