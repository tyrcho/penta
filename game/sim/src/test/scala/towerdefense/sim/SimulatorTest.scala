package towerdefense.sim

// Smoke tests for the headless simulator: prove it runs to completion on the JVM and
// produces well-formed results. Win-rate numbers are exploratory, not a correctness
// contract, so nothing here asserts a specific outcome — just shape and termination.
class SimulatorTest extends munit.FunSuite:

  // A recent rebalance (Balance.scala: cheaper Grove/lower Jungle-upgrade cost) makes
  // LinearStrategy accumulate far more buildings before either side reaches a victory
  // condition — the maxTicks=3_000 fixture below now regularly needs 15s+ of wall time per
  // match (more buildings means more per-tick work), well past munit's 30s default.
  override val munitTimeout: _root_.scala.concurrent.duration.Duration =
    _root_.scala.concurrent.duration.Duration(120, "s")

  test("linear vs linear runs to completion and returns well-formed tallies") {
    val tallies = Simulator.runMatches("linear", "linear", matches = 5, maxTicks = 300, deltaMs = 100.0)
    assertEquals(tallies.size, 2)
    tallies.foreach { t =>
      assert(t.wins + t.draws <= 5, s"wins+draws should never exceed matches played: $t")
      assert(t.avgTicks >= 0.0, s"avgTicks should never be negative: $t")
    }
  }

  test("searchWeights over a tiny grid runs to completion and returns a ranked, non-empty list") {
    val results =
      Simulator.searchWeights("linear", matchesPerPoint = 1, step = 1.0, maxTicks = 300, deltaMs = 100.0)
    assert(results.nonEmpty, "a weight grid must produce at least one candidate")
    assertEquals(results, results.sortBy(-_.winRate), "results must be ranked by win rate descending")
    results.foreach(r => assert(r.winRate >= 0.0 && r.winRate <= 1.0, s"winRate out of range: $r"))
  }

  test("tournament round-robins every pairing and ranks standings by win rate descending") {
    val standings =
      Simulator.tournamentStandings(Seq("linear", "comb", "maze-only"), matchesPerPairing = 2, maxTicks = 300, deltaMs = 100.0)
    assertEquals(standings.map(_.name).toSet, Set("linear", "comb", "maze-only"))
    // 3 strategies round-robin = 3 pairings, 2 matches each = 4 matches played per strategy.
    standings.foreach(s => assertEquals(s.matches, 4))
    standings.foreach(s => assert(s.wins + s.draws + s.losses == s.matches, s"$s"))
    assertEquals(standings, standings.sortBy(-_.winRate), "standings must be ranked by win rate descending")
  }

  test("tournament assigns every strategy an Elo rating, zero-sum around the starting rating") {
    // maxTicks = 300 (30 virtual seconds, this file's other fixtures' shared smoke-test
    // value) is too short for any strategy to actually reach a victory condition — every
    // match in that fixture draws, which would leave every rating sitting untouched at
    // InitialRating and prove nothing about the Elo wiring. Needs the same maxTicks the
    // `tournament` CLI actually plays with (3_000) to get real decisive results on this
    // trio — matchesPerPairing = 1 suffices since the simulation is fully deterministic
    // (see AiStrategy.ladder's doc): repeating an identical match buys no new information.
    val standings =
      Simulator.tournamentStandings(Seq("linear", "comb", "maze-only"), matchesPerPairing = 1, maxTicks = 3_000, deltaMs = 100.0)
    // Every strategy played some non-drawn matches on this trio (a full round-robin
    // history — see AiStrategy.ladder's doc — confirms comb and linear never draw against
    // each other here), so nobody should still be sitting exactly at the untouched
    // starting rating — that would mean Elo silently isn't wired up.
    standings.foreach(s => assertNotEquals(s.elo, EloRating.InitialRating, s"$s never moved off the starting rating"))
    // Every individual match's Elo update is zero-sum (EloRatingTest), so summed across every
    // strategy the total must still equal n times the shared starting rating.
    assertEqualsDouble(
      standings.map(_.elo).sum,
      standings.size * EloRating.InitialRating,
      1e-6,
      "Elo ratings across the whole tournament must stay zero-sum"
    )
    // The undefeated strategy (comb, per this trio's known 0-losses-across-90-games
    // history — see AiStrategy.ladder's doc) must outrank whichever strategy lost the most.
    val best = standings.minBy(_.losses)
    val worst = standings.maxBy(_.losses)
    assert(best.elo > worst.elo, s"$best should outrate $worst")
  }

  // CLAUDE.md: "any job running more than a few seconds should report an ETA to stderr" —
  // these prove the batch functions actually invoke their progress callback once per unit
  // of work completed, in order. The stderr-printing/throttling itself lives in
  // ProgressReporter, tested separately below without any wall-clock dependency here.
  test("runMatches reports progress once per match completed, in order") {
    val progress = scala.collection.mutable.ArrayBuffer.empty[Int]
    Simulator.runMatches("linear", "linear", matches = 3, maxTicks = 300, deltaMs = 100.0, onProgress = progress.append(_))
    assertEquals(progress.toList, List(1, 2, 3))
  }

  test("tournamentStandings reports progress once per pairing completed, in order") {
    val progress = scala.collection.mutable.ArrayBuffer.empty[Int]
    Simulator.tournamentStandings(
      Seq("linear", "comb", "maze-only"),
      matchesPerPairing = 1,
      maxTicks = 300,
      deltaMs = 100.0,
      onPairingDone = progress.append(_)
    )
    // 3 strategies round-robin = 3 pairings (linear-comb, linear-maze-only, comb-maze-only).
    assertEquals(progress.toList, List(1, 2, 3))
  }

  test("searchWeights reports progress once per weight-grid point completed, in order") {
    val progress = scala.collection.mutable.ArrayBuffer.empty[Int]
    val results = Simulator.searchWeights(
      "linear",
      matchesPerPoint = 1,
      step = 1.0,
      maxTicks = 300,
      deltaMs = 100.0,
      onPointDone = progress.append(_)
    )
    assertEquals(progress.toList, (1 to results.size).toList)
  }

  test("runMatch reports each side's total research level (summed across all labs) at match end") {
    // 1 tick isn't enough time for either side to even build a lab, let alone research it —
    // an exact-zero assertion, not just "non-negative", since this case is fully predictable.
    val tooShort =
      Simulator.runMatch(towerdefense.domain.AiStrategy.all("linear"), towerdefense.domain.AiStrategy.all("linear"), maxTicks = 1, deltaMs = 100.0)
    assertEquals(tooShort.totalResearchA, 0)
    assertEquals(tooShort.totalResearchB, 0)

    val longEnough = Simulator.runMatch(
      towerdefense.domain.AiStrategy.all("linear"),
      towerdefense.domain.AiStrategy.all("linear"),
      maxTicks = 3_000,
      deltaMs = 100.0
    )
    assert(longEnough.totalResearchA >= 0, s"$longEnough")
    assert(longEnough.totalResearchB >= 0, s"$longEnough")
  }

  test("runMatches' tallies report each side's average research level across its matches") {
    val tallies = Simulator.runMatches("linear", "linear", matches = 3, maxTicks = 3_000, deltaMs = 100.0)
    tallies.foreach(t => assert(t.avgResearch >= 0.0, s"$t"))
  }

  test("tournamentStandings reports each strategy's average research level across all its matches") {
    val standings =
      Simulator.tournamentStandings(Seq("linear", "comb", "maze-only"), matchesPerPairing = 1, maxTicks = 3_000, deltaMs = 100.0)
    standings.foreach(s => assert(s.avgResearch >= 0.0, s"$s"))
  }

  test("runLoggedMatch writes a transcript, ending in a WINS line whenever the match resolves") {
    val lines = scala.collection.mutable.ArrayBuffer.empty[String]
    val outcome = Simulator.runLoggedMatch(
      towerdefense.domain.AiStrategy.all("linear"),
      towerdefense.domain.AiStrategy.all("linear"),
      maxTicks = 300,
      deltaMs = 100.0,
      logEvery = 100,
      writeLine = lines.append(_)
    )
    outcome.winner.foreach { w =>
      assert(lines.nonEmpty, "a resolved match must have logged at least the final line")
      assert(
        lines.last.startsWith(s"tick ${outcome.ticks}  $w  WINS  "),
        s"expected the last line to be a WINS line for side $w, got: ${lines.last}"
      )
    }
    // Every logged line must be attributable to the match that produced it (same tick
    // range) — a loose sanity check that runLoggedMatch isn't leaking state across calls.
    assert(lines.forall(_.startsWith("tick ")), lines.mkString("\n"))
  }

  // A full tournament re-run is what surfaced the need for this: diagnosing an unusual
  // draw meant separately reproducing it by hand with `sim/run --log` afterwards, which
  // isn't even guaranteed to reproduce the same match since ComposedStrategy's tie-breaks
  // are unseeded during a real tournament run. Every match now gets a cheap one-line
  // summary (pairing, winner, final resource/plunder/corrupted snapshot) written as it's
  // played, so no match's outcome needs to be reproduced after the fact just to see why
  // it ended the way it did.
  test("tournamentStandings logs a one-line summary per match when a log sink is given") {
    val lines = scala.collection.mutable.ArrayBuffer.empty[String]
    Simulator.tournamentStandings(
      Seq("linear", "comb", "maze-only"),
      matchesPerPairing = 2,
      maxTicks = 300,
      deltaMs = 100.0,
      logLine = lines.append(_)
    )
    // 3 pairings x 2 matches/pairing = 6 match summaries, one line each.
    assertEquals(lines.size, 6)
    lines.foreach { l =>
      assert(l.contains(" vs "), s"expected a pairing name in the log line: $l")
      assert(l.contains("winner="), s"expected a winner field in the log line: $l")
      assert(l.contains("SNAPSHOT"), s"expected a final-state snapshot in the log line: $l")
    }
  }

  // Swiss pairing (replaces the full round-robin `tournament` CLI plays, to cut a
  // 25-strategy ladder's 300-pairing round-robin down to a handful of rounds): rank by
  // score (win=1, draw=0.5) first and Elo second, fold-pair the ranked field in half
  // (rank 1 vs the top of the bottom half, and so on), skipping any pairing already
  // played this tournament, for ceil(log2(n)) rounds.
  test("swissStandings plays ceil(log2(n)) rounds, one match per pairing per round") {
    val progress = scala.collection.mutable.ArrayBuffer.empty[Int]
    // 8 names -> log2(8) = 3 rounds exactly, 4 pairings/round (even field, no byes).
    val names = Seq(
      "linear",
      "comb",
      "comb-vertical",
      "maze-only",
      "counter-only",
      "resource-only",
      "maze-counter",
      "balanced"
    )
    val standings = Simulator.swissStandings(names, matchesPerPairing = 1, maxTicks = 300, deltaMs = 100.0, onRoundDone = progress.append(_))
    assertEquals(progress.toList, List(1, 2, 3))
    assertEquals(standings.map(_.name).toSet, names.toSet)
    // Every strategy plays exactly one match per round (no byes: the field is even).
    standings.foreach(s => assertEquals(s.matches, 3, s"$s"))
  }

  test("swissStandings never repeats a pairing across rounds when an unplayed opponent is available") {
    val lines = scala.collection.mutable.ArrayBuffer.empty[String]
    val names = Seq(
      "linear",
      "comb",
      "comb-vertical",
      "maze-only",
      "counter-only",
      "resource-only",
      "maze-counter",
      "balanced"
    )
    // 3 rounds x 4 pairings = 12 matches, well under this 8-name field's C(8,2) = 28
    // possible pairings — every round should have a fully avoidable rematch.
    Simulator.swissStandings(names, matchesPerPairing = 1, maxTicks = 300, deltaMs = 100.0, logLine = lines.append(_))
    val pairings = lines.map(_.split("  ").head.split(" vs ").toSeq.sorted)
    assertEquals(pairings.distinct.size, pairings.size, s"a pairing repeated: $pairings")
  }

  // Odd-sized fields (the real 25-entry ladder among them) can't fold-pair everyone every
  // round — one strategy sits out with a bye each round instead. A bye counts as a full
  // win (and a played "match") in the final standings, same as beating an opponent, so
  // every strategy still ends up with exactly `rounds` matches recorded.
  test("swissStandings gives an odd-sized field one bye per round, counted as a win") {
    val names = Seq("linear", "comb", "comb-vertical", "maze-only", "counter-only", "resource-only", "maze-counter")
    // log2(7) = 2.807... -> 3 rounds.
    val standings = Simulator.swissStandings(names, matchesPerPairing = 1, maxTicks = 300, deltaMs = 100.0)
    standings.foreach(s => assertEquals(s.matches, 3, s"$s"))
  }

  test("swissStandings' final standings are ranked by score (win=1, draw=0.5) first, Elo second") {
    val standings =
      Simulator.swissStandings(
        Seq("linear", "comb", "comb-vertical", "maze-only", "counter-only", "resource-only", "maze-counter", "balanced"),
        matchesPerPairing = 1,
        maxTicks = 3_000,
        deltaMs = 100.0
      )
    def score(s: Simulator.Standing): Double = s.wins + 0.5 * s.draws
    standings.sliding(2).foreach {
      case Seq(a, b) =>
        assert(
          score(a) > score(b) || (score(a) == score(b) && a.elo >= b.elo),
          s"$a should rank at or above $b"
        )
      case _ => ()
    }
  }

  // Elo only moves on a real game (byes touch no Elo, matching runMatch's own zero-sum
  // update) — same invariant tournamentStandings' round-robin already guarantees.
  test("swissStandings' Elo ratings stay zero-sum across the whole tournament, byes included") {
    val names = Seq("linear", "comb", "comb-vertical", "maze-only", "counter-only", "resource-only", "maze-counter")
    val standings = Simulator.swissStandings(names, matchesPerPairing = 1, maxTicks = 300, deltaMs = 100.0)
    assertEqualsDouble(
      standings.map(_.elo).sum,
      standings.size * EloRating.InitialRating,
      1e-6,
      "Elo ratings across the whole Swiss tournament must stay zero-sum"
    )
  }
