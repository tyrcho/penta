package towerdefense.domain

// Dumbest possible opponent: as soon as it can afford a building, place one on the
// first buildable cell in row-major order. Deterministic (easy to test), no
// randomness — a reasonable POC default since the vault doesn't specify AI behavior.
// Both sides can build any directly-buildable BuildingKind — see CLAUDE.md, "the game
// is symmetric" — Forest/Jungle are reached only via maybeUpgrade, never listed here.
// Tried by descending wood cost (Stonehenge 150 > Church 20 = BlackCastle 20 >
// Labyrinth 10 = Watchtower 10 = DeathHouse 10 > Grove 5 = Tomb 5 = LaboNaturel 5 >
// Cave 0 = Angel 0 = the four remaining Labo* kinds 0): each one's wood cost dominates
// every cheaper building's, so trying it later would make it unreachable — by the time
// its wood cost is affordable, the cheaper buildings' wood costs always are too (their
// non-wood requirements are independent currencies and don't create the same trap). Angel
// costs only Light (no Wood at all, unlike Grove/Tomb/LaboNaturel's shared-Wood trap —
// see the NOTE below), so it sits in the zero-wood tier alongside Cave/the four Labo*
// kinds, ordered among them arbitrarily but stably. Cave joined this tier in the same
// rebalance that made Grove/Tomb/LaboNaturel tie at 5. Every same-wood-cost tie breaks by
// list position — arbitrary but stable. Kept as an explicit list, not derived by sorting
// BuildingSpecs at runtime — a re-derived sort risks silently flipping a tie with no test
// to catch it.
// NOTE: at the 5-wood tier, Grove's cost (Wood only) is now a strict subset of Tomb's
// (Wood + Shadow) and LaboNaturel's (Wood + Crystal) — so whenever Tomb or LaboNaturel is
// affordable, Grove always is too, and being first in that tier, Grove always wins. Under
// this strategy specifically, Tomb/LaboNaturel are effectively unreachable as long as any
// Wood income exists (see AiStrategyTest). Worth revisiting if LinearStrategy is meant to
// exercise Death/Science at all.
// Kept as the fixed baseline other AiStrategy implementations (e.g. CompositeStrategy)
// are measured against — it never reads `opponent`.
object LinearStrategy extends AiStrategy:

  private val buildOrder: Seq[BuildingKind] = Seq(
    BuildingKind.Stonehenge,
    BuildingKind.Church,
    BuildingKind.BlackCastle,
    BuildingKind.Labyrinth,
    BuildingKind.Watchtower,
    BuildingKind.DeathHouse,
    BuildingKind.Grove,
    BuildingKind.Tomb,
    BuildingKind.LaboNaturel,
    BuildingKind.Cave,
    BuildingKind.Angel,
    BuildingKind.LaboSombre,
    BuildingKind.LaboDeRecherche,
    BuildingKind.LaboDeLaLoi,
    BuildingKind.LaboDuChaos
  )

  def maybeBuild(state: MazeState, opponent: MazeState): MazeState =
    buildOrder.iterator.flatMap(kind => tryBuildOneOf(state, kind)).nextOption().getOrElse(state)

  // Upgrades the first (row-major, by building list order) Grove/Forest it can afford —
  // see AiStrategy.upgradeAnyAffordable, shared with every other strategy that upgrades.
  override def maybeUpgrade(state: MazeState, opponent: MazeState): MazeState =
    AiStrategy.upgradeAnyAffordable(state)

  // Researches the first affordable next level among owned Science labs — see
  // AiStrategy.researchAnyAffordable, shared with every other strategy.
  override def maybeResearch(state: MazeState, opponent: MazeState): MazeState =
    AiStrategy.researchAnyAffordable(state)

  private def tryBuildOneOf(state: MazeState, kind: BuildingKind): Option[MazeState] =
    GridConfig.allCells.iterator
      .map(c => Placement.tryPlaceBuilding(state, kind, c._1, c._2))
      .collectFirst { case Right(s) => s }
