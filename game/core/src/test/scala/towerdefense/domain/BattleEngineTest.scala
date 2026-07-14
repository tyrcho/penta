package towerdefense.domain

class BattleEngineTest extends munit.FunSuite:

  private def buildingCount(m: MazeState): Int = m.forests.size + m.caves.size + m.labyrinths.size

  test("a forest's Elf arrives in the opponent's maze, not its own") {
    val forest = Forest(100, col = 5, row = 5, elfSpawnInMs = Balance.ElfSpawnIntervalMs)
    val battle = BattleState(
      player = MazeState.initial.copy(forests = List(forest), wood = 0.0),
      ai = MazeState.initial
        .copy(wood = 0.0, fire = 0.0) // AI can't afford anything: doesn't mask the effect
    )
    val result = BattleEngine.tick(battle, deltaMs = Balance.ElfSpawnIntervalMs)
    assertEquals(result.player.enemies, Nil)
    assertEquals(result.ai.enemies.size, 1)
    assertEquals(result.ai.enemies.head.kind, UnitKind.Elf)
  }

  test("the AI builds something once it can afford one, on either side (symmetric)") {
    val battle = BattleState.initial
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(buildingCount(result.ai), 1)
  }

  test(
    "the AI cannot build a second building before its cooldown elapses, even with excess resources"
  ) {
    val battle =
      BattleState.initial.copy(ai = MazeState.initial.copy(wood = 10_000.0, fire = 10_000.0))
    val afterFirstBuild = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(buildingCount(afterFirstBuild.ai), 1)

    val stillCoolingDown =
      BattleEngine.tick(afterFirstBuild, deltaMs = Balance.AiBuildCooldownMs - 1.0)
    assertEquals(buildingCount(stillCoolingDown.ai), 1)

    val cooldownElapsed = BattleEngine.tick(stillCoolingDown, deltaMs = 1.0)
    assertEquals(buildingCount(cooldownElapsed.ai), 2)
  }

  test("a goblin pillaging the player credits the stolen resources and tally to the AI") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val incomingGoblin =
      Enemy(1, goalPos, Balance.GoblinMaxHp, Balance.GoblinMaxHp, speedPerMs = 0.0, UnitKind.Goblin)
    val battle = BattleState(
      player = MazeState.initial.copy(enemies = List(incomingGoblin), wood = 5.0, fire = 5.0),
      ai = MazeState.initial
        .copy(wood = 0.0, fire = 0.0) // isolates the plunder-credit effect from production
    )
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(result.player.wood, 5.0 - Balance.PlunderPerUnit)
    assertEquals(result.player.fire, 5.0 - Balance.PlunderPerUnit)
    assertEquals(result.ai.wood, Balance.PlunderPerUnit)
    assertEquals(result.ai.fire, Balance.PlunderPerUnit)
    assertEquals(result.ai.resourcesPlundered, 2 * Balance.PlunderPerUnit)
  }

  test("a minotaur pillaging the player credits the stolen resources and tally to the AI") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val incomingMinotaur = Enemy(
      1,
      goalPos,
      Balance.MinotaurMaxHp,
      Balance.MinotaurMaxHp,
      speedPerMs = 0.0,
      UnitKind.Minotaur
    )
    val battle = BattleState(
      player = MazeState.initial.copy(enemies = List(incomingMinotaur), wood = 50.0, fire = 50.0),
      ai = MazeState.initial
        .copy(wood = 0.0, fire = 0.0) // isolates the plunder-credit effect from production
    )
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(result.player.wood, 50.0 - Balance.MinotaurPlunderPerUnit)
    assertEquals(result.player.fire, 50.0 - Balance.MinotaurPlunderPerUnit)
    assertEquals(result.ai.wood, Balance.MinotaurPlunderPerUnit)
    assertEquals(result.ai.fire, Balance.MinotaurPlunderPerUnit)
    assertEquals(result.ai.resourcesPlundered, 2 * Balance.MinotaurPlunderPerUnit)
  }

  test("the battle freezes once the player reaches the Nature victory target") {
    val forests = (0 until Balance.NatureVictoryForestTarget)
      .map(i =>
        Forest(
          i.toLong,
          col = i % GridConfig.cols,
          row = 1 + i / GridConfig.cols,
          elfSpawnInMs = Double.MaxValue
        )
      )
      .toList
    val battle =
      BattleState(player = MazeState.initial.copy(forests = forests), ai = MazeState.initial)
    val ticked = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(ticked.outcome.map(_.isInstanceOf[MatchResult.PlayerWins]), Some(true))

    val frozen = BattleEngine.tick(ticked, deltaMs = 10_000.0)
    assertEquals(frozen, ticked)
  }
