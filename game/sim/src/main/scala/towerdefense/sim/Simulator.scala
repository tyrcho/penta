package towerdefense.sim

import towerdefense.domain.*

// Headless AI-vs-AI battle runner: drives BattleEngine.tick with both sides
// strategy-controlled (player slot = side "a", ai slot = side "b") and no rendering, so
// strategies can be compared/tuned by simulating many matches on the JVM.
object Simulator:

  case class MatchOutcome(winner: Option[String], ticks: Int)
  case class Tally(name: String, wins: Int, draws: Int, avgTicks: Double)
  case class SpendingWeights(resourceWeight: Double, counterWeight: Double, layoutWeight: Double)
  case class WeightResult(weights: SpendingWeights, winRate: Double)
  case class Standing(name: String, wins: Int, draws: Int, losses: Int, matches: Int, winRate: Double, elo: Double)

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
    MatchOutcome(winner, ticks)

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
    MatchOutcome(winner, ticks)

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
    Tally(name, wins, draws, avgTicks)

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
  def tournamentStandings(
      names: Seq[String],
      matchesPerPairing: Int,
      maxTicks: Int,
      deltaMs: Double,
      onPairingDone: Int => Unit = _ => (),
      resolve: String => AiStrategy = AiStrategy.all
  ): Seq[Standing] =
    val strategies = names.map(n => n -> resolve(n)).toMap
    val records = scala.collection.mutable.Map.empty[String, (Int, Int, Int)].withDefaultValue((0, 0, 0))
    val ratings = scala.collection.mutable.Map.from(names.map(_ -> EloRating.InitialRating))
    names.combinations(2).zipWithIndex.foreach { case (Seq(nameA, nameB), idx) =>
      val outcomes =
        Seq.fill(matchesPerPairing)(runMatch(strategies(nameA), strategies(nameB), maxTicks, deltaMs))
      outcomes.foreach { outcome =>
        val scoreA = outcome.winner match
          case Some("a") => 1.0
          case Some("b") => 0.0
          case _         => 0.5
        val (newA, newB) = EloRating.updateRatings(ratings(nameA), ratings(nameB), scoreA)
        ratings(nameA) = newA
        ratings(nameB) = newB
      }
      val (winsA, drawsA, lossesA) = record(outcomes, "a")
      val (winsB, drawsB, lossesB) = record(outcomes, "b")
      records(nameA) = addRecord(records(nameA), (winsA, drawsA, lossesA))
      records(nameB) = addRecord(records(nameB), (winsB, drawsB, lossesB))
      onPairingDone(idx + 1)
    }
    names
      .map { name =>
        val (wins, draws, losses) = records(name)
        val matches = wins + draws + losses
        val winRate = if matches == 0 then 0.0 else wins.toDouble / matches
        Standing(name, wins, draws, losses, matches, winRate, ratings(name))
      }
      .sortBy(-_.winRate)

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
    val header = f"${"strategy"}%-14s ${"wins"}%6s ${"draws"}%6s ${"avgTicks"}%10s"
    val rows = tallies.map(t => f"${t.name}%-14s ${t.wins}%6d ${t.draws}%6d ${t.avgTicks}%10.1f")
    (header +: rows).mkString(s"$matches matches\n", "\n", "")

  private def formatStandingsTable(standings: Seq[Standing]): String =
    val header = f"${"strategy"}%-14s ${"wins"}%6s ${"draws"}%6s ${"losses"}%6s ${"winRate"}%8s ${"elo"}%7s"
    val rows = standings.map(s =>
      f"${s.name}%-14s ${s.wins}%6d ${s.draws}%6d ${s.losses}%6d ${s.winRate}%8.2f ${s.elo}%7.0f"
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
  // means all of them, not a hand-picked subset — round-robin, one pairing at a time.
  @main def tournament(args: String*): Unit =
    val matchesPerPairing = args.lift(0).map(_.toInt).getOrElse(10)
    val maxTicks = args.lift(1).map(_.toInt).getOrElse(3_000)
    val deltaMs = args.lift(2).map(_.toDouble).getOrElse(100.0)
    val names = AiStrategy.ladder.map(_._1)
    val reporter = new ProgressReporter("tournament", names.combinations(2).size)
    val standings = tournamentStandings(names, matchesPerPairing, maxTicks, deltaMs, reporter.tick)
    println(s"Tournament: ${names.mkString(", ")} ($matchesPerPairing matches/pairing):")
    println(formatStandingsTable(standings))

  // One-off comparison of build *speed* (RateLimited.buildCooldownMs) crossed with a
  // handful of existing ladder strategies, all in one round-robin — not a permanent ladder
  // addition (AiStrategy.all is untouched; these names only exist inside this run, via
  // tournamentStandings' resolve override). Rates are builds/upgrades per second, converted
  // to the cooldown RateLimited actually stores (buildCooldownMs = 1000/rate).
  // matchesPerPairing = 1 ("single win" per pairing, not averaged over many).
  //
  // baseNames defaults to 5 strategies spanning the full ladder's measured strength (see
  // AiStrategy.ladder's own doc): "linear" (bottom), "resource-maze" and "balanced" (mid),
  // "comb-corruption" and "maze-corruption" (top) — not the entire 16-entry ladder, since
  // 16 x 5 rates = 80 names -> C(80,2) = 3160 single matches, which timing (~10s/match
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
    val ratesPerSec = Seq(1, 2, 3, 5, 8)
    val variants: Map[String, AiStrategy] = (for
      baseName <- baseNames
      baseStrategy = AiStrategy.all(baseName)
      rate <- ratesPerSec
    yield s"$baseName@${rate}bps" -> RateLimited(baseStrategy, buildCooldownMs = 1000.0 / rate)).toMap
    val names = variants.keys.toSeq
    val reporter = new ProgressReporter("rateTournament", names.combinations(2).size)
    val standings = tournamentStandings(names, matchesPerPairing = 1, maxTicks, deltaMs, reporter.tick, variants)
    println(s"Rate tournament: ${names.size} strategy/rate combinations (${baseNames.mkString(", ")} x $ratesPerSec bps), 1 match/pairing:")
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
