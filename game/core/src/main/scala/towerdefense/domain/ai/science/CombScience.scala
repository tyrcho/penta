package towerdefense.domain.ai.science

import towerdefense.domain.ai.*
import towerdefense.domain.grid.MazeTemplate

// Science's own racing strategy atop MazeTemplate.comb's fixed wall, instead of
// MazeScience's FreeformLayout. Wrapped in ScienceShared.FondamentaleFirstResearch for the
// same reason MazeScience is — see that wrapper's own doc.
object CombScience
    extends FondamentaleFirstResearch(
      ComposedStrategy(
        ForcedOpeningLayout(ScienceShared.opening, TemplateLayout(MazeTemplate.comb)),
        ScienceSpending,
        name = "comb-science"
      )
    )
