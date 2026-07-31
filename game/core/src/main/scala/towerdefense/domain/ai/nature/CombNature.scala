package towerdefense.domain.ai.nature

import towerdefense.domain.ai.*
import towerdefense.domain.grid.MazeTemplate

// Nature's own racing strategy atop MazeTemplate.comb's fixed wall, instead of
// MazeNature's FreeformLayout.
object CombNature
    extends ComposedStrategy(
      ForcedOpeningLayout(
        NatureShared.opening,
        HealClusterLayout(
          NatureShared.clusterKinds,
          NatureShared.healClusterBonus,
          TemplateLayout(MazeTemplate.comb)
        )
      ),
      NatureSpending,
      name = "comb-nature"
    )
