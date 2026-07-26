package towerdefense.domain

import towerdefense.domain.geometry.Vec2
import towerdefense.domain.i18n.EntityNames

// spawned: units this maze's buildings just launched — the caller (BattleEngine)
// delivers those into the *opponent's* maze.
// stolen: resources this maze just LOST to arriving Goblins/Minotaurs/Elves this tick —
// clamped to what was actually on hand (can't go negative), purely descriptive of this
// side's own loss.
// plundered: what the ATTACKER gets credited (BattleEngine.creditPlunder) for those same
// arrivals — the same resource(s) as `stolen` (every creature's plunder pays out in the
// real resource it drains — Elf: Wood, Goblin/Minotaur: Gold, both a genuine transfer
// rather than a conversion), but NOT clamped by what this side actually had: always the
// full nominal CreatureSpecs.all(kind).plunder amount, even raiding a maze with nothing
// left to steal.
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
    plundered: Map[Resource, Double],
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
  // creatures walking `state`) — only Recherches Sombres reads it (see applyCorruption),
  // needed because a corrupting creature's corruption rate is normally a pure function of
  // its kind, but Sombres makes it depend on research the creature's owner did in their
  // *own* maze, invisible from `state` alone. Defaults to empty so every caller untouched
  // by Science (every existing test, the live browser game before a match ever researches
  // anything) keeps today's exact behavior with no plumbing required.
  def tick(state: MazeState, deltaMs: Double, attackerResearchLevels: Map[BuildingKind, Int] = Map.empty): TickResult =
    val s0 = advanceConstruction(state, deltaMs)
    val (s1, stolen, plundered, arrivals) = moveCreatures(s0, deltaMs)
    val (s2, deaths) = applyDamageSources(s1, deltaMs)
    val (s3, corrupted, hitsByCreature) = applyCorruption(s2, deltaMs, attackerResearchLevels)
    val s3a = healBuildingCorruption(s3, deltaMs)
    val s3b = healSummoners(s3a, hitsByCreature, deltaMs)
    val s4 = produceResources(s3b, deltaMs)
    val (s5, spawned) = advanceSpawnTimers(s4, deltaMs)
    val s6 = advanceCreatureSummons(s5, deltaMs)
    TickResult(s6, spawned, stolen, plundered, deaths, arrivals, corrupted)

  // Re-pathfinds every creature from its current cell to the goal each tick, avoiding
  // building cells — no cached path to invalidate when a new building changes the maze.
  // Plunder varies by kind — see CreatureSpecs.all(_).plunder: Elf takes wood only,
  // Goblin/Minotaur take Gold (Minotaur much more), and the Paladin/Wolf take neither
  // (Paladin.md/Loup.md give them no plunder ability — their value is the shield/speed
  // buff they provide in applyDamageSources/effectiveSpeedPerMs). `arrived`'s kinds are
  // reported in full via the final return value, since `stolen` alone drops any arrival
  // with no plunder ability entirely.
  // stolen (this side's own loss) and plundered (what the attacker gets — TickResult's
  // own doc) start from the exact same aggregate plunder map; stolen just clamps it to
  // what was actually on hand, plundered doesn't.
  private def moveCreatures(
      state: MazeState,
      deltaMs: Double
  ): (MazeState, Map[Resource, Double], Map[Resource, Double], List[UnitKind]) =
    val blocked = state.buildingCells
    val angelCells = state.buildings.filter(_.kind == BuildingKind.Angel).map(b => (b.col, b.row)).toSet
    val stasisCells = state.buildings.filter(_.kind == BuildingKind.StasisField).map(b => (b.col, b.row)).toSet
    val (remaining, arrived) =
      state.creatures
        .map(stepCreature(_, state.creatures, blocked, angelCells, stasisCells, deltaMs))
        .partitionMap(identity)
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
    (next, stolen, plundered, arrived.map(_.kind))

  // Necromancien.md: "pendant 1 seconde, il reste immobile" (see CreatureSpec.
  // spawnFreezeMs/Creature.frozenMs) — a frozen creature doesn't pathfind or move at all
  // this tick, just burns down its own freeze timer; still takes damage normally (this
  // check only short-circuits movement, applyDamageSources doesn't look at frozenMs).
  private def stepCreature(
      creature: Creature,
      allCreatures: List[Creature],
      blocked: Set[(Int, Int)],
      angelCells: Set[(Int, Int)],
      stasisCells: Set[(Int, Int)],
      deltaMs: Double
  ): Either[Creature, Creature] =
    if creature.frozenMs > 0 then Left(creature.copy(frozenMs = math.max(0.0, creature.frozenMs - deltaMs)))
    else
      val currentCell = GridConfig.cellOf(creature.pos)
      if currentCell == GridConfig.goalCell then Right(creature)
      else
        Pathfinding.shortestPath(currentCell, GridConfig.goalCell, blocked) match
          case None => Left(creature) // no route right now (shouldn't happen, placement guards this)
          case Some(path) => Left(advanceTowards(creature, allCreatures, angelCells, stasisCells, path, deltaMs))

  private def advanceTowards(
      creature: Creature,
      allCreatures: List[Creature],
      angelCells: Set[(Int, Int)],
      stasisCells: Set[(Int, Int)],
      path: List[(Int, Int)],
      deltaMs: Double
  ): Creature =
    val nextCell = if path.size > 1 then path(1) else path.head
    val target = GridConfig.cellCenter(nextCell._1, nextCell._2)
    val speed = effectiveSpeedPerMs(creature, allCreatures, angelCells, stasisCells)
    creature.copy(pos = moveToward(creature.pos, target, speed * deltaMs))

  // Loup.md: "augmente la vitesse de deplacement des unites a 2 cases de 50%" — any
  // Wolf within range multiplies another creature's speed (not its own; the boost is for
  // *other* units, Wolf's own speed is already baked into its CreatureSpec). Multiple
  // nearby Wolves don't stack — presence of at least one is enough, mirroring how
  // Paladin's shield is binary rather than additive per source. Ange.md's slow ("ralentit
  // leur vitesse de deplacement de 25%") is the opposite kind of aura — a *building*
  // debuffing any enemy creature adjacent to it (same adjacency rule as its own damage,
  // see accumulateAuraHits) — and stacks multiplicatively with Wolf's boost rather than
  // overriding it, since nothing in Ange.md/Loup.md says otherwise. Champ de Stase
  // (Science) adds a third, independent slow source on the same adjacency rule as Angel's
  // (see Balance.StasisSlowFraction's doc), also stacking multiplicatively rather than
  // overriding.
  private def effectiveSpeedPerMs(
      creature: Creature,
      allCreatures: List[Creature],
      angelCells: Set[(Int, Int)],
      stasisCells: Set[(Int, Int)]
  ): Double =
    val cell = GridConfig.cellOf(creature.pos)
    val boosted = allCreatures.exists(other =>
      other.id != creature.id && other.kind == UnitKind.Wolf &&
        chebyshevDistance(cell, GridConfig.cellOf(other.pos)) <= Balance.WolfSpeedAuraRangeCells
    )
    val slowedByAngel = angelCells.exists(ac => Pathfinding.neighbors(ac).contains(cell))
    val slowedByStasis = stasisCells.exists(sc => Pathfinding.neighbors(sc).contains(cell))
    val boostMultiplier = if boosted then Balance.WolfSpeedAuraMultiplier else 1.0
    val angelSlowMultiplier = if slowedByAngel then 1.0 - Balance.AngelSlowFraction else 1.0
    val stasisSlowMultiplier = if slowedByStasis then 1.0 - Balance.StasisSlowFraction else 1.0
    creature.speedPerMs * boostMultiplier * angelSlowMultiplier * stasisSlowMultiplier

  private def moveToward(pos: Vec2, target: Vec2, maxDist: Double): Vec2 =
    val delta = target - pos
    if delta.length <= maxDist then target else pos + delta.normalized * maxDist

  // Buildings that damage every enemy creature standing adjacent to them, each at its own
  // rate (see auraDamagePerSecFor) — Foret.md introduces the Ent aura, Jungle (an upgrade
  // of Foret) inherits it since "Amelioration" is cumulative (Grove/Bosquet, the base tier,
  // doesn't have it yet), and Ange.md adds a third, independently-costed source at a
  // different rate (plus its own slow debuff — see effectiveSpeedPerMs). domain-visible
  // (not private) since CompositeStrategy's maze scoring needs the same set to value
  // routing paths past these buildings — that heuristic (LayoutPolicy.dangerScore) still
  // assumes a single flat Balance.AuraDamagePerSec per aura cell, so it currently
  // underrates how dangerous a path past an Angel specifically is; only the real per-tick
  // damage below (applyDamageSources) needs to be exact.
  private[domain] val auraBuildingKinds: Set[BuildingKind] =
    Set(BuildingKind.Forest, BuildingKind.Jungle, BuildingKind.Angel, BuildingKind.PassingGate)

  private def auraDamagePerSecFor(kind: BuildingKind): Double = kind match
    case BuildingKind.Angel       => Balance.AngelDamagePerSec
    case BuildingKind.PassingGate => Balance.PassingGateDamagePerSec
    case _                        => Balance.AuraDamagePerSec

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
  //
  // Each source fires a single discrete full-rate hit every Balance.DamageTickIntervalMs
  // (see its doc and tickDamageCooldown) rather than dribbling out a deltaMs-scaled
  // fraction every engine tick — so the reduction/shield amounts below are flat per-hit
  // values too, applied only in a tick where something actually fired, not scaled by
  // deltaMs the way a continuous rate would need.
  private def applyDamageSources(state: MazeState, deltaMs: Double): (MazeState, List[Death]) =
    // Recherches loyales.md: "Augmente la vitesse d'attaque des batiments" — purely local
    // to `state` (the maze whose own buildings are dealing the damage), and scoped to
    // Loi-faction damage dealers ONLY (Watchtower/Angel — Faction.Loi): Forest/Jungle's
    // aura (Nature) and PassingGate's aura (Mort) always fire at the plain
    // Balance.DamageTickIntervalMs below, regardless of this maze's own Loi research
    // level. A faster interval, not a bigger per-hit number — damage-per-hit stays the
    // building's own flat Balance.*DamagePerSec no matter what.
    val loyalesLevel = state.researchLevels.getOrElse(BuildingKind.LaboDeLaLoi, 0)
    val loyalesSpeedMultiplier = 1.0 + ResearchSpecs.all(BuildingKind.LaboDeLaLoi).effectAtLevel(loyalesLevel)
    def intervalFor(kind: BuildingKind): Double =
      if EntityNames.buildingInfo(kind).faction == Faction.Loi then Balance.DamageTickIntervalMs / loyalesSpeedMultiplier
      else Balance.DamageTickIntervalMs

    // Still under construction (Building.constructionRemainingMs's doc): excluded here
    // entirely rather than ticked-but-inert, so its damageCooldownMs stays untouched at
    // its fresh default and it doesn't accumulate a free "overdue" hit while inactive.
    val active = state.buildings.filter(_.constructionRemainingMs <= 0.0)
    val forestsTicked =
      active.filter(b => auraBuildingKinds.contains(b.kind)).map(b => tickDamageCooldown(b, deltaMs, intervalFor(b.kind)))
    val towersTicked =
      active.filter(_.kind == BuildingKind.Watchtower).map(b => tickDamageCooldown(b, deltaMs, intervalFor(b.kind)))

    val fromForests = forestsTicked.foldLeft(Map.empty[Long, Double]) { case (acc, (f, fires)) =>
      if fires then accumulateAuraHits(f, state.creatures, auraDamagePerSecFor(f.kind), acc)
      else acc
    }
    val fromTowers = towersTicked.foldLeft(Map.empty[Long, Double]) { case (acc, (w, fires)) =>
      if fires then accumulateWatchtowerHit(w, state.creatures, Balance.WatchtowerDamagePerSec, acc) else acc
    }
    // Angel-only subset of forestsTicked's own aura sources — fromForests above mixes
    // Nature (Forest/Jungle), Loi (Angel), and Mort (PassingGate) damage together for the
    // actual damage total, which doesn't say which building contributed to a given kill.
    // This is purely for the Gold-reward attribution below, not damage math (unaffected).
    val fromLoiAura = forestsTicked.foldLeft(Map.empty[Long, Double]) { case (acc, (f, fires)) =>
      if fires && f.kind == BuildingKind.Angel then accumulateAuraHits(f, state.creatures, auraDamagePerSecFor(f.kind), acc)
      else acc
    }
    val tickedById = (forestsTicked ++ towersTicked).map { case (b, _) => b.id -> b }.toMap
    val buildingsAfterCooldowns = state.buildings.map(b => tickedById.getOrElse(b.id, b))

    val damageByCreature = mergeSum(fromForests, fromTowers)
    val shielded = paladinShieldedIds(state.creatures)
    val paired = soldierPairedIds(state.creatures)
    val damaged = state.creatures.map { c =>
      val raw = damageByCreature.getOrElse(c.id, 0.0)
      // Vampire.md: "Reduit les degats qu'il subit de 50% (mais n'est pas protege par
      // l'aura du Paladin)" — explicitly excluded from Paladin's shield even when
      // standing adjacent to one, and instead gets its own unconditional flat reduction
      // applied to whatever raw damage it takes from any source.
      val afterShield =
        if c.kind != UnitKind.Vampire && shielded.contains(c.id) then
          math.max(0.0, raw - Balance.PaladinAuraDamageReductionPerSec)
        else raw
      // "Rang serre" (Caserne.md) — a Soldier takes reduced damage only while paired
      // with another living Soldier (see soldierPairedIds), unlike Paladin's unconditional
      // self-shield above.
      val afterCloseRanks =
        if c.kind == UnitKind.Soldier && paired.contains(c.id) then
          math.max(0.0, afterShield - Balance.SoldierCloseRanksDamageReductionPerSec)
        else afterShield
      val taken =
        if c.kind == UnitKind.Vampire then afterCloseRanks * (1.0 - Balance.VampireDamageReductionFraction)
        else afterCloseRanks
      c.copy(hp = c.hp - taken)
    }
    val dead = damaged.filter(_.hp <= 0)
    val deaths = dead.map(c => Death(c.id, c.kind, deathCause(c.id, fromForests, fromTowers)))
    // Recherches loyales.md has nothing to say about this (added at the project owner's
    // explicit request alongside the new Gold resource): a kill any of THIS maze's own Loi
    // buildings (Watchtower and/or Angel) contributed damage to this tick earns `state`
    // itself Gold — same "any contribution counts" attribution DeathCause.AuraAndWatchtower
    // already uses for reporting, not "whichever hit was the literal final one." Purely
    // local to `state`: these are its own buildings rewarding it for its own kills, no
    // BattleEngine-level crediting needed (contrast Chaos/Mort's gold, which cross to the
    // *attacker* — see BattleEngine.creditPlunder/creditCorruption).
    val goldFromKills = dead
      .filter(c => fromTowers.contains(c.id) || fromLoiAura.contains(c.id))
      .map(c => if isLargeKill(c.kind) then Balance.LoyalesLargeKillGoldReward else Balance.LoyalesKillGoldReward)
      .sum
    val withoutDead = state.copy(
      buildings = buildingsAfterCooldowns,
      creatures = damaged.filter(_.hp > 0),
      resources =
        if goldFromKills <= 0.0 then state.resources
        else state.resources.updated(Resource.Gold, state.resources.getOrElse(Resource.Gold, 0.0) + goldFromKills)
    )
    (applyPassingGateHarvest(withoutDead, dead, deltaMs), deaths)

  // Minotaur is the only "large" unit today (Vampire/Tree are bigger in some other sense —
  // HP, size — but the vault never calls them out as a size class the way Minotaur.md
  // itself does); easy to extend if a future unit earns the same label.
  private def isLargeKill(kind: UnitKind): Boolean = kind == UnitKind.Minotaur

  // Decrements a damage-dealing building's cooldown by deltaMs and reports whether it
  // fires its one discrete full-rate hit this tick (Balance.DamageTickIntervalMs's doc).
  // `intervalMs` is normally that flat constant, but a Loi building (Watchtower/Angel)
  // gets a shorter one from applyDamageSources' own intervalFor when this maze has
  // researched Recherches loyales — same "attack speed" effect either way, just applied
  // once per firing here rather than baked into a stored value. On firing, the new
  // cooldown carries forward whatever the decrement overshot by (intervalMs + remaining,
  // where remaining is <= 0) instead of resetting to a flat intervalMs, so a building's
  // long-run hit rate stays exactly one per interval regardless of how deltaMs happens to
  // divide it — the same "preserve the phase" approach spawnCountdownMs/flashMs use
  // elsewhere in this domain.
  private def tickDamageCooldown(building: Building, deltaMs: Double, intervalMs: Double): (Building, Boolean) =
    val remaining = building.damageCooldownMs - deltaMs
    if remaining <= 0.0 then (building.copy(damageCooldownMs = intervalMs + remaining), true)
    else (building.copy(damageCooldownMs = remaining), false)

  private def mergeSum(a: Map[Long, Double], b: Map[Long, Double]): Map[Long, Double] =
    b.foldLeft(a) { case (acc, (id, amount)) => acc.updated(id, acc.getOrElse(id, 0.0) + amount) }

  // Portail.md: any creature dying on one of a PassingGate's 4 orthogonally-adjacent cells
  // this tick — regardless of what actually killed it (its own aura, a Watchtower, even a
  // Forest/Angel aura elsewhere reaching the same cell) — earns the owning maze a Gold
  // reward equal to PassingGateHarvestFraction of the total cost of the BUILDING that
  // made that unit (CreatureSpecs.spawningBuilding/BuildingSpecs.all(_).cost), not a share
  // of the maze's own stockpile and not the unit's own plunder value. Every unit kind has
  // *some* spawning building with a nonzero cost (CreatureSpecsTest asserts this), so even
  // a non-plunderer death (Paladin, Wolf, Zombie, ...) now contributes something — unlike
  // scoring off `plunder`, which is empty for most kinds.
  // Two gates both adjacent to the same death each independently harvest it, and each also
  // sets its own flashMs (Building.flashMs's doc) to the UI's kill-flash duration; a gate
  // with no qualifying death nearby this tick just counts flashMs down toward 0 instead,
  // same shape as spawnCountdownMs/frozenMs elsewhere in the domain.
  private def applyPassingGateHarvest(state: MazeState, dead: List[Creature], deltaMs: Double): MazeState =
    val deadByCell = dead.map(c => GridConfig.cellOf(c.pos) -> c.kind)
    val (buildings, goldReward) =
      state.buildings.foldLeft((List.empty[Building], 0.0)) { case ((acc, reward), b) =>
        // Still under construction: no harvest ability yet (Building.constructionRemainingMs's
        // doc) — passed through unchanged rather than fading a flashMs that's still 0 anyway.
        if b.kind != BuildingKind.PassingGate || b.constructionRemainingMs > 0.0 then (b :: acc, reward)
        else
          val adjacent = Pathfinding.neighbors((b.col, b.row)).toSet
          val harvestedKinds = deadByCell.collect { case (cell, kind) if adjacent.contains(cell) => kind }
          if harvestedKinds.nonEmpty then
            val gateReward = harvestedKinds.map { kind =>
              val spawningBuildingCost = BuildingSpecs.all(CreatureSpecs.spawningBuilding(kind)).cost.values.sum
              Balance.PassingGateHarvestFraction * spawningBuildingCost
            }.sum
            (b.copy(flashMs = Balance.PassingGateFlashMs) :: acc, reward + gateReward)
          else (b.copy(flashMs = math.max(0.0, b.flashMs - deltaMs)) :: acc, reward)
      }
    val resources =
      if goldReward > 0 then state.resources.updated(Resource.Gold, state.resources.getOrElse(Resource.Gold, 0.0) + goldReward)
      else state.resources
    state.copy(buildings = buildings.reverse, resources = resources)

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

  // Caserne.md's "Rang serre" — unlike Paladin's shield (any creature standing near a
  // Paladin benefits), this only protects Soldiers, and only while another living Soldier
  // is itself nearby — a pairwise condition on the Soldiers themselves, not a one-way aura
  // from a fixed source kind.
  private def soldierPairedIds(creatures: List[Creature]): Set[Long] =
    val soldiers = creatures.filter(_.kind == UnitKind.Soldier)
    soldiers
      .filter { s =>
        val cell = GridConfig.cellOf(s.pos)
        val nearby = (cell :: Pathfinding.neighbors(cell)).toSet
        soldiers.exists(other => other.id != s.id && nearby.contains(GridConfig.cellOf(other.pos)))
      }
      .map(_.id)
      .toSet

  // Zombie.md/Vampire.md/Ame.md's corruption rates — Corruption.md: "Les unites de cette
  // faction corrompent les batiments qu'elles touchent", no restriction to particular
  // building kinds, so every building in `state` is a fair target the same way Forest's
  // aura hits every creature, not just certain kinds of them.
  private val corruptionRatesPerSec: Map[UnitKind, Double] = Map(
    UnitKind.Zombie -> Balance.ZombieCorruptionPercentPerSec,
    UnitKind.Vampire -> Balance.VampireCorruptionPercentPerSec,
    UnitKind.Soul -> Balance.SoulCorruptionPercentPerSec
  )

  // Mirrors applyDamageSources' aura pattern but building-side: instead of a *building*
  // hitting *creatures* on adjacent cells, a *creature* corrupts *buildings* on adjacent
  // cells. Multiple corrupting creatures near the same building stack (summed, same as
  // multiple Forests would stack aura damage on a creature between them). A building
  // whose corruptionPercent reaches Balance.CorruptionMaxPercent is removed from the
  // maze and reported as a Corrosion for the caller to refund.
  // The third return value — how many buildings *each individual corruptor* is adjacent
  // to this tick — exists solely for Ame.md's heal-the-summoner effect (see
  // healSummoners): every other corruptor (Zombie/Vampire) simply has no `summonedBy` to
  // credit it to, so it's inert for them.
  // Recherches Sombres.md: "Augmente la vitesse de corruption" — every corruptor in
  // `state.creatures` belongs to the attacker (whoever `attackerResearchLevels` represents,
  // same convention as `tick`'s own doc), so a single multiplier read once from there
  // applies uniformly, the same way Recherches loyales' speed boost is purely local to
  // `state`'s own buildings but mirrored onto the *attacking* side here instead.
  private def applyCorruption(
      state: MazeState,
      deltaMs: Double,
      attackerResearchLevels: Map[BuildingKind, Int]
  ): (MazeState, List[Corrosion], Map[Long, Int]) =
    val corruptors = state.creatures.filter(c => corruptionRatesPerSec.contains(c.kind))
    if corruptors.isEmpty then (state, Nil, Map.empty)
    else
      val sombresLevel = attackerResearchLevels.getOrElse(BuildingKind.LaboSombre, 0)
      val sombresMultiplier = 1.0 + ResearchSpecs.all(BuildingKind.LaboSombre).effectAtLevel(sombresLevel)
      val corruptionByCell = corruptors
        .groupMapReduce(c => GridConfig.cellOf(c.pos))(c => corruptionRatesPerSec(c.kind) * sombresMultiplier * deltaMs / 1000.0)(
          _ + _
        )
      val updated = state.buildings.map { b =>
        val hits = Pathfinding.neighbors((b.col, b.row)).flatMap(corruptionByCell.get).sum
        if hits <= 0.0 then b
        else b.copy(corruptionPercent = math.min(Balance.CorruptionMaxPercent, b.corruptionPercent + hits))
      }
      val (destroyed, remaining) = updated.partition(_.corruptionPercent >= Balance.CorruptionMaxPercent)
      val corrosions = destroyed.map(b => Corrosion(b.id, b.kind, b.col, b.row, BuildingSpecs.all(b.kind).cost))
      val buildingCellsBefore = state.buildings.map(b => (b.col, b.row)).toSet
      val hitCountByCreature = corruptors.map { c =>
        val cell = GridConfig.cellOf(c.pos)
        c.id -> Pathfinding.neighbors(cell).count(buildingCellsBefore.contains)
      }.toMap
      (state.copy(buildings = remaining), corrosions, hitCountByCreature)

  // Not from the vault's own numbers — added at the project owner's explicit request (see
  // Balance.GroveCorruptionHealPercentPerSec's doc): each tier heals at its own rate.
  private val natureCorruptionHealPercentPerSec: Map[BuildingKind, Double] = Map(
    BuildingKind.Grove -> Balance.GroveCorruptionHealPercentPerSec,
    BuildingKind.Forest -> Balance.ForestCorruptionHealPercentPerSec,
    BuildingKind.Jungle -> Balance.JungleCorruptionHealPercentPerSec
  )

  // Mirrors applyCorruption's shape but in reverse (reduces corruptionPercent instead of
  // raising it) and building-to-building rather than creature-to-building: a Nature
  // building heals itself and its 8 surrounding buildings (Chebyshev distance <= 1, unlike
  // the 4-orthogonal-neighbor rule everywhere else in this file — self-healing at distance
  // 0 needs the wider metric anyway). Multiple nearby healers stack, same as multiple
  // corrupting creatures do above. Runs after applyCorruption so it can partially (or
  // fully) offset the same tick's corruption increase, but can't save a building that
  // already hit CorruptionMaxPercent and was destroyed this tick — that's resolved first.
  private def healBuildingCorruption(state: MazeState, deltaMs: Double): MazeState =
    val healers = state.buildings.filter(b => natureCorruptionHealPercentPerSec.contains(b.kind))
    if healers.isEmpty || !state.buildings.exists(_.corruptionPercent > 0.0) then state
    else
      state.copy(buildings = state.buildings.map { b =>
        if b.corruptionPercent <= 0.0 then b
        else
          val heal = healers
            .filter(h => chebyshevDistance((h.col, h.row), (b.col, b.row)) <= 1)
            .map(h => natureCorruptionHealPercentPerSec(h.kind) * deltaMs / 1000.0)
            .sum
          if heal <= 0.0 then b else b.copy(corruptionPercent = math.max(0.0, b.corruptionPercent - heal))
      })

  // Ame.md: "Chaque fois qu'elle corrompt un batiment, elle soigne le Necromancien... de 1
  // PV... Si sa corruption touche plusieurs batiments a la fois, elle soigne davantage" —
  // credited to the *specific* Necromancer each Soul was summoned by (Creature.summonedBy),
  // not any Necromancer present in the maze. A Soul whose summoner has already died simply
  // heals no one (summonedBy no longer matches any living creature) — same "lost, not an
  // error" shape as Corrosion crediting a side that has since changed.
  private def healSummoners(state: MazeState, hitsByCreature: Map[Long, Int], deltaMs: Double): MazeState =
    val healPerSummoner = state.creatures
      .filter(c => c.kind == UnitKind.Soul && c.summonedBy.isDefined && hitsByCreature.getOrElse(c.id, 0) > 0)
      .groupMapReduce(_.summonedBy.get)(c => hitsByCreature(c.id) * Balance.SoulHealPerSecPerBuilding * deltaMs / 1000.0)(
        _ + _
      )
    if healPerSummoner.isEmpty then state
    else
      state.copy(creatures = state.creatures.map { c =>
        healPerSummoner.get(c.id) match
          case Some(amount) => c.copy(hp = math.min(c.maxHp, c.hp + amount))
          case None         => c
      })

  // Necromancien.md: "Toutes les 5 secondes, invoque une Ame" — a *creature* spawning
  // another creature directly into the same maze it's currently walking, unlike every
  // building's spawn (which always crosses into the opponent's maze via BattleEngine's
  // deliverUnits). Mirrors advanceSpawnTimers' countdown shape, but appends straight to
  // `state.creatures` and consumes `state.nextId` itself instead of returning a count for
  // the caller to deliver elsewhere.
  private def advanceCreatureSummons(state: MazeState, deltaMs: Double): MazeState =
    val summoners = state.creatures.filter(c => CreatureSpecs.all(c.kind).spawns.isDefined)
    if summoners.isEmpty then state
    else
      val blocked = state.buildingCells
      val (updatedSummoners, newCreatures, nextId) =
        summoners.foldLeft((List.empty[Creature], List.empty[Creature], state.nextId)) {
          case ((accUpdated, accNew, id), summoner) =>
            val summonerSpec = CreatureSpecs.all(summoner.kind)
            val (summonedKind, intervalMs) = summonerSpec.spawns.get
            val remaining = summoner.spawnCountdownMs - deltaMs
            if remaining > 0 then (summoner.copy(spawnCountdownMs = remaining) :: accUpdated, accNew, id)
            else
              val spec = CreatureSpecs.all(summonedKind)
              val spawnPos =
                if summonerSpec.spawnAtNextCell then nextPathCellCenter(summoner, blocked) else summoner.pos
              // Arbre Anime.md: a self-clone (summonedKind == summoner.kind, i.e. a Tree
              // cloning a Tree) is smaller than whatever made it, not just the original —
              // any creature reachable through this chain can keep cloning. A different-
              // kind summon (e.g. Necromancer -> Soul) is unaffected, full size as always.
              val childSizeFraction =
                if summonedKind == summoner.kind then
                  math.max(Balance.TreeMinCloneSizeFraction, summoner.sizeFraction - Balance.TreeCloneSizeStepFraction)
                else 1.0
              val summoned = Creature(
                id,
                spawnPos,
                spec.maxHp * childSizeFraction,
                spec.maxHp * childSizeFraction,
                spec.speedPerMs,
                summonedKind,
                spawnCountdownMs = spec.spawns.map(_._2).getOrElse(0.0),
                summonedBy = Some(summoner.id),
                sizeFraction = childSizeFraction
              )
              (
                summoner.copy(
                  spawnCountdownMs = remaining + intervalMs,
                  frozenMs = summonerSpec.spawnFreezeMs
                ) :: accUpdated,
                summoned :: accNew,
                id + 1
              )
        }
      val summonerIds = summoners.map(_.id).toSet
      val untouched = state.creatures.filterNot(c => summonerIds.contains(c.id))
      state.copy(creatures = untouched ++ updatedSummoners ++ newCreatures, nextId = nextId)

  // Arbre Anime.md: a self-cloning Tree's copy appears one cell further along its own path
  // (not on top of it, like the Necromancer/Soul's same-position summon — see
  // CreatureSpec.spawnAtNextCell) — falls back to the summoner's own position if it's
  // already at the goal or (shouldn't happen, placement guards this) has no route at all.
  private def nextPathCellCenter(summoner: Creature, blocked: Set[(Int, Int)]): Vec2 =
    val currentCell = GridConfig.cellOf(summoner.pos)
    if currentCell == GridConfig.goalCell then summoner.pos
    else
      Pathfinding.shortestPath(currentCell, GridConfig.goalCell, blocked) match
        case Some(path) if path.size > 1 => GridConfig.cellCenter(path(1)._1, path(1)._2)
        case _                           => summoner.pos

  // Exposed (not private) so any other reader of live production rates — the UI's stock
  // display, tooltips — computes the exact same number tick applies, instead of
  // re-deriving `count * rate` by hand and risking it drift out of sync.
  def productionPerSec(state: MazeState, resource: Resource): Double =
    val base = state.buildings
      .filter(_.constructionRemainingMs <= 0.0)
      .groupBy(_.kind)
      .map { case (kind, bs) =>
        bs.size * BuildingSpecs.all(kind).produces.getOrElse(resource, 0.0) *
          researchProductionMultiplier(state, kind, resource)
      }
      .sum
    base * (1.0 + engendreBoost(state, resource))

  // Note sur les laboratoires.md: "Chaque amelioration (recherche) dans un labo augmente sa
  // production de crystal de 75% par rapport au niveau precedent" — a lab's own research
  // level compounds ONLY its own Crystal output, not any other building's production of any
  // other resource. Exposed (not private) so GameApp's per-building tooltip (effectiveRate)
  // shows the exact same number this applies, same reasoning as engendreBoost below.
  def researchProductionMultiplier(state: MazeState, kind: BuildingKind, resource: Resource): Double =
    if resource != Resource.Crystal then 1.0
    else math.pow(1.0 + Balance.LaboCrystalBoostPerResearchLevel, state.researchLevels.getOrElse(kind, 0).toDouble)

  // Engendre.md's resource-generation cycle, keyed by *target* — the resource whose
  // producer-buildings boost `resource`'s own production rate (see Balance.
  // EngendreBoostPerBuilding's doc): Wood's boost comes from Light producers, Fire's from
  // Wood producers, Shadow's from Fire producers, Crystal's from Shadow producers, Light's
  // from Crystal producers — the same 5-cycle Engendre.md itself describes. Deliberately
  // has no Gold entry — Gold isn't part of this cycle at all (see engendreBoost's own
  // None-handling), only the vault's original 5 resources are.
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
  // `.get` (not `.apply`) on engendreSource: produceResources calls this for every
  // Resource.values entry including Gold, which has no entry there at all (see its doc) —
  // an unconditional `Map.apply` would throw NoSuchElementException the instant a maze's
  // resources are ticked, every single tick, since Gold was added to the enum.
  def engendreBoost(state: MazeState, resource: Resource): Double =
    engendreSource.get(resource) match
      case None => 0.0
      case Some(source) =>
        val sourceBuildingCount =
          state.buildings.count(b => BuildingSpecs.all(b.kind).produces.getOrElse(source, 0.0) > 0.0)
        Balance.EngendreBoostPerBuilding * sourceBuildingCount

  // Balance.ConstructionMsPerCostUnit's doc — counts every building's construction timer
  // down toward 0.0, floored there rather than going negative. Runs first in `tick`, ahead
  // of production/spawning/damage, so a building that finishes construction partway
  // through this very tick is already treated as active for the rest of it (see
  // Building.constructionRemainingMs — every gate below reads the just-updated value, not
  // the value from before this tick started).
  private def advanceConstruction(state: MazeState, deltaMs: Double): MazeState =
    if !state.buildings.exists(_.constructionRemainingMs > 0.0) then state
    else
      state.copy(buildings = state.buildings.map { b =>
        if b.constructionRemainingMs <= 0.0 then b
        else b.copy(constructionRemainingMs = math.max(0.0, b.constructionRemainingMs - deltaMs))
      })

  private def produceResources(state: MazeState, deltaMs: Double): MazeState =
    val produced = Resource.values.map(res => res -> productionPerSec(state, res) * deltaMs / 1000.0)
    state.copy(
      resources = produced.foldLeft(state.resources) { case (acc, (res, amount)) =>
        acc.updated(res, acc.getOrElse(res, 0.0) + amount)
      }
    )

  // Recherches chaotiques.md: "Diminue le temps de production des unites" — purely local
  // to `state` (this maze's own buildings), scoped to Chaos-faction spawners ONLY (Cave/
  // Labyrinth — Faction.Chaos); every other spawner (Tomb, BlackCastle, DeathHouse,
  // Stonehenge) always resets to its own plain BuildingSpecs interval below, regardless of
  // this maze's own Chaotiques research level.
  private def advanceSpawnTimers(state: MazeState, deltaMs: Double): (MazeState, Map[UnitKind, Int]) =
    val chaotiquesLevel = state.researchLevels.getOrElse(BuildingKind.LaboDuChaos, 0)
    val chaotiquesReduction = ResearchSpecs.all(BuildingKind.LaboDuChaos).effectAtLevel(chaotiquesLevel)
    def effectiveInterval(kind: BuildingKind, intervalMs: Double): Double =
      if EntityNames.buildingInfo(kind).faction == Faction.Chaos then intervalMs * (1.0 - chaotiquesReduction)
      else intervalMs
    val (buildings, spawned) =
      state.buildings.foldLeft((List.empty[Building], Map.empty[UnitKind, Int])) {
        case ((acc, counts), b) =>
          // Still under construction: frozen, not just skipped-this-tick — its own
          // spawnCountdownMs doesn't advance at all until construction finishes (see
          // Building.constructionRemainingMs's doc).
          if b.constructionRemainingMs > 0.0 then (b :: acc, counts)
          else
            BuildingSpecs.all(b.kind).spawns match
              case None => (b :: acc, counts)
              case Some((unitKind, baseIntervalMs)) =>
                val intervalMs = effectiveInterval(b.kind, baseIntervalMs)
                val remaining = b.spawnCountdownMs - deltaMs
                if remaining <= 0 then
                  (
                    b.copy(spawnCountdownMs = remaining + intervalMs) :: acc,
                    counts.updated(unitKind, counts.getOrElse(unitKind, 0) + 1)
                  )
                else (b.copy(spawnCountdownMs = remaining) :: acc, counts)
      }
    (state.copy(buildings = buildings), spawned)
