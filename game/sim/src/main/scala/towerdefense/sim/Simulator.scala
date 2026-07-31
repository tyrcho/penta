package towerdefense.sim

import towerdefense.domain.*
import towerdefense.domain.ai.*
import towerdefense.domain.combat.*
import towerdefense.domain.economy.*
import towerdefense.domain.grid.*

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

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

  // seed: when given, reseeds both strategies (AiStrategy.reseed — a no-op for anything
  // without internal randomness, real for ComposedStrategy's own tie-break Random) before
  // playing, so the SAME (strategyA, strategyB, seed) triple always plays out identically.
  // strategyB gets seed+1, not the same seed as strategyA, so the two sides' tie-breaks
  // don't correlate. Default None preserves every existing caller's unseeded behavior —
  // added specifically to make batch measurements (runMatches' own seed below, most
  // obviously the rock-paper-scissors tuning loop) reproducible: without this, repeated
  // measurements of the identical two strategies swung by tens of percentage points
  // between runs with zero code changes in between, purely from tie-break noise.
  def runMatch(
      strategyA: AiStrategy,
      strategyB: AiStrategy,
      maxTicks: Int,
      deltaMs: Double,
      seed: Option[Long] = None
  ): MatchOutcome =
    val (seededA, seededB) = seed match
      case Some(s) => (strategyA.reseed(s), strategyB.reseed(s + 1))
      case None    => (strategyA, strategyB)
    var battle = BattleState.initial
    var ticks = 0
    while battle.outcome.isEmpty && ticks < maxTicks do
      battle =
        BattleEngine.tick(battle, deltaMs, aiStrategy = seededB, playerStrategy = Some(seededA))
      ticks += 1
    val winner = battle.outcome.map {
      case MatchResult.PlayerWins(_) => "a"
      case MatchResult.AiWins(_)     => "b"
    }
    MatchOutcome(
      winner,
      ticks,
      battle.player.researchLevels.values.sum,
      battle.ai.researchLevels.values.sum,
      battle
    )

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
      writeLine: String => Unit,
      seed: Option[Long] = None
  ): MatchOutcome =
    val (seededA, seededB) = seed match
      case Some(s) => (strategyA.reseed(s), strategyB.reseed(s + 1))
      case None    => (strategyA, strategyB)
    var battle = BattleState.initial
    var ticks = 0
    while battle.outcome.isEmpty && ticks < maxTicks do
      val before = battle
      val (next, events) =
        BattleEngine.tickDetailed(
          battle,
          deltaMs,
          aiStrategy = seededB,
          playerStrategy = Some(seededA)
        )
      battle = next
      ticks += 1
      MatchLog.diff(ticks, before, battle, events).foreach(writeLine)
      if ticks % logEvery == 0 then writeLine(MatchLog.snapshotLine(ticks, battle))
    battle.outcome.foreach(outcome => writeLine(MatchLog.finalLine(ticks, outcome)))
    val winner = battle.outcome.map {
      case MatchResult.PlayerWins(_) => "a"
      case MatchResult.AiWins(_)     => "b"
    }
    MatchOutcome(
      winner,
      ticks,
      battle.player.researchLevels.values.sum,
      battle.ai.researchLevels.values.sum,
      battle
    )

  // Runs `matches` independent games of the two named strategies (resolved via
  // AiStrategy.all) and tallies wins/draws/avg-ticks per side. `onProgress` (default
  // no-op, so this stays a plain pure-ish function for tests/other callers) is called
  // with the number of matches completed so far, in order — the `run` CLI wires it to a
  // ProgressReporter per CLAUDE.md's "long-running jobs report an ETA to stderr" rule.
  // baseSeed: when given, match i uses runMatch's own seed = baseSeed + i (distinct per
  // match, so they don't all replay the identical game, but fully reproducible run to run
  // — same reasoning as runMatch's own seed param).
  def runMatches(
      nameA: String,
      nameB: String,
      matches: Int,
      maxTicks: Int,
      deltaMs: Double,
      onProgress: Int => Unit = _ => (),
      baseSeed: Option[Long] = None
  ): Seq[Tally] =
    val strategyA = AiStrategy.all(nameA)
    val strategyB = AiStrategy.all(nameB)
    val outcomes = (1 to matches).map { i =>
      val outcome = runMatch(strategyA, strategyB, maxTicks, deltaMs, seed = baseSeed.map(_ + i))
      onProgress(i)
      outcome
    }
    Seq(tallyFor(nameA, "a", outcomes), tallyFor(nameB, "b", outcomes))

  // Every pairing's matches are independent (runMatch reads no shared mutable state), so
  // they're the part worth spreading across cores — the elo/records aggregation that
  // follows stays single-threaded and in original pairing order, since Elo updates are
  // path-dependent (see tournamentStandings' own doc). Exposed at `private[sim]` (not
  // fully private) purely so a test can confirm it actually scales with the machine
  // instead of silently pinning to 1 thread.
  private[sim] def parallelism: Int = math.max(1, Runtime.getRuntime.availableProcessors())

  private def withParallelExecutor[A](work: ExecutionContext => A): A =
    val pool = Executors.newFixedThreadPool(parallelism)
    try work(ExecutionContext.fromExecutor(pool))
    finally pool.shutdown()

  // Runs every match of a pairing in parallel (matchesPerPairing repeats of the same two
  // strategies don't depend on each other either) and returns them in match-index order —
  // Future.traverse preserves input order regardless of completion order, so callers that
  // fold outcomes sequentially afterward (elo, per-match logging) see the same order they
  // would have from a plain sequential loop.
  private def runPairingMatches(
      strategyA: AiStrategy,
      strategyB: AiStrategy,
      matchesPerPairing: Int,
      maxTicks: Int,
      deltaMs: Double
  )(using ExecutionContext): Future[Seq[MatchOutcome]] =
    Future.traverse(1 to matchesPerPairing)(_ =>
      Future(runMatch(strategyA, strategyB, maxTicks, deltaMs))
    )

  private def tallyFor(name: String, side: String, outcomes: Seq[MatchOutcome]): Tally =
    val wins = outcomes.count(_.winner.contains(side))
    val draws = outcomes.count(_.winner.isEmpty)
    val avgTicks =
      if outcomes.isEmpty then 0.0 else outcomes.map(_.ticks).sum.toDouble / outcomes.size
    val avgResearch =
      if outcomes.isEmpty then 0.0
      else
        outcomes
          .map(o => if side == "a" then o.totalResearchA else o.totalResearchB)
          .sum
          .toDouble / outcomes.size
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
  // creditPairingResult/pairRound/buildStandings each take one parameter for "the running
  // tally" instead of three.
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

  // onPairingResult: called once per pairing with its full outcome list, in addition to
  // logLine's per-match text — the `tournament` CLI uses this (not logLine, which is
  // text-only) to build the markdown matchup grid without re-parsing its own log lines.
  //
  // Every pairing's matches run across a fixed-size thread pool (see `parallelism`) since
  // runMatch reads no state shared across pairings — only the elo/records aggregation
  // below has to stay single-threaded and in original pairing order: Elo is path-dependent
  // (a strategy's rating going into pairing N depends on the outcome of every earlier
  // pairing it played), so parallelizing that fold would make ratings non-deterministic.
  def tournamentStandings(
      names: Seq[String],
      matchesPerPairing: Int,
      maxTicks: Int,
      deltaMs: Double,
      onPairingDone: Int => Unit = _ => (),
      resolve: String => AiStrategy = AiStrategy.all,
      logLine: String => Unit = _ => (),
      onPairingResult: (String, String, Seq[MatchOutcome]) => Unit = (_, _, _) => ()
  ): Seq[Standing] =
    val strategies = names.map(n => n -> resolve(n)).toMap
    val acc = newAccumulator(names)
    val pairings = names.combinations(2).toSeq
    val completed = new AtomicInteger(0)
    val pairingOutcomes = withParallelExecutor { ec =>
      given ExecutionContext = ec
      val futures = pairings.map { case Seq(nameA, nameB) =>
        runPairingMatches(
          strategies(nameA),
          strategies(nameB),
          matchesPerPairing,
          maxTicks,
          deltaMs
        ).map { outcomes =>
          onPairingDone(completed.incrementAndGet())
          (nameA, nameB, outcomes)
        }
      }
      Await.result(Future.sequence(futures), Duration.Inf)
    }
    pairingOutcomes.foreach { case (nameA, nameB, outcomes) =>
      outcomes.zipWithIndex.foreach { case (outcome, m) =>
        logLine(tournamentMatchLine(nameA, nameB, m + 1, matchesPerPairing, outcome))
      }
      onPairingResult(nameA, nameB, outcomes)
      creditPairingResult(nameA, nameB, outcomes, acc)
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

  // One played bracket pairing: which round it was, both entrants, every game played
  // between them (matchesPerPairing might be >1), and the name that advances.
  case class BracketMatch(
      round: String,
      nameA: String,
      nameB: String,
      outcomes: Seq[MatchOutcome],
      winnerName: String
  )

  // Standard single-elimination seeding: for a bracket of size n (a power of two), returns
  // the seed numbers (1 = best) in bracket-slot order, e.g. seedOrder(8) =
  // Seq(1,8,4,5,2,7,3,6) so round 1 pairs 1v8, 4v5, 2v7, 3v6 — the textbook recursive
  // construction (each subtree's slot s is paired with n+1-s) that keeps the best seeds
  // apart until as late in the bracket as possible, instead of 1v2 meeting in round 1.
  private[sim] def seedOrder(n: Int): Seq[Int] =
    if n <= 1 then Seq(1)
    else seedOrder(n / 2).flatMap(s => Seq(s, n + 1 - s))

  private def bracketRoundName(fieldSize: Int): String =
    fieldSize match
      case 2 => "Final"
      case 4 => "Semifinal"
      case 8 => "Quarterfinal"
      case n => s"Round of $n"

  // Decides a single pairing's advancing name: whichever side won more of the
  // matchesPerPairing games, or — if genuinely tied (including an all-draw pairing, common
  // between mirror-image strategies or a short maxTicks) — whichever seed ranked better in
  // the standings this bracket was seeded from. That tie-break (not an arbitrary "side a
  // wins") is what keeps the bracket from silently favoring whichever name happened to
  // sort first in a pairing.
  private def bracketWinner(
      nameA: String,
      nameB: String,
      outcomes: Seq[MatchOutcome],
      rank: Map[String, Int]
  ): String =
    val winsA = outcomes.count(_.winner.contains("a"))
    val winsB = outcomes.count(_.winner.contains("b"))
    if winsA > winsB then nameA
    else if winsB > winsA then nameB
    else if rank.getOrElse(nameA, Int.MaxValue) <= rank.getOrElse(nameB, Int.MaxValue) then nameA
    else nameB

  // A single-elimination playoff among `seeds` (best standing first) to find the actual
  // head-to-head winner: the Swiss/round-robin standings above only approximate a ranking
  // (every strategy plays a handful of rounds, not everyone it could meet), so the top of
  // the ladder can still hide ties or near-ties a direct elimination bracket resolves
  // definitively. `seeds.size` must be a power of two (8 or 16 in practice — the `tournament`
  // CLI's own choices). Runs the same way tournamentStandings/swissStandings do: every
  // round's pairings in parallel across `parallelism` threads, only the round-to-round
  // progression (this round's winners become next round's field) staying sequential.
  def playoffBracket(
      seeds: Seq[String],
      matchesPerPairing: Int,
      maxTicks: Int,
      deltaMs: Double,
      resolve: String => AiStrategy = AiStrategy.all,
      logLine: String => Unit = _ => (),
      onMatchDone: Int => Unit = _ => ()
  ): (Seq[Seq[BracketMatch]], String) =
    require(
      seeds.nonEmpty && (seeds.size & (seeds.size - 1)) == 0,
      s"bracket size must be a power of two, got ${seeds.size}"
    )
    val strategies = seeds.map(n => n -> resolve(n)).toMap
    val rank = seeds.zipWithIndex.toMap
    val completed = new AtomicInteger(0)
    val rounds = scala.collection.mutable.ArrayBuffer.empty[Seq[BracketMatch]]
    withParallelExecutor { ec =>
      given ExecutionContext = ec
      var current = seedOrder(seeds.size).map(s => seeds(s - 1))
      while current.size > 1 do
        val roundName = bracketRoundName(current.size)
        val futures = current.grouped(2).toSeq.map { case Seq(nameA, nameB) =>
          runPairingMatches(
            strategies(nameA),
            strategies(nameB),
            matchesPerPairing,
            maxTicks,
            deltaMs
          )
            .map(outcomes => (nameA, nameB, outcomes))
        }
        val results = Await.result(Future.sequence(futures), Duration.Inf)
        val roundMatches = results.map { case (nameA, nameB, outcomes) =>
          outcomes.zipWithIndex.foreach { case (outcome, m) =>
            logLine(tournamentMatchLine(nameA, nameB, m + 1, matchesPerPairing, outcome))
          }
          onMatchDone(completed.incrementAndGet())
          BracketMatch(
            roundName,
            nameA,
            nameB,
            outcomes,
            bracketWinner(nameA, nameB, outcomes, rank)
          )
        }
        rounds += roundMatches
        current = roundMatches.map(_.winnerName)
    }
    (rounds.toSeq, rounds.last.head.winnerName)

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
  // onPairingResult: see tournamentStandings' doc — same purpose (feeding the markdown
  // matchup grid), same signature. Every round's pairings run across a fixed-size thread
  // pool (see `parallelism`): unlike tournamentStandings' round-robin, a name plays at
  // most once per round here, so no pairing within a round can race another pairing's
  // read/write of that name's Elo rating — the pool is created once and reused across all
  // rounds, with only the elo/records fold staying single-threaded and round-ordered
  // (a later round's pairing decisions read the scores/ratings the previous round wrote).
  def swissStandings(
      names: Seq[String],
      matchesPerPairing: Int,
      maxTicks: Int,
      deltaMs: Double,
      onRoundDone: Int => Unit = _ => (),
      resolve: String => AiStrategy = AiStrategy.all,
      logLine: String => Unit = _ => (),
      onPairingResult: (String, String, Seq[MatchOutcome]) => Unit = (_, _, _) => ()
  ): Seq[Standing] =
    val strategies = names.map(n => n -> resolve(n)).toMap
    val acc = newAccumulator(names)
    val hadBye = scala.collection.mutable.Set.empty[String]
    val played = scala.collection.mutable.Set.empty[Set[String]]

    val rounds = swissRounds(names.size)
    withParallelExecutor { ec =>
      given ExecutionContext = ec
      (1 to rounds).foreach { round =>
        val pairs = pairRound(names, acc, hadBye, played)
        pairs.foreach { case (nameA, nameB) => played += Set(nameA, nameB) }
        val futures = pairs.map { case (nameA, nameB) =>
          runPairingMatches(
            strategies(nameA),
            strategies(nameB),
            matchesPerPairing,
            maxTicks,
            deltaMs
          )
            .map(outcomes => (nameA, nameB, outcomes))
        }
        val pairResults = Await.result(Future.sequence(futures), Duration.Inf)
        pairResults.foreach { case (nameA, nameB, outcomes) =>
          outcomes.zipWithIndex.foreach { case (outcome, m) =>
            logLine(tournamentMatchLine(nameA, nameB, m + 1, matchesPerPairing, outcome))
          }
          onPairingResult(nameA, nameB, outcomes)
          creditPairingResult(nameA, nameB, outcomes, acc)
        }
        onRoundDone(round)
      }
    }
    buildStandings(names, acc).sortBy(s => (-(s.wins + 0.5 * s.draws), -s.elo))

  // Ranking/bye/pairing, plus crediting the bye's automatic win into `acc.records` — the
  // one piece of "run a match" bookkeeping this function does perform itself, since a bye
  // has no actual match to play elsewhere. Everything else here only reads `acc` to rank.
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

  // Folds one pairing's outcomes into `acc` — the exact same sequence
  // (Elo-update-per-match, then a single record/research-total update for the whole
  // pairing) tournamentStandings and swissStandings both do once their (possibly
  // parallel-computed) outcomes are in hand.
  private def creditPairingResult(
      nameA: String,
      nameB: String,
      outcomes: Seq[MatchOutcome],
      acc: MatchAccumulator
  ): Unit =
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
    weightGrid(step).zipWithIndex
      .map { case (weights, idx) =>
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
      }
      .sortBy(-_.winRate)

  private def weightGrid(step: Double): Seq[SpendingWeights] =
    val values = Iterator.iterate(0.0)(_ + step).takeWhile(_ <= 1.0 + 1e-9).toSeq
    for
      resource <- values
      counter <- values
      layout <- values
    yield SpendingWeights(resource, counter, layout)

  // Locale.ROOT, not the JVM default: on a machine whose default locale uses a comma
  // decimal separator (e.g. French), the f-interpolator's %f specifiers would silently
  // corrupt every number in these tables.
  private def fmtRoot(pattern: String, args: Any*): String =
    pattern.formatLocal(java.util.Locale.ROOT, args*)

  private def formatTallyTable(tallies: Seq[Tally], matches: Int): String =
    val header =
      fmtRoot("%-14s %6s %6s %10s %11s", "strategy", "wins", "draws", "avgTicks", "avgResearch")
    val rows =
      tallies.map(t =>
        fmtRoot("%-14s %6d %6d %10.1f %11.1f", t.name, t.wins, t.draws, t.avgTicks, t.avgResearch)
      )
    (header +: rows).mkString(s"$matches matches\n", "\n", "")

  private def formatStandingsTable(standings: Seq[Standing]): String =
    val header =
      fmtRoot(
        "%-14s %6s %6s %6s %8s %7s %11s",
        "strategy",
        "wins",
        "draws",
        "losses",
        "winRate",
        "elo",
        "avgResearch"
      )
    val rows = standings.map(s =>
      fmtRoot(
        "%-14s %6d %6d %6d %8.2f %7.0f %11.1f",
        s.name,
        s.wins,
        s.draws,
        s.losses,
        s.winRate,
        s.elo,
        s.avgResearch
      )
    )
    (header +: rows).mkString("\n")

  private def formatWeightTable(results: Seq[WeightResult], top: Int): String =
    val header = fmtRoot("%9s %9s %9s %9s", "resource", "counter", "layout", "winRate")
    val rows = results
      .take(top)
      .map(r =>
        fmtRoot(
          "%9.2f %9.2f %9.2f %9.2f",
          r.weights.resourceWeight,
          r.weights.counterWeight,
          r.weights.layoutWeight,
          r.winRate
        )
      )
    (header +: rows).mkString("\n")

  // The reason string on MatchResult (VictoryConditions.winReason) is a full sentence
  // with the exact target numbers — too long for a table cell. The part before the first
  // ":" already names which victory condition fired (Nature/Chaos/Mort/Science), which is
  // all a matchup-grid cell needs.
  private def shortReason(result: MatchResult): String =
    result.reason.split(":").headOption.getOrElse(result.reason)

  // One cell's text from `self`'s point of view (`self` is whichever side of the pairing
  // the row strategy played as) — win/loss carry the win condition, a draw only needs its
  // tick count. Multiple matches per pairing join with "; ", in play order.
  private def cellText(outcomes: Seq[MatchOutcome], self: String): String =
    outcomes
      .map { outcome =>
        outcome.winner match
          case None       => s"draw (${outcome.ticks}t)"
          case Some(side) =>
            val verb = if side == self then "win" else "loss"
            val reason = outcome.finalBattle.outcome.map(shortReason).getOrElse("")
            s"$verb (${outcome.ticks}t, $reason)"
      }
      .mkString("; ")

  // Markdown report: a standings table (same numbers as formatStandingsTable) followed by
  // a matchup grid — rows and columns both ordered by final standing, one row per
  // strategy, one cell per opponent, each cell reporting the row strategy's own
  // ticks-to-win/draw and win condition against that column, from `grid` (populated by
  // tournamentStandings/swissStandings' onPairingResult, keyed (nameA, nameB) with nameA
  // always the side that played "a"). Cells for a pairing that was never played (Swiss
  // tournaments don't play every pairing — see swissStandings' doc) show "—", same as the
  // diagonal.
  // playoff: the single-elimination bracket run among the top standings finishers (see
  // playoffBracket) — optional since not every markdown report caller runs a playoff
  // (rateTournament et al. still only produce standings + a matchup grid).
  private[sim] def formatMarkdownReport(
      standings: Seq[Standing],
      grid: Map[(String, String), Seq[MatchOutcome]],
      rounds: Int,
      matchesPerPairing: Int,
      playoff: Option[(Seq[Seq[BracketMatch]], String)] = None
  ): String =
    val names = standings.map(_.name)
    val standingsHeader = "| Strategy | Wins | Draws | Losses | Win rate | Elo | Avg research |"
    val standingsSep = "|---|---|---|---|---|---|---|"
    // Locale.ROOT, not the JVM default: a locale with comma decimal separators (e.g.
    // French) would otherwise corrupt the numbers in this markdown table.
    val standingsRows = standings.map(s =>
      "| %s | %d | %d | %d | %.2f | %.0f | %.1f |".formatLocal(
        java.util.Locale.ROOT,
        s.name,
        s.wins,
        s.draws,
        s.losses,
        s.winRate,
        s.elo,
        s.avgResearch
      )
    )
    val gridHeader = "| vs | " + names.mkString(" | ") + " |"
    val gridSep = "|" + Seq.fill(names.size + 1)("---").mkString("|") + "|"
    val gridRows = names.map { row =>
      val cells = names.map { col =>
        if row == col then "—"
        else
          grid
            .get((row, col))
            .map(cellText(_, "a"))
            .orElse(grid.get((col, row)).map(cellText(_, "b")))
            .getOrElse("—")
      }
      "| " + row + " | " + cells.mkString(" | ") + " |"
    }
    // Built via string concatenation, not a triple-quoted stripMargin block: stripMargin
    // strips the leading "|" from every line of the FINAL string, including lines that
    // came from interpolated table rows (which themselves start with "|") — it can't tell
    // those apart from the template's own margin markers.
    Seq(
      "# Tournament report",
      "",
      s"${names.size} strategies, $rounds round(s), $matchesPerPairing match(es)/pairing.",
      "",
      "## Standings",
      "",
      standingsHeader,
      standingsSep
    ).appendedAll(standingsRows)
      .appendedAll(Seq("", "## Matchups", "", gridHeader, gridSep))
      .appendedAll(gridRows)
      .appendedAll(
        playoff
          .map { case (bracketRounds, champion) => formatBracketSection(bracketRounds, champion) }
          .getOrElse(Nil)
      )
      .appended("")
      .mkString("\n")

  // One "- A vs B → **winner** (ticks, condition)" line per match, grouped under a
  // "### <round name>" heading per round — mirrors cellText's "from the winner's own
  // perspective" wording (win/loss + ticks + win condition) so a reader sees not just who
  // advanced but how, same information the matchup grid gives for the Swiss/round-robin
  // phase above it.
  private def formatBracketSection(rounds: Seq[Seq[BracketMatch]], champion: String): Seq[String] =
    val body = rounds.flatMap { matches =>
      val heading = s"### ${matches.head.round}"
      val lines = matches.map { m =>
        s"- ${m.nameA} vs ${m.nameB} → **${m.winnerName}** (${cellText(m.outcomes, "a")})"
      }
      Seq(heading).appendedAll(lines).appended("")
    }
    Seq("", "## Playoffs", "").appendedAll(body).appended(s"**Champion: $champion**")

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
    val (logPath, logEvery, seed, rest) = extractLogFlags(args.toList)
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
          val outcome =
            runLoggedMatch(strategyA, strategyB, maxTicks, deltaMs, logEvery, writer.println, seed)
          val winner = outcome.winner.getOrElse("draw")
          println(s"Logged 1 match ($a vs $b, ${outcome.ticks} ticks, winner=$winner) to $path")
        finally writer.close()
      case None =>
        val reporter = new ProgressReporter(s"$a vs $b", matches)
        println(
          formatTallyTable(
            runMatches(a, b, matches, maxTicks, deltaMs, reporter.tick, baseSeed = seed),
            matches
          )
        )

  // --seed <n>: reseeds both strategies for reproducible matches (see runMatch's own doc)
  // — pairs especially well with --log, to replay and re-inspect the exact same match
  // transcript after a balance change instead of a fresh random one each time.
  private def extractLogFlags(
      args: List[String]
  ): (Option[String], Int, Option[Long], List[String]) =
    args match
      case "--log" :: path :: rest =>
        val (_, logEvery, seed, rest2) = extractLogFlags(rest)
        (Some(path), logEvery, seed, rest2)
      case "--log-every" :: n :: rest =>
        val (logPath, _, seed, rest2) = extractLogFlags(rest)
        (logPath, n.toInt, seed, rest2)
      case "--seed" :: n :: rest =>
        val (logPath, logEvery, _, rest2) = extractLogFlags(rest)
        (logPath, logEvery, Some(n.toLong), rest2)
      case other :: rest =>
        val (logPath, logEvery, seed, rest2) = extractLogFlags(rest)
        (logPath, logEvery, seed, other :: rest2)
      case Nil => (None, 100, None, Nil)

  // Defaults to every strategy on AiStrategy.ladder — "a mini tournament across the AIs"
  // means all of them, not a hand-picked subset. Swiss rounds (swissStandings), not a full
  // round-robin: a 25-entry ladder's C(25,2)=300 pairings took ~2h17m at 2 matches/pairing
  // last measured — swissRounds(25)=5 rounds x 12 pairings cuts that down to a fraction of
  // the matches while still separating a clear ranking, and each round's pairings now run
  // across a thread pool sized to the machine's cores (see swissStandings' doc) rather
  // than one match at a time. Every match played is logged preemptively (one line:
  // pairing, winner, final snapshot) to `logPath` as it happens, not just on request after
  // the fact — diagnosing an odd result (e.g. an unexpectedly high draw rate at the
  // ladder's slowest tier) used to mean manually reproducing it with `sim/run --log`
  // afterwards, which isn't even guaranteed to reproduce the same outcome since
  // ComposedStrategy's tie-breaks are unseeded here.
  // playoffSize (arg 5, default 8): after the Swiss phase ranks the field, the top
  // `playoffSize` finishers play a single-elimination bracket (see playoffBracket) to find
  // the actual head-to-head winner — Swiss standings alone are only an approximate
  // ranking (nobody plays every possible opponent), so a clean #1 by win rate/Elo can
  // still be an artifact of a lucky draw rather than a proven best strategy. Must be a
  // power of two; 8 or 16 are the two sizes actually used in practice.
  @main def tournament(args: String*): Unit =
    val matchesPerPairing = args.lift(0).map(_.toInt).getOrElse(1)
    val maxTicks = args.lift(1).map(_.toInt).getOrElse(3_000)
    val deltaMs = args.lift(2).map(_.toDouble).getOrElse(100.0)
    val logPath = args.lift(3).getOrElse("tournament-matches.log")
    val mdPath = args.lift(4).getOrElse("tournament-report.md")
    val playoffSize = args.lift(5).map(_.toInt).getOrElse(8)
    val names = AiStrategy.ladder.map(_._1)
    val rounds = swissRounds(names.size)
    val reporter = new ProgressReporter("tournament", rounds)
    val writer = new java.io.PrintWriter(logPath)
    val grid = scala.collection.mutable.Map.empty[(String, String), Seq[MatchOutcome]]
    val standings =
      try
        swissStandings(
          names,
          matchesPerPairing,
          maxTicks,
          deltaMs,
          reporter.tick,
          logLine = writer.println,
          onPairingResult = (a, b, outcomes) => grid((a, b)) = outcomes
        )
      finally writer.close()
    val seeds = standings.take(playoffSize).map(_.name)
    val playoffReporter = new ProgressReporter("playoff", seeds.size - 1)
    val playoffWriter =
      new java.io.PrintWriter(new java.io.FileWriter(logPath, /* append = */ true))
    val (bracketRounds, champion) =
      try
        playoffBracket(
          seeds,
          matchesPerPairing,
          maxTicks,
          deltaMs,
          logLine = playoffWriter.println,
          onMatchDone = playoffReporter.tick
        )
      finally playoffWriter.close()
    val mdWriter = new java.io.PrintWriter(mdPath)
    try
      mdWriter.print(
        formatMarkdownReport(
          standings,
          grid.toMap,
          rounds,
          matchesPerPairing,
          Some((bracketRounds, champion))
        )
      )
    finally mdWriter.close()
    println(
      s"Swiss tournament: ${names.size} strategies, $rounds rounds, " +
        s"$matchesPerPairing match(es)/pairing across $parallelism cores " +
        s"(per-match log: $logPath, markdown report: $mdPath):"
    )
    println(formatStandingsTable(standings))
    println(s"\nTop $playoffSize playoff bracket:")
    bracketRounds.foreach { matches =>
      println(s"  ${matches.head.round}:")
      matches.foreach(m => println(s"    ${m.nameA} vs ${m.nameB} -> ${m.winnerName}"))
    }
    println(s"\nChampion: $champion")

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
    yield s"$baseName@${periodSec}s" -> RateLimited(
      baseStrategy,
      buildCooldownMs = periodSec * 1_000.0
    )).toMap
    val names = variants.keys.toSeq
    val reporter = new ProgressReporter("rateTournament", names.combinations(2).size)
    val standings =
      tournamentStandings(names, matchesPerPairing = 1, maxTicks, deltaMs, reporter.tick, variants)
    println(
      s"Rate tournament: ${names.size} strategy/speed combinations " +
        s"(${baseNames.mkString(", ")} x $secondsPerBuild sec/build), 1 match/pairing:"
    )
    println(formatStandingsTable(standings))

  // Reports which VictoryConditions.WinCondition actually decided each match of a
  // nameA-vs-nameB series, alongside which side won — the granularity a plain Tally can't
  // give (a win is a win regardless of which of the 5 conditions fired). Exists for
  // rockPaperScissors' own breakdown below: a leg's winner can be the "right" faction by
  // name while still winning via a confound (e.g. Loi's sudden-death firing before either
  // side's own intended race resolves, or a strategy incidentally racing an unrelated
  // faction's condition just by owning a building that happens to spawn a raiding unit —
  // see SimulatorTest's rock-paper-scissors test for the real correctness bar this feeds).
  private[sim] def matchConditions(
      nameA: String,
      nameB: String,
      matches: Int,
      maxTicks: Int,
      deltaMs: Double,
      baseSeed: Option[Long] = None
  ): Seq[Option[(String, VictoryConditions.WinCondition)]] =
    val strategyA = AiStrategy.all(nameA)
    val strategyB = AiStrategy.all(nameB)
    (1 to matches).map { i =>
      val outcome = runMatch(strategyA, strategyB, maxTicks, deltaMs, seed = baseSeed.map(_ + i))
      outcome.winner.map {
        case "a" =>
          "a" -> VictoryConditions.winningCondition(
            outcome.finalBattle.player,
            outcome.finalBattle.ai,
            outcome.finalBattle
          )
        case "b" =>
          "b" -> VictoryConditions.winningCondition(
            outcome.finalBattle.ai,
            outcome.finalBattle.player,
            outcome.finalBattle
          )
      }
    }

  private def formatConditionBreakdown(
      conditions: Seq[Option[(String, VictoryConditions.WinCondition)]]
  ): String =
    val counts = conditions.flatten.groupBy(identity).view.mapValues(_.size).toSeq.sortBy(-_._2)
    val draws = conditions.count(_.isEmpty)
    val parts = counts.map { case ((side, cond), n) =>
      s"$side/$cond=$n"
    } ++ (if draws > 0 then Seq(s"draw=$draws") else Nil)
    "  by condition: " + parts.mkString(", ")

  // Verifies the claimed 5-faction rock-paper-scissors cycle (Chaos plunder > Science,
  // Science > Nature, Nature > Mort/corruption, Mort > Loi, Loi > Chaos — each leg one
  // maze-<faction> rush strategy vs the next, via runMatches) as a printed win-rate table
  // plus a per-condition breakdown (see matchConditions/formatConditionBreakdown above).
  // A real correctness bar now lives in SimulatorTest's own rock-paper-scissors test (>=60%
  // of matches won by the expected faction via its own condition) — this CLI stays the
  // exploratory/diagnostic tool for tuning toward that bar, not a replacement for it.
  // maxTicks defaults comfortably above Balance.LoiVictoryTickThreshold (3_000) so the
  // Mort->Loi and Loi->Chaos legs get real margin past the threshold for Loi's sudden-death
  // comparison to actually decide a strict inequality, not just barely reach it.
  // seed defaults to 0 (reproducible by default — see runMatch's own doc for why this
  // matters: repeated measurements swung by tens of percentage points on tie-break noise
  // alone before seeding existed), pass "unseeded" as the 4th arg to opt back into
  // genuinely random matches.
  @main def rockPaperScissors(args: String*): Unit =
    val matches = args.lift(0).map(_.toInt).getOrElse(20)
    val maxTicks = args.lift(1).map(_.toInt).getOrElse(3_500)
    val deltaMs = args.lift(2).map(_.toDouble).getOrElse(100.0)
    val seed = args.lift(3) match
      case Some("unseeded") => None
      case Some(s)          => Some(s.toLong)
      case None             => Some(0L)
    val legs = Seq(
      "maze-plunder" -> "maze-science",
      "maze-science" -> "maze-nature",
      "maze-nature" -> "maze-corruption",
      "maze-corruption" -> "maze-law",
      "maze-law" -> "maze-plunder"
    )
    val reporter = new ProgressReporter("rockPaperScissors", legs.size)
    println(
      s"Rock-paper-scissors cycle check: $matches matches/leg, maxTicks=$maxTicks, seed=$seed:"
    )
    legs.zipWithIndex.foreach { case ((a, b), idx) =>
      val conditions = matchConditions(a, b, matches, maxTicks, deltaMs, baseSeed = seed)
      val winsA = conditions.count(_.exists(_._1 == "a"))
      val winsB = conditions.count(_.exists(_._1 == "b"))
      val draws = conditions.count(_.isEmpty)
      println(s"$matches matches: $a wins=$winsA, $b wins=$winsB, draws=$draws")
      println(formatConditionBreakdown(conditions))
      reporter.tick(idx + 1)
    }

  @main def tune(args: String*): Unit =
    val baseline = args.lift(0).getOrElse("linear")
    val matchesPerPoint = args.lift(1).map(_.toInt).getOrElse(50)
    val step = args.lift(2).map(_.toDouble).getOrElse(0.25)
    val maxTicks = args.lift(3).map(_.toInt).getOrElse(3_000)
    val deltaMs = args.lift(4).map(_.toDouble).getOrElse(100.0)
    val reporter = new ProgressReporter(s"tune vs $baseline", weightGrid(step).size)
    val results = searchWeights(baseline, matchesPerPoint, step, maxTicks, deltaMs, reporter.tick)
    println(
      s"FreeformLayout+WeightedSpending weight sweep vs '$baseline' ($matchesPerPoint matches/point, step $step):"
    )
    println(formatWeightTable(results, top = 10))
