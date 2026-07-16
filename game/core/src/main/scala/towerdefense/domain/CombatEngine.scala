package towerdefense.domain

import towerdefense.domain.geometry.Vec2

// spawned: units this maze's buildings just launched — the caller (BattleEngine)
// delivers those into the *opponent's* maze.
// stolen: resources this maze just lost to arriving Goblins/Minotaurs — the caller
// credits those (and the plunder tally) to the opponent that owns them.
case class TickResult(
    state: MazeState,
    spawned: Map[UnitKind, Int],
    stolen: Map[Resource, Double]
)

object CombatEngine:

  def tick(state: MazeState, deltaMs: Double): TickResult =
    val (s1, stolen) = moveCreatures(state, deltaMs)
    val s2 = applyDamageSources(s1, deltaMs)
    val s3 = produceResources(s2, deltaMs)
    val (s4, spawned) = advanceSpawnTimers(s3, deltaMs)
    TickResult(s4, spawned, stolen)

  // Re-pathfinds every creature from its current cell to the goal each tick, avoiding
  // building cells — no cached path to invalidate when a new building changes the maze.
  // Plunder varies by kind — see CreatureSpecs.all(_).plunder: Elf takes wood only,
  // Goblin/Minotaur take both (Minotaur much more), and the Paladin/Wolf take neither
  // (Paladin.md/Loup.md give them no plunder ability — their value is the shield/speed
  // buff they provide in applyDamageSources/effectiveSpeedPerMs).
  private def moveCreatures(state: MazeState, deltaMs: Double): (MazeState, Map[Resource, Double]) =
    val blocked = state.buildingCells
    val (remaining, arrived) =
      state.creatures.map(stepCreature(_, state.creatures, blocked, deltaMs)).partitionMap(identity)
    val plundered = arrived
      .flatMap(c => CreatureSpecs.all(c.kind).plunder)
      .groupMapReduce(_._1)(_._2)(_ + _)
    val stolen = plundered.map { case (res, amount) =>
      res -> math.min(state.resources.getOrElse(res, 0.0), amount)
    }
    val next = state.copy(
      creatures = remaining,
      resources = stolen.foldLeft(state.resources) { case (acc, (res, amount)) =>
        acc.updated(res, acc.getOrElse(res, 0.0) - amount)
      }
    )
    (next, stolen)

  private def stepCreature(
      creature: Creature,
      allCreatures: List[Creature],
      blocked: Set[(Int, Int)],
      deltaMs: Double
  ): Either[Creature, Creature] =
    val currentCell = GridConfig.cellOf(creature.pos)
    if currentCell == GridConfig.goalCell then Right(creature)
    else
      Pathfinding.shortestPath(currentCell, GridConfig.goalCell, blocked) match
        case None => Left(creature) // no route right now (shouldn't happen, placement guards this)
        case Some(path) => Left(advanceTowards(creature, allCreatures, path, deltaMs))

  private def advanceTowards(
      creature: Creature,
      allCreatures: List[Creature],
      path: List[(Int, Int)],
      deltaMs: Double
  ): Creature =
    val nextCell = if path.size > 1 then path(1) else path.head
    val target = GridConfig.cellCenter(nextCell._1, nextCell._2)
    val speed = effectiveSpeedPerMs(creature, allCreatures)
    creature.copy(pos = moveToward(creature.pos, target, speed * deltaMs))

  // Loup.md: "augmente la vitesse de deplacement des unites a 2 cases de 50%" — any
  // Wolf within range multiplies another creature's speed (not its own; the boost is for
  // *other* units, Wolf's own speed is already baked into its CreatureSpec). Multiple
  // nearby Wolves don't stack — presence of at least one is enough, mirroring how
  // Paladin's shield is binary rather than additive per source.
  private def effectiveSpeedPerMs(creature: Creature, allCreatures: List[Creature]): Double =
    val cell = GridConfig.cellOf(creature.pos)
    val boosted = allCreatures.exists(other =>
      other.id != creature.id && other.kind == UnitKind.Wolf &&
        chebyshevDistance(cell, GridConfig.cellOf(other.pos)) <= Balance.WolfSpeedAuraRangeCells
    )
    if boosted then creature.speedPerMs * Balance.WolfSpeedAuraMultiplier else creature.speedPerMs

  private def moveToward(pos: Vec2, target: Vec2, maxDist: Double): Vec2 =
    val delta = target - pos
    if delta.length <= maxDist then target else pos + delta.normalized * maxDist

  // Buildings with the Ent aura — Foret.md introduces it, and Jungle (an upgrade of
  // Foret) inherits it since "Amelioration" is cumulative; Grove/Bosquet, the base tier,
  // doesn't have it yet. domain-visible (not private) since CompositeStrategy's maze
  // scoring needs the same set to value routing paths past these buildings.
  private[domain] val auraBuildingKinds: Set[BuildingKind] = Set(BuildingKind.Forest, BuildingKind.Jungle)

  // Two independent damage sources, combined before Paladin shielding is applied once to
  // the total (not once per source) — Forest/Jungle deal passive damage-over-time to
  // every creature standing on an adjacent cell (Foret.md: "attaquent les unites qui
  // passent sur les cases adjacentes"), while each Watchtower picks a single nearest
  // target within its range and hits only that one (Tour de guet.md: "Inflige 10 degats
  // chaque seconde a une cible"). Caves don't fight back — Cave.md gives them no such
  // ability. A Paladin shields any creature adjacent to (or sharing a cell with) it from
  // some of that damage (Paladin.md: "protege les unites adjacentes de 2 degats") — it's
  // on the receiving maze's side of the fight, same as the units it protects. This combat
  // math is intentionally kept as kind-based special cases, not folded into BuildingSpec/
  // CreatureSpec (see the refactor's confirmed scope).
  private def applyDamageSources(state: MazeState, deltaMs: Double): MazeState =
    val forestDamagePerHit = Balance.AuraDamagePerSec * deltaMs / 1000.0
    val watchtowerDamagePerHit = Balance.WatchtowerDamagePerSec * deltaMs / 1000.0
    val forests = state.buildings.filter(b => auraBuildingKinds.contains(b.kind))
    val watchtowers = state.buildings.filter(_.kind == BuildingKind.Watchtower)
    val fromForests = forests.foldLeft(Map.empty[Long, Double])((acc, f) =>
      accumulateAuraHits(f, state.creatures, forestDamagePerHit, acc)
    )
    val damageByCreature = watchtowers.foldLeft(fromForests)((acc, w) =>
      accumulateWatchtowerHit(w, state.creatures, watchtowerDamagePerHit, acc)
    )
    val reductionPerHit = Balance.PaladinAuraDamageReductionPerSec * deltaMs / 1000.0
    val shielded = paladinShieldedIds(state.creatures)
    val damaged = state.creatures.map { c =>
      val raw = damageByCreature.getOrElse(c.id, 0.0)
      val taken = if shielded.contains(c.id) then math.max(0.0, raw - reductionPerHit) else raw
      c.copy(hp = c.hp - taken)
    }
    state.copy(creatures = damaged.filter(_.hp > 0))

  private def accumulateAuraHits(
      forest: Building,
      creatures: List[Creature],
      damagePerHit: Double,
      acc: Map[Long, Double]
  ): Map[Long, Double] =
    val adjacent = Pathfinding.neighbors((forest.col, forest.row)).toSet
    creatures
      .filter(c => adjacent.contains(GridConfig.cellOf(c.pos)))
      .foldLeft(acc)((m, c) => m.updated(c.id, m.getOrElse(c.id, 0.0) + damagePerHit))

  private def accumulateWatchtowerHit(
      tower: Building,
      creatures: List[Creature],
      damagePerHit: Double,
      acc: Map[Long, Double]
  ): Map[Long, Double] =
    nearestTargetInRange(tower, creatures) match
      case None           => acc
      case Some(targetId) => acc.updated(targetId, acc.getOrElse(targetId, 0.0) + damagePerHit)

  // Ties broken by id for determinism — the exact tie-break doesn't matter gameplay-wise,
  // just that it's stable rather than map/set iteration order.
  private def nearestTargetInRange(tower: Building, creatures: List[Creature]): Option[Long] =
    creatures
      .map(c => (c.id, chebyshevDistance((tower.col, tower.row), GridConfig.cellOf(c.pos))))
      .filter { case (_, dist) => dist <= Balance.WatchtowerRangeCells }
      .sortBy { case (id, dist) => (dist, id) }
      .headOption
      .map(_._1)

  private def chebyshevDistance(a: (Int, Int), b: (Int, Int)): Int =
    math.max(math.abs(a._1 - b._1), math.abs(a._2 - b._2))

  private def paladinShieldedIds(creatures: List[Creature]): Set[Long] =
    val paladinCells =
      creatures.filter(_.kind == UnitKind.Paladin).map(c => GridConfig.cellOf(c.pos))
    val shieldedCells = paladinCells.flatMap(c => c :: Pathfinding.neighbors(c)).toSet
    creatures.filter(c => shieldedCells.contains(GridConfig.cellOf(c.pos))).map(_.id).toSet

  // Exposed (not private) so any other reader of live production rates — the UI's stock
  // display, tooltips — computes the exact same number tick applies, instead of
  // re-deriving `count * rate` by hand and risking it drift out of sync.
  def productionPerSec(state: MazeState, resource: Resource): Double =
    state.buildings
      .groupBy(_.kind)
      .map { case (kind, bs) => bs.size * BuildingSpecs.all(kind).produces.getOrElse(resource, 0.0) }
      .sum

  private def produceResources(state: MazeState, deltaMs: Double): MazeState =
    val produced = Resource.values.map(res => res -> productionPerSec(state, res) * deltaMs / 1000.0)
    state.copy(
      resources = produced.foldLeft(state.resources) { case (acc, (res, amount)) =>
        acc.updated(res, acc.getOrElse(res, 0.0) + amount)
      }
    )

  private def advanceSpawnTimers(state: MazeState, deltaMs: Double): (MazeState, Map[UnitKind, Int]) =
    val (buildings, spawned) =
      state.buildings.foldLeft((List.empty[Building], Map.empty[UnitKind, Int])) {
        case ((acc, counts), b) =>
          BuildingSpecs.all(b.kind).spawns match
            case None => (b :: acc, counts)
            case Some((unitKind, intervalMs)) =>
              val remaining = b.spawnCountdownMs - deltaMs
              if remaining <= 0 then
                (
                  b.copy(spawnCountdownMs = remaining + intervalMs) :: acc,
                  counts.updated(unitKind, counts.getOrElse(unitKind, 0) + 1)
                )
              else (b.copy(spawnCountdownMs = remaining) :: acc, counts)
      }
    (state.copy(buildings = buildings), spawned)
