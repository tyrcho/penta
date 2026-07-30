package towerdefense.sim

import towerdefense.domain.*

// Headless AI-vs-AI battle runner: drives BattleEngine.tick with both sides
// strategy-controlled (player slot = side "a", ai slot = side "b") and no rendering, so
// strategies can be compared/tuned by simulating many matches on the JVM.
object Simulator:

  // totalResearchA/B: each side's own research investment at match end — the sum of every
  // lab's level (MazeState.researchLevels.values.sum), not any one lab's level alone, so a
  // side that spread research across several labs and one that maxed a single lab both
  // show up as "did research", comparably. Exists to make research visible in the
  // aggregate stats (Tally.avgResearch/Standing.avgResearch below) — a strategy's own
  // build/upgrade choices already drive whether it researches at all (see
  // AiStrategy.maybeResearch), this just surfaces the outcome.
  // finalBattle: the match's last BattleState, kept around (not just discarded after
  // winner/ticks/research are extracted) so a caller can log a cheap final snapshot
  // (MatchLog.snapshotLine) without re-simulating the match just to see it — see
  // tournamentMatchLine below, the reason this field exists.
  case class MatchOutcome(
      winner: Option[String],
      ticks: Int,
      totalResearchA: Int,
      totalResearchB: Int,
      finalBattle: BattleState
  )
  case class Tally(name: String, wins: Int, draws: Int, avgTicks: Double, avgResearch: Double)
  case class SpendingWeights(resourceWeight: Double, counterWeight: Double, layoutWeight: Double)
  case class WeightResult(weights: SpendingWeights, winRate: Double)
  case class Standing(
      name: String,
      wins: Int,
      draws: Int,
      losses: Int,
      matches: Int,
      winRate: Double,
      elo: Double,
      avgResearch: Double
  )

  def runMatch(
      strategyA: AiStrategy,
      strategyB: AiStrategy,
      maxTicks: Int,
      deltaMs: Double
  ): MatchOutcome =
    var battle = BattleState.initial
    var ticks = 0
    while battle.outcome.isEmpty && ticks < maxTicks do
      battle = BattleEngine.tick(battle, deltaMs, aiStrategy = strategyB, playerStrategy = Some(strategyA))
      ticks += 1
    val winner = battle.outcome.map {
      case MatchResult.PlayerWins(_) => "a"
      case MatchResult.AiWins(_)     => "b"
    }
    MatchOutcome(winner, ticks, battle.player.researchLevels.values.sum, battle.ai.researchLevels.values.sum, battle)

  // Same match as runMatch, but drives BattleEngine.tickDetailed instead of tick and
  // formats every event through MatchLog, handing each line to `writeLine` — a separate
  // function rather than a parameter added to runMatch, so runMatches/searchWeights (used
  // by `tune` and every batch comparison) stay exactly as they were, zero cost when
  // logging isn't wanted. `writeLine` is the only I/O seam (a plain String => Unit), so
  // this stays testable with an in-memory buffer instead of a real file — the `run` CLI's
  // `--log` flag is what wires it to a PrintWriter.
  def runLoggedMatch(
      strategyA: AiStrategy,
      strategyB: AiStrategy,
      maxTicks: Int,
      deltaMs: Double,
      logEvery: Int,
      writeLine: String => Unit
  ): MatchOutcome =
    var battle = BattleState.initial
    var ticks = 0
    while battle.outcome.isEmpty && ticks < maxTicks do
      val before = battle
      val (next, events) =
        BattleEngine.tickDetailed(battle, deltaMs, aiStrategy = strategyB, playerStrategy = Some(strategyA))
      battle = next
      ticks += 1
      MatchLog.diff(ticks, before, battle, events).foreach(writeLine)
      if ticks % logEvery == 0 then writeLine(MatchLog.snapshotLine(ticks, battle))
    battle.outcome.foreach(outcome => writeLine(MatchLog.finalLine(ticks, outcome)))
    val winner = battle.outcome.map {
      case MatchResult.PlayerWins(_) => "a"
      case MatchResult.AiWins(_)     => "b"
    }
    MatchOutcome(winner, ticks, battle.player.researchLevels.values.sum, battle.ai.researchLevels.values.sum, battle)

  // Runs `matches` independent games of the two named strategies (resolved via
  // AiStrategy.all) and tallies wins/draws/avg-ticks per side. `onProgress` (default
  // no-op, so this stays a plain pure-ish function for tests/other callers) is called
  // with the number of matches completed so far, in order — the `run` CLI wires it to a
  // ProgressReporter per CLAUDE.md's "long-running jobs report an ETA to stderr" rule.
  def runMatches(
      nameA: String,
      nameB: String,
      matches: Int,
      maxTicks: Int,
      deltaMs: Double,
      onProgress: Int => Unit = _ => ()
  ): Seq[Tally] =
    val strategyA = AiStrategy.all(nameA)
    val strategyB = AiStrategy.all(nameB)
    val outcomes = (1 to matches).map { i =>
      val outcome = runMatch(strategyA, strategyB, maxTicks, deltaMs)
      onProgress(i)
      outcome
    }
    Seq(tallyFor(nameA, "a", outcomes), tallyFor(nameB, "b", outcomes))

  private def tallyFor(name: String, side: String, outcomes: Seq[MatchOutcome]): Tally =
    val wins = outcomes.count(_.winner.contains(side))
    val draws = outcomes.count(_.winner.isEmpty)
    val avgTicks =
      if outcomes.isEmpty then 0.0 else outcomes.map(_.ticks).sum.toDouble / outcomes.size
    val avgResearch =
      if outcomes.isEmpty then 0.0
      else outcomes.map(o => if side == "a" then o.totalResearchA else o.totalResearchB).sum.toDouble / outcomes.size
    Tally(name, wins, draws, avgTicks, avgResearch)

  // Every pairing among `names` (each combination played once, `matchesPerPairing`
  // matches within it — not the full N² including mirror matches, since a strategy
  // playing itself from both slots doesn't reveal anything runMatches("x","x",...)
  // doesn't already), aggregated into one win/draw/loss record per strategy across all
  // its pairings. Ranked by win rate, not wins, since every strategy plays the same
  // number of matches here (names.size - 1 pairings) but that stops being true if this
  // is ever called with an uneven subset.
  //
  // Also tracks an EloRating per strategy, updated match by match in the same
  // pairing-by-pairing order the round-robin plays them (all strategies start at
  // EloRating.InitialRating). Win rate alone can't tell an unbeaten record against weak
  // opposition apart from one earned against the strongest strategies on the ladder —
  // Elo is the "who did those wins come against" signal win rate can't give. Standings
  // still rank by win rate, not Elo, to keep today's ranking behavior unchanged; Elo
  // rides along as an extra column (see formatStandingsTable) rather than replacing it.
  // resolve: how to turn a name into a strategy — defaults to the canonical AiStrategy.all
  // (today's exact behavior), but overridable so a caller can run a tournament over
  // strategies that aren't (and shouldn't be) registered on the real ladder — e.g.
  // rateTournament's RateLimited-wrapped variants, a one-off comparison rather than a
  // permanent addition to AiStrategy.all.
  // logLine: called once per individual match (not once per pairing) with a cheap
  // one-line summary — no-op by default so every existing caller/test pays nothing for
  // it; the `tournament` CLI wires it to a PrintWriter so every match played is logged
  // preemptively, rather than needing a separate reproduction step (not even guaranteed
  // to reproduce the same outcome, since ComposedStrategy's tie-breaks are unseeded here)
  // after the fact to see why an unusual result happened.
  // The three mutable per-name accumulators tournamentStandings/swissStandings both keep
  // (and both used to thread through as three separate parameters everywhere) — bundled so
  // playPairing/pairRound/buildStandings each take one parameter for "the running tally"
  // instead of three.
  private class MatchAccumulator(
      val ratings: scala.collection.mutable.Map[String, Double],
      val records: scala.collection.mutable.Map[String, (Int, Int, Int)],
      val researchTotals: scala.collection.mutable.Map[String, Int]
  )

  private def newAccumulator(names: Seq[String]): MatchAccumulator =
    MatchAccumulator(
      ratings = scala.collection.mutable.Map.from(names.map(_ -> EloRating.InitialRating)),
      records =
        scala.collection.mutable.Map.empty[String, (Int, Int, Int)].withDefaultValue((0, 0, 0)),
      researchTotals = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
    )

  // Plays every match for one pairing and folds the results into `acc` — the exact same
  // sequence (run matches, log each one, update Elo match by match, then update the
  // win/draw/loss record and research total once for the whole pairing) both
  // tournamentStandings and swissStandings used to duplicate in full.
  private def playPairing(
      nameA: String,
      nameB: String,
      strategies: Map[String, AiStrategy],
      matchesPerPairing: Int,
      maxTicks: Int,
      deltaMs: Double,
      logLine: String => Unit,
      acc: MatchAccumulator
  ): Unit =
    val outcomes = (1 to matchesPerPairing).map { m =>
      val outcome = runMatch(strategies(nameA), strategies(nameB), maxTicks, deltaMs)
      logLine(tournamentMatchLine(nameA, nameB, m, matchesPerPairing, outcome))
      outcome
    }
    outcomes.foreach(applyEloUpdate(nameA, nameB, acc.ratings, _))
    acc.records(nameA) = addRecord(acc.records(nameA), record(outcomes, "a"))
    acc.records(nameB) = addRecord(acc.records(nameB), record(outcomes, "b"))
    acc.researchTotals(nameA) = acc.researchTotals(nameA) + outcomes.map(_.totalResearchA).sum
    acc.researchTotals(nameB) = acc.researchTotals(nameB) + outcomes.map(_.totalResearchB).sum

  private def applyEloUpdate(
      nameA: String,
      nameB: String,
      ratings: scala.collection.mutable.Map[String, Double],
      outcome: MatchOutcome
  ): Unit =
    val scoreA = outcome.winner match
      case Some("a") => 1.0
      case Some("b") => 0.0
      case _         => 0.5
    val (newA, newB) = EloRating.updateRatings(ratings(nameA), ratings(nameB), scoreA)
    ratings(nameA) = newA
    ratings(nameB) = newB

  def tournamentStandings(
      names: Seq[String],
      matchesPerPairing: Int,
      maxTicks: Int,
      deltaMs: Double,
      onPairingDone: Int => Unit = _ => (),
      resolve: String => AiStrategy = AiStrategy.all,
      logLine: String => Unit = _ => ()
  ): Seq[Standing] =
    val strategies = names.map(n => n -> resolve(n)).toMap
    val acc = newAccumulator(names)
    names.combinations(2).zipWithIndex.foreach { case (Seq(nameA, nameB), idx) =>
      playPairing(nameA, nameB, strategies, matchesPerPairing, maxTicks, deltaMs, logLine, acc)
      onPairingDone(idx + 1)
    }
    buildStandings(names, acc).sortBy(-_.winRate)

  // One line per match: pairing, which match within the pairing, winner, and a final
  // resource/plunder/corrupted snapshot (MatchLog.snapshotLine — cheap, no per-tick
  // diffing) — enough to see e.g. "both sides only ever built Cave, so the plunder-race
  // victory condition's 2x-opponent target chased itself into a draw" without needing a
  // separate `sim/run --log` reproduction.
  private def tournamentMatchLine(
      nameA: String,
      nameB: String,
      matchIndex: Int,
      matchesPerPairing: Int,
      outcome: MatchOutcome
  ): String =
    val winner = outcome.winner.getOrElse("draw")
    s"$nameA vs $nameB  match $matchIndex/$matchesPerPairing  winner=$winner  " +
      MatchLog.snapshotLine(outcome.ticks, outcome.finalBattle)

  // ceil, not floor/round: a Swiss tournament needs enough rounds to fully separate a
  // ranking as the field doubles, and ceil is what guarantees that (a 33-name field needs
  // a 6th round just as much as a 64-name one does, floor would round both down to 5).
  private[sim] def swissRounds(playerCount: Int): Int =
    math.ceil(math.log(playerCount.toDouble) / math.log(2.0)).toInt

  // Swiss-system pairing instead of a full round-robin: each round ranks every player by
  // score (win=1, draw=0.5 — the same accumulator the final Standing.wins/draws produce)
  // first and Elo second, then folds the ranked field in half and pairs rank i of the top
  // half against rank i of the bottom half ("pair the best ranked with the best of the
  // second half, and so on"), skipping any pairing already played this tournament where
  // an alternative is available. Cuts a 25-name ladder's 300-pairing round-robin down to
  // swissRounds(25) = 5 rounds x 12 pairings = 60 matches — enough rounds to separate a
  // clear ranking without needing to play every possible pairing.
  //
  // Odd-sized fields can't fold-pair everyone every round — the lowest-ranked player who
  // hasn't already had a bye sits out instead, credited a full win (and a played "match")
  // in the standings, same as beating a real opponent, but touching no Elo since no game
  // was actually simulated.
  //
  // The rematch-avoidance search below is greedy, not a globally-optimal Swiss matcher: it
  // walks the top half in rank order, giving each the best-ranked still-available bottom-
  // half opponent it hasn't already played, falling back to a forced rematch only if
  // every remaining bottom-half opponent has already been played (only plausible on a
  // very small or heavily-rematched field) — simple, and sufficient for "avoid matches
  // which were already played" without needing a real matching-algorithm dependency.
  // Decides this round's pairing and immediately credits a bye's win (the bye's own record
  // update is bookkeeping, not pairing logic, but folding it in here keeps swissStandings'
  // own round loop to "get this round's pairs, then play them" instead of a third
  // responsibility). Never runs a match itself.
  private def pairRound(
      names: Seq[String],
      acc: MatchAccumulator,
      hadBye: scala.collection.mutable.Set[String],
      played: scala.collection.mutable.Set[Set[String]]
  ): Seq[(String, String)] =
    def scoreOf(name: String): Double =
      val (wins, draws, _) = acc.records(name)
      wins + 0.5 * draws
    val ranked = names.sortBy(n => (-scoreOf(n), -acc.ratings(n)))
    val (byeName, field) =
      if ranked.size % 2 == 1 then
        val bye = ranked.reverseIterator.find(n => !hadBye.contains(n)).getOrElse(ranked.last)
        (Some(bye), ranked.filterNot(_ == bye))
      else (None, ranked)
    byeName.foreach { name =>
      hadBye += name
      acc.records(name) = addRecord(acc.records(name), (1, 0, 0))
    }
    val half = field.size / 2
    val bottomPool = scala.collection.mutable.ListBuffer.from(field.drop(half))
    field.take(half).map { nameA =>
      val idx = bottomPool.indexWhere(nameB => !played.contains(Set(nameA, nameB)))
      val nameB = if idx >= 0 then bottomPool.remove(idx) else bottomPool.remove(0)
      (nameA, nameB)
    }

  def swissStandings(
      names: Seq[String],
      matchesPerPairing: Int,
      maxTicks: Int,
      deltaMs: Double,
      onRoundDone: Int => Unit = _ => (),
      resolve: String => AiStrategy = AiStrategy.all,
      logLine: String => Unit = _ => ()
  ): Seq[Standing] =
    val strategies = names.map(n => n -> resolve(n)).toMap
    val acc = newAccumulator(names)
    val hadBye = scala.collection.mutable.Set.empty[String]
    val played = scala.collection.mutable.Set.empty[Set[String]]

    val rounds = swissRounds(names.size)
    (1 to rounds).foreach { round =>
      pairRound(names, acc, hadBye, played).foreach { case (nameA, nameB) =>
        played += Set(nameA, nameB)
        playPairing(nameA, nameB, strategies, matchesPerPairing, maxTicks, deltaMs, logLine, acc)
      }
      onRoundDone(round)
    }
    buildStandings(names, acc).sortBy(s => (-(s.wins + 0.5 * s.draws), -s.elo))

  // Turns the running per-name accumulator (both tournamentStandings and swissStandings
  // keep one) into unsorted Standing rows — each caller applies its own sort (win rate
  // alone vs. score-then-Elo) afterward.
  private def buildStandings(names: Seq[String], acc: MatchAccumulator): Seq[Standing] =
    names.map { name =>
      val (wins, draws, losses) = acc.records(name)
      val matches = wins + draws + losses
      val winRate = if matches == 0 then 0.0 else wins.toDouble / matches
      val avgResearch = if matches == 0 then 0.0 else acc.researchTotals(name).toDouble / matches
      Standing(name, wins, draws, losses, matches, winRate, acc.ratings(name), avgResearch)
    }

  private def record(outcomes: Seq[MatchOutcome], side: String): (Int, Int, Int) =
    val wins = outcomes.count(_.winner.contains(side))
    val draws = outcomes.count(_.winner.isEmpty)
    val losses = outcomes.size - wins - draws
    (wins, draws, losses)

  private def addRecord(a: (Int, Int, Int), b: (Int, Int, Int)): (Int, Int, Int) =
    (a._1 + b._1, a._2 + b._2, a._3 + b._3)

  // A sweep, not a training loop: every SpendingWeights combination on the grid is run
  // head-to-head against `baseline` and ranked by win rate — the "tunable variables
  // validated via simulation" mechanism from the plan. Each grid point builds a
  // ComposedStrategy(FreeformLayout, WeightedSpending(resourceWeight, counterWeight),
  // layoutWeight), i.e. it only sweeps FreeformLayout-based combinations — TemplateLayout
  // combinations (comb/comb-vertical + a SpendingPolicy) are explored by hand via
  // `sim/run` head-to-head matches instead, since there's no continuous "wall shape"
  // parameter to grid-search over.
  def searchWeights(
      baseline: String,
      matchesPerPoint: Int,
      step: Double,
      maxTicks: Int,
      deltaMs: Double,
      onPointDone: Int => Unit = _ => ()
  ): Seq[WeightResult] =
    val baselineStrategy = AiStrategy.all(baseline)
    weightGrid(step).zipWithIndex.map { case (weights, idx) =>
      val candidate = ComposedStrategy(
        FreeformLayout,
        WeightedSpending(weights.resourceWeight, weights.counterWeight),
        layoutWeight = weights.layoutWeight
      )
      val outcomes =
        Seq.fill(matchesPerPoint)(runMatch(candidate, baselineStrategy, maxTicks, deltaMs))
      val wins = outcomes.count(_.winner.contains("a"))
      onPointDone(idx + 1)
      WeightResult(weights, wins.toDouble / matchesPerPoint)
    }.sortBy(-_.winRate)

  private def weightGrid(step: Double): Seq[SpendingWeights] =
    val values = Iterator.iterate(0.0)(_ + step).takeWhile(_ <= 1.0 + 1e-9).toSeq
    for
      resource <- values
      counter <- values
      layout <- values
    yield SpendingWeights(resource, counter, layout)

  private def formatTallyTable(tallies: Seq[Tally], matches: Int): String =
    val header = f"${"strategy"}%-14s ${"wins"}%6s ${"draws"}%6s ${"avgTicks"}%10s ${"avgResearch"}%11s"
    val rows =
      tallies.map(t => f"${t.name}%-14s ${t.wins}%6d ${t.draws}%6d ${t.avgTicks}%10.1f ${t.avgResearch}%11.1f")
    (header +: rows).mkString(s"$matches matches\n", "\n", "")

  private def formatStandingsTable(standings: Seq[Standing]): String =
    val header =
      f"${"strategy"}%-14s ${"wins"}%6s ${"draws"}%6s ${"losses"}%6s ${"winRate"}%8s ${"elo"}%7s ${"avgResearch"}%11s"
    val rows = standings.map(s =>
      f"${s.name}%-14s ${s.wins}%6d ${s.draws}%6d ${s.losses}%6d ${s.winRate}%8.2f ${s.elo}%7.0f ${s.avgResearch}%11.1f"
    )
    (header +: rows).mkString("\n")

  private def formatWeightTable(results: Seq[WeightResult], top: Int): String =
    val header = f"${"resource"}%9s ${"counter"}%9s ${"layout"}%9s ${"winRate"}%9s"
    val rows = results.take(top).map(r =>
      f"${r.weights.resourceWeight}%9.2f ${r.weights.counterWeight}%9.2f ${r.weights.layoutWeight}%9.2f ${r.winRate}%9.2f"
    )
    (header +: rows).mkString("\n")

  // maxTicks default: 3_000 ticks * 100ms = 300 virtual seconds, comfortably past the
  // time either strategy needs to reach a victory condition on the 12x12 grid — a match
  // that hits this cap and draws is a sign the pairing is a stalemate, not that the cap
  // is too low.
  // Parsed by hand (not plain @main defaults) because sbt's `runMain` — unlike the
  // standalone `scala` runner — doesn't fill in a Scala 3 @main's default arguments when
  // trailing ones are omitted; it demands every positional argument or none. `--log
  // <path>`/`--log-every <n>` are scanned out and stripped before that positional parsing
  // runs, so they can appear anywhere in `args` without disturbing today's
  // `run linear balanced 100`-style usage. `--log` only ever drives `matches`' *first*
  // match — a per-tick transcript of a 100-match batch would be enormous and useless;
  // pair `--log` with a small `matches` count (typically 1).
  @main def run(args: String*): Unit =
    val (logPath, logEvery, rest) = extractLogFlags(args.toList)
    val a = rest.lift(0).getOrElse("linear")
    val b = rest.lift(1).getOrElse("balanced")
    val matches = rest.lift(2).map(_.toInt).getOrElse(100)
    val maxTicks = rest.lift(3).map(_.toInt).getOrElse(3_000)
    val deltaMs = rest.lift(4).map(_.toDouble).getOrElse(100.0)
    logPath match
      case Some(path) =>
        val writer = new java.io.PrintWriter(path)
        try
          val strategyA = AiStrategy.all(a)
          val strategyB = AiStrategy.all(b)
          val outcome = runLoggedMatch(strategyA, strategyB, maxTicks, deltaMs, logEvery, writer.println)
          val winner = outcome.winner.getOrElse("draw")
          println(s"Logged 1 match ($a vs $b, ${outcome.ticks} ticks, winner=$winner) to $path")
        finally writer.close()
      case None =>
        val reporter = new ProgressReporter(s"$a vs $b", matches)
        println(formatTallyTable(runMatches(a, b, matches, maxTicks, deltaMs, reporter.tick), matches))

  private def extractLogFlags(args: List[String]): (Option[String], Int, List[String]) =
    args match
      case "--log" :: path :: rest =>
        val (_, logEvery, rest2) = extractLogFlags(rest)
        (Some(path), logEvery, rest2)
      case "--log-every" :: n :: rest =>
        val (logPath, _, rest2) = extractLogFlags(rest)
        (logPath, n.toInt, rest2)
      case other :: rest =>
        val (logPath, logEvery, rest2) = extractLogFlags(rest)
        (logPath, logEvery, other :: rest2)
      case Nil => (None, 100, Nil)

  // Defaults to every strategy on AiStrategy.ladder — "a mini tournament across the AIs"
  // means all of them, not a hand-picked subset. Swiss rounds (swissStandings), not a full
  // round-robin: a 25-entry ladder's C(25,2)=300 pairings took ~2h17m at 2 matches/pairing
  // last measured — swissRounds(25)=5 rounds x 12 pairings cuts that down to a fraction of
  // the matches while still separating a clear ranking. Every match played is logged
  // preemptively (one line: pairing, winner, final snapshot) to `logPath` as it happens,
  // not just on request after the fact — diagnosing an odd result (e.g. an unexpectedly
  // high draw rate at the ladder's slowest tier) used to mean manually reproducing it with
  // `sim/run --log` afterwards, which isn't even guaranteed to reproduce the same outcome
  // since ComposedStrategy's tie-breaks are unseeded here.
  @main def tournament(args: String*): Unit =
    val matchesPerPairing = args.lift(0).map(_.toInt).getOrElse(1)
    val maxTicks = args.lift(1).map(_.toInt).getOrElse(3_000)
    val deltaMs = args.lift(2).map(_.toDouble).getOrElse(100.0)
    val logPath = args.lift(3).getOrElse("tournament-matches.log")
    val names = AiStrategy.ladder.map(_._1)
    val rounds = swissRounds(names.size)
    val reporter = new ProgressReporter("tournament", rounds)
    val writer = new java.io.PrintWriter(logPath)
    val standings =
      try swissStandings(names, matchesPerPairing, maxTicks, deltaMs, reporter.tick, logLine = writer.println)
      finally writer.close()
    println(
      s"Swiss tournament: ${names.size} strategies, $rounds rounds, " +
        s"$matchesPerPairing match(es)/pairing (per-match log: $logPath):"
    )
    println(formatStandingsTable(standings))

  // One-off comparison of build *speed* (RateLimited.buildCooldownMs) crossed with a
  // handful of existing ladder strategies, all in one round-robin — not a permanent ladder
  // addition (AiStrategy.all is untouched; these names only exist inside this run, via
  // tournamentStandings' resolve override). Speeds are seconds-per-build periods, not a
  // builds/sec rate: 1 second (the fastest — not faster than a human could plausibly click)
  // down to 1 build every 8 seconds (the slowest), converted straight to the cooldown
  // RateLimited stores (buildCooldownMs = periodSec * 1000). matchesPerPairing = 1 ("single
  // win" per pairing, not averaged over many).
  //
  // baseNames defaults to 5 strategies spanning the full ladder's measured strength (see
  // AiStrategy.ladder's own doc): "linear" (bottom), "resource-maze" and "balanced" (mid),
  // "comb-corruption" and "maze-corruption" (top) — not the entire 16-entry ladder, since
  // 16 x 5 periods = 80 names -> C(80,2) = 3160 single matches, which timing (~10s/match
  // observed via `sim/run linear balanced 3`) puts at several hours; 5 x 5 = 25 names ->
  // C(25,2) = 300 matches is a comparable-effort, comparable-signal stand-in. Pass a
  // comma-separated 3rd arg to override which base strategies are included.
  @main def rateTournament(args: String*): Unit =
    val maxTicks = args.lift(0).map(_.toInt).getOrElse(3_000)
    val deltaMs = args.lift(1).map(_.toDouble).getOrElse(100.0)
    val baseNames = args
      .lift(2)
      .map(_.split(",").toSeq)
      .getOrElse(Seq("linear", "resource-maze", "balanced", "comb-corruption", "maze-corruption"))
    val secondsPerBuild = Seq(1, 2, 3, 5, 8)
    val variants: Map[String, AiStrategy] = (for
      baseName <- baseNames
      baseStrategy = AiStrategy.all(baseName)
      periodSec <- secondsPerBuild
    yield s"$baseName@${periodSec}s" -> RateLimited(baseStrategy, buildCooldownMs = periodSec * 1_000.0)).toMap
    val names = variants.keys.toSeq
    val reporter = new ProgressReporter("rateTournament", names.combinations(2).size)
    val standings = tournamentStandings(names, matchesPerPairing = 1, maxTicks, deltaMs, reporter.tick, variants)
    println(
      s"Rate tournament: ${names.size} strategy/speed combinations " +
        s"(${baseNames.mkString(", ")} x $secondsPerBuild sec/build), 1 match/pairing:"
    )
    println(formatStandingsTable(standings))

  @main def tune(args: String*): Unit =
    val baseline = args.lift(0).getOrElse("linear")
    val matchesPerPoint = args.lift(1).map(_.toInt).getOrElse(50)
    val step = args.lift(2).map(_.toDouble).getOrElse(0.25)
    val maxTicks = args.lift(3).map(_.toInt).getOrElse(3_000)
    val deltaMs = args.lift(4).map(_.toDouble).getOrElse(100.0)
    val reporter = new ProgressReporter(s"tune vs $baseline", weightGrid(step).size)
    val results = searchWeights(baseline, matchesPerPoint, step, maxTicks, deltaMs, reporter.tick)
    println(s"FreeformLayout+WeightedSpending weight sweep vs '$baseline' ($matchesPerPoint matches/point, step $step):")
    println(formatWeightTable(results, top = 10))
