package towerdefense.domain

// Stats and plunder-on-arrival amounts per unit kind — the data-driven replacement for
// the old scattered per-kind Balance constants + hardcoded plunderAmounts match. Combat
// abilities (Paladin's shield) stay a kind-based special case in CombatEngine.
case class CreatureSpec(maxHp: Double, speedPerMs: Double, plunder: Map[Resource, Double])

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
    UnitKind.Vampire -> CreatureSpec(Balance.VampireMaxHp, Balance.VampireSpeedPerMs, plunder = Map.empty)
  )
