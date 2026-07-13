package towerdefense.domain

class BattleEngineTest extends munit.FunSuite:

  test("a foret's Elfe arrives in the opponent's maze, not its own") {
    val foret = Foret(100, col = 5, row = 5, elfeSpawnInMs = Balance.ElfeSpawnIntervalMs)
    val battle = BattleState(
      player = MazeState.initial.copy(forets = List(foret), bois = 0.0),
      ai = MazeState.initial.copy(bois = 0.0), // no bois: AI can't build and mask the effect
    )
    val result = BattleEngine.tick(battle, deltaMs = Balance.ElfeSpawnIntervalMs)
    assertEquals(result.player.enemies, Nil)
    assertEquals(result.ai.enemies.size, 1)
    assertEquals(result.ai.enemies.head.hp, Balance.ElfeMaxHp)
  }

  test("the AI builds a foret once it can afford one") {
    val battle = BattleState.initial
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(result.ai.forets.size, 1)
  }

  test("the AI cannot build a second foret before its cooldown elapses, even with excess bois") {
    val battle = BattleState.initial.copy(ai = MazeState.initial.copy(bois = 10_000.0))
    val afterFirstBuild = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(afterFirstBuild.ai.forets.size, 1)

    val stillCoolingDown = BattleEngine.tick(afterFirstBuild, deltaMs = Balance.AiBuildCooldownMs - 1.0)
    assertEquals(stillCoolingDown.ai.forets.size, 1)

    val cooldownElapsed = BattleEngine.tick(stillCoolingDown, deltaMs = 1.0)
    assertEquals(cooldownElapsed.ai.forets.size, 2)
  }
