package towerdefense.domain.ai.loi

import towerdefense.domain.BuildingKind
import towerdefense.domain.ai.*
import towerdefense.domain.grid.MazeTemplate

// Loi's own racing strategy atop MazeTemplate.comb's fixed wall, instead of MazeLaw's
// FreeformLayout.
object CombLaw
    extends ComposedStrategy(
      ForcedOpeningLayout(
        LoiShared.opening,
        CountCapLayout(BuildingKind.LaboFondamental, LoiShared.labKinds, 1, TemplateLayout(MazeTemplate.comb))
      ),
      LawSpending,
      name = "comb-law"
    )
