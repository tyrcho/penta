package towerdefense.domain

import towerdefense.domain.geometry.Vec2

class CombatEngineTest extends munit.FunSuite:

  private def withResources(
      wood: Double = 0.0,
      fire: Double = 0.0,
      light: Double = 0.0
  ): MazeState =
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
    val blockingForest =
      Building(100, col = 1, row = 0, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
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
    val blockingCave =
      Building(100, col = 1, row = 0, BuildingKind.Cave, Balance.GoblinSpawnIntervalMs)
    val state = withResources().copy(creatures = List(creature), buildings = List(blockingCave))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(GridConfig.cellOf(result.state.creatures.head.pos), (0, 1))
  }

  test("forest damages an adjacent enemy but not a distant one") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val adjacent =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 10.0,
        maxHp = 10.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val distant =
      Creature(
        2,
        GridConfig.cellCenter(0, 0),
        hp = 10.0,
        maxHp = 10.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
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
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 10.0,
        maxHp = 10.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
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
      Creature(
        1,
        GridConfig.cellCenter(5, 5),
        Balance.WolfMaxHp,
        Balance.WolfMaxHp,
        Balance.WolfSpeedPerMs,
        UnitKind.Wolf
      )
    val nearbyElf =
      Creature(
        2,
        GridConfig.cellCenter(6, 5),
        Balance.ElfMaxHp,
        Balance.ElfMaxHp,
        Balance.ElfSpeedPerMs,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(wolf, nearbyElf))
    val result = CombatEngine.tick(state, deltaMs = 10.0)
    val elfAfter = result.state.creatures.find(_.id == 2).get
    val elfAlone =
      CombatEngine
        .tick(withResources().copy(creatures = List(nearbyElf)), deltaMs = 10.0)
        .state
        .creatures
        .head
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
      Creature(
        1,
        GridConfig.cellCenter(0, 0),
        Balance.WolfMaxHp,
        Balance.WolfMaxHp,
        Balance.WolfSpeedPerMs,
        UnitKind.Wolf
      )
    val farElf =
      Creature(
        2,
        GridConfig.cellCenter(5, 5),
        Balance.ElfMaxHp,
        Balance.ElfMaxHp,
        Balance.ElfSpeedPerMs,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(wolf, farElf))
    val result = CombatEngine.tick(state, deltaMs = 10.0)
    val boosted = result.state.creatures.find(_.id == 2).get
    val alone =
      CombatEngine
        .tick(withResources().copy(creatures = List(farElf)), deltaMs = 10.0)
        .state
        .creatures
        .head
    assertEquals(boosted.pos, alone.pos)
  }

  test("forests produce wood, caves produce fire, over time") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val cave = Building(101, col = 6, row = 6, BuildingKind.Cave, Balance.GoblinSpawnIntervalMs)
    val state = withResources(wood = 0.0, fire = 0.0).copy(buildings = List(forest, cave))
    val result = CombatEngine.tick(state, deltaMs = 2000.0)
    // Wood has no boost here (its Engendre source is Light, and nothing here produces
    // Light), but Fire's Engendre source is Wood — the one Forest gives it a +5% boost
    // (Balance.EngendreBoostPerBuilding) on top of Cave's own base rate.
    assertEquals(result.state.resources(Resource.Wood), Balance.WoodPerSecPerForest * 2.0)
    assertEquals(
      result.state.resources(Resource.Fire),
      Balance.FirePerSecPerCave * (1.0 + Balance.EngendreBoostPerBuilding) * 2.0
    )
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
    // Wood's Engendre source is Light — the one Church gives it a +5% boost. Fire's
    // Engendre source is Wood — the two Forests give it a +10% boost. Light's Engendre
    // source is Crystal, absent here, so Light stays exactly at its base rate.
    assertEquals(
      CombatEngine.productionPerSec(state, Resource.Wood),
      2 * Balance.WoodPerSecPerForest * (1.0 + Balance.EngendreBoostPerBuilding)
    )
    assertEquals(
      CombatEngine.productionPerSec(state, Resource.Fire),
      1 * Balance.FirePerSecPerCave * (1.0 + 2 * Balance.EngendreBoostPerBuilding)
    )
    assertEquals(
      CombatEngine.productionPerSec(state, Resource.Light),
      1 * Balance.LightPerSecPerEglise
    )
  }

  // ── Engendre production-boost rule ────────────────────────────────────

  test("each Engendre-source building adds a flat +5% to the next resource's production rate") {
    val caves = List(
      Building(1, 0, 1, BuildingKind.Cave, 0.0),
      Building(2, 0, 2, BuildingKind.Cave, 0.0),
      Building(3, 0, 3, BuildingKind.Cave, 0.0)
    )
    // Shadow's Engendre source is Fire — three Fire-producing Caves give it a flat +15%
    // multiplier, even though nothing here produces Shadow itself yet (base rate 0).
    val state = withResources().copy(buildings = caves)
    assertEqualsDouble(
      CombatEngine.engendreBoost(state, Resource.Shadow),
      3 * Balance.EngendreBoostPerBuilding,
      1e-9
    )
    assertEquals(CombatEngine.productionPerSec(state, Resource.Shadow), 0.0)
  }

  test(
    "the Engendre boost only counts buildings that produce the *source* resource, not any building"
  ) {
    // Church produces Light, not Fire — it shouldn't count toward Shadow's boost (whose
    // source is Fire), even though it's a building "in play".
    val church = Building(1, 5, 5, BuildingKind.Church, 0.0)
    val state = withResources().copy(buildings = List(church))
    assertEquals(CombatEngine.engendreBoost(state, Resource.Shadow), 0.0)
  }

  test(
    "the Engendre cycle wraps: Light producers boost Wood, closing Wood -> Fire -> Shadow -> Crystal -> Light -> Wood"
  ) {
    val church = Building(1, 5, 5, BuildingKind.Church, 0.0)
    val grove = Building(2, 6, 6, BuildingKind.Grove, 0.0)
    val state = withResources().copy(buildings = List(church, grove))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(
      result.state.resources(Resource.Wood) - state.resources(Resource.Wood),
      Balance.WoodPerSecPerGrove * (1.0 + Balance.EngendreBoostPerBuilding)
    )
  }

  // ── Lab research boosts its own Crystal production ────────────────────
  // Note sur les laboratoires.md: "Chaque amelioration (recherche) dans un labo augmente
  // sa production de crystal de 75% par rapport au niveau precedent" — distinct from each
  // lab's own separately-researched effect (cost reduction/opponent target/plunder/damage),
  // which reads the exact same researchLevels entry for a different purpose.

  test("an unresearched lab produces Crystal at its plain base rate") {
    val labo = Building(1, 0, 1, BuildingKind.LaboNaturel, 0.0)
    val state = withResources().copy(buildings = List(labo))
    assertEquals(
      CombatEngine.productionPerSec(state, Resource.Crystal),
      Balance.CrystalPerSecPerLaboNaturel
    )
  }

  test("one research level on a lab increases its own Crystal production by exactly 75%") {
    val labo = Building(1, 0, 1, BuildingKind.LaboNaturel, 0.0)
    val state = withResources()
      .copy(buildings = List(labo), researchLevels = Map(BuildingKind.LaboNaturel -> 1))
    assertEqualsDouble(
      CombatEngine.productionPerSec(state, Resource.Crystal),
      Balance.CrystalPerSecPerLaboNaturel * 1.75,
      1e-9
    )
  }

  test("research boost compounds: level 2 is 75% more than level 1, not just double level 1") {
    val labo = Building(1, 0, 1, BuildingKind.LaboSombre, 0.0)
    val state = withResources()
      .copy(buildings = List(labo), researchLevels = Map(BuildingKind.LaboSombre -> 2))
    assertEqualsDouble(
      CombatEngine.productionPerSec(state, Resource.Crystal),
      Balance.CrystalPerSecPerLaboSombre * 1.75 * 1.75,
      1e-9
    )
  }

  test("a lab's research boost never leaks onto another lab kind's Crystal production") {
    val naturel = Building(1, 0, 1, BuildingKind.LaboNaturel, 0.0)
    val sombre = Building(2, 0, 2, BuildingKind.LaboSombre, 0.0)
    // Only LaboNaturel is researched — LaboSombre must still produce at its plain base rate.
    val state = withResources()
      .copy(buildings = List(naturel, sombre), researchLevels = Map(BuildingKind.LaboNaturel -> 3))
    assertEqualsDouble(
      CombatEngine.productionPerSec(state, Resource.Crystal),
      Balance.CrystalPerSecPerLaboNaturel * math.pow(1.75, 3) + Balance.CrystalPerSecPerLaboSombre,
      1e-9
    )
  }

  test("a lab's research level never boosts a *different* resource's production") {
    // LaboDeLaLoi's research level is keyed the same as Recherches loyales' building-damage
    // effect — that shouldn't spill over into boosting some other building's Wood output.
    val grove = Building(1, 0, 1, BuildingKind.Grove, 0.0)
    val state = withResources()
      .copy(buildings = List(grove), researchLevels = Map(BuildingKind.LaboDeLaLoi -> 4))
    assertEquals(CombatEngine.productionPerSec(state, Resource.Wood), Balance.WoodPerSecPerGrove)
  }

  // ── Construction time (buildings take time to build) ───────────────────

  test("a building still under construction produces nothing, even at a normally nonzero rate") {
    val grove = Building(
      1,
      5,
      5,
      BuildingKind.Grove,
      Balance.ElfSpawnIntervalMs,
      constructionRemainingMs = 1_000.0
    )
    val state = withResources().copy(buildings = List(grove))
    assertEquals(CombatEngine.productionPerSec(state, Resource.Wood), 0.0)
  }

  test(
    "a building never emits a spawn signal while still under construction, however long deltaMs is"
  ) {
    val forest = Building(
      100,
      col = 5,
      row = 5,
      kind = BuildingKind.Forest,
      spawnCountdownMs = 0.0,
      constructionRemainingMs = 1_000_000.0
    )
    val state = withResources().copy(buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = Balance.ElfSpawnIntervalMs * 10)
    assertEquals(result.spawned.getOrElse(UnitKind.Elf, 0), 0)
    // Frozen, not just skipped-this-tick: an under-construction building's own spawn
    // timer doesn't advance at all while it waits out its construction.
    assertEquals(result.state.buildings.head.spawnCountdownMs, 0.0)
  }

  test("a forest deals no aura damage while still under construction") {
    val forest = Building(
      100,
      col = 5,
      row = 5,
      kind = BuildingKind.Forest,
      spawnCountdownMs = Balance.ElfSpawnIntervalMs,
      constructionRemainingMs = 5_000.0
    )
    val adjacent =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 10.0,
        maxHp = 10.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(adjacent), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures.head.hp, 10.0)
  }

  test("a watchtower deals no damage while still under construction") {
    val watchtower = Building(
      100,
      col = 5,
      row = 5,
      kind = BuildingKind.Watchtower,
      spawnCountdownMs = 0.0,
      constructionRemainingMs = 5_000.0
    )
    val adjacent =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 10.0,
        maxHp = 10.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(adjacent), buildings = List(watchtower))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures.head.hp, 10.0)
  }

  test("a passing gate under construction neither harvests shadow nor flashes for a nearby death") {
    val gate = Building(
      100,
      col = 5,
      row = 5,
      kind = BuildingKind.PassingGate,
      spawnCountdownMs = 0.0,
      constructionRemainingMs = 5_000.0
    )
    val dying =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 1.0,
        maxHp = 100.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val watchtower = Building(101, col = 6, row = 5, BuildingKind.Watchtower, 0.0)
    val resources =
      Map(
        Resource.Wood -> 100.0,
        Resource.Fire -> 0.0,
        Resource.Light -> 0.0,
        Resource.Shadow -> 20.0,
        Resource.Crystal -> 0.0
      )
    val state = MazeState.initial.copy(
      resources = resources,
      creatures = List(dying),
      buildings = List(gate, watchtower)
    )
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures, Nil) // the watchtower still kills it
    assertEqualsDouble(result.state.resources(Resource.Shadow), resources(Resource.Shadow), 1e-9)
    assertEquals(result.state.buildings.find(_.kind == BuildingKind.PassingGate).get.flashMs, 0.0)
  }

  test(
    "constructionRemainingMs counts down each tick and floors at zero rather than going negative"
  ) {
    val cave = Building(
      100,
      col = 5,
      row = 5,
      kind = BuildingKind.Cave,
      spawnCountdownMs = Balance.GoblinSpawnIntervalMs,
      constructionRemainingMs = 1_500.0
    )
    val state = withResources().copy(buildings = List(cave))
    val afterPartial = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEqualsDouble(afterPartial.state.buildings.head.constructionRemainingMs, 500.0, 1e-9)
    val afterFull = CombatEngine.tick(afterPartial.state, deltaMs = 1000.0)
    assertEquals(afterFull.state.buildings.head.constructionRemainingMs, 0.0)
  }

  test("constructionTotalMs is untouched by ticking — only constructionRemainingMs counts down") {
    val cave = Building(
      100,
      col = 5,
      row = 5,
      kind = BuildingKind.Cave,
      spawnCountdownMs = Balance.GoblinSpawnIntervalMs,
      constructionRemainingMs = 1_500.0,
      constructionTotalMs = 1_500.0
    )
    val state = withResources().copy(buildings = List(cave))
    val afterPartial = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEqualsDouble(afterPartial.state.buildings.head.constructionTotalMs, 1_500.0, 1e-9)
  }

  test("a building resumes producing once its construction timer has fully counted down") {
    val grove = Building(
      1,
      5,
      5,
      BuildingKind.Grove,
      Balance.ElfSpawnIntervalMs,
      constructionRemainingMs = 0.0
    )
    val state = withResources().copy(buildings = List(grove))
    assertEquals(CombatEngine.productionPerSec(state, Resource.Wood), Balance.WoodPerSecPerGrove)
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

  test(
    "a goblin reaching the goal steals Gold directly, clamped for the victim but not for the attacker"
  ) {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val goblin =
      Creature(
        1,
        goalPos,
        Balance.GoblinMaxHp,
        Balance.GoblinMaxHp,
        speedPerMs = 0.0,
        UnitKind.Goblin
      )
    // Gold set below the nominal plunder amount, to prove the victim's own loss is capped
    // while the attacker's credit (checked below) is not.
    val state =
      MazeState.initial.copy(resources = Map(Resource.Gold -> 0.5), creatures = List(goblin))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEqualsDouble(
      result.stolen.getOrElse(Resource.Gold, 0.0),
      0.5,
      1e-9
    ) // clamped: only 0.5 Gold available
    assertEqualsDouble(result.state.resources(Resource.Gold), 0.0, 1e-9)
    // Goblin steals Gold directly now ("steal gold, not convert" — project owner's
    // explicit request) — the full nominal 2 * PlunderPerUnit, not clamped by the 0.5
    // the victim actually had.
    assertEqualsDouble(
      result.plundered.getOrElse(Resource.Gold, 0.0),
      2 * Balance.PlunderPerUnit,
      1e-9
    )
  }

  test("an elf reaching the goal only plunders wood, not fire or gold") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val elf =
      Creature(1, goalPos, Balance.ElfMaxHp, Balance.ElfMaxHp, speedPerMs = 0.0, UnitKind.Elf)
    val state = withResources(wood = 5.0, fire = 5.0).copy(creatures = List(elf))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.stolen.getOrElse(Resource.Wood, 0.0), Balance.PlunderPerUnit)
    assertEquals(result.stolen.getOrElse(Resource.Fire, 0.0), 0.0)
    assertEquals(result.stolen.getOrElse(Resource.Gold, 0.0), 0.0)
    assertEquals(result.plundered, Map(Resource.Wood -> Balance.PlunderPerUnit))
  }

  test(
    "an elf's plundered credit is the full nominal Wood amount even when the victim has less than that"
  ) {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val elf =
      Creature(1, goalPos, Balance.ElfMaxHp, Balance.ElfMaxHp, speedPerMs = 0.0, UnitKind.Elf)
    val state = withResources(wood = 0.1).copy(creatures = List(elf))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    // The victim's own loss is still clamped (can't go negative)...
    assertEqualsDouble(result.stolen.getOrElse(Resource.Wood, 0.0), 0.1, 1e-9)
    // ...but the attacker's credit is the full plunder amount regardless.
    assertEqualsDouble(result.plundered.getOrElse(Resource.Wood, 0.0), Balance.PlunderPerUnit, 1e-9)
  }

  test("a labyrinthe emits exactly one minotaur-spawn signal per interval") {
    val labyrinthe =
      Building(100, col = 5, row = 5, BuildingKind.Labyrinth, Balance.MinotaurSpawnIntervalMs)
    val state = withResources().copy(buildings = List(labyrinthe))
    val before = CombatEngine.tick(state, deltaMs = Balance.MinotaurSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.MinotaurSpawnIntervalMs)
    assertEquals(before.spawned.getOrElse(UnitKind.Minotaur, 0), 0)
    assertEquals(at.spawned.getOrElse(UnitKind.Minotaur, 0), 1)
  }

  test(
    "a minotaur reaching the goal steals Gold directly, clamped for the victim but not for the attacker"
  ) {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val minotaur = Creature(
      1,
      goalPos,
      Balance.MinotaurMaxHp,
      Balance.MinotaurMaxHp,
      speedPerMs = 0.0,
      UnitKind.Minotaur
    )
    val state =
      MazeState.initial.copy(resources = Map(Resource.Gold -> 5.0), creatures = List(minotaur))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEqualsDouble(
      result.stolen.getOrElse(Resource.Gold, 0.0),
      5.0,
      1e-9
    ) // clamped: only 5 Gold available
    assertEqualsDouble(result.state.resources(Resource.Gold), 0.0, 1e-9)
    // Uncapped: the full nominal 2 * MinotaurPlunderPerUnit, not clamped by the 5 the
    // victim actually had.
    assertEqualsDouble(
      result.plundered.getOrElse(Resource.Gold, 0.0),
      2 * Balance.MinotaurPlunderPerUnit,
      1e-9
    )
  }

  test("an eglise emits exactly one paladin-spawn signal per interval") {
    val eglise =
      Building(100, col = 5, row = 5, BuildingKind.Church, Balance.PaladinSpawnIntervalMs)
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
      Creature(
        1,
        GridConfig.cellCenter(6, 6),
        hp = 100.0,
        maxHp = 100.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
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
    // Both (6,5) and (6,6) are Chebyshev distance 1 from (5,5) — in range regardless of
    // WatchtowerRangeCells' exact value — but (6,6) is diagonal, so strictly farther in
    // real (Euclidean) distance, the tie-break nearestTargetInRange actually uses.
    val closer = Creature(1, GridConfig.cellCenter(6, 5), 100.0, 100.0, 0.0, UnitKind.Elf)
    val farther = Creature(2, GridConfig.cellCenter(6, 6), 100.0, 100.0, 0.0, UnitKind.Elf)
    val state =
      withResources().copy(creatures = List(closer, farther), buildings = List(watchtower))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.state.creatures.map(c => c.id -> c).toMap
    assertEquals(byId(1).hp, closer.hp - Balance.WatchtowerDamagePerSec)
    assertEquals(byId(2).hp, farther.hp)
  }

  test("angels produce light over time, at their own rate") {
    val angel = Building(100, col = 5, row = 5, BuildingKind.Angel, 0.0)
    val state = withResources(light = 0.0).copy(buildings = List(angel))
    val result = CombatEngine.tick(state, deltaMs = 2000.0)
    assertEquals(result.state.resources(Resource.Light), Balance.LightPerSecPerAngel * 2.0)
  }

  test("an angel damages an adjacent enemy at its own rate, not Forest's AuraDamagePerSec") {
    val angel = Building(100, col = 5, row = 5, BuildingKind.Angel, 0.0)
    val adjacent =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 100.0,
        maxHp = 100.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val distant =
      Creature(
        2,
        GridConfig.cellCenter(0, 0),
        hp = 100.0,
        maxHp = 100.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(adjacent, distant), buildings = List(angel))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.state.creatures.map(c => c.id -> c).toMap
    assertEquals(byId(1).hp, adjacent.hp - Balance.AngelDamagePerSec)
    assertEquals(byId(2).hp, distant.hp)
  }

  test("an angel slows an adjacent enemy's movement by AngelSlowFraction") {
    val angel = Building(100, col = 5, row = 5, BuildingKind.Angel, 0.0)
    val nearbyElf =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        Balance.ElfMaxHp,
        Balance.ElfMaxHp,
        Balance.ElfSpeedPerMs,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(nearbyElf), buildings = List(angel))
    val result = CombatEngine.tick(state, deltaMs = 10.0)
    val slowedElf = result.state.creatures.find(_.id == 1).get
    val aloneElf =
      CombatEngine
        .tick(withResources().copy(creatures = List(nearbyElf)), deltaMs = 10.0)
        .state
        .creatures
        .head
    val slowedDistance = distanceMoved(nearbyElf.pos, slowedElf.pos)
    val aloneDistance = distanceMoved(nearbyElf.pos, aloneElf.pos)
    assertEqualsDouble(slowedDistance, aloneDistance * (1.0 - Balance.AngelSlowFraction), 1e-9)
  }

  test("an angel's slow does not reach a creature on a non-adjacent cell") {
    val angel = Building(100, col = 5, row = 5, BuildingKind.Angel, 0.0)
    val farElf =
      Creature(
        1,
        GridConfig.cellCenter(0, 0),
        Balance.ElfMaxHp,
        Balance.ElfMaxHp,
        Balance.ElfSpeedPerMs,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(farElf), buildings = List(angel))
    val result = CombatEngine.tick(state, deltaMs = 10.0)
    val unaffected = result.state.creatures.find(_.id == 1).get
    val alone =
      CombatEngine
        .tick(withResources().copy(creatures = List(farElf)), deltaMs = 10.0)
        .state
        .creatures
        .head
    assertEquals(unaffected.pos, alone.pos)
  }

  test("a wolf's speed boost and an angel's slow stack multiplicatively") {
    val angel = Building(100, col = 5, row = 5, BuildingKind.Angel, 0.0)
    // A Wolf never boosts itself (see effectiveSpeedPerMs: the boost is for *other*
    // creatures within range) — so the boosted-and-slowed subject here is the Elf, sitting
    // adjacent to both the Angel (for the slow) and the Wolf (for the boost).
    val wolf =
      Creature(
        1,
        GridConfig.cellCenter(6, 6),
        Balance.WolfMaxHp,
        Balance.WolfMaxHp,
        Balance.WolfSpeedPerMs,
        UnitKind.Wolf
      )
    val elf =
      Creature(
        2,
        GridConfig.cellCenter(6, 5),
        Balance.ElfMaxHp,
        Balance.ElfMaxHp,
        Balance.ElfSpeedPerMs,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(wolf, elf), buildings = List(angel))
    val result = CombatEngine.tick(state, deltaMs = 10.0)
    val boostedAndSlowed = result.state.creatures.find(_.id == 2).get
    val plainElf =
      CombatEngine
        .tick(withResources().copy(creatures = List(elf)), deltaMs = 10.0)
        .state
        .creatures
        .head
    val actualDistance = distanceMoved(elf.pos, boostedAndSlowed.pos)
    val plainDistance = distanceMoved(elf.pos, plainElf.pos)
    assertEqualsDouble(
      actualDistance,
      plainDistance * Balance.WolfSpeedAuraMultiplier * (1.0 - Balance.AngelSlowFraction),
      1e-9
    )
  }

  test("a stasis field slows an adjacent enemy's movement by StasisSlowFraction") {
    val stasisField = Building(100, col = 5, row = 5, BuildingKind.StasisField, 0.0)
    val nearbyElf =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        Balance.ElfMaxHp,
        Balance.ElfMaxHp,
        Balance.ElfSpeedPerMs,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(nearbyElf), buildings = List(stasisField))
    val result = CombatEngine.tick(state, deltaMs = 10.0)
    val slowedElf = result.state.creatures.find(_.id == 1).get
    val aloneElf =
      CombatEngine
        .tick(withResources().copy(creatures = List(nearbyElf)), deltaMs = 10.0)
        .state
        .creatures
        .head
    val slowedDistance = distanceMoved(nearbyElf.pos, slowedElf.pos)
    val aloneDistance = distanceMoved(nearbyElf.pos, aloneElf.pos)
    assertEqualsDouble(slowedDistance, aloneDistance * (1.0 - Balance.StasisSlowFraction), 1e-9)
  }

  test("a stasis field's slow does not reach a creature on a non-adjacent cell") {
    val stasisField = Building(100, col = 5, row = 5, BuildingKind.StasisField, 0.0)
    val farElf =
      Creature(
        1,
        GridConfig.cellCenter(0, 0),
        Balance.ElfMaxHp,
        Balance.ElfMaxHp,
        Balance.ElfSpeedPerMs,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(farElf), buildings = List(stasisField))
    val result = CombatEngine.tick(state, deltaMs = 10.0)
    val unaffected = result.state.creatures.head
    val alone = CombatEngine
      .tick(withResources().copy(creatures = List(farElf)), deltaMs = 10.0)
      .state
      .creatures
      .head
    assertEquals(unaffected.pos, alone.pos)
  }

  test("a stasis field's slow and an angel's slow stack multiplicatively") {
    // Elf at (6,5) sits orthogonally adjacent to both the stasis field (5,5) and the
    // angel (7,5) — one cell to either side, on the same row.
    val stasisField = Building(100, col = 5, row = 5, BuildingKind.StasisField, 0.0)
    val angel = Building(101, col = 7, row = 5, BuildingKind.Angel, 0.0)
    val elf = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      Balance.ElfMaxHp,
      Balance.ElfMaxHp,
      Balance.ElfSpeedPerMs,
      UnitKind.Elf
    )
    val state = withResources().copy(creatures = List(elf), buildings = List(stasisField, angel))
    val result = CombatEngine.tick(state, deltaMs = 10.0)
    val actualDistance = distanceMoved(elf.pos, result.state.creatures.head.pos)
    val plainElf = CombatEngine
      .tick(withResources().copy(creatures = List(elf)), deltaMs = 10.0)
      .state
      .creatures
      .head
    val plainDistance = distanceMoved(elf.pos, plainElf.pos)
    assertEqualsDouble(
      actualDistance,
      plainDistance * (1.0 - Balance.AngelSlowFraction) * (1.0 - Balance.StasisSlowFraction),
      1e-9
    )
  }

  test("a passing gate damages an adjacent enemy at its own rate, not Forest's/Angel's") {
    val gate = Building(100, col = 5, row = 5, BuildingKind.PassingGate, 0.0)
    val adjacent =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 100.0,
        maxHp = 100.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val distant =
      Creature(
        2,
        GridConfig.cellCenter(0, 0),
        hp = 100.0,
        maxHp = 100.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(adjacent, distant), buildings = List(gate))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.state.creatures.map(c => c.id -> c).toMap
    assertEquals(byId(1).hp, adjacent.hp - Balance.PassingGateDamagePerSec)
    assertEquals(byId(2).hp, distant.hp)
  }

  test(
    "a passing gate harvests PassingGateHarvestFraction of the SPAWNING BUILDING's cost, " +
      "as Gold — even for a unit with no plunder ability of its own"
  ) {
    val gate = Building(100, col = 5, row = 5, BuildingKind.PassingGate, 0.0)
    // A Zombie specifically: CreatureSpecs.all(Zombie).plunder is empty (Zombie.md gives it
    // no plunder ability — its value is corrupting buildings instead), so this proves the
    // harvest is scored off Tomb's cost (CreatureSpecs.spawningBuilding(Zombie)), not the
    // dying unit's own plunder value.
    val dying = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = Balance.PassingGateDamagePerSec,
      maxHp = 100.0,
      speedPerMs = 0.0,
      UnitKind.Zombie
    )
    val state =
      MazeState.initial.copy(resources = Map.empty, creatures = List(dying), buildings = List(gate))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures, Nil)
    val tombCost = Balance.TombCostWood + Balance.TombCostShadow
    val expectedGold = Balance.PassingGateHarvestFraction * tombCost
    assertEqualsDouble(result.state.resources(Resource.Gold), expectedGold, 1e-9)
    assertEquals(result.state.buildings.head.flashMs, Balance.PassingGateFlashMs)
  }

  test("a passing gate does not harvest gold (or flash) for a death far from it") {
    val gate = Building(100, col = 0, row = 0, BuildingKind.PassingGate, 0.0)
    val watchtower = Building(101, col = 10, row = 10, BuildingKind.Watchtower, 0.0)
    val farDying =
      Creature(
        1,
        GridConfig.cellCenter(9, 10),
        hp = Balance.WatchtowerDamagePerSec,
        maxHp = 100.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val state = MazeState.initial.copy(
      resources = Map.empty,
      creatures = List(farDying),
      buildings = List(gate, watchtower)
    )
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures, Nil)
    // The Watchtower kill itself grants Balance.LoyalesKillGoldReward Gold (a Loi
    // mechanic, unrelated to Passing Gate) — this test is only about the FAR gate's own
    // contribution being exactly 0, not about total Gold staying at 0.
    assertEqualsDouble(
      result.state.resources.getOrElse(Resource.Gold, 0.0),
      Balance.LoyalesKillGoldReward,
      1e-9
    )
    assertEquals(result.state.buildings.find(_.kind == BuildingKind.PassingGate).get.flashMs, 0.0)
  }

  test("a passing gate's flash fades over time (deltaMs per tick) when no death is nearby") {
    val gate = Building(
      100,
      col = 5,
      row = 5,
      BuildingKind.PassingGate,
      0.0,
      flashMs = Balance.PassingGateFlashMs
    )
    val state = withResources().copy(buildings = List(gate))
    val result = CombatEngine.tick(state, deltaMs = 200.0)
    assertEqualsDouble(
      result.state.buildings.head.flashMs,
      Balance.PassingGateFlashMs - 200.0,
      1e-9
    )
  }

  test("a passing gate's flash never goes negative, even ticked past its full duration") {
    val gate = Building(100, col = 5, row = 5, BuildingKind.PassingGate, 0.0, flashMs = 50.0)
    val state = withResources().copy(buildings = List(gate))
    val result = CombatEngine.tick(state, deltaMs = 200.0)
    assertEquals(result.state.buildings.head.flashMs, 0.0)
  }

  // ── damage cadence (once per Balance.DamageTickIntervalMs, not smoothly continuous) ──

  test("a watchtower does not deal a second hit until a full DamageTickIntervalMs has elapsed") {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val target =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 100.0,
        maxHp = 100.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(target), buildings = List(watchtower))
    val afterFirstHit = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(afterFirstHit.state.creatures.head.hp, target.hp - Balance.WatchtowerDamagePerSec)
    val stillWithinSameSecond = CombatEngine.tick(afterFirstHit.state, deltaMs = 500.0)
    assertEquals(
      stillWithinSameSecond.state.creatures.head.hp,
      afterFirstHit.state.creatures.head.hp,
      "no damage should land again before a full second has passed since the last hit"
    )
  }

  test("a watchtower deals its second hit once the remaining time since the first elapses") {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val target =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 100.0,
        maxHp = 100.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(target), buildings = List(watchtower))
    val afterFirstHit = CombatEngine.tick(state, deltaMs = 1000.0)
    val partial = CombatEngine.tick(afterFirstHit.state, deltaMs = 500.0)
    val afterSecondHit = CombatEngine.tick(partial.state, deltaMs = 500.0)
    assertEquals(
      afterSecondHit.state.creatures.head.hp,
      target.hp - Balance.WatchtowerDamagePerSec * 2.0
    )
  }

  test("a forest aura follows the same once-per-second cadence as a watchtower") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val target =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 100.0,
        maxHp = 100.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(target), buildings = List(forest))
    val afterFirstHit = CombatEngine.tick(state, deltaMs = 1000.0)
    val stillWithinSameSecond = CombatEngine.tick(afterFirstHit.state, deltaMs = 900.0)
    assertEquals(
      stillWithinSameSecond.state.creatures.head.hp,
      afterFirstHit.state.creatures.head.hp
    )
  }

  test("a lump hit that overkills its target wastes the excess instead of carrying it anywhere") {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val fragile =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 1.0,
        maxHp = 1.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(fragile), buildings = List(watchtower))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures, Nil)
    assertEquals(result.deaths, List(Death(1, UnitKind.Elf, DeathCause.Watchtower)))
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
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 10.0,
        maxHp = 10.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(elf), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures.head.hp, elf.hp - Balance.AuraDamagePerSec)
  }

  test("a creature killed purely by forest aura is reported with cause Aura") {
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
    assertEquals(result.deaths, List(Death(1, UnitKind.Elf, DeathCause.Aura)))
  }

  test("a creature killed purely by a watchtower is reported with cause Watchtower") {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val creature = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = Balance.WatchtowerDamagePerSec,
      maxHp = Balance.WatchtowerDamagePerSec,
      speedPerMs = 0.0,
      UnitKind.Elf
    )
    val state = withResources().copy(creatures = List(creature), buildings = List(watchtower))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.deaths, List(Death(1, UnitKind.Elf, DeathCause.Watchtower)))
  }

  test(
    "a creature killed by both an aura and a watchtower in the same tick is reported as AuraAndWatchtower"
  ) {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val watchtower = Building(101, col = 7, row = 5, BuildingKind.Watchtower, 0.0)
    val creature = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = Balance.AuraDamagePerSec + Balance.WatchtowerDamagePerSec,
      maxHp = Balance.AuraDamagePerSec + Balance.WatchtowerDamagePerSec,
      speedPerMs = 0.0,
      UnitKind.Elf
    )
    val state =
      withResources().copy(creatures = List(creature), buildings = List(forest, watchtower))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.deaths, List(Death(1, UnitKind.Elf, DeathCause.AuraAndWatchtower)))
  }

  test("a creature that survives the tick is not reported as a death") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val creature =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 100.0,
        maxHp = 100.0,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(creature), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.deaths, Nil)
  }

  test("a paladin reaching the goal is reported as an arrival even though it plunders nothing") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val paladin = Creature(
      1,
      goalPos,
      Balance.PaladinMaxHp,
      Balance.PaladinMaxHp,
      speedPerMs = 0.0,
      UnitKind.Paladin
    )
    val state = withResources().copy(creatures = List(paladin))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.arrivals, List(UnitKind.Paladin))
    assertEquals(result.stolen, Map.empty[Resource, Double])
  }

  test("a goblin reaching the goal is reported as an arrival alongside its plunder") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val goblin =
      Creature(
        1,
        goalPos,
        Balance.GoblinMaxHp,
        Balance.GoblinMaxHp,
        speedPerMs = 0.0,
        UnitKind.Goblin
      )
    val state = withResources(wood = 100.0, fire = 100.0).copy(creatures = List(goblin))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.arrivals, List(UnitKind.Goblin))
  }

  test("a creature still walking is not reported as an arrival") {
    val creature =
      Creature(
        1,
        GridConfig.cellCenter(0, 0),
        Balance.ElfMaxHp,
        Balance.ElfMaxHp,
        speedPerMs = 0.0,
        UnitKind.Elf
      )
    val state = withResources().copy(creatures = List(creature))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.arrivals, Nil)
  }

  test("a tomb emits exactly one zombie-spawn signal per interval") {
    val tomb = Building(100, col = 5, row = 5, BuildingKind.Tomb, Balance.ZombieSpawnIntervalMs)
    val state = withResources().copy(buildings = List(tomb))
    val before = CombatEngine.tick(state, deltaMs = Balance.ZombieSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.ZombieSpawnIntervalMs)
    assertEquals(before.spawned.getOrElse(UnitKind.Zombie, 0), 0)
    assertEquals(at.spawned.getOrElse(UnitKind.Zombie, 0), 1)
  }

  test("a black castle emits exactly one vampire-spawn signal per interval") {
    val blackCastle =
      Building(100, col = 5, row = 5, BuildingKind.BlackCastle, Balance.VampireSpawnIntervalMs)
    val state = withResources().copy(buildings = List(blackCastle))
    val before = CombatEngine.tick(state, deltaMs = Balance.VampireSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.VampireSpawnIntervalMs)
    assertEquals(before.spawned.getOrElse(UnitKind.Vampire, 0), 0)
    assertEquals(at.spawned.getOrElse(UnitKind.Vampire, 0), 1)
  }

  test("tombs and black castles produce shadow over time") {
    val tomb = Building(100, col = 5, row = 5, BuildingKind.Tomb, Balance.ZombieSpawnIntervalMs)
    val state = withResources().copy(buildings = List(tomb))
    val result = CombatEngine.tick(state, deltaMs = 2000.0)
    assertEquals(result.state.resources(Resource.Shadow), Balance.ShadowPerSecPerTomb * 2.0)
  }

  test("a zombie corrupts an adjacent enemy building but not a distant one") {
    // A Cave, not a Grove — a Nature building would also heal itself (see
    // CombatEngine.healBuildingCorruption), which isn't what this test is exercising.
    val cave = Building(100, col = 5, row = 5, BuildingKind.Cave, 0.0)
    val adjacent = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      Balance.ZombieMaxHp,
      Balance.ZombieMaxHp,
      0.0,
      UnitKind.Zombie
    )
    val distant = Creature(
      2,
      GridConfig.cellCenter(0, 0),
      Balance.ZombieMaxHp,
      Balance.ZombieMaxHp,
      0.0,
      UnitKind.Zombie
    )
    val other = Building(101, col = 0, row = 5, BuildingKind.Labyrinth, 0.0)
    val state =
      withResources().copy(creatures = List(adjacent, distant), buildings = List(cave, other))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.state.buildings.map(b => b.id -> b).toMap
    assertEquals(byId(100).corruptionPercent, Balance.ZombieCorruptionPercentPerSec)
    assertEquals(byId(101).corruptionPercent, 0.0)
  }

  // ── Recherches Sombres: boosts the ATTACKER's own corruption speed ────────
  // (not either maze's victory-condition targets any more — see VictoryConditionsTest
  // and Balance.SombresCorruptionSpeedIncreaseByLevel's doc.)

  test("Recherches Sombres speeds up the attacker's own corrupting creature") {
    val cave = Building(100, col = 5, row = 5, BuildingKind.Cave, 0.0)
    val zombie = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      Balance.ZombieMaxHp,
      Balance.ZombieMaxHp,
      0.0,
      UnitKind.Zombie
    )
    val state = withResources().copy(creatures = List(zombie), buildings = List(cave))
    val sombresLevel = 2
    val result = CombatEngine.tick(
      state,
      deltaMs = 1000.0,
      attackerResearchLevels = Map(BuildingKind.LaboSombre -> sombresLevel)
    )
    val bonus = Balance.SombresCorruptionSpeedIncreaseByLevel(sombresLevel - 1)
    assertEqualsDouble(
      result.state.buildings.head.corruptionPercent,
      Balance.ZombieCorruptionPercentPerSec * (1.0 + bonus),
      1e-9
    )
  }

  test("without any Recherches Sombres research, corruption speed is the plain per-kind rate") {
    val cave = Building(100, col = 5, row = 5, BuildingKind.Cave, 0.0)
    val zombie = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      Balance.ZombieMaxHp,
      Balance.ZombieMaxHp,
      0.0,
      UnitKind.Zombie
    )
    val state = withResources().copy(creatures = List(zombie), buildings = List(cave))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(
      result.state.buildings.head.corruptionPercent,
      Balance.ZombieCorruptionPercentPerSec
    )
  }

  // Vampire's corruption rate leads Zombie's by MORE than a flat 2x (rock-paper-scissors
  // tuning pass: Mort's tier-2 unit already has higher HP/speed than Zombie — see
  // Balance.VampireCorruptionPercentPerSec's own doc — so its corruption edge should be
  // more than just "twice as fast" too, not merely match the HP/speed lead's magnitude).
  test("a vampire corrupts faster than twice a zombie's rate") {
    // Not a Grove/Forest/Jungle — see the note above.
    val cave = Building(100, col = 5, row = 5, BuildingKind.Cave, 0.0)
    val vampire = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      Balance.VampireMaxHp,
      Balance.VampireMaxHp,
      0.0,
      UnitKind.Vampire
    )
    val state = withResources().copy(creatures = List(vampire), buildings = List(cave))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(
      result.state.buildings.head.corruptionPercent,
      Balance.VampireCorruptionPercentPerSec
    )
    assert(
      Balance.VampireCorruptionPercentPerSec > Balance.ZombieCorruptionPercentPerSec * 2.0,
      s"expected Vampire's corruption rate to lead Zombie's by more than 2x, got " +
        s"${Balance.VampireCorruptionPercentPerSec} vs ${Balance.ZombieCorruptionPercentPerSec}"
    )
  }

  test("multiple corrupting creatures adjacent to the same building stack their corruption") {
    // Not a Grove/Forest/Jungle — see the note above.
    val cave = Building(100, col = 5, row = 5, BuildingKind.Cave, 0.0)
    val zombieA = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      Balance.ZombieMaxHp,
      Balance.ZombieMaxHp,
      0.0,
      UnitKind.Zombie
    )
    val zombieB = Creature(
      2,
      GridConfig.cellCenter(4, 5),
      Balance.ZombieMaxHp,
      Balance.ZombieMaxHp,
      0.0,
      UnitKind.Zombie
    )
    val state = withResources().copy(creatures = List(zombieA, zombieB), buildings = List(cave))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(
      result.state.buildings.head.corruptionPercent,
      Balance.ZombieCorruptionPercentPerSec * 2.0
    )
  }

  test("a building corrupted to 100% is destroyed and reported for refund") {
    val almostCorrupted = Building(
      100,
      col = 5,
      row = 5,
      BuildingKind.Grove,
      spawnCountdownMs = 0.0,
      corruptionPercent = Balance.CorruptionMaxPercent - Balance.ZombieCorruptionPercentPerSec
    )
    val zombie = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      Balance.ZombieMaxHp,
      Balance.ZombieMaxHp,
      0.0,
      UnitKind.Zombie
    )
    val state = withResources().copy(creatures = List(zombie), buildings = List(almostCorrupted))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.buildings, Nil)
    assertEquals(
      result.corrupted,
      List(Corrosion(100, BuildingKind.Grove, 5, 5, BuildingSpecs.all(BuildingKind.Grove).cost))
    )
  }

  test("corruption never exceeds 100%, even with excess corrupting exposure") {
    val grove = Building(100, col = 5, row = 5, BuildingKind.Grove, 0.0)
    val zombie = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      Balance.ZombieMaxHp,
      Balance.ZombieMaxHp,
      0.0,
      UnitKind.Zombie
    )
    val state = withResources().copy(creatures = List(zombie), buildings = List(grove))
    val result = CombatEngine.tick(
      state,
      deltaMs = Balance.CorruptionMaxPercent / Balance.ZombieCorruptionPercentPerSec * 1000.0 * 2
    )
    assertEquals(result.state.buildings, Nil)
    assertEquals(result.corrupted.size, 1)
  }

  test("a grove heals corruption on itself at GroveCorruptionHealPercentPerSec") {
    val grove = Building(100, col = 5, row = 5, BuildingKind.Grove, 0.0, corruptionPercent = 50.0)
    val state = withResources().copy(buildings = List(grove))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(
      result.state.buildings.head.corruptionPercent,
      50.0 - Balance.GroveCorruptionHealPercentPerSec
    )
  }

  test(
    "a grove heals corruption on a diagonally-adjacent building too, not just the 4 orthogonal neighbors"
  ) {
    val grove = Building(100, col = 5, row = 5, BuildingKind.Grove, 0.0)
    val diagonal = Building(101, col = 6, row = 6, BuildingKind.Cave, 0.0, corruptionPercent = 50.0)
    val state = withResources().copy(buildings = List(grove, diagonal))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.state.buildings.map(b => b.id -> b).toMap
    assertEquals(byId(101).corruptionPercent, 50.0 - Balance.GroveCorruptionHealPercentPerSec)
  }

  test("forest and jungle heal corruption at their own, higher rates than a grove") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, 0.0, corruptionPercent = 50.0)
    val jungle = Building(101, col = 0, row = 0, BuildingKind.Jungle, 0.0, corruptionPercent = 50.0)
    val state = withResources().copy(buildings = List(forest, jungle))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.state.buildings.map(b => b.id -> b).toMap
    assertEquals(byId(100).corruptionPercent, 50.0 - Balance.ForestCorruptionHealPercentPerSec)
    assertEquals(byId(101).corruptionPercent, 50.0 - Balance.JungleCorruptionHealPercentPerSec)
  }

  test("a grove's healing does not reach a building 2 cells away") {
    val grove = Building(100, col = 5, row = 5, BuildingKind.Grove, 0.0)
    val farAway = Building(101, col = 7, row = 5, BuildingKind.Cave, 0.0, corruptionPercent = 50.0)
    val state = withResources().copy(buildings = List(grove, farAway))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.buildings.find(_.id == 101).get.corruptionPercent, 50.0)
  }

  test("multiple nearby nature buildings stack their corruption healing") {
    val groveA = Building(100, col = 4, row = 5, BuildingKind.Grove, 0.0)
    val groveB = Building(101, col = 6, row = 5, BuildingKind.Grove, 0.0)
    val target = Building(102, col = 5, row = 5, BuildingKind.Cave, 0.0, corruptionPercent = 50.0)
    val state = withResources().copy(buildings = List(groveA, groveB, target))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(
      result.state.buildings.find(_.id == 102).get.corruptionPercent,
      50.0 - 2 * Balance.GroveCorruptionHealPercentPerSec
    )
  }

  test("corruption healing never goes below 0%") {
    // Below the heal amount a full-second tick would apply, so it floors at 0 rather
    // than going negative.
    val grove = Building(
      100,
      col = 5,
      row = 5,
      BuildingKind.Grove,
      0.0,
      corruptionPercent = Balance.GroveCorruptionHealPercentPerSec / 2.0
    )
    val state = withResources().copy(buildings = List(grove))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.buildings.head.corruptionPercent, 0.0)
  }

  test(
    "a nature building's own healing partially offsets an adjacent zombie's corruption in the same tick"
  ) {
    // Grove and zombie flank the Cave from opposite sides (west/east) — both adjacent to
    // it, neither on its own cell (corruption/healing only reach a building's neighbors,
    // never the cell the corruptor/healer itself occupies).
    val grove = Building(100, col = 5, row = 5, BuildingKind.Grove, 0.0)
    val cave = Building(101, col = 6, row = 5, BuildingKind.Cave, 0.0)
    val zombie = Creature(
      1,
      GridConfig.cellCenter(7, 5),
      Balance.ZombieMaxHp,
      Balance.ZombieMaxHp,
      0.0,
      UnitKind.Zombie
    )
    val state = withResources().copy(creatures = List(zombie), buildings = List(grove, cave))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    // The zombie corrupts the Cave (adjacent to it) by ZombieCorruptionPercentPerSec, then
    // the Grove heals it back down by GroveCorruptionHealPercentPerSec (also adjacent).
    val cavePercent = result.state.buildings.find(_.id == 101).get.corruptionPercent
    assertEquals(
      cavePercent,
      Balance.ZombieCorruptionPercentPerSec - Balance.GroveCorruptionHealPercentPerSec
    )
  }

  test("a death house emits exactly one necromancer-spawn signal per interval") {
    val deathHouse =
      Building(100, col = 5, row = 5, BuildingKind.DeathHouse, Balance.NecromancerSpawnIntervalMs)
    val state = withResources().copy(buildings = List(deathHouse))
    val before = CombatEngine.tick(state, deltaMs = Balance.NecromancerSpawnIntervalMs - 1.0)
    val at = CombatEngine.tick(state, deltaMs = Balance.NecromancerSpawnIntervalMs)
    assertEquals(before.spawned.getOrElse(UnitKind.Necromancer, 0), 0)
    assertEquals(at.spawned.getOrElse(UnitKind.Necromancer, 0), 1)
  }

  test("death houses produce shadow over time") {
    val deathHouse =
      Building(100, col = 5, row = 5, BuildingKind.DeathHouse, Balance.NecromancerSpawnIntervalMs)
    val state = withResources().copy(buildings = List(deathHouse))
    val result = CombatEngine.tick(state, deltaMs = 2000.0)
    assertEquals(result.state.resources(Resource.Shadow), Balance.ShadowPerSecPerDeathHouse * 2.0)
  }

  test("a soul corrupts an adjacent building at the same rate as a zombie") {
    // Not a Grove/Forest/Jungle — a Nature building would also heal itself (see
    // CombatEngine.healBuildingCorruption), which isn't what this test is exercising.
    val cave = Building(100, col = 5, row = 5, BuildingKind.Cave, 0.0)
    val soul = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      Balance.SoulMaxHp,
      Balance.SoulMaxHp,
      0.0,
      UnitKind.Soul
    )
    val state = withResources().copy(creatures = List(soul), buildings = List(cave))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.buildings.head.corruptionPercent, Balance.SoulCorruptionPercentPerSec)
    assertEquals(Balance.SoulCorruptionPercentPerSec, Balance.ZombieCorruptionPercentPerSec)
  }

  test("a necromancer invokes a soul every SoulSummonIntervalMs, appearing at its own position") {
    val necromancer = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      Balance.NecromancerMaxHp,
      Balance.NecromancerMaxHp,
      0.0,
      UnitKind.Necromancer,
      spawnCountdownMs = Balance.SoulSummonIntervalMs
    )
    val state = withResources().copy(creatures = List(necromancer))
    val before = CombatEngine.tick(state, deltaMs = Balance.SoulSummonIntervalMs - 1.0)
    assertEquals(before.state.creatures.count(_.kind == UnitKind.Soul), 0)
    val at = CombatEngine.tick(before.state, deltaMs = 1.0)
    val souls = at.state.creatures.filter(_.kind == UnitKind.Soul)
    assertEquals(souls.size, 1)
    assertEquals(GridConfig.cellOf(souls.head.pos), GridConfig.cellOf(necromancer.pos))
    assertEquals(souls.head.summonedBy, Some(1L))
    // The necromancer itself survives (it isn't consumed by summoning) and its own
    // countdown resets for the next Soul.
    assertEquals(at.state.creatures.count(_.kind == UnitKind.Necromancer), 1)
  }

  test(
    "a freshly spawned necromancer's first soul only appears after the full interval, not instantly"
  ) {
    val spec = CreatureSpecs.all(UnitKind.Necromancer)
    assertEquals(spec.spawns, Some((UnitKind.Soul, Balance.SoulSummonIntervalMs)))
  }

  test("a necromancer freezes for NecromancerSummonFreezeMs the instant it summons a soul") {
    val necromancer = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      Balance.NecromancerMaxHp,
      Balance.NecromancerMaxHp,
      0.0,
      UnitKind.Necromancer,
      spawnCountdownMs = Balance.SoulSummonIntervalMs
    )
    val state = withResources().copy(creatures = List(necromancer))
    val result = CombatEngine.tick(state, deltaMs = Balance.SoulSummonIntervalMs)
    val updated = result.state.creatures.find(_.kind == UnitKind.Necromancer).get
    assertEquals(updated.frozenMs, Balance.NecromancerSummonFreezeMs)
  }

  test(
    "a frozen necromancer does not move forward, even with nonzero speed, and its freeze ticks down"
  ) {
    val necromancer = Creature(
      1,
      GridConfig.cellCenter(0, 0),
      Balance.NecromancerMaxHp,
      Balance.NecromancerMaxHp,
      Balance.NecromancerSpeedPerMs,
      UnitKind.Necromancer,
      // Comfortably far from its own next summon, so advanceCreatureSummons doesn't also
      // fire this tick and reset frozenMs back to the full duration.
      spawnCountdownMs = Balance.SoulSummonIntervalMs,
      frozenMs = 1000.0
    )
    val state = withResources().copy(creatures = List(necromancer))
    val result = CombatEngine.tick(state, deltaMs = 400.0)
    val updated = result.state.creatures.find(_.kind == UnitKind.Necromancer).get
    assertEquals(updated.pos, necromancer.pos)
    assertEquals(updated.frozenMs, 600.0)
  }

  test("a necromancer resumes normal movement once its freeze reaches zero") {
    val necromancer = Creature(
      1,
      GridConfig.cellCenter(0, 0),
      Balance.NecromancerMaxHp,
      Balance.NecromancerMaxHp,
      Balance.NecromancerSpeedPerMs,
      UnitKind.Necromancer,
      spawnCountdownMs = Balance.SoulSummonIntervalMs,
      frozenMs = 100.0
    )
    val state = withResources().copy(creatures = List(necromancer))
    // First tick burns off the remaining freeze (100ms out of 500ms elapsed) but still
    // doesn't move — the whole tick is treated as frozen-or-not, not partially frozen.
    val afterFreeze = CombatEngine.tick(state, deltaMs = 100.0)
    val stillFrozen = afterFreeze.state.creatures.find(_.kind == UnitKind.Necromancer).get
    assertEquals(stillFrozen.frozenMs, 0.0)
    assertEquals(stillFrozen.pos, necromancer.pos)
    val afterMove = CombatEngine.tick(afterFreeze.state, deltaMs = 400.0)
    val moved = afterMove.state.creatures.find(_.kind == UnitKind.Necromancer).get
    assertNotEquals(moved.pos, necromancer.pos)
  }

  // ── Tree self-cloning (Stonehenge/Arbre Anime) ─────────────────────────
  // Unlike the Necromancer/Soul (child appears on the summoner's own position), a Tree's
  // clone appears one cell further along the summoner's own path — see CreatureSpec.
  // spawnAtNextCell / CombatEngine.advanceCreatureSummons's nextPathCellCenter.

  test(
    "a tree clones itself onto the next path cell, not its own position, when its clone timer elapses"
  ) {
    val tree = Creature(
      1,
      GridConfig.cellCenter(0, 0),
      Balance.TreeMaxHp,
      Balance.TreeMaxHp,
      speedPerMs = 0.0,
      UnitKind.Tree,
      spawnCountdownMs = Balance.TreeCloneIntervalMs
    )
    val state = withResources().copy(creatures = List(tree), nextId = 2L)
    val result = CombatEngine.tick(state, deltaMs = Balance.TreeCloneIntervalMs)
    assertEquals(result.state.creatures.size, 2)
    val clone = result.state.creatures.find(_.id != tree.id).get
    val expectedNextCell = Pathfinding.shortestPath((0, 0), GridConfig.goalCell, Set.empty).get(1)
    assertEquals(clone.pos, GridConfig.cellCenter(expectedNextCell._1, expectedNextCell._2))
    assertNotEquals(clone.pos, tree.pos)
  }

  test(
    "the original tree's clone is TreeCloneSizeStepFraction smaller (size and HP), same kind and fresh countdown"
  ) {
    val tree = Creature(
      1,
      GridConfig.cellCenter(0, 0),
      Balance.TreeMaxHp,
      Balance.TreeMaxHp,
      speedPerMs = 0.0,
      UnitKind.Tree,
      spawnCountdownMs = Balance.TreeCloneIntervalMs
      // sizeFraction defaults to 1.0 — this is the original, full-size tree.
    )
    val state = withResources().copy(creatures = List(tree), nextId = 2L)
    val result = CombatEngine.tick(state, deltaMs = Balance.TreeCloneIntervalMs)
    val clone = result.state.creatures.find(_.id != tree.id).get
    val expectedFraction = 1.0 - Balance.TreeCloneSizeStepFraction
    assertEquals(clone.kind, UnitKind.Tree)
    assertEqualsDouble(clone.sizeFraction, expectedFraction, 1e-9)
    assertEqualsDouble(clone.hp, Balance.TreeMaxHp * expectedFraction, 1e-9)
    assertEqualsDouble(clone.maxHp, Balance.TreeMaxHp * expectedFraction, 1e-9)
    assertEquals(clone.spawnCountdownMs, Balance.TreeCloneIntervalMs)
  }

  test(
    "a clone can clone itself too, shrinking another TreeCloneSizeStepFraction from ITS OWN size"
  ) {
    val cloneTree = Creature(
      1,
      GridConfig.cellCenter(0, 0),
      hp = 80.0,
      maxHp = 80.0,
      speedPerMs = 0.0,
      UnitKind.Tree,
      spawnCountdownMs = Balance.TreeCloneIntervalMs,
      summonedBy = Some(99L),
      sizeFraction = 0.8
    )
    val state = withResources().copy(creatures = List(cloneTree), nextId = 2L)
    val result = CombatEngine.tick(state, deltaMs = Balance.TreeCloneIntervalMs)
    assertEquals(result.state.creatures.size, 2)
    val grandchild = result.state.creatures.find(_.id != cloneTree.id).get
    assertEqualsDouble(grandchild.sizeFraction, 0.6, 1e-9)
    assertEqualsDouble(grandchild.maxHp, Balance.TreeMaxHp * 0.6, 1e-9)
  }

  test(
    "cloning never shrinks below TreeMinCloneSizeFraction — a clone at the floor makes another at the floor"
  ) {
    val tinyTree = Creature(
      1,
      GridConfig.cellCenter(0, 0),
      hp = 20.0,
      maxHp = 20.0,
      speedPerMs = 0.0,
      UnitKind.Tree,
      spawnCountdownMs = Balance.TreeCloneIntervalMs,
      summonedBy = Some(99L),
      sizeFraction = Balance.TreeMinCloneSizeFraction
    )
    val state = withResources().copy(creatures = List(tinyTree), nextId = 2L)
    val result = CombatEngine.tick(state, deltaMs = Balance.TreeCloneIntervalMs)
    val clone = result.state.creatures.find(_.id != tinyTree.id).get
    assertEqualsDouble(clone.sizeFraction, Balance.TreeMinCloneSizeFraction, 1e-9)
  }

  test("a tree freezes for TreeCloneFreezeMs the instant it clones itself") {
    val tree = Creature(
      1,
      GridConfig.cellCenter(0, 0),
      Balance.TreeMaxHp,
      Balance.TreeMaxHp,
      // 0.0 speed, same as the equivalent Necromancer freeze test — isolates the freeze
      // flag from the fact that movement (moveCreatures) runs *before* this tick's own
      // freeze gets set (advanceCreatureSummons), so a nonzero-speed tree still takes one
      // step on the very tick it clones.
      speedPerMs = 0.0,
      UnitKind.Tree,
      spawnCountdownMs = Balance.TreeCloneIntervalMs
    )
    val state = withResources().copy(creatures = List(tree), nextId = 2L)
    val result = CombatEngine.tick(state, deltaMs = Balance.TreeCloneIntervalMs)
    val parent = result.state.creatures.find(_.id == tree.id).get
    assertEquals(parent.frozenMs, Balance.TreeCloneFreezeMs)
  }

  test("a frozen tree does not advance, even with nonzero speed, until its freeze reaches zero") {
    val tree = Creature(
      1,
      GridConfig.cellCenter(0, 0),
      Balance.TreeMaxHp,
      Balance.TreeMaxHp,
      speedPerMs = Balance.TreeSpeedPerMs,
      UnitKind.Tree,
      spawnCountdownMs = Balance.TreeCloneIntervalMs,
      frozenMs = Balance.TreeCloneFreezeMs
    )
    val state = withResources().copy(creatures = List(tree))
    val result = CombatEngine.tick(state, deltaMs = 100.0)
    val stillFrozen = result.state.creatures.find(_.id == tree.id).get
    assertEquals(stillFrozen.pos, tree.pos)
    assertEquals(stillFrozen.frozenMs, Balance.TreeCloneFreezeMs - 100.0)
  }

  test(
    "a soul's corruption heals its summoning necromancer by SoulHealPerSecPerBuilding, capped at max HP"
  ) {
    val grove = Building(100, col = 5, row = 5, BuildingKind.Grove, 0.0)
    val necromancer = Creature(
      1,
      GridConfig.cellCenter(0, 0),
      hp = Balance.NecromancerMaxHp - 5.0,
      maxHp = Balance.NecromancerMaxHp,
      speedPerMs = 0.0,
      UnitKind.Necromancer
    )
    val soul = Creature(
      2,
      GridConfig.cellCenter(6, 5),
      Balance.SoulMaxHp,
      Balance.SoulMaxHp,
      speedPerMs = 0.0,
      UnitKind.Soul,
      summonedBy = Some(1L)
    )
    val state = withResources().copy(creatures = List(necromancer, soul), buildings = List(grove))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val healedNecromancer = result.state.creatures.find(_.id == 1).get
    assertEquals(healedNecromancer.hp, necromancer.hp + Balance.SoulHealPerSecPerBuilding)
  }

  test("healing never exceeds the necromancer's max HP") {
    val grove = Building(100, col = 5, row = 5, BuildingKind.Grove, 0.0)
    val necromancer = Creature(
      1,
      GridConfig.cellCenter(0, 0),
      hp = Balance.NecromancerMaxHp,
      maxHp = Balance.NecromancerMaxHp,
      speedPerMs = 0.0,
      UnitKind.Necromancer
    )
    val soul = Creature(
      2,
      GridConfig.cellCenter(6, 5),
      Balance.SoulMaxHp,
      Balance.SoulMaxHp,
      speedPerMs = 0.0,
      UnitKind.Soul,
      summonedBy = Some(1L)
    )
    val state = withResources().copy(creatures = List(necromancer, soul), buildings = List(grove))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val healedNecromancer = result.state.creatures.find(_.id == 1).get
    assertEquals(healedNecromancer.hp, Balance.NecromancerMaxHp)
  }

  test("a soul adjacent to two buildings at once heals its necromancer twice as much") {
    val groveA = Building(100, col = 5, row = 5, BuildingKind.Grove, 0.0)
    val groveB = Building(101, col = 7, row = 5, BuildingKind.Grove, 0.0)
    val necromancer = Creature(
      1,
      GridConfig.cellCenter(0, 0),
      hp = Balance.NecromancerMaxHp - 10.0,
      maxHp = Balance.NecromancerMaxHp,
      speedPerMs = 0.0,
      UnitKind.Necromancer
    )
    val soul = Creature(
      2,
      GridConfig.cellCenter(6, 5),
      Balance.SoulMaxHp,
      Balance.SoulMaxHp,
      speedPerMs = 0.0,
      UnitKind.Soul,
      summonedBy = Some(1L)
    )
    val state =
      withResources().copy(creatures = List(necromancer, soul), buildings = List(groveA, groveB))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val healedNecromancer = result.state.creatures.find(_.id == 1).get
    assertEquals(healedNecromancer.hp, necromancer.hp + Balance.SoulHealPerSecPerBuilding * 2.0)
  }

  test("a soul with no living summoning necromancer heals no one") {
    val grove = Building(100, col = 5, row = 5, BuildingKind.Grove, 0.0)
    val soul = Creature(
      2,
      GridConfig.cellCenter(6, 5),
      Balance.SoulMaxHp,
      Balance.SoulMaxHp,
      speedPerMs = 0.0,
      UnitKind.Soul,
      summonedBy = Some(999L) // no creature with this id exists
    )
    val state = withResources().copy(creatures = List(soul), buildings = List(grove))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    // Should not throw, and no creature should have gained hp from nowhere.
    assertEquals(result.state.creatures.map(c => c.id -> c.hp).toMap, Map(2L -> Balance.SoulMaxHp))
  }

  test("a vampire takes half damage from a forest aura") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val vampire =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 100.0,
        maxHp = 100.0,
        speedPerMs = 0.0,
        UnitKind.Vampire
      )
    val state = withResources().copy(creatures = List(vampire), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(
      result.state.creatures.head.hp,
      vampire.hp - Balance.AuraDamagePerSec * (1.0 - Balance.VampireDamageReductionFraction)
    )
  }

  test("a vampire is not protected by an adjacent paladin's shield, unlike other creatures") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val sharedPos = GridConfig.cellCenter(6, 5)
    val vampire =
      Creature(1, sharedPos, hp = 100.0, maxHp = 100.0, speedPerMs = 0.0, UnitKind.Vampire)
    val paladin = Creature(
      2,
      sharedPos,
      Balance.PaladinMaxHp,
      Balance.PaladinMaxHp,
      speedPerMs = 0.0,
      UnitKind.Paladin
    )
    val state = withResources().copy(creatures = List(vampire, paladin), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.state.creatures.map(c => c.id -> c).toMap
    // No Paladin reduction applied at all — only Vampire's own 50% flat reduction, same as
    // if no Paladin were present (contrast with the Elf in the shield test above, which
    // takes zero damage since the Paladin fully cancels Forest's aura for it).
    assertEquals(
      byId(1).hp,
      vampire.hp - Balance.AuraDamagePerSec * (1.0 - Balance.VampireDamageReductionFraction)
    )
  }

  // Camp de Guerre's Orc (see Balance.OrcMaxHp's doc): the first hit that would kill it
  // instead clamps it to 1 HP and marks hasCheatedDeath, exactly once — a second lethal
  // hit kills it normally. Uses a Forest aura (same fixture as the Vampire tests above)
  // rather than a Watchtower, since Watchtower's own range change is a separate tuning
  // pass — this mechanic is unconditional, not tied to any one damage source.
  test("an orc that would die instead survives its first lethal hit at 1 HP") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val orc = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = Balance.AuraDamagePerSec,
      maxHp = Balance.OrcMaxHp,
      speedPerMs = 0.0,
      UnitKind.Orc
    )
    val state = withResources().copy(creatures = List(orc), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures.map(c => (c.hp, c.hasCheatedDeath)), List((1.0, true)))
  }

  test("an orc's second lethal hit, after already cheating death once, kills it for real") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val orc = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = Balance.AuraDamagePerSec,
      maxHp = Balance.OrcMaxHp,
      speedPerMs = 0.0,
      UnitKind.Orc,
      hasCheatedDeath = true
    )
    val state = withResources().copy(creatures = List(orc), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures, Nil)
    assertEquals(result.deaths.map(_.kind), List(UnitKind.Orc))
  }

  test("an orc that survives a hit with HP to spare doesn't trigger the cheat-death clamp") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val orc = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = Balance.OrcMaxHp,
      maxHp = Balance.OrcMaxHp,
      speedPerMs = 0.0,
      UnitKind.Orc
    )
    val state = withResources().copy(creatures = List(orc), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(
      result.state.creatures.map(c => (c.hp, c.hasCheatedDeath)),
      List((Balance.OrcMaxHp - Balance.AuraDamagePerSec, false))
    )
  }

  test("a soldier paired with another nearby soldier takes reduced aura damage (Rang serre)") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val soldierPos = GridConfig.cellCenter(6, 5)
    val allyPos = GridConfig.cellCenter(6, 6) // orthogonally adjacent to soldierPos
    val soldier = Creature(
      1,
      soldierPos,
      Balance.SoldierMaxHp,
      Balance.SoldierMaxHp,
      speedPerMs = 0.0,
      UnitKind.Soldier
    )
    val ally = Creature(
      2,
      allyPos,
      Balance.SoldierMaxHp,
      Balance.SoldierMaxHp,
      speedPerMs = 0.0,
      UnitKind.Soldier
    )
    val state = withResources().copy(creatures = List(soldier, ally), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    val byId = result.state.creatures.map(c => c.id -> c).toMap
    assertEquals(
      byId(1).hp,
      soldier.hp - math.max(
        0.0,
        Balance.AuraDamagePerSec - Balance.SoldierCloseRanksDamageReductionPerSec
      )
    )
  }

  test("a lone soldier with no nearby ally takes full aura damage") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val soldier =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        Balance.SoldierMaxHp,
        Balance.SoldierMaxHp,
        speedPerMs = 0.0,
        UnitKind.Soldier
      )
    val state = withResources().copy(creatures = List(soldier), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures.head.hp, soldier.hp - Balance.AuraDamagePerSec)
  }

  test("a zombie reaching the goal is reported as an arrival, but plunders nothing") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val zombie = Creature(
      1,
      goalPos,
      Balance.ZombieMaxHp,
      Balance.ZombieMaxHp,
      speedPerMs = 0.0,
      UnitKind.Zombie
    )
    val state = withResources(wood = 5.0, fire = 5.0).copy(creatures = List(zombie))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.arrivals, List(UnitKind.Zombie))
    assertEquals(result.stolen, Map.empty[Resource, Double])
  }

  test("labs emit no unit and produce crystal over time") {
    val labo = Building(100, col = 5, row = 5, BuildingKind.LaboNaturel, 0.0)
    val state = withResources().copy(buildings = List(labo))
    val result = CombatEngine.tick(state, deltaMs = 2000.0)
    assertEquals(result.spawned, Map.empty[UnitKind, Int])
    assertEquals(
      result.state.resources(Resource.Crystal),
      Balance.CrystalPerSecPerLaboNaturel * 2.0
    )
  }

  // ── Recherches loyales: boosts this maze's OWN Loi buildings' attack SPEED ──
  // (not damage-per-hit, and not any other faction's damage dealers — see Balance.
  // LoyalesAttackSpeedIncreaseByLevel's doc.)

  test(
    "Recherches loyales shortens a watchtower's attack interval, without changing its per-hit damage"
  ) {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val target = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = 1000.0,
      maxHp = 1000.0,
      speedPerMs = 0.0,
      UnitKind.Elf
    )
    val loyalesLevel = 1
    val state = withResources()
      .copy(
        creatures = List(target),
        buildings = List(watchtower),
        researchLevels = Map(BuildingKind.LaboDeLaLoi -> loyalesLevel)
      )
    // First hit still lands after the building's plain initial cooldown (Balance.
    // DamageTickIntervalMs, unaffected by research), at the flat per-hit rate.
    val afterFirstHit = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(afterFirstHit.state.creatures.head.hp, target.hp - Balance.WatchtowerDamagePerSec)
    // The *next* interval is the boosted one: shorter than the plain 1000ms, so 950ms
    // (which would NOT be enough under the unboosted interval — see the sibling forest
    // test below) is already enough for the second hit to land, at the same flat rate.
    val bonus = Balance.LoyalesAttackSpeedIncreaseByLevel(loyalesLevel - 1)
    val effectiveIntervalMs = Balance.DamageTickIntervalMs / (1.0 + bonus)
    assert(effectiveIntervalMs < 950.0, "test assumes the boosted interval is under 950ms")
    val afterSecondHit = CombatEngine.tick(afterFirstHit.state, deltaMs = 950.0)
    assertEquals(
      afterSecondHit.state.creatures.head.hp,
      target.hp - Balance.WatchtowerDamagePerSec * 2.0
    )
  }

  test("Recherches loyales also speeds up an Angel's aura (Loi, not just Watchtower)") {
    val angel = Building(100, col = 5, row = 5, BuildingKind.Angel, 0.0)
    val target = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = 1000.0,
      maxHp = 1000.0,
      speedPerMs = 0.0,
      UnitKind.Elf
    )
    val loyalesLevel = 1
    val state = withResources()
      .copy(
        creatures = List(target),
        buildings = List(angel),
        researchLevels = Map(BuildingKind.LaboDeLaLoi -> loyalesLevel)
      )
    val afterFirstHit = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(afterFirstHit.state.creatures.head.hp, target.hp - Balance.AngelDamagePerSec)
    val afterSecondHit = CombatEngine.tick(afterFirstHit.state, deltaMs = 950.0)
    assertEquals(
      afterSecondHit.state.creatures.head.hp,
      target.hp - Balance.AngelDamagePerSec * 2.0
    )
  }

  test("Recherches loyales does NOT speed up a forest's aura — Nature, not a Loi building") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val target = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = 1000.0,
      maxHp = 1000.0,
      speedPerMs = 0.0,
      UnitKind.Elf
    )
    val state = withResources()
      .copy(
        creatures = List(target),
        buildings = List(forest),
        researchLevels = Map(BuildingKind.LaboDeLaLoi -> 5)
      )
    val afterFirstHit = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(afterFirstHit.state.creatures.head.hp, target.hp - Balance.AuraDamagePerSec)
    // Still the plain (unboosted) 1000ms interval, so 950ms isn't enough for a second hit.
    val stillWithinSameInterval = CombatEngine.tick(afterFirstHit.state, deltaMs = 950.0)
    assertEquals(
      stillWithinSameInterval.state.creatures.head.hp,
      afterFirstHit.state.creatures.head.hp
    )
  }

  // ── Loi buildings earn Gold for their own kills (project owner's explicit request,
  // alongside the new Gold resource — not a vault mechanic) ──────────────────

  test("a watchtower kill earns its own maze 1 Gold") {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val target = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = 1.0,
      maxHp = 1.0,
      speedPerMs = 0.0,
      UnitKind.Elf
    )
    val state = withResources().copy(creatures = List(target), buildings = List(watchtower))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures, Nil) // confirms the kill actually happened
    assertEquals(
      result.state.resources.getOrElse(Resource.Gold, 0.0),
      Balance.LoyalesKillGoldReward
    )
  }

  test("an Angel kill earns its own maze 1 Gold too, not just Watchtower") {
    val angel = Building(100, col = 5, row = 5, BuildingKind.Angel, 0.0)
    val target = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = 1.0,
      maxHp = 1.0,
      speedPerMs = 0.0,
      UnitKind.Elf
    )
    val state = withResources().copy(creatures = List(target), buildings = List(angel))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures, Nil)
    assertEquals(
      result.state.resources.getOrElse(Resource.Gold, 0.0),
      Balance.LoyalesKillGoldReward
    )
  }

  test("a watchtower kill on a Minotaur (a large unit) earns double Gold") {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val target =
      Creature(
        1,
        GridConfig.cellCenter(6, 5),
        hp = 1.0,
        maxHp = Balance.MinotaurMaxHp,
        speedPerMs = 0.0,
        UnitKind.Minotaur
      )
    val state = withResources().copy(creatures = List(target), buildings = List(watchtower))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures, Nil)
    assertEquals(
      result.state.resources.getOrElse(Resource.Gold, 0.0),
      Balance.LoyalesLargeKillGoldReward
    )
  }

  test("a kill from a Nature/Mort aura (Forest/PassingGate) earns no Gold — Loi buildings only") {
    val forest = Building(100, col = 5, row = 5, BuildingKind.Forest, Balance.ElfSpawnIntervalMs)
    val target = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = 1.0,
      maxHp = 1.0,
      speedPerMs = 0.0,
      UnitKind.Elf
    )
    val state = withResources().copy(creatures = List(target), buildings = List(forest))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures, Nil)
    assertEquals(result.state.resources.getOrElse(Resource.Gold, 0.0), 0.0)
  }

  test("a creature that survives the tick earns no Gold, even standing next to a Loi building") {
    val watchtower = Building(100, col = 5, row = 5, BuildingKind.Watchtower, 0.0)
    val target = Creature(
      1,
      GridConfig.cellCenter(6, 5),
      hp = 1000.0,
      maxHp = 1000.0,
      speedPerMs = 0.0,
      UnitKind.Elf
    )
    val state = withResources().copy(creatures = List(target), buildings = List(watchtower))
    val result = CombatEngine.tick(state, deltaMs = 1000.0)
    assertEquals(result.state.creatures.size, 1) // still alive
    assertEquals(result.state.resources.getOrElse(Resource.Gold, 0.0), 0.0)
  }

  // ── Regression: Gold must never crash CombatEngine.tick (engendreBoost's own doc) ──

  test(
    "ticking a maze that produces only Wood never crashes, and Gold's own production stays exactly 0"
  ) {
    val grove = Building(1, 5, 5, BuildingKind.Grove, Balance.ElfSpawnIntervalMs)
    val state = withResources().copy(buildings = List(grove))
    val result = CombatEngine.tick(state, deltaMs = 1000.0) // must not throw
    assertEquals(CombatEngine.productionPerSec(result.state, Resource.Gold), 0.0)
  }

  // ── Recherches chaotiques: reduces THIS maze's OWN Chaos buildings' unit-spawn time ──
  // (not plunder any more — see Balance.ChaotiquesSpawnTimeReductionByLevel's doc.)

  test("Recherches chaotiques shortens a Cave's Goblin spawn interval") {
    val cave = Building(100, col = 5, row = 5, BuildingKind.Cave, spawnCountdownMs = 0.0)
    val chaotiquesLevel = 1
    val state = withResources().copy(
      buildings = List(cave),
      researchLevels = Map(BuildingKind.LaboDuChaos -> chaotiquesLevel)
    )
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.spawned.getOrElse(UnitKind.Goblin, 0), 1)
    val reduction = Balance.ChaotiquesSpawnTimeReductionByLevel(chaotiquesLevel - 1)
    assertEqualsDouble(
      result.state.buildings.head.spawnCountdownMs,
      Balance.GoblinSpawnIntervalMs * (1.0 - reduction) - 1.0,
      1e-9
    )
  }

  test(
    "Recherches chaotiques does NOT shorten a Tomb's Zombie spawn interval — Mort, not a Chaos building"
  ) {
    val tomb = Building(100, col = 5, row = 5, BuildingKind.Tomb, spawnCountdownMs = 0.0)
    val state = withResources().copy(
      buildings = List(tomb),
      researchLevels = Map(BuildingKind.LaboDuChaos -> 5)
    )
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEquals(result.spawned.getOrElse(UnitKind.Zombie, 0), 1)
    assertEqualsDouble(
      result.state.buildings.head.spawnCountdownMs,
      Balance.ZombieSpawnIntervalMs - 1.0,
      1e-9
    )
  }

  test("without any Recherches chaotiques research, Cave's spawn interval is the plain base one") {
    val cave = Building(100, col = 5, row = 5, BuildingKind.Cave, spawnCountdownMs = 0.0)
    val state = withResources().copy(buildings = List(cave))
    val result = CombatEngine.tick(state, deltaMs = 1.0)
    assertEqualsDouble(
      result.state.buildings.head.spawnCountdownMs,
      Balance.GoblinSpawnIntervalMs - 1.0,
      1e-9
    )
  }

  // ── Recherches chaotiques no longer touches plunder at all ────────────────

  test("plunder is always the plain per-kind amount now, regardless of attackerResearchLevels") {
    val goalPos = GridConfig.cellCenter(GridConfig.goalCell._1, GridConfig.goalCell._2)
    val wolf =
      Creature(1, goalPos, Balance.WolfMaxHp, Balance.WolfMaxHp, speedPerMs = 0.0, UnitKind.Wolf)
    val goblin = Creature(
      2,
      goalPos,
      Balance.GoblinMaxHp,
      Balance.GoblinMaxHp,
      speedPerMs = 0.0,
      UnitKind.Goblin
    )
    val state = MazeState.initial.copy(
      resources = Map(Resource.Gold -> 100.0),
      creatures = List(wolf, goblin)
    )
    val result =
      CombatEngine.tick(
        state,
        deltaMs = 1.0,
        attackerResearchLevels = Map(BuildingKind.LaboDuChaos -> 5)
      )
    assertEquals(
      result.stolen.getOrElse(Resource.Gold, 0.0),
      2 * Balance.PlunderPerUnit
    ) // only Goblin plunders (Gold)
  }
