package towerdefense.domain.ai

import towerdefense.domain.*
import towerdefense.domain.combat.*
import towerdefense.domain.economy.*
import towerdefense.domain.grid.*

// LinearStrategy must reproduce AiController's exact historical behavior (see
// AiControllerTest, which this mirrors) now that build decisions go through the
// AiStrategy trait instead of a single hardcoded object.
class AiStrategyTest extends munit.FunSuite:

  private val noOpponent = MazeState.initial

  private def withResources(
      wood: Double = 0.0,
      fire: Double = 0.0,
      light: Double = 0.0,
      shadow: Double = 0.0,
      crystal: Double = 0.0
  ): MazeState =
    MazeState.initial.copy(
      resources = Map(
        Resource.Wood -> wood,
        Resource.Fire -> fire,
        Resource.Light -> light,
        Resource.Shadow -> shadow,
        Resource.Crystal -> crystal
      )
    )

  private def count(state: MazeState, kind: BuildingKind): Int =
    state.buildings.count(_.kind == kind)

  // Diagnosed via code review: LinearStrategy.buildOrder is a hand-written literal list,
  // not derived from BuildingKind's own cases — WarCamp was added to the enum without a
  // matching buildOrder entry, silently making it unbuildable by this strategy despite the
  // file's own doc claiming "Both sides can build any directly-buildable BuildingKind".
  // This test makes that claim an enforced invariant instead of an aspirational comment.
  test("buildOrder contains every buildableDirectly BuildingKind, exactly once") {
    val expected = BuildingKind.values.filter(_.buildableDirectly).toSet
    assertEquals(LinearStrategy.buildOrder.toSet, expected)
    assertEquals(
      LinearStrategy.buildOrder.size,
      expected.size,
      "buildOrder must not list any kind twice"
    )
  }

  test("does nothing without enough resources for either building") {
    val state = withResources(wood = 0.0, fire = 0.0)
    assertEquals(LinearStrategy.maybeBuild(state, noOpponent), state)
  }

  test("builds a grove when it can only afford a grove") {
    // light = 0.0 also rules out a Watchtower (wood10+light5, tied with Grove on wood).
    val state = withResources(wood = Balance.GroveCostWood, fire = 0.0, light = 0.0)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Grove), 1)
    assertEquals(count(result, BuildingKind.Cave), 0)
  }

  test("builds a cave when it can only afford a cave") {
    val state = withResources(wood = Balance.CaveCostWood, fire = Balance.CaveCostFire)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Cave), 1)
  }

  test("builds a labyrinthe when it can only afford a labyrinthe") {
    val state = withResources(wood = Balance.LabyrintheCostWood, fire = Balance.LabyrintheCostFire)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Labyrinth), 1)
    assertEquals(count(result, BuildingKind.Forest), 0)
    assertEquals(count(result, BuildingKind.Cave), 0)
  }

  // A recent rebalance dropped GroveCostWood to equal TombCostWood exactly (5 each), and
  // Grove costs nothing else — so Grove's cost is now a strict subset of Tomb's. Any
  // resource level that affords a Tomb necessarily affords a Grove too, and Grove sits
  // earlier in buildOrder, so LinearStrategy (fixed priority, never reconsiders) always
  // picks Grove over Tomb now. "Only afford a tomb" is no longer constructible.
  test(
    "Grove wins the tie over Tomb (subsumed cost, earlier in buildOrder) even when shadow only helps Tomb"
  ) {
    val state = withResources(wood = Balance.TombCostWood, shadow = Balance.TombCostShadow)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Grove), 1)
    assertEquals(count(result, BuildingKind.Tomb), 0)
  }

  test("builds a black castle when it can only afford one, over cheaper buildings tied on wood") {
    val state =
      withResources(wood = Balance.BlackCastleCostWood, shadow = Balance.BlackCastleCostShadow)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.BlackCastle), 1)
    assertEquals(count(result, BuildingKind.Labyrinth), 0)
  }

  test("builds a fondamental lab when it can only afford one (zero wood cost, tried last)") {
    val state = withResources(wood = 0.0, crystal = Balance.LaboFondamentalCostCrystal)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.LaboFondamental), 1)
  }

  test("builds an eglise over any cheaper building once it can afford one") {
    val state = withResources(wood = Balance.EgliseCostWood, light = Balance.EgliseCostLight)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Church), 1)
    assertEquals(count(result, BuildingKind.Grove), 0)
    assertEquals(count(result, BuildingKind.Cave), 0)
  }

  test("builds a watchtower over a grove when it can afford either (tied wood cost)") {
    val state = withResources(
      wood = Balance.WatchtowerCostWood,
      fire = 0.0,
      light = Balance.WatchtowerCostLight
    )
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    assertEquals(count(result, BuildingKind.Watchtower), 1)
    assertEquals(count(result, BuildingKind.Grove), 0)
  }

  test("skips the spawn and goal cells when picking a spot") {
    val state = withResources(wood = Balance.GroveCostWood, light = 0.0)
    val result = LinearStrategy.maybeBuild(state, noOpponent)
    val built = result.buildings.find(_.kind == BuildingKind.Grove).get
    assertNotEquals(Pos(built.col, built.row), GridConfig.spawnCell)
    assertNotEquals(Pos(built.col, built.row), GridConfig.goalCell)
  }

  test("opponent's state does not influence Linear's decision") {
    val state = withResources(wood = Balance.GroveCostWood, fire = 0.0)
    val busyOpponent = MazeState.initial.copy(
      buildings = List(Building(1, 5, 5, BuildingKind.Grove, 0.0))
    )
    assertEquals(
      LinearStrategy.maybeBuild(state, noOpponent),
      LinearStrategy.maybeBuild(state, busyOpponent)
    )
  }

  test("maybeUpgrade does nothing when there is no upgradeable building") {
    val state = withResources(wood = 1_000.0)
    assertEquals(LinearStrategy.maybeUpgrade(state, noOpponent), state)
  }

  test("maybeUpgrade does nothing when the only Grove can't afford the Forest tier") {
    val poor = withResources(wood = Balance.GroveCostWood)
    val withGrove = Placement.tryPlaceBuilding(poor, BuildingKind.Grove, 5, 5).toOption.get
    assertEquals(LinearStrategy.maybeUpgrade(withGrove, noOpponent), withGrove)
  }

  test("maybeUpgrade upgrades the first affordable Grove into a Forest") {
    val rich = withResources(wood = 1_000.0)
    val withGrove = Placement.tryPlaceBuilding(rich, BuildingKind.Grove, 5, 5).toOption.get
    val result = LinearStrategy.maybeUpgrade(withGrove, noOpponent)
    assertEquals(count(result, BuildingKind.Forest), 1)
    assertEquals(count(result, BuildingKind.Grove), 0)
  }

  // Re-measured via `sim/runMain towerdefense.sim.tournament 2` (25 entries, replacing the
  // previous 6-base-strategy mix — see AiStrategy.ladder's own doc) after the 2-round
  // multi-agent tuning pass gave every faction a real, individually-tuned rush strategy
  // worth playing against. 25 strategies/5 Swiss rounds/2 matches per pairing:
  // maze-science@1s anchors the bottom (its lab economy is the slowest to compound), and
  // maze-science@3s tops the ladder, narrowly ahead of maze-nature@1s/maze-nature@2s/
  // maze-science@5s. Ranked by Elo rating, weakest to strongest, ascending.
  test("the ladder is ordered weakest to strongest by measured Elo rating") {
    assertEquals(
      AiStrategy.ladder.map(_.name),
      Seq(
        "maze-science@1s",
        "maze-corruption@8s",
        "maze-corruption@5s",
        "maze-science@8s",
        "maze-nature@5s",
        "maze-corruption@2s",
        "maze-corruption@1s",
        "maze-nature@8s",
        "maze-law@1s",
        "maze-law@8s",
        "maze-law@5s",
        "maze-nature@3s",
        "maze-law@3s",
        "maze-science@2s",
        "maze-law@2s",
        "maze-plunder@8s",
        "maze-plunder@5s",
        "maze-corruption@3s",
        "maze-plunder@3s",
        "maze-plunder@2s",
        "maze-plunder@1s",
        "maze-nature@2s",
        "maze-nature@1s",
        "maze-science@5s",
        "maze-science@3s"
      )
    )
  }

  test("all contains both the catalog's plain names and the ladder's speed-suffixed names") {
    assertEquals(
      AiStrategy.all,
      (AiStrategy.catalog ++ AiStrategy.ladder).map(s => s.name -> s).toMap
    )
    assertEquals(AiStrategy.all("linear"), LinearStrategy)
    assert(AiStrategy.all.contains("maze-science@1s"))
  }

  test("every ladder entry's buildCooldownMs matches its name's speed suffix") {
    AiStrategy.ladder.foreach { strategy =>
      val periodSec = strategy.name.split("@")(1).stripSuffix("s").toInt
      assertEqualsDouble(strategy.buildCooldownMs, periodSec * 1_000.0, 1e-9, strategy.name)
    }
  }

  // buildCooldownMs (see AiStrategy's doc): a trait-level default so every existing
  // strategy keeps today's exact pacing with zero code changes, overridable per-instance
  // via RateLimited for anything that wants to tune "how fast" independently of "what".
  test(
    "buildCooldownMs defaults to Balance.AiBuildCooldownMs for any strategy that doesn't override it"
  ) {
    assertEquals(LinearStrategy.buildCooldownMs, Balance.AiBuildCooldownMs)
    assertEquals(
      ComposedStrategy(NoLayoutPreference, GrovePriority).buildCooldownMs,
      Balance.AiBuildCooldownMs
    )
  }

  test(
    "RateLimited overrides buildCooldownMs but delegates every decision to the wrapped strategy"
  ) {
    val fast = RateLimited(LinearStrategy, buildCooldownMs = 500.0)
    assertEquals(fast.buildCooldownMs, 500.0)
    val state = withResources(wood = Balance.GroveCostWood)
    assertEquals(fast.maybeBuild(state, noOpponent), LinearStrategy.maybeBuild(state, noOpponent))
  }

  test("RateLimited forwards maybeUpgrade/maybeResearch/maybeDestroy to the wrapped strategy too") {
    val rich = withResources(wood = 1_000.0)
    val withGrove = Placement.tryPlaceBuilding(rich, BuildingKind.Grove, 5, 5).toOption.get
    val fast = RateLimited(LinearStrategy, buildCooldownMs = 500.0)
    assertEquals(
      fast.maybeUpgrade(withGrove, noOpponent),
      LinearStrategy.maybeUpgrade(withGrove, noOpponent)
    )
    assertEquals(
      fast.maybeResearch(withGrove, noOpponent),
      LinearStrategy.maybeResearch(withGrove, noOpponent)
    )
    assertEquals(
      fast.maybeDestroy(withGrove, noOpponent),
      LinearStrategy.maybeDestroy(withGrove, noOpponent)
    )
  }

  // reseed (see AiStrategy's own doc): a trait-level no-op default so any strategy with no
  // internal randomness (LinearStrategy, every plain SpendingPolicy/LayoutPolicy) is
  // unaffected — only ComposedStrategy's own tie-break Random actually needs reseeding.
  test("the default AiStrategy.reseed is a no-op identity for a strategy with no randomness") {
    assertEquals(LinearStrategy.reseed(42L), LinearStrategy)
  }

  test("RateLimited.reseed delegates to the wrapped strategy, keeping its own buildCooldownMs") {
    val fast =
      RateLimited(ComposedStrategy(NoLayoutPreference, GrovePriority), buildCooldownMs = 500.0)
    val reseeded = fast.reseed(42L)
    assertEquals(reseeded.buildCooldownMs, 500.0)
    reseeded match
      case RateLimited(inner: ComposedStrategy, _) =>
        assertEquals(
          inner.random.nextInt(),
          new scala.util.Random(42L).nextInt(),
          "the seed must reach the wrapped ComposedStrategy's own Random"
        )
      case other => fail(s"expected a RateLimited(ComposedStrategy, ...), got $other")
  }
