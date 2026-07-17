package towerdefense.domain

// A single candidate (building kind, cell, and the MazeState that placing it there would
// produce) that already passed Placement.tryPlaceBuilding — i.e. affordable and doesn't
// seal off the enemy's path.
private case class Candidate(kind: BuildingKind, col: Int, row: Int, result: MazeState)

// Combines a LayoutPolicy ("where") and a SpendingPolicy ("what") into one AiStrategy by
// jointly scoring every (kind, cell) candidate placement and picking the best-scoring one
// — replaces the old CompositeStrategy (LayoutPolicy = FreeformLayout or
// NoLayoutPreference) and TemplateStrategy (LayoutPolicy = TemplateLayout,
// SpendingPolicy = GrovePriority). Side-agnostic and opponent-aware like any AiStrategy —
// see AiStrategy's doc.
case class ComposedStrategy(
    layout: LayoutPolicy,
    spending: SpendingPolicy,
    layoutWeight: Double = 1.0,
    spendingWeight: Double = 1.0,
    random: scala.util.Random = new scala.util.Random()
) extends AiStrategy:

  def maybeBuild(state: MazeState, opponent: MazeState): MazeState =
    val candidates = allCandidates(state)
    if candidates.isEmpty then state
    else
      val layoutScores = candidates.map(c => layout.score(state, c.kind, (c.col, c.row)))
      // A LayoutPolicy can veto a cell outright (Double.NegativeInfinity, e.g.
      // TemplateLayout off-template) — if at least one candidate isn't vetoed, only those
      // are eligible; if every candidate is vetoed, do nothing rather than build off a
      // layout's own restriction.
      val eligible = candidates.zip(layoutScores).filter { case (_, score) => score.isFinite }
      if eligible.isEmpty then state
      else
        val finiteScores = eligible.map(_._2)
        val minScore = finiteScores.min
        val maxScore = finiteScores.max
        val totals = eligible.map { case (c, layoutScore) =>
          val normalizedLayout = normalize(layoutScore, minScore, maxScore)
          val total = spendingWeight * spending.score(state, opponent, c.kind) + layoutWeight * normalizedLayout
          (c, total)
        }
        val best = totals.map(_._2).max
        val tied = totals.collect { case (c, total) if total == best => c }
        tied(random.nextInt(tied.size)).result

  // Grows any of its own buildings to the next upgrade tier when affordable — see
  // AiStrategy.upgradeAnyAffordable. Shared unconditionally by every ComposedStrategy
  // instance, same as every other AiStrategy implementation.
  override def maybeUpgrade(state: MazeState, opponent: MazeState): MazeState =
    AiStrategy.upgradeAnyAffordable(state)

  private def allCandidates(state: MazeState): Seq[Candidate] =
    for
      kind <- BuildingKind.values.toSeq
      (col, row) <- GridConfig.allCells
      result <- Placement.tryPlaceBuilding(state, kind, col, row).toOption
    yield Candidate(kind, col, row, result)

  private def normalize(score: Double, minScore: Double, maxScore: Double): Double =
    if maxScore == minScore then 1.0 else (score - minScore) / (maxScore - minScore)
