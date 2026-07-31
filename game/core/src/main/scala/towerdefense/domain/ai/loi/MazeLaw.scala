package towerdefense.domain.ai.loi

import towerdefense.domain.BuildingKind
import towerdefense.domain.ai.*

// Loi's own racing strategy: forces Barracks/Watchtower (LoiShared.opening), caps
// LaboFondamental to LoiShared.labKinds' one-lab limit, then plays LawSpending atop
// FreeformLayout. Loi's own racing counterpart to chaos.MazePlunder/mort.MazeCorruption/
// science.MazeScience — completes one dedicated rush strategy per faction. LawSpending's
// racing kinds already include Watchtower, giving it built-in defense the same mechanism
// science.ScienceSpending provides via its own Watchtower bonus.
object MazeLaw
    extends ComposedStrategy(
      ForcedOpeningLayout(
        LoiShared.opening,
        CountCapLayout(BuildingKind.LaboFondamental, LoiShared.labKinds, 1, FreeformLayout)
      ),
      LawSpending,
      name = "maze-law"
    )
