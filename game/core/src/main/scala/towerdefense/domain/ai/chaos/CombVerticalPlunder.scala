package towerdefense.domain.ai.chaos

import towerdefense.domain.ai.*
import towerdefense.domain.grid.MazeTemplate

// Chaos's own racing strategy atop MazeTemplate.combVertical's fixed wall, instead of
// MazePlunder's FreeformLayout.
object CombVerticalPlunder
    extends ComposedStrategy(
      ForcedOpeningLayout(ChaosShared.opening, TemplateLayout(MazeTemplate.combVertical)),
      PlunderSpending,
      name = "comb-vertical-plunder"
    )
