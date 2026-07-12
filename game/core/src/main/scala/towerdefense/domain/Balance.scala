package towerdefense.domain

// Single source of truth for POC gameplay tuning.
object Balance:
  val TowerCost: Int = 20
  val EnemyReward: Int = 5
  val SpawnIntervalMs: Double = 2000.0

  val EnemyMaxHp: Double = 20.0
  val EnemySpeedPerMs: Double = 0.05 // 50 px/s

  val TowerRangePx: Double = 140.0
  val TowerDamage: Double = 8.0
  val TowerCooldownMs: Double = 600.0

  val ProjectileSpeedPerMs: Double = 0.3 // 300 px/s
