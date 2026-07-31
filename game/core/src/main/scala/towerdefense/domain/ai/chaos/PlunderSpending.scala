package towerdefense.domain.ai.chaos

import towerdefense.domain.*
import towerdefense.domain.ai.SpendingPolicy

// Always favors Chaos (every one of its 4 buildings — Cave/Labyrinth/WarCamp/DragonsLair)
// regardless of the opponent's own faction mix, racing the Chaos/plunder victory condition
// instead of reacting to what the opponent builds the way WeightedSpending's counter term
// does. Ties among Chaos kinds (and among non-Chaos fallbacks, if none is affordable)
// break by margin. WarCamp's Orc (see Balance.OrcMaxHp's doc) is what lets this policy
// actually survive a Watchtower-defended opponent (e.g. maze-science) long enough to
// complete its own plunder race; DragonsLair's Dragon (rock-paper-scissors tuning pass —
// missing here until audited against every Chaos building) is the other half: its 40 Gold
// plunder alone very nearly secures the whole victory (target 50) in a single successful
// raid — but a flat, equal bonus for every Chaos kind alone never actually got it built in
// practice (confirmed via `sim/run maze-plunder maze-science --log`): Cave's near-zero
// cost always won the fallback margin tie-break, so a real match just spammed Cave
// forever. DragonsLair gets its own priority tier instead (mirroring science.
// ScienceSpending's tiered bonuses), guaranteeing at least one gets built once some Chaos
// economy already exists — capped at 1 (its whole value is one raid's magnitude, not
// volume), and gated on already owning a Cave/WarCamp: an UNgated priority bonus was
// confirmed (via the same transcript) to spend this policy's entire starting Gold on a
// turn-1 DragonsLair — a building with zero economic return — the exact Gold-starvation
// lockout ScienceSpending's own producer bonuses exist to avoid.
//
// A Watchtower-defense tier (mirroring ScienceSpending's) was tried and reverted here —
// it overcorrected Law-vs-Chaos into a total Chaos sweep without fixing Chaos-vs-Science
// at all, net-negative across the 5-leg cycle. Left as a note for a future, more careful
// pass rather than re-attempted blindly.
//
// warCampBonus/labyrinthBonus (this pass): diagnosed via several real `sim/run
// maze-plunder maze-law --log` transcripts (5 seeds) that Chaos's own plunder race stalls
// hard, not gradually — every single PLUNDER line across every seed landed inside a short
// ~700-1,000-tick window (final totals ranged 13-44 out of the 50 target, always well
// short), then zero more for the remaining ~2,000+ ticks despite continuing to build and
// dispatch raiders the whole time, because Law's own Watchtower count (uncapped — see
// loi.LawSpending's watchtowerDefenseCap doc) crosses a density past which even a
// Minotaur (80 hp, the single tankiest Chaos raider) dies before reaching the goal —
// confirmed in one transcript: 137 Minotaurs died to Watchtower fire between tick 1,000
// and 3,000, without a single one landing a plunder. Once that wall saturates, no amount
// of extra raider volume or toughness recovers it (verified: raising labyrinthBonus to 3.0
// changed nothing about that early window in a replayed seed) — the wall isn't gated by
// this policy's own priority at all, it's gated by how much Wood/Fire has physically
// accumulated by then, which these two small nudges only shift at the margin, if at all:
// they matter only in the rare tick where >1 Chaos kind is SIMULTANEOUSLY affordable
// (confirmed: with them, the early build order was byte-for-byte identical to the
// unmodified policy in a replayed seed, aside from 2 late slots past tick 900). Kept anyway
// as a small, low-risk, independently-justified correction, not a fix for the Law leg:
// WarCamp already strictly beats Cave on economy (FirePerSecPerWarCamp >
// FirePerSecPerCave) and produces the tougher Orc raider on top, yet SpendingPolicy.
// rawMargin's proportional-cost measure structurally favors Cave's small absolute Fire
// cost whenever both ARE simultaneously affordable; Labyrinth's Minotaur, in a low-defense
// environment, is Chaos's best plunder-per-second raider of all four kinds (its 2x
// MinotaurPlunderPerUnit per MinotaurSpawnIntervalMs beats Cave/WarCamp/DragonsLair's own
// plunder-value/spawn-interval ratios) yet previously tied Cave on flat priority. Left
// DragonsLair's own tier untouched. The actual Law leg blocker is Law's own uncapped
// defense scaling — see this session's balance complaint, not fixable from this file.
case object PlunderSpending extends SpendingPolicy:
  private val chaosKinds =
    Set(BuildingKind.Cave, BuildingKind.Labyrinth, BuildingKind.WarCamp, BuildingKind.DragonsLair)
  private val chaosEconomyKinds = Set(BuildingKind.Cave, BuildingKind.WarCamp)
  private val dragonsLairCap = 1

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    val chaosBonus = if chaosKinds.contains(kind) then 1.0 else 0.0
    val hasEconomy = state.buildings.exists(b => chaosEconomyKinds.contains(b.kind))
    val dragonsLairCount = state.buildings.count(_.kind == BuildingKind.DragonsLair)
    val dragonsLairBonus =
      if kind == BuildingKind.DragonsLair && dragonsLairCount < dragonsLairCap && hasEconomy then
        3.0
      else 0.0
    // Small, unconditional (no gate needed — WarCamp is already part of ChaosShared.opening,
    // so it's never a turn-1 concern the way DragonsLair was): nudges the Cave/WarCamp tie
    // toward WarCamp's strictly-better economy + raider whenever both are simultaneously
    // affordable, instead of letting rawMargin's proportional-cost bias default to Cave.
    val warCampBonus = if kind == BuildingKind.WarCamp then 0.2 else 0.0
    // Gated like dragonsLairBonus (same hasEconomy check, same "don't blow turn-1 Gold on
    // a zero-return building" reasoning) but smaller and uncapped: Minotaur's value compounds
    // with volume the same way Cave/WarCamp's raiders do, unlike DragonsLair's one-shot value.
    val labyrinthBonus = if kind == BuildingKind.Labyrinth && hasEconomy then 0.5 else 0.0
    chaosBonus + dragonsLairBonus + warCampBonus + labyrinthBonus + 0.25 * SpendingPolicy
      .resourceScore(state, kind)
