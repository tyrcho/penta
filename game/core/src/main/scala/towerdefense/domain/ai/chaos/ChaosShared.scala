package towerdefense.domain.ai.chaos

import towerdefense.domain.BuildingKind

// Values shared by every Chaos rush strategy (MazePlunder/CombPlunder/CombVerticalPlunder).
private[chaos] object ChaosShared:
  // Forces Cave then WarCamp before PlunderSpending's own candidate scoring takes over —
  // explicit, auditable control over how the first Gold gets spent, instead of leaving the
  // opening to emerge from the ordinary candidate-scoring competition. Found by reading a
  // real `sim/run maze-plunder maze-science --log` transcript: PlunderSpending once spent
  // its entire starting Gold on a zero-return DragonsLair turn 1 before this opening
  // existed.
  val opening: Seq[BuildingKind] = Seq(BuildingKind.Cave, BuildingKind.WarCamp)
