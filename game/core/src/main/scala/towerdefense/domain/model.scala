package towerdefense.domain

import towerdefense.domain.geometry.Vec2

enum UnitKind derives CanEqual:
  case Elf, Goblin

// A unit currently walking this maze. From this maze owner's point of view it's
// always hostile — it's either an Elf (Nature) or a Goblin (Chaos) sent by the
// opponent's building. Only Goblin does anything special on arrival (plunder).
case class Enemy(
    id: Long,
    pos: Vec2,
    hp: Double,
    maxHp: Double,
    speedPerMs: Double,
    kind: UnitKind
)

case class Forest(
    id: Long,
    col: Int,
    row: Int,
    elfSpawnInMs: Double // countdown to the next Elf sent to the opponent's maze
)

case class Cave(
    id: Long,
    col: Int,
    row: Int,
    goblinSpawnInMs: Double // countdown to the next Goblin sent to the opponent's maze
)

// One player's maze: grid, economy and units currently walking it. A battle is two of these.
case class MazeState(
    enemies: List[Enemy],
    forests: List[Forest],
    caves: List[Cave],
    wood: Double,
    fire: Double,
    resourcesPlundered: Double, // this maze's own progress toward the Chaos victory condition
    nextId: Long
):
  // Cells occupied by any building — the single source of truth for both pathfinding
  // obstacles (CombatEngine/Placement) and rendering (GameApp), so it's only ever defined once.
  def buildingCells: Set[(Int, Int)] =
    forests.map(f => (f.col, f.row)).toSet ++ caves.map(c => (c.col, c.row))

object MazeState:
  val initial: MazeState = MazeState(
    enemies = Nil,
    forests = Nil,
    caves = Nil,
    wood = Balance.StartingWood,
    fire = Balance.StartingFire,
    resourcesPlundered = 0.0,
    nextId = 1L
  )
