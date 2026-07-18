package towerdefense.domain

import towerdefense.domain.geometry.Vec2

// All 5 resources across the vault's factions.
enum Resource derives CanEqual:
  case Wood, Fire, Light, Shadow, Crystal

// Zombie/Vampire/Necromancer/Soul (Mort) have no plunder ability — see CreatureSpecs and
// CombatEngine's corruption mechanic. Science (Recherches*.md) has no unit at all in the
// vault, only buildings — see BuildingKind's Science cases.
enum UnitKind derives CanEqual:
  case Elf, Goblin, Minotaur, Paladin, Wolf, Zombie, Vampire, Necromancer, Soul

// Grove/Forest/Jungle form Nature's upgrade chain (Bosquet.md/Foret.md/Jungle.md) — only
// Grove is directly buildable; Forest and Jungle are reached by upgrading an existing
// Grove/Forest in place (see BuildingSpecs.upgradesTo, Placement.tryUpgradeBuilding).
//
// Tomb/BlackCastle (Tombe.md/Chateau Noir.md) are Mort's pair, mirroring Cave/Labyrinth's
// shape (two independently-buildable tiers, not an upgrade chain).
//
// The five Labo* kinds are Science's buildings (Labo Naturel/Sombre/de Recherche/de la
// Loi/du Chaos) — Crystal producers only in this pass; see BuildingSpecs' doc for what's
// deliberately not implemented yet (the leveled research tree, its global modifiers, and
// Science's victory condition).
enum BuildingKind derives CanEqual:
  case Grove, Forest, Jungle, Cave, Labyrinth, Church, Watchtower, Angel, Tomb, BlackCastle,
    DeathHouse, LaboNaturel, LaboSombre, LaboDeRecherche, LaboDeLaLoi, LaboDuChaos

// A unit currently walking this maze. From this maze owner's point of view it's
// always hostile — sent by one of the opponent's buildings. See CreatureSpecs for
// per-kind stats/plunder, and CombatEngine for combat abilities (Paladin's shield,
// Forest's aura, Watchtower's ranged damage), which stay kind-based special cases.
// spawnCountdownMs: inert (0.0) for every kind except Necromancer (see CreatureSpec.spawns
// and CombatEngine.advanceCreatureSummons) — same "inert field, cheap to carry" choice as
// Building.spawnCountdownMs.
// summonedBy: the id of the Necromancer that invoked this creature (only ever set for a
// Soul — see Ame.md) — None for every other kind, including Necromancer itself (it's
// spawned by a building, not another creature). Used solely to credit Ame.md's heal to
// the *specific* Necromancer that summoned this Soul, not any Necromancer present.
// frozenMs: how much longer this creature is rooted in place, not advancing toward the
// goal — set by CombatEngine.advanceCreatureSummons the instant a summon triggers (see
// CreatureSpec.spawnFreezeMs), inert (0.0) for every kind whose spec has no freeze.
case class Creature(
    id: Long,
    pos: Vec2,
    hp: Double,
    maxHp: Double,
    speedPerMs: Double,
    kind: UnitKind,
    spawnCountdownMs: Double = 0.0,
    summonedBy: Option[Long] = None,
    frozenMs: Double = 0.0
)

// Replaces the old per-faction Forest/Cave/Labyrinth/Eglise/Watchtower case classes —
// see BuildingSpecs for what each kind costs/produces/spawns. spawnCountdownMs is
// inert (0.0, never read) for kinds whose spec has no `spawns` (only Watchtower and the
// Science labs today) rather than modeled as an Option — cheaper than threading an
// unwrap through every fold/copy site for the sake of a few kinds out of many.
// corruptionPercent (Corruption.md, Mort's mechanic — see CombatEngine): 0-100, how far a
// Zombie/Vampire standing adjacent has corrupted this building; defaults to 0.0 so every
// existing call site (none of which involves Mort) is unaffected. Inert for any maze this
// faction never touches, same reasoning as spawnCountdownMs above.
case class Building(
    id: Long,
    col: Int,
    row: Int,
    kind: BuildingKind,
    spawnCountdownMs: Double,
    corruptionPercent: Double = 0.0
)

// One player's maze: grid, economy and units currently walking it. A battle is two of these.
case class MazeState(
    creatures: List[Creature],
    buildings: List[Building],
    resources: Map[Resource, Double],
    resourcesPlundered: Double, // this maze's own progress toward the Chaos victory
                                // condition — deliberately cross-resource (Elf/Goblin/
                                // Minotaur plunder different resource combinations into
                                // the same tally), so it stays a flat Double rather than
                                // living inside `resources`.
    buildingsCorrupted: Double = 0.0, // this maze's own progress toward the Mort victory
                                       // condition (Corruption.md/Victoire.md "B") — counts
                                       // enemy buildings this maze's Zombies/Vampires have
                                       // corrupted to 100% and destroyed, symmetric in shape
                                       // to resourcesPlundered above (see CombatEngine/
                                       // BattleEngine's corruption handling).
    // Science's leveled research (Recherches*.md/Recherche fondamentale.md) — keyed by the
    // five Labo* BuildingKinds, absent/0 meaning "not researched". Placement.tryResearch is
    // the only way this advances, gated on owning that lab (see its doc); a level, once
    // reached, persists even if the lab is later destroyed — POC interpretation, since the
    // vault doesn't say whether losing the building should erase accumulated research.
    // See ResearchSpecs for what each level costs/does.
    researchLevels: Map[BuildingKind, Int] = Map.empty,
    nextId: Long
):
  // Cells occupied by any building — the single source of truth for both pathfinding
  // obstacles (CombatEngine/Placement) and rendering (GameApp), so it's only ever defined once.
  def buildingCells: Set[(Int, Int)] = buildings.map(b => (b.col, b.row)).toSet

object MazeState:
  val initial: MazeState = MazeState(
    creatures = Nil,
    buildings = Nil,
    resources = Balance.StartingResources,
    resourcesPlundered = 0.0,
    buildingsCorrupted = 0.0,
    nextId = 1L
  )
