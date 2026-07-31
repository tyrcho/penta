package towerdefense.sim

import towerdefense.domain.VictoryConditions

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

// Diagnostic tool for the 5-faction rush-strategy tuning exercise (see
// docs — the multi-agent round loop coordinating chaos/mort/science/loi/nature).
// Runs all 10 pairings among the 5 maze-<faction> rush strategies, not just the 5
// adjacent legs Simulator.rockPaperScissors checks — a faction's off-cycle
// competitiveness (its 2 matchups outside the RPS cycle) needs to stay visible
// alongside its favored/defensive cycle legs, without 10 separate `sim/run` shell-outs.
// Deliberately its own file, not appended to Simulator.scala: every faction agent in
// this exercise is told never to touch Simulator.scala, and this file staying separate
// (written once, before round 1, never edited during the exercise) keeps that promise
// trivially true for this tool too — so a round-over-round score change can only mean
// "the AI changed," never "the measurement changed."
//
// Reuses Simulator.matchConditions (private[sim], so same-package access needs no
// visibility change) rather than Simulator.formatConditionBreakdown, which is plainly
// private (object-private, not package-visible) — reimplemented locally instead of
// widening Simulator.scala's own visibility for this one caller. Same reasoning for
// running the 10 pairings across Simulator.parallelism threads (private[sim], reused
// directly) instead of the fully-private withParallelExecutor: matchConditions itself
// runs one pairing's matches sequentially, so without this the 10-pairing matrix is
// single-threaded and takes ~10x longer than the per-round wall-clock budget allows.
//
// Named apart from the `factionMatrix` @main below (not just by capitalization) —
// an object differing from a top-level @main-generated class only in case collides on
// case-insensitive filesystems (macOS default), which sbt warns about.
object FactionMatrixReport:

  // faction name -> (favored opponent, this faction's own WinCondition) — the RPS
  // cycle Simulator.rockPaperScissors checks, restated here so each pairing's console
  // line can be tagged [RPS LEG] without re-deriving the cycle.
  private val favoredOpponent: Map[String, String] = Map(
    "maze-plunder" -> "maze-science",
    "maze-science" -> "maze-nature",
    "maze-nature" -> "maze-corruption",
    "maze-corruption" -> "maze-law",
    "maze-law" -> "maze-plunder"
  )

  private def formatConditionBreakdown(
      conditions: Seq[Option[(String, VictoryConditions.WinCondition)]]
  ): String =
    val counts = conditions.flatten.groupBy(identity).view.mapValues(_.size).toSeq.sortBy(-_._2)
    val draws = conditions.count(_.isEmpty)
    val parts = counts.map { case ((side, cond), n) =>
      s"$side/$cond=$n"
    } ++ (if draws > 0 then Seq(s"draw=$draws") else Nil)
    "  by condition: " + parts.mkString(", ")

  @main def factionMatrix(args: String*): Unit =
    val matches = args.lift(0).map(_.toInt).getOrElse(12)
    val maxTicks = args.lift(1).map(_.toInt).getOrElse(3_500)
    val deltaMs = args.lift(2).map(_.toDouble).getOrElse(100.0)
    val seed = args.lift(3) match
      case Some("unseeded") => None
      case Some(s)          => Some(s.toLong)
      case None             => Some(0L)

    val names =
      Seq("maze-plunder", "maze-science", "maze-nature", "maze-corruption", "maze-law")
    val pairs = names.combinations(2).toSeq
    println(
      s"Faction matrix: $matches matches/pairing (${pairs.size} pairings), " +
        s"maxTicks=$maxTicks, seed=$seed, ${Simulator.parallelism} cores:"
    )

    val reporter = new ProgressReporter("factionMatrix", pairs.size)
    val completed = new AtomicInteger(0)
    val pool = Executors.newFixedThreadPool(Simulator.parallelism)
    val results =
      try
        given ec: ExecutionContext = ExecutionContext.fromExecutor(pool)
        val work = Future.traverse(pairs) { pair =>
          Future {
            val conditions =
              Simulator.matchConditions(pair(0), pair(1), matches, maxTicks, deltaMs, baseSeed = seed)
            reporter.tick(completed.incrementAndGet())
            (pair(0), pair(1), conditions)
          }
        }
        Await.result(work, Duration.Inf)
      finally pool.shutdown()

    // Printed after every pairing finishes (not streamed as each completes) so
    // concurrent pairings' output never interleaves mid-line — order matches `pairs`,
    // stable regardless of which pairing's Future actually finished first.
    results.foreach { case (a, b, conditions) =>
      val winsA = conditions.count(_.exists(_._1 == "a"))
      val winsB = conditions.count(_.exists(_._1 == "b"))
      val draws = conditions.count(_.isEmpty)
      val tag = if favoredOpponent.get(a).contains(b) then " [RPS LEG]" else ""
      println(s"$a vs $b$tag: $matches matches, $a wins=$winsA, $b wins=$winsB, draws=$draws")
      println(formatConditionBreakdown(conditions))
    }
