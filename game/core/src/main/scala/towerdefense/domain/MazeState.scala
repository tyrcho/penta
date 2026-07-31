package towerdefense.domain

import towerdefense.domain.economy.*

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
    buildingsCorrupted: Int = 0, // this maze's own progress toward the Mort victory
    // condition (Corruption.md/Victoire.md "B") — counts
    // enemy buildings this maze's Zombies/Vampires have
    // corrupted to 100% and destroyed, a whole-number tally
    // (unlike resourcesPlundered above, which stays a flat
    // Double since it sums fractional resource amounts, not
    // building counts — see CombatEngine/BattleEngine's
    // corruption handling).
    // Science's leveled research (Recherches*.md/Recherche fondamentale.md) — keyed by the
    // five Labo* BuildingKinds, absent/0 meaning "not researched". Placement.tryResearch is
    // the only way this advances, gated on owning that lab (see its doc); a level, once
    // reached, persists even if the lab is later destroyed — POC interpretation, since the
    // vault doesn't say whether losing the building should erase accumulated research.
    // See ResearchSpecs for what each level costs/does.
    researchLevels: Map[BuildingKind, Int] = Map.empty,
    // Lifetime total spent per named resource (Wood/Fire/Light/Shadow/Crystal), across
    // every building placement/upgrade/research this maze has ever paid for — keyed by
    // what the cost actually named, never by Gold, even though Gold's joker substitution
    // (Placement.canAfford/debit) may have covered part or all of a given payment: a cost
    // of "5 Wood" paid entirely out of Gold still adds 5 to resourcesSpent(Wood), not to
    // a separate Gold bucket, since Gold is a payment method, not a resource category a
    // player is spending *on* (see GameApp's spending-breakdown donut). Never decreases —
    // demolishing/losing a building refunds `resources`, not this lifetime tally.
    resourcesSpent: Map[Resource, Double] = Map.empty,
    nextId: Long
):
  // Cells occupied by any building — the single source of truth for both pathfinding
  // obstacles (CombatEngine/Placement) and rendering (GameApp), so it's only ever defined once.
  def buildingCells: Set[Pos] = buildings.map(b => Pos(b.col, b.row)).toSet

object MazeState:
  val initial: MazeState = MazeState(
    creatures = Nil,
    buildings = Nil,
    resources = Balance.StartingResources,
    resourcesPlundered = 0.0,
    nextId = 1L
  )
