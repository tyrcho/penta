package towerdefense.domain

class CombatEngineTest extends munit.FunSuite:

  test("enemy reaching the goal cell is removed") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val enemy =
      Enemy(1, goalPos, Balance.ElfMaxHp, Balance.ElfMaxHp, speedPerMs = 0.0, UnitKind.Elf)
    val state = MazeState.initial.copy(enemies = List(enemy))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.state.enemies, Nil)
  }

  test("enemy re-routes around a forest blocking its straight-line step") {
    val startPos = GridConfig.cellCenter(0, 0)
    val enemy =
      Enemy(1, startPos, Balance.ElfMaxHp, Balance.ElfMaxHp, speedPerMs = 1000.0, UnitKind.Elf)
    val blockingForest = Forest(100, col = 1, row = 0, elfSpawnInMs = Balance.ElfSpawnIntervalMs)
    val state = MazeState.initial.copy(enemies = List(enemy), forests = List(blockingForest))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(GridConfig.cellOf(result.state.enemies.head.pos), (0, 1))
  }

  test("enemy also re-routes around a cave (both building types block)") {
    val startPos = GridConfig.cellCenter(0, 0)
    val enemy = Enemy(
      1,
      startPos,
      Balance.GoblinMaxHp,
      Balance.GoblinMaxHp,
      speedPerMs = 1000.0,
      UnitKind.Goblin
    )
    val blockingCave = Cave(100, col = 1, row = 0, goblinSpawnInMs = Balance.GoblinSpawnIntervalMs)
    val state = MazeState.initial.copy(enemies = List(enemy), caves = List(blockingCave))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(GridConfig.cellOf(result.state.enemies.head.pos), (0, 1))
  }

  test("forest damages an adjacent enemy but not a distant one") {
    val forest = Forest(100, col = 5, row = 5, elfSpawnInMs = Balance.ElfSpawnIntervalMs)
    val adjacent =
      Enemy(1, GridConfig.cellCenter(6, 5), hp = 10.0, maxHp = 10.0, speedPerMs = 0.0, UnitKind.Elf)
    val distant =
      Enemy(2, GridConfig.cellCenter(0, 0), hp = 10.0, maxHp = 10.0, speedPerMs = 0.0, UnitKind.Elf)
    val state = MazeState.initial.copy(enemies = List(adjacent, distant), forests = List(forest))

    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.state.enemies.map(e => e.id -> e).toMap
    assertEquals(byId(1).hp, adjacent.hp - Balance.AuraDamagePerSec)
    assertEquals(byId(2).hp, distant.hp)
  }

  test("forest aura kills an enemy once its hp is depleted") {
    val forest = Forest(100, col = 5, row = 5, elfSpawnInMs = Balance.ElfSpawnIntervalMs)
    val enemy = Enemy(
      1,
      GridConfig.cellCenter(6, 5),
      hp = Balance.AuraDamagePerSec,
      maxHp = Balance.AuraDamagePerSec,
      speedPerMs = 0.0,
      UnitKind.Elf
    )
    val state = MazeState.initial.copy(enemies = List(enemy), forests = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.enemies, Nil)
  }

  test("forests produce wood, caves produce fire, over time") {
    val forest = Forest(100, col = 5, row = 5, elfSpawnInMs = Balance.ElfSpawnIntervalMs)
    val cave = Cave(101, col = 6, row = 6, goblinSpawnInMs = Balance.GoblinSpawnIntervalMs)
    val state =
      MazeState.initial.copy(forests = List(forest), caves = List(cave), wood = 0.0, fire = 0.0)
    val result = CombatEngine.tick(state, deltaMs = 2000.0)
    assertEquals(result.state.wood, Balance.WoodPerSecPerForest * 2.0)
    assertEquals(result.state.fire, Balance.FirePerSecPerCave * 2.0)
  }

  test("a forest emits exactly one elf-spawn signal per interval") {
    val forest = Forest(100, col = 5, row = 5, elfSpawnInMs = Balance.ElfSpawnIntervalMs)
    val state = MazeState.initial.copy(forests = List(forest))
    val before = CombatEngine.tick(state, deltaMs = Balance.ElfSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.ElfSpawnIntervalMs)
    assertEquals(before.spawnedElf, 0)
    assertEquals(at.spawnedElf, 1)
  }

  test("a cave emits exactly one goblin-spawn signal per interval") {
    val cave = Cave(100, col = 5, row = 5, goblinSpawnInMs = Balance.GoblinSpawnIntervalMs)
    val state = MazeState.initial.copy(caves = List(cave))
    val before = CombatEngine.tick(state, deltaMs = Balance.GoblinSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.GoblinSpawnIntervalMs)
    assertEquals(before.spawnedGoblin, 0)
    assertEquals(at.spawnedGoblin, 1)
  }

  test("a goblin reaching the goal plunders wood and fire, clamped to what's available") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val goblin =
      Enemy(1, goalPos, Balance.GoblinMaxHp, Balance.GoblinMaxHp, speedPerMs = 0.0, UnitKind.Goblin)
    val state = MazeState.initial.copy(enemies = List(goblin), wood = 0.5, fire = 100.0)
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.stolenWood, 0.5) // clamped: only 0.5 wood was available to steal
    assertEquals(result.stolenFire, Balance.PlunderPerUnit)
    assertEquals(result.state.wood, 0.0)
    assertEquals(result.state.fire, 100.0 - Balance.PlunderPerUnit)
  }

  test("an elf reaching the goal only plunders wood, not fire") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val elf = Enemy(1, goalPos, Balance.ElfMaxHp, Balance.ElfMaxHp, speedPerMs = 0.0, UnitKind.Elf)
    val state = MazeState.initial.copy(enemies = List(elf), wood = 5.0, fire = 5.0)
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.stolenWood, Balance.PlunderPerUnit)
    assertEquals(result.stolenFire, 0.0)
  }

  test("a labyrinthe emits exactly one minotaur-spawn signal per interval") {
    val labyrinthe =
      Labyrinth(100, col = 5, row = 5, minotaurSpawnInMs = Balance.MinotaurSpawnIntervalMs)
    val state = MazeState.initial.copy(labyrinths = List(labyrinthe))
    val before = CombatEngine.tick(state, deltaMs = Balance.MinotaurSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.MinotaurSpawnIntervalMs)
    assertEquals(before.spawnedMinotaur, 0)
    assertEquals(at.spawnedMinotaur, 1)
  }

  test("a minotaur reaching the goal plunders 10 of each resource, clamped to what's available") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val minotaur = Enemy(
      1,
      goalPos,
      Balance.MinotaurMaxHp,
      Balance.MinotaurMaxHp,
      speedPerMs = 0.0,
      UnitKind.Minotaur
    )
    val state = MazeState.initial.copy(enemies = List(minotaur), wood = 5.0, fire = 100.0)
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.stolenWood, 5.0) // clamped: only 5 wood was available to steal
    assertEquals(result.stolenFire, Balance.MinotaurPlunderPerUnit)
    assertEquals(result.state.wood, 0.0)
    assertEquals(result.state.fire, 100.0 - Balance.MinotaurPlunderPerUnit)
  }

  test("an eglise emits exactly one paladin-spawn signal per interval") {
    val eglise = Eglise(100, col = 5, row = 5, paladinSpawnInMs = Balance.PaladinSpawnIntervalMs)
    val state = MazeState.initial.copy(eglises = List(eglise))
    val before = CombatEngine.tick(state, deltaMs = Balance.PaladinSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.PaladinSpawnIntervalMs)
    assertEquals(before.spawnedPaladin, 0)
    assertEquals(at.spawnedPaladin, 1)
  }

  test("eglises produce light over time") {
    val eglise = Eglise(100, col = 5, row = 5, paladinSpawnInMs = Double.MaxValue)
    val state = MazeState.initial.copy(eglises = List(eglise), light = 0.0)
    val result = CombatEngine.tick(state, deltaMs = 2000.0)
    assertEquals(result.state.light, Balance.LightPerSecPerEglise * 2.0)
  }

  test("a paladin reaching the goal plunders nothing") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val paladin =
      Enemy(1, goalPos, Balance.PaladinMaxHp, Balance.PaladinMaxHp, speedPerMs = 0.0, UnitKind.Paladin)
    val state = MazeState.initial.copy(enemies = List(paladin), wood = 5.0, fire = 5.0)
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.stolenWood, 0.0)
    assertEquals(result.stolenFire, 0.0)
  }

  test("a paladin shields an adjacent unit from forest aura damage") {
    val forest = Forest(100, col = 5, row = 5, elfSpawnInMs = Balance.ElfSpawnIntervalMs)
    val shieldedPos = GridConfig.cellCenter(6, 5)
    val elf =
      Enemy(1, shieldedPos, hp = 10.0, maxHp = 10.0, speedPerMs = 0.0, UnitKind.Elf)
    val paladin =
      Enemy(2, shieldedPos, Balance.PaladinMaxHp, Balance.PaladinMaxHp, speedPerMs = 0.0, UnitKind.Paladin)
    val state = MazeState.initial.copy(enemies = List(elf, paladin), forests = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    // Balance.PaladinAuraDamageReductionPerSec fully cancels Balance.AuraDamagePerSec (both 2.0)
    val byId = result.state.enemies.map(e => e.id -> e).toMap
    assertEquals(byId(1).hp, elf.hp)
  }

  test("an unshielded unit still takes full forest aura damage") {
    val forest = Forest(100, col = 5, row = 5, elfSpawnInMs = Balance.ElfSpawnIntervalMs)
    val elf =
      Enemy(1, GridConfig.cellCenter(6, 5), hp = 10.0, maxHp = 10.0, speedPerMs = 0.0, UnitKind.Elf)
    val state = MazeState.initial.copy(enemies = List(elf), forests = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.enemies.head.hp, elf.hp - Balance.AuraDamagePerSec)
  }
