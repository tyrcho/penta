package towerdefense.domain

// Three independently-tunable weights, one per component score CompositeStrategy blends
// (see CompositeStrategy's doc). All-zero weights would never place anything different
// from a valid candidate list's first entry; in practice at least one weight is nonzero.
case class Weights(resource: Double, counter: Double, maze: Double)

// A single candidate (building kind, cell, and the MazeState that placing it there
// would produce) that already passed Placement.tryPlaceBuilding — i.e. affordable and
// doesn't seal off the enemy's path.
private case class Candidate(kind: BuildingKind, col: Int, row: Int, result: MazeState)

// Blends three signals per candidate placement into one score and builds the best-scoring
// one, instead of LinearStrategy's fixed cost-order/first-cell scan. Side-agnostic and
// opponent-aware like any AiStrategy — see AiStrategy's doc.
case class CompositeStrategy(weights: Weights) extends AiStrategy:

  def maybeBuild(state: MazeState, opponent: MazeState): MazeState =
    val candidates = allCandidates(state)
    if candidates.isEmpty then state
    else
      val scores = candidates.map(c => dangerScore(state, (c.col, c.row), c.kind == BuildingKind.Forest))
      val minScore = scores.min
      val maxScore = scores.max
      candidates
        .zip(scores)
        .map { case (c, score) =>
          val total =
            weights.resource * resourceScore(state, c.kind) +
              weights.counter * counterScore(opponent, c.kind) +
              weights.maze * normalizedMazeScore(score, minScore, maxScore)
          (c.result, total)
        }
        .maxBy(_._2)
        ._1

  private def allCandidates(state: MazeState): Seq[Candidate] =
    for
      kind <- BuildingKind.values.toSeq
      (col, row) <- GridConfig.allCells
      result <- Placement.tryPlaceBuilding(state, kind, col, row).toOption
    yield Candidate(kind, col, row, result)

  // How dangerous the resulting path is for an enemy to walk, not just how long it is:
  // path length alone only delays plunder, but CombatEngine.applyDamageSources deals
  // AuraDamagePerSec to any enemy standing adjacent to a Forest (see accumulateAuraHits
  // there) — routing the path past Forests can kill the enemy outright, which is strictly
  // better than merely making it walk further. `isForestCandidate` counts the candidate
  // itself as a Forest for this check, since the Forest being placed also auras once it's
  // up (existing Forests already do). Exposed at `private[domain]` so tests can verify
  // the scoring directly instead of reasoning backward from `maybeBuild`'s final pick.
  private[domain] def dangerScore(
      state: MazeState,
      candidate: (Int, Int),
      isForestCandidate: Boolean
  ): Double =
    val path = Pathfinding
      .shortestPath(GridConfig.spawnCell, GridConfig.goalCell, state.buildingCells + candidate)
      .getOrElse(Nil)
    val forestCells =
      state.buildings.filter(_.kind == BuildingKind.Forest).map(f => (f.col, f.row)).toSet ++
        (if isForestCandidate then Set(candidate) else Set.empty)
    pathDangerScore(path, forestCells)

  // Split out from dangerScore so the scoring math is testable against a hand-built path,
  // independent of BFS routing specifics. Sums one AuraDamagePerSec hit per *adjacent
  // forest*, not per cell: CombatEngine.applyDamageSources adds damagePerHit once for every
  // forest bordering an enemy's cell (a corridor flanked by forests on both sides deals
  // double damage per second), so a cell adjacent to two forests must score double a cell
  // adjacent to only one, not the same.
  private[domain] def pathDangerScore(path: List[(Int, Int)], forestCells: Set[(Int, Int)]): Double =
    val auraHits = path.map(cell => Pathfinding.neighbors(cell).count(forestCells.contains)).sum
    path.length.toDouble + Balance.AuraDamagePerSec * auraHits

  private def normalizedMazeScore(score: Double, minScore: Double, maxScore: Double): Double =
    if maxScore == minScore then 1.0 else (score - minScore) / (maxScore - minScore)

  // Average affordability margin left over the currencies this building kind consumes —
  // higher when the spend is a smaller fraction of what's on hand. Costs come straight
  // from BuildingSpecs, the same map Placement checks against.
  private def resourceScore(state: MazeState, kind: BuildingKind): Double =
    val cost = BuildingSpecs.all(kind).cost
    val margins = cost.map { case (res, amount) =>
      val available = state.resources.getOrElse(res, 0.0)
      (available - amount) / available
    }
    margins.sum / margins.size

  // Mirrors whichever of Nature (Forest) or Chaos (Cave/Labyrinth) the opponent invests
  // in more — see VictoryConditions: my target for each win condition scales with the
  // opponent's own count of that stat, so matching their pace keeps that multiplier-based
  // target rising instead of letting them coast to a low floor. Loi (Eglise and
  // Watchtower) is deliberately excluded from the comparison and never scores: it feeds
  // neither VictoryConditions target, so mirroring an opponent that invests heavily in
  // either would just copy their one unproductive habit and stalemate forever.
  private def counterScore(opponent: MazeState, kind: BuildingKind): Double =
    val natureCount = opponent.buildings.count(_.kind == BuildingKind.Forest)
    val chaosCount =
      opponent.buildings.count(b => b.kind == BuildingKind.Cave || b.kind == BuildingKind.Labyrinth)
    val leaderCount = natureCount.max(chaosCount)
    val ownFactionCount = kind match
      case BuildingKind.Forest                       => Some(natureCount)
      case BuildingKind.Cave | BuildingKind.Labyrinth => Some(chaosCount)
      case BuildingKind.Eglise | BuildingKind.Watchtower => None
    if ownFactionCount.contains(leaderCount) then 1.0 else 0.0
