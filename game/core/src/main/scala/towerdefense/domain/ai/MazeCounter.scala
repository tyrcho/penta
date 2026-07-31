package towerdefense.domain.ai

// Pure counterScore atop FreeformLayout's danger-maximizing placement, instead of
// counter-only's NoLayoutPreference.
object MazeCounter
    extends ComposedStrategy(
      FreeformLayout,
      WeightedSpending(resourceWeight = 0.0, counterWeight = 1.0),
      name = "maze-counter"
    )
