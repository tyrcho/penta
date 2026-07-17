package towerdefense.sim

// A simple Elo/MMR-style rating so tournament results carry a single strength number per
// strategy, not just a win rate — win rate alone doesn't reflect *who* those wins were
// against (an undefeated record against weak opposition looks identical to one earned
// against the strongest strategies on the ladder). Standard Elo formula, applied
// sequentially one match at a time as Simulator.tournamentStandings works through the
// round-robin — see its call site for how `matchesPerPairing` interacts with this (the
// simulation's determinism means repeated matches within one pairing all carry the same
// result, so the K-factor compounds across them exactly like a real repeated-game Elo
// system would; this is a simple rating, not a statistically rigorous one).
object EloRating:
  val InitialRating: Double = 1500.0
  val KFactor: Double = 32.0

  // Updates both ratings after one game between A and B. `scoreA` is 1.0 for a win, 0.5
  // for a draw, 0.0 for a loss, all from A's perspective — B's actual score is implicitly
  // 1 - scoreA. Zero-sum: whatever one side gains, the other loses exactly as much.
  def updateRatings(ratingA: Double, ratingB: Double, scoreA: Double): (Double, Double) =
    val expectedA = 1.0 / (1.0 + math.pow(10.0, (ratingB - ratingA) / 400.0))
    val newA = ratingA + KFactor * (scoreA - expectedA)
    val newB = ratingB - (newA - ratingA)
    (newA, newB)
