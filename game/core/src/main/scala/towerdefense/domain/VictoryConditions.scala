package towerdefense.domain

enum MatchResult(val reason: String) derives CanEqual:
  case PlayerWins(override val reason: String) extends MatchResult(reason)
  case AiWins(override val reason: String) extends MatchResult(reason)

// Symmetric: either maze can win via either faction's condition from Victoire.md,
// since either side may have built Forests and/or Caves (see CLAUDE.md). Whichever
// maze meets a condition first wins — checked player-first, so a simultaneous
// double-win (same tick) favors the player, which only matters for tie-breaking.
// No "lives"/overrun fallback: that was never a vault concept, only the two
// per-faction conditions below count for now (a match can run indefinitely otherwise).
//
// Each target is relative to the opponent (see Balance.VictoryMultiplierOverOpponent):
// you must reach the floor AND double whatever the opponent has, so a lead built up
// early doesn't win the game outright once the opponent has caught back up.
object VictoryConditions:

  def evaluate(battle: BattleState): Option[MatchResult] =
    if hasWon(battle.player, battle.ai) then
      Some(MatchResult.PlayerWins(winReason(battle.player, battle.ai)))
    else if hasWon(battle.ai, battle.player) then
      Some(MatchResult.AiWins(winReason(battle.ai, battle.player)))
    else None

  private def hasWon(state: MazeState, opponent: MazeState): Boolean =
    forestCount(state) >= forestTarget(opponent) ||
      state.resourcesPlundered >= plunderTarget(opponent)

  private def forestCount(state: MazeState): Int = state.buildings.count(_.kind == BuildingKind.Forest)

  // Exposed (not just private) so the UI can display the live target, which moves
  // with the opponent's own count — see the module doc above.
  def forestTarget(opponent: MazeState): Double =
    math.max(Balance.NatureVictoryForestTarget.toDouble, opponentTarget(forestCount(opponent)))

  def plunderTarget(opponent: MazeState): Double =
    math.max(Balance.ChaosVictoryPlunderTarget, opponentTarget(opponent.resourcesPlundered))

  private def opponentTarget(opponentCount: Double): Double =
    Balance.VictoryMultiplierOverOpponent * opponentCount

  private def winReason(state: MazeState, opponent: MazeState): String =
    if forestCount(state) >= forestTarget(opponent) then
      s"Nature's unstoppable expansion: ${forestCount(state)} Forests built (target ${forestTarget(opponent).toInt})."
    else
      s"Chaos plunder: ${state.resourcesPlundered.toInt} resources stolen (target ${plunderTarget(opponent).toInt})."
