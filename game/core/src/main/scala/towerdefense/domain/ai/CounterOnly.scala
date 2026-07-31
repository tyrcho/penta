package towerdefense.domain.ai

// Pure counterScore, no resource-affordability weighting and no positional preference —
// mirrors whichever faction (Nature/Chaos/Mort) the opponent invests in more.
object CounterOnly
    extends ComposedStrategy(
      NoLayoutPreference,
      WeightedSpending(resourceWeight = 0.0, counterWeight = 1.0),
      name = "counter-only"
    )
