package towerdefense.domain

// Stats and plunder-on-arrival amounts per unit kind — the data-driven replacement for
// the old scattered per-kind Balance constants + hardcoded plunderAmounts match. Combat
// abilities (Paladin's shield) stay a kind-based special case in CombatEngine.
// spawns: mirrors BuildingSpec's identical field — None for every kind except Necromancer
// (Ame.md/Necromancien.md: a *creature* that itself periodically spawns another creature
// into the same maze it's walking, unlike every building's spawn which crosses into the
// opponent's maze — see CombatEngine.advanceCreatureSummons/BattleEngine.spawnCreature).
// spawnFreezeMs: how long this creature stops advancing toward the goal the instant its
// own `spawns` triggers — 0.0 (no freeze) for every kind except Necromancer (Necromancien.
// md: "pendant 1 seconde, il reste immobile" — see CombatEngine.advanceCreatureSummons/
// stepCreature). Meaningless without `spawns` set, but kept as its own field rather than
// folded into the pair so a future summoner without a freeze doesn't need a fake 0.0 there.
// spawnAtNextCell: where the spawned creature appears — false (the summoner's own
// position, e.g. a Soul appearing on top of its Necromancer) for every kind except Tree,
// which clones itself one cell further along its own path instead (Arbre Anime.md — see
// CombatEngine.advanceCreatureSummons's nextPathCellCenter).
case class CreatureSpec(
    maxHp: Double,
    speedPerMs: Double,
    plunder: Map[Resource, Double],
    spawns: Option[(UnitKind, Double)] = None,
    spawnFreezeMs: Double = 0.0,
    spawnAtNextCell: Boolean = false
)

object CreatureSpecs:
  val all: Map[UnitKind, CreatureSpec] = Map(
    UnitKind.Elf -> CreatureSpec(
      Balance.ElfMaxHp,
      Balance.ElfSpeedPerMs,
      plunder = Map(Resource.Wood -> Balance.PlunderPerUnit)
    ),
    // Goblin/Minotaur steal Gold directly (project owner's explicit request: "steal gold,
    // not convert") — a real transfer, same shape as Elf's Wood theft: the victim loses
    // Gold, the attacker gains that same Gold, both capped/uncapped the same way
    // (CombatEngine.moveCreatures' stolen/plundered, BattleEngine.creditPlunder). The
    // 2x multiplier keeps the same total value these two used to steal (1 Wood + 1 Fire,
    // 10 + 10 for Minotaur) before that value was Wood/Fire specifically.
    UnitKind.Goblin -> CreatureSpec(
      Balance.GoblinMaxHp,
      Balance.GoblinSpeedPerMs,
      plunder = Map(Resource.Gold -> 2 * Balance.PlunderPerUnit)
    ),
    UnitKind.Minotaur -> CreatureSpec(
      Balance.MinotaurMaxHp,
      Balance.MinotaurSpeedPerMs,
      plunder = Map(Resource.Gold -> 2 * Balance.MinotaurPlunderPerUnit)
    ),
    // Paladin.md gives it no plunder ability — its value is the shield it provides to
    // adjacent allies, a combat ability that stays outside this spec (see CombatEngine).
    UnitKind.Paladin -> CreatureSpec(Balance.PaladinMaxHp, Balance.PaladinSpeedPerMs, plunder = Map.empty),
    // Loup.md gives it no plunder ability either — its value is the speed buff it grants
    // nearby allies, a combat ability that stays outside this spec (see CombatEngine).
    UnitKind.Wolf -> CreatureSpec(Balance.WolfMaxHp, Balance.WolfSpeedPerMs, plunder = Map.empty),
    // Zombie.md/Vampire.md give neither a plunder ability — their value is corrupting
    // adjacent enemy buildings over time (Corruption.md), a combat ability that stays
    // outside this spec (see CombatEngine's corruption handling).
    UnitKind.Zombie -> CreatureSpec(Balance.ZombieMaxHp, Balance.ZombieSpeedPerMs, plunder = Map.empty),
    UnitKind.Vampire -> CreatureSpec(Balance.VampireMaxHp, Balance.VampireSpeedPerMs, plunder = Map.empty),
    // Necromancien.md gives it no plunder ability either — its value is periodically
    // invoking an Ame, a combat ability that stays outside this spec (see CombatEngine's
    // advanceCreatureSummons).
    UnitKind.Necromancer -> CreatureSpec(
      Balance.NecromancerMaxHp,
      Balance.NecromancerSpeedPerMs,
      plunder = Map.empty,
      spawns = Some(UnitKind.Soul -> Balance.SoulSummonIntervalMs),
      spawnFreezeMs = Balance.NecromancerSummonFreezeMs
    ),
    // Ame.md gives it no plunder ability either — its value is corrupting adjacent enemy
    // buildings and healing its summoning Necromancer, a combat ability that stays outside
    // this spec (see CombatEngine's corruption/healSummoners handling).
    UnitKind.Soul -> CreatureSpec(Balance.SoulMaxHp, Balance.SoulSpeedPerMs, plunder = Map.empty),
    // Arbre Anime.md gives it no plunder ability either — its value is the self-cloning
    // that grows its OWNER's own forest tally even while raiding the opponent
    // (VictoryConditions.forestCount), a combat ability that stays outside this spec
    // (see CombatEngine's advanceCreatureSummons).
    UnitKind.Tree -> CreatureSpec(
      Balance.TreeMaxHp,
      Balance.TreeSpeedPerMs,
      plunder = Map.empty,
      spawns = Some(UnitKind.Tree -> Balance.TreeCloneIntervalMs),
      spawnFreezeMs = Balance.TreeCloneFreezeMs,
      spawnAtNextCell = true
    ),
    // Antre du Dragon.md: a glass-cannon raider — fast, fragile, and its plunder alone is
    // meant to secure (or nearly secure) the Chaos plunder victory in one successful run,
    // same Gold-theft shape as Goblin/Minotaur, just at a much higher magnitude.
    UnitKind.Dragon -> CreatureSpec(
      Balance.DragonMaxHp,
      Balance.DragonSpeedPerMs,
      plunder = Map(Resource.Gold -> Balance.DragonPlunderGold)
    ),
    // Caserne.md gives it no plunder ability — its value is surviving combat via "Rang
    // serre" (see CombatEngine.soldierPairedIds/applyDamageSources), a combat ability
    // that stays outside this spec, same as Paladin/Wolf's own abilities above.
    UnitKind.Soldier -> CreatureSpec(Balance.SoldierMaxHp, Balance.SoldierSpeedPerMs, plunder = Map.empty)
  )

  // Which building "made" each unit kind — used by CombatEngine.applyPassingGateHarvest to
  // scale a dying unit's harvest off the cost of what produced it, not off `plunder`
  // (which is empty for most kinds — Paladin, Wolf, Zombie, ... — that would otherwise
  // never contribute anything to a nearby Passing Gate). The inverse of BuildingSpecs.
  // all(_).spawns, picking the BASE tier when an upgrade chain spawns the same kind at
  // multiple tiers (Elf: Grove, even though upgrading to Forest keeps spawning Elf too —
  // same convention EntityText.unitBodies' spawnedByLine already uses for Elf's wiki
  // page), and tracing a creature-to-creature spawn back to that summoner's own building
  // (Soul is summoned by a Necromancer, never by a building directly, so it maps to
  // DeathHouse — the building that made the Necromancer that then made it).
  val spawningBuilding: Map[UnitKind, BuildingKind] = Map(
    UnitKind.Elf -> BuildingKind.Grove,
    UnitKind.Goblin -> BuildingKind.Cave,
    UnitKind.Minotaur -> BuildingKind.Labyrinth,
    UnitKind.Paladin -> BuildingKind.Church,
    UnitKind.Wolf -> BuildingKind.Jungle,
    UnitKind.Zombie -> BuildingKind.Tomb,
    UnitKind.Vampire -> BuildingKind.BlackCastle,
    UnitKind.Necromancer -> BuildingKind.DeathHouse,
    UnitKind.Soul -> BuildingKind.DeathHouse,
    UnitKind.Tree -> BuildingKind.Stonehenge,
    UnitKind.Dragon -> BuildingKind.DragonsLair,
    UnitKind.Soldier -> BuildingKind.Barracks
  )
