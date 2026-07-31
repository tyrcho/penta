package towerdefense.domain.ai

// Pure resourceScore atop FreeformLayout's danger-maximizing placement, weighted 0.5
// spending vs 0.25 layout — added alongside Watchtower-scoring/Elo work (see docs/adr/0009)
// as a resource-aware strategy that still cares some about maze danger, unlike
// resource-only's NoLayoutPreference.
object ResourceMaze
    extends ComposedStrategy(
      FreeformLayout,
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0),
      layoutWeight = 0.25,
      spendingWeight = 0.5,
      name = "resource-maze"
    )
