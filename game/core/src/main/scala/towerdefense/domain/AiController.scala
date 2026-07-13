package towerdefense.domain

// Dumbest possible opponent: as soon as it can afford a Foret, place it on the
// first buildable cell in row-major order. Deterministic (easy to test), no
// randomness — a reasonable POC default since the vault doesn't specify AI behavior.
object AiController:

  def maybeBuild(state: MazeState): MazeState =
    if state.bois < Balance.ForetCostBois then state
    else buildableCells.iterator.map(c => Placement.tryPlaceForet(state, c._1, c._2)).collectFirst { case Right(s) => s }.getOrElse(state)

  private def buildableCells: Seq[(Int, Int)] =
    for
      row <- 0 until GridConfig.rows
      col <- 0 until GridConfig.cols
    yield (col, row)
