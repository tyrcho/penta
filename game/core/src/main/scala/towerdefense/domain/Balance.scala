package towerdefense.domain

// Single source of truth for gameplay tuning. Numbers marked "per Forest.md/Elf.md" or
// "per Cave.md/Goblin.md" come straight from the vault's faction definitions
// (Resources/Nature/, Resources/Chaos/); numbers marked "POC default" aren't
// specified there and were chosen for playability, tuned after actually running the game.
object Balance:

  // ── Nature (player) ─────────────────────────────────────────────────────
  // Three-tier upgrade chain (Bosquet.md/Foret.md/Jungle.md) — only Grove is directly
  // buildable; Forest and Jungle are reached by upgrading in place (see
  // Placement.tryUpgradeBuilding). Each tier's cost is the upgrade cost paid at that
  // step, not a cumulative total.
  val GroveCostWood: Double = 10.0 // Bosquet.md: "cout en bois: 10"
  val WoodPerSecPerGrove: Double = 0.2 // Bosquet.md: "produit 1 bois / 5 sec"
  val ElfSpawnIntervalMs: Double = 10_000.0 // Bosquet.md: "toutes les 10 sec genere un Elf"

  val ForestUpgradeCostWood: Double = 30.0 // Foret.md: "cout en bois: 30"
  val WoodPerSecPerForest: Double = 0.5 // Foret.md: "produit 1 bois / 2 sec"
  // Foret.md: Ents deal 2 dmg/sec to adjacent units — Jungle inherits this (upgrades are
  // cumulative: Jungle.md doesn't repeat the ability, but "Amelioration" implies it keeps
  // what the prior tier had, only Grove/Bosquet lacks it).
  val AuraDamagePerSec: Double = 2.0

  val JungleUpgradeCostWood: Double = 60.0 // Jungle.md: "cout en bois: 60"
  val WoodPerSecPerJungle: Double = 1.0 // Jungle.md: "produit 1 bois / sec"
  val WolfSpawnIntervalMs: Double = 5_000.0 // Jungle.md: "toutes les 5 sec"

  val ElfMaxHp: Double = 5.0
  val ElfSpeedPerMs: Double = 0.05 // POC default: 50 px/s

  val WolfMaxHp: Double = 40.0 // Loup.md: "PV: 40"
  val WolfSpeedPerMs: Double = ElfSpeedPerMs * 1.5 // Loup.md: "1.5x plus vite que les unites standard"
  // Loup.md: "augmente la vitesse de deplacement des unites a 2 cases de 50%" — a
  // multiplier (1.5x), not a flat addition, applied to any other creature within range.
  val WolfSpeedAuraMultiplier: Double = 1.5
  val WolfSpeedAuraRangeCells: Int = 2

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

  // Tour de guet.md: Loi's second building — no unit spawn, produces Light like Eglise
  // and deals direct single-target damage instead of a passive adjacency aura.
  val WatchtowerCostWood: Double = 10.0 // "cout en bois: 10"
  val WatchtowerCostLight: Double = 5.0 // "cout en lumiere: 5"
  val LightPerSecPerWatchtower: Double = 1.0 // "Produit 1 Lumiere par seconde"
  val WatchtowerDamagePerSec: Double = 10.0 // "Inflige 10 degats chaque seconde a une cible"
  // "jusqu'a 2 cases de distance" — Chebyshev (king-move) distance in cells, the usual
  // reading of tower range on a grid: any cell within a 5x5 block centered on the tower.
  val WatchtowerRangeCells: Int = 2

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

  val StartingResources: Map[Resource, Double] =
    Map(Resource.Wood -> StartingWood, Resource.Fire -> StartingFire, Resource.Light -> StartingLight)

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
  val NatureVictoryForestTarget: Int = 40
  val ChaosVictoryPlunderTarget: Double = 50.0 // Victoire.md, "R: Plunder — piller XX ressources"

  // Must double the opponent's own count (not just clear a fixed floor) to win.
  val VictoryMultiplierOverOpponent: Double = 2.0

  // POC default: tearing a building down returns half of what it cost, so reshaping a
  // maze isn't free (discourages build/destroy spam) but also isn't punitive.
  val DemolishRefundFraction: Double = 0.5
