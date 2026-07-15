package towerdefense.domain

enum PlacementError derives CanEqual:
  case OutOfBounds, OnSpawnOrGoal, CellOccupied, WouldBlockPath, InsufficientResources

object Placement:

  def tryPlaceBuilding(
      state: MazeState,
      kind: BuildingKind,
      col: Int,
      row: Int
  ): Either[PlacementError, MazeState] =
    val spec = BuildingSpecs.all(kind)
    for
      _ <- checkCell(state, col, row)
      _ <- Either.cond(canAfford(state.resources, spec.cost), (), PlacementError.InsufficientResources)
    yield placeBuilding(state, kind, spec, col, row)

  def canAfford(resources: Map[Resource, Double], cost: Map[Resource, Double]): Boolean =
    cost.forall { case (res, amount) => resources.getOrElse(res, 0.0) >= amount }

  private def checkCell(state: MazeState, col: Int, row: Int): Either[PlacementError, Unit] =
    if !GridConfig.isInBounds(col, row) then Left(PlacementError.OutOfBounds)
    else if Set(GridConfig.spawnCell, GridConfig.goalCell).contains((col, row)) then
      Left(PlacementError.OnSpawnOrGoal)
    else if state.buildingCells.contains((col, row)) then Left(PlacementError.CellOccupied)
    else if wouldBlockPath(state, col, row) then Left(PlacementError.WouldBlockPath)
    else Right(())

  private def wouldBlockPath(state: MazeState, col: Int, row: Int): Boolean =
    val blocked = state.buildingCells + ((col, row))
    !Pathfinding.isReachable(GridConfig.spawnCell, GridConfig.goalCell, blocked)

  private def placeBuilding(
      state: MazeState,
      kind: BuildingKind,
      spec: BuildingSpec,
      col: Int,
      row: Int
  ): MazeState =
    val building = Building(
      state.nextId,
      col,
      row,
      kind,
      spawnCountdownMs = spec.spawns.map(_._2).getOrElse(0.0)
    )
    state.copy(
      buildings = building :: state.buildings,
      resources = debit(state.resources, spec.cost),
      nextId = state.nextId + 1
    )

  private def debit(resources: Map[Resource, Double], cost: Map[Resource, Double]): Map[Resource, Double] =
    cost.foldLeft(resources) { case (acc, (res, amount)) =>
      acc.updated(res, acc.getOrElse(res, 0.0) - amount)
    }
