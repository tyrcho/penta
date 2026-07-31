package towerdefense.domain.ai.science

import towerdefense.domain.ai.*
import towerdefense.domain.grid.MazeTemplate

// Science's own racing strategy atop MazeTemplate.comb's fixed wall, instead of
// MazeScience's FreeformLayout.
object CombScience
    extends ComposedStrategy(
      ForcedOpeningLayout(ScienceShared.opening, TemplateLayout(MazeTemplate.comb)),
      ScienceSpending,
      name = "comb-science"
    )
