package towerdefense.domain

// What a building costs, what it produces (per second), and what unit it spawns (and
// how often) — the data-driven replacement for the old per-faction case classes. Combat
// abilities (Forest/Jungle/Angel/PassingGate's aura, Watchtower's ranged damage, Wolf's
// speed buff) are NOT modeled here — they stay as kind-based special cases in CombatEngine,
// reading Balance's constants directly.
// buildableDirectly: false for Forest/Jungle — Nature's upgrade chain (Bosquet.md/
// Foret.md/Jungle.md) only lets Grove be placed from scratch; Forest and Jungle are
// reached by upgrading an existing Grove/Forest via Placement.tryUpgradeBuilding, using
// `cost` here as the upgrade's cost, not a from-scratch price. Also false for all five
// specific Science labs (LaboNaturel/Sombre/DeRecherche/DeLaLoi/DuChaos) — only
// LaboFondamental is placed from scratch; the five are reached by upgrading one (see
// upgradeOptions), same shape as Nature's chain but with 5 possible targets from a single
// source instead of 1.
// maxPerMaze: Some(1) for the five specific Science labs (Note sur les laboratoires.md:
// "Il n'est possible de controler qu'un seul laboratoire de chaque type") — every other
// kind, including LaboFondamental itself, is unlimited (None), see Placement.checkMaxCount.
//
// Science's leveled research tree (5 levels/lab, tripling cost per level — Recherches*.md/
// Recherche fondamentale.md), its global modifiers (building cost reduction, building
// damage boost, plunder efficiency boost, opponent victory-target increase), and its own
// victory condition are all implemented — see Placement.tryResearch, ResearchSpecs, and
// VictoryConditions.hasWonViaFondamentale/fondamentaleLevel/fondamentaleReadyLabCount.
// Labs are wired up here only as Crystal producers; the research-level state itself lives
// on MazeState.researchLevels, not in this per-building-kind spec table. Loi's own victory
// condition ("Paix Eternelle" — win by building count at a turn-count deadline) remains
// genuinely unwired, since it needs a "number of turns"/time-limit concept this real-time
// game doesn't have anywhere yet (see CLAUDE.md/README's note on that gap).
case class BuildingSpec(
    cost: Map[Resource, Double],
    produces: Map[Resource, Double]=Map.empty, // rate per second
    spawns: Option[(UnitKind, Double)] = None, // (unit kind, interval ms) — None for Watchtower and the Science labs
    buildableDirectly: Boolean = true,
    maxPerMaze: Option[Int] = None
)

object BuildingSpecs:
  val all: Map[BuildingKind, BuildingSpec] = Map(
    BuildingKind.Grove -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.GroveCostWood),
      produces = Map(Resource.Wood -> Balance.WoodPerSecPerGrove),
      spawns = Some(UnitKind.Elf -> Balance.ElfSpawnIntervalMs)
    ),
    BuildingKind.Forest -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.ForestUpgradeCostWood),
      produces = Map(Resource.Wood -> Balance.WoodPerSecPerForest),
      spawns = Some(UnitKind.Elf -> Balance.ElfSpawnIntervalMs),
      buildableDirectly = false
    ),
    BuildingKind.Jungle -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.JungleUpgradeCostWood),
      produces = Map(Resource.Wood -> Balance.WoodPerSecPerJungle),
      spawns = Some(UnitKind.Wolf -> Balance.WolfSpawnIntervalMs),
      buildableDirectly = false
    ),
    BuildingKind.Stonehenge -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.StonehengeCostWood),
      produces = Map.empty,
      spawns = Some(UnitKind.Tree -> Balance.StonehengeSpawnIntervalMs)
    ),
    BuildingKind.Cave -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.CaveCostWood, Resource.Fire -> Balance.CaveCostFire),
      produces = Map(Resource.Fire -> Balance.FirePerSecPerCave),
      spawns = Some(UnitKind.Goblin -> Balance.GoblinSpawnIntervalMs)
    ),
    BuildingKind.Labyrinth -> BuildingSpec(
      cost = Map(
        Resource.Wood -> Balance.LabyrintheCostWood,
        Resource.Fire -> Balance.LabyrintheCostFire
      ),
      produces = Map.empty,
      spawns = Some(UnitKind.Minotaur -> Balance.MinotaurSpawnIntervalMs)
    ),
    BuildingKind.Church -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.EgliseCostWood, Resource.Light -> Balance.EgliseCostLight),
      produces = Map(Resource.Light -> Balance.LightPerSecPerEglise),
      spawns = Some(UnitKind.Paladin -> Balance.PaladinSpawnIntervalMs)
    ),
    BuildingKind.Watchtower -> BuildingSpec(
      cost = Map(
        Resource.Wood -> Balance.WatchtowerCostWood,
        Resource.Light -> Balance.WatchtowerCostLight
      ),
      produces = Map(Resource.Light -> Balance.LightPerSecPerWatchtower),
      spawns = None
    ),
    BuildingKind.Angel -> BuildingSpec(
      cost = Map(Resource.Light -> Balance.AngelCostLight),
      produces = Map(Resource.Light -> Balance.LightPerSecPerAngel),
      spawns = None
    ),
    BuildingKind.Tomb -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.TombCostWood, Resource.Shadow -> Balance.TombCostShadow),
      produces = Map(Resource.Shadow -> Balance.ShadowPerSecPerTomb),
      spawns = Some(UnitKind.Zombie -> Balance.ZombieSpawnIntervalMs)
    ),
    BuildingKind.BlackCastle -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.BlackCastleCostWood, Resource.Shadow -> Balance.BlackCastleCostShadow),
      produces = Map(Resource.Shadow -> Balance.ShadowPerSecPerBlackCastle),
      spawns = Some(UnitKind.Vampire -> Balance.VampireSpawnIntervalMs)
    ),
    BuildingKind.DeathHouse -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.DeathHouseCostWood, Resource.Shadow -> Balance.DeathHouseCostShadow),
      produces = Map(Resource.Shadow -> Balance.ShadowPerSecPerDeathHouse),
      spawns = Some(UnitKind.Necromancer -> Balance.NecromancerSpawnIntervalMs)
    ),
    // Portail.md: no unit spawn, no passive production either — its value is entirely the
    // aura damage + death-harvest combat ability in CombatEngine (see auraBuildingKinds/
    // applyPassingGateHarvest), same "combat abilities stay out of this data table" split
    // as Forest/Jungle/Angel's aura and Watchtower's ranged damage.
    BuildingKind.PassingGate -> BuildingSpec(
      cost = Map(Resource.Shadow -> Balance.PassingGateCostShadow, Resource.Light -> Balance.PassingGateCostLight),
      produces = Map.empty,
      spawns = None
    ),
    // Note sur les laboratoires.md: the only Science kind placed fresh — see Balance's doc.
    // No maxPerMaze: unlike the five specific kinds below, a maze can run several of these
    // side by side, each free to specialize into a *different* one (see upgradeOptions).
    BuildingKind.LaboFondamental -> BuildingSpec(
      cost = Map(Resource.Crystal -> Balance.LaboFondamentalCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboFondamental),
      spawns = None
    ),
    // buildableDirectly = false for all five: reached only by upgrading a LaboFondamental
    // (see upgradeOptions/Placement.tryUpgradeBuilding), never placed from scratch.
    BuildingKind.LaboNaturel -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.LaboNaturelCostWood, Resource.Crystal -> Balance.LaboNaturelCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboNaturel),
      spawns = None,
      buildableDirectly = false,
      maxPerMaze = Some(1)
    ),
    BuildingKind.LaboSombre -> BuildingSpec(
      cost = Map(Resource.Shadow -> Balance.LaboSombreCostShadow, Resource.Crystal -> Balance.LaboSombreCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboSombre),
      spawns = None,
      buildableDirectly = false,
      maxPerMaze = Some(1)
    ),
    BuildingKind.LaboDeRecherche -> BuildingSpec(
      cost = Map(Resource.Crystal -> Balance.LaboDeRechercheCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboDeRecherche),
      spawns = None,
      buildableDirectly = false,
      maxPerMaze = Some(1)
    ),
    BuildingKind.LaboDeLaLoi -> BuildingSpec(
      cost = Map(Resource.Light -> Balance.LaboDeLaLoiCostLight, Resource.Crystal -> Balance.LaboDeLaLoiCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboDeLaLoi),
      spawns = None,
      buildableDirectly = false,
      maxPerMaze = Some(1)
    ),
    BuildingKind.LaboDuChaos -> BuildingSpec(
      cost = Map(Resource.Fire -> Balance.LaboDuChaosCostFire, Resource.Crystal -> Balance.LaboDuChaosCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboDuChaos),
      spawns = None,
      buildableDirectly = false,
      maxPerMaze = Some(1)
    )
  )

  // Grove -> Forest -> Jungle (a single-option chain), and LaboFondamental -> one of the
  // five specific labs (a 5-option branch, the first source with more than one target) —
  // see Placement.tryUpgradeBuilding for how a caller picks among several. Absent for every
  // other kind (no upgrade path at all).
  val upgradeOptions: Map[BuildingKind, List[BuildingKind]] = Map(
    BuildingKind.Grove -> List(BuildingKind.Forest),
    BuildingKind.Forest -> List(BuildingKind.Jungle),
    BuildingKind.LaboFondamental -> List(
      BuildingKind.LaboNaturel,
      BuildingKind.LaboSombre,
      BuildingKind.LaboDeRecherche,
      BuildingKind.LaboDeLaLoi,
      BuildingKind.LaboDuChaos
    )
  )
