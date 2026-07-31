package towerdefense.domain.ai

import towerdefense.domain.grid.MazeTemplate

// Pure resourceScore atop MazeTemplate.combVertical's fixed wall.
object CombVerticalResource
    extends ComposedStrategy(
      TemplateLayout(MazeTemplate.combVertical),
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0),
      name = "comb-vertical-resource"
    )
