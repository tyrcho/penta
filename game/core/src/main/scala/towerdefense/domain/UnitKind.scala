package towerdefense.domain

import towerdefense.domain.economy.*

// Stats and plunder-on-arrival amounts per unit kind — literal per-case fields, the
// data-driven replacement for the old scattered per-kind Balance constants + hardcoded
// plunderAmounts match (see CreatureSpecs, now slimmed to just the unit->spawning-building
// relation and the tier it derives). Combat abilities (Paladin's shield) stay a kind-based
// special case in CombatEngine.
// Zombie/Vampire/Necromancer/Soul (Mort) have no plunder ability — see CombatEngine's
// corruption mechanic. Science (Recherches*.md) has no unit at all in the vault, only
// buildings — see BuildingKind's Science cases.
// spawns: mirrors BuildingKind's identical field, but — unlike BuildingKind's, which is a
// safe `val` constructor parameter (see BuildingKind's own doc) — this can't be a literal
// per-case field at all: Necromancer (Ame.md/Necromancien.md: a *creature* that itself
// periodically spawns another creature into the same maze it's walking, unlike every
// building's spawn which crosses into the opponent's maze — see CombatEngine.
// advanceCreatureSummons/BattleEngine.spawnCreature) needs to reference UnitKind.Soul,
// declared AFTER it (a forward reference to a not-yet-initialized case), and Tree
// (Arbre Anime.md: clones itself) needs to reference itself (a self reference, same
// problem) — Scala 3 enum cases initialize as ordered vals, so either reference would
// resolve to null/fail at class-init time if `spawns` were a `val`. Implemented instead as
// a single shared `def` pattern-matching on `this`, evaluated lazily on each call (by
// which point the whole enum is already fully initialized) — see below, after every case
// is declared.
// spawnFreezeMs: how long this creature stops advancing toward the goal the instant its
// own `spawns` triggers — 0.0 (no freeze) for every kind except Necromancer (Necromancien.
// md: "pendant 1 seconde, il reste immobile" — see CombatEngine.advanceCreatureSummons/
// stepCreature). Meaningless without `spawns` set, but kept as its own field rather than
// folded into the pair so a future summoner without a freeze doesn't need a fake 0.0 there.
// spawnAtNextCell: where the spawned creature appears — false (the summoner's own
// position, e.g. a Soul appearing on top of its Necromancer) for every kind except Tree,
// which clones itself one cell further along its own path instead (Arbre Anime.md — see
// CombatEngine.advanceCreatureSummons's nextPathCellCenter).
// corruptionRatePerSec: Corruption.md's per-second corruption a standing-adjacent creature
// deals to an enemy building (see CombatEngine.applyCorruption) — 0.0 (no corruption
// ability at all) for every kind except Zombie/Vampire/Soul, a plain literal since it's
// just a Balance constant with no cross-kind reference.
enum UnitKind(
    val faction: Faction,
    val maxHp: Double,
    val speedPerMs: Double,
    val plunder: Map[Resource, Double],
    val spawnFreezeMs: Double = 0.0,
    val spawnAtNextCell: Boolean = false,
    val corruptionRatePerSec: Double = 0.0
) derives CanEqual:

  case Elf
      extends UnitKind(
        Faction.Nature,
        Balance.ElfMaxHp,
        Balance.ElfSpeedPerMs,
        plunder = Map(Resource.Wood -> Balance.PlunderPerUnit)
      )
  // Goblin/Minotaur steal Gold directly (project owner's explicit request: "steal gold,
  // not convert") — a real transfer, same shape as Elf's Wood theft: the victim loses
  // Gold, the attacker gains that same Gold, both capped/uncapped the same way
  // (CombatEngine.moveCreatures' stolen/plundered, BattleEngine.creditPlunder). The
  // 2x multiplier keeps the same total value these two used to steal (1 Wood + 1 Fire,
  // 10 + 10 for Minotaur) before that value was Wood/Fire specifically.
  case Goblin
      extends UnitKind(
        Faction.Chaos,
        Balance.GoblinMaxHp,
        Balance.GoblinSpeedPerMs,
        plunder = Map(Resource.Gold -> 2 * Balance.PlunderPerUnit)
      )
  case Minotaur
      extends UnitKind(
        Faction.Chaos,
        Balance.MinotaurMaxHp,
        Balance.MinotaurSpeedPerMs,
        plunder = Map(Resource.Gold -> 2 * Balance.MinotaurPlunderPerUnit)
      )
  // Paladin.md gives it no plunder ability — its value is the shield it provides to
  // adjacent allies, a combat ability that stays outside this enum (see CombatEngine).
  case Paladin
      extends UnitKind(
        Faction.Loi,
        Balance.PaladinMaxHp,
        Balance.PaladinSpeedPerMs,
        plunder = Map.empty
      )
  // Loup.md gives it no plunder ability either — its value is the speed buff it grants
  // nearby allies, a combat ability that stays outside this enum (see CombatEngine).
  case Wolf
      extends UnitKind(
        Faction.Nature,
        Balance.WolfMaxHp,
        Balance.WolfSpeedPerMs,
        plunder = Map.empty
      )
  // Zombie.md/Vampire.md give neither a plunder ability — their value is corrupting
  // adjacent enemy buildings over time (Corruption.md), a combat ability that stays
  // outside this enum (see CombatEngine's corruption handling).
  case Zombie
      extends UnitKind(
        Faction.Mort,
        Balance.ZombieMaxHp,
        Balance.ZombieSpeedPerMs,
        plunder = Map.empty,
        corruptionRatePerSec = Balance.ZombieCorruptionPercentPerSec
      )
  case Vampire
      extends UnitKind(
        Faction.Mort,
        Balance.VampireMaxHp,
        Balance.VampireSpeedPerMs,
        plunder = Map.empty,
        corruptionRatePerSec = Balance.VampireCorruptionPercentPerSec
      )
  // Necromancien.md gives it no plunder ability either — its value is periodically
  // invoking an Ame, a combat ability that stays outside this enum (see CombatEngine's
  // advanceCreatureSummons). See `spawns` below for why this is a shared pattern-match
  // method rather than a per-case constructor argument.
  case Necromancer
      extends UnitKind(
        Faction.Mort,
        Balance.NecromancerMaxHp,
        Balance.NecromancerSpeedPerMs,
        plunder = Map.empty,
        spawnFreezeMs = Balance.NecromancerSummonFreezeMs
      )
  // Ame.md gives it no plunder ability either — its value is corrupting adjacent enemy
  // buildings and healing its summoning Necromancer, a combat ability that stays outside
  // this enum (see CombatEngine's corruption/healSummoners handling).
  case Soul
      extends UnitKind(
        Faction.Mort,
        Balance.SoulMaxHp,
        Balance.SoulSpeedPerMs,
        plunder = Map.empty,
        corruptionRatePerSec = Balance.SoulCorruptionPercentPerSec
      )
  // Arbre Anime.md gives it no plunder ability either — its value is the self-cloning
  // that grows its OWNER's own forest tally even while raiding the opponent
  // (VictoryConditions.forestCount), a combat ability that stays outside this enum
  // (see CombatEngine's advanceCreatureSummons). See `spawns` below for why this is a
  // shared pattern-match method rather than a per-case constructor argument.
  case Tree
      extends UnitKind(
        Faction.Nature,
        Balance.TreeMaxHp,
        Balance.TreeSpeedPerMs,
        plunder = Map.empty,
        spawnFreezeMs = Balance.TreeCloneFreezeMs,
        spawnAtNextCell = true
      )
  // Antre du Dragon.md: a glass-cannon raider — fast, fragile, and its plunder alone is
  // meant to secure (or nearly secure) the Chaos plunder victory in one successful run,
  // same Gold-theft shape as Goblin/Minotaur, just at a much higher magnitude.
  case Dragon
      extends UnitKind(
        Faction.Chaos,
        Balance.DragonMaxHp,
        Balance.DragonSpeedPerMs,
        plunder = Map(Resource.Gold -> Balance.DragonPlunderGold)
      )
  // Caserne.md gives it no plunder ability — its value is surviving combat via "Rang
  // serre" (see CombatEngine.soldierPairedIds/applyDamageSources), a combat ability
  // that stays outside this enum, same as Paladin/Wolf's own abilities above.
  case Soldier
      extends UnitKind(
        Faction.Loi,
        Balance.SoldierMaxHp,
        Balance.SoldierSpeedPerMs,
        plunder = Map.empty
      )
  // Camp de Guerre's tanky counterpart to DragonsLair's glass-cannon Dragon (see
  // Balance.OrcMaxHp's doc) — steals Gold directly, same shape as Goblin/Minotaur, just
  // at a higher per-trip amount to reward the tougher, costlier building that made it.
  case Orc
      extends UnitKind(
        Faction.Chaos,
        Balance.OrcMaxHp,
        Balance.OrcSpeedPerMs,
        plunder = Map(Resource.Gold -> Balance.OrcPlunderPerUnit)
      )

  // Every kind spawns nothing except Necromancer (a Soul) and Tree (a clone of itself) —
  // see this enum's own doc for why this is a shared pattern-match method instead of a
  // per-case constructor argument (both reference a UnitKind case unsafe to forward/self
  // reference from inside a `val` at case-construction time).
  def spawns: Option[(UnitKind, Double)] = this match
    case UnitKind.Necromancer => Some(UnitKind.Soul -> Balance.SoulSummonIntervalMs)
    case UnitKind.Tree        => Some(UnitKind.Tree -> Balance.TreeCloneIntervalMs)
    case _                    => None

  // Which building "made" this unit kind — used by CombatEngine.applyPassingGateHarvest to
  // scale a dying unit's harvest off the cost of what produced it, not off `plunder`
  // (which is empty for most kinds — Paladin, Wolf, Zombie, ... — that would otherwise
  // never contribute anything to a nearby Passing Gate), and by `tier` below. Picks the
  // BASE tier when an upgrade chain spawns the same kind at multiple tiers (Elf: Grove,
  // even though upgrading to Forest keeps spawning Elf too — same convention
  // EntityText.unitBodies' spawnedByLine already uses for Elf's wiki page), and traces a
  // creature-to-creature spawn back to that summoner's own building (Soul is summoned by a
  // Necromancer, never by a building directly, so it maps to DeathHouse — the building
  // that made the Necromancer that then made it).
  // A `def` pattern-matching on `this`, not a literal per-case constructor argument like
  // BuildingKind.spawns' own reverse direction: BuildingKind.spawns already references
  // UnitKind cases as a safe `val` (a cross-enum reference, fine on ITS OWN — see
  // BuildingKind's own doc), but this field points the opposite way, UnitKind -> BuildingKind.
  // Making BOTH directions literal `val`s at once would create a genuine circular
  // initialization dependency between the two enums' companion objects (whichever
  // enum's class loads first would trigger the other's load mid-construction, and the
  // reentrant reference back to the first enum's still-being-built case would see null) —
  // so this stays a lazily-evaluated `def`, safe because by the time anything calls it,
  // both enums have long since finished initializing.
  def producedFrom: Option[BuildingKind] = this match
    case UnitKind.Elf         => Some(BuildingKind.Grove)
    case UnitKind.Goblin      => Some(BuildingKind.Cave)
    case UnitKind.Minotaur    => Some(BuildingKind.Labyrinth)
    case UnitKind.Paladin     => Some(BuildingKind.Church)
    case UnitKind.Wolf        => Some(BuildingKind.Jungle)
    case UnitKind.Zombie      => Some(BuildingKind.Tomb)
    case UnitKind.Vampire     => Some(BuildingKind.BlackCastle)
    case UnitKind.Necromancer => Some(BuildingKind.DeathHouse)
    case UnitKind.Soul        => Some(BuildingKind.DeathHouse)
    case UnitKind.Tree        => Some(BuildingKind.Stonehenge)
    case UnitKind.Dragon      => Some(BuildingKind.DragonsLair)
    case UnitKind.Soldier     => Some(BuildingKind.Barracks)
    case UnitKind.Orc         => Some(BuildingKind.WarCamp)

  // Not its own design value — always its producing building's own tier, since a unit's
  // "power level" is really what its spawning building paid for, not a separately-tuned
  // number. Every kind actually has a producedFrom (see the test asserting so), but the
  // fallback keeps this total rather than partial.
  def tier: Int = producedFrom.fold(0)(_.tier)
