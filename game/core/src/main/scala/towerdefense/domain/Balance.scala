package towerdefense.domain

// Single source of truth for gameplay tuning. Numbers marked "per Forest.md/Elf.md" or
// "per Cave.md/Goblin.md" come straight from the vault's faction definitions
// (Resources/Nature/, Resources/Chaos/); numbers marked "POC default" aren't
// specified there and were chosen for playability, tuned after actually running the game.
object Balance:

  // ── Nature (player) ─────────────────────────────────────────────────────
  val ForestCostWood: Double = 10.0 // Forest.md: "cout en bois: 10"
  val WoodPerSecPerForest: Double = 0.2 // Forest.md: "produit 1 bois / 5 sec"
  val AuraDamagePerSec: Double = 2.0 // Forest.md: Ents deal 2 dmg/sec to adjacent units
  val ElfSpawnIntervalMs: Double = 10_000.0 // Forest.md: "toutes les 10 sec genere un Elf"

  val ElfMaxHp: Double = 5.0
  val ElfSpeedPerMs: Double = 0.05 // POC default: 50 px/s

  // ── Chaos (AI) ───────────────────────────────────────────────────────────
  val CaveCostWood: Double = 5.0 // Cave.md: "cout en wood: 5"
  val CaveCostFire: Double = 10.0 // Cave.md: "cout en fire: 10"
  val FirePerSecPerCave: Double = 0.2 // Cave.md: "produit 1 feu / 5 sec"
  val GoblinSpawnIntervalMs: Double = 5_000.0 // Cave.md: "toutes les 5 sec elle genere un Goblin"

  val GoblinMaxHp: Double = 5.0
  val GoblinSpeedPerMs: Double = 0.05 // POC default, matches Elf

  // Goblin.md: "Pille une ressource de chaque type" — POC default: 1 unit of wood
  // and 1 unit of fire per unit that reaches the goal (Elf included: symmetric, see
  // CLAUDE.md), clamped to what's available.
  val PlunderPerUnit: Double = 1.0

  val LabyrintheCostWood: Double = 20.0 // Labyrinthe.md: "cout en bois: 20"
  val LabyrintheCostFire: Double = 40.0 // Labyrinthe.md: "cout en feu: 40"
  val MinotaurSpawnIntervalMs: Double =
    10_000.0 // Labyrinthe.md: "toutes les 10 secondes genere un Minotaure"

  val MinotaurMaxHp: Double = 50.0 // Minotaure.md: "PV: 50"
  val MinotaurSpeedPerMs: Double = 0.05 // POC default, matches Elf/Goblin

  // Minotaure.md: "Pille 10 ressources de chaque type" — a heavier, slower-to-produce
  // raider than the Goblin's PlunderPerUnit.
  val MinotaurPlunderPerUnit: Double = 10.0

  // ── Loi ──────────────────────────────────────────────────────────────────
  val EgliseCostWood: Double = 40.0 // Eglise.md: "cout en bois: 40"
  val EgliseCostLight: Double = 20.0 // Eglise.md: "cout en lumiere: 20"
  val LightPerSecPerEglise: Double = 1.0 // Eglise.md: "Produit 1 Lumiere par seconde"
  val PaladinSpawnIntervalMs: Double =
    10_000.0 // Eglise.md: "toutes les 10 secondes genere un Paladin"

  val PaladinMaxHp: Double = 50.0 // Paladin.md: "PV: 50"
  val PaladinSpeedPerMs: Double = 0.05 // POC default, matches Elf/Goblin/Minotaur

  // Paladin.md: "Aura: protege les unites adjacentes de 2 degats" — fully cancels the
  // Forest's AuraDamagePerSec (also 2.0) for any adjacent unit, POC default. The
  // Paladin itself has no plunder ability (Paladin.md doesn't mention one, unlike
  // Elf/Goblin/Minotaur) — see CombatEngine.plunderAmounts.
  val PaladinAuraDamageReductionPerSec: Double = 2.0

  // ── Shared / meta ────────────────────────────────────────────────────────

  // POC default, not required to match each other — both mazes still get the identical
  // pair (see CLAUDE.md: symmetry is about player vs AI having the same rules, not
  // about every number being equal to every other number).
  val StartingWood: Double = 50.0
  val StartingFire: Double = 30.0

  // Light has no producer besides the Eglise itself, so without a starting amount at
  // least EgliseCostLight, the very first Eglise could never be built. POC default:
  // just enough for exactly one, same relationship as StartingFire to CaveCostFire.
  val StartingLight: Double = 20.0

  // POC default: wood/fire production compounds with building count, so without a pace
  // limit the AI can tile its maze within seconds. This caps it to roughly a human's
  // tapping speed. Found by actually running the game, not decided upfront.
  val AiBuildCooldownMs: Double = 3_000.0

  // Victoire.md leaves both targets as an unfilled "XX" — POC defaults, tuned to be
  // reachable within a few minutes of play at the rates above. Each is a floor: the
  // actual target is whichever is higher between this floor and
  // VictoryMultiplierOverOpponent times the opponent's own count, so leading by a
  // fixed margin early in the match doesn't win the game outright once the opponent
  // has caught up.
  val NatureVictoryForestTarget: Int =
    10 // Victoire.md, "G: Expansion Inarretable — construire XX batiments"
  val ChaosVictoryPlunderTarget: Double = 50.0 // Victoire.md, "R: Plunder — piller XX ressources"

  // Must double the opponent's own count (not just clear a fixed floor) to win.
  val VictoryMultiplierOverOpponent: Double = 2.0
