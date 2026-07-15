package towerdefense.sim

import towerdefense.domain.*

// Headless AI-vs-AI battle runner: drives BattleEngine.tick with both sides
// strategy-controlled (player slot = side "a", ai slot = side "b") and no rendering, so
// strategies can be compared/tuned by simulating many matches on the JVM.
object Simulator:

  case class MatchOutcome(winner: Option[String], ticks: Int)
  case class Tally(name: String, wins: Int, draws: Int, avgTicks: Double)
  case class WeightResult(weights: Weights, winRate: Double)

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

  // Runs `matches` independent games of the two named strategies (resolved via
  // AiStrategy.all) and tallies wins/draws/avg-ticks per side.
  def runMatches(
      nameA: String,
      nameB: String,
      matches: Int,
      maxTicks: Int,
      deltaMs: Double
  ): Seq[Tally] =
    val strategyA = AiStrategy.all(nameA)
    val strategyB = AiStrategy.all(nameB)
    val outcomes = Seq.fill(matches)(runMatch(strategyA, strategyB, maxTicks, deltaMs))
    Seq(tallyFor(nameA, "a", outcomes), tallyFor(nameB, "b", outcomes))

  private def tallyFor(name: String, side: String, outcomes: Seq[MatchOutcome]): Tally =
    val wins = outcomes.count(_.winner.contains(side))
    val draws = outcomes.count(_.winner.isEmpty)
    val avgTicks =
      if outcomes.isEmpty then 0.0 else outcomes.map(_.ticks).sum.toDouble / outcomes.size
    Tally(name, wins, draws, avgTicks)

  // A deterministic sweep, not a training loop: every Weights combination on the grid is
  // run head-to-head against `baseline` and ranked by win rate — the "tunable variables
  // validated via simulation" mechanism from the plan.
  def searchWeights(
      baseline: String,
      matchesPerPoint: Int,
      step: Double,
      maxTicks: Int,
      deltaMs: Double
  ): Seq[WeightResult] =
    val baselineStrategy = AiStrategy.all(baseline)
    weightGrid(step).map { weights =>
      val candidate = CompositeStrategy(weights)
      val outcomes =
        Seq.fill(matchesPerPoint)(runMatch(candidate, baselineStrategy, maxTicks, deltaMs))
      val wins = outcomes.count(_.winner.contains("a"))
      WeightResult(weights, wins.toDouble / matchesPerPoint)
    }.sortBy(-_.winRate)

  private def weightGrid(step: Double): Seq[Weights] =
    val values = Iterator.iterate(0.0)(_ + step).takeWhile(_ <= 1.0 + 1e-9).toSeq
    for
      resource <- values
      counter <- values
      maze <- values
    yield Weights(resource, counter, maze)

  private def formatTallyTable(tallies: Seq[Tally], matches: Int): String =
    val header = f"${"strategy"}%-14s ${"wins"}%6s ${"draws"}%6s ${"avgTicks"}%10s"
    val rows = tallies.map(t => f"${t.name}%-14s ${t.wins}%6d ${t.draws}%6d ${t.avgTicks}%10.1f")
    (header +: rows).mkString(s"$matches matches\n", "\n", "")

  private def formatWeightTable(results: Seq[WeightResult], top: Int): String =
    val header = f"${"resource"}%9s ${"counter"}%9s ${"maze"}%9s ${"winRate"}%9s"
    val rows = results.take(top).map(r =>
      f"${r.weights.resource}%9.2f ${r.weights.counter}%9.2f ${r.weights.maze}%9.2f ${r.winRate}%9.2f"
    )
    (header +: rows).mkString("\n")

  // maxTicks default: 3_000 ticks * 100ms = 300 virtual seconds, comfortably past the
  // time either strategy needs to reach a victory condition on the 12x12 grid — a match
  // that hits this cap and draws is a sign the pairing is a stalemate, not that the cap
  // is too low.
  // Parsed by hand (not plain @main defaults) because sbt's `runMain` — unlike the
  // standalone `scala` runner — doesn't fill in a Scala 3 @main's default arguments when
  // trailing ones are omitted; it demands every positional argument or none.
  @main def run(args: String*): Unit =
    val a = args.lift(0).getOrElse("linear")
    val b = args.lift(1).getOrElse("balanced")
    val matches = args.lift(2).map(_.toInt).getOrElse(100)
    val maxTicks = args.lift(3).map(_.toInt).getOrElse(3_000)
    val deltaMs = args.lift(4).map(_.toDouble).getOrElse(100.0)
    println(formatTallyTable(runMatches(a, b, matches, maxTicks, deltaMs), matches))

  @main def tune(args: String*): Unit =
    val baseline = args.lift(0).getOrElse("linear")
    val matchesPerPoint = args.lift(1).map(_.toInt).getOrElse(50)
    val step = args.lift(2).map(_.toDouble).getOrElse(0.25)
    val maxTicks = args.lift(3).map(_.toInt).getOrElse(3_000)
    val deltaMs = args.lift(4).map(_.toDouble).getOrElse(100.0)
    val results = searchWeights(baseline, matchesPerPoint, step, maxTicks, deltaMs)
    println(s"CompositeStrategy weight sweep vs '$baseline' ($matchesPerPoint matches/point, step $step):")
    println(formatWeightTable(results, top = 10))
