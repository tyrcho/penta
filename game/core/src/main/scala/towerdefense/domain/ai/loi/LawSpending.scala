package towerdefense.domain.ai.loi

import towerdefense.domain.*
import towerdefense.domain.ai.SpendingPolicy
import towerdefense.domain.economy.*

// Races Loi's "Paix Éternelle" victory condition (VictoryConditions.hasWonViaLoi) the same
// way chaos.PlunderSpending/mort.CorruptionSpending race Chaos/Mort's: build as many of
// the four Loi kinds (Church/Watchtower/Angel/Barracks) as possible before Balance.
// LoiVictoryTickThreshold elapses, since it's a pure building-count comparison at that
// instant, not a resource total. Watchtower doubles as this strategy's own defense (10
// dmg/sec, 2-cell range — same mechanic science.ScienceSpending borrows via a separate
// bonus tier, see its own doc) at zero extra cost here, since Watchtower is already one of
// the four counted kinds.
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
  // The cap itself is enforced by AiStrategy's own CountCapLayout wrapping (a hard veto,
  // see loi.MazeLaw's own use of LoiShared.labKinds), not a SpendingPolicy penalty here —
  // diagnosed via transcript that even a -1_000 flat penalty on a second LaboFondamental
  // still lost the tie-break during a genuine Wood drought (none of Law's own 4 kinds
  // produce Wood): every OTHER candidate hit SpendingPolicy.marginFor's own
  // UnaffordableMarginFloor (-1_000_000), so a merely-very-negative LaboFondamental still
  // won by comparison. See CountCapLayout's own doc for why a layout-level veto (immune to
  // every other candidate's score) was the fix. This policy still needs its own labCount
  // check for the BONUS below the cap — the veto only stops going OVER it, it doesn't
  // make Law want to build one in the first place.
  private val laboFondamentalCap = 1
  private val allLabKinds = ResearchSpecs.all.keySet + BuildingKind.LaboFondamental

  // Defensive matchup lever (maze-corruption vs maze-law: baseline 3/12) — diagnosed via
  // `sim/run maze-corruption maze-law --log`: every corrupted-to-dust building in a lost
  // seed's transcript died to a SIMULTANEOUS wave (Tomb+DeathHouse+BlackCastle each firing
  // a Zombie/Vampire/Soul around the same time), not a lone raider — Watchtower kills only
  // its one nearest target per Balance.DamageTickIntervalMs (1 kill/sec/tower, see
  // CombatEngine's watchtowerFiring), so every OTHER corruptor standing adjacent that same
  // second corrodes completely unopposed. Angel is the one Loi kind that doesn't share that
  // limit: like Forest/Jungle/PassingGate it's an AURA (CombatEngine.auraBuildingKinds),
  // hitting *every* adjacent creature each tick simultaneously (Balance.AngelDamagePerSec)
  // plus a 25% slow (Balance.AngelSlowFraction) that gives Watchtower/other Angels more
  // ticks to finish each one off — the actual counter to a multi-corruptor swarm arriving
  // in the same window, not more single-target throughput.
  //
  // Currently starved out: Angel ties Church/Barracks/Watchtower on loiBonus alone, and
  // Watchtower's own uncapped defenseBonus above wins that tie every time it's affordable
  // (by design, see its own doc) — a measured loss built 53 Watchtower against only 8
  // Angel. This bonus is deliberately BELOW defenseBonus (1.0+1.3=2.3 vs Watchtower's
  // 1.0+2.0=3.0), so Watchtower still wins every head-to-head and Chaos's already-perfect
  // 12/12 leg (tuned specifically on "Watchtower wins the fallback tie-break too") is
  // untouched — this only changes which of Church/Barracks/Angel wins THEIR OWN three-way
  // tie once Watchtower isn't the pick that tick, giving Law real aura coverage against
  // swarm corrosion instead of Barracks' zero-dps Soldier or Church's lone-target Paladin
  // shield.
  //
  // Bonus 0.8->1.3 and cap 6->10 (round 2, re-tuned against mort.CorruptionSpending's own
  // round-1 strengthening — spendingWeight 1.0->3.0 plus a BlackCastle-specific bonus, see
  // its own doc): the round-1 numbers were measured against Mort's OLD, diluted-scoring
  // code (3/12, briefly 4/12) and collapsed to 0/12 once Mort actually committed to a real
  // Tomb/BlackCastle rush every turn instead of drifting into Watchtower/Grove filler.
  // Vampire in particular (BlackCastle's own unit) has 50 HP against Zombie's 15 — one
  // Watchtower alone needs 5 ticks to solo-kill it, plenty of time for its 2.5%/sec
  // corruption to land repeatedly — so more simultaneous Angel aura coverage (hits every
  // adjacent creature every tick, not just Watchtower's own one-nearest-target) is worth
  // more of the build order now than it was against the weaker round-1 Mort.
  private val angelSwarmDefenseCap = 10
  private val angelSwarmDefenseBonus = 1.3

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    val loiBonus = if loiKinds.contains(kind) then 1.0 else 0.0
    val watchtowerCount = state.buildings.count(_.kind == BuildingKind.Watchtower)
    val defenseBonus =
      if kind == BuildingKind.Watchtower && watchtowerCount < watchtowerDefenseCap then 2.0 else 0.0
    val angelCount = state.buildings.count(_.kind == BuildingKind.Angel)
    val angelBonus =
      if kind == BuildingKind.Angel && angelCount < angelSwarmDefenseCap then angelSwarmDefenseBonus
      else 0.0
    val labCount = state.buildings.count(b => allLabKinds.contains(b.kind))
    val labBonus =
      if kind == BuildingKind.LaboFondamental && watchtowerCount >= 2 && labCount < laboFondamentalCap
      then 1.5
      else 0.0
    val natureCount = state.buildings.count(b => natureKinds.contains(b.kind))
    val naturePenalty =
      if natureKinds.contains(kind) && natureCount >= natureBuildingCap then -1.0 else 0.0
    loiBonus + defenseBonus + angelBonus + labBonus + naturePenalty + 0.25 * SpendingPolicy
      .resourceScore(
        state,
        kind
      )
