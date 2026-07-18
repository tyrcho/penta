package towerdefense.domain

// The "which building kind" half of an AiStrategy — how resources get spent, independent
// of where the result goes (that's LayoutPolicy's job). ComposedStrategy blends a
// SpendingPolicy's score with a LayoutPolicy's score per (kind, cell) candidate.
trait SpendingPolicy:
  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double

object SpendingPolicy:

  // Average affordability margin left over the currencies this kind consumes, penalized
  // per currency that has no active producer on the board yet (see marginFor) — moved
  // from CompositeStrategy.resourceScore's raw component, now growth-aware.
  private[domain] def rawMargin(state: MazeState, kind: BuildingKind): Double =
    val cost = BuildingSpecs.all(kind).cost
    val margins = cost.map { case (res, amount) => marginFor(state, res, amount) }
    margins.sum / margins.size

  // Plain affordability margin ((available - amount) / available) once this resource has
  // some ongoing production — spending it down is safe, it'll refill. Without any active
  // producer, the spend is a one-way trip: nothing on the board will ever bring this
  // currency back up, so a spend that consumes a big fraction of the remaining stock (a
  // small fraction still available afterward) risks locking every future building that
  // needs this resource out permanently. The penalty term (amount / available, i.e. the
  // fraction of the current stock this spend consumes) grows toward 1.0 as the spend
  // approaches the entire stock, and is 0 when the spend is negligible relative to what's
  // on hand.
  private def marginFor(state: MazeState, res: Resource, amount: Double): Double =
    val available = state.resources.getOrElse(res, 0.0)
    val plainMargin = (available - amount) / available
    val rate = CombatEngine.productionPerSec(state, res)
    if rate > 0.0 then plainMargin else plainMargin - amount / available

  // rawMargin's penalty alone isn't enough to avoid a lockout: it discourages *spending*
  // a no-production resource, but a kind that costs that same resource without producing
  // it (e.g. Watchtower, which shares Grove's Wood cost) still often out-scores the kind
  // that would actually fix the shortage (Grove, Wood's only producer) — Watchtower's
  // margin gets pulled up by averaging in an abundant currency (Light) it also spends,
  // while Grove's lone Wood term has nothing to average against and sinks with it. This
  // flat bonus — one point per currently-unproduced resource `kind` would start producing
  // — directly credits *fixing* the shortage, not just avoiding worsening it, so Grove
  // wins that comparison once Wood production has actually hit zero.
  private def growthBonus(state: MazeState, kind: BuildingKind): Double =
    BuildingSpecs.all(kind).produces.keySet.count(res => CombatEngine.productionPerSec(state, res) == 0.0).toDouble

  // Divides rawMargin (plus the growth bonus) by one plus how many of that kind are
  // already built — see CompositeStrategy's original doc: without this, a pure
  // resource-margin strategy locks onto whichever single kind has the best margin and
  // never reconsiders, since margins barely move build to build. `1 + count` keeps the
  // first building of any kind scored at its full raw margin, only discounting repeats.
  private[domain] def resourceScore(state: MazeState, kind: BuildingKind): Double =
    val existingCount = state.buildings.count(_.kind == kind)
    (rawMargin(state, kind) + growthBonus(state, kind)) / (1.0 + existingCount)

  // Mirrors whichever of Nature (Grove/Forest/Jungle), Chaos (Cave/Labyrinth), or Mort
  // (Tomb/BlackCastle) the opponent invests in more — moved verbatim from
  // CompositeStrategy.counterScore, now with Mort added alongside the original two once
  // its own victory condition (buildingsCorrupted) existed to counter. Loi (Church/
  // Watchtower) never scores here: it feeds no VictoryConditions target (still a genuinely
  // unwired gap — see BuildingSpecs' doc). Science (the five Labo* kinds) is also excluded,
  // even though Recherche fondamentale IS a real victory condition now: countering it means
  // researching your OWN labs' levels, not building more copies of a maxPerMaze: Some(1)
  // building, so it doesn't fit this "build more of what they're building" heuristic.
  private val natureBuildingKinds: Set[BuildingKind] =
    Set(BuildingKind.Grove, BuildingKind.Forest, BuildingKind.Jungle, BuildingKind.Stonehenge)
  private val chaosBuildingKinds: Set[BuildingKind] =
    Set(BuildingKind.Cave, BuildingKind.Labyrinth)
  private val mortBuildingKinds: Set[BuildingKind] =
    Set(BuildingKind.Tomb, BuildingKind.BlackCastle, BuildingKind.DeathHouse)

  private[domain] def counterScore(opponent: MazeState, kind: BuildingKind): Double =
    val natureCount = opponent.buildings.count(b => natureBuildingKinds.contains(b.kind))
    val chaosCount = opponent.buildings.count(b => chaosBuildingKinds.contains(b.kind))
    val mortCount = opponent.buildings.count(b => mortBuildingKinds.contains(b.kind))
    val leaderCount = natureCount.max(chaosCount).max(mortCount)
    val ownFactionCount =
      if natureBuildingKinds.contains(kind) then Some(natureCount)
      else if chaosBuildingKinds.contains(kind) then Some(chaosCount)
      else if mortBuildingKinds.contains(kind) then Some(mortCount)
      else None
    if ownFactionCount.contains(leaderCount) then 1.0 else 0.0

// Blends resourceScore and counterScore — subsumes today's resource-only (1,0),
// counter-only (0,1), and balanced (1,1) spending halves.
case class WeightedSpending(resourceWeight: Double, counterWeight: Double) extends SpendingPolicy:
  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    resourceWeight * SpendingPolicy.resourceScore(state, kind) +
      counterWeight * SpendingPolicy.counterScore(opponent, kind)

// Always favors Chaos (Cave/Labyrinth) regardless of the opponent's own faction mix,
// racing the Chaos/plunder victory condition instead of reacting to what the opponent
// builds the way WeightedSpending's counter term does. Ties among Chaos kinds (and among
// non-Chaos fallbacks, if neither Cave nor Labyrinth is affordable) break by margin.
case object PlunderSpending extends SpendingPolicy:
  private val chaosKinds = Set(BuildingKind.Cave, BuildingKind.Labyrinth)

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    (if chaosKinds.contains(kind) then 1.0 else 0.0) + 0.25 * SpendingPolicy.resourceScore(state, kind)

// Always favors Mort (Tomb/BlackCastle) regardless of the opponent's own faction mix,
// racing the Mort/corruption victory condition the same way PlunderSpending races Chaos's.
case object CorruptionSpending extends SpendingPolicy:
  private val mortKinds = Set(BuildingKind.Tomb, BuildingKind.BlackCastle, BuildingKind.DeathHouse)

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    (if mortKinds.contains(kind) then 1.0 else 0.0) + 0.25 * SpendingPolicy.resourceScore(state, kind)

// Prefers Grove outright (Nature's only directly-buildable tier — see BuildingSpecs.
// buildableDirectly) whenever it's a candidate at all, falling back to plain affordability
// margin among the rest otherwise — moved from TemplateStrategy's Grove-first/
// margin-fallback behavior. The 1000.0 constant is arbitrary but must dominate every
// realistic rawMargin value (bounded well below that) so a LayoutPolicy's own score is
// still what breaks ties among several affordable Grove cells.
case object GrovePriority extends SpendingPolicy:
  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    if kind == BuildingKind.Grove then 1000.0 else SpendingPolicy.rawMargin(state, kind)

// Fixed try-order, ignoring both affordability margin and the opponent — same idea as
// LinearStrategy's buildOrder, made pluggable. Earlier entries score higher; a kind absent
// from `order` scores below every listed kind.
case class FixedOrderSpending(order: Seq[BuildingKind]) extends SpendingPolicy:
  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    val idx = order.indexOf(kind)
    if idx < 0 then -1.0 else (order.size - idx).toDouble
