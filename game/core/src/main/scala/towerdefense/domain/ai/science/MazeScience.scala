package towerdefense.domain.ai.science

import towerdefense.domain.ai.*

// Science's own racing strategy: forces Cave (ScienceShared.opening), then plays
// ScienceSpending atop FreeformLayout. Science's own racing counterpart to
// chaos.MazePlunder/mort.MazeCorruption — added after a full-ladder tournament turned up 0
// Science victories out of 118 decisive matches (Chaos plunder alone took 60%): every
// strategy before this either ignored the 5-lab-building requirement entirely or only
// ever researched opportunistically, never on purpose (see ScienceSpending's own doc).
// maze-science@1s now tops the entire ladder — see AiStrategyTest's ladder-order test.
object MazeScience
    extends ComposedStrategy(
      ForcedOpeningLayout(ScienceShared.opening, FreeformLayout),
      ScienceSpending,
      name = "maze-science"
    )
