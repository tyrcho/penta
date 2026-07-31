package towerdefense.domain.economy

import towerdefense.domain.*

// One research line per Science lab (Recherches naturelles/Sombres/chaotiques/loyales.md,
// Recherche fondamentale.md) — keyed by the lab's own BuildingKind since it's a 1:1
// pairing, no separate enum needed. `baseCost` is the level-1 price; level N costs
// baseCost scaled by Balance.ResearchCostMultiplierPerLevel^(N-1) — see that constant's
// doc for why it's named rather than a literal here.
// `effectAtLevel` is the magnitude Balance.*ByLevel lists give for level N (1-indexed);
// Fondamentale has no such magnitude (its "effect" is the victory check itself — see
// VictoryConditions.hasWonViaFondamentale), so its list is empty.
case class ResearchSpec(baseCost: Map[Resource, Double], effectByLevel: List[Double]):
  def costAtLevel(level: Int): Map[Resource, Double] =
    baseCost.view
      .mapValues(_ * math.pow(Balance.ResearchCostMultiplierPerLevel, (level - 1).toDouble))
      .toMap

  def effectAtLevel(level: Int): Double =
    if level <= 0 then 0.0 else effectByLevel(level - 1)

object ResearchSpecs:
  // Derived from BuildingKind.researchSpec (each lab's own literal field) rather than
  // hand-maintained here directly — see BuildingKind's own doc on why that's safe (a plain
  // data class with no BuildingKind reference back, unlike upgradeFrom/spawns).
  val all: Map[BuildingKind, ResearchSpec] =
    BuildingKind.values.toList.flatMap(k => k.researchSpec.map(k -> _)).toMap

  // The four "other labs" Recherche fondamentale checks against — every Science lab kind
  // except LaboDeRecherche itself.
  val otherLabKinds: Set[BuildingKind] =
    all.keySet - BuildingKind.LaboDeRecherche

  // The magnitude a lab's level actually gives at `level`, for any of the 5 labs — reads
  // straight off `effectAtLevel` for four of them, but Recherche fondamentale's own
  // ResearchSpec has no effectByLevel (empty list, see the doc above), so its "magnitude"
  // is instead the other-lab level it demands at that level, from Balance.
  // FondamentaleRequiredOtherLabLevel — the same list VictoryConditions.hasWonViaFondamentale
  // compares against. Centralized here (rather than duplicated per reader — the in-game
  // tooltip and the generated wiki page's per-level table both need this exact number) so
  // neither can special-case Fondamentale differently from the other.
  def magnitudeAtLevel(kind: BuildingKind, level: Int): Double = kind match
    case BuildingKind.LaboDeRecherche =>
      Balance.FondamentaleRequiredOtherLabLevel(level - 1).toDouble
    case other => all(other).effectAtLevel(level)

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
