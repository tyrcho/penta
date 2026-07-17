package towerdefense.sim

import towerdefense.domain.*

// Pure formatting/diffing logic for a plain-text, one-line-per-event match transcript —
// no I/O here, `Simulator.run`'s `--log` flag owns the file writing. Plain text, not
// JSON: the project has no JSON library anywhere (core/sim are dependency-free besides
// munit), and a line-based transcript is just as readable to a human or an LLM agent
// reviewing it for strategic mistakes, arguably more so since it needs no parsing step.
//
// Builds/upgrades/destroys are found by diffing each maze's `buildings` list by id
// between two consecutive BattleState snapshots: Placement.tryUpgradeBuilding keeps the
// same id across a kind change (see its doc), so a same-id kind change is unambiguously
// an upgrade, not a destroy+rebuild. Plunder is found by diffing the already-cumulative
// `resourcesPlundered` field. Deaths/arrivals come from TickEvents (BattleEngine's
// tickDetailed) directly, since — unlike builds/plunder — they can't be reconstructed by
// diffing alone: a creature that dies and one that reaches the goal without plunder
// (Paladin, Wolf) both just vanish from `creatures` either way.
object MatchLog:

  def diff(tick: Int, before: BattleState, after: BattleState, events: TickEvents): Seq[String] =
    sideLines(tick, "a", before.player, after.player, events.playerDeaths, events.playerArrivals, events.playerCorrupted) ++
      sideLines(tick, "b", before.ai, after.ai, events.aiDeaths, events.aiArrivals, events.aiCorrupted)

  private def sideLines(
      tick: Int,
      side: String,
      before: MazeState,
      after: MazeState,
      deaths: List[Death],
      arrivals: List[UnitKind],
      corrupted: List[Corrosion]
  ): Seq[String] =
    buildingLines(tick, side, before, after, corrupted) ++
      plunderLine(tick, side, before, after) ++
      corruptedTallyLine(tick, side, before, after) ++
      deaths.map(deathLine(tick, side, _)) ++
      corrupted.map(corruptLine(tick, side, _)) ++
      arrivals.filter(k => CreatureSpecs.all(k).plunder.isEmpty).map(arriveLine(tick, side, _))

  // `corrupted` (from TickEvents, not diffed) tells builtIds/destroyedIds apart from a
  // plain demolish: a corrupted-to-death building would otherwise look identical to a
  // Demolition in the before/after id diff alone, but refunds its full cost to the
  // *opponent* rather than half back to this side — see corruptLine, not the generic
  // DESTROY line, for those ids.
  private def buildingLines(
      tick: Int,
      side: String,
      before: MazeState,
      after: MazeState,
      corrupted: List[Corrosion]
  ): Seq[String] =
    val beforeById = before.buildings.map(b => b.id -> b).toMap
    val afterById = after.buildings.map(b => b.id -> b).toMap
    val corruptedIds = corrupted.map(_.buildingId).toSet
    val builtIds = (afterById.keySet -- beforeById.keySet).toSeq.sorted
    val destroyedIds = (beforeById.keySet -- afterById.keySet -- corruptedIds).toSeq.sorted
    val upgradedIds = afterById.keySet
      .intersect(beforeById.keySet)
      .filter(id => afterById(id).kind != beforeById(id).kind)
      .toSeq
      .sorted

    val built = builtIds.map { id =>
      val b = afterById(id)
      val cost = fmtResources(BuildingSpecs.all(b.kind).cost)
      formatLine(tick, side, "BUILD", s"${b.kind} (${b.col},${b.row}) cost $cost")
    }
    val upgraded = upgradedIds.map { id =>
      val b = afterById(id)
      val fromKind = beforeById(id).kind
      formatLine(
        tick,
        side,
        "UPGRADE",
        s"$fromKind→${b.kind} (${b.col},${b.row}) cost ${fmtResources(BuildingSpecs.all(b.kind).cost)}"
      )
    }
    val destroyed = destroyedIds.map { id =>
      val b = beforeById(id)
      val refund = BuildingSpecs.all(b.kind).cost.view.mapValues(_ * Balance.DemolishRefundFraction).toMap
      formatLine(tick, side, "DESTROY", s"${b.kind} (${b.col},${b.row}) refund ${fmtResources(refund)}")
    }
    built ++ upgraded ++ destroyed

  // Corrosion.cost is already the full building cost, refunded to the *opponent* (the
  // corrupting creature's owner) — unlike DESTROY's half-cost self-refund above.
  private def corruptLine(tick: Int, side: String, corrosion: Corrosion): String =
    formatLine(
      tick,
      side,
      "CORRUPT",
      s"${corrosion.kind} (${corrosion.col},${corrosion.row}) corrupted to dust, " +
        s"opponent refunded ${fmtResources(corrosion.cost)}"
    )

  private def corruptedTallyLine(tick: Int, side: String, before: MazeState, after: MazeState): Option[String] =
    val delta = after.buildingsCorrupted - before.buildingsCorrupted
    if delta <= 0.0 then None
    else Some(formatLine(tick, side, "CORRUPTED_TOTAL", f"+${delta.toInt} (total ${after.buildingsCorrupted.toInt})"))

  private def plunderLine(tick: Int, side: String, before: MazeState, after: MazeState): Option[String] =
    val delta = after.resourcesPlundered - before.resourcesPlundered
    if delta <= 0.0 then None
    else Some(formatLine(tick, side, "PLUNDER", f"+$delta%.1f (total ${after.resourcesPlundered}%.1f)"))

  private def deathLine(tick: Int, side: String, death: Death): String =
    val cause = death.cause match
      case DeathCause.Aura             => "Aura"
      case DeathCause.Watchtower       => "Watchtower"
      case DeathCause.AuraAndWatchtower => "Aura+Watchtower"
    formatLine(tick, side, "DEATH", s"${death.kind} killed by $cause")

  private def arriveLine(tick: Int, side: String, kind: UnitKind): String =
    formatLine(tick, side, "ARRIVE", s"$kind reached goal, no plunder")

  // Not per-side like the other lines (both sides' progress at once, easier to compare
  // at a glance) — forest/plunder targets come from VictoryConditions.forestTarget/
  // plunderTarget (already public for exactly this "external reader wants the live
  // number" reason, see its doc), the same values VictoryConditions.evaluate itself
  // compares against, so this can never silently drift from what actually decides a win.
  def snapshotLine(tick: Int, battle: BattleState): String =
    val a = sideSummary(battle.player, battle.ai)
    val b = sideSummary(battle.ai, battle.player)
    s"tick $tick  SNAPSHOT  a: $a | b: $b"

  private def sideSummary(state: MazeState, opponent: MazeState): String =
    val forests = VictoryConditions.forestCount(state)
    val forestTarget = VictoryConditions.forestTarget(opponent).toInt
    val plunderTarget = VictoryConditions.plunderTarget(opponent).toInt
    val plundered = state.resourcesPlundered
    val corrupted = state.buildingsCorrupted.toInt
    val corruptionTarget = VictoryConditions.corruptionTarget(opponent).toInt
    val resources = fmtResources(state.resources)
    f"forests $forests/$forestTarget  plunder $plundered%.1f/$plunderTarget  corrupted $corrupted/$corruptionTarget  $resources"

  def finalLine(tick: Int, outcome: MatchResult): String =
    val winnerSide = outcome match
      case MatchResult.PlayerWins(_) => "a"
      case MatchResult.AiWins(_)     => "b"
    formatLine(tick, winnerSide, "WINS", outcome.reason)

  private def formatLine(tick: Int, side: String, kind: String, detail: String): String =
    s"tick $tick  $side  $kind  $detail"

  // Sorted by Resource.ordinal for a deterministic, stable column order across lines —
  // private[sim] (not fully private) so MatchLogTest can build its own expected strings
  // from the same formatting instead of duplicating it by hand.
  private[sim] def fmtResources(resources: Map[Resource, Double]): String =
    resources.toSeq.sortBy(_._1.ordinal).map { case (res, amt) => f"$res $amt%.1f" }.mkString(" ")
