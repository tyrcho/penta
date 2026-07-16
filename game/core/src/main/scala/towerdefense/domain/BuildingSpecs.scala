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
case class BuildingSpec(
    cost: Map[Resource, Double],
    produces: Map[Resource, Double], // rate per second
    spawns: Option[(UnitKind, Double)], // (unit kind, interval ms) — None only for Watchtower
    buildableDirectly: Boolean = true
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
    )
  )

  // Grove -> Forest -> Jungle. Absent for every other kind (no upgrade path).
  val upgradesTo: Map[BuildingKind, BuildingKind] = Map(
    BuildingKind.Grove -> BuildingKind.Forest,
    BuildingKind.Forest -> BuildingKind.Jungle
  )
