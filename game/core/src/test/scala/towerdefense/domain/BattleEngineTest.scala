package towerdefense.domain

class BattleEngineTest extends munit.FunSuite:

  private def buildingCount(m: MazeState): Int = m.buildings.size

  private def withResources(wood: Double = 0.0, fire: Double = 0.0, light: Double = 0.0): MazeState =
    MazeState.initial.copy(
      resources = Map(Resource.Wood -> wood, Resource.Fire -> fire, Resource.Light -> light)
    )

  test("a forest's Elf arrives in the opponent's maze, not its own") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val battle = BattleState(
      player = withResources().copy(buildings = List(forest)),
      ai = withResources() // AI can't afford anything: doesn't mask the effect
    )
    val result = BattleEngine.tick(battle, deltaMs = Balance.ElfSpawnIntervalMs)
    assertEquals(result.player.creatures, Nil)
    assertEquals(result.ai.creatures.size, 1)
    assertEquals(result.ai.creatures.head.kind, UnitKind.Elf)
  }

  test("a death house's Necromancer arrives in the opponent's maze with a full Soul-summon countdown") {
    val deathHouse = Building(100, col = 5, row = 5, BuildingKind.DeathHouse, Balance.NecromancerSpawnIntervalMs)
    val battle = BattleState(
      player = withResources().copy(buildings = List(deathHouse)),
      ai = withResources()
    )
    val result = BattleEngine.tick(battle, deltaMs = Balance.NecromancerSpawnIntervalMs)
    assertEquals(result.player.creatures, Nil)
    assertEquals(result.ai.creatures.size, 1)
    val necromancer = result.ai.creatures.head
    assertEquals(necromancer.kind, UnitKind.Necromancer)
    // Starts with the full interval (like a freshly placed building's own spawnCountdownMs
    // — see Placement.tryPlaceBuilding), not 0 — its first Soul shouldn't appear instantly.
    assertEquals(necromancer.spawnCountdownMs, Balance.SoulSummonIntervalMs)
  }

  // Stonehenge.md/Arbre Anime.md: unlike every other spawner (Elf/Necromancer above), a
  // Tree stays in its OWNER's maze instead of crossing into the opponent's — Stonehenge's
  // whole point is growing this maze's own forest tally through units, not raiders.
  test("a stonehenge's Tree arrives in its OWN maze, not the opponent's") {
    val stonehenge = Building(100, col = 5, row = 5, BuildingKind.Stonehenge, Balance.StonehengeSpawnIntervalMs)
    val battle = BattleState(
      player = withResources().copy(buildings = List(stonehenge)),
      ai = withResources()
    )
    val result = BattleEngine.tick(battle, deltaMs = Balance.StonehengeSpawnIntervalMs)
    assertEquals(result.ai.creatures, Nil)
    assertEquals(result.player.creatures.size, 1)
    val tree = result.player.creatures.head
    assertEquals(tree.kind, UnitKind.Tree)
    // Starts with the full clone interval, like the Necromancer's first Soul countdown
    // above — its first clone shouldn't appear instantly.
    assertEquals(tree.spawnCountdownMs, Balance.TreeCloneIntervalMs)
  }

  test("the AI builds something once it can afford one, on either side (symmetric)") {
    val battle = BattleState.initial
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(buildingCount(result.ai), 1)
  }

  test("a strategy's maybeDestroy is applied to its own side, before it builds again") {
    val demolisher: AiStrategy = new AiStrategy:
      def maybeBuild(state: MazeState, opponent: MazeState): MazeState = state
      override def maybeDestroy(state: MazeState, opponent: MazeState): MazeState =
        Demolition.tryDestroy(state, 5, 5).getOrElse(state)
    val forest = Building(1, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val battle = BattleState.initial.copy(ai = MazeState.initial.copy(buildings = List(forest)))
    val result = BattleEngine.tick(battle, deltaMs = 1.0, aiStrategy = demolisher)
    assertEquals(result.ai.buildings.count(_.kind == BuildingKind.Forest), 0)
  }

  test("the default AiStrategy.maybeDestroy is a no-op, so existing strategies never tear anything down") {
    val forest = Building(1, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    // No Wood at all: isolates the destroy no-op check from LinearStrategy's own
    // maybeUpgrade step (which runs unconditionally before maybeBuild/maybeDestroy would
    // matter here) — with StartingWood, this Forest would be affordably upgraded into a
    // Jungle the same tick, masking whether maybeDestroy left it alone.
    val battle = BattleState.initial.copy(
      ai = MazeState.initial.copy(buildings = List(forest), resources = Map(Resource.Wood -> 0.0))
    )
    val result = BattleEngine.tick(battle, deltaMs = 1.0, aiStrategy = LinearStrategy)
    assertEquals(result.ai.buildings.count(_.kind == BuildingKind.Forest), 1)
  }

  test("both sides build symmetrically when both are given a strategy") {
    val battle = BattleState.initial
    val result = BattleEngine.tick(battle, deltaMs = 1.0, playerStrategy = Some(LinearStrategy))
    assertEquals(buildingCount(result.ai), 1)
    assertEquals(buildingCount(result.player), 1)
  }

  test("the player does not auto-build when no strategy is given (human-controlled default)") {
    val battle = BattleState.initial
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(buildingCount(result.player), 0)
  }

  test("the player cannot build a second building before its cooldown elapses either") {
    val battle = BattleState.initial.copy(player = withResources(wood = 10_000.0, fire = 10_000.0))
    val afterFirstBuild =
      BattleEngine.tick(battle, deltaMs = 1.0, playerStrategy = Some(LinearStrategy))
    assertEquals(buildingCount(afterFirstBuild.player), 1)

    val stillCoolingDown = BattleEngine.tick(
      afterFirstBuild,
      deltaMs = Balance.AiBuildCooldownMs - 1.0,
      playerStrategy = Some(LinearStrategy)
    )
    assertEquals(buildingCount(stillCoolingDown.player), 1)

    val cooldownElapsed =
      BattleEngine.tick(stillCoolingDown, deltaMs = 1.0, playerStrategy = Some(LinearStrategy))
    assertEquals(buildingCount(cooldownElapsed.player), 2)
  }

  test(
    "a build attempt that fails for lack of resources still paces the next attempt, " +
      "instead of rescanning the whole grid every tick"
  ) {
    val battle = BattleState.initial.copy(ai = withResources(wood = 0.0, fire = 0.0, light = 0.0))
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(buildingCount(result.ai), 0)
    assertEquals(result.aiBuildCooldownMs, Balance.AiBuildCooldownMs)
  }

  test(
    "the AI cannot build a second building before its cooldown elapses, even with excess resources"
  ) {
    val battle = BattleState.initial.copy(ai = withResources(wood = 10_000.0, fire = 10_000.0))
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
      Creature(1, goalPos, Balance.GoblinMaxHp, Balance.GoblinMaxHp, speedPerMs = 0.0, UnitKind.Goblin)
    val battle = BattleState(
      player = withResources(wood = 5.0, fire = 5.0).copy(creatures = List(incomingGoblin)),
      ai = withResources() // isolates the plunder-credit effect from production
    )
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(result.player.resources(Resource.Wood), 5.0 - Balance.PlunderPerUnit)
    assertEquals(result.player.resources(Resource.Fire), 5.0 - Balance.PlunderPerUnit)
    assertEquals(result.ai.resources(Resource.Wood), Balance.PlunderPerUnit)
    assertEquals(result.ai.resources(Resource.Fire), Balance.PlunderPerUnit)
    assertEquals(result.ai.resourcesPlundered, 2 * Balance.PlunderPerUnit)
  }

  test("a minotaur pillaging the player credits the stolen resources and tally to the AI") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val incomingMinotaur = Creature(
      1,
      goalPos,
      Balance.MinotaurMaxHp,
      Balance.MinotaurMaxHp,
      speedPerMs = 0.0,
      UnitKind.Minotaur
    )
    val battle = BattleState(
      player = withResources(wood = 50.0, fire = 50.0).copy(creatures = List(incomingMinotaur)),
      ai = withResources() // isolates the plunder-credit effect from production
    )
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(result.player.resources(Resource.Wood), 50.0 - Balance.MinotaurPlunderPerUnit)
    assertEquals(result.player.resources(Resource.Fire), 50.0 - Balance.MinotaurPlunderPerUnit)
    assertEquals(result.ai.resources(Resource.Wood), Balance.MinotaurPlunderPerUnit)
    assertEquals(result.ai.resources(Resource.Fire), Balance.MinotaurPlunderPerUnit)
    assertEquals(result.ai.resourcesPlundered, 2 * Balance.MinotaurPlunderPerUnit)
  }

  test("a zombie corrupting a building to destruction credits the full cost and tally to the AI") {
    val almostCorrupted = Building(
      1,
      col = 5,
      row = 5,
      BuildingKind.Grove,
      spawnCountdownMs = 0.0,
      corruptionPercent = Balance.CorruptionMaxPercent - Balance.ZombieCorruptionPercentPerSec
    )
    val incomingZombie =
      Creature(1, GridConfig.cellCenter(6, 5), Balance.ZombieMaxHp, Balance.ZombieMaxHp, 0.0, UnitKind.Zombie)
    val battle = BattleState(
      player = withResources().copy(buildings = List(almostCorrupted), creatures = List(incomingZombie)),
      ai = withResources() // isolates the corruption-credit effect from production
    )
    val result = BattleEngine.tick(battle, deltaMs = 1000.0)
    assertEquals(result.player.buildings, Nil)
    assertEquals(result.player.buildingsCorrupted, 0.0) // this side lost the building, didn't corrupt one
    assertEquals(result.ai.resources(Resource.Wood), Balance.GroveCostWood)
    assertEquals(result.ai.buildingsCorrupted, 1.0)
  }

  test("the battle freezes once the player reaches the Nature victory target") {
    val forests = (0 until Balance.NatureVictoryForestTarget)
      .map(i =>
        Building(
          i.toLong,
          col = i % GridConfig.cols,
          row = 1 + i / GridConfig.cols,
          BuildingKind.Forest,
          spawnCountdownMs = Double.MaxValue
        )
      )
      .toList
    val battle =
      BattleState(player = MazeState.initial.copy(buildings = forests), ai = MazeState.initial)
    val ticked = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(ticked.outcome.map(_.isInstanceOf[MatchResult.PlayerWins]), Some(true))

    val frozen = BattleEngine.tick(ticked, deltaMs = 10_000.0)
    assertEquals(frozen, ticked)
  }
