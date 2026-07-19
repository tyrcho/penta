package towerdefense.domain

// One research line per Science lab (Recherches naturelles/Sombres/chaotiques/loyales.md,
// Recherche fondamentale.md) — keyed by the lab's own BuildingKind since it's a 1:1
// pairing, no separate enum needed. `baseCost` is the level-1 price; level N costs
// baseCost scaled by 2^(N-1) ("Chaque niveau coute le double du precedent", every file).
// `effectAtLevel` is the magnitude Balance.*ByLevel lists give for level N (1-indexed);
// Fondamentale has no such magnitude (its "effect" is the victory check itself — see
// VictoryConditions.hasWonViaFondamentale), so its list is empty.
case class ResearchSpec(baseCost: Map[Resource, Double], effectByLevel: List[Double]):
  def costAtLevel(level: Int): Map[Resource, Double] =
    baseCost.view.mapValues(_ * math.pow(3.0, (level - 1).toDouble)).toMap

  def effectAtLevel(level: Int): Double =
    if level <= 0 then 0.0 else effectByLevel(level - 1)

object ResearchSpecs:
  val all: Map[BuildingKind, ResearchSpec] = Map(
    BuildingKind.LaboNaturel -> ResearchSpec(
      baseCost = Map(Resource.Wood -> Balance.RecherchesNaturellesCostWood, Resource.Crystal -> Balance.RecherchesNaturellesCostCrystal),
      effectByLevel = Balance.NaturellesCostReductionByLevel
    ),
    BuildingKind.LaboSombre -> ResearchSpec(
      baseCost = Map(Resource.Shadow -> Balance.RecherchesSombresCostShadow, Resource.Crystal -> Balance.RecherchesSombresCostCrystal),
      effectByLevel = Balance.SombresOpponentTargetIncreaseByLevel
    ),
    BuildingKind.LaboDuChaos -> ResearchSpec(
      baseCost = Map(Resource.Fire -> Balance.RecherchesChaotiquesCostFire, Resource.Crystal -> Balance.RecherchesChaotiquesCostCrystal),
      effectByLevel = Balance.ChaotiquesPlunderBonusByLevel
    ),
    BuildingKind.LaboDeLaLoi -> ResearchSpec(
      baseCost = Map(Resource.Light -> Balance.RecherchesLoyalesCostLight, Resource.Crystal -> Balance.RecherchesLoyalesCostCrystal),
      effectByLevel = Balance.LoyalesBuildingDamageIncreaseByLevel
    ),
    BuildingKind.LaboDeRecherche -> ResearchSpec(
      baseCost = Map(Resource.Crystal -> Balance.RechercheFondamentaleCostCrystal),
      effectByLevel = Nil
    )
  )

  // The four "other labs" Recherche fondamentale checks against — every Science lab kind
  // except LaboDeRecherche itself.
  val otherLabKinds: Set[BuildingKind] =
    all.keySet - BuildingKind.LaboDeRecherche

  // Explicit, stable iteration order for AiStrategy.researchAnyAffordable — `all.keys`
  // alone isn't guaranteed deterministic across runs, and a strategy's research choice
  // (when several are simultaneously affordable) needs to be reproducible the same way
  // upgradeAnyAffordable's building-list order already is.
  val orderedLabs: Seq[BuildingKind] = Seq(
    BuildingKind.LaboNaturel,
    BuildingKind.LaboSombre,
    BuildingKind.LaboDeRecherche,
    BuildingKind.LaboDeLaLoi,
    BuildingKind.LaboDuChaos
  )
