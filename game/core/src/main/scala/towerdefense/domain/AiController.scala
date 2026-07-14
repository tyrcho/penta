package towerdefense.domain

// Dumbest possible opponent: as soon as it can afford a building, place one on the
// first buildable cell in row-major order. Deterministic (easy to test), no
// randomness — a reasonable POC default since the vault doesn't specify AI behavior.
// Both sides can build a Forest, a Cave, a Labyrinthe, or an Eglise — see CLAUDE.md,
// "the game is symmetric". Tried by descending wood cost (Eglise 40 > Labyrinthe 20 >
// Forest 10 > Cave 5): each one's wood cost dominates every cheaper building's, so
// trying it later would make it unreachable — by the time its wood cost is affordable,
// the cheaper buildings' wood costs always are too (their fire/light requirements are
// independent currencies and don't create the same trap).
object AiController:

  def maybeBuild(state: MazeState): MazeState =
    tryBuildOneOf(state, Placement.tryPlaceEglise)
      .orElse(tryBuildOneOf(state, Placement.tryPlaceLabyrinthe))
      .orElse(tryBuildOneOf(state, Placement.tryPlaceForest))
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
