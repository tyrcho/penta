package towerdefense.domain

import towerdefense.domain.geometry.Vec2

object CombatEngine:

  // Advances one maze by one tick. Returns the new state plus how many Elfe
  // this maze's Forets launched — the caller (BattleEngine) delivers those
  // into the *opponent's* maze.
  def tick(state: MazeState, deltaMs: Double): (MazeState, Int) =
    val s1 = moveEnemies(state, deltaMs)
    val s2 = applyForetAuras(s1, deltaMs)
    val s3 = produceBois(s2, deltaMs)
    advanceForetTimers(s3, deltaMs)

  // Re-pathfinds every enemy from its current cell to the goal each tick, avoiding
  // Foret cells — no cached path to invalidate when a new Foret changes the maze.
  private def moveEnemies(state: MazeState, deltaMs: Double): MazeState =
    val blocked = state.forets.map(f => (f.col, f.row)).toSet
    val (remaining, reachedBase) = state.enemies.map(stepEnemy(_, blocked, deltaMs)).partitionMap(identity)
    state.copy(enemies = remaining, lives = state.lives - reachedBase.size)

  private def stepEnemy(enemy: Enemy, blocked: Set[(Int, Int)], deltaMs: Double): Either[Enemy, Enemy] =
    val currentCell = GridConfig.cellOf(enemy.pos)
    if currentCell == GridConfig.goalCell then Right(enemy)
    else
      Pathfinding.shortestPath(currentCell, GridConfig.goalCell, blocked) match
        case None       => Left(enemy) // no route right now (shouldn't happen, placement guards this)
        case Some(path) => Left(advanceTowards(enemy, path, deltaMs))

  private def advanceTowards(enemy: Enemy, path: List[(Int, Int)], deltaMs: Double): Enemy =
    val nextCell = if path.size > 1 then path(1) else path.head
    val target = GridConfig.cellCenter(nextCell._1, nextCell._2)
    enemy.copy(pos = moveToward(enemy.pos, target, enemy.speedPerMs * deltaMs))

  private def moveToward(pos: Vec2, target: Vec2, maxDist: Double): Vec2 =
    val delta = target - pos
    if delta.length <= maxDist then target else pos + delta.normalized * maxDist

  // Forets deal passive damage-over-time to any enemy standing on an adjacent cell
  // (Foret.md: "attaquent les unites qui passent sur les cases adjacentes"). No
  // targeting, no projectiles, no bois reward for kills — none of that is specified.
  private def applyForetAuras(state: MazeState, deltaMs: Double): MazeState =
    val damagePerHit = Balance.AuraDamagePerSec * deltaMs / 1000.0
    val damageByEnemy = state.forets.foldLeft(Map.empty[Long, Double])((acc, f) => accumulateAuraHits(f, state.enemies, damagePerHit, acc))
    val damaged = state.enemies.map(e => e.copy(hp = e.hp - damageByEnemy.getOrElse(e.id, 0.0)))
    state.copy(enemies = damaged.filter(_.hp > 0))

  private def accumulateAuraHits(foret: Foret, enemies: List[Enemy], damagePerHit: Double, acc: Map[Long, Double]): Map[Long, Double] =
    val adjacent = Pathfinding.neighbors((foret.col, foret.row)).toSet
    enemies
      .filter(e => adjacent.contains(GridConfig.cellOf(e.pos)))
      .foldLeft(acc)((m, e) => m.updated(e.id, m.getOrElse(e.id, 0.0) + damagePerHit))

  // Foret.md: "produit 1 bois/sec" per Foret.
  private def produceBois(state: MazeState, deltaMs: Double): MazeState =
    state.copy(bois = state.bois + state.forets.size * Balance.WoodPerSecPerForet * deltaMs / 1000.0)

  // Foret.md: "toutes les 10 sec elle genere un Elfe".
  private def advanceForetTimers(state: MazeState, deltaMs: Double): (MazeState, Int) =
    val (forets, spawned) = state.forets.foldLeft((List.empty[Foret], 0)) { case ((acc, count), f) =>
      val remaining = f.elfeSpawnInMs - deltaMs
      if remaining <= 0 then (f.copy(elfeSpawnInMs = remaining + Balance.ElfeSpawnIntervalMs) :: acc, count + 1)
      else (f.copy(elfeSpawnInMs = remaining) :: acc, count)
    }
    (state.copy(forets = forets), spawned)
