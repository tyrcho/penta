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
object VictoryConditions:

  def evaluate(battle: BattleState): Option[MatchResult] =
    if hasWon(battle.player) then Some(MatchResult.PlayerWins(winReason(battle.player)))
    else if hasWon(battle.ai) then Some(MatchResult.AiWins(winReason(battle.ai)))
    else None

  private def hasWon(state: MazeState): Boolean =
    state.forests.size >= Balance.NatureVictoryForestTarget || state.resourcesPlundered >= Balance.ChaosVictoryPlunderTarget

  private def winReason(state: MazeState): String =
    if state.forests.size >= Balance.NatureVictoryForestTarget then
      s"Nature's unstoppable expansion: ${Balance.NatureVictoryForestTarget} Forests built."
    else
      s"Chaos plunder: ${Balance.ChaosVictoryPlunderTarget.toInt} resources stolen from the opponent."
