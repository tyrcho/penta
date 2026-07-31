package towerdefense.domain

import towerdefense.domain.economy.*

// What a building costs, what it produces (per second), and what unit it spawns (and how
// often) — literal per-case fields, the data-driven replacement for the old separate
// old BuildingSpecs.all Map (now slimmed to just the tier derivation). Combat abilities
// (Forest/Jungle/Angel/PassingGate's aura, Watchtower's ranged damage, Wolf's speed buff)
// are NOT modeled here — they stay as kind-based special cases in CombatEngine, reading
// Balance's constants directly.
// buildableDirectly: false for Forest/Jungle — Nature's upgrade chain (Bosquet.md/
// Foret.md/Jungle.md) only lets Grove be placed from scratch; Forest and Jungle are
// reached by upgrading an existing Grove/Forest via Placement.tryUpgradeBuilding, using
// `cost` here as the upgrade's cost, not a from-scratch price. Also false for all five
// specific Science labs (LaboNaturel/Sombre/DeRecherche/DeLaLoi/DuChaos) — only
// LaboFondamental is placed from scratch; the five are reached by upgrading one (see
// `upgradeFrom` below), same shape as Nature's chain but with 5 possible targets from a
// single source instead of 1.
// maxPerMaze: Some(1) for the five specific Science labs (Note sur les laboratoires.md:
// "Il n'est possible de controler qu'un seul laboratoire de chaque type") — every other
// kind, including LaboFondamental itself, is unlimited (None), see Placement.checkMaxCount.
//
// Science's leveled research tree (5 levels/lab, tripling cost per level — Recherches*.md/
// Recherche fondamentale.md), its global modifiers (building cost reduction, building
// damage boost, plunder efficiency boost, opponent victory-target increase), and its own
// victory condition are all implemented — see Placement.tryResearch, `researchSpec` below,
// and VictoryConditions.hasWonViaFondamentale/fondamentaleLevel/fondamentaleReadyLabCount.
// Labs are wired up here only as Crystal producers; the research-level state itself lives
// on MazeState.researchLevels, not on this enum. Loi's own victory condition ("Paix
// Eternelle" — win by building count at a turn-count deadline) is wired too, via
// BattleState.elapsedTicks and VictoryConditions.hasWonViaLoi.
// dps: passive/ranged damage per second dealt to enemy creatures (Forest/Jungle/Angel/
// PassingGate's adjacency aura, Watchtower's single-target range attack) — 0.0 (the
// enum-level default) for every other kind. The actual targeting rule (adjacency vs
// nearest-in-range) stays a kind-based special case in CombatEngine
// (auraBuildingKinds/Watchtower branch); this field is just the per-kind magnitude,
// sourced from the same Balance constants CombatEngine reads, so a building's damage
// can't drift between the two.
// spawns: Option[(UnitKind, Double)] referencing a UnitKind case is always safe here as a
// `val` (unlike UnitKind's own self-referencing `spawns` — see that enum's doc): it's a
// cross-enum reference to the fully independent UnitKind, which never references
// BuildingKind back, so there's no initialization-order cycle to worry about.
// upgradeFrom: which BuildingKind this one is reached by upgrading, if any — always a
// BACKWARD same-enum reference (a case only ever names a source declared earlier: Forest
// names Grove, Jungle names Forest, every specific lab names LaboFondamental), so unlike
// UnitKind.spawns/BuildingKind's own forward-referencing needs, this is safe as a literal
// `val` — Scala 3 enum cases initialize as ordered vals, and a reference to an
// already-initialized earlier case is never null. The forward direction (a source's list
// of upgrade targets) is the opposite, unsafe case — see BuildingSpecs.upgradeOptions,
// which derives that list FROM this field instead of hand-maintaining it separately.
// researchSpec: the Science lab's own research line (cost/effect per level), for the five
// specific labs only — a plain data class with no BuildingKind reference of its own, so
// there's no cross-enum cycle risk here either. See ResearchSpecs.all, now just this
// field collected across BuildingKind.values instead of a hand-maintained parallel map.
// Grove/Forest/Jungle form Nature's upgrade chain (Bosquet.md/Foret.md/Jungle.md) — only
// Grove is directly buildable; Forest and Jungle are reached by upgrading an existing
// Grove/Forest in place (see `upgradeFrom` above, Placement.tryUpgradeBuilding).
//
// Tomb/BlackCastle (Tombe.md/Chateau Noir.md) are Mort's pair, mirroring Cave/Labyrinth's
// shape (two independently-buildable tiers, not an upgrade chain). PassingGate (Portail.md)
// is a third, independently-costed Mort building — a Loi/Mort-flavored hybrid cost, dealing
// its own aura damage like Forest/Jungle/Angel and, uniquely, harvesting Shadow from any
// nearby death regardless of what killed it (see CombatEngine's applyPassingGateHarvest
// and Building.flashMs).
//
// LaboFondamental is Science's only directly-buildable kind — a plain, unspecialized base
// lab (flat Balance.CrystalPerSecPerLaboFondamental production, no research line of its
// own). It upgrades into exactly one of the other five Labo* kinds (Labo de la Loi/
// Naturel/Sombre/de Recherche/du Chaos — see each one's `upgradeFrom`), each still capped
// at one per maze (maxPerMaze), so a maze can run several LaboFondamental at once but only
// ever specialize into a given kind once (until that specific building is lost, freeing
// the slot for a *different* LaboFondamental to claim it — see Placement.tryUpgradeBuilding).
// The upgrade itself grants the chosen kind an instant, free research level 1 — "further
// research is the same as now" (Placement.tryResearch), just starting one level higher.
// The five specific labs are declared LaboDeLaLoi-first (rock-paper-scissors tuning pass,
// at the project owner's explicit direction): BuildingSpecs.upgradeOptions preserves
// BuildingKind.values' own declaration order, and AiStrategy.upgradeAnyAffordable always
// picks the first affordable target in that list — LawSpending's own rush specifically
// wants its one LaboFondamental to become LaboDeLaLoi (its research speeds up Watchtower/
// Angel's attack rate — see LawSpending's own doc) rather than whichever of the 5 happens
// to be cheapest to afford first. Order otherwise doesn't matter to ScienceSpending, which
// wants all 5 regardless of sequence (see VictoryConditions.hasWonViaFondamentale).
// tier: 1-3, a mandatory literal (no default) so a new case can't be added without an
// explicit design call on where it lands — no design doc tiers every building (see
// DocGenerator/CLAUDE.md's symmetry rule and the TODO.md design-pass note this reads
// from), so most of these values are a proxy for design intent (originally computed by
// ranking each building's own cost against its faction's other buildings, before this
// field existed), not the intent itself; DragonsLair/Barracks/StasisField were the only
// three explicitly hand-picked from the start.
enum BuildingKind(
    val faction: Faction,
    val tier: Int,
    val cost: Map[Resource, Double],
    val produces: Map[Resource, Double] = Map.empty,
    val spawns: Option[(UnitKind, Double)] = None,
    val buildableDirectly: Boolean = true,
    val maxPerMaze: Option[Int] = None,
    val dps: Double = 0.0,
    val upgradeFrom: Option[BuildingKind] = None,
    val researchSpec: Option[ResearchSpec] = None,
    val corruptionHealPercentPerSec: Double = 0.0
) derives CanEqual:

  case Grove
      extends BuildingKind(
        faction = Faction.Nature,
        tier = 1,
        cost = Map(Resource.Wood -> Balance.GroveCostWood),
        produces = Map(Resource.Wood -> Balance.WoodPerSecPerGrove),
        spawns = Some(UnitKind.Elf -> Balance.ElfSpawnIntervalMs),
        corruptionHealPercentPerSec = Balance.GroveCorruptionHealPercentPerSec
      )
  case Forest
      extends BuildingKind(
        faction = Faction.Nature,
        tier = 2,
        cost = Map(Resource.Wood -> Balance.ForestUpgradeCostWood),
        produces = Map(Resource.Wood -> Balance.WoodPerSecPerForest),
        spawns = Some(UnitKind.Elf -> Balance.ElfSpawnIntervalMs),
        buildableDirectly = false,
        dps = Balance.AuraDamagePerSec,
        upgradeFrom = Some(BuildingKind.Grove),
        corruptionHealPercentPerSec = Balance.ForestCorruptionHealPercentPerSec
      )
  case Jungle
      extends BuildingKind(
        faction = Faction.Nature,
        tier = 3,
        cost = Map(Resource.Wood -> Balance.JungleUpgradeCostWood),
        produces = Map(Resource.Wood -> Balance.WoodPerSecPerJungle),
        spawns = Some(UnitKind.Wolf -> Balance.WolfSpawnIntervalMs),
        buildableDirectly = false,
        dps =
          Balance.AuraDamagePerSec, // inherited from Forest — see Balance.AuraDamagePerSec's doc
        upgradeFrom = Some(BuildingKind.Forest),
        corruptionHealPercentPerSec = Balance.JungleCorruptionHealPercentPerSec
      )
  case Stonehenge
      extends BuildingKind(
        faction = Faction.Nature,
        tier = 3,
        cost = Map(Resource.Wood -> Balance.StonehengeCostWood),
        spawns = Some(UnitKind.Tree -> Balance.StonehengeSpawnIntervalMs)
      )
  case Cave
      extends BuildingKind(
        faction = Faction.Chaos,
        tier = 1,
        cost = Map(Resource.Wood -> Balance.CaveCostWood, Resource.Fire -> Balance.CaveCostFire),
        produces = Map(Resource.Fire -> Balance.FirePerSecPerCave),
        spawns = Some(UnitKind.Goblin -> Balance.GoblinSpawnIntervalMs)
      )
  case Labyrinth
      extends BuildingKind(
        faction = Faction.Chaos,
        tier = 3,
        cost = Map(
          Resource.Wood -> Balance.LabyrintheCostWood,
          Resource.Fire -> Balance.LabyrintheCostFire
        ),
        spawns = Some(UnitKind.Minotaur -> Balance.MinotaurSpawnIntervalMs)
      )
  // Antre du Dragon.md: Chaos's tier-3 building — no resource production at all, its
  // value is purely the glass-cannon Dragon it spawns (see UnitKind.Dragon).
  case DragonsLair
      extends BuildingKind(
        faction = Faction.Chaos,
        tier = 3,
        cost = Map(
          Resource.Wood -> Balance.DragonsLairCostWood,
          Resource.Fire -> Balance.DragonsLairCostFire
        ),
        spawns = Some(UnitKind.Dragon -> Balance.DragonSpawnIntervalMs)
      )
  // Camp de Guerre: Chaos's tier-2 building (see Balance.OrcMaxHp's doc) — unlike
  // Labyrinth/DragonsLair it still produces Fire, same as Cave, on top of the Orc it
  // spawns.
  case WarCamp
      extends BuildingKind(
        faction = Faction.Chaos,
        tier = 2,
        cost =
          Map(Resource.Wood -> Balance.WarCampCostWood, Resource.Fire -> Balance.WarCampCostFire),
        produces = Map(Resource.Fire -> Balance.FirePerSecPerWarCamp),
        spawns = Some(UnitKind.Orc -> Balance.OrcSpawnIntervalMs)
      )
  case Church
      extends BuildingKind(
        faction = Faction.Loi,
        tier = 3,
        cost =
          Map(Resource.Wood -> Balance.EgliseCostWood, Resource.Light -> Balance.EgliseCostLight),
        produces = Map(Resource.Light -> Balance.LightPerSecPerEglise),
        spawns = Some(UnitKind.Paladin -> Balance.PaladinSpawnIntervalMs)
      )
  case Watchtower
      extends BuildingKind(
        faction = Faction.Loi,
        tier = 2,
        cost = Map(
          Resource.Wood -> Balance.WatchtowerCostWood,
          Resource.Light -> Balance.WatchtowerCostLight
        ),
        produces = Map(Resource.Light -> Balance.LightPerSecPerWatchtower),
        dps = Balance.WatchtowerDamagePerSec
      )
  case Angel
      extends BuildingKind(
        faction = Faction.Loi,
        tier = 3,
        cost = Map(Resource.Light -> Balance.AngelCostLight),
        produces = Map(Resource.Light -> Balance.LightPerSecPerAngel),
        dps = Balance.AngelDamagePerSec
      )
  // Caserne.md: Loi's tier-1 building — a genuinely cheap entry point, below Church's
  // own price, spawning the cheap/fragile Soldat (see UnitKind.Soldier).
  case Barracks
      extends BuildingKind(
        faction = Faction.Loi,
        tier = 1,
        cost = Map(
          Resource.Wood -> Balance.BarracksCostWood,
          Resource.Light -> Balance.BarracksCostLight
        ),
        produces = Map(Resource.Light -> Balance.LightPerSecPerBarracks),
        spawns = Some(UnitKind.Soldier -> Balance.SoldierSpawnIntervalMs)
      )
  case Tomb
      extends BuildingKind(
        faction = Faction.Mort,
        tier = 1,
        cost =
          Map(Resource.Wood -> Balance.TombCostWood, Resource.Shadow -> Balance.TombCostShadow),
        produces = Map(Resource.Shadow -> Balance.ShadowPerSecPerTomb),
        spawns = Some(UnitKind.Zombie -> Balance.ZombieSpawnIntervalMs)
      )
  case BlackCastle
      extends BuildingKind(
        faction = Faction.Mort,
        tier = 3,
        cost = Map(
          Resource.Wood -> Balance.BlackCastleCostWood,
          Resource.Shadow -> Balance.BlackCastleCostShadow
        ),
        produces = Map(Resource.Shadow -> Balance.ShadowPerSecPerBlackCastle),
        spawns = Some(UnitKind.Vampire -> Balance.VampireSpawnIntervalMs)
      )
  case DeathHouse
      extends BuildingKind(
        faction = Faction.Mort,
        tier = 2,
        cost = Map(
          Resource.Wood -> Balance.DeathHouseCostWood,
          Resource.Shadow -> Balance.DeathHouseCostShadow
        ),
        produces = Map(Resource.Shadow -> Balance.ShadowPerSecPerDeathHouse),
        spawns = Some(UnitKind.Necromancer -> Balance.NecromancerSpawnIntervalMs)
      )
  // Portail.md: no unit spawn, no passive production either — its value is entirely the
  // aura damage + death-harvest combat ability in CombatEngine (see auraBuildingKinds/
  // applyPassingGateHarvest), same "combat abilities stay out of this enum" split as
  // Forest/Jungle/Angel's aura and Watchtower's ranged damage.
  case PassingGate
      extends BuildingKind(
        faction = Faction.Mort,
        tier = 3,
        cost = Map(
          Resource.Shadow -> Balance.PassingGateCostShadow,
          Resource.Light -> Balance.PassingGateCostLight
        ),
        dps = Balance.PassingGateDamagePerSec
      )
  // Note sur les laboratoires.md: the only Science kind placed fresh. No maxPerMaze:
  // unlike the five specific kinds below, a maze can run several of these side by side,
  // each free to specialize into a *different* one (see each one's `upgradeFrom`).
  case LaboFondamental
      extends BuildingKind(
        faction = Faction.Science,
        tier = 1,
        cost = Map(Resource.Crystal -> Balance.LaboFondamentalCostCrystal),
        produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboFondamental)
      )
  // Champ de Stase.md: Science's tier-2 building — no unit spawn, produces more Crystal
  // than LaboFondamental, and slows adjacent enemy creatures (see CombatEngine's
  // stasisCells handling in effectiveSpeedPerMs) — a separate, additive building, not
  // part of the LaboFondamental upgrade chain.
  case StasisField
      extends BuildingKind(
        faction = Faction.Science,
        tier = 2,
        cost = Map(Resource.Crystal -> Balance.StasisFieldCostCrystal),
        produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerStasisField)
      )
  // buildableDirectly = false for all five below: reached only by upgrading a
  // LaboFondamental (see `upgradeFrom`/Placement.tryUpgradeBuilding), never placed from
  // scratch. Declared LaboDeLaLoi-first — see this enum's own doc on why the order matters.
  case LaboDeLaLoi
      extends BuildingKind(
        faction = Faction.Science,
        tier = 2,
        cost = Map(
          Resource.Light -> Balance.LaboDeLaLoiCostLight,
          Resource.Crystal -> Balance.LaboDeLaLoiCostCrystal
        ),
        produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboDeLaLoi),
        buildableDirectly = false,
        maxPerMaze = Some(1),
        upgradeFrom = Some(BuildingKind.LaboFondamental),
        researchSpec = Some(
          ResearchSpec(
            baseCost = Map(
              Resource.Light -> Balance.RecherchesLoyalesCostLight,
              Resource.Crystal -> Balance.RecherchesLoyalesCostCrystal
            ),
            effectByLevel = Balance.LoyalesAttackSpeedIncreaseByLevel
          )
        )
      )
  case LaboNaturel
      extends BuildingKind(
        faction = Faction.Science,
        tier = 2,
        cost = Map(
          Resource.Wood -> Balance.LaboNaturelCostWood,
          Resource.Crystal -> Balance.LaboNaturelCostCrystal
        ),
        produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboNaturel),
        buildableDirectly = false,
        maxPerMaze = Some(1),
        upgradeFrom = Some(BuildingKind.LaboFondamental),
        researchSpec = Some(
          ResearchSpec(
            baseCost = Map(
              Resource.Wood -> Balance.RecherchesNaturellesCostWood,
              Resource.Crystal -> Balance.RecherchesNaturellesCostCrystal
            ),
            effectByLevel = Balance.NaturellesCostReductionByLevel
          )
        )
      )
  case LaboSombre
      extends BuildingKind(
        faction = Faction.Science,
        tier = 2,
        cost = Map(
          Resource.Shadow -> Balance.LaboSombreCostShadow,
          Resource.Crystal -> Balance.LaboSombreCostCrystal
        ),
        produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboSombre),
        buildableDirectly = false,
        maxPerMaze = Some(1),
        upgradeFrom = Some(BuildingKind.LaboFondamental),
        researchSpec = Some(
          ResearchSpec(
            baseCost = Map(
              Resource.Shadow -> Balance.RecherchesSombresCostShadow,
              Resource.Crystal -> Balance.RecherchesSombresCostCrystal
            ),
            effectByLevel = Balance.SombresCorruptionSpeedIncreaseByLevel
          )
        )
      )
  case LaboDeRecherche
      extends BuildingKind(
        faction = Faction.Science,
        tier = 2,
        cost = Map(Resource.Crystal -> Balance.LaboDeRechercheCostCrystal),
        produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboDeRecherche),
        buildableDirectly = false,
        maxPerMaze = Some(1),
        upgradeFrom = Some(BuildingKind.LaboFondamental),
        // Recherche fondamentale has no numeric effect list — its effect is the victory
        // check itself (see VictoryConditions.hasWonViaFondamentale).
        researchSpec = Some(
          ResearchSpec(
            baseCost = Map(Resource.Crystal -> Balance.RechercheFondamentaleCostCrystal),
            effectByLevel = Nil
          )
        )
      )
  case LaboDuChaos
      extends BuildingKind(
        faction = Faction.Science,
        tier = 2,
        cost = Map(
          Resource.Fire -> Balance.LaboDuChaosCostFire,
          Resource.Crystal -> Balance.LaboDuChaosCostCrystal
        ),
        produces = Map(Resource.Crystal -> Balance.CrystalPerSecPerLaboDuChaos),
        buildableDirectly = false,
        maxPerMaze = Some(1),
        upgradeFrom = Some(BuildingKind.LaboFondamental),
        researchSpec = Some(
          ResearchSpec(
            baseCost = Map(
              Resource.Fire -> Balance.RecherchesChaotiquesCostFire,
              Resource.Crystal -> Balance.RecherchesChaotiquesCostCrystal
            ),
            effectByLevel = Balance.ChaotiquesSpawnTimeReductionByLevel
          )
        )
      )
