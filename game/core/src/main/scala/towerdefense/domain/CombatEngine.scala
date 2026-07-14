package towerdefense.domain

import towerdefense.domain.geometry.Vec2

// spawnedElf/spawnedGoblin: units this maze's buildings just launched — the caller
// (BattleEngine) delivers those into the *opponent's* maze.
// stolenWood/stolenFire: resources this maze just lost to arriving Goblins — the
// caller credits those (and the plunder tally) to the opponent that owns them.
case class TickResult(
    state: MazeState,
    spawnedElf: Int,
    spawnedGoblin: Int,
    stolenWood: Double,
    stolenFire: Double
)

object CombatEngine:

  def tick(state: MazeState, deltaMs: Double): TickResult =
    val (s1, stolenWood, stolenFire) = moveEnemies(state, deltaMs)
    val s2 = applyForestAuras(s1, deltaMs)
    val s3 = produceWood(s2, deltaMs)
    val s4 = produceFire(s3, deltaMs)
    val (s5, spawnedElf) = advanceForestTimers(s4, deltaMs)
    val (s6, spawnedGoblin) = advanceCaveTimers(s5, deltaMs)
    TickResult(s6, spawnedElf, spawnedGoblin, stolenWood, stolenFire)

  // Re-pathfinds every enemy from its current cell to the goal each tick, avoiding
  // building cells — no cached path to invalidate when a new building changes the maze.
  // Every arriving unit plunders wood; only Goblin also plunders fire (Goblin.md:
  // "one resource of each type" — Elf.md gives Elf no such ability beyond wood).
  private def moveEnemies(state: MazeState, deltaMs: Double): (MazeState, Double, Double) =
    val blocked = state.buildingCells
    val (remaining, arrived) =
      state.enemies.map(stepEnemy(_, blocked, deltaMs)).partitionMap(identity)
    val goblinsArrived = arrived.count(_.kind == UnitKind.Goblin)
    val stolenWood = math.min(state.wood, arrived.size * Balance.PlunderPerUnit)
    val stolenFire = math.min(state.fire, goblinsArrived * Balance.PlunderPerUnit)
    val next = state.copy(
      enemies = remaining,
      wood = state.wood - stolenWood,
      fire = state.fire - stolenFire
    )
    (next, stolenWood, stolenFire)

  private def stepEnemy(
      enemy: Enemy,
      blocked: Set[(Int, Int)],
      deltaMs: Double
  ): Either[Enemy, Enemy] =
    val currentCell = GridConfig.cellOf(enemy.pos)
    if currentCell == GridConfig.goalCell then Right(enemy)
    else
      Pathfinding.shortestPath(currentCell, GridConfig.goalCell, blocked) match
        case None => Left(enemy) // no route right now (shouldn't happen, placement guards this)
        case Some(path) => Left(advanceTowards(enemy, path, deltaMs))

  private def advanceTowards(enemy: Enemy, path: List[(Int, Int)], deltaMs: Double): Enemy =
    val nextCell = if path.size > 1 then path(1) else path.head
    val target = GridConfig.cellCenter(nextCell._1, nextCell._2)
    enemy.copy(pos = moveToward(enemy.pos, target, enemy.speedPerMs * deltaMs))

  private def moveToward(pos: Vec2, target: Vec2, maxDist: Double): Vec2 =
    val delta = target - pos
    if delta.length <= maxDist then target else pos + delta.normalized * maxDist

  // Forests deal passive damage-over-time to any enemy standing on an adjacent cell
  // (Forest.md: "attaquent les unites qui passent sur les cases adjacentes"). Caves
  // don't fight back — Cave.md gives them no such ability.
  private def applyForestAuras(state: MazeState, deltaMs: Double): MazeState =
    val damagePerHit = Balance.AuraDamagePerSec * deltaMs / 1000.0
    val damageByEnemy = state.forests.foldLeft(Map.empty[Long, Double])((acc, f) =>
      accumulateAuraHits(f, state.enemies, damagePerHit, acc)
    )
    val damaged = state.enemies.map(e => e.copy(hp = e.hp - damageByEnemy.getOrElse(e.id, 0.0)))
    state.copy(enemies = damaged.filter(_.hp > 0))

  private def accumulateAuraHits(
      forest: Forest,
      enemies: List[Enemy],
      damagePerHit: Double,
      acc: Map[Long, Double]
  ): Map[Long, Double] =
    val adjacent = Pathfinding.neighbors((forest.col, forest.row)).toSet
    enemies
      .filter(e => adjacent.contains(GridConfig.cellOf(e.pos)))
      .foldLeft(acc)((m, e) => m.updated(e.id, m.getOrElse(e.id, 0.0) + damagePerHit))

  // Forest.md: "produit 1 wood/sec" per Forest (tuned in Balance).
  private def produceWood(state: MazeState, deltaMs: Double): MazeState =
    state.copy(wood =
      state.wood + state.forests.size * Balance.WoodPerSecPerForest * deltaMs / 1000.0
    )

  // Cave.md: "produit 2 fire/sec" per Cave.
  private def produceFire(state: MazeState, deltaMs: Double): MazeState =
    state.copy(fire = state.fire + state.caves.size * Balance.FirePerSecPerCave * deltaMs / 1000.0)

  // Forest.md: "toutes les 10 sec elle genere un Elf".
  private def advanceForestTimers(state: MazeState, deltaMs: Double): (MazeState, Int) =
    val (forests, spawned) = state.forests.foldLeft((List.empty[Forest], 0)) {
      case ((acc, count), f) =>
        val remaining = f.elfSpawnInMs - deltaMs
        if remaining <= 0 then
          (f.copy(elfSpawnInMs = remaining + Balance.ElfSpawnIntervalMs) :: acc, count + 1)
        else (f.copy(elfSpawnInMs = remaining) :: acc, count)
    }
    (state.copy(forests = forests), spawned)

  // Cave.md: "toutes les 5 sec elle genere un Goblin".
  private def advanceCaveTimers(state: MazeState, deltaMs: Double): (MazeState, Int) =
    val (caves, spawned) = state.caves.foldLeft((List.empty[Cave], 0)) { case ((acc, count), c) =>
      val remaining = c.goblinSpawnInMs - deltaMs
      if remaining <= 0 then
        (c.copy(goblinSpawnInMs = remaining + Balance.GoblinSpawnIntervalMs) :: acc, count + 1)
      else (c.copy(goblinSpawnInMs = remaining) :: acc, count)
    }
    (state.copy(caves = caves), spawned)
