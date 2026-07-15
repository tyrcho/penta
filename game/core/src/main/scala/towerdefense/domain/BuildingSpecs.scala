package towerdefense.domain

// What a building costs, what it produces (per second), and what unit it spawns (and
// how often) — the data-driven replacement for the old per-faction case classes. Combat
// abilities (Forest's aura, Watchtower's ranged damage) are NOT modeled here — they stay
// as kind-based special cases in CombatEngine, reading Balance's constants directly.
case class BuildingSpec(
    cost: Map[Resource, Double],
    produces: Map[Resource, Double], // rate per second
    spawns: Option[(UnitKind, Double)] // (unit kind, interval ms) — None only for Watchtower
)

object BuildingSpecs:
  val all: Map[BuildingKind, BuildingSpec] = Map(
    BuildingKind.Forest -> BuildingSpec(
      cost = Map(Resource.Wood -> Balance.ForestCostWood),
      produces = Map(Resource.Wood -> Balance.WoodPerSecPerForest),
      spawns = Some(UnitKind.Elf -> Balance.ElfSpawnIntervalMs)
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
