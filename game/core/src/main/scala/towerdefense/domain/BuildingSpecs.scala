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
// dps: passive/ranged damage per second dealt to enemy creatures (Forest/Jungle/Angel/
// PassingGate's adjacency aura, Watchtower's single-target range attack) — 0.0 (no combat
// ability) for every other kind. The actual targeting rule (adjacency vs nearest-in-range)
// stays a kind-based special case in CombatEngine (auraBuildingKinds/Watchtower branch);
// this field is just the per-kind magnitude, sourced from the same Balance constants
// CombatEngine reads, so a building's damage can't drift between the two.
// tier: 1-3, computed by BuildingSpecs.computedTiers below from each building's own cost
// relative to its faction's other buildings — not itself a hand-picked design value except
// for the three overrides (see tierOverrides) that came out of an explicit design pass.
case class BuildingSpec(
    cost: Map[Resource, Double],
    produces: Map[Resource, Double]=Map.empty, // rate per second
    spawns: Option[(UnitKind, Double)] = None, // (unit kind, interval ms) — None for Watchtower and the Science labs
    buildableDirectly: Boolean = true,
    maxPerMaze: Option[Int] = None,
    dps: Double = 0.0,
    tier: Int = 0
)

object BuildingSpecs:
  // Kept private and un-tiered — `all` below is what every other module reads; this is
  // just the literal per-kind data tier computation is derived from.
  private val baseSpecs: Map[BuildingKind, BuildingSpec] = Map(
    BuildingKind.Grove -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.GroveCostWood),
      produces = Map(Resource.Wood -> Balance.WoodPerSecPerGrove),
      spawns = Some(UnitKind.Elf -> Balance.ElfSpawnIntervalMs)
    ),
    BuildingKind.Forest -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.ForestUpgradeCostWood),
      produces = Map(Resource.Wood -> Balance.WoodPerSecPerForest),
      spawns = Some(UnitKind.Elf -> Balance.ElfSpawnIntervalMs),
      buildableDirectly = false,
      dps = Balance.AuraDamagePerSec
    ),
    BuildingKind.Jungle -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.JungleUpgradeCostWood),
      produces = Map(Resource.Wood -> Balance.WoodPerSecPerJungle),
      spawns = Some(UnitKind.Wolf -> Balance.WolfSpawnIntervalMs),
      buildableDirectly = false,
      dps = Balance.AuraDamagePerSec // inherited from Forest — see Balance.AuraDamagePerSec's doc
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
    // Antre du Dragon.md: Chaos's tier-3 building — no resource production at all, its
    // value is purely the glass-cannon Dragon it spawns (see CreatureSpecs).
    BuildingKind.DragonsLair -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.DragonsLairCostWood, Resource.Fire -> Balance.DragonsLairCostFire),
      produces = Map.empty,
      spawns = Some(UnitKind.Dragon -> Balance.DragonSpawnIntervalMs)
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
      spawns = None,
      dps = Balance.WatchtowerDamagePerSec
    ),
    BuildingKind.Angel -> BuildingSpec(
      cost = Map(Resource.Light -> Balance.AngelCostLight),
      produces = Map(Resource.Light -> Balance.LightPerSecPerAngel),
      spawns = None,
      dps = Balance.AngelDamagePerSec
    ),
    // Caserne.md: Loi's tier-1 building — a genuinely cheap entry point, below Church's
    // own price, spawning the cheap/fragile Soldat (see CreatureSpecs).
    BuildingKind.Barracks -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.BarracksCostWood, Resource.Light -> Balance.BarracksCostLight),
      produces = Map(Resource.Light -> Balance.LightPerSecPerBarracks),
      spawns = Some(UnitKind.Soldier -> Balance.SoldierSpawnIntervalMs)
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
      spawns = None,
      dps = Balance.PassingGateDamagePerSec
    ),
    // Note sur les laboratoires.md: the only Science kind placed fresh — see Balance's doc.
    // No maxPerMaze: unlike the five specific kinds below, a maze can run several of these
    // side by side, each free to specialize into a *different* one (see upgradeOptions).
    BuildingKind.LaboFondamental -> BuildingSpec(
      cost = Map(Resource.Crystal -> Balance.LaboFondamentalCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboFondamental),
      spawns = None
    ),
    // Champ de Stase.md: Science's tier-2 building — no unit spawn, produces more Crystal
    // than LaboFondamental, and slows adjacent enemy creatures (see CombatEngine's
    // stasisCells handling in effectiveSpeedPerMs) — a separate, additive building, not
    // part of the LaboFondamental upgrade chain.
    BuildingKind.StasisField -> BuildingSpec(
      cost = Map(Resource.Crystal -> Balance.StasisFieldCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerStasisField),
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

  // The inverse of upgradeOptions — every upgrade target's single source (Forest -> Grove,
  // each specific lab -> LaboFondamental) — used by DocGenerator's "upgrade of" frontmatter
  // line. Absent for every kind with no upgrade source (every buildableDirectly = true kind).
  val upgradeFrom: Map[BuildingKind, BuildingKind] =
    upgradeOptions.flatMap { case (source, targets) => targets.map(_ -> source) }

  // Per-faction groups this generator's tier ranking is computed within — literal lists
  // (not derived from EntityNames.buildingInfo(_).faction) because EntityNames' i18n
  // package itself imports this domain package; reaching back the other way would be a
  // cycle. Mirrors the grouping `baseSpecs`' own "── Faction ──" comments already show.
  private val costRankGroups: List[List[BuildingKind]] = List(
    List(BuildingKind.Grove, BuildingKind.Forest, BuildingKind.Jungle, BuildingKind.Stonehenge),
    List(BuildingKind.Cave, BuildingKind.Labyrinth, BuildingKind.DragonsLair),
    List(BuildingKind.Church, BuildingKind.Watchtower, BuildingKind.Angel, BuildingKind.Barracks),
    List(BuildingKind.Tomb, BuildingKind.BlackCastle, BuildingKind.DeathHouse, BuildingKind.PassingGate),
    List(
      BuildingKind.LaboFondamental,
      BuildingKind.StasisField,
      BuildingKind.LaboNaturel,
      BuildingKind.LaboSombre,
      BuildingKind.LaboDeRecherche,
      BuildingKind.LaboDeLaLoi,
      BuildingKind.LaboDuChaos
    )
  )

  // No design doc tiers every building (see DocGenerator/CLAUDE.md's symmetry rule and the
  // TODO.md design-pass note this reads from) — only Dragon's Lair/Barracks/Stasis Field
  // were explicitly tiered when they were added. Every other kind's tier is instead derived
  // here from its own total cost, ranked against the other buildings in its
  // costRankGroups entry: within a faction group, distinct cost values are sorted
  // ascending and split into (up to) 3 buckets, so the cheapest building(s) land in tier 1
  // and the priciest in tier 3 — buildings sharing the exact same cost always land in the
  // same tier. This is a proxy for design intent, not the intent itself.
  private val tierOverrides: Map[BuildingKind, Int] =
    Map(BuildingKind.DragonsLair -> 3, BuildingKind.Barracks -> 1, BuildingKind.StasisField -> 2)

  private def totalCost(kind: BuildingKind): Double = baseSpecs(kind).cost.values.sum

  private val computedTiers: Map[BuildingKind, Int] = costRankGroups.flatMap { group =>
    val distinctCostsAscending = group.map(totalCost).distinct.sorted
    val bucketCount = distinctCostsAscending.size
    group.map { kind =>
      val rank = distinctCostsAscending.indexOf(totalCost(kind)) + 1
      kind -> math.min(3, math.ceil(rank * 3.0 / bucketCount).toInt)
    }
  }.toMap

  val all: Map[BuildingKind, BuildingSpec] =
    baseSpecs.map { case (kind, spec) => kind -> spec.copy(tier = tierOverrides.getOrElse(kind, computedTiers(kind))) }
