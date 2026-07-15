package towerdefense.domain

// Each component of CompositeStrategy is tested in isolation via a weight vector that
// zeroes out the other two, then combined to prove more than one signal can drive the
// final pick together. See the plan's "Component scores" section for what each measures.
class CompositeStrategyTest extends munit.FunSuite:

  private val noOpponent = MazeState.initial

  private def withResources(wood: Double = 0.0, fire: Double = 0.0, light: Double = 0.0): MazeState =
    MazeState.initial.copy(
      resources = Map(Resource.Wood -> wood, Resource.Fire -> fire, Resource.Light -> light)
    )

  private def building(id: Long, col: Int, row: Int, kind: BuildingKind): Building =
    Building(id, col, row, kind, spawnCountdownMs = 0.0)

  private def count(state: MazeState, kind: BuildingKind): Int = state.buildings.count(_.kind == kind)

  test("resource-only prefers the building that leaves the largest affordability margin") {
    // Flush enough to afford a Cave (5 wood / 10 fire) or a Forest (10 wood), but a
    // Forest at this wood level spends the same fraction of a much smaller pool, so a
    // pure resource-margin strategy should prefer the Cave here.
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    val strategy = CompositeStrategy(Weights(resource = 1.0, counter = 0.0, maze = 0.0))
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Cave), 1)
    assertEquals(count(result, BuildingKind.Forest), 0)
  }

  test("counter-only mirrors the opponent's dominant faction") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 100.0)
    val chaosHeavyOpponent = MazeState.initial.copy(
      buildings = List(building(1, 5, 5, BuildingKind.Cave), building(2, 6, 6, BuildingKind.Cave))
    )
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 1.0, maze = 0.0))
    val result = strategy.maybeBuild(state, chaosHeavyOpponent)
    assert(
      count(result, BuildingKind.Cave) == 1 || count(result, BuildingKind.Labyrinth) == 1,
      "expected a Chaos building"
    )
    assertEquals(count(result, BuildingKind.Forest), 0)
    assertEquals(count(result, BuildingKind.Church), 0)
  }

  test("counter-only ignores Watchtower/Loi investment too, same as Eglise") {
    val state = withResources(wood = 100.0, fire = 100.0, light = 100.0)
    val loiHeavyOpponent = MazeState.initial.copy(
      buildings = List(
        building(1, 5, 5, BuildingKind.Watchtower),
        building(2, 6, 6, BuildingKind.Watchtower),
        building(3, 7, 7, BuildingKind.Watchtower),
        building(4, 2, 2, BuildingKind.Forest)
      )
    )
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 1.0, maze = 0.0))
    val result = strategy.maybeBuild(state, loiHeavyOpponent)
    assertEquals(
      count(result, BuildingKind.Watchtower),
      0,
      "must not mirror Watchtower even though it's the opponent's largest count"
    )
    assertEquals(
      count(result, BuildingKind.Forest),
      1,
      "Nature (1) leads over Chaos (0), so Forest should be favored"
    )
  }

  test("resource-only can pick a watchtower over a forest when light is abundant") {
    // Forest's only margin is on wood (0.9). Watchtower averages a wood margin (0.9) with
    // an even better light margin (0.95, since light is flush here), edging Forest out.
    val state = withResources(wood = 100.0, fire = 0.0, light = 100.0)
    val strategy = CompositeStrategy(Weights(resource = 1.0, counter = 0.0, maze = 0.0))
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Watchtower), 1)
    assertEquals(count(result, BuildingKind.Forest), 0)
  }

  test("counter-only ignores Eglise/Loi investment since it never feeds a victory condition") {
    // Opponent's largest single count is Eglises, but Eglise doesn't count toward either
    // VictoryConditions target (forests or resourcesPlundered) — mirroring it would trap
    // counter-only in a permanent stalemate against an opponent that spams Eglises. It
    // must compare Nature vs Chaos investment only, ignoring Loi entirely.
    val state = withResources(wood = 100.0, fire = 100.0, light = 100.0)
    val loiHeavyOpponent = MazeState.initial.copy(
      buildings = List(
        building(1, 5, 5, BuildingKind.Church),
        building(2, 6, 6, BuildingKind.Church),
        building(3, 7, 7, BuildingKind.Church),
        building(4, 2, 2, BuildingKind.Forest)
      )
    )
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 1.0, maze = 0.0))
    val result = strategy.maybeBuild(state, loiHeavyOpponent)
    assertEquals(
      count(result, BuildingKind.Church),
      0,
      "must not mirror Eglise even though it's the opponent's largest count"
    )
    assertEquals(
      count(result, BuildingKind.Forest),
      1,
      "Nature (1) leads over Chaos (0), so Forest should be favored"
    )
  }

  test("maze-only picks the cell that maximizes the resulting enemy path length") {
    val state = withResources(wood = 100.0, fire = 0.0, light = 0.0)
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Forest), 1)
    val built = result.buildings.find(_.kind == BuildingKind.Forest).get
    val chosenLength = Pathfinding
      .shortestPath(GridConfig.spawnCell, GridConfig.goalCell, Set((built.col, built.row)))
      .map(_.length)
      .getOrElse(0)
    val bestPossible = GridConfig.allCells
      .filterNot(c => Set(GridConfig.spawnCell, GridConfig.goalCell).contains(c))
      .flatMap(c => Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, Set(c)).map(_.length))
      .max
    assertEquals(chosenLength, bestPossible)
  }

  test("dangerScore combines path length with aura-damage exposure from existing forests") {
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val noForests = MazeState.initial
    val isolatedPath =
      Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, noForests.buildingCells + ((6, 6))).get
    assertEquals(
      strategy.dangerScore(noForests, (6, 6), isForestCandidate = false),
      isolatedPath.length.toDouble,
      "with no forests anywhere, danger score is plain path length"
    )

    val withForest = MazeState.initial.copy(buildings = List(building(1, 6, 5, BuildingKind.Forest)))
    val exposedPath =
      Pathfinding.shortestPath(GridConfig.spawnCell, GridConfig.goalCell, withForest.buildingCells + ((6, 6))).get
    val adjacentCount = exposedPath.count(c => Pathfinding.neighbors(c).contains((6, 5)))
    assertEquals(
      strategy.dangerScore(withForest, (6, 6), isForestCandidate = false),
      exposedPath.length.toDouble + Balance.AuraDamagePerSec * adjacentCount,
      "each path cell adjacent to an existing forest adds one AuraDamagePerSec hit"
    )
  }

  test(
    "dangerScore sums damage from every adjacent forest a path cell borders, not just whether any exist"
  ) {
    // CombatEngine.applyDamageSources sums damagePerHit from EVERY forest adjacent to an
    // enemy, so a corridor flanked by forests on both sides deals double the damage per
    // second of a single-walled one. dangerScore must reflect that, or the AI has no
    // reason to prefer flanking corridors over one-sided walls despite them killing
    // roughly twice as fast.
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val onePath = List((5, 5))
    val oneFlank = Set((5, 4))
    val twoFlanks = Set((5, 4), (5, 6))
    val oneFlankScore = strategy.pathDangerScore(onePath, oneFlank)
    val twoFlankScore = strategy.pathDangerScore(onePath, twoFlanks)
    assertEquals(oneFlankScore, 1.0 + Balance.AuraDamagePerSec)
    assertEquals(twoFlankScore, 1.0 + 2 * Balance.AuraDamagePerSec)
  }

  test(
    "maze-only picks the candidate maximizing length + forest exposure together, not just the longest path"
  ) {
    val state = withResources(wood = 100.0, fire = 0.0, light = 0.0).copy(
      buildings = List(building(1, 6, 5, BuildingKind.Forest)),
      nextId = 2L
    )
    val strategy = CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
    val result = strategy.maybeBuild(state, MazeState.initial)
    val built = result.buildings.filter(_.kind == BuildingKind.Forest).filterNot(_.id == 1).head
    val bestPossibleScore = GridConfig.allCells
      .filterNot(Set(GridConfig.spawnCell, GridConfig.goalCell, (6, 5)).contains)
      .map(c => strategy.dangerScore(state, c, isForestCandidate = true))
      .max
    assertEquals(strategy.dangerScore(state, (built.col, built.row), isForestCandidate = true), bestPossibleScore)
  }

  test("a combined weight vector can pick a placement only explainable by more than one signal") {
    // Resource-only alone picks a Cave here (best affordability margin, see the
    // resource-only test above). Adding maze weight flips the pick to Forest: only a
    // Forest candidate counts toward its own aura-adjacency bonus in dangerScore, so once
    // maze contributes at all, Forest's combined score overtakes Cave's despite Cave's
    // better margin — a result neither signal alone would produce.
    val state = withResources(wood = 100.0, fire = 100.0, light = 0.0)
    val strategy = CompositeStrategy(Weights(resource = 1.0, counter = 0.0, maze = 1.0))
    val result = strategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Forest), 1)
    assertEquals(count(result, BuildingKind.Cave), 0)
  }

  test("does nothing without enough resources for any building, like LinearStrategy") {
    val state = withResources(wood = 0.0, fire = 0.0, light = 0.0)
    val strategy = CompositeStrategy(Weights(resource = 1.0, counter = 1.0, maze = 1.0))
    assertEquals(strategy.maybeBuild(state, noOpponent), state)
  }
