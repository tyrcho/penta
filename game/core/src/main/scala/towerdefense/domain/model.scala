package towerdefense.domain

import towerdefense.domain.geometry.Vec2

// A unit currently walking this maze. From this maze owner's point of view it's
// always hostile — it's either a generic intruder or an Elfe sent by the opponent's Foret.
case class Enemy(
  id: Long,
  pos: Vec2,
  hp: Double,
  maxHp: Double,
  speedPerMs: Double,
)

case class Foret(
  id: Long,
  col: Int,
  row: Int,
  elfeSpawnInMs: Double, // countdown to the next Elfe sent to the opponent's maze
)

// One player's maze: grid, economy and units currently walking it. A battle is two of these.
case class MazeState(
  enemies: List[Enemy],
  forets: List[Foret],
  bois: Double,
  lives: Int,
  nextId: Long,
)

object MazeState:
  // Starting stipend covers exactly one Foret (POC default, not specified in the
  // vault) — otherwise neither side could ever afford the first Foret that would
  // start their wood production.
  val initial: MazeState = MazeState(
    enemies = Nil,
    forets = Nil,
    bois = Balance.ForetCostBois,
    lives = 10,
    nextId = 1L,
  )
