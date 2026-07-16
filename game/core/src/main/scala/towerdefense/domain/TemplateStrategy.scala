package towerdefense.domain

// Builds toward a fixed MazeTemplate one cell per tick, instead of scoring candidates
// like CompositeStrategy or scanning the whole grid like LinearStrategy. `template`'s
// returned order is followed as given, not re-sorted: MazeTemplate.comb/combVertical
// order their cells so that one tooth row (or column) finishes before the next starts,
// since a half-finished tooth blocks nothing (see MazeTemplate's doc) — building in any
// other order would spread cells evenly across every tooth and leave the whole maze
// ineffective for far longer. It's still safe regardless of order in the sense that no
// partial build state can ever trip Placement's WouldBlockPath check: MazeTemplateTest
// proves every subset of a reachable template's walls is itself reachable. `template`
// takes the live grid dimensions rather than being a precomputed List, so this stays
// correct if GridConfig's size ever changes.
case class TemplateStrategy(template: (Int, Int) => List[(Int, Int)]) extends AiStrategy:

  // Grove first, unlike LinearStrategy's pure cost order: Grove is Nature's only
  // directly-buildable tier (Forest/Jungle are upgrade-only — see Placement's
  // buildableDirectly check), and Nature is the only faction with a combat aura
  // (CombatEngine.applyDamageSources/auraBuildingKinds), so a wall built from Groves —
  // which maybeUpgrade below then grows into Forests — doesn't just lengthen the enemy's
  // path the way any other kind would, it eventually kills them outright. The same reason
  // CompositeStrategy's maze-only always favors an aura kind (see its dangerScore doc),
  // and why an early version of this strategy, which reused LinearStrategy's
  // expensive-first order verbatim, lost 40-0 to maze-only in `make sim`: it was building
  // whatever Church/Labyrinth/Watchtower it could afford on the comb's cells instead,
  // forcing a long walk but never actually killing anything. The remaining kinds stay in
  // LinearStrategy's original descending-wood-cost order as the affordability fallback
  // for when wood specifically is scarce.
  private val buildOrder: Seq[BuildingKind] = Seq(
    BuildingKind.Grove,
    BuildingKind.Church,
    BuildingKind.Labyrinth,
    BuildingKind.Watchtower,
    BuildingKind.Cave
  )

  def maybeBuild(state: MazeState, opponent: MazeState): MazeState =
    val remaining = template(GridConfig.cols, GridConfig.rows).filterNot(state.buildingCells.contains)
    remaining.iterator
      .flatMap { case (col, row) =>
        buildOrder.iterator.flatMap(kind => Placement.tryPlaceBuilding(state, kind, col, row).toOption)
      }
      .nextOption()
      .getOrElse(state)

  // Grows an existing Grove into a Forest (and a Forest into a Jungle) once affordable —
  // same "first that works" simplicity as maybeBuild, no attempt to pick the "best" one.
  // Every building this strategy has is already on a template cell (maybeBuild never
  // places elsewhere), so upgrading any of them is always upgrading the maze wall, no
  // extra filtering needed — same pattern as LinearStrategy.maybeUpgrade.
  override def maybeUpgrade(state: MazeState, opponent: MazeState): MazeState =
    state.buildings.iterator
      .flatMap(b => Placement.tryUpgradeBuilding(state, b.col, b.row).toOption)
      .nextOption()
      .getOrElse(state)
