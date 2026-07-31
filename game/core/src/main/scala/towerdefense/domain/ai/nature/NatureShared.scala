package towerdefense.domain.ai.nature

import towerdefense.domain.BuildingKind

// Values shared by every Nature rush strategy (MazeNature/CombNature).
private[nature] object NatureShared:
  val opening: Seq[BuildingKind] = Seq(BuildingKind.Grove)

  // See HealClusterLayout's own doc: rewards placing one of these kinds next to more of
  // this maze's own existing buildings, since a healer covers *any* building (not just
  // other healers) within Chebyshev distance 1 of it and multiple nearby healers stack —
  // added to give Nature a real defense against Death's corruption-sabotage of its own
  // forestCount win condition, confirmed by transcript to otherwise go undefended.
  //
  // Watchtower is included even though it isn't itself a healer (CombatEngine.
  // healBuildingCorruption's own healer map has no Watchtower entry): a transcript
  // (`sim/run maze-nature maze-corruption --log`) showed all 12 of Death's corruption
  // wins landing on Watchtowers specifically, every one built off on its own via plain
  // FreeformLayout danger-scoring, nowhere near the Grove/Forest cluster — Nature kept
  // rebuilding them as an undefended, disposable corruption target instead of the
  // intended defense. Clustering Watchtower alongside the healers it doesn't itself
  // provide, but can still receive, was the actual fix.
  val clusterKinds: Set[BuildingKind] =
    Set(BuildingKind.Grove, BuildingKind.Forest, BuildingKind.Jungle, BuildingKind.Watchtower)

  // Raised from an initial 3.0 (comparable to one AuraDamagePerSec hit): confirmed via
  // transcript that 3.0 was too weak to move Watchtower off a single stand-out chokepoint
  // cell that dominates FreeformLayout's own path-danger score by a wide margin — Death
  // just camped a corrupting unit there and picked off every Watchtower Nature rebuilt on
  // that exact cell, one after another (6 in a row, in one measured match). Strong enough
  // now to reliably outweigh a chokepoint's raw path-danger lead, so a healer-adjacent
  // (but slightly less path-optimal) cell wins instead.
  val healClusterBonus = 15.0
