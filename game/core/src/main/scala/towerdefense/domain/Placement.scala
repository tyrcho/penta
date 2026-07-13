package towerdefense.domain

object Placement:

  def tryPlaceForet(state: MazeState, col: Int, row: Int): Either[String, MazeState] =
    if !GridConfig.isInBounds(col, row) then Left("Cell is outside the grid")
    else if Set(GridConfig.spawnCell, GridConfig.goalCell).contains((col, row)) then Left("Cannot build on spawn or goal")
    else if state.forets.exists(f => f.col == col && f.row == row) then Left("Cell already occupied")
    else if state.bois < Balance.ForetCostBois then Left("Not enough bois")
    else if wouldBlockPath(state, col, row) then Left("Would block the only path to the goal")
    else Right(placeForet(state, col, row))

  private def wouldBlockPath(state: MazeState, col: Int, row: Int): Boolean =
    val blocked = state.forets.map(f => (f.col, f.row)).toSet + ((col, row))
    !Pathfinding.isReachable(GridConfig.spawnCell, GridConfig.goalCell, blocked)

  private def placeForet(state: MazeState, col: Int, row: Int): MazeState =
    val foret = Foret(state.nextId, col, row, elfeSpawnInMs = Balance.ElfeSpawnIntervalMs)
    state.copy(forets = foret :: state.forets, bois = state.bois - Balance.ForetCostBois, nextId = state.nextId + 1)
