package towerdefense.domain

import towerdefense.domain.geometry.Vec2

class CombatEngineTest extends munit.FunSuite:

  private def withResources(wood: Double = 0.0, fire: Double = 0.0, light: Double = 0.0): MazeState =
    MazeState.initial.copy(
      resources = Map(Resource.Wood -> wood, Resource.Fire -> fire, Resource.Light -> light)
    )

  test("enemy reaching the goal cell is removed") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val creature =
      Creature(1, goalPos, Balance.ElfMaxHp, Balance.ElfMaxHp, speedPerMs = 0.0, UnitKind.Elf)
    val state = withResources().copy(creatures = List(creature))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.state.creatures, Nil)
  }

  test("enemy re-routes around a forest blocking its straight-line step") {
    val startPos = GridConfig.cellCenter(0, 0)
    val creature =
      Creature(1, startPos, Balance.ElfMaxHp, Balance.ElfMaxHp, speedPerMs = 1000.0, UnitKind.Elf)
    val blockingForest = Building(100, col = 1, row = 0, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val state = withResources().copy(creatures = List(creature), buildings = List(blockingForest))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(GridConfig.cellOf(result.state.creatures.head.pos), (0, 1))
  }

  test("enemy also re-routes around a cave (both building types block)") {
    val startPos = GridConfig.cellCenter(0, 0)
    val creature = Creature(
      1,
      startPos,
      Balance.GoblinMaxHp,
      Balance.GoblinMaxHp,
      speedPerMs = 1000.0,
      UnitKind.Goblin
    )
    val blockingCave = Building(100, col = 1, row = 0, BuildingKind.Cave, Balance.GoblinSpawnIntervalMs)
    val state = withResources().copy(creatures = List(creature), buildings = List(blockingCave))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(GridConfig.cellOf(result.state.creatures.head.pos), (0, 1))
  }

  test("forest damages an adjacent enemy but not a distant one") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val adjacent =
      Creature(1, GridConfig.cellCenter(6, 5), hp = 10.0, maxHp = 10.0, speedPerMs = 0.0, UnitKind.Elf)
    val distant =
      Creature(2, GridConfig.cellCenter(0, 0), hp = 10.0, maxHp = 10.0, speedPerMs = 0.0, UnitKind.Elf)
    val state = withResources().copy(creatures = List(adjacent, distant), buildings = List(forest))

    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.state.creatures.map(c => c.id -> c).toMap
    assertEquals(byId(1).hp, adjacent.hp - Balance.AuraDamagePerSec)
    assertEquals(byId(2).hp, distant.hp)
  }

  test("forest aura kills an enemy once its hp is depleted") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val creature = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = Balance.AuraDamagePerSec,
      maxHp = Balance.AuraDamagePerSec,
      speedPerMs = 0.0,
      UnitKind.Elf
    )
    val state = withResources().copy(creatures = List(creature), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures, Nil)
  }

  test("jungle damages an adjacent enemy just like a forest (aura is inherited by the top tier)") {
    val jungle = Building(100, col = 5, row = 5, BuildingKind.Jungle, Balance.WolfSpawnIntervalMs)
    val adjacent =
      Creature(1, GridConfig.cellCenter(6, 5), hp = 10.0, maxHp = 10.0, speedPerMs = 0.0, UnitKind.Elf)
    val state = withResources().copy(creatures = List(adjacent), buildings = List(jungle))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures.head.hp, adjacent.hp - Balance.AuraDamagePerSec)
  }

  test("a jungle emits exactly one wolf-spawn signal per interval, not an elf") {
    val jungle = Building(100, col = 5, row = 5, BuildingKind.Jungle, Balance.WolfSpawnIntervalMs)
    val state = withResources().copy(buildings = List(jungle))
    val before = CombatEngine.tick(state, deltaMs = Balance.WolfSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.WolfSpawnIntervalMs)
    assertEquals(before.spawned.getOrElse(UnitKind.Wolf, 0), 0)
    assertEquals(at.spawned.getOrElse(UnitKind.Wolf, 0), 1)
    assertEquals(at.spawned.getOrElse(UnitKind.Elf, 0), 0)
  }

  private def distanceMoved(before: Vec2, after: Vec2): Double =
    math.hypot(after.x - before.x, after.y - before.y)

  test("a wolf speeds up any creature within its aura range, itself included") {
    val wolf =
      Creature(1, GridConfig.cellCenter(5, 5), Balance.WolfMaxHp, Balance.WolfMaxHp, Balance.WolfSpeedPerMs, UnitKind.Wolf)
    val nearbyElf =
      Creature(2, GridConfig.cellCenter(6, 5), Balance.ElfMaxHp, Balance.ElfMaxHp, Balance.ElfSpeedPerMs, UnitKind.Elf)
    val state = withResources().copy(creatures = List(wolf, nearbyElf))
    val result = CombatEngine.tick(state, deltaMs = 10.0)
    val elfAfter = result.state.creatures.find(_.id == 2).get
    val elfAlone =
      CombatEngine.tick(withResources().copy(creatures = List(nearbyElf)), deltaMs = 10.0).state.creatures.head
    val boostedDistance = distanceMoved(nearbyElf.pos, elfAfter.pos)
    val aloneDistance = distanceMoved(nearbyElf.pos, elfAlone.pos)
    assert(
      boostedDistance > aloneDistance,
      s"expected the wolf-boosted elf to move further than $aloneDistance, but it moved $boostedDistance"
    )
    assertEqualsDouble(boostedDistance, aloneDistance * Balance.WolfSpeedAuraMultiplier, 1e-9)
  }

  test("a wolf's speed aura does not reach a creature more than 2 cells away") {
    val wolf =
      Creature(1, GridConfig.cellCenter(0, 0), Balance.WolfMaxHp, Balance.WolfMaxHp, Balance.WolfSpeedPerMs, UnitKind.Wolf)
    val farElf =
      Creature(2, GridConfig.cellCenter(5, 5), Balance.ElfMaxHp, Balance.ElfMaxHp, Balance.ElfSpeedPerMs, UnitKind.Elf)
    val state = withResources().copy(creatures = List(wolf, farElf))
    val result = CombatEngine.tick(state, deltaMs = 10.0)
    val boosted = result.state.creatures.find(_.id == 2).get
    val alone =
      CombatEngine.tick(withResources().copy(creatures = List(farElf)), deltaMs = 10.0).state.creatures.head
    assertEquals(boosted.pos, alone.pos)
  }

  test("forests produce wood, caves produce fire, over time") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val cave = Building(101, col = 6, row = 6, BuildingKind.Cave, Balance.GoblinSpawnIntervalMs)
    val state = withResources(wood = 0.0, fire = 0.0).copy(buildings = List(forest, cave))
    val result = CombatEngine.tick(state, deltaMs = 2000.0)
    assertEquals(result.state.resources(Resource.Wood), Balance.WoodPerSecPerForest * 2.0)
    assertEquals(result.state.resources(Resource.Fire), Balance.FirePerSecPerCave * 2.0)
  }

  test(
    "productionPerSec is the exact rate CombatEngine.tick applies — the UI reads this " +
      "instead of re-deriving its own"
  ) {
    val state = withResources().copy(
      buildings = List(
        Building(1, 0, 1, BuildingKind.Forest, 0.0),
        Building(2, 0, 2, BuildingKind.Forest, 0.0),
        Building(3, 0, 3, BuildingKind.Cave, 0.0),
        Building(4, 0, 4, BuildingKind.Church, 0.0)
      )
    )
    assertEquals(CombatEngine.productionPerSec(state, Resource.Wood), 2 * Balance.WoodPerSecPerForest)
    assertEquals(CombatEngine.productionPerSec(state, Resource.Fire), 1 * Balance.FirePerSecPerCave)
    assertEquals(CombatEngine.productionPerSec(state, Resource.Light), 1 * Balance.LightPerSecPerEglise)
  }

  test("a forest emits exactly one elf-spawn signal per interval") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val state = withResources().copy(buildings = List(forest))
    val before = CombatEngine.tick(state, deltaMs = Balance.ElfSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.ElfSpawnIntervalMs)
    assertEquals(before.spawned.getOrElse(UnitKind.Elf, 0), 0)
    assertEquals(at.spawned.getOrElse(UnitKind.Elf, 0), 1)
  }

  test("a cave emits exactly one goblin-spawn signal per interval") {
    val cave = Building(100, col = 5, row = 5, BuildingKind.Cave, Balance.GoblinSpawnIntervalMs)
    val state = withResources().copy(buildings = List(cave))
    val before = CombatEngine.tick(state, deltaMs = Balance.GoblinSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.GoblinSpawnIntervalMs)
    assertEquals(before.spawned.getOrElse(UnitKind.Goblin, 0), 0)
    assertEquals(at.spawned.getOrElse(UnitKind.Goblin, 0), 1)
  }

  test("a goblin reaching the goal plunders wood and fire, clamped to what's available") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val goblin =
      Creature(1, goalPos, Balance.GoblinMaxHp, Balance.GoblinMaxHp, speedPerMs = 0.0, UnitKind.Goblin)
    val state = withResources(wood = 0.5, fire = 100.0).copy(creatures = List(goblin))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.stolen.getOrElse(Resource.Wood, 0.0), 0.5) // clamped: only 0.5 wood available
    assertEquals(result.stolen.getOrElse(Resource.Fire, 0.0), Balance.PlunderPerUnit)
    assertEquals(result.state.resources(Resource.Wood), 0.0)
    assertEquals(result.state.resources(Resource.Fire), 100.0 - Balance.PlunderPerUnit)
  }

  test("an elf reaching the goal only plunders wood, not fire") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val elf = Creature(1, goalPos, Balance.ElfMaxHp, Balance.ElfMaxHp, speedPerMs = 0.0, UnitKind.Elf)
    val state = withResources(wood = 5.0, fire = 5.0).copy(creatures = List(elf))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.stolen.getOrElse(Resource.Wood, 0.0), Balance.PlunderPerUnit)
    assertEquals(result.stolen.getOrElse(Resource.Fire, 0.0), 0.0)
  }

  test("a labyrinthe emits exactly one minotaur-spawn signal per interval") {
    val labyrinthe = Building(100, col = 5, row = 5, BuildingKind.Labyrinth, Balance.MinotaurSpawnIntervalMs)
    val state = withResources().copy(buildings = List(labyrinthe))
    val before = CombatEngine.tick(state, deltaMs = Balance.MinotaurSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.MinotaurSpawnIntervalMs)
    assertEquals(before.spawned.getOrElse(UnitKind.Minotaur, 0), 0)
    assertEquals(at.spawned.getOrElse(UnitKind.Minotaur, 0), 1)
  }

  test("a minotaur reaching the goal plunders 10 of each resource, clamped to what's available") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val minotaur = Creature(
      1,
      goalPos,
      Balance.MinotaurMaxHp,
      Balance.MinotaurMaxHp,
      speedPerMs = 0.0,
      UnitKind.Minotaur
    )
    val state = withResources(wood = 5.0, fire = 100.0).copy(creatures = List(minotaur))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.stolen.getOrElse(Resource.Wood, 0.0), 5.0) // clamped: only 5 wood available
    assertEquals(result.stolen.getOrElse(Resource.Fire, 0.0), Balance.MinotaurPlunderPerUnit)
    assertEquals(result.state.resources(Resource.Wood), 0.0)
    assertEquals(result.state.resources(Resource.Fire), 100.0 - Balance.MinotaurPlunderPerUnit)
  }

  test("an eglise emits exactly one paladin-spawn signal per interval") {
    val eglise = Building(100, col = 5, row = 5, BuildingKind.Church, Balance.PaladinSpawnIntervalMs)
    val state = withResources().copy(buildings = List(eglise))
    val before = CombatEngine.tick(state, deltaMs = Balance.PaladinSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.PaladinSpawnIntervalMs)
    assertEquals(before.spawned.getOrElse(UnitKind.Paladin, 0), 0)
    assertEquals(at.spawned.getOrElse(UnitKind.Paladin, 0), 1)
  }

  test("eglises produce light over time") {
    val eglise = Building(100, col = 5, row = 5, BuildingKind.Church, Double.MaxValue)
    val state = withResources(light = 0.0).copy(buildings = List(eglise))
    val result = CombatEngine.tick(state, deltaMs = 2000.0)
    assertEquals(result.state.resources(Resource.Light), Balance.LightPerSecPerEglise * 2.0)
  }

  test("watchtowers also produce light, alongside eglises") {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val state = withResources(light = 0.0).copy(buildings = List(watchtower))
    val result = CombatEngine.tick(state, deltaMs = 2000.0)
    assertEquals(result.state.resources(Resource.Light), Balance.LightPerSecPerWatchtower * 2.0)
  }

  test("a watchtower damages the nearest enemy within its range every tick") {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val nearby =
      Creature(1, GridConfig.cellCenter(6, 6), hp = 100.0, maxHp = 100.0, speedPerMs = 0.0, UnitKind.Elf)
    val state = withResources().copy(creatures = List(nearby), buildings = List(watchtower))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures.head.hp, nearby.hp - Balance.WatchtowerDamagePerSec)
  }

  test("a watchtower does not damage an enemy beyond its range") {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val farAway = Creature(
      1,
      GridConfig.cellCenter(5 + Balance.WatchtowerRangeCells + 1, 5),
      10.0,
      10.0,
      0.0,
      UnitKind.Elf
    )
    val state = withResources().copy(creatures = List(farAway), buildings = List(watchtower))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures.head.hp, farAway.hp)
  }

  test("a watchtower only damages one target even when several are in range") {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val closer = Creature(1, GridConfig.cellCenter(6, 5), 100.0, 100.0, 0.0, UnitKind.Elf)
    val farther = Creature(2, GridConfig.cellCenter(7, 5), 100.0, 100.0, 0.0, UnitKind.Elf)
    val state = withResources().copy(creatures = List(closer, farther), buildings = List(watchtower))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.state.creatures.map(c => c.id -> c).toMap
    assertEquals(byId(1).hp, closer.hp - Balance.WatchtowerDamagePerSec)
    assertEquals(byId(2).hp, farther.hp)
  }

  test("a paladin shields its target from watchtower damage too, not just forest aura") {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val shieldedPos = GridConfig.cellCenter(6, 5)
    val elf = Creature(1, shieldedPos, hp = 10.0, maxHp = 10.0, speedPerMs = 0.0, UnitKind.Elf)
    val paladin = Creature(
      2,
      shieldedPos,
      Balance.PaladinMaxHp,
      Balance.PaladinMaxHp,
      speedPerMs = 0.0,
      UnitKind.Paladin
    )
    val state = withResources().copy(creatures = List(elf, paladin), buildings = List(watchtower))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.state.creatures.map(c => c.id -> c).toMap
    // Balance.PaladinAuraDamageReductionPerSec (2.0) reduces but doesn't fully cancel
    // WatchtowerDamagePerSec (10.0), unlike Forest's weaker aura.
    assertEquals(
      byId(1).hp,
      elf.hp - (Balance.WatchtowerDamagePerSec - Balance.PaladinAuraDamageReductionPerSec)
    )
  }

  test("a paladin reaching the goal plunders nothing") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val paladin = Creature(
      1,
      goalPos,
      Balance.PaladinMaxHp,
      Balance.PaladinMaxHp,
      speedPerMs = 0.0,
      UnitKind.Paladin
    )
    val state = withResources(wood = 5.0, fire = 5.0).copy(creatures = List(paladin))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.stolen.getOrElse(Resource.Wood, 0.0), 0.0)
    assertEquals(result.stolen.getOrElse(Resource.Fire, 0.0), 0.0)
  }

  test("a paladin shields an adjacent unit from forest aura damage") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val shieldedPos = GridConfig.cellCenter(6, 5)
    val elf = Creature(1, shieldedPos, hp = 10.0, maxHp = 10.0, speedPerMs = 0.0, UnitKind.Elf)
    val paladin = Creature(
      2,
      shieldedPos,
      Balance.PaladinMaxHp,
      Balance.PaladinMaxHp,
      speedPerMs = 0.0,
      UnitKind.Paladin
    )
    val state = withResources().copy(creatures = List(elf, paladin), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    // Balance.PaladinAuraDamageReductionPerSec fully cancels Balance.AuraDamagePerSec (both 2.0)
    val byId = result.state.creatures.map(c => c.id -> c).toMap
    assertEquals(byId(1).hp, elf.hp)
  }

  test("an unshielded unit still takes full forest aura damage") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val elf =
      Creature(1, GridConfig.cellCenter(6, 5), hp = 10.0, maxHp = 10.0, speedPerMs = 0.0, UnitKind.Elf)
    val state = withResources().copy(creatures = List(elf), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures.head.hp, elf.hp - Balance.AuraDamagePerSec)
  }
