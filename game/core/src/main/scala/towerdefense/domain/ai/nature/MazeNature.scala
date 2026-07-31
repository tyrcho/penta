package towerdefense.domain.ai.nature

import towerdefense.domain.ai.*

// Nature's own racing strategy: forces Grove (NatureShared.opening), clusters its own
// healer kinds (NatureShared.clusterKinds/healClusterBonus), then plays NatureSpending
// atop FreeformLayout. Completes one dedicated rush strategy per faction alongside
// chaos.MazePlunder/mort.MazeCorruption/science.MazeScience/loi.MazeLaw — NatureSpending is
// genuinely new (GrovePriority only ever chases Grove itself, a different shape — see its
// own doc).
object MazeNature
    extends ComposedStrategy(
      ForcedOpeningLayout(
        NatureShared.opening,
        HealClusterLayout(NatureShared.clusterKinds, NatureShared.healClusterBonus, FreeformLayout)
      ),
      NatureSpending,
      name = "maze-nature"
    )
