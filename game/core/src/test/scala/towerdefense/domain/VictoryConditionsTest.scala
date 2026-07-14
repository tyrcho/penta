package towerdefense.domain

class VictoryConditionsTest extends munit.FunSuite:

  test("no result while nobody has met any condition") {
    assertEquals(VictoryConditions.evaluate(BattleState.initial), None)
  }

  test("player wins once they've built enough forests") {
    val forests =
      List.fill(Balance.NatureVictoryForestTarget)(Forest(1, col = 2, row = 2, elfSpawnInMs = 0.0))
    val battle =
      BattleState(player = MazeState.initial.copy(forests = forests), ai = MazeState.initial)
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.PlayerWins]),
      Some(true)
    )
  }

  test("ai wins once it has plundered enough resources") {
    val battle = BattleState(
      player = MazeState.initial,
      ai = MazeState.initial.copy(resourcesPlundered = Balance.ChaosVictoryPlunderTarget)
    )
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.AiWins]),
      Some(true)
    )
  }

  test(
    "player wins by building enough forests even though the player is 'player 1' by convention (symmetric)"
  ) {
    val forests =
      List.fill(Balance.NatureVictoryForestTarget)(Forest(1, col = 2, row = 2, elfSpawnInMs = 0.0))
    val battle =
      BattleState(player = MazeState.initial, ai = MazeState.initial.copy(forests = forests))
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.AiWins]),
      Some(true)
    )
  }

  test("the player can also win via plunder (symmetric)") {
    val battle = BattleState(
      player = MazeState.initial.copy(resourcesPlundered = Balance.ChaosVictoryPlunderTarget),
      ai = MazeState.initial
    )
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.PlayerWins]),
      Some(true)
    )
  }
