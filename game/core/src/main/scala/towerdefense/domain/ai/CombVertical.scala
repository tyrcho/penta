package towerdefense.domain.ai

import towerdefense.domain.grid.MazeTemplate

// Fills MazeTemplate.combVertical's fixed wall, Grove-first with margin-fallback
// (GrovePriority) for whatever isn't Grove — moved from the old TemplateStrategy.
object CombVertical
    extends ComposedStrategy(
      TemplateLayout(MazeTemplate.combVertical),
      GrovePriority,
      name = "comb-vertical"
    )
