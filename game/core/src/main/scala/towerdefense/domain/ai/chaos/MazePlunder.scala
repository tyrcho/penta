package towerdefense.domain.ai.chaos

import towerdefense.domain.ai.*

// Chaos's own racing strategy: forces Cave/WarCamp (ChaosShared.opening), then plays
// PlunderSpending atop FreeformLayout's danger-maximizing placement.
object MazePlunder
    extends ComposedStrategy(
      ForcedOpeningLayout(ChaosShared.opening, FreeformLayout),
      PlunderSpending,
      name = "maze-plunder"
    )
