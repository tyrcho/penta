package towerdefense.domain.ai

// Pure resourceScore, no counter-awareness and no positional preference.
object ResourceOnly
    extends ComposedStrategy(
      NoLayoutPreference,
      WeightedSpending(resourceWeight = 1.0, counterWeight = 0.0),
      name = "resource-only"
    )
