package towerdefense.domain

import towerdefense.domain.geometry.Vec2

object CombatEngine:

  def tick(state: GameState, deltaMs: Double): GameState =
    val s1 = spawnEnemies(state, deltaMs)
    val s2 = moveEnemies(s1, deltaMs)
    val s3 = updateTowers(s2, deltaMs)
    updateProjectiles(s3, deltaMs)

  private def spawnEnemies(state: GameState, deltaMs: Double): GameState =
    val elapsed = state.elapsedMs + deltaMs
    if elapsed < state.nextSpawnAtMs then state.copy(elapsedMs = elapsed)
    else
      val spawnPos = GridConfig.cellCenter(GridConfig.spawnCell._1, GridConfig.spawnCell._2)
      val enemy = Enemy(state.nextId, spawnPos, Balance.EnemyMaxHp, Balance.EnemyMaxHp, Balance.EnemySpeedPerMs)
      state.copy(
        enemies = enemy :: state.enemies,
        elapsedMs = elapsed,
        nextSpawnAtMs = elapsed + Balance.SpawnIntervalMs,
        nextId = state.nextId + 1,
      )

  // Re-pathfinds every enemy from its current cell to the goal each tick, avoiding
  // tower cells — no cached path to invalidate when a new tower changes the maze.
  private def moveEnemies(state: GameState, deltaMs: Double): GameState =
    val blocked = state.towers.map(t => (t.col, t.row)).toSet
    val (remaining, reachedBase) = state.enemies.map(stepEnemy(_, blocked, deltaMs)).partitionMap(identity)
    state.copy(enemies = remaining, lives = state.lives - reachedBase.size)

  private def stepEnemy(enemy: Enemy, blocked: Set[(Int, Int)], deltaMs: Double): Either[Enemy, Enemy] =
    val currentCell = GridConfig.cellOf(enemy.pos)
    if currentCell == GridConfig.goalCell then Right(enemy)
    else
      Pathfinding.shortestPath(currentCell, GridConfig.goalCell, blocked) match
        case None            => Left(enemy) // no route right now (shouldn't happen, placement guards this)
        case Some(path)      => Left(advanceTowards(enemy, path, deltaMs))

  private def advanceTowards(enemy: Enemy, path: List[(Int, Int)], deltaMs: Double): Enemy =
    val nextCell = if path.size > 1 then path(1) else path.head
    val target = GridConfig.cellCenter(nextCell._1, nextCell._2)
    enemy.copy(pos = moveToward(enemy.pos, target, enemy.speedPerMs * deltaMs))

  private def moveToward(pos: Vec2, target: Vec2, maxDist: Double): Vec2 =
    val delta = target - pos
    if delta.length <= maxDist then target else pos + delta.normalized * maxDist

  private case class TowerTickAcc(towers: List[Tower], projectiles: List[Projectile], nextId: Long)

  private def updateTowers(state: GameState, deltaMs: Double): GameState =
    val acc = state.towers.foldLeft(TowerTickAcc(Nil, Nil, state.nextId)) { (acc, tower) =>
      val cooled = tower.copy(reloadMs = (tower.reloadMs - deltaMs).max(0))
      fireIfReady(cooled, state.enemies, acc)
    }
    state.copy(towers = acc.towers, projectiles = acc.projectiles ++ state.projectiles, nextId = acc.nextId)

  private def fireIfReady(tower: Tower, enemies: List[Enemy], acc: TowerTickAcc): TowerTickAcc =
    if tower.reloadMs > 0 then TowerTickAcc(tower :: acc.towers, acc.projectiles, acc.nextId)
    else
      findTarget(tower, enemies) match
        case None => TowerTickAcc(tower :: acc.towers, acc.projectiles, acc.nextId)
        case Some(target) =>
          val projectile = Projectile(acc.nextId, target.id, GridConfig.cellCenter(tower.col, tower.row), Balance.ProjectileSpeedPerMs, tower.damage)
          val reloaded = tower.copy(reloadMs = tower.cooldownMs)
          TowerTickAcc(reloaded :: acc.towers, projectile :: acc.projectiles, acc.nextId + 1)

  private def findTarget(tower: Tower, enemies: List[Enemy]): Option[Enemy] =
    val towerPos = GridConfig.cellCenter(tower.col, tower.row)
    enemies
      .filter(e => towerPos.distanceTo(e.pos) <= tower.rangePx)
      .minByOption(e => towerPos.distanceTo(e.pos))

  private case class ProjectileTickAcc(projectiles: List[Projectile], damageByEnemy: Map[Long, Double])

  private def updateProjectiles(state: GameState, deltaMs: Double): GameState =
    val enemyById = state.enemies.map(e => e.id -> e).toMap
    val acc = state.projectiles.foldLeft(ProjectileTickAcc(Nil, Map.empty)) { (acc, p) =>
      enemyById.get(p.targetId) match
        case None         => acc // target already dead/gone: projectile vanishes
        case Some(target) => stepProjectile(p, target, deltaMs, acc)
    }
    applyDamage(state, acc)

  private def stepProjectile(p: Projectile, target: Enemy, deltaMs: Double, acc: ProjectileTickAcc): ProjectileTickAcc =
    val moveDist = p.speedPerMs * deltaMs
    if p.pos.distanceTo(target.pos) <= moveDist then
      acc.copy(damageByEnemy = acc.damageByEnemy.updated(p.targetId, acc.damageByEnemy.getOrElse(p.targetId, 0.0) + p.damage))
    else
      val dir = (target.pos - p.pos).normalized
      acc.copy(projectiles = p.copy(pos = p.pos + dir * moveDist) :: acc.projectiles)

  private def applyDamage(state: GameState, acc: ProjectileTickAcc): GameState =
    val damaged = state.enemies.map(e => e.copy(hp = e.hp - acc.damageByEnemy.getOrElse(e.id, 0.0)))
    val (dead, alive) = damaged.partition(_.hp <= 0)
    state.copy(enemies = alive, projectiles = acc.projectiles, gold = state.gold + dead.size * Balance.EnemyReward)
