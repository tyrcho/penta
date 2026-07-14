package towerdefense.domain

enum PlacementError derives CanEqual:
  case OutOfBounds, OnSpawnOrGoal, CellOccupied, WouldBlockPath, InsufficientResources

object Placement:

  def tryPlaceForest(state: MazeState, col: Int, row: Int): Either[PlacementError, MazeState] =
    for
      _ <- checkCell(state, col, row)
      _ <- Either.cond(
        state.wood >= Balance.ForestCostWood,
        (),
        PlacementError.InsufficientResources
      )
    yield placeForest(state, col, row)

  def tryPlaceCave(state: MazeState, col: Int, row: Int): Either[PlacementError, MazeState] =
    for
      _ <- checkCell(state, col, row)
      _ <- Either.cond(
        state.wood >= Balance.CaveCostWood && state.fire >= Balance.CaveCostFire,
        (),
        PlacementError.InsufficientResources
      )
    yield placeCave(state, col, row)

  def tryPlaceLabyrinthe(state: MazeState, col: Int, row: Int): Either[PlacementError, MazeState] =
    for
      _ <- checkCell(state, col, row)
      _ <- Either.cond(
        state.wood >= Balance.LabyrintheCostWood && state.fire >= Balance.LabyrintheCostFire,
        (),
        PlacementError.InsufficientResources
      )
    yield placeLabyrinthe(state, col, row)

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

  private def placeForest(state: MazeState, col: Int, row: Int): MazeState =
    val forest = Forest(state.nextId, col, row, elfSpawnInMs = Balance.ElfSpawnIntervalMs)
    state.copy(
      forests = forest :: state.forests,
      wood = state.wood - Balance.ForestCostWood,
      nextId = state.nextId + 1
    )

  private def placeCave(state: MazeState, col: Int, row: Int): MazeState =
    val cave = Cave(state.nextId, col, row, goblinSpawnInMs = Balance.GoblinSpawnIntervalMs)
    state.copy(
      caves = cave :: state.caves,
      wood = state.wood - Balance.CaveCostWood,
      fire = state.fire - Balance.CaveCostFire,
      nextId = state.nextId + 1
    )

  private def placeLabyrinthe(state: MazeState, col: Int, row: Int): MazeState =
    val labyrinthe =
      Labyrinth(state.nextId, col, row, minotaurSpawnInMs = Balance.MinotaurSpawnIntervalMs)
    state.copy(
      labyrinths = labyrinthe :: state.labyrinths,
      wood = state.wood - Balance.LabyrintheCostWood,
      fire = state.fire - Balance.LabyrintheCostFire,
      nextId = state.nextId + 1
    )
