package towerdefense.domain

// Single source of truth for gameplay tuning. Numbers marked "per Forest.md/Elf.md" or
// "per Cave.md/Goblin.md" come straight from the vault's faction definitions
// (Resources/Nature/, Resources/Chaos/); numbers marked "POC default" aren't
// specified there and were chosen for playability, tuned after actually running the game.
object Balance:

  // ── Nature (player) ─────────────────────────────────────────────────────
  val ForestCostWood: Double = 10.0 // Forest.md: "cout en wood: 10"
  val WoodPerSecPerForest: Double = 0.2 // Forest.md says 1/sec; tuned down after playtesting the compounding economy
  val AuraDamagePerSec: Double = 2.0 // Forest.md: Ents deal 2 dmg/sec to adjacent units
  val ElfSpawnIntervalMs: Double = 10_000.0 // Forest.md: "toutes les 10 sec genere un Elf"

  val ElfMaxHp: Double = 5.0
  val ElfSpeedPerMs: Double = 0.05 // POC default: 50 px/s

  // ── Chaos (AI) ───────────────────────────────────────────────────────────
  val CaveCostWood: Double = 5.0 // Cave.md: "cout en wood: 5"
  val CaveCostFire: Double = 10.0 // Cave.md: "cout en fire: 10"
  val FirePerSecPerCave: Double = 2.0 // Cave.md: "produit 2 fire/sec"
  val GoblinSpawnIntervalMs: Double = 5_000.0 // Cave.md: "toutes les 5 sec elle genere un Goblin"

  val GoblinMaxHp: Double = 5.0
  val GoblinSpeedPerMs: Double = 0.05 // POC default, matches Elf

  // Goblin.md: "Pille une ressource de chaque type" — POC default: 1 unit of wood
  // and 1 unit of fire per unit that reaches the goal (Elf included: symmetric, see
  // CLAUDE.md), clamped to what's available.
  val PlunderPerUnit: Double = 1.0

  // ── Shared / meta ────────────────────────────────────────────────────────

  // POC default, not required to match each other — both mazes still get the identical
  // pair (see CLAUDE.md: symmetry is about player vs AI having the same rules, not
  // about every number being equal to every other number).
  val StartingWood: Double = 20.0
  val StartingFire: Double = 10.0

  // POC default: wood/fire production compounds with building count, so without a pace
  // limit the AI can tile its maze within seconds. This caps it to roughly a human's
  // tapping speed. Found by actually running the game, not decided upfront.
  val AiBuildCooldownMs: Double = 3_000.0

  // Victoire.md leaves both targets as an unfilled "XX" — POC defaults, tuned to be
  // reachable within a few minutes of play at the rates above.
  val NatureVictoryForestTarget: Int = 10 // Victoire.md, "G: Expansion Inarretable — construire XX batiments"
  val ChaosVictoryPlunderTarget: Double = 20.0 // Victoire.md, "R: Plunder — piller XX ressources"
