package towerdefense.domain

// Builds toward a fixed MazeTemplate one cell per tick, instead of scoring candidates
// like CompositeStrategy or scanning the whole grid like LinearStrategy. `template`'s
// returned order is followed as given, not re-sorted: MazeTemplate.comb/combVertical
// order their cells so that one tooth row (or column) finishes before the next starts,
// since a half-finished tooth blocks nothing (see MazeTemplate's doc) — building in any
// other order would spread cells evenly across every tooth and leave the whole maze
// ineffective for far longer. It's still safe regardless of order in the sense that no
// partial build state can ever trip Placement's WouldBlockPath check: MazeTemplateTest
// proves every subset of a reachable template's walls is itself reachable. `template`
// takes the live grid dimensions rather than being a precomputed List, so this stays
// correct if GridConfig's size ever changes.
case class TemplateStrategy(template: (Int, Int) => List[(Int, Int)]) extends AiStrategy:

  // Grove first, unlike LinearStrategy's pure cost order: Grove is Nature's only
  // directly-buildable tier (Forest/Jungle are upgrade-only — see Placement's
  // buildableDirectly check), and Nature is the only faction with a combat aura
  // (CombatEngine.applyDamageSources/auraBuildingKinds), so a wall built from Groves —
  // which maybeUpgrade below then grows into Forests — doesn't just lengthen the enemy's
  // path the way any other kind would, it eventually kills them outright. The same reason
  // CompositeStrategy's maze-only always favors an aura kind (see its dangerScore doc),
  // and why an early version of this strategy, which reused LinearStrategy's
  // expensive-first order verbatim, lost 40-0 to maze-only in `make sim`: it was building
  // whatever Church/Labyrinth/Watchtower it could afford on the comb's cells instead,
  // forcing a long walk but never actually killing anything.
  private val fallbackKinds: Seq[BuildingKind] =
    Seq(BuildingKind.Church, BuildingKind.Labyrinth, BuildingKind.Watchtower, BuildingKind.Cave)

  def maybeBuild(state: MazeState, opponent: MazeState): MazeState =
    val remaining = template(GridConfig.cols, GridConfig.rows).filterNot(state.buildingCells.contains)
    remaining.iterator
      .flatMap { case (col, row) =>
        Placement.tryPlaceBuilding(state, BuildingKind.Grove, col, row).toOption.orElse(bestFallback(state, col, row))
      }
      .nextOption()
      .getOrElse(state)

  // Chosen by affordability margin instead of a fixed try-order (old order was
  // Church > Labyrinth > Watchtower > Cave, so a Church that was merely affordable — not
  // necessarily a *good* spend — always won a tie against a cheaper option with room to
  // spare). Every fallback kind still costs some wood (5 to 40 — see BuildingSpecs), and
  // Grove above already wins outright whenever wood >= 10 since it needs no other
  // resource, so in practice this competition can only ever be reached when wood < 10,
  // where only Cave (wood 5) is ever affordable among these four — the margin comparison
  // itself is real and unit-tested (see fallbackMarginScore), but current Balance numbers
  // never give it more than one candidate to choose between end-to-end. Documented
  // honestly rather than claimed as a bigger fix than it is — same as isMazeAuraCandidate's
  // "narrows, doesn't eliminate" framing in CompositeStrategy.
  private def bestFallback(state: MazeState, col: Int, row: Int): Option[MazeState] =
    fallbackKinds
      .flatMap(kind => Placement.tryPlaceBuilding(state, kind, col, row).toOption.map(result => (kind, result)))
      .maxByOption { case (kind, _) => fallbackMarginScore(state, kind) }
      .map(_._2)

  // Average affordability margin left over the currencies this kind consumes — higher
  // when the spend is a smaller fraction of what's on hand. Same raw math as
  // CompositeStrategy.resourceScore, minus its diminishing-returns divisor: that divisor
  // exists to stop resource-only spamming one kind over and over, but this strategy only
  // ever wants a single building per template cell, so there's no repeat-kind pattern to
  // discourage here. Exposed at `private[domain]` so the comparison is testable directly.
  private[domain] def fallbackMarginScore(state: MazeState, kind: BuildingKind): Double =
    val cost = BuildingSpecs.all(kind).cost
    val margins = cost.map { case (res, amount) =>
      val available = state.resources.getOrElse(res, 0.0)
      (available - amount) / available
    }
    margins.sum / margins.size

  // Grows an existing Grove into a Forest (and a Forest into a Jungle) once affordable —
  // see AiStrategy.upgradeAnyAffordable. Every building this strategy has is already on a
  // template cell (maybeBuild never places elsewhere), so upgrading any of them is always
  // upgrading the maze wall, no extra filtering needed.
  override def maybeUpgrade(state: MazeState, opponent: MazeState): MazeState =
    AiStrategy.upgradeAnyAffordable(state)
