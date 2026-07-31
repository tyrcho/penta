package towerdefense.domain.ai

import towerdefense.domain.*

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
// forever. DragonsLair gets its own priority tier instead (mirroring ScienceSpending's
// tiered bonuses), guaranteeing at least one gets built once some Chaos economy already
// exists — capped at 1 (its whole value is one raid's magnitude, not volume), and gated on
// already owning a Cave/WarCamp: an UNgated priority bonus was confirmed (via the same
// transcript) to spend this policy's entire starting Gold on a turn-1 DragonsLair — a
// building with zero economic return — the exact Gold-starvation lockout
// ScienceSpending's own producer bonuses exist to avoid.
//
// A Watchtower-defense tier (mirroring ScienceSpending's) was tried and reverted here —
// it overcorrected Law-vs-Chaos into a total Chaos sweep without fixing Chaos-vs-Science
// at all, net-negative across the 5-leg cycle. Left as a note for a future, more careful
// pass rather than re-attempted blindly.
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
    chaosBonus + dragonsLairBonus + 0.25 * SpendingPolicy.resourceScore(state, kind)
