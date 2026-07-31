package towerdefense.domain.ai

import towerdefense.domain.grid.MazeTemplate

// Pure resourceScore atop MazeTemplate.comb's fixed wall.
object CombResource
    extends ComposedStrategy(
      TemplateLayout(MazeTemplate.comb),
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0),
      name = "comb-resource"
    )
