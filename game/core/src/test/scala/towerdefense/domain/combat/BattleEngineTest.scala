package towerdefense.domain.combat

import towerdefense.domain.*
import towerdefense.domain.ai.*
import towerdefense.domain.economy.*
import towerdefense.domain.grid.*

class BattleEngineTest extends munit.FunSuite:

  private def buildingCount(m: MazeState): Int = m.buildings.size

  private def withResources(
      wood: Double = 0.0,
      fire: Double = 0.0,
      light: Double = 0.0
  ): MazeState =
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

  test(
    "a death house's Necromancer arrives in the opponent's maze with a full Soul-summon countdown"
  ) {
    val deathHouse =
      Building(100, col = 5, row = 5, BuildingKind.DeathHouse, Balance.NecromancerSpawnIntervalMs)
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
  test("a stonehenge's Tree arrives in the opponent's maze, same as every other spawner") {
    val stonehenge =
      Building(100, col = 5, row = 5, BuildingKind.Stonehenge, Balance.StonehengeSpawnIntervalMs)
    val battle = BattleState(
      player = withResources().copy(buildings = List(stonehenge)),
      ai = withResources()
    )
    val result = BattleEngine.tick(battle, deltaMs = Balance.StonehengeSpawnIntervalMs)
    assertEquals(result.player.creatures, Nil)
    assertEquals(result.ai.creatures.size, 1)
    val tree = result.ai.creatures.head
    assertEquals(tree.kind, UnitKind.Tree)
    // Starts with the full clone interval, like the Necromancer's first Soul countdown
    // above — its first clone shouldn't appear instantly.
    assertEquals(tree.spawnCountdownMs, Balance.TreeCloneIntervalMs)
  }

  test("an Elf spawns with bonus HP from every other living Elf already in the maze it's raiding") {
    val grove = Building(100, col = 5, row = 5, BuildingKind.Grove, Balance.ElfSpawnIntervalMs)
    val existingElves = (1 to 3)
      .map(i =>
        Creature(
          i.toLong,
          GridConfig.cellCenter(0, 0),
          Balance.ElfMaxHp,
          Balance.ElfMaxHp,
          Balance.ElfSpeedPerMs,
          UnitKind.Elf
        )
      )
      .toList
    val battle = BattleState(
      player = withResources().copy(buildings = List(grove)),
      ai = withResources().copy(creatures = existingElves, nextId = 100L)
    )
    val result = BattleEngine.tick(battle, deltaMs = Balance.ElfSpawnIntervalMs)
    val existingIds = existingElves.map(_.id).toSet
    val newElf = result.ai.creatures.find(c => !existingIds.contains(c.id)).get
    assertEquals(newElf.kind, UnitKind.Elf)
    assertEqualsDouble(newElf.maxHp, Balance.ElfMaxHp * (1.0 + 3 * Balance.ElfHpBonusPerAlly), 1e-9)
    assertEqualsDouble(newElf.hp, newElf.maxHp, 1e-9)
  }

  test("a lone Elf with no other allies in the maze it's raiding spawns at plain ElfMaxHp") {
    val grove = Building(100, col = 5, row = 5, BuildingKind.Grove, Balance.ElfSpawnIntervalMs)
    val battle = BattleState(
      player = withResources().copy(buildings = List(grove)),
      ai = withResources()
    )
    val result = BattleEngine.tick(battle, deltaMs = Balance.ElfSpawnIntervalMs)
    assertEqualsDouble(result.ai.creatures.head.maxHp, Balance.ElfMaxHp, 1e-9)
  }

  test("the AI builds something once it can afford one, on either side (symmetric)") {
    val battle = BattleState.initial
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(buildingCount(result.ai), 1)
  }

  test("a strategy's maybeDestroy is applied to its own side, before it builds again") {
    val demolisher: AiStrategy = new AiStrategy:
      val name = "demolisher"
      def maybeBuild(state: MazeState, opponent: MazeState): MazeState = state
      override def maybeDestroy(state: MazeState, opponent: MazeState): MazeState =
        Demolition.tryDestroy(state, 5, 5).getOrElse(state)
    val forest = Building(1, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val battle = BattleState.initial.copy(ai = MazeState.initial.copy(buildings = List(forest)))
    val result = BattleEngine.tick(battle, deltaMs = 1.0, aiStrategy = demolisher)
    assertEquals(result.ai.buildings.count(_.kind == BuildingKind.Forest), 0)
  }

  test(
    "the default AiStrategy.maybeDestroy is a no-op, so existing strategies never tear anything down"
  ) {
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

  // RateLimited (see AiStrategy.buildCooldownMs's doc) lets a strategy's own build/upgrade/
  // research pace be tuned independently of what/where it builds — BattleEngine must
  // actually read the *strategy's* cooldown, not always fall back to the shared constant.
  test(
    "a RateLimited strategy resets its cooldown to its own buildCooldownMs, not the shared default"
  ) {
    val fast = RateLimited(LinearStrategy, buildCooldownMs = 10.0)
    val battle = BattleState.initial.copy(ai = withResources(wood = 10_000.0, fire = 10_000.0))
    val afterFirstBuild = BattleEngine.tick(battle, deltaMs = 1.0, aiStrategy = fast)
    assertEquals(buildingCount(afterFirstBuild.ai), 1)
    assertEquals(afterFirstBuild.aiBuildCooldownMs, 10.0)

    // Its short 10ms cooldown clears well before Balance.AiBuildCooldownMs (3000ms) would —
    // a second building appears almost immediately, unlike the default-cooldown test above.
    val secondBuild = BattleEngine.tick(afterFirstBuild, deltaMs = 9.0, aiStrategy = fast)
    assertEquals(buildingCount(secondBuild.ai), 1) // 9ms < 10ms, still cooling down
    val thirdTick = BattleEngine.tick(secondBuild, deltaMs = 1.0, aiStrategy = fast)
    assertEquals(buildingCount(thirdTick.ai), 2)
  }

  test(
    "a goblin pillaging the player steals Gold directly, capped for the player but not for the AI"
  ) {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val incomingGoblin =
      Creature(
        1,
        goalPos,
        Balance.GoblinMaxHp,
        Balance.GoblinMaxHp,
        speedPerMs = 0.0,
        UnitKind.Goblin
      )
    val battle = BattleState(
      // Gold set below the nominal plunder amount on purpose, to prove the attacker's
      // credit isn't capped by it — "you always win res, even if the opponent does not
      // have them" (project owner's explicit request).
      player = withResources()
        .copy(resources = Map(Resource.Gold -> 0.5), creatures = List(incomingGoblin)),
      ai = withResources() // isolates the plunder-credit effect from production
    )
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    // The victim's own loss is capped at what they actually had.
    assertEqualsDouble(result.player.resources.getOrElse(Resource.Gold, 0.0), 0.0, 1e-9)
    // Goblin steals Gold directly ("steal gold, not convert" — project owner's explicit
    // request) — the FULL nominal 2 * PlunderPerUnit, not just the 0.5 the player had.
    assertEqualsDouble(
      result.ai.resources.getOrElse(Resource.Gold, 0.0),
      2 * Balance.PlunderPerUnit,
      1e-9
    )
    assertEqualsDouble(result.ai.resourcesPlundered, 2 * Balance.PlunderPerUnit, 1e-9)
  }

  test(
    "a minotaur pillaging the player steals Gold directly, capped for the player but not for the AI"
  ) {
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
      player = withResources()
        .copy(resources = Map(Resource.Gold -> 5.0), creatures = List(incomingMinotaur)),
      ai = withResources() // isolates the plunder-credit effect from production
    )
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEqualsDouble(
      result.player.resources.getOrElse(Resource.Gold, 0.0),
      0.0,
      1e-9
    ) // capped: only 5 Gold available
    // Uncapped: the full nominal 2 * MinotaurPlunderPerUnit, not just the 5 Gold the player
    // actually had to lose.
    assertEqualsDouble(
      result.ai.resources.getOrElse(Resource.Gold, 0.0),
      2 * Balance.MinotaurPlunderPerUnit,
      1e-9
    )
    assertEqualsDouble(result.ai.resourcesPlundered, 2 * Balance.MinotaurPlunderPerUnit, 1e-9)
  }

  test(
    "an elf pillaging the player drains the player's real Wood and credits the AI real Wood too, uncapped"
  ) {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val incomingElf =
      Creature(1, goalPos, Balance.ElfMaxHp, Balance.ElfMaxHp, speedPerMs = 0.0, UnitKind.Elf)
    val battle = BattleState(
      // Wood set below the nominal plunder amount, same reasoning as the Goblin test above.
      player = withResources(wood = 0.1).copy(creatures = List(incomingElf)),
      ai = withResources()
    )
    val result = BattleEngine.tick(battle, deltaMs = 1.0)
    assertEquals(result.player.resources(Resource.Wood), 0.0) // capped: only 0.1 available
    // Elf pays out real Wood, not Gold (UnitKind.Elf.plunder) — and the full
    // nominal PlunderPerUnit, not the 0.1 the player actually had to lose.
    assertEquals(result.ai.resources(Resource.Wood), Balance.PlunderPerUnit)
    assertEquals(result.ai.resources.getOrElse(Resource.Gold, 0.0), 0.0)
    assertEquals(result.ai.resourcesPlundered, Balance.PlunderPerUnit)
  }

  test(
    "a zombie corrupting a building to destruction credits the full cost and tally to the AI, plus a Gold bonus"
  ) {
    val almostCorrupted = Building(
      1,
      col = 5,
      row = 5,
      BuildingKind.Grove,
      spawnCountdownMs = 0.0,
      corruptionPercent = Balance.CorruptionMaxPercent - Balance.ZombieCorruptionPercentPerSec
    )
    val incomingZombie =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        Balance.ZombieMaxHp,
        Balance.ZombieMaxHp,
        0.0,
        UnitKind.Zombie
      )
    val battle = BattleState(
      player =
        withResources().copy(buildings = List(almostCorrupted), creatures = List(incomingZombie)),
      ai = withResources() // isolates the corruption-credit effect from production
    )
    val result = BattleEngine.tick(battle, deltaMs = 1000.0)
    assertEquals(result.player.buildings, Nil)
    assertEquals(
      result.player.buildingsCorrupted,
      0
    ) // this side lost the building, didn't corrupt one
    assertEquals(result.ai.resources(Resource.Wood), Balance.GroveCostWood)
    assertEquals(result.ai.resources(Resource.Gold), Balance.CorruptionGoldReward)
    assertEquals(result.ai.buildingsCorrupted, 1)
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

  // A plain per-tick counter, incremented once per BattleEngine.tick call regardless of
  // deltaMs — kept for logging/persistence (a tick number to correlate log lines by), but
  // NOT what Loi's own victory condition compares against anymore: equal tick counts mean
  // wildly different real durations depending on the caller (Simulator's fixed 100ms/tick
  // vs GameApp's real, frame-rate-dependent delta — a browser at 60fps calls `tick` ~6x
  // more often per second of wall-clock time than the simulator's 100ms convention
  // assumes). See elapsedMs below, and Balance.LoiVictoryMsThreshold's doc, for the fix.
  test("elapsedTicks starts at 0 and increments by exactly 1 per tick, regardless of deltaMs") {
    assertEquals(BattleState.initial.elapsedTicks, 0)
    val once = BattleEngine.tick(BattleState.initial, deltaMs = 250.0)
    assertEquals(once.elapsedTicks, 1)
    val twice = BattleEngine.tick(once, deltaMs = 1.0)
    assertEquals(twice.elapsedTicks, 2)
  }

  // Same "freeze on outcome" invariant as the forest-victory test above: a decided match
  // must stop advancing its own tick clock too, not just stop changing buildings/creatures
  // — otherwise a frozen match replayed for many more ticks (e.g. a UI left open after a
  // win) would silently drift elapsedTicks far past reality.
  test("elapsedTicks stops incrementing once the battle is frozen by an outcome") {
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
    assert(ticked.outcome.isDefined)
    val frozen = BattleEngine.tick(ticked, deltaMs = 10_000.0)
    assertEquals(frozen.elapsedTicks, ticked.elapsedTicks)
  }

  // elapsedMs accumulates actual simulated time (the sum of every deltaMs `tick` has been
  // called with) rather than counting calls — this is what Loi's victory condition compares
  // against Balance.LoiVictoryMsThreshold, so the same real duration means the same thing
  // whether the caller is the simulator's fixed 100ms/tick or the browser's real frame delta.
  test("elapsedMs starts at 0 and accumulates deltaMs per tick") {
    assertEquals(BattleState.initial.elapsedMs, 0.0)
    val once = BattleEngine.tick(BattleState.initial, deltaMs = 250.0)
    assertEquals(once.elapsedMs, 250.0)
    val twice = BattleEngine.tick(once, deltaMs = 16.67)
    assertEquals(twice.elapsedMs, 266.67)
  }

  // Same "freeze on outcome" invariant as elapsedTicks above — a decided match's simulated
  // clock must stop too, not just its call counter.
  test("elapsedMs stops accumulating once the battle is frozen by an outcome") {
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
    assert(ticked.outcome.isDefined)
    val frozen = BattleEngine.tick(ticked, deltaMs = 10_000.0)
    assertEquals(frozen.elapsedMs, ticked.elapsedMs)
  }
