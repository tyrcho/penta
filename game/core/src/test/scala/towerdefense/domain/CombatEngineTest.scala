package towerdefense.domain

class CombatEngineTest extends munit.FunSuite:

  test("enemy reaching the goal cell costs a life and is removed") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val enemy = Enemy(1, goalPos, Balance.ElfeMaxHp, Balance.ElfeMaxHp, speedPerMs = 0.0)
    val state = MazeState.initial.copy(enemies = List(enemy))
    val (result, _) = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.lives, MazeState.initial.lives - 1)
    assertEquals(result.enemies, Nil)
  }

  test("enemy re-routes around a foret blocking its straight-line step") {
    val startPos = GridConfig.cellCenter(0, 0)
    val enemy = Enemy(1, startPos, Balance.ElfeMaxHp, Balance.ElfeMaxHp, speedPerMs = 1000.0)
    val blockingForet = Foret(100, col = 1, row = 0, elfeSpawnInMs = Balance.ElfeSpawnIntervalMs)
    val state = MazeState.initial.copy(enemies = List(enemy), forets = List(blockingForet))
    val (result, _) = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(GridConfig.cellOf(result.enemies.head.pos), (0, 1))
  }

  test("foret damages an adjacent enemy but not a distant one") {
    val foret = Foret(100, col = 5, row = 5, elfeSpawnInMs = Balance.ElfeSpawnIntervalMs)
    val adjacent = Enemy(1, GridConfig.cellCenter(6, 5), hp = 10.0, maxHp = 10.0, speedPerMs = 0.0)
    val distant = Enemy(2, GridConfig.cellCenter(0, 0), hp = 10.0, maxHp = 10.0, speedPerMs = 0.0)
    val state = MazeState.initial.copy(enemies = List(adjacent, distant), forets = List(foret))

    val (result, _) = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.enemies.map(e => e.id -> e).toMap
    assertEquals(byId(1).hp, adjacent.hp - Balance.AuraDamagePerSec)
    assertEquals(byId(2).hp, distant.hp)
  }

  test("foret aura kills an enemy once its hp is depleted") {
    val foret = Foret(100, col = 5, row = 5, elfeSpawnInMs = Balance.ElfeSpawnIntervalMs)
    val enemy = Enemy(1, GridConfig.cellCenter(6, 5), hp = Balance.AuraDamagePerSec, maxHp = Balance.AuraDamagePerSec, speedPerMs = 0.0)
    val state = MazeState.initial.copy(enemies = List(enemy), forets = List(foret))
    val (result, _) = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.enemies, Nil)
  }

  test("forets produce bois over time") {
    val foret = Foret(100, col = 5, row = 5, elfeSpawnInMs = Balance.ElfeSpawnIntervalMs)
    val state = MazeState.initial.copy(forets = List(foret), bois = 0.0)
    val (result, _) = CombatEngine.tick(state, deltaMs = 2000.0)
    assertEquals(result.bois, Balance.WoodPerSecPerForet * 2.0)
  }

  test("a foret emits exactly one elfe-spawn signal per interval") {
    val foret = Foret(100, col = 5, row = 5, elfeSpawnInMs = Balance.ElfeSpawnIntervalMs)
    val state = MazeState.initial.copy(forets = List(foret))
    val (_, spawnedBefore) = CombatEngine.tick(state, deltaMs = Balance.ElfeSpawnIntervalMs - 1.0)
    val (_, spawnedAt) = CombatEngine.tick(state, deltaMs = Balance.ElfeSpawnIntervalMs)
    assertEquals(spawnedBefore, 0)
    assertEquals(spawnedAt, 1)
  }
