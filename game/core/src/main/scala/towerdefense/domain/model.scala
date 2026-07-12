package towerdefense.domain

import towerdefense.domain.geometry.Vec2

case class Enemy(
  id: Long,
  pos: Vec2,
  hp: Double,
  maxHp: Double,
  speedPerMs: Double,
)

case class Tower(
  id: Long,
  col: Int,
  row: Int,
  rangePx: Double,
  damage: Double,
  cooldownMs: Double,
  reloadMs: Double, // time until next shot is ready; 0 = ready now
)

case class Projectile(
  id: Long,
  targetId: Long,
  pos: Vec2,
  speedPerMs: Double,
  damage: Double,
)

case class GameState(
  enemies: List[Enemy],
  towers: List[Tower],
  projectiles: List[Projectile],
  gold: Int,
  lives: Int,
  elapsedMs: Double,
  nextSpawnAtMs: Double,
  nextId: Long,
)

object GameState:
  val initial: GameState = GameState(
    enemies = Nil,
    towers = Nil,
    projectiles = Nil,
    gold = 100,
    lives = 10,
    elapsedMs = 0.0,
    nextSpawnAtMs = Balance.SpawnIntervalMs,
    nextId = 1L,
  )
