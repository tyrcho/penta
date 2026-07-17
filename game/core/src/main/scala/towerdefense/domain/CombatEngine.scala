package towerdefense.domain

import towerdefense.domain.geometry.Vec2

// spawned: units this maze's buildings just launched — the caller (BattleEngine)
// delivers those into the *opponent's* maze.
// stolen: resources this maze just lost to arriving Goblins/Minotaurs — the caller
// credits those (and the plunder tally) to the opponent that owns them.
// deaths: creatures killed this tick by an aura and/or a Watchtower (see DeathCause) —
// purely observational, nothing else in CombatEngine reads it back.
// arrivals: the UnitKind of every creature that reached the goal this tick, including
// ones with no plunder ability (Paladin, Wolf) that `stolen` alone would miss entirely.
// corrupted: buildings this maze just lost to an enemy Zombie/Vampire finishing a
// corruption (Corruption.md) — like `stolen`, the caller credits their cost (in full,
// see Corrosion's doc) and a count toward the corrupting side's own Mort victory tally.
case class TickResult(
    state: MazeState,
    spawned: Map[UnitKind, Int],
    stolen: Map[Resource, Double],
    deaths: List[Death],
    arrivals: List[UnitKind],
    corrupted: List[Corrosion]
)

// Which damage source(s) killed a creature this tick — a *type* of source, not which
// specific Forest/Watchtower instance (that would need per-building damage maps; not
// worth the complexity for what this is used for, see MatchLog's doc in the sim module).
enum DeathCause derives CanEqual:
  case Aura, Watchtower, AuraAndWatchtower

case class Death(creatureId: Long, kind: UnitKind, cause: DeathCause)

// A building destroyed by corruption this tick (Corruption.md) — cost is the full
// BuildingSpecs cost of `kind`, refunded to the corrupting creature's owner by
// BattleEngine (unlike Demolition's partial self-refund). `cost` already reflects only
// the last upgrade's price for an upgraded kind like Jungle (see BuildingSpecs/
// Placement.upgradeBuilding — cost was never modeled as cumulative), matching
// Corruption.md's own clarification that an upgraded building only refunds its last
// upgrade's cost.
case class Corrosion(buildingId: Long, kind: BuildingKind, col: Int, row: Int, cost: Map[Resource, Double])

object CombatEngine:

  // attackerResearchLevels: the *opponent's* researchLevels (i.e. whoever owns the
  // creatures walking `state`) — only Recherches chaotiques reads it (see moveCreatures),
  // needed because a creature's plunder is normally a pure function of its kind, but
  // chaotiques makes it depend on research the creature's owner did in their *own* maze,
  // invisible from `state` alone. Defaults to empty so every caller untouched by Science
  // (every existing test, the live browser game before a match ever researches anything)
  // keeps today's exact behavior with no plumbing required.
  def tick(state: MazeState, deltaMs: Double, attackerResearchLevels: Map[BuildingKind, Int] = Map.empty): TickResult =
    val (s1, stolen, arrivals) = moveCreatures(state, deltaMs, attackerResearchLevels)
    val (s2, deaths) = applyDamageSources(s1, deltaMs)
    val (s3, corrupted) = applyCorruption(s2, deltaMs)
    val s4 = produceResources(s3, deltaMs)
    val (s5, spawned) = advanceSpawnTimers(s4, deltaMs)
    TickResult(s5, spawned, stolen, deaths, arrivals, corrupted)

  // Re-pathfinds every creature from its current cell to the goal each tick, avoiding
  // building cells — no cached path to invalidate when a new building changes the maze.
  // Plunder varies by kind — see CreatureSpecs.all(_).plunder: Elf takes wood only,
  // Goblin/Minotaur take both (Minotaur much more), and the Paladin/Wolf take neither
  // (Paladin.md/Loup.md give them no plunder ability — their value is the shield/speed
  // buff they provide in applyDamageSources/effectiveSpeedPerMs). `arrived`'s kinds are
  // reported in full via the third return value, since `stolen` alone drops any arrival
  // with no plunder ability entirely.
  private def moveCreatures(
      state: MazeState,
      deltaMs: Double,
      attackerResearchLevels: Map[BuildingKind, Int]
  ): (MazeState, Map[Resource, Double], List[UnitKind]) =
    val blocked = state.buildingCells
    val (remaining, arrived) =
      state.creatures.map(stepCreature(_, state.creatures, blocked, deltaMs)).partitionMap(identity)
    val plundered = arrived
      .flatMap(c => effectivePlunder(c.kind, attackerResearchLevels))
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
    (next, stolen, arrived.map(_.kind))

  // Recherches chaotiques.md: "Augmente l'efficacite du pillage de chaque unite (meme
  // celles qui ne pillent pas initialement) dans chaque ressource de: X" — a flat bonus
  // added to *every* resource (not just ones the kind already plunders), so at a high
  // enough chaotiques level even Paladin/Wolf/Zombie/Vampire arrivals start stealing.
  private def effectivePlunder(kind: UnitKind, attackerResearchLevels: Map[BuildingKind, Int]): Map[Resource, Double] =
    val chaotiquesLevel = attackerResearchLevels.getOrElse(BuildingKind.LaboDuChaos, 0)
    if chaotiquesLevel <= 0 then CreatureSpecs.all(kind).plunder
    else
      val bonus = ResearchSpecs.all(BuildingKind.LaboDuChaos).effectAtLevel(chaotiquesLevel)
      val base = CreatureSpecs.all(kind).plunder
      Resource.values.map(res => res -> (base.getOrElse(res, 0.0) + bonus)).toMap

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
  private def applyDamageSources(state: MazeState, deltaMs: Double): (MazeState, List[Death]) =
    // Recherches loyales.md: "Augmente les degats infliges par les batiments" — purely
    // local to `state` (the maze whose own buildings are dealing the damage), unlike
    // chaotiques' plunder bonus above which needs the *opponent's* research instead.
    val loyalesLevel = state.researchLevels.getOrElse(BuildingKind.LaboDeLaLoi, 0)
    val loyalesMultiplier = 1.0 + ResearchSpecs.all(BuildingKind.LaboDeLaLoi).effectAtLevel(loyalesLevel)
    val forestDamagePerHit = Balance.AuraDamagePerSec * deltaMs / 1000.0 * loyalesMultiplier
    val watchtowerDamagePerHit = Balance.WatchtowerDamagePerSec * deltaMs / 1000.0 * loyalesMultiplier
    val forests = state.buildings.filter(b => auraBuildingKinds.contains(b.kind))
    val watchtowers = state.buildings.filter(_.kind == BuildingKind.Watchtower)
    val fromForests = forests.foldLeft(Map.empty[Long, Double])((acc, f) =>
      accumulateAuraHits(f, state.creatures, forestDamagePerHit, acc)
    )
    val fromTowers = watchtowers.foldLeft(Map.empty[Long, Double])((acc, w) =>
      accumulateWatchtowerHit(w, state.creatures, watchtowerDamagePerHit, acc)
    )
    val damageByCreature = mergeSum(fromForests, fromTowers)
    val reductionPerHit = Balance.PaladinAuraDamageReductionPerSec * deltaMs / 1000.0
    val shielded = paladinShieldedIds(state.creatures)
    val damaged = state.creatures.map { c =>
      val raw = damageByCreature.getOrElse(c.id, 0.0)
      // Vampire.md: "Reduit les degats qu'il subit de 50% (mais n'est pas protege par
      // l'aura du Paladin)" — explicitly excluded from Paladin's shield even when
      // standing adjacent to one, and instead gets its own unconditional flat reduction
      // applied to whatever raw damage it takes from any source.
      val afterShield =
        if c.kind != UnitKind.Vampire && shielded.contains(c.id) then math.max(0.0, raw - reductionPerHit)
        else raw
      val taken =
        if c.kind == UnitKind.Vampire then afterShield * (1.0 - Balance.VampireDamageReductionFraction)
        else afterShield
      c.copy(hp = c.hp - taken)
    }
    val dead = damaged.filter(_.hp <= 0)
    val deaths = dead.map(c => Death(c.id, c.kind, deathCause(c.id, fromForests, fromTowers)))
    (state.copy(creatures = damaged.filter(_.hp > 0)), deaths)

  private def mergeSum(a: Map[Long, Double], b: Map[Long, Double]): Map[Long, Double] =
    b.foldLeft(a) { case (acc, (id, amount)) => acc.updated(id, acc.getOrElse(id, 0.0) + amount) }

  // A creature only dies from a source it actually took damage from this tick — Paladin
  // shielding can zero out one source's contribution to `damaged` without it being absent
  // from `fromForests`/`fromTowers` (those record raw pre-shield hits), but a dead
  // creature's cause listing which sources actually hit it is still accurate: shielding
  // reduces the total, it doesn't erase which sources fired.
  private def deathCause(
      creatureId: Long,
      fromForests: Map[Long, Double],
      fromTowers: Map[Long, Double]
  ): DeathCause =
    val auraHit = fromForests.contains(creatureId)
    val towerHit = fromTowers.contains(creatureId)
    if auraHit && towerHit then DeathCause.AuraAndWatchtower
    else if towerHit then DeathCause.Watchtower
    else DeathCause.Aura

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

  // private[domain] (not private) so CompositeStrategy's dangerScore can use the exact
  // same distance metric Watchtower targeting itself uses, instead of redefining it.
  private[domain] def chebyshevDistance(a: (Int, Int), b: (Int, Int)): Int =
    math.max(math.abs(a._1 - b._1), math.abs(a._2 - b._2))

  private def paladinShieldedIds(creatures: List[Creature]): Set[Long] =
    val paladinCells =
      creatures.filter(_.kind == UnitKind.Paladin).map(c => GridConfig.cellOf(c.pos))
    val shieldedCells = paladinCells.flatMap(c => c :: Pathfinding.neighbors(c)).toSet
    creatures.filter(c => shieldedCells.contains(GridConfig.cellOf(c.pos))).map(_.id).toSet

  // Zombie.md/Vampire.md's corruption rates — Corruption.md: "Les unites de cette faction
  // corrompent les batiments qu'elles touchent", no restriction to particular building
  // kinds, so every building in `state` is a fair target the same way Forest's aura hits
  // every creature, not just certain kinds of them.
  private val corruptionRatesPerSec: Map[UnitKind, Double] =
    Map(UnitKind.Zombie -> Balance.ZombieCorruptionPercentPerSec, UnitKind.Vampire -> Balance.VampireCorruptionPercentPerSec)

  // Mirrors applyDamageSources' aura pattern but building-side: instead of a *building*
  // hitting *creatures* on adjacent cells, a *creature* corrupts *buildings* on adjacent
  // cells. Multiple corrupting creatures near the same building stack (summed, same as
  // multiple Forests would stack aura damage on a creature between them). A building
  // whose corruptionPercent reaches Balance.CorruptionMaxPercent is removed from the
  // maze and reported as a Corrosion for the caller to refund.
  private def applyCorruption(state: MazeState, deltaMs: Double): (MazeState, List[Corrosion]) =
    val corruptors = state.creatures.filter(c => corruptionRatesPerSec.contains(c.kind))
    if corruptors.isEmpty then (state, Nil)
    else
      val corruptionByCell = corruptors
        .groupMapReduce(c => GridConfig.cellOf(c.pos))(c => corruptionRatesPerSec(c.kind) * deltaMs / 1000.0)(_ + _)
      val updated = state.buildings.map { b =>
        val hits = Pathfinding.neighbors((b.col, b.row)).flatMap(corruptionByCell.get).sum
        if hits <= 0.0 then b
        else b.copy(corruptionPercent = math.min(Balance.CorruptionMaxPercent, b.corruptionPercent + hits))
      }
      val (destroyed, remaining) = updated.partition(_.corruptionPercent >= Balance.CorruptionMaxPercent)
      val corrosions = destroyed.map(b => Corrosion(b.id, b.kind, b.col, b.row, BuildingSpecs.all(b.kind).cost))
      (state.copy(buildings = remaining), corrosions)

  // Exposed (not private) so any other reader of live production rates — the UI's stock
  // display, tooltips — computes the exact same number tick applies, instead of
  // re-deriving `count * rate` by hand and risking it drift out of sync.
  def productionPerSec(state: MazeState, resource: Resource): Double =
    val base = state.buildings
      .groupBy(_.kind)
      .map { case (kind, bs) => bs.size * BuildingSpecs.all(kind).produces.getOrElse(resource, 0.0) }
      .sum
    base * (1.0 + engendreBoost(state, resource))

  // Engendre.md's resource-generation cycle, keyed by *target* — the resource whose
  // producer-buildings boost `resource`'s own production rate (see Balance.
  // EngendreBoostPerBuilding's doc): Wood's boost comes from Light producers, Fire's from
  // Wood producers, Shadow's from Fire producers, Crystal's from Shadow producers, Light's
  // from Crystal producers — the same 5-cycle Engendre.md itself describes.
  private val engendreSource: Map[Resource, Resource] = Map(
    Resource.Fire -> Resource.Wood,
    Resource.Shadow -> Resource.Fire,
    Resource.Crystal -> Resource.Shadow,
    Resource.Light -> Resource.Crystal,
    Resource.Wood -> Resource.Light
  )

  // Exposed (not private) so any other reader of the live boost — GameApp's per-building
  // hover tooltip, which shows *this building's* effective rate, not the maze-wide total
  // productionPerSec already reports — computes the exact same multiplier, instead of
  // re-deriving "which resource sources this one" and risking it drift.
  def engendreBoost(state: MazeState, resource: Resource): Double =
    val source = engendreSource(resource)
    val sourceBuildingCount =
      state.buildings.count(b => BuildingSpecs.all(b.kind).produces.getOrElse(source, 0.0) > 0.0)
    Balance.EngendreBoostPerBuilding * sourceBuildingCount

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
