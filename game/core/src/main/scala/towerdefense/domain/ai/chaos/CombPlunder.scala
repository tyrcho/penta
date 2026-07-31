package towerdefense.domain.ai.chaos

import towerdefense.domain.ai.*
import towerdefense.domain.grid.MazeTemplate

// Chaos's own racing strategy atop MazeTemplate.comb's fixed wall, instead of MazePlunder's
// FreeformLayout.
object CombPlunder
    extends ComposedStrategy(
      ForcedOpeningLayout(ChaosShared.opening, TemplateLayout(MazeTemplate.comb)),
      PlunderSpending,
      name = "comb-plunder"
    )
