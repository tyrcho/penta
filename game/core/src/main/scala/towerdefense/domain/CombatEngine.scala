package towerdefense.domain

import towerdefense.domain.geometry.Vec2

// spawnedElf/spawnedGoblin/spawnedMinotaur/spawnedPaladin: units this maze's buildings
// just launched — the caller (BattleEngine) delivers those into the *opponent's* maze.
// stolenWood/stolenFire: resources this maze just lost to arriving Goblins/Minotaurs —
// the caller credits those (and the plunder tally) to the opponent that owns them.
case class TickResult(
    state: MazeState,
    spawnedElf: Int,
    spawnedGoblin: Int,
    spawnedMinotaur: Int,
    spawnedPaladin: Int,
    stolenWood: Double,
    stolenFire: Double
)

object CombatEngine:

  def tick(state: MazeState, deltaMs: Double): TickResult =
    val (s1, stolenWood, stolenFire) = moveEnemies(state, deltaMs)
    val s2 = applyForestAuras(s1, deltaMs)
    val s3 = produceWood(s2, deltaMs)
    val s4 = produceFire(s3, deltaMs)
    val s5 = produceLight(s4, deltaMs)
    val (s6, spawnedElf) = advanceForestTimers(s5, deltaMs)
    val (s7, spawnedGoblin) = advanceCaveTimers(s6, deltaMs)
    val (s8, spawnedMinotaur) = advanceLabyrintheTimers(s7, deltaMs)
    val (s9, spawnedPaladin) = advanceEgliseTimers(s8, deltaMs)
    TickResult(s9, spawnedElf, spawnedGoblin, spawnedMinotaur, spawnedPaladin, stolenWood, stolenFire)

  // Re-pathfinds every enemy from its current cell to the goal each tick, avoiding
  // building cells — no cached path to invalidate when a new building changes the maze.
  // Plunder varies by kind — see plunderAmounts: Elf takes wood only, Goblin/Minotaur
  // take both (Minotaur much more), and the Paladin takes neither (Paladin.md gives it
  // no plunder ability — its value is the aura it provides in applyForestAuras).
  private def moveEnemies(state: MazeState, deltaMs: Double): (MazeState, Double, Double) =
    val blocked = state.buildingCells
    val (remaining, arrived) =
      state.enemies.map(stepEnemy(_, blocked, deltaMs)).partitionMap(identity)
    val (woodAmounts, fireAmounts) = arrived.map(e => plunderAmounts(e.kind)).unzip
    val stolenWood = math.min(state.wood, woodAmounts.sum)
    val stolenFire = math.min(state.fire, fireAmounts.sum)
    val next = state.copy(
      enemies = remaining,
      wood = state.wood - stolenWood,
      fire = state.fire - stolenFire
    )
    (next, stolenWood, stolenFire)

  private def plunderAmounts(kind: UnitKind): (Double, Double) = kind match
    case UnitKind.Elf      => (Balance.PlunderPerUnit, 0.0)
    case UnitKind.Goblin   => (Balance.PlunderPerUnit, Balance.PlunderPerUnit)
    case UnitKind.Minotaur => (Balance.MinotaurPlunderPerUnit, Balance.MinotaurPlunderPerUnit)
    case UnitKind.Paladin  => (0.0, 0.0)

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
  // don't fight back — Cave.md gives them no such ability. A Paladin shields any enemy
  // adjacent to (or sharing a cell with) it from some of that damage (Paladin.md:
  // "protege les unites adjacentes de 2 degats") — it's on the receiving maze's side of
  // the fight, same as the units it protects.
  private def applyForestAuras(state: MazeState, deltaMs: Double): MazeState =
    val damagePerHit = Balance.AuraDamagePerSec * deltaMs / 1000.0
    val damageByEnemy = state.forests.foldLeft(Map.empty[Long, Double])((acc, f) =>
      accumulateAuraHits(f, state.enemies, damagePerHit, acc)
    )
    val reductionPerHit = Balance.PaladinAuraDamageReductionPerSec * deltaMs / 1000.0
    val shielded = paladinShieldedIds(state.enemies)
    val damaged = state.enemies.map { e =>
      val raw = damageByEnemy.getOrElse(e.id, 0.0)
      val taken = if shielded.contains(e.id) then math.max(0.0, raw - reductionPerHit) else raw
      e.copy(hp = e.hp - taken)
    }
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

  private def paladinShieldedIds(enemies: List[Enemy]): Set[Long] =
    val paladinCells = enemies.filter(_.kind == UnitKind.Paladin).map(e => GridConfig.cellOf(e.pos))
    val shieldedCells = paladinCells.flatMap(c => c :: Pathfinding.neighbors(c)).toSet
    enemies.filter(e => shieldedCells.contains(GridConfig.cellOf(e.pos))).map(_.id).toSet

  // Forest.md: "produit 1 bois/5 sec" per Forest (tuned in Balance).
  private def produceWood(state: MazeState, deltaMs: Double): MazeState =
    state.copy(wood =
      state.wood + state.forests.size * Balance.WoodPerSecPerForest * deltaMs / 1000.0
    )

  // Cave.md: "produit 1 feu/5 sec" per Cave.
  private def produceFire(state: MazeState, deltaMs: Double): MazeState =
    state.copy(fire = state.fire + state.caves.size * Balance.FirePerSecPerCave * deltaMs / 1000.0)

  // Eglise.md: "Produit 1 Lumiere par seconde" per Eglise.
  private def produceLight(state: MazeState, deltaMs: Double): MazeState =
    state.copy(light =
      state.light + state.eglises.size * Balance.LightPerSecPerEglise * deltaMs / 1000.0
    )

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

  // Labyrinthe.md: "toutes les 10 secondes genere un Minotaure".
  private def advanceLabyrintheTimers(state: MazeState, deltaMs: Double): (MazeState, Int) =
    val (labyrinths, spawned) =
      state.labyrinths.foldLeft((List.empty[Labyrinth], 0)) { case ((acc, count), l) =>
        val remaining = l.minotaurSpawnInMs - deltaMs
        if remaining <= 0 then
          (
            l.copy(minotaurSpawnInMs = remaining + Balance.MinotaurSpawnIntervalMs) :: acc,
            count + 1
          )
        else (l.copy(minotaurSpawnInMs = remaining) :: acc, count)
      }
    (state.copy(labyrinths = labyrinths), spawned)

  // Eglise.md: "toutes les 10 secondes genere un Paladin".
  private def advanceEgliseTimers(state: MazeState, deltaMs: Double): (MazeState, Int) =
    val (eglises, spawned) = state.eglises.foldLeft((List.empty[Eglise], 0)) {
      case ((acc, count), e) =>
        val remaining = e.paladinSpawnInMs - deltaMs
        if remaining <= 0 then
          (e.copy(paladinSpawnInMs = remaining + Balance.PaladinSpawnIntervalMs) :: acc, count + 1)
        else (e.copy(paladinSpawnInMs = remaining) :: acc, count)
    }
    (state.copy(eglises = eglises), spawned)
