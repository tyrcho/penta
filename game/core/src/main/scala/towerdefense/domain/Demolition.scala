package towerdefense.domain

enum DemolitionError derives CanEqual:
  case NoBuildingThere

// The inverse of Placement: removes whichever building occupies a cell and refunds
// Balance.DemolishRefundFraction of its original cost. No reachability check like
// Placement's wouldBlockPath — removing an obstacle can only ever add reachable cells,
// never seal off the goal, so destruction is always allowed once something is there.
object Demolition:

  def tryDestroy(state: MazeState, col: Int, row: Int): Either[DemolitionError, MazeState] =
    state.buildings.find(b => b.col == col && b.row == row) match
      case None    => Left(DemolitionError.NoBuildingThere)
      case Some(b) => Right(destroyBuilding(state, b))

  private def destroyBuilding(state: MazeState, b: Building): MazeState =
    val cost = BuildingSpecs.all(b.kind).cost
    state.copy(
      buildings = state.buildings.filterNot(_.id == b.id),
      resources = credit(state.resources, cost, Balance.DemolishRefundFraction)
    )

  private def credit(
      resources: Map[Resource, Double],
      cost: Map[Resource, Double],
      fraction: Double
  ): Map[Resource, Double] =
    cost.foldLeft(resources) { case (acc, (res, amount)) =>
      acc.updated(res, acc.getOrElse(res, 0.0) + amount * fraction)
    }
