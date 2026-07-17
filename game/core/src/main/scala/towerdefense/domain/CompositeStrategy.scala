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
      val scores = candidates.map(c => dangerScore(state, (c.col, c.row), isMazeAuraCandidate(c.kind)))
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

  // Grows any of its own buildings to the next upgrade tier when affordable — see
  // AiStrategy.upgradeAnyAffordable. Just as necessary here as it is for
  // LinearStrategy/TemplateStrategy: dangerScore's whole premise is routing the enemy
  // path past an aura-dealing building (CombatEngine.auraBuildingKinds = Forest, Jungle),
  // but maybeBuild can only ever place a Grove directly (Forest/Jungle are upgrade-only —
  // see Placement.buildableDirectly). Without this override a maze-weighted
  // CompositeStrategy builds a wall of harmless Groves and never once realizes the aura
  // damage its own scoring is optimizing for — caught via `make sim`: maze-only landed
  // zero kills across two full tournament matches before this fix.
  override def maybeUpgrade(state: MazeState, opponent: MazeState): MazeState =
    AiStrategy.upgradeAnyAffordable(state)

  private def allCandidates(state: MazeState): Seq[Candidate] =
    for
      kind <- BuildingKind.values.toSeq
      (col, row) <- GridConfig.allCells
      result <- Placement.tryPlaceBuilding(state, kind, col, row).toOption
    yield Candidate(kind, col, row, result)

  // A *new* candidate counts as an aura source if it already auras (Forest/Jungle) or
  // will the moment maybeUpgrade above gets to it (Grove — the only directly-buildable
  // kind on Nature's aura-bound upgrade chain, see BuildingSpecs.upgradesTo). Existing
  // buildings on the board score via CombatEngine.auraBuildingKinds directly inside
  // dangerScore, unaffected by this — it's only about how a *new* placement's own kind is
  // valued. Without crediting Grove here, a Grove candidate ties on raw path length with
  // a same-cell Cave/Labyrinth/Church/Watchtower candidate that can never aura, so a
  // strategy briefly short on wood (e.g. right after paying for an earlier upgrade)
  // happily settles for the Cave, permanently losing that wall cell's damage potential —
  // seen directly in a `make sim` transcript: maze-only built 9 walls, 6 of them Cave,
  // because Grove just wasn't affordable in the moment on 6 separate ticks. This fix
  // narrows that pattern (Grove now wins whenever it's merely tied or close, not just
  // literally the only affordable option) without fully eliminating it — genuinely
  // reserving wood for a future Grove instead of spending it the moment something's
  // affordable would need lookahead this scoring doesn't have.
  private def isMazeAuraCandidate(kind: BuildingKind): Boolean =
    kind == BuildingKind.Grove || CombatEngine.auraBuildingKinds.contains(kind)

  // How dangerous the resulting path is for an enemy to walk, not just how long it is:
  // path length alone only delays plunder, but CombatEngine.applyDamageSources deals
  // AuraDamagePerSec to any enemy standing adjacent to a Forest/Jungle (see
  // accumulateAuraHits there) — routing the path past them can kill the enemy outright,
  // which is strictly better than merely making it walk further. `isAuraCandidate` is
  // isMazeAuraCandidate at maybeBuild's call site, but dangerScore itself takes a plain
  // Boolean so tests can drive it directly instead of reasoning backward from
  // maybeBuild's final pick. Exposed at `private[domain]` for exactly that.
  private[domain] def dangerScore(
      state: MazeState,
      candidate: (Int, Int),
      isAuraCandidate: Boolean
  ): Double =
    val path = Pathfinding
      .shortestPath(GridConfig.spawnCell, GridConfig.goalCell, state.buildingCells + candidate)
      .getOrElse(Nil)
    val forestCells =
      state.buildings.filter(b => CombatEngine.auraBuildingKinds.contains(b.kind)).map(f => (f.col, f.row)).toSet ++
        (if isAuraCandidate then Set(candidate) else Set.empty)
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

  // Mirrors whichever of Nature (Grove/Forest/Jungle) or Chaos (Cave/Labyrinth) the
  // opponent invests in more — see VictoryConditions: my target for each win condition
  // scales with the opponent's own count of that stat, so matching their pace keeps that
  // multiplier-based target rising instead of letting them coast to a low floor. Loi
  // (Church and Watchtower) is deliberately excluded from the comparison and never
  // scores: it feeds neither VictoryConditions target, so mirroring an opponent that
  // invests heavily in either would just copy their one unproductive habit and
  // stalemate forever.
  private val natureBuildingKinds: Set[BuildingKind] =
    Set(BuildingKind.Grove, BuildingKind.Forest, BuildingKind.Jungle)

  private def counterScore(opponent: MazeState, kind: BuildingKind): Double =
    val natureCount = opponent.buildings.count(b => natureBuildingKinds.contains(b.kind))
    val chaosCount =
      opponent.buildings.count(b => b.kind == BuildingKind.Cave || b.kind == BuildingKind.Labyrinth)
    val leaderCount = natureCount.max(chaosCount)
    val ownFactionCount = kind match
      case BuildingKind.Grove | BuildingKind.Forest | BuildingKind.Jungle => Some(natureCount)
      case BuildingKind.Cave | BuildingKind.Labyrinth                    => Some(chaosCount)
      case BuildingKind.Church | BuildingKind.Watchtower                 => None
    if ownFactionCount.contains(leaderCount) then 1.0 else 0.0
