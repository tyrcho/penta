package towerdefense.domain.ai

// Equal-weight blend of resourceScore and counterScore atop FreeformLayout.
object Balanced
    extends ComposedStrategy(
      FreeformLayout,
      WeightedSpending(resourceWeight = 1.0, counterWeight = 1.0),
      name = "balanced"
    )
