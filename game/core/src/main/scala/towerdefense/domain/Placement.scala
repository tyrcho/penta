package towerdefense.domain

object Placement:

  def tryPlaceTower(state: GameState, col: Int, row: Int): Either[String, GameState] =
    if !GridConfig.isInBounds(col, row) then Left("Cell is outside the grid")
    else if Set(GridConfig.spawnCell, GridConfig.goalCell).contains((col, row)) then Left("Cannot build on spawn or goal")
    else if state.towers.exists(t => t.col == col && t.row == row) then Left("Cell already occupied")
    else if state.gold < Balance.TowerCost then Left("Not enough gold")
    else if wouldBlockPath(state, col, row) then Left("Would block the only path to the goal")
    else Right(placeTower(state, col, row))

  private def wouldBlockPath(state: GameState, col: Int, row: Int): Boolean =
    val blocked = state.towers.map(t => (t.col, t.row)).toSet + ((col, row))
    !Pathfinding.isReachable(GridConfig.spawnCell, GridConfig.goalCell, blocked)

  private def placeTower(state: GameState, col: Int, row: Int): GameState =
    val tower = Tower(state.nextId, col, row, Balance.TowerRangePx, Balance.TowerDamage, Balance.TowerCooldownMs, reloadMs = 0)
    state.copy(towers = tower :: state.towers, gold = state.gold - Balance.TowerCost, nextId = state.nextId + 1)
