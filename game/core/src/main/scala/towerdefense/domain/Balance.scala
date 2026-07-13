package towerdefense.domain

// Single source of truth for gameplay tuning. Numbers marked "per Foret.md/Elfe.md"
// come straight from the vault's Nature definitions (Resources/Nature/); numbers
// marked "POC default" aren't specified there and were chosen for playability.
object Balance:
  val ForetCostBois: Double = 10.0 // Foret.md: "cout en bois: 10"
  val WoodPerSecPerForet: Double = 1.0 // Foret.md: "produit 1 bois/sec"
  val AuraDamagePerSec: Double = 2.0 // Foret.md: Ents deal 2 dmg/sec to adjacent units
  val ElfeSpawnIntervalMs: Double = 10_000.0 // Foret.md: "toutes les 10 sec genere un Elfe"

  val ElfeMaxHp: Double = 10.0 // Elfe.md, per user clarification
  val ElfeSpeedPerMs: Double = 0.05 // POC default: 50 px/s

  // POC default: bois production compounds with Foret count, so without a pace
  // limit the AI can tile its maze within seconds. This caps it to roughly a
  // human's tapping speed.
  val AiBuildCooldownMs: Double = 3_000.0

