package towerdefense.domain

import towerdefense.domain.geometry.Vec2

enum UnitKind derives CanEqual:
  case Elf, Goblin, Minotaur, Paladin

// A unit currently walking this maze. From this maze owner's point of view it's
// always hostile — it's an Elf (Nature), a Goblin/Minotaur (Chaos), or a Paladin
// (Loi) sent by the opponent's building. Goblin/Minotaur plunder on arrival; the
// Paladin doesn't (Paladin.md gives it no plunder ability) — instead it shields
// adjacent allied units from Forest aura damage while in transit (see CombatEngine).
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

case class Labyrinth(
    id: Long,
    col: Int,
    row: Int,
    minotaurSpawnInMs: Double // countdown to the next Minotaur sent to the opponent's maze
)

case class Eglise(
    id: Long,
    col: Int,
    row: Int,
    paladinSpawnInMs: Double // countdown to the next Paladin sent to the opponent's maze
)

// One player's maze: grid, economy and units currently walking it. A battle is two of these.
case class MazeState(
    enemies: List[Enemy],
    forests: List[Forest],
    caves: List[Cave],
    labyrinths: List[Labyrinth],
    eglises: List[Eglise],
    wood: Double,
    fire: Double,
    light: Double,
    resourcesPlundered: Double, // this maze's own progress toward the Chaos victory condition
    nextId: Long
):
  // Cells occupied by any building — the single source of truth for both pathfinding
  // obstacles (CombatEngine/Placement) and rendering (GameApp), so it's only ever defined once.
  def buildingCells: Set[(Int, Int)] =
    forests.map(f => (f.col, f.row)).toSet ++ caves.map(c => (c.col, c.row)) ++ labyrinths.map(l =>
      (l.col, l.row)
    ) ++ eglises.map(e => (e.col, e.row))

object MazeState:
  val initial: MazeState = MazeState(
    enemies = Nil,
    forests = Nil,
    caves = Nil,
    labyrinths = Nil,
    eglises = Nil,
    wood = Balance.StartingWood,
    fire = Balance.StartingFire,
    light = Balance.StartingLight,
    resourcesPlundered = 0.0,
    nextId = 1L
  )
