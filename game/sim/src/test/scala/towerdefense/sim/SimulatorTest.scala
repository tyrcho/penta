package towerdefense.sim

import towerdefense.domain.VictoryConditions
import towerdefense.domain.ai.AiStrategy

// Smoke tests for the headless simulator: prove it runs to completion on the JVM and
// produces well-formed results. Win-rate numbers are exploratory, not a correctness
// contract, so nothing here asserts a specific outcome — just shape and termination.
class SimulatorTest extends munit.FunSuite:

  // A recent rebalance (Balance.scala: cheaper Grove/lower Jungle-upgrade cost) makes
  // LinearStrategy accumulate far more buildings before either side reaches a victory
  // condition — the maxTicks=3_000 fixture below now regularly needs 15s+ of wall time per
  // match (more buildings means more per-tick work), well past munit's 30s default.
  override val munitTimeout: _root_.scala.concurrent.duration.Duration =
    _root_.scala.concurrent.duration.Duration(300, "s")

  test("linear vs linear runs to completion and returns well-formed tallies") {
    val tallies =
      Simulator.runMatches("linear", "linear", matches = 5, maxTicks = 300, deltaMs = 100.0)
    assertEquals(tallies.size, 2)
    tallies.foreach { t =>
      assert(t.wins + t.draws <= 5, s"wins+draws should never exceed matches played: $t")
      assert(t.avgTicks >= 0.0, s"avgTicks should never be negative: $t")
    }
  }

  test("searchWeights over a tiny grid runs to completion and returns a ranked, non-empty list") {
    val results =
      Simulator.searchWeights(
        "linear",
        matchesPerPoint = 1,
        step = 1.0,
        maxTicks = 300,
        deltaMs = 100.0
      )
    assert(results.nonEmpty, "a weight grid must produce at least one candidate")
    assertEquals(
      results,
      results.sortBy(-_.winRate),
      "results must be ranked by win rate descending"
    )
    results.foreach(r => assert(r.winRate >= 0.0 && r.winRate <= 1.0, s"winRate out of range: $r"))
  }

  test("tournament round-robins every pairing and ranks standings by win rate descending") {
    val standings =
      Simulator.tournamentStandings(
        Seq("linear", "comb", "maze-only"),
        matchesPerPairing = 2,
        maxTicks = 300,
        deltaMs = 100.0
      )
    assertEquals(standings.map(_.name).toSet, Set("linear", "comb", "maze-only"))
    // 3 strategies round-robin = 3 pairings, 2 matches each = 4 matches played per strategy.
    standings.foreach(s => assertEquals(s.matches, 4))
    standings.foreach(s => assert(s.wins + s.draws + s.losses == s.matches, s"$s"))
    assertEquals(
      standings,
      standings.sortBy(-_.winRate),
      "standings must be ranked by win rate descending"
    )
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
      Simulator.tournamentStandings(
        Seq("linear", "comb", "maze-only"),
        matchesPerPairing = 1,
        maxTicks = 3_000,
        deltaMs = 100.0
      )
    // Every strategy played some non-drawn matches on this trio (a full round-robin
    // history — see AiStrategy.ladder's doc — confirms comb and linear never draw against
    // each other here), so nobody should still be sitting exactly at the untouched
    // starting rating — that would mean Elo silently isn't wired up.
    standings.foreach(s =>
      assertNotEquals(s.elo, EloRating.InitialRating, s"$s never moved off the starting rating")
    )
    // Every individual match's Elo update is zero-sum (EloRatingTest), so summed across every
    // strategy the total must still equal n times the shared starting rating.
    assertEqualsDouble(
      standings.map(_.elo).sum,
      standings.size * EloRating.InitialRating,
      1e-6,
      "Elo ratings across the whole tournament must stay zero-sum"
    )
    // Whichever strategy lost the least must outrank whichever lost the most — but only
    // when this trio's balance actually produces a real skill gap on a given run (not
    // hardcoded to a specific "undefeated strategy": which of linear/comb/maze-only comes
    // out ahead is an empirical, rebalance-sensitive fact — see AiStrategy.ladder's own
    // doc on how often that's shifted). A 3-way round-robin can also land on a perfect
    // rock-paper-scissors tie (every strategy 1 win/1 loss), in which case there's no
    // "best"/"worst" to compare at all.
    val best = standings.minBy(_.losses)
    val worst = standings.maxBy(_.losses)
    if best.losses < worst.losses then assert(best.elo > worst.elo, s"$best should outrate $worst")
  }

  // CLAUDE.md: "any job running more than a few seconds should report an ETA to stderr" —
  // these prove the batch functions actually invoke their progress callback once per unit
  // of work completed, in order. The stderr-printing/throttling itself lives in
  // ProgressReporter, tested separately below without any wall-clock dependency here.
  test("runMatches reports progress once per match completed, in order") {
    val progress = scala.collection.mutable.ArrayBuffer.empty[Int]
    Simulator.runMatches(
      "linear",
      "linear",
      matches = 3,
      maxTicks = 300,
      deltaMs = 100.0,
      onProgress = progress.append(_)
    )
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
      Simulator.runMatch(
        AiStrategy.all("linear"),
        AiStrategy.all("linear"),
        maxTicks = 1,
        deltaMs = 100.0
      )
    assertEquals(tooShort.totalResearchA, 0)
    assertEquals(tooShort.totalResearchB, 0)

    val longEnough = Simulator.runMatch(
      AiStrategy.all("linear"),
      AiStrategy.all("linear"),
      maxTicks = 3_000,
      deltaMs = 100.0
    )
    assert(longEnough.totalResearchA >= 0, s"$longEnough")
    assert(longEnough.totalResearchB >= 0, s"$longEnough")
  }

  test("runMatches' tallies report each side's average research level across its matches") {
    val tallies =
      Simulator.runMatches("linear", "linear", matches = 3, maxTicks = 3_000, deltaMs = 100.0)
    tallies.foreach(t => assert(t.avgResearch >= 0.0, s"$t"))
  }

  test(
    "tournamentStandings reports each strategy's average research level across all its matches"
  ) {
    val standings =
      Simulator.tournamentStandings(
        Seq("linear", "comb", "maze-only"),
        matchesPerPairing = 1,
        maxTicks = 3_000,
        deltaMs = 100.0
      )
    standings.foreach(s => assert(s.avgResearch >= 0.0, s"$s"))
  }

  test("runLoggedMatch writes a transcript, ending in a WINS line whenever the match resolves") {
    val lines = scala.collection.mutable.ArrayBuffer.empty[String]
    val outcome = Simulator.runLoggedMatch(
      AiStrategy.all("linear"),
      AiStrategy.all("linear"),
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
    val standings = Simulator.swissStandings(
      names,
      matchesPerPairing = 1,
      maxTicks = 300,
      deltaMs = 100.0,
      onRoundDone = progress.append(_)
    )
    assertEquals(progress.toList, List(1, 2, 3))
    assertEquals(standings.map(_.name).toSet, names.toSet)
    // Every strategy plays exactly one match per round (no byes: the field is even).
    standings.foreach(s => assertEquals(s.matches, 3, s"$s"))
  }

  test(
    "swissStandings never repeats a pairing across rounds when an unplayed opponent is available"
  ) {
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
    Simulator.swissStandings(
      names,
      matchesPerPairing = 1,
      maxTicks = 300,
      deltaMs = 100.0,
      logLine = lines.append(_)
    )
    val pairings = lines.map(_.split("  ").head.split(" vs ").toSeq.sorted)
    assertEquals(pairings.distinct.size, pairings.size, s"a pairing repeated: $pairings")
  }

  // Odd-sized fields (the real 25-entry ladder among them) can't fold-pair everyone every
  // round — one strategy sits out with a bye each round instead. A bye counts as a full
  // win (and a played "match") in the final standings, same as beating an opponent, so
  // every strategy still ends up with exactly `rounds` matches recorded.
  test("swissStandings gives an odd-sized field one bye per round, counted as a win") {
    val names = Seq(
      "linear",
      "comb",
      "comb-vertical",
      "maze-only",
      "counter-only",
      "resource-only",
      "maze-counter"
    )
    // log2(7) = 2.807... -> 3 rounds.
    val standings =
      Simulator.swissStandings(names, matchesPerPairing = 1, maxTicks = 300, deltaMs = 100.0)
    standings.foreach(s => assertEquals(s.matches, 3, s"$s"))
  }

  test("swissStandings' final standings are ranked by score (win=1, draw=0.5) first, Elo second") {
    val standings =
      Simulator.swissStandings(
        Seq(
          "linear",
          "comb",
          "comb-vertical",
          "maze-only",
          "counter-only",
          "resource-only",
          "maze-counter",
          "balanced"
        ),
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
    val names = Seq(
      "linear",
      "comb",
      "comb-vertical",
      "maze-only",
      "counter-only",
      "resource-only",
      "maze-counter"
    )
    val standings =
      Simulator.swissStandings(names, matchesPerPairing = 1, maxTicks = 300, deltaMs = 100.0)
    assertEqualsDouble(
      standings.map(_.elo).sum,
      standings.size * EloRating.InitialRating,
      1e-6,
      "Elo ratings across the whole Swiss tournament must stay zero-sum"
    )
  }

  // The Swiss tournament runs its matches across a fixed thread pool now, but every
  // pairing played must still be reported exactly once — the markdown grid (built from
  // this callback by the `tournament` CLI) would silently drop or duplicate a matchup
  // otherwise.
  test("swissStandings reports each played pairing's outcomes exactly once via onPairingResult") {
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
    val reported = scala.collection.mutable.ArrayBuffer.empty[(String, String, Int)]
    Simulator.swissStandings(
      names,
      matchesPerPairing = 2,
      maxTicks = 300,
      deltaMs = 100.0,
      onPairingResult = (a, b, outcomes) => reported += ((a, b, outcomes.size))
    )
    val pairs = reported.map { case (a, b, _) => Set(a, b) }
    assertEquals(
      pairs.distinct.size,
      pairs.size,
      s"a pairing was reported more than once: $reported"
    )
    reported.foreach { case (_, _, count) =>
      assertEquals(count, 2, s"expected 2 matches/pairing: $reported")
    }
  }

  test("tournamentStandings reports each pairing's outcomes exactly once via onPairingResult") {
    val names = Seq("linear", "comb", "maze-only")
    val reported = scala.collection.mutable.ArrayBuffer.empty[(String, String, Int)]
    Simulator.tournamentStandings(
      names,
      matchesPerPairing = 2,
      maxTicks = 300,
      deltaMs = 100.0,
      onPairingResult = (a, b, outcomes) => reported += ((a, b, outcomes.size))
    )
    // 3 strategies round-robin = 3 pairings.
    assertEquals(reported.size, 3)
    reported.foreach { case (_, _, count) =>
      assertEquals(count, 2, s"expected 2 matches/pairing: $reported")
    }
  }

  // Both tournament functions run their matches across a fixed thread pool sized to the
  // available cores, so a large ladder no longer sits at ~1 core's worth of throughput —
  // this only checks the pool-sizing helper (the actual concurrency is an implementation
  // detail that isn't reliably observable from a single-run unit test).
  test("parallelism uses at least one thread and does not exceed the machine's core count") {
    assert(Simulator.parallelism >= 1, "must use at least one worker thread")
    assertEquals(Simulator.parallelism, math.max(1, Runtime.getRuntime.availableProcessors()))
  }

  // The markdown report is what `make sim-tournament` writes to disk: a standings table
  // plus a matchup grid where every cell shows the row strategy's own outcome (win/loss/
  // draw), how many ticks the match took, and (for decisive matches) the short win-
  // condition label — exactly what a reader needs to spot e.g. "every Chaos matchup here
  // ended via plunder" without opening the per-match log.
  test("formatMarkdownReport renders a standings table and a symmetric matchup grid") {
    val names = Seq("linear", "comb", "maze-only")
    val grid = scala.collection.mutable.Map.empty[(String, String), Seq[Simulator.MatchOutcome]]
    val standings = Simulator.tournamentStandings(
      names,
      matchesPerPairing = 1,
      maxTicks = 3_000,
      deltaMs = 100.0,
      onPairingResult = (a, b, outcomes) => grid((a, b)) = outcomes
    )
    val markdown =
      Simulator.formatMarkdownReport(standings, grid.toMap, rounds = 1, matchesPerPairing = 1)
    assert(markdown.contains("# Tournament report"), markdown)
    assert(markdown.contains("## Standings"), markdown)
    assert(markdown.contains("## Matchups"), markdown)
    names.foreach(n => assert(markdown.contains(n), s"expected $n in report:\n$markdown"))
    // Every played pairing must show a non-empty cell in both directions (row=A/col=B and
    // row=B/col=A), each reporting the number of ticks the match took.
    grid.foreach { case ((a, b), outcomes) =>
      val ticks = outcomes.head.ticks.toString
      val rows =
        markdown.linesIterator.filter(l => l.startsWith(s"| $a ") || l.startsWith(s"| $b ")).toSeq
      assert(
        rows.exists(_.contains(ticks)),
        s"expected a cell reporting $ticks ticks for $a vs $b:\n$markdown"
      )
    }
  }

  // Standard single-elimination bracket seeding (1v8, 4v5, 2v7, 3v6 for a field of 8) —
  // every round pairs the best remaining seed against the weakest remaining seed, so the
  // strongest seeds don't meet until as late as possible. Verified against the textbook
  // seeding rather than just "some pairing", since a wrong seeding (e.g. 1v2 in round 1)
  // would silently produce a valid-looking but unfair bracket.
  test("seedOrder produces the standard bracket seeding for 8 and 16 entrants") {
    assertEquals(Simulator.seedOrder(8), Seq(1, 8, 4, 5, 2, 7, 3, 6))
    assertEquals(
      Simulator.seedOrder(16),
      Seq(1, 16, 8, 9, 4, 13, 5, 12, 2, 15, 7, 10, 3, 14, 6, 11)
    )
  }

  // The playoff exists to find "the real winner" beyond the Swiss standings' approximate
  // ranking: a single-elimination bracket among the top N Swiss finishers, seeded 1v8,
  // 4v5, etc. so the strongest seeds meet last. log2(8) = 3 rounds must be played
  // (quarterfinal, semifinal, final), producing exactly one champion drawn from the seeds.
  test("playoffBracket runs log2(n) rounds and crowns exactly one champion from the seeds") {
    val seeds =
      Seq(
        "linear",
        "comb",
        "comb-vertical",
        "maze-only",
        "counter-only",
        "resource-only",
        "maze-counter",
        "balanced"
      )
    val progress = scala.collection.mutable.ArrayBuffer.empty[Int]
    val (rounds, champion) =
      Simulator.playoffBracket(
        seeds,
        matchesPerPairing = 1,
        maxTicks = 3_000,
        deltaMs = 100.0,
        onMatchDone = progress.append(_)
      )
    assertEquals(
      rounds.map(_.size),
      Seq(4, 2, 1),
      "8 seeds must play 4 quarterfinals, 2 semifinals, 1 final"
    )
    assertEquals(
      progress.toList,
      (1 to 7).toList,
      "one progress tick per bracket match played, in order"
    )
    assert(seeds.contains(champion), s"champion $champion must be one of the original seeds")
    // Every round's winners must feed directly into the next round's participants.
    rounds.sliding(2).foreach { case Seq(earlier, later) =>
      val advanced = earlier.map(_.winnerName).toSet
      val nextRoundNames = later.flatMap(m => Seq(m.nameA, m.nameB)).toSet
      assertEquals(
        nextRoundNames,
        advanced,
        s"winners of one round must be exactly the next round's field"
      )
    }
  }

  // A drawn match (both matchesPerPairing games end in a draw, or split evenly) can't
  // advance nobody — the bracket needs a decisive winner every round. The better Swiss
  // seed advances on a tie, same convention many real single-elimination brackets use,
  // rather than an arbitrary "side a always wins" rule that would bias the whole bracket
  // toward whichever strategy happened to be listed first in a pairing.
  test("playoffBracket's tie-break favors the better-seeded strategy when a pairing is undecided") {
    // "linear" vs itself, several rounds deep, always draws (identical strategy, identical
    // starting state) — forces every match in this 2-seed bracket to hit the tie-break.
    val seeds = Seq("linear", "linear")
    val (rounds, champion) =
      Simulator.playoffBracket(seeds, matchesPerPairing = 1, maxTicks = 50, deltaMs = 100.0)
    assertEquals(rounds.size, 1)
    assertEquals(champion, "linear")
  }

  // ── Rock-paper-scissors cycle: a real correctness contract, not exploratory ─────────
  // Unlike every other win-rate number in this file (deliberately not asserted, per this
  // file's own doc — win rates are usually exploratory), the 5 maze-<faction> rush
  // strategies exist specifically to embody a designed cycle (Victoire.md/project owner's
  // explicit statement): Chaos > Science > Nature > Mort > Loi > Chaos. Each leg must be
  // won by the *expected* faction via its *own* win condition — not just any win (a leg
  // won by an unrelated confound, e.g. Loi's sudden-death building count firing before the
  // intended race resolves, does not count, even if the "expected" side happens to be the
  // one it fires for) — see AiStrategy.natureHealClusterBonus's doc and
  // VictoryConditions.WinCondition for the machinery this leans on.
  //
  // matchesPerLeg=15 and a 60% bar (not 100%) accept some genuine run-to-run variance
  // while still catching a leg that's flipped or reduced to a coin flip. seed=0 (via
  // Simulator.runMatch's own seed param) makes this test fully reproducible instead of
  // subject to ComposedStrategy's tie-break Random — before seeding existed, identical
  // code measured anywhere from 13% to 87% on the same leg between runs, making this test
  // impossible to trust either way. Matches within a leg run in parallel (Future.traverse),
  // since each is fully independent — same reasoning as Simulator.runPairingMatches —
  // keeping 75 total matches within the extended munitTimeout above. Every leg is measured
  // (not short-circuited on the first failure) so a single failing leg doesn't hide the
  // status of the other 4.
  test(
    "rock-paper-scissors: each leg is won by its own faction via its own win condition, at least 60% of the time"
  ) {
    case class Leg(
        a: String,
        b: String,
        expectedCondition: VictoryConditions.WinCondition,
        label: String
    )
    val legs = Seq(
      Leg("maze-plunder", "maze-science", VictoryConditions.WinCondition.Chaos, "Chaos > Science"),
      Leg(
        "maze-science",
        "maze-nature",
        VictoryConditions.WinCondition.Science,
        "Science > Nature"
      ),
      Leg("maze-nature", "maze-corruption", VictoryConditions.WinCondition.Nature, "Nature > Mort"),
      Leg("maze-corruption", "maze-law", VictoryConditions.WinCondition.Mort, "Mort > Loi"),
      Leg("maze-law", "maze-plunder", VictoryConditions.WinCondition.Loi, "Loi > Chaos")
    )
    val matchesPerLeg = 15
    given ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    val failures = legs.flatMap { leg =>
      val strategyA = AiStrategy.all(leg.a)
      val strategyB = AiStrategy.all(leg.b)
      val outcomes = scala.concurrent.Await.result(
        scala.concurrent.Future.traverse(1 to matchesPerLeg)(i =>
          scala.concurrent.Future(
            Simulator.runMatch(
              strategyA,
              strategyB,
              maxTicks = 3_500,
              deltaMs = 100.0,
              seed = Some(i.toLong)
            )
          )
        ),
        scala.concurrent.duration.Duration(180, "s")
      )
      val conditions = outcomes.map { o =>
        o.winner.map {
          case "a" =>
            "a" -> VictoryConditions.winningCondition(
              o.finalBattle.player,
              o.finalBattle.ai,
              o.finalBattle
            )
          case "b" =>
            "b" -> VictoryConditions.winningCondition(
              o.finalBattle.ai,
              o.finalBattle.player,
              o.finalBattle
            )
        }
      }
      val decisiveWins = conditions.count(_.contains("a" -> leg.expectedCondition))
      val rate = decisiveWins.toDouble / matchesPerLeg
      Option.when(rate < 0.6)(
        s"${leg.label}: expected >=60% of matches won by ${leg.a} via ${leg.expectedCondition}, " +
          s"got $decisiveWins/$matchesPerLeg (outcomes: ${conditions.mkString(", ")})"
      )
    }
    assert(failures.isEmpty, failures.mkString("\n"))
  }
