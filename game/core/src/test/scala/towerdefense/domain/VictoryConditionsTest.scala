package towerdefense.domain

class VictoryConditionsTest extends munit.FunSuite:

  private def forestBuilding(col: Int, row: Int): Building =
    Building(1, col, row, BuildingKind.Forest, spawnCountdownMs = 0.0)

  test("no result while nobody has met any condition") {
    assertEquals(VictoryConditions.evaluate(BattleState.initial), None)
  }

  test("player wins once they've built enough forests") {
    val forests = List.fill(Balance.NatureVictoryForestTarget)(forestBuilding(2, 2))
    val battle =
      BattleState(player = MazeState.initial.copy(buildings = forests), ai = MazeState.initial)
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
    val forests = List.fill(Balance.NatureVictoryForestTarget)(forestBuilding(2, 2))
    val battle =
      BattleState(player = MazeState.initial, ai = MazeState.initial.copy(buildings = forests))
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

  test("clearing the floor isn't enough once the opponent has caught up: must double them too") {
    val forests = List.fill(Balance.NatureVictoryForestTarget)(forestBuilding(2, 2))
    val opponentForests =
      List.fill(Balance.NatureVictoryForestTarget / 2 + 1)(forestBuilding(3, 3))
    val battle = BattleState(
      player = MazeState.initial.copy(buildings = forests),
      ai = MazeState.initial.copy(buildings = opponentForests)
    )
    assertEquals(VictoryConditions.evaluate(battle), None)
  }

  test("doubling a caught-up opponent's count wins even above the floor") {
    val opponentForests = List.fill(Balance.NatureVictoryForestTarget)(forestBuilding(3, 3))
    val forests = List.fill(Balance.NatureVictoryForestTarget * 2)(forestBuilding(2, 2))
    val battle = BattleState(
      player = MazeState.initial.copy(buildings = forests),
      ai = MazeState.initial.copy(buildings = opponentForests)
    )
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.PlayerWins]),
      Some(true)
    )
  }

  test("Forest and Jungle both count toward the target, since both are real forests") {
    val mixedTiers = List(
      Building(1, 1, 1, BuildingKind.Forest, spawnCountdownMs = 0.0),
      Building(2, 2, 2, BuildingKind.Jungle, spawnCountdownMs = 0.0)
    ) ++ List.fill(Balance.NatureVictoryForestTarget - 2)(forestBuilding(4, 4))
    val battle =
      BattleState(player = MazeState.initial.copy(buildings = mixedTiers), ai = MazeState.initial)
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.PlayerWins]),
      Some(true)
    )
  }

  // Bosquet.md's asset is a bush, not a tree — a Grove hasn't grown into a real forest
  // yet, so it must not count toward "Nature's unstoppable expansion" even though it's
  // still Grove's own faction/upgrade-chain kin.
  test("a Grove does not count toward the forest target — it's a bush, not a forest yet") {
    val groves = List.fill(Balance.NatureVictoryForestTarget)(Building(1, 1, 1, BuildingKind.Grove, 0.0))
    val battle =
      BattleState(player = MazeState.initial.copy(buildings = groves), ai = MazeState.initial)
    assertEquals(VictoryConditions.evaluate(battle), None)
  }

  test("a pile of Groves doesn't make up for one real forest short of the target") {
    val mixedTiers =
      List.fill(Balance.NatureVictoryForestTarget)(Building(1, 1, 1, BuildingKind.Grove, 0.0)) ++
        List.fill(Balance.NatureVictoryForestTarget - 1)(forestBuilding(4, 4))
    val battle =
      BattleState(player = MazeState.initial.copy(buildings = mixedTiers), ai = MazeState.initial)
    assertEquals(VictoryConditions.evaluate(battle), None)
  }

  test("ai wins once it has corrupted enough enemy buildings (Mort)") {
    val battle = BattleState(
      player = MazeState.initial,
      ai = MazeState.initial.copy(buildingsCorrupted = Balance.MortVictoryCorruptionTarget)
    )
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.AiWins]),
      Some(true)
    )
  }

  test("the player can also win via corruption (symmetric)") {
    val battle = BattleState(
      player = MazeState.initial.copy(buildingsCorrupted = Balance.MortVictoryCorruptionTarget),
      ai = MazeState.initial
    )
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.PlayerWins]),
      Some(true)
    )
  }

  test("clearing the corruption floor isn't enough once the opponent has caught up: must double them too") {
    val battle = BattleState(
      player = MazeState.initial.copy(buildingsCorrupted = Balance.MortVictoryCorruptionTarget),
      ai = MazeState.initial.copy(buildingsCorrupted = Balance.MortVictoryCorruptionTarget / 2 + 1)
    )
    assertEquals(VictoryConditions.evaluate(battle), None)
  }

  // ── Recherches Sombres: inflates the OPPONENT's own victory targets ───────

  test("researching Sombres raises the researcher's opponent's forest target") {
    val sombresLevel = 3
    val researcher = MazeState.initial.copy(researchLevels = Map(BuildingKind.LaboSombre -> sombresLevel))
    val bonus = Balance.SombresOpponentTargetIncreaseByLevel(sombresLevel - 1)
    // forestTarget(opponent) reads `opponent`'s own Sombres level, since `opponent` here is
    // the side making it harder for `state` (whoever calls forestTarget) to win.
    assertEquals(
      VictoryConditions.forestTarget(researcher),
      Balance.NatureVictoryForestTarget * (1.0 + bonus)
    )
  }

  test("Sombres also inflates the plunder and corruption targets, not just forests") {
    val sombresLevel = 1
    val researcher = MazeState.initial.copy(researchLevels = Map(BuildingKind.LaboSombre -> sombresLevel))
    val bonus = Balance.SombresOpponentTargetIncreaseByLevel(sombresLevel - 1)
    assertEquals(VictoryConditions.plunderTarget(researcher), Balance.ChaosVictoryPlunderTarget * (1.0 + bonus))
    assertEquals(
      VictoryConditions.corruptionTarget(researcher),
      Balance.MortVictoryCorruptionTarget * (1.0 + bonus)
    )
  }

  test("a maze that researched Sombres against itself still needs to beat its own inflated target") {
    // Researching Sombres makes life harder for whoever's *opponent* you are — from the
    // researcher's own point of view as `state`, its own target is unaffected (only the
    // *other* side reads this maze's Sombres level as their opponent).
    val researcher = MazeState.initial.copy(
      buildingsCorrupted = Balance.MortVictoryCorruptionTarget,
      researchLevels = Map(BuildingKind.LaboSombre -> 5)
    )
    val battle = BattleState(player = researcher, ai = MazeState.initial)
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.PlayerWins]),
      Some(true)
    )
  }

  // ── Recherche fondamentale ─────────────────────────────────────────────

  test("fondamentale level 1 requires every other lab at level 5") {
    val almost = MazeState.initial.copy(
      researchLevels = Map(
        BuildingKind.LaboDeRecherche -> 1,
        BuildingKind.LaboNaturel -> 5,
        BuildingKind.LaboSombre -> 5,
        BuildingKind.LaboDeLaLoi -> 5,
        BuildingKind.LaboDuChaos -> 4 // one short
      )
    )
    assertEquals(VictoryConditions.hasWonViaFondamentale(almost), false)

    val complete = almost.copy(researchLevels = almost.researchLevels.updated(BuildingKind.LaboDuChaos, 5))
    assertEquals(VictoryConditions.hasWonViaFondamentale(complete), true)
  }

  test("fondamentale level 5 only requires the other labs at level 1") {
    val state = MazeState.initial.copy(
      researchLevels = Map(
        BuildingKind.LaboDeRecherche -> 5,
        BuildingKind.LaboNaturel -> 1,
        BuildingKind.LaboSombre -> 1,
        BuildingKind.LaboDeLaLoi -> 1,
        BuildingKind.LaboDuChaos -> 1
      )
    )
    assertEquals(VictoryConditions.hasWonViaFondamentale(state), true)
  }

  test("fondamentale never researched (level 0) never wins via this condition") {
    val state = MazeState.initial.copy(
      researchLevels = Map(
        BuildingKind.LaboNaturel -> 5,
        BuildingKind.LaboSombre -> 5,
        BuildingKind.LaboDeLaLoi -> 5,
        BuildingKind.LaboDuChaos -> 5
      )
    )
    assertEquals(VictoryConditions.hasWonViaFondamentale(state), false)
  }

  test("evaluate reports a fondamentale win as a real match outcome") {
    val winner = MazeState.initial.copy(
      researchLevels = Map(
        BuildingKind.LaboDeRecherche -> 5,
        BuildingKind.LaboNaturel -> 1,
        BuildingKind.LaboSombre -> 1,
        BuildingKind.LaboDeLaLoi -> 1,
        BuildingKind.LaboDuChaos -> 1
      )
    )
    val battle = BattleState(player = MazeState.initial, ai = winner)
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.AiWins]),
      Some(true)
    )
  }
