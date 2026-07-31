package towerdefense.domain.economy

import towerdefense.domain.*

// What's left here once cost/produces/spawns/dps/buildableDirectly/maxPerMaze/faction/
// tier/upgradeFrom/researchSpec moved onto BuildingKind itself (see model.scala): just the
// forward direction of the upgrade-chain relation.
object BuildingSpecs:

  // Grove -> Forest -> Jungle (a single-option chain), and LaboFondamental -> one of the
  // five specific labs (a 5-option branch, the first source with more than one target) —
  // see Placement.tryUpgradeBuilding for how a caller picks among several. Absent for every
  // other kind (no upgrade path at all).
  //
  // Derived from BuildingKind.upgradeFrom (each target's own literal field) rather than
  // hand-maintained here directly: `upgradeFrom` is a safe literal `val` because it's
  // always a BACKWARD same-enum reference (a case only ever names an earlier-declared
  // source), but this forward direction — a source's list of targets, some of which are
  // declared LATER than the source — can't be a literal field the same way (see
  // BuildingKind's own doc), so it stays computed here instead. groupMap preserves
  // BuildingKind.values' own declaration order within each group, which is why
  // BuildingKind.scala declares the five specific labs LaboDeLaLoi-first — see its own doc
  // on why that order matters to AiStrategy.upgradeAnyAffordable/LawSpending.
  val upgradeOptions: Map[BuildingKind, List[BuildingKind]] =
    BuildingKind.values.toList.flatMap(k => k.upgradeFrom.map(_ -> k)).groupMap(_._1)(_._2)
