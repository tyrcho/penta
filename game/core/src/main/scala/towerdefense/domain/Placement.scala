package towerdefense.domain

object Placement:

  def tryPlaceForest(state: MazeState, col: Int, row: Int): Either[String, MazeState] =
    for
      _ <- checkCell(state, col, row)
      _ <- Either.cond(state.wood >= Balance.ForestCostWood, (), "Not enough wood")
    yield placeForest(state, col, row)

  def tryPlaceCave(state: MazeState, col: Int, row: Int): Either[String, MazeState] =
    for
      _ <- checkCell(state, col, row)
      _ <- Either.cond(state.wood >= Balance.CaveCostWood && state.fire >= Balance.CaveCostFire, (), "Not enough resources")
    yield placeCave(state, col, row)

  private def checkCell(state: MazeState, col: Int, row: Int): Either[String, Unit] =
    if !GridConfig.isInBounds(col, row) then Left("Cell is outside the grid")
    else if Set(GridConfig.spawnCell, GridConfig.goalCell).contains((col, row)) then Left("Cannot build on spawn or goal")
    else if buildingCells(state).contains((col, row)) then Left("Cell already occupied")
    else if wouldBlockPath(state, col, row) then Left("Would block the only path to the goal")
    else Right(())

  private def wouldBlockPath(state: MazeState, col: Int, row: Int): Boolean =
    val blocked = buildingCells(state) + ((col, row))
    !Pathfinding.isReachable(GridConfig.spawnCell, GridConfig.goalCell, blocked)

  private def buildingCells(state: MazeState): Set[(Int, Int)] =
    state.forests.map(f => (f.col, f.row)).toSet ++ state.caves.map(c => (c.col, c.row))

  private def placeForest(state: MazeState, col: Int, row: Int): MazeState =
    val forest = Forest(state.nextId, col, row, elfSpawnInMs = Balance.ElfSpawnIntervalMs)
    state.copy(forests = forest :: state.forests, wood = state.wood - Balance.ForestCostWood, nextId = state.nextId + 1)

  private def placeCave(state: MazeState, col: Int, row: Int): MazeState =
    val cave = Cave(state.nextId, col, row, goblinSpawnInMs = Balance.GoblinSpawnIntervalMs)
    state.copy(
      caves = cave :: state.caves,
      wood = state.wood - Balance.CaveCostWood,
      fire = state.fire - Balance.CaveCostFire,
      nextId = state.nextId + 1,
    )
