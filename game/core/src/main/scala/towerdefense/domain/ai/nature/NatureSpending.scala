package towerdefense.domain.ai.nature

import towerdefense.domain.*
import towerdefense.domain.ai.SpendingPolicy

// Races Nature's own forest-count victory condition (VictoryConditions.forestCount) the
// same way chaos.PlunderSpending/mort.CorruptionSpending/loi.LawSpending race their
// factions' — GrovePriority only ever chases Grove itself via a very different
// flat-1000/rawMargin shape, so it isn't a fair "pure Nature rush" comparable to the other
// three; this targets the whole Grove/Forest/Jungle upgrade chain plus Stonehenge instead.
//
// Also builds a little defense (Watchtower, same capped tier science.ScienceSpending/
// LawSpending use for themselves) — diagnosed via a real transcript: unlike Chaos/
// Science/Law's win conditions (cumulative stats that don't undo), a Forest corrupted to
// death is REMOVED from Nature's own forestCount, so Mort's corruption doesn't just race
// its own target, it actively reverses Nature's progress. Forest/Jungle's own passive
// corruption self-heal (0.1-0.5%/sec, well below a Zombie/Vampire's 1.15-3.0%/sec
// corruption rate) never stops a sustained assault alone.
case object NatureSpending extends SpendingPolicy:
  private val natureKinds =
    Set(BuildingKind.Grove, BuildingKind.Forest, BuildingKind.Jungle, BuildingKind.Stonehenge)
  // Raised from 2 (rock-paper-scissors tuning pass): 2 Watchtowers let far too many
  // Zombie/Vampire raiders reach a Grove/Forest cluster before dying, and every raider
  // that gets adjacent starts corroding a building whether or not the heal cluster can
  // out-pace it (see HealClusterLayout's own doc) — killing more raiders BEFORE they reach
  // a building is a more robust lever than trying to out-heal an open-ended number of them
  // once they're already there.
  // Raised again from 4 (project owner's explicit direction — "nature strat should also
  // build some defenses"): diagnosed via transcript that once the old cap of 4 was
  // reached, Nature dumped everything into cheap Grove spam (its own forestCount race) and
  // let a Science opponent's incidental Cave-spawned Goblins/Minotaurs accumulate enough
  // stolen Gold to win via Chaos's plunder condition first — an unrelated confound winning
  // before either side's own intended race did.
  // Raised again from 8 to 10 (off-cycle round-1 pass, `sim/run maze-plunder maze-nature`):
  // unlike Mort's corruption (which actively reverses Nature's own forestCount — see this
  // object's own top-level doc), a Chaos raider that reaches the far end just needs to
  // "arrive" once to bank its plunder toward Chaos's small, fixed, never-undone
  // ChaosVictoryPlunderTarget — so even a defense already killing the "large majority" of
  // raiders in transit (confirmed by a `--log --seed` transcript: Watchtower/Aura kills
  // vastly outnumber the handful of PLUNDER events) still eventually leaks enough raiders
  // over a long match to hit that small target before Nature's own (much slower)
  // forestCount race finishes. 2 extra Watchtowers measurably delayed and reduced Chaos's
  // plunder rate in seeded testing (1/12 -> 2/12 wins for Nature, `--seed 0`) with ZERO
  // regression on the favored maze-nature-vs-maze-corruption matchup at the same seed
  // (still 6/6) — cap 12 was also tried and rejected here: it cost 2 of 6 favored-matchup
  // wins for no further gain against Chaos, confirming this lever is close to its ceiling
  // without trading away the favored matchup, which this pass will not do (see
  // NatureSpending's package-level constraint against self-nerfing to manufacture a result
  // elsewhere). The remaining gap against Chaos looks structural (ChaosVictoryPlunderTarget
  // itself, not this policy) — see this round's balance complaint.
  private val watchtowerDefenseCap = 10

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    val natureBonus = if natureKinds.contains(kind) then 1.0 else 0.0
    val watchtowerCount = state.buildings.count(_.kind == BuildingKind.Watchtower)
    val defenseBonus =
      if kind == BuildingKind.Watchtower && watchtowerCount < watchtowerDefenseCap then 2.0 else 0.0
    natureBonus + defenseBonus + 0.25 * SpendingPolicy.resourceScore(state, kind)
