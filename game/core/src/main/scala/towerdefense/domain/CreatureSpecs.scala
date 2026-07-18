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
case class CreatureSpec(
    maxHp: Double,
    speedPerMs: Double,
    plunder: Map[Resource, Double],
    spawns: Option[(UnitKind, Double)] = None,
    spawnFreezeMs: Double = 0.0
)

object CreatureSpecs:
  val all: Map[UnitKind, CreatureSpec] = Map(
    UnitKind.Elf -> CreatureSpec(
      Balance.ElfMaxHp,
      Balance.ElfSpeedPerMs,
      plunder = Map(Resource.Wood -> Balance.PlunderPerUnit)
    ),
    UnitKind.Goblin -> CreatureSpec(
      Balance.GoblinMaxHp,
      Balance.GoblinSpeedPerMs,
      plunder = Map(Resource.Wood -> Balance.PlunderPerUnit, Resource.Fire -> Balance.PlunderPerUnit)
    ),
    UnitKind.Minotaur -> CreatureSpec(
      Balance.MinotaurMaxHp,
      Balance.MinotaurSpeedPerMs,
      plunder = Map(
        Resource.Wood -> Balance.MinotaurPlunderPerUnit,
        Resource.Fire -> Balance.MinotaurPlunderPerUnit
      )
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
    UnitKind.Soul -> CreatureSpec(Balance.SoulMaxHp, Balance.SoulSpeedPerMs, plunder = Map.empty)
  )
