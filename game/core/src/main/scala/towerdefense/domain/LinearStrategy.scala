package towerdefense.domain

// Dumbest possible opponent: as soon as it can afford a building, place one on the
// first buildable cell in row-major order. Deterministic (easy to test), no
// randomness — a reasonable POC default since the vault doesn't specify AI behavior.
// Both sides can build any directly-buildable BuildingKind — see CLAUDE.md, "the game
// is symmetric" — Forest/Jungle are reached only via maybeUpgrade, never listed here.
// Tried by descending wood cost (Church 40 > Labyrinth 20 = BlackCastle 20 > Watchtower
// 10 = Grove 10 > Cave 5 = Tomb 5 = LaboNaturel 5 > the four remaining Labo* kinds 0):
// each one's wood cost dominates every cheaper building's, so trying it later would make
// it unreachable — by the time its wood cost is affordable, the cheaper buildings' wood
// costs always are too (their non-wood requirements are independent currencies and don't
// create the same trap). The four zero-wood Science labs (Sombre/de Recherche/de la Loi/
// du Chaos) sit at the very end for the same reason Cave/Tomb/LaboNaturel sit above them:
// their own wood cost (0) is dominated by everything above, so they can never create a
// wood-trap either way — their relative order among themselves is arbitrary. Every same-
// wood-cost tie (Labyrinth/BlackCastle, Watchtower/Grove, Cave/Tomb/LaboNaturel) breaks by
// list position — arbitrary but stable. Kept as an explicit list, not derived by sorting
// BuildingSpecs at runtime — a re-derived sort risks silently flipping a tie with no test
// to catch it.
// Kept as the fixed baseline other AiStrategy implementations (e.g. CompositeStrategy)
// are measured against — it never reads `opponent`.
object LinearStrategy extends AiStrategy:

  private val buildOrder: Seq[BuildingKind] = Seq(
    BuildingKind.Church,
    BuildingKind.Labyrinth,
    BuildingKind.BlackCastle,
    BuildingKind.Watchtower,
    BuildingKind.Grove,
    BuildingKind.Cave,
    BuildingKind.Tomb,
    BuildingKind.LaboNaturel,
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

  private def tryBuildOneOf(state: MazeState, kind: BuildingKind): Option[MazeState] =
    GridConfig.allCells.iterator
      .map(c => Placement.tryPlaceBuilding(state, kind, c._1, c._2))
      .collectFirst { case Right(s) => s }
