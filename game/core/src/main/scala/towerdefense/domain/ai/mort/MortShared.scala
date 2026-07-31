package towerdefense.domain.ai.mort

import towerdefense.domain.BuildingKind

// Values shared by every Mort rush strategy (MazeCorruption/CombCorruption).
private[mort] object MortShared:
  val opening: Seq[BuildingKind] = Seq(BuildingKind.Tomb)
