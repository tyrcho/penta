package towerdefense.domain.ai.science

import towerdefense.domain.BuildingKind

// Values shared by every Science rush strategy (MazeScience/CombScience).
private[science] object ScienceShared:
  // Shortened from an initial [Cave, Church, Tomb] (rock-paper-scissors tuning pass): a
  // 3-item forced opening made Science's own economy dramatically slower to get going
  // than every other faction's 1-2 item opening, losing almost every match on pure
  // opening speed before its labs/defense ever mattered — confirmed via `rockPaperScissors
  // 9` (Science lost 0-9 to both Chaos and Nature). One forced item (Cave, cheapest
  // producer) is now enough to kick off Fire income; ScienceSpending's own
  // missingProducerBonus tier already prioritizes Church/Tomb dynamically right after,
  // just without forcing a specific (possibly not cost-optimal) order for them.
  val opening: Seq[BuildingKind] = Seq(BuildingKind.Cave)
