package towerdefense.domain.ai

import towerdefense.domain.*
import towerdefense.domain.combat.*
import towerdefense.domain.economy.*
import towerdefense.domain.grid.*

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
  def score(state: MazeState, kind: BuildingKind, cell: Pos): Double

// No positional preference: every cell scores identically, so only the SpendingPolicy
// (and candidate generation order, as a final tie-break) decides.
case object NoLayoutPreference extends LayoutPolicy:
  def score(state: MazeState, kind: BuildingKind, cell: Pos): Double = 0.0

// How dangerous the resulting path is for an enemy to walk, not just how long it is —
// moved verbatim from CompositeStrategy's dangerScore/pathDangerScore. See
// CombatEngine.applyDamageSources: enemies take AuraDamagePerSec from adjacent
// Forest/Jungle and WatchtowerDamagePerSec from any Watchtower within range, so routing
// the path past them can kill the enemy outright, strictly better than merely lengthening
// the walk.
case object FreeformLayout extends LayoutPolicy:

  def score(state: MazeState, kind: BuildingKind, cell: Pos): Double =
    dangerScore(state, cell, isMazeAuraCandidate(kind), kind == BuildingKind.Watchtower)

  // A *new* candidate counts as an aura source if it already auras (Forest/Jungle) or
  // will the moment AiStrategy.upgradeAnyAffordable gets to it (Grove — the only
  // directly-buildable kind on Nature's aura-bound upgrade chain).
  private def isMazeAuraCandidate(kind: BuildingKind): Boolean =
    kind == BuildingKind.Grove || CombatEngine.auraBuildingKinds.contains(kind)

  private[domain] def dangerScore(
      state: MazeState,
      candidate: Pos,
      isAuraCandidate: Boolean,
      isRangedCandidate: Boolean = false
  ): Double =
    val path = Pathfinding
      .shortestPath(GridConfig.spawnCell, GridConfig.goalCell, state.buildingCells + candidate)
      .getOrElse(Nil)
    val forestCells =
      state.buildings
        .filter(b => CombatEngine.auraBuildingKinds.contains(b.kind))
        .map(f => Pos(f.col, f.row))
        .toSet ++
        (if isAuraCandidate then Set(candidate) else Set.empty)
    val towerCells =
      state.buildings.filter(_.kind == BuildingKind.Watchtower).map(w => Pos(w.col, w.row)).toSet ++
        (if isRangedCandidate then Set(candidate) else Set.empty)
    pathDangerScore(path, forestCells, towerCells)

  private[domain] def pathDangerScore(
      path: List[Pos],
      forestCells: Set[Pos],
      towerCells: Set[Pos] = Set.empty
  ): Double =
    val auraHits = path.map(cell => Pathfinding.neighbors(cell).count(forestCells.contains)).sum
    val towerHits =
      path
        .map(cell =>
          towerCells
            .count(t => CombatEngine.chebyshevDistance(cell, t) <= Balance.WatchtowerRangeCells)
        )
        .sum
    path.length.toDouble + Balance.AuraDamagePerSec * auraHits + Balance.WatchtowerDamagePerSec * towerHits

// Precomputed wall layout (see MazeTemplate) followed strictly in order: the earliest
// not-yet-built template cell scores highest, every other cell (on-template but already
// built, or off-template entirely) scores Double.NegativeInfinity — moved from
// TemplateStrategy, generalized into a score instead of a hand-walked iterator so
// ComposedStrategy can treat every LayoutPolicy uniformly.
case class TemplateLayout(template: (Int, Int) => List[Pos]) extends LayoutPolicy:
  def score(state: MazeState, kind: BuildingKind, cell: Pos): Double =
    val remaining =
      template(GridConfig.cols, GridConfig.rows).filterNot(state.buildingCells.contains)
    val idx = remaining.indexOf(cell)
    if idx < 0 then Double.NegativeInfinity else -idx.toDouble

// Forces a fixed sequence of building kinds for the first `opening.size` buildings a maze
// places, deferring entirely to `inner`'s own cell-scoring both for the forced kind's
// placement and for everything once the opening is complete. Vetoes (Double.
// NegativeInfinity) every other kind during the opening — same veto convention
// TemplateLayout already uses for off-template cells, and the one ComposedStrategy.
// maybeBuild already honors: if the forced kind isn't affordable yet, EVERY candidate is
// vetoed, `eligible` ends up empty, and the maze does nothing that tick rather than
// substituting a different kind and drifting off-script.
//
// Exists to give an AI strategy explicit, auditable control over how its starting Gold
// gets spent, instead of leaving the opening to emerge from the ordinary
// candidate-scoring competition — motivated by two real bugs found by measuring actual
// matches: PlunderSpending once spent its entire starting Gold on a zero-return
// DragonsLair turn 1 (no gate on its own priority bonus), and ScienceSpending
// incidentally built a 13-Forest wall chasing Wood income instead of its intended
// producers, both discovered only by reading `sim/run <a> <b> --log` transcripts.
//
// Position is read directly off `state.buildings.size`, not separately tracked — safe
// because a fresh match starts with 0 buildings and this maze's own maybeBuild is the
// only thing that ever adds one (upgrades change kind in place, they don't add a new
// building). Side-agnostic like every LayoutPolicy/AiStrategy: reads only this maze's own
// state, so it composes identically for the player or AI slot.
case class ForcedOpeningLayout(opening: Seq[BuildingKind], inner: LayoutPolicy)
    extends LayoutPolicy:
  def score(state: MazeState, kind: BuildingKind, cell: Pos): Double =
    val position = state.buildings.size
    if position < opening.size then
      if kind == opening(position) then inner.score(state, kind, cell) else Double.NegativeInfinity
    else inner.score(state, kind, cell)

// Rewards a healer-kind candidate (Grove/Forest/Jungle) for landing next to more of the
// maze's own existing buildings — see CombatEngine.healBuildingCorruption: a Nature
// building heals itself and every building within Chebyshev distance 1 of it, and
// multiple nearby healers stack. Clustering a healer among existing buildings ("in
// alternance with others"), rather than off in an unclaimed corner, maximizes how many
// buildings get covered and how much their healing stacks — added at the project owner's
// explicit request, alongside Balance's own corruption-speed/heal-speed tuning, to make
// corruption resistance a real positioning lever instead of raw constants alone.
//
// Non-healer kinds, and any cell inner already vetoes (-Infinity), pass through
// unchanged — the bonus never overrides a veto, same convention every other decorator
// LayoutPolicy in this file follows.
case class HealClusterLayout(
    healerKinds: Set[BuildingKind],
    bonusPerNeighbor: Double,
    inner: LayoutPolicy
) extends LayoutPolicy:
  def score(state: MazeState, kind: BuildingKind, cell: Pos): Double =
    val base = inner.score(state, kind, cell)
    if !healerKinds.contains(kind) || base == Double.NegativeInfinity then base
    else
      val neighborCount =
        state.buildings.count(b => CombatEngine.chebyshevDistance(Pos(b.col, b.row), cell) <= 1)
      base + bonusPerNeighbor * neighborCount

// Vetoes (-Infinity) placing `capped` once the maze already has `cap` buildings whose
// CURRENT kind is in `countsAs` — a hard, unconditional veto, unlike any SpendingPolicy
// penalty (however large a negative number). Diagnosed via transcript: a -1_000
// SpendingPolicy penalty on a second LaboFondamental still lost the tie-break during a
// genuine resource drought, when every OTHER candidate (needing Wood, which Law's own
// building set never produces) hit SpendingPolicy.marginFor's own UnaffordableMarginFloor
// (-1_000_000) — a merely-very-negative LaboFondamental still won by comparison. A layout
// veto removes the candidate from consideration entirely, immune to what every other
// candidate's own score happens to be that tick.
//
// `countsAs` is separate from `capped` (not just `Set(capped)`) because a building like
// LaboFondamental upgrades in place into a different kind (same id — Placement.
// tryUpgradeBuilding), so counting only the literal `capped` kind would undercount once
// the first one's already been upgraded away, letting a second slip through.
case class CountCapLayout(
    capped: BuildingKind,
    countsAs: Set[BuildingKind],
    cap: Int,
    inner: LayoutPolicy
) extends LayoutPolicy:
  def score(state: MazeState, kind: BuildingKind, cell: Pos): Double =
    if kind == capped && state.buildings.count(b => countsAs.contains(b.kind)) >= cap then
      Double.NegativeInfinity
    else inner.score(state, kind, cell)
