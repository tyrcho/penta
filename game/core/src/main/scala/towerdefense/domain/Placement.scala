package towerdefense.domain

enum PlacementError derives CanEqual:
  case OutOfBounds, OnSpawnOrGoal, CellOccupied, WouldBlockPath, InsufficientResources,
    CannotBuildDirectly, NoBuildingThere, NoUpgradeAvailable, MaxCountReached,
    LabNotOwned, MaxResearchLevelReached

object Placement:

  // Cheap checks (buildableDirectly, bounds/spawn/occupied, affordability, max-count) run
  // before wouldBlockPath's full-grid BFS reachability scan — a strategy scanning every
  // (kind, cell) candidate each tick (see ComposedStrategy.allCandidates) rejects the vast
  // majority on cost or occupancy alone, so paying for a BFS on every single one of those
  // was pure waste. Correctness is unaffected (Either short-circuits on the first Left
  // either way); only which specific PlacementError comes back changes for a cell that
  // fails more than one check at once, and no caller (including every existing test)
  // distinguishes those beyond isLeft/toOption.
  def tryPlaceBuilding(
      state: MazeState,
      kind: BuildingKind,
      col: Int,
      row: Int
  ): Either[PlacementError, MazeState] =
    tryPlaceBuildingKnowingReachability(state, kind, col, row, wouldBlockPath(state, col, row))

  // wouldBlockPath only depends on the *cell* (state.buildingCells + this one candidate) —
  // never on which kind is being placed there — so a caller scanning many kinds against the
  // same cells (ComposedStrategy.allCandidates, LinearStrategy.tryBuildOneOf) can compute it
  // once per cell via nonBlockingCells below and reuse it across every kind, instead of
  // re-running the BFS once per (kind, cell) pair. Single source of truth for the actual
  // placement rules stays tryPlaceBuildingKnowingReachability; this and the plain
  // tryPlaceBuilding above are just two ways of supplying its `blocksPath` argument.
  def tryPlaceBuildingCached(
      state: MazeState,
      kind: BuildingKind,
      col: Int,
      row: Int,
      nonBlocking: Set[(Int, Int)]
  ): Either[PlacementError, MazeState] =
    tryPlaceBuildingKnowingReachability(state, kind, col, row, blocksPath = !nonBlocking.contains((col, row)))

  // Every cell where placing *something* wouldn't seal the only route from spawn to goal —
  // kind-independent, see tryPlaceBuildingCached's doc. Cheap checks (occupied, etc.) are
  // deliberately not folded in here: a cell already occupied is still "non-blocking" in the
  // sense that nothing new would obstruct the path there if it were somehow free, and
  // computing that once per cell (144 BFS calls total) is what makes tryPlaceBuildingCached
  // worth calling instead of tryPlaceBuilding for every kind separately.
  def nonBlockingCells(state: MazeState): Set[(Int, Int)] =
    GridConfig.allCells.filterNot((col, row) => wouldBlockPath(state, col, row)).toSet

  private def tryPlaceBuildingKnowingReachability(
      state: MazeState,
      kind: BuildingKind,
      col: Int,
      row: Int,
      blocksPath: Boolean
  ): Either[PlacementError, MazeState] =
    val spec = BuildingSpecs.all(kind)
    val cost = effectiveCost(state, spec.cost)
    for
      _ <- Either.cond(spec.buildableDirectly, (), PlacementError.CannotBuildDirectly)
      _ <- checkCellCheap(state, col, row)
      _ <- Either.cond(canAfford(state.resources, cost), (), PlacementError.InsufficientResources)
      _ <- checkMaxCount(state, kind, spec)
      _ <- Either.cond(!blocksPath, (), PlacementError.WouldBlockPath)
    yield placeBuilding(state, kind, cost, col, row)

  // Recherches naturelles.md: "Diminue le cout des batiments" — a flat % reduction on every
  // resource this maze's OWN Naturelles research level applies to, read from state's own
  // researchLevels (never the opponent's — this discounts what you build, not what they
  // do). Research costs themselves are exempt (see Balance.NaturellesCostReductionByLevel's
  // doc): callers computing a *research* cost use spec.costAtLevel directly, not this.
  private[domain] def effectiveCost(state: MazeState, baseCost: Map[Resource, Double]): Map[Resource, Double] =
    val level = state.researchLevels.getOrElse(BuildingKind.LaboNaturel, 0)
    if level <= 0 then baseCost
    else
      val reduction = ResearchSpecs.all(BuildingKind.LaboNaturel).effectAtLevel(level)
      baseCost.view.mapValues(_ * (1.0 - reduction)).toMap

  // Science's five labs cap at one each (Note sur les laboratoires.md: "Il n'est possible
  // de controler qu'un seul laboratoire de chaque type") — every other kind has
  // maxPerMaze = None and is unrestricted.
  private def checkMaxCount(state: MazeState, kind: BuildingKind, spec: BuildingSpec): Either[PlacementError, Unit] =
    spec.maxPerMaze match
      case Some(max) => Either.cond(state.buildings.count(_.kind == kind) < max, (), PlacementError.MaxCountReached)
      case None       => Right(())

  // Upgrades whatever building already sits at (col, row) to the next tier in
  // BuildingSpecs.upgradesTo (Grove -> Forest -> Jungle today) — the only way to reach a
  // kind with buildableDirectly = false. No reachability check like tryPlaceBuilding's:
  // the building already occupies that cell, so the maze's obstacle footprint doesn't
  // change shape, only which kind sits there.
  def tryUpgradeBuilding(state: MazeState, col: Int, row: Int): Either[PlacementError, MazeState] =
    for
      building <- state.buildings.find(b => b.col == col && b.row == row).toRight(PlacementError.NoBuildingThere)
      nextKind <- BuildingSpecs.upgradesTo.get(building.kind).toRight(PlacementError.NoUpgradeAvailable)
      cost = effectiveCost(state, BuildingSpecs.all(nextKind).cost)
      _ <- Either.cond(canAfford(state.resources, cost), (), PlacementError.InsufficientResources)
    yield upgradeBuilding(state, building, nextKind, cost)

  // Researches the next level of `labKind`'s line (ResearchSpecs.all) — requires actually
  // owning that lab (Recherches*.md: "debloque"/"permet de debloquer", read as "the
  // building unlocks its research", not "researchable from anywhere"), not yet at the max
  // level, and able to afford that level's doubling cost. Losing the lab afterward doesn't
  // undo an already-reached level (see MazeState.researchLevels' doc) — only reaching a
  // *new* level requires ownership at the moment of research.
  def tryResearch(state: MazeState, labKind: BuildingKind): Either[PlacementError, MazeState] =
    val spec = ResearchSpecs.all(labKind)
    val currentLevel = state.researchLevels.getOrElse(labKind, 0)
    val cost = spec.costAtLevel(currentLevel + 1)
    for
      _ <- Either.cond(state.buildings.exists(_.kind == labKind), (), PlacementError.LabNotOwned)
      _ <- Either.cond(currentLevel < Balance.MaxResearchLevel, (), PlacementError.MaxResearchLevelReached)
      _ <- Either.cond(canAfford(state.resources, cost), (), PlacementError.InsufficientResources)
    yield state.copy(
      resources = debit(state.resources, cost),
      researchLevels = state.researchLevels.updated(labKind, currentLevel + 1)
    )

  def canAfford(resources: Map[Resource, Double], cost: Map[Resource, Double]): Boolean =
    cost.forall { case (res, amount) => resources.getOrElse(res, 0.0) >= amount }

  // Bounds/spawn-goal/occupied only — the expensive wouldBlockPath BFS is checked
  // separately, last, in tryPlaceBuilding (see its doc).
  private def checkCellCheap(state: MazeState, col: Int, row: Int): Either[PlacementError, Unit] =
    if !GridConfig.isInBounds(col, row) then Left(PlacementError.OutOfBounds)
    else if Set(GridConfig.spawnCell, GridConfig.goalCell).contains((col, row)) then
      Left(PlacementError.OnSpawnOrGoal)
    else if state.buildingCells.contains((col, row)) then Left(PlacementError.CellOccupied)
    else Right(())

  private def wouldBlockPath(state: MazeState, col: Int, row: Int): Boolean =
    val blocked = state.buildingCells + ((col, row))
    !Pathfinding.isReachable(GridConfig.spawnCell, GridConfig.goalCell, blocked)

  private def placeBuilding(
      state: MazeState,
      kind: BuildingKind,
      cost: Map[Resource, Double],
      col: Int,
      row: Int
  ): MazeState =
    val spec = BuildingSpecs.all(kind)
    val building = Building(
      state.nextId,
      col,
      row,
      kind,
      spawnCountdownMs = spec.spawns.map(_._2).getOrElse(0.0)
    )
    state.copy(
      buildings = building :: state.buildings,
      resources = debit(state.resources, cost),
      nextId = state.nextId + 1
    )

  // Same id, same cell, new kind — the timer resets to the new tier's own interval
  // (simplest behavior: an upgrade doesn't inherit a partial countdown from the tier it
  // replaced, since the two tiers can spawn different units at different rates).
  private def upgradeBuilding(
      state: MazeState,
      building: Building,
      nextKind: BuildingKind,
      cost: Map[Resource, Double]
  ): MazeState =
    val spawnCountdownMs = BuildingSpecs.all(nextKind).spawns.map(_._2).getOrElse(0.0)
    val upgraded = building.copy(kind = nextKind, spawnCountdownMs = spawnCountdownMs)
    state.copy(
      buildings = upgraded :: state.buildings.filterNot(_.id == building.id),
      resources = debit(state.resources, cost)
    )

  private def debit(resources: Map[Resource, Double], cost: Map[Resource, Double]): Map[Resource, Double] =
    cost.foldLeft(resources) { case (acc, (res, amount)) =>
      acc.updated(res, acc.getOrElse(res, 0.0) - amount)
    }
