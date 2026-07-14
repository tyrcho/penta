package towerdefense.domain

// Dumbest possible opponent: as soon as it can afford a building, place one on the
// first buildable cell in row-major order. Deterministic (easy to test), no
// randomness — a reasonable POC default since the vault doesn't specify AI behavior.
// Both sides can build either Forest or Cave — see CLAUDE.md, "the game is symmetric".
object AiController:

  def maybeBuild(state: MazeState): MazeState =
    tryBuildOneOf(state, Placement.tryPlaceForest)
      .orElse(tryBuildOneOf(state, Placement.tryPlaceCave))
      .getOrElse(state)

  private def tryBuildOneOf(
      state: MazeState,
      tryPlace: (MazeState, Int, Int) => Either[PlacementError, MazeState]
  ): Option[MazeState] =
    buildableCells.iterator.map(c => tryPlace(state, c._1, c._2)).collectFirst { case Right(s) =>
      s
    }

  private def buildableCells: Seq[(Int, Int)] =
    for
      row <- 0 until GridConfig.rows
      col <- 0 until GridConfig.cols
    yield (col, row)
