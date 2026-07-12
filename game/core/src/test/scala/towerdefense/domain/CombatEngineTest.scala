package towerdefense.domain

class CombatEngineTest extends munit.FunSuite:

  test("spawns an enemy once the spawn interval elapses") {
    val result = CombatEngine.tick(GameState.initial, Balance.SpawnIntervalMs)
    assertEquals(result.enemies.size, 1)
  }

  test("does not spawn before the spawn interval elapses") {
    val result = CombatEngine.tick(GameState.initial, Balance.SpawnIntervalMs / 2)
    assertEquals(result.enemies.size, 0)
  }

  test("enemy reaching the goal cell costs a life and is removed") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val enemy = Enemy(1, goalPos, Balance.EnemyMaxHp, Balance.EnemyMaxHp, speedPerMs = 0.0)
    val state = GameState.initial.copy(enemies = List(enemy), nextSpawnAtMs = Double.MaxValue)
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.lives, GameState.initial.lives - 1)
    assertEquals(result.enemies, Nil)
  }

  test("enemy re-routes around a tower blocking its straight-line step") {
    val startPos = GridConfig.cellCenter(0, 0)
    val enemy = Enemy(1, startPos, Balance.EnemyMaxHp, Balance.EnemyMaxHp, speedPerMs = 1000.0)
    val blockingTower = Tower(100, col = 1, row = 0, Balance.TowerRangePx, 0, Balance.TowerCooldownMs, reloadMs = Double.MaxValue)
    val state = GameState.initial.copy(enemies = List(enemy), towers = List(blockingTower), nextSpawnAtMs = Double.MaxValue)
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(GridConfig.cellOf(result.enemies.head.pos), (0, 1))
  }

  test("tower does not fire at an enemy out of range") {
    val enemy = Enemy(1, GridConfig.cellCenter(0, 0), hp = 20.0, maxHp = 20.0, speedPerMs = 0.0)
    val farTower = Tower(100, col = GridConfig.cols - 1, row = GridConfig.rows - 1, Balance.TowerRangePx, Balance.TowerDamage, Balance.TowerCooldownMs, reloadMs = 0)
    val state = GameState.initial.copy(enemies = List(enemy), towers = List(farTower), nextSpawnAtMs = Double.MaxValue)
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.projectiles, Nil)
  }

  test("tower kills an in-range enemy and grants gold") {
    val enemy = Enemy(1, GridConfig.cellCenter(0, 0), hp = Balance.TowerDamage, maxHp = Balance.TowerDamage, speedPerMs = 0.0)
    val tower = Tower(100, col = 0, row = 1, Balance.TowerRangePx, Balance.TowerDamage, Balance.TowerCooldownMs, reloadMs = 0)
    val state = GameState.initial.copy(enemies = List(enemy), towers = List(tower), nextSpawnAtMs = Double.MaxValue)

    val afterShot = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(afterShot.projectiles.size, 1)
    assertEquals(afterShot.enemies.head.hp, enemy.hp) // projectile hasn't reached the target yet

    val afterHit = CombatEngine.tick(afterShot, deltaMs = 1000.0)
    assertEquals(afterHit.enemies, Nil)
    assertEquals(afterHit.gold, GameState.initial.gold + Balance.EnemyReward)
  }
