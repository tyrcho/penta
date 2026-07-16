package towerdefense.domain

enum PlacementError derives CanEqual:
  case OutOfBounds, OnSpawnOrGoal, CellOccupied, WouldBlockPath, InsufficientResources,
    CannotBuildDirectly, NoBuildingThere, NoUpgradeAvailable

object Placement:

  def tryPlaceBuilding(
      state: MazeState,
      kind: BuildingKind,
      col: Int,
      row: Int
  ): Either[PlacementError, MazeState] =
    val spec = BuildingSpecs.all(kind)
    for
      _ <- Either.cond(spec.buildableDirectly, (), PlacementError.CannotBuildDirectly)
      _ <- checkCell(state, col, row)
      _ <- Either.cond(canAfford(state.resources, spec.cost), (), PlacementError.InsufficientResources)
    yield placeBuilding(state, kind, spec, col, row)

  // Upgrades whatever building already sits at (col, row) to the next tier in
  // BuildingSpecs.upgradesTo (Grove -> Forest -> Jungle today) — the only way to reach a
  // kind with buildableDirectly = false. No reachability check like tryPlaceBuilding's:
  // the building already occupies that cell, so the maze's obstacle footprint doesn't
  // change shape, only which kind sits there.
  def tryUpgradeBuilding(state: MazeState, col: Int, row: Int): Either[PlacementError, MazeState] =
    for
      building <- state.buildings.find(b => b.col == col && b.row == row).toRight(PlacementError.NoBuildingThere)
      nextKind <- BuildingSpecs.upgradesTo.get(building.kind).toRight(PlacementError.NoUpgradeAvailable)
      spec = BuildingSpecs.all(nextKind)
      _ <- Either.cond(canAfford(state.resources, spec.cost), (), PlacementError.InsufficientResources)
    yield upgradeBuilding(state, building, nextKind, spec)

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

  // Same id, same cell, new kind — the timer resets to the new tier's own interval
  // (simplest behavior: an upgrade doesn't inherit a partial countdown from the tier it
  // replaced, since the two tiers can spawn different units at different rates).
  private def upgradeBuilding(
      state: MazeState,
      building: Building,
      nextKind: BuildingKind,
      spec: BuildingSpec
  ): MazeState =
    val upgraded = building.copy(kind = nextKind, spawnCountdownMs = spec.spawns.map(_._2).getOrElse(0.0))
    state.copy(
      buildings = upgraded :: state.buildings.filterNot(_.id == building.id),
      resources = debit(state.resources, spec.cost)
    )

  private def debit(resources: Map[Resource, Double], cost: Map[Resource, Double]): Map[Resource, Double] =
    cost.foldLeft(resources) { case (acc, (res, amount)) =>
      acc.updated(res, acc.getOrElse(res, 0.0) - amount)
    }
