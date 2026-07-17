package towerdefense.domain

// What a building costs, what it produces (per second), and what unit it spawns (and
// how often) — the data-driven replacement for the old per-faction case classes. Combat
// abilities (Forest/Jungle's aura, Watchtower's ranged damage, Wolf's speed buff) are NOT
// modeled here — they stay as kind-based special cases in CombatEngine, reading Balance's
// constants directly.
// buildableDirectly: false for Forest/Jungle — Nature's upgrade chain (Bosquet.md/
// Foret.md/Jungle.md) only lets Grove be placed from scratch; Forest and Jungle are
// reached by upgrading an existing Grove/Forest via Placement.tryUpgradeBuilding, using
// `cost` here as the upgrade's cost, not a from-scratch price.
// maxPerMaze: Some(1) for the five Science labs (Note sur les laboratoires.md: "Il n'est
// possible de controler qu'un seul laboratoire de chaque type") — every other kind is
// unlimited (None), see Placement.checkMaxCount.
//
// Deliberately NOT modeled here yet: Science's leveled research tree (5 levels/lab,
// doubling cost per level — Recherches*.md/Recherche fondamentale.md) and its global
// modifiers (building cost reduction, building damage boost, plunder efficiency boost,
// opponent victory-target increase) or Science's own victory condition. Those touch
// every other faction's numbers (a genuine architecture change — new per-maze research-
// level state, cross-cutting modifiers threaded through Balance/CombatEngine/Placement/
// VictoryConditions) rather than being new data rows like a building/unit is. The vault's
// spec for the fundamental-research victory condition is well-defined once read as its
// own 5-level research (Recherche fondamentale.md's numbered list is per-level, not per-
// lab): level N requires all 4 other labs at level (6-N) or higher — level 1 needs every
// other lab at 5, level 5 needs them only at 1+, trading fondamentale's own doubling cost
// against the other labs'. This is a scope decision, not an ambiguity: deferred by choice
// to a follow-up rather than built alongside Death this session. Labs are wired up here
// only as Crystal producers, same deliberate-gap treatment CLAUDE.md/README already give
// Loi's unwired victory condition.
case class BuildingSpec(
    cost: Map[Resource, Double],
    produces: Map[Resource, Double], // rate per second
    spawns: Option[(UnitKind, Double)], // (unit kind, interval ms) — None for Watchtower and the Science labs
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
    BuildingKind.LaboNaturel -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.LaboNaturelCostWood, Resource.Crystal -> Balance.LaboNaturelCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboNaturel),
      spawns = None,
      maxPerMaze = Some(1)
    ),
    BuildingKind.LaboSombre -> BuildingSpec(
      cost = Map(Resource.Shadow -> Balance.LaboSombreCostShadow, Resource.Crystal -> Balance.LaboSombreCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboSombre),
      spawns = None,
      maxPerMaze = Some(1)
    ),
    BuildingKind.LaboDeRecherche -> BuildingSpec(
      cost = Map(Resource.Crystal -> Balance.LaboDeRechercheCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboDeRecherche),
      spawns = None,
      maxPerMaze = Some(1)
    ),
    BuildingKind.LaboDeLaLoi -> BuildingSpec(
      cost = Map(Resource.Light -> Balance.LaboDeLaLoiCostLight, Resource.Crystal -> Balance.LaboDeLaLoiCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboDeLaLoi),
      spawns = None,
      maxPerMaze = Some(1)
    ),
    BuildingKind.LaboDuChaos -> BuildingSpec(
      cost = Map(Resource.Fire -> Balance.LaboDuChaosCostFire, Resource.Crystal -> Balance.LaboDuChaosCostCrystal),
      produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboDuChaos),
      spawns = None,
      maxPerMaze = Some(1)
    )
  )

  // Grove -> Forest -> Jungle. Absent for every other kind (no upgrade path).
  val upgradesTo: Map[BuildingKind, BuildingKind] = Map(
    BuildingKind.Grove -> BuildingKind.Forest,
    BuildingKind.Forest -> BuildingKind.Jungle
  )
