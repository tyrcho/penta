package towerdefense.domain

import towerdefense.domain.geometry.Vec2

// All 5 resources across the vault's factions.
enum Resource derives CanEqual:
  case Wood, Fire, Light, Shadow, Crystal

// Zombie/Vampire/Necromancer/Soul (Mort) have no plunder ability — see CreatureSpecs and
// CombatEngine's corruption mechanic. Science (Recherches*.md) has no unit at all in the
// vault, only buildings — see BuildingKind's Science cases.
enum UnitKind derives CanEqual:
  case Elf, Goblin, Minotaur, Paladin, Wolf, Zombie, Vampire, Necromancer, Soul, Tree

// Grove/Forest/Jungle form Nature's upgrade chain (Bosquet.md/Foret.md/Jungle.md) — only
// Grove is directly buildable; Forest and Jungle are reached by upgrading an existing
// Grove/Forest in place (see BuildingSpecs.upgradesTo, Placement.tryUpgradeBuilding).
//
// Tomb/BlackCastle (Tombe.md/Chateau Noir.md) are Mort's pair, mirroring Cave/Labyrinth's
// shape (two independently-buildable tiers, not an upgrade chain). PassingGate (Portail.md)
// is a third, independently-costed Mort building — a Loi/Mort-flavored hybrid cost (see
// BuildingSpecs), dealing its own aura damage like Forest/Jungle/Angel and, uniquely,
// harvesting Shadow from any nearby death regardless of what killed it (see CombatEngine's
// applyPassingGateHarvest and Building.flashMs).
//
// The five Labo* kinds are Science's buildings (Labo Naturel/Sombre/de Recherche/de la
// Loi/du Chaos) — Crystal producers here; the leveled research tree, its global modifiers,
// and its victory condition (Recherche fondamentale) all live on MazeState.researchLevels/
// VictoryConditions instead, see BuildingSpecs' doc.
enum BuildingKind derives CanEqual:
  case Grove, Forest, Jungle, Stonehenge, Cave, Labyrinth, Church, Watchtower, Angel, Tomb, BlackCastle,
    DeathHouse, PassingGate, LaboNaturel, LaboSombre, LaboDeRecherche, LaboDeLaLoi, LaboDuChaos

// A unit currently walking this maze. From this maze owner's point of view it's
// always hostile — sent by one of the opponent's buildings. See CreatureSpecs for
// per-kind stats/plunder, and CombatEngine for combat abilities (Paladin's shield,
// Forest's aura, Watchtower's ranged damage), which stay kind-based special cases.
// spawnCountdownMs: inert (0.0) for every kind whose CreatureSpec has no `spawns` (see
// CombatEngine.advanceCreatureSummons) — same "inert field, cheap to carry" choice as
// Building.spawnCountdownMs.
// summonedBy: the id of the creature that invoked this one via CreatureSpec.spawns (a
// Soul's Necromancer — see Ame.md — or a Tree's parent clone — see Arbre Anime.md) — None
// for a creature that arrived via a building's own spawn instead (every other kind, and
// an "original" Tree). Used to credit Ame.md's heal to the *specific* Necromancer that
// summoned a given Soul, not any Necromancer present.
// frozenMs: how much longer this creature is rooted in place, not advancing toward the
// goal — set by CombatEngine.advanceCreatureSummons the instant a summon triggers (see
// CreatureSpec.spawnFreezeMs), inert (0.0) for every kind whose spec has no freeze.
// sizeFraction: what fraction of its kind's base maxHp/render size this particular
// creature has — 1.0 (full size) for every kind except a self-cloned Tree (Arbre Anime.md:
// each clone is TreeCloneSizeStepFraction smaller than the parent that made it, floored at
// TreeMinCloneSizeFraction — see CombatEngine.advanceCreatureSummons).
case class Creature(
    id: Long,
    pos: Vec2,
    hp: Double,
    maxHp: Double,
    speedPerMs: Double,
    kind: UnitKind,
    spawnCountdownMs: Double = 0.0,
    summonedBy: Option[Long] = None,
    frozenMs: Double = 0.0,
    sizeFraction: Double = 1.0
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
// flashMs (Portail.md's PassingGate only): counts down from Balance.PassingGateFlashMs
// whenever a creature dies adjacent to this gate (see CombatEngine.applyPassingGateHarvest)
// — purely a UI cue (GameApp.scala tints the sprite while it's positive), read by nothing
// else in the domain. Inert (0.0, never set) for every other kind, same reasoning as
// spawnCountdownMs/corruptionPercent above.
case class Building(
    id: Long,
    col: Int,
    row: Int,
    kind: BuildingKind,
    spawnCountdownMs: Double,
    corruptionPercent: Double = 0.0,
    flashMs: Double = 0.0
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
