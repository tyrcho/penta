package towerdefense.domain.ai

import towerdefense.domain.*
import towerdefense.domain.economy.*

// Races Loi's "Paix Éternelle" victory condition (VictoryConditions.hasWonViaLoi) the same
// way PlunderSpending/CorruptionSpending race Chaos/Mort's: build as many of the four Loi
// kinds (Church/Watchtower/Angel/Barracks) as possible before Balance.
// LoiVictoryTickThreshold elapses, since it's a pure building-count comparison at that
// instant, not a resource total. Watchtower doubles as this strategy's own defense (10
// dmg/sec, 2-cell range — same mechanic ScienceSpending borrows via a separate bonus
// tier, see its own doc) at zero extra cost here, since Watchtower is already one of the
// four counted kinds.
//
// Also penalizes incidental Grove/Forest/Jungle past a small cap (same mechanism
// ScienceSpending uses for itself) — diagnosed via a real transcript: none of the 4 Loi
// kinds produce Wood, so this policy's fallback kept building Grove for Wood income, pure
// waste for a strategy whose victory condition is a building-COUNT tally that Grove never
// contributes to. Capped, not banned outright, since Law still needs *some* ongoing Wood
// income to keep affording more Loi buildings.
case object LawSpending extends SpendingPolicy:
  private val loiKinds =
    Set(BuildingKind.Church, BuildingKind.Watchtower, BuildingKind.Angel, BuildingKind.Barracks)
  private val natureKinds = Set(BuildingKind.Grove, BuildingKind.Forest, BuildingKind.Jungle)
  private val natureBuildingCap = 2
  // Extra priority on top of loiBonus (Watchtower already counts toward Law's own win
  // condition just like Church/Angel/Barracks, so this only reorders WHICH of the 4 gets
  // built first, never hurts the building-count race itself) — diagnosed via transcript
  // (`sim/run maze-law maze-plunder --log`): with every loiKind scoring an equal flat
  // bonus, margin-based tie-breaking let Law drift toward spamming Barracks (cheapest)
  // for raw volume, leaving only 1-2 Watchtowers up against a sustained Goblin/Orc/
  // Minotaur rush — Chaos's plunder target (a small, fixed number) kept getting hit well
  // before Balance.LoiVictoryTickThreshold, regardless of how large Law's own building
  // lead would eventually have been.
  //
  // No cap at all (unlike Nature/Science's own 2-4 defense tiers): raised from an initial
  // 6 after confirming via transcript that Chaos's raiders scale UP over the whole match
  // (WarCamp/Labyrinth/DragonsLair compound its own economy), so Law's kill-throughput
  // needs to keep growing right alongside it, not plateau early and drift back to
  // Church/Angel/Barracks once "enough" Watchtowers exist. Watchtower still costs real
  // Light (Barracks/Church stay the only producers of it), so this doesn't starve Law's
  // own income outright — it just means Watchtower wins the fallback tie-break too, not
  // only the capped-priority tier.
  private val watchtowerDefenseCap = Int.MaxValue
  // Recherches loyales.md: LaboDeLaLoi's own research speeds up every Loi-faction damage
  // dealer's attack INTERVAL (CombatEngine's intervalFor — level 5 gives a 2.2x rate, not
  // just 2.2x damage), which multiplies Watchtower's kill throughput at a FIXED building
  // count instead of needing an ever-larger number of towers — the real bottleneck found
  // via transcript: each Watchtower only ever kills one nearest creature per second, and
  // Chaos's own raider volume keeps scaling with its economy. Added at the project owner's
  // explicit direction ("consider adding angels and lab upgrades to the law strat").
  // laboFondamentalCap=1: Law only needs the one Fondamental -> LaboDeLaLoi chain, not a
  // full Science-style lab spread — see BuildingSpecs.upgradeOptions' LaboDeLaLoi-first
  // reordering for the "which of the 5 it upgrades into" half of this. Gated on already
  // having 2+ Watchtowers up so this doesn't derail the early defense rush itself.
  //
  // The cap itself is enforced by AiStrategy's own CountCapLayout wrapping (a hard veto),
  // not a SpendingPolicy penalty here — diagnosed via transcript that even a -1_000 flat
  // penalty on a second LaboFondamental still lost the tie-break during a genuine Wood
  // drought (none of Law's own 4 kinds produce Wood): every OTHER candidate hit
  // SpendingPolicy.marginFor's own UnaffordableMarginFloor (-1_000_000), so a
  // merely-very-negative LaboFondamental still won by comparison. See CountCapLayout's own
  // doc for why a layout-level veto (immune to every other candidate's score) was the fix.
  // This policy still needs its own labCount check for the BONUS below the cap — the veto
  // only stops going OVER it, it doesn't make Law want to build one in the first place.
  private val laboFondamentalCap = 1
  private val allLabKinds = ResearchSpecs.all.keySet + BuildingKind.LaboFondamental

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    val loiBonus = if loiKinds.contains(kind) then 1.0 else 0.0
    val watchtowerCount = state.buildings.count(_.kind == BuildingKind.Watchtower)
    val defenseBonus =
      if kind == BuildingKind.Watchtower && watchtowerCount < watchtowerDefenseCap then 2.0 else 0.0
    val labCount = state.buildings.count(b => allLabKinds.contains(b.kind))
    val labBonus =
      if kind == BuildingKind.LaboFondamental && watchtowerCount >= 2 && labCount < laboFondamentalCap
      then 1.5
      else 0.0
    val natureCount = state.buildings.count(b => natureKinds.contains(b.kind))
    val naturePenalty =
      if natureKinds.contains(kind) && natureCount >= natureBuildingCap then -1.0 else 0.0
    loiBonus + defenseBonus + labBonus + naturePenalty + 0.25 * SpendingPolicy.resourceScore(
      state,
      kind
    )
