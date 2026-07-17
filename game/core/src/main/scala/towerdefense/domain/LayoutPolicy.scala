package towerdefense.domain

// The "which cell" half of an AiStrategy — where a new building of a given kind should
// go, independent of the SpendingPolicy that chose the kind. `kind` is still a parameter
// (not purely positional) because a cell's layout value can depend on what's placed there
// — see FreeformLayout, where an aura/ranged candidate scores a cell higher than a
// harmless one at the same location.
//
// Double.NegativeInfinity means "never build here": ComposedStrategy drops any candidate
// scoring -Infinity whenever at least one candidate doesn't, and no-ops entirely if every
// candidate does — this is what lets TemplateLayout stay strictly on-template.
trait LayoutPolicy:
  def score(state: MazeState, kind: BuildingKind, cell: (Int, Int)): Double

// No positional preference: every cell scores identically, so only the SpendingPolicy
// (and candidate generation order, as a final tie-break) decides.
case object NoLayoutPreference extends LayoutPolicy:
  def score(state: MazeState, kind: BuildingKind, cell: (Int, Int)): Double = 0.0

// How dangerous the resulting path is for an enemy to walk, not just how long it is —
// moved verbatim from CompositeStrategy's dangerScore/pathDangerScore. See
// CombatEngine.applyDamageSources: enemies take AuraDamagePerSec from adjacent
// Forest/Jungle and WatchtowerDamagePerSec from any Watchtower within range, so routing
// the path past them can kill the enemy outright, strictly better than merely lengthening
// the walk.
case object FreeformLayout extends LayoutPolicy:

  def score(state: MazeState, kind: BuildingKind, cell: (Int, Int)): Double =
    dangerScore(state, cell, isMazeAuraCandidate(kind), kind == BuildingKind.Watchtower)

  // A *new* candidate counts as an aura source if it already auras (Forest/Jungle) or
  // will the moment AiStrategy.upgradeAnyAffordable gets to it (Grove — the only
  // directly-buildable kind on Nature's aura-bound upgrade chain).
  private def isMazeAuraCandidate(kind: BuildingKind): Boolean =
    kind == BuildingKind.Grove || CombatEngine.auraBuildingKinds.contains(kind)

  private[domain] def dangerScore(
      state: MazeState,
      candidate: (Int, Int),
      isAuraCandidate: Boolean,
      isRangedCandidate: Boolean = false
  ): Double =
    val path = Pathfinding
      .shortestPath(GridConfig.spawnCell, GridConfig.goalCell, state.buildingCells + candidate)
      .getOrElse(Nil)
    val forestCells =
      state.buildings.filter(b => CombatEngine.auraBuildingKinds.contains(b.kind)).map(f => (f.col, f.row)).toSet ++
        (if isAuraCandidate then Set(candidate) else Set.empty)
    val towerCells =
      state.buildings.filter(_.kind == BuildingKind.Watchtower).map(w => (w.col, w.row)).toSet ++
        (if isRangedCandidate then Set(candidate) else Set.empty)
    pathDangerScore(path, forestCells, towerCells)

  private[domain] def pathDangerScore(
      path: List[(Int, Int)],
      forestCells: Set[(Int, Int)],
      towerCells: Set[(Int, Int)] = Set.empty
  ): Double =
    val auraHits = path.map(cell => Pathfinding.neighbors(cell).count(forestCells.contains)).sum
    val towerHits =
      path.map(cell => towerCells.count(t => CombatEngine.chebyshevDistance(cell, t) <= Balance.WatchtowerRangeCells)).sum
    path.length.toDouble + Balance.AuraDamagePerSec * auraHits + Balance.WatchtowerDamagePerSec * towerHits

// Precomputed wall layout (see MazeTemplate) followed strictly in order: the earliest
// not-yet-built template cell scores highest, every other cell (on-template but already
// built, or off-template entirely) scores Double.NegativeInfinity — moved from
// TemplateStrategy, generalized into a score instead of a hand-walked iterator so
// ComposedStrategy can treat every LayoutPolicy uniformly.
case class TemplateLayout(template: (Int, Int) => List[(Int, Int)]) extends LayoutPolicy:
  def score(state: MazeState, kind: BuildingKind, cell: (Int, Int)): Double =
    val remaining = template(GridConfig.cols, GridConfig.rows).filterNot(state.buildingCells.contains)
    val idx = remaining.indexOf(cell)
    if idx < 0 then Double.NegativeInfinity else -idx.toDouble
