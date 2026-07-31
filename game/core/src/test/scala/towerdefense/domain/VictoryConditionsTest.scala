package towerdefense.domain

import towerdefense.domain.combat.*
import towerdefense.domain.economy.*
import towerdefense.domain.grid.*

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
    val groves =
      List.fill(Balance.NatureVictoryForestTarget)(Building(1, 1, 1, BuildingKind.Grove, 0.0))
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

  // Arbre Anime.md/Stonehenge.md: unlike every other creature, a Tree still belongs to
  // whoever's Stonehenge made it even after it crosses into the opponent's maze to raid
  // (Creature.kind alone can't say whose it is, but every creature in a maze's own
  // creature list is — by the game's own invariant — always "sent by the opponent of
  // that maze", so a Tree sitting in the AI's creatures list can only be the player's).
  test(
    "a Tree raiding the opponent's maze counts toward ITS OWNER's forest tally, not a bare building count"
  ) {
    val raidingTrees = List.fill(Balance.NatureVictoryForestTarget)(
      Creature(
        1,
        GridConfig.cellCenter(2, 2),
        Balance.TreeMaxHp,
        Balance.TreeMaxHp,
        0.0,
        UnitKind.Tree
      )
    )
    // The trees sit in the AI's OWN creature list (they crossed over to raid it) — the
    // player (who built the Stonehenge that sent them) still wins, not the AI.
    val battle =
      BattleState(player = MazeState.initial, ai = MazeState.initial.copy(creatures = raidingTrees))
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.PlayerWins]),
      Some(true)
    )
  }

  test("a Tree raiding YOUR maze counts toward the RAIDER's forest tally, not your own") {
    // Same trees, but sitting in the player's own creature list this time — per the same
    // invariant, they must be the AI's raiders, so the AI (not the player) wins here.
    val raidingTrees = List.fill(Balance.NatureVictoryForestTarget)(
      Creature(
        1,
        GridConfig.cellCenter(2, 2),
        Balance.TreeMaxHp,
        Balance.TreeMaxHp,
        0.0,
        UnitKind.Tree
      )
    )
    val battle =
      BattleState(player = MazeState.initial.copy(creatures = raidingTrees), ai = MazeState.initial)
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.AiWins]),
      Some(true)
    )
  }

  test("real Forest buildings and raiding Trees add up together toward the same forest tally") {
    val raidingTrees = List.fill(Balance.NatureVictoryForestTarget - 1)(
      Creature(
        1,
        GridConfig.cellCenter(2, 2),
        Balance.TreeMaxHp,
        Balance.TreeMaxHp,
        0.0,
        UnitKind.Tree
      )
    )
    val battle = BattleState(
      player = MazeState.initial.copy(buildings = List(forestBuilding(5, 5))),
      ai = MazeState.initial.copy(creatures = raidingTrees)
    )
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.PlayerWins]),
      Some(true)
    )
  }

  test("ai wins once it has corrupted enough enemy buildings (Mort)") {
    val battle = BattleState(
      player = MazeState.initial,
      ai = MazeState.initial.copy(buildingsCorrupted = Balance.MortVictoryCorruptionTarget.toInt)
    )
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.AiWins]),
      Some(true)
    )
  }

  test("the player can also win via corruption (symmetric)") {
    val battle = BattleState(
      player =
        MazeState.initial.copy(buildingsCorrupted = Balance.MortVictoryCorruptionTarget.toInt),
      ai = MazeState.initial
    )
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.PlayerWins]),
      Some(true)
    )
  }

  test(
    "clearing the corruption floor isn't enough once the opponent has caught up: must double them too"
  ) {
    val battle = BattleState(
      player =
        MazeState.initial.copy(buildingsCorrupted = Balance.MortVictoryCorruptionTarget.toInt),
      ai = MazeState.initial.copy(buildingsCorrupted =
        (Balance.MortVictoryCorruptionTarget / 2 + 1).toInt
      )
    )
    assertEquals(VictoryConditions.evaluate(battle), None)
  }

  // ── Recherches Sombres: no effect on either maze's victory targets ────────
  // (its effect is now a corruption-speed boost for the researcher's own corrupting
  // creatures — see CombatEngineTest — not anything that touches VictoryConditions.)

  test("researching Sombres has no effect on the researcher's opponent's forest target") {
    val researcher = MazeState.initial.copy(researchLevels = Map(BuildingKind.LaboSombre -> 5))
    assertEquals(
      VictoryConditions.forestTarget(MazeState.initial, researcher),
      Balance.NatureVictoryForestTarget.toDouble
    )
  }

  test("researching Sombres has no effect on the plunder or corruption targets either") {
    val researcher = MazeState.initial.copy(researchLevels = Map(BuildingKind.LaboSombre -> 5))
    assertEquals(VictoryConditions.plunderTarget(researcher), Balance.ChaosVictoryPlunderTarget)
    assertEquals(
      VictoryConditions.corruptionTarget(researcher),
      Balance.MortVictoryCorruptionTarget
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

    val complete =
      almost.copy(researchLevels = almost.researchLevels.updated(BuildingKind.LaboDuChaos, 5))
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

  // ── Fondamentale progress (exposed for the UI's Science row) ────────────

  test("fondamentaleLevel reads LaboDeRecherche's own research level, 0 if never researched") {
    assertEquals(VictoryConditions.fondamentaleLevel(MazeState.initial), 0)
    val state = MazeState.initial.copy(researchLevels = Map(BuildingKind.LaboDeRecherche -> 3))
    assertEquals(VictoryConditions.fondamentaleLevel(state), 3)
  }

  test("fondamentaleReadyLabCount is 0 before Recherche fondamentale has ever been researched") {
    // Every lab starts at level 0, which is >= the level-0 "required depth" of nothing in
    // particular — without gating on fondamentaleLevel > 0 this would misleadingly read
    // as "all labs ready" the instant a match starts.
    val state = MazeState.initial.copy(
      researchLevels = Map(
        BuildingKind.LaboNaturel -> 5,
        BuildingKind.LaboSombre -> 5,
        BuildingKind.LaboDeLaLoi -> 5,
        BuildingKind.LaboDuChaos -> 5
      )
    )
    assertEquals(VictoryConditions.fondamentaleReadyLabCount(state), 0)
  }

  test(
    "fondamentaleReadyLabCount counts exactly how many of the other 4 labs meet the CURRENT level's required depth"
  ) {
    val state = MazeState.initial.copy(
      researchLevels = Map(
        BuildingKind.LaboDeRecherche -> 1, // requires every other lab at level 5
        BuildingKind.LaboNaturel -> 5,
        BuildingKind.LaboSombre -> 5,
        BuildingKind.LaboDeLaLoi -> 4, // one short
        BuildingKind.LaboDuChaos -> 0
      )
    )
    assertEquals(VictoryConditions.fondamentaleReadyLabCount(state), 2)
  }

  test("fondamentaleReadyLabCount reflects a higher fondamentale level's easier requirement") {
    val state = MazeState.initial.copy(
      researchLevels = Map(
        BuildingKind.LaboDeRecherche -> 5, // requires every other lab at only level 1
        BuildingKind.LaboNaturel -> 1,
        BuildingKind.LaboSombre -> 1,
        BuildingKind.LaboDeLaLoi -> 1,
        BuildingKind.LaboDuChaos -> 0 // one short
      )
    )
    assertEquals(VictoryConditions.fondamentaleReadyLabCount(state), 3)
  }

  // ── Loi: Paix Éternelle (Victoire.md's "W" condition) ───────────────────
  // Unlike the other 4, this isn't a race against a floor/opponent-multiplier target: it's
  // a sudden-death comparison that only starts once Balance.LoiVictoryTickThreshold ticks
  // have passed, at which point whoever has strictly more Loi buildings (Church/
  // Watchtower/Angel/Barracks) wins — no minimum floor, even 1 vs 0 counts.

  private def loiBuilding(id: Long, kind: BuildingKind): Building =
    Building(
      id,
      col = id.toInt % GridConfig.cols,
      row = 1 + (id.toInt / GridConfig.cols),
      kind,
      spawnCountdownMs = 0.0
    )

  test("no Loi win before the tick threshold, even with a lopsided building count") {
    val battle = BattleState(
      player = MazeState.initial.copy(buildings = List(loiBuilding(1, BuildingKind.Church))),
      ai = MazeState.initial,
      elapsedTicks = Balance.LoiVictoryTickThreshold - 1
    )
    assertEquals(VictoryConditions.evaluate(battle), None)
  }

  test(
    "player wins via Loi with even 1 Loi building vs 0, once at the tick threshold (no minimum floor)"
  ) {
    val battle = BattleState(
      player = MazeState.initial.copy(buildings = List(loiBuilding(1, BuildingKind.Church))),
      ai = MazeState.initial,
      elapsedTicks = Balance.LoiVictoryTickThreshold
    )
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.PlayerWins]),
      Some(true)
    )
  }

  test("the AI can also win via Loi (symmetric)") {
    val battle = BattleState(
      player = MazeState.initial,
      ai = MazeState.initial.copy(buildings = List(loiBuilding(1, BuildingKind.Watchtower))),
      elapsedTicks = Balance.LoiVictoryTickThreshold
    )
    assertEquals(
      VictoryConditions.evaluate(battle).map(_.isInstanceOf[MatchResult.AiWins]),
      Some(true)
    )
  }

  test(
    "a tie at the threshold does not resolve, and stays unresolved as more ticks pass while still tied"
  ) {
    val tiedBuildings = List(loiBuilding(1, BuildingKind.Church))
    val atThreshold = BattleState(
      player = MazeState.initial.copy(buildings = tiedBuildings),
      ai = MazeState.initial.copy(buildings = List(loiBuilding(2, BuildingKind.Watchtower))),
      elapsedTicks = Balance.LoiVictoryTickThreshold
    )
    assertEquals(VictoryConditions.evaluate(atThreshold), None)
    val muchLater = atThreshold.copy(elapsedTicks = Balance.LoiVictoryTickThreshold + 500)
    assertEquals(VictoryConditions.evaluate(muchLater), None)
  }

  test("a tie that later breaks resolves once one side pulls strictly ahead") {
    val playerAhead = BattleState(
      player = MazeState.initial.copy(buildings =
        List(loiBuilding(1, BuildingKind.Church), loiBuilding(2, BuildingKind.Angel))
      ),
      ai = MazeState.initial.copy(buildings = List(loiBuilding(3, BuildingKind.Watchtower))),
      elapsedTicks = Balance.LoiVictoryTickThreshold + 500
    )
    assertEquals(
      VictoryConditions.evaluate(playerAhead).map(_.isInstanceOf[MatchResult.PlayerWins]),
      Some(true)
    )
  }

  test("loiBuildingCount counts Church/Watchtower/Angel/Barracks but not PassingGate") {
    val state = MazeState.initial.copy(
      buildings = List(
        loiBuilding(1, BuildingKind.Church),
        loiBuilding(2, BuildingKind.Watchtower),
        loiBuilding(3, BuildingKind.Angel),
        loiBuilding(4, BuildingKind.Barracks),
        loiBuilding(5, BuildingKind.PassingGate)
      )
    )
    assertEquals(VictoryConditions.loiBuildingCount(state), 4)
  }

  test("winReason names Loi's eternal peace when that's the branch that decided the match") {
    val battle = BattleState(
      player = MazeState.initial.copy(buildings = List(loiBuilding(1, BuildingKind.Church))),
      ai = MazeState.initial,
      elapsedTicks = Balance.LoiVictoryTickThreshold
    )
    val reason = VictoryConditions.evaluate(battle).map(_.reason).getOrElse("")
    assert(reason.contains("Loi"), s"expected the win reason to mention Loi, got: $reason")
  }

  // ── winningCondition ─────────────────────────────────────────────────────
  // A structured counterpart to winReason's prose, sharing the exact same branch
  // precedence (forest, then plunder, then corruption, then Loi, then fondamentale) — see
  // its own doc. Exists so callers that need to know WHICH condition decided a match (the
  // rock-paper-scissors regression test in sim, most obviously) don't have to parse an
  // English sentence meant for a match log.

  test("winningCondition reports Nature when the forest target decided the match") {
    val forests = List.fill(Balance.NatureVictoryForestTarget)(forestBuilding(2, 2))
    val battle =
      BattleState(player = MazeState.initial.copy(buildings = forests), ai = MazeState.initial)
    assertEquals(
      VictoryConditions.winningCondition(battle.player, battle.ai, battle),
      VictoryConditions.WinCondition.Nature
    )
  }

  test("winningCondition reports Chaos when the plunder target decided the match") {
    val battle = BattleState(
      player = MazeState.initial.copy(resourcesPlundered = Balance.ChaosVictoryPlunderTarget),
      ai = MazeState.initial
    )
    assertEquals(
      VictoryConditions.winningCondition(battle.player, battle.ai, battle),
      VictoryConditions.WinCondition.Chaos
    )
  }

  test("winningCondition reports Mort when the corruption target decided the match") {
    val battle = BattleState(
      player =
        MazeState.initial.copy(buildingsCorrupted = Balance.MortVictoryCorruptionTarget.toInt),
      ai = MazeState.initial
    )
    assertEquals(
      VictoryConditions.winningCondition(battle.player, battle.ai, battle),
      VictoryConditions.WinCondition.Mort
    )
  }

  test("winningCondition reports Loi when the sudden-death building count decided the match") {
    val battle = BattleState(
      player = MazeState.initial.copy(buildings = List(loiBuilding(1, BuildingKind.Church))),
      ai = MazeState.initial,
      elapsedTicks = Balance.LoiVictoryTickThreshold
    )
    assertEquals(
      VictoryConditions.winningCondition(battle.player, battle.ai, battle),
      VictoryConditions.WinCondition.Loi
    )
  }

  test("winningCondition reports Science when the fondamentale research decided the match") {
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
      VictoryConditions.winningCondition(battle.ai, battle.player, battle),
      VictoryConditions.WinCondition.Science
    )
  }
