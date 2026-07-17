package towerdefense.sim

// Standard Elo update math, tested independent of any tournament plumbing — see
// Simulator.tournamentStandings for where this gets applied match by match.
class EloRatingTest extends munit.FunSuite:

  test("equal ratings: a win moves the winner up and the loser down by exactly half the K-factor") {
    val (a, b) = EloRating.updateRatings(EloRating.InitialRating, EloRating.InitialRating, scoreA = 1.0)
    assertEquals(a, EloRating.InitialRating + EloRating.KFactor / 2.0)
    assertEquals(b, EloRating.InitialRating - EloRating.KFactor / 2.0)
  }

  test("equal ratings: a draw leaves both ratings unchanged") {
    val (a, b) = EloRating.updateRatings(EloRating.InitialRating, EloRating.InitialRating, scoreA = 0.5)
    assertEquals(a, EloRating.InitialRating)
    assertEquals(b, EloRating.InitialRating)
  }

  test("a big favorite winning the expected result gains less than an even match would") {
    val (favorite, _) = EloRating.updateRatings(1800.0, 1200.0, scoreA = 1.0)
    assert(favorite - 1800.0 < EloRating.KFactor / 2.0, s"expected gain under ${EloRating.KFactor / 2.0}, was ${favorite - 1800.0}")
  }

  test("an underdog upset gains more than an even match would") {
    val (underdog, _) = EloRating.updateRatings(1200.0, 1800.0, scoreA = 1.0)
    assert(underdog - 1200.0 > EloRating.KFactor / 2.0, s"expected gain over ${EloRating.KFactor / 2.0}, was ${underdog - 1200.0}")
  }

  test("every update is zero-sum: whatever the winner gains, the loser loses the same amount") {
    val (a, b) = EloRating.updateRatings(1600.0, 1450.0, scoreA = 0.0)
    assertEquals((a - 1600.0) + (b - 1450.0), 0.0, 1e-9)
  }
