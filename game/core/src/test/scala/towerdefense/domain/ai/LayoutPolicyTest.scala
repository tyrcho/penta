package towerdefense.domain.ai

import towerdefense.domain.*
import towerdefense.domain.combat.*
import towerdefense.domain.economy.*
import towerdefense.domain.grid.*

// LayoutPolicy is the "which cell" half of an AiStrategy — the counterpart to
// SpendingPolicy's "which building kind". FreeformLayout's dangerScore/pathDangerScore
// are moved verbatim from CompositeStrategy; these tests are the same regression suite
// that used to live in CompositeStrategyTest.
class LayoutPolicyTest extends munit.FunSuite:

  private def withResources(
      wood: Double = 0.0,
      fire: Double = 0.0,
      light: Double = 0.0
  ): MazeState =
    MazeState.initial.copy(
      resources = Map(Resource.Wood -> wood, Resource.Fire -> fire, Resource.Light -> light)
    )

  private def building(id: Long, col: Int, row: Int, kind: BuildingKind): Building =
    Building(id, col, row, kind, spawnCountdownMs = 0.0)

  // ── NoLayoutPreference ─────────────────────────────────────────────────

  test("NoLayoutPreference scores every cell and kind identically") {
    assertEquals(NoLayoutPreference.score(MazeState.initial, BuildingKind.Grove, Pos(3, 3)), 0.0)
    assertEquals(NoLayoutPreference.score(MazeState.initial, BuildingKind.Cave, Pos(7, 7)), 0.0)
  }

  // ── FreeformLayout ─────────────────────────────────────────────────────

  test("dangerScore combines path length with aura-damage exposure from existing forests") {
    val noForests = MazeState.initial
    val isolatedPath =
      Pathfinding
        .shortestPath(
          GridConfig.spawnCell,
          GridConfig.goalCell,
          noForests.buildingCells + Pos(6, 6)
        )
        .get
    assertEquals(
      FreeformLayout.dangerScore(noForests, Pos(6, 6), isAuraCandidate = false),
      isolatedPath.length.toDouble,
      "with no forests anywhere, danger score is plain path length"
    )

    val withForest =
      MazeState.initial.copy(buildings = List(building(1, 6, 5, BuildingKind.Forest)))
    val exposedPath =
      Pathfinding
        .shortestPath(
          GridConfig.spawnCell,
          GridConfig.goalCell,
          withForest.buildingCells + Pos(6, 6)
        )
        .get
    val adjacentCount = exposedPath.count(c => Pathfinding.neighbors(c).contains(Pos(6, 5)))
    assertEquals(
      FreeformLayout.dangerScore(withForest, Pos(6, 6), isAuraCandidate = false),
      exposedPath.length.toDouble + Balance.AuraDamagePerSec * adjacentCount,
      "each path cell adjacent to an existing forest adds one AuraDamagePerSec hit"
    )
  }

  test(
    "dangerScore sums damage from every adjacent forest a path cell borders, not just whether any exist"
  ) {
    val onePath = List(Pos(5, 5))
    val oneFlank = Set(Pos(5, 4))
    val twoFlanks = Set(Pos(5, 4), Pos(5, 6))
    val oneFlankScore = FreeformLayout.pathDangerScore(onePath, oneFlank)
    val twoFlankScore = FreeformLayout.pathDangerScore(onePath, twoFlanks)
    assertEquals(oneFlankScore, 1.0 + Balance.AuraDamagePerSec)
    assertEquals(twoFlankScore, 1.0 + 2 * Balance.AuraDamagePerSec)
  }

  test("dangerScore combines path length with ranged damage exposure from an existing watchtower") {
    val withTower =
      MazeState.initial.copy(buildings = List(building(1, 6, 5, BuildingKind.Watchtower)))
    val exposedPath =
      Pathfinding
        .shortestPath(
          GridConfig.spawnCell,
          GridConfig.goalCell,
          withTower.buildingCells + Pos(6, 6)
        )
        .get
    val inRangeCount = exposedPath.count(c =>
      CombatEngine.chebyshevDistance(c, Pos(6, 5)) <= Balance.WatchtowerRangeCells
    )
    assertEquals(
      FreeformLayout.dangerScore(withTower, Pos(6, 6), isAuraCandidate = false),
      exposedPath.length.toDouble + Balance.WatchtowerDamagePerSec * inRangeCount,
      "each path cell within WatchtowerRangeCells of an existing watchtower adds one WatchtowerDamagePerSec hit"
    )
  }

  test(
    "dangerScore credits a new Watchtower candidate for its own range only when scored as ranged"
  ) {
    val state = MazeState.initial
    val path = Pathfinding
      .shortestPath(GridConfig.spawnCell, GridConfig.goalCell, state.buildingCells + Pos(6, 6))
      .get
    val selfRangeCount =
      path.count(c => CombatEngine.chebyshevDistance(c, Pos(6, 6)) <= Balance.WatchtowerRangeCells)
    assertEquals(
      FreeformLayout
        .dangerScore(state, Pos(6, 6), isAuraCandidate = false, isRangedCandidate = true),
      path.length.toDouble + Balance.WatchtowerDamagePerSec * selfRangeCount,
      "a candidate scored isRangedCandidate=true credits its own range, since it auras once built (same idea as isAuraCandidate for Grove)"
    )
    assertEquals(
      FreeformLayout
        .dangerScore(state, Pos(6, 6), isAuraCandidate = false, isRangedCandidate = false),
      path.length.toDouble,
      "without isRangedCandidate, a Watchtower candidate doesn't credit itself for its own range"
    )
  }

  test("pathDangerScore sums watchtower damage for every tower a path cell is within range of") {
    val onePath = List(Pos(5, 5))
    val oneTower = Set(Pos(5, 4))
    val twoTowers = Set(Pos(5, 4), Pos(6, 6))
    val oneTowerScore = FreeformLayout.pathDangerScore(onePath, Set.empty, oneTower)
    val twoTowerScore = FreeformLayout.pathDangerScore(onePath, Set.empty, twoTowers)
    assertEquals(oneTowerScore, 1.0 + Balance.WatchtowerDamagePerSec)
    assertEquals(twoTowerScore, 1.0 + 2 * Balance.WatchtowerDamagePerSec)
  }

  test("pathDangerScore combines aura and watchtower damage together when both are present") {
    val onePath = List(Pos(5, 5))
    val forest = Set(Pos(5, 4))
    val tower = Set(Pos(6, 6))
    val score = FreeformLayout.pathDangerScore(onePath, forest, tower)
    assertEquals(score, 1.0 + Balance.AuraDamagePerSec + Balance.WatchtowerDamagePerSec)
  }

  test(
    "score(kind, cell) credits Grove/Forest/Jungle/Watchtower as their own future danger source"
  ) {
    val state = withResources(wood = 100.0, fire = 0.0, light = 0.0)
    val cell = GridConfig.allCells
      .filterNot(Set(GridConfig.spawnCell, GridConfig.goalCell).contains)
      .maxBy(c => FreeformLayout.dangerScore(state, c, isAuraCandidate = false))
    val groveScore = FreeformLayout.score(state, BuildingKind.Grove, cell)
    val caveScore = FreeformLayout.score(state, BuildingKind.Cave, cell)
    assert(groveScore > caveScore, "Grove will aura once upgraded, Cave never will")
  }

  test(
    "score(kind, cell) prefers Watchtower over Grove at the same cell, ranged damage outscoring future aura"
  ) {
    val state = withResources(wood = 100.0, fire = 0.0, light = 100.0)
    val cell = GridConfig.allCells
      .filterNot(Set(GridConfig.spawnCell, GridConfig.goalCell).contains)
      .maxBy(c => FreeformLayout.dangerScore(state, c, isAuraCandidate = false))
    assert(
      FreeformLayout.score(state, BuildingKind.Watchtower, cell) > FreeformLayout.score(
        state,
        BuildingKind.Grove,
        cell
      )
    )
  }

  // ── TemplateLayout ─────────────────────────────────────────────────────

  test("TemplateLayout scores the earliest remaining template cell highest") {
    val layout = TemplateLayout(MazeTemplate.comb)
    val target = MazeTemplate.comb(GridConfig.cols, GridConfig.rows)
    val first = target.head
    val second = target(1)
    assert(
      layout.score(MazeState.initial, BuildingKind.Grove, first) >
        layout.score(MazeState.initial, BuildingKind.Grove, second)
    )
  }

  test("TemplateLayout scores a cell outside the template as negative infinity") {
    val layout = TemplateLayout(MazeTemplate.comb)
    val outside = GridConfig.allCells
      .find(c => !MazeTemplate.comb(GridConfig.cols, GridConfig.rows).contains(c))
      .get
    assertEquals(
      layout.score(MazeState.initial, BuildingKind.Grove, outside),
      Double.NegativeInfinity
    )
  }

  test(
    "TemplateLayout scores an already-built template cell as negative infinity, so it's never picked again"
  ) {
    val layout = TemplateLayout(MazeTemplate.comb)
    val cell = MazeTemplate.comb(GridConfig.cols, GridConfig.rows).head
    val built = Placement
      .tryPlaceBuilding(withResources(wood = 100.0), BuildingKind.Grove, cell.col, cell.row)
      .toOption
      .get
    assertEquals(layout.score(built, BuildingKind.Grove, cell), Double.NegativeInfinity)
  }

  test("TemplateLayout ignores which kind is being scored") {
    val layout = TemplateLayout(MazeTemplate.comb)
    val cell = MazeTemplate.comb(GridConfig.cols, GridConfig.rows).head
    assertEquals(
      layout.score(MazeState.initial, BuildingKind.Grove, cell),
      layout.score(MazeState.initial, BuildingKind.Cave, cell)
    )
  }

  // ── ForcedOpeningLayout ────────────────────────────────────────────────
  // Forces a fixed sequence of kinds for a maze's first few buildings — gives an AI
  // strategy explicit, auditable control over how its starting Gold gets spent, instead
  // of leaving the opening to emerge from the ordinary candidate-scoring competition (see
  // its own doc for the real bugs that motivated this: PlunderSpending once spent its
  // entire starting Gold on a zero-return DragonsLair turn 1, ScienceSpending incidentally
  // built a 13-Forest wall chasing Wood income).

  test("ForcedOpeningLayout scores the opening's kind at position 0, before any buildings exist") {
    val layout = ForcedOpeningLayout(Seq(BuildingKind.Cave, BuildingKind.WarCamp), FreeformLayout)
    assertEquals(
      layout.score(MazeState.initial, BuildingKind.Cave, Pos(5, 5)),
      FreeformLayout.score(MazeState.initial, BuildingKind.Cave, Pos(5, 5))
    )
  }

  test("ForcedOpeningLayout vetoes every other kind while an opening position is still pending") {
    val layout = ForcedOpeningLayout(Seq(BuildingKind.Cave, BuildingKind.WarCamp), FreeformLayout)
    assertEquals(
      layout.score(MazeState.initial, BuildingKind.Grove, Pos(5, 5)),
      Double.NegativeInfinity
    )
    assertEquals(
      layout.score(MazeState.initial, BuildingKind.WarCamp, Pos(5, 5)),
      Double.NegativeInfinity
    )
  }

  test("ForcedOpeningLayout advances to the next opening kind once the previous one exists") {
    val layout = ForcedOpeningLayout(Seq(BuildingKind.Cave, BuildingKind.WarCamp), FreeformLayout)
    val oneCave = MazeState.initial.copy(buildings = List(building(1, 0, 0, BuildingKind.Cave)))
    assertEquals(
      layout.score(oneCave, BuildingKind.WarCamp, Pos(5, 5)),
      FreeformLayout.score(oneCave, BuildingKind.WarCamp, Pos(5, 5))
    )
    assertEquals(layout.score(oneCave, BuildingKind.Cave, Pos(5, 5)), Double.NegativeInfinity)
  }

  test("ForcedOpeningLayout defers entirely to inner once the opening is complete") {
    val layout = ForcedOpeningLayout(Seq(BuildingKind.Cave), FreeformLayout)
    val pastOpening = MazeState.initial.copy(
      buildings = List(building(1, 0, 0, BuildingKind.Cave), building(2, 1, 1, BuildingKind.Grove))
    )
    assertEquals(
      layout.score(pastOpening, BuildingKind.Watchtower, Pos(5, 5)),
      FreeformLayout.score(pastOpening, BuildingKind.Watchtower, Pos(5, 5))
    )
    assertEquals(
      layout.score(pastOpening, BuildingKind.Grove, Pos(5, 5)),
      FreeformLayout.score(pastOpening, BuildingKind.Grove, Pos(5, 5)),
      "no leftover veto once the opening is done, even for the opening's own kind"
    )
  }

  test("ForcedOpeningLayout with an empty opening defers to inner immediately") {
    val layout = ForcedOpeningLayout(Seq.empty, FreeformLayout)
    assertEquals(
      layout.score(MazeState.initial, BuildingKind.Cave, Pos(5, 5)),
      FreeformLayout.score(MazeState.initial, BuildingKind.Cave, Pos(5, 5))
    )
  }

  // ── HealClusterLayout ──────────────────────────────────────────────────
  // Rewards a healer-kind candidate (Grove/Forest/Jungle — see
  // CombatEngine.healBuildingCorruption) for landing next to more of the maze's own
  // existing buildings, since a healer heals every building within Chebyshev distance 1
  // of it and multiple nearby healers stack — added per the project owner's explicit
  // request to make "position nature buildings in alternance with others" a real,
  // testable positioning lever rather than emergent behavior.

  test("HealClusterLayout adds a per-neighbor bonus on top of inner's score for a healer kind") {
    val layout =
      HealClusterLayout(Set(BuildingKind.Grove), bonusPerNeighbor = 5.0, NoLayoutPreference)
    val noNeighbors = MazeState.initial
    val oneNeighbor = MazeState.initial.copy(buildings = List(building(1, 5, 4, BuildingKind.Cave)))
    val twoNeighbors =
      MazeState.initial.copy(buildings =
        List(building(1, 5, 4, BuildingKind.Cave), building(2, 4, 5, BuildingKind.Watchtower))
      )
    assertEquals(layout.score(noNeighbors, BuildingKind.Grove, Pos(5, 5)), 0.0)
    assertEquals(layout.score(oneNeighbor, BuildingKind.Grove, Pos(5, 5)), 5.0)
    assertEquals(layout.score(twoNeighbors, BuildingKind.Grove, Pos(5, 5)), 10.0)
  }

  test("HealClusterLayout only counts buildings within Chebyshev distance 1, not farther ones") {
    val layout =
      HealClusterLayout(Set(BuildingKind.Grove), bonusPerNeighbor = 5.0, NoLayoutPreference)
    val farAway = MazeState.initial.copy(buildings = List(building(1, 9, 9, BuildingKind.Cave)))
    assertEquals(layout.score(farAway, BuildingKind.Grove, Pos(5, 5)), 0.0)
  }

  test("HealClusterLayout leaves non-healer kinds entirely to inner, no bonus") {
    val layout = HealClusterLayout(Set(BuildingKind.Grove), bonusPerNeighbor = 5.0, FreeformLayout)
    val state = MazeState.initial.copy(buildings = List(building(1, 5, 4, BuildingKind.Cave)))
    assertEquals(
      layout.score(state, BuildingKind.Cave, Pos(5, 5)),
      FreeformLayout.score(state, BuildingKind.Cave, Pos(5, 5))
    )
  }

  test("HealClusterLayout never overrides a veto (-Infinity) from inner with a bonus") {
    val layout = HealClusterLayout(
      Set(BuildingKind.Grove),
      bonusPerNeighbor = 5.0,
      TemplateLayout(MazeTemplate.comb)
    )
    val outside = GridConfig.allCells
      .find(c => !MazeTemplate.comb(GridConfig.cols, GridConfig.rows).contains(c))
      .get
    assertEquals(
      layout.score(MazeState.initial, BuildingKind.Grove, outside),
      Double.NegativeInfinity
    )
  }

  // ── CountCapLayout ─────────────────────────────────────────────────────
  // Vetoes (-Infinity) placing MORE of `kind` once the maze already has `cap` of it — a
  // hard, unconditional veto, unlike a SpendingPolicy penalty (however large): diagnosed
  // via transcript that even a -1_000 SpendingPolicy penalty on a second LaboFondamental
  // couldn't reliably win once Law's OTHER candidates (Barracks/Watchtower/Church, all of
  // which need Wood — a resource none of Law's own kinds produce) hit
  // SpendingPolicy.marginFor's own UnaffordableMarginFloor (-1_000_000) during a genuine
  // Wood drought: a merely-very-negative LaboFondamental score still won by comparison. A
  // LayoutPolicy veto removes the candidate from consideration entirely, immune to
  // whatever every other candidate's own score happens to be.

  test("CountCapLayout vetoes the capped kind once countsAs buildings reach the cap") {
    val layout = CountCapLayout(
      BuildingKind.LaboFondamental,
      Set(BuildingKind.LaboFondamental),
      cap = 1,
      FreeformLayout
    )
    val oneLab =
      MazeState.initial.copy(buildings = List(building(1, 5, 5, BuildingKind.LaboFondamental)))
    assertEquals(
      layout.score(oneLab, BuildingKind.LaboFondamental, Pos(6, 6)),
      Double.NegativeInfinity
    )
  }

  test("CountCapLayout defers to inner below the cap") {
    val layout = CountCapLayout(
      BuildingKind.LaboFondamental,
      Set(BuildingKind.LaboFondamental),
      cap = 1,
      FreeformLayout
    )
    assertEquals(
      layout.score(MazeState.initial, BuildingKind.LaboFondamental, Pos(6, 6)),
      FreeformLayout.score(MazeState.initial, BuildingKind.LaboFondamental, Pos(6, 6))
    )
  }

  test(
    "CountCapLayout's countsAs set can count an upgraded form toward the capped kind's own cap"
  ) {
    // LaboFondamental upgrades in place into e.g. LaboDeLaLoi (same building id, new kind
    // — Placement.tryUpgradeBuilding) — a cap meant to stop a SECOND LaboFondamental must
    // still count the first one after it's upgraded away, or the veto never engages. This
    // is why countsAs is a separate parameter from the capped kind, not just `Set(capped)`.
    val layout = CountCapLayout(
      BuildingKind.LaboFondamental,
      Set(BuildingKind.LaboFondamental, BuildingKind.LaboDeLaLoi),
      cap = 1,
      FreeformLayout
    )
    val upgraded =
      MazeState.initial.copy(buildings = List(building(1, 5, 5, BuildingKind.LaboDeLaLoi)))
    assertEquals(
      layout.score(upgraded, BuildingKind.LaboFondamental, Pos(6, 6)),
      Double.NegativeInfinity,
      "counting only the literal LaboFondamental kind (not its upgraded form) would wrongly defer to inner here"
    )
  }

  test("CountCapLayout leaves every other kind entirely to inner, no veto") {
    val layout = CountCapLayout(
      BuildingKind.LaboFondamental,
      Set(BuildingKind.LaboFondamental),
      cap = 1,
      FreeformLayout
    )
    val oneLab =
      MazeState.initial.copy(buildings = List(building(1, 5, 5, BuildingKind.LaboFondamental)))
    assertEquals(
      layout.score(oneLab, BuildingKind.Watchtower, Pos(6, 6)),
      FreeformLayout.score(oneLab, BuildingKind.Watchtower, Pos(6, 6))
    )
  }
