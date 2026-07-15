package towerdefense.domain

import towerdefense.domain.geometry.Vec2

// All 5 resources across the vault's factions. Shadow (Mort) and Crystal (Science)
// have no buildings producing/consuming them yet — enumerated now, per explicit
// request, rather than re-enumerating once those factions exist.
enum Resource derives CanEqual:
  case Wood, Fire, Light, Shadow, Crystal

enum UnitKind derives CanEqual:
  case Elf, Goblin, Minotaur, Paladin

enum BuildingKind derives CanEqual:
  case Forest, Cave, Labyrinth, Church, Watchtower

// A unit currently walking this maze. From this maze owner's point of view it's
// always hostile — sent by one of the opponent's buildings. See CreatureSpecs for
// per-kind stats/plunder, and CombatEngine for combat abilities (Paladin's shield,
// Forest's aura, Watchtower's ranged damage), which stay kind-based special cases.
case class Creature(
    id: Long,
    pos: Vec2,
    hp: Double,
    maxHp: Double,
    speedPerMs: Double,
    kind: UnitKind
)

// Replaces the old per-faction Forest/Cave/Labyrinth/Eglise/Watchtower case classes —
// see BuildingSpecs for what each kind costs/produces/spawns. spawnCountdownMs is
// inert (0.0, never read) for kinds whose spec has no `spawns` (only Watchtower today)
// rather than modeled as an Option — cheaper than threading an unwrap through every
// fold/copy site for the sake of one kind out of five.
case class Building(
    id: Long,
    col: Int,
    row: Int,
    kind: BuildingKind,
    spawnCountdownMs: Double
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
    nextId = 1L
  )
