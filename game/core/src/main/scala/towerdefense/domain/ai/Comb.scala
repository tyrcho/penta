package towerdefense.domain.ai

import towerdefense.domain.grid.MazeTemplate

// Fills MazeTemplate.comb's fixed wall, Grove-first with margin-fallback (GrovePriority)
// for whatever isn't Grove — moved from the old TemplateStrategy.
object Comb extends ComposedStrategy(TemplateLayout(MazeTemplate.comb), GrovePriority, name = "comb")
