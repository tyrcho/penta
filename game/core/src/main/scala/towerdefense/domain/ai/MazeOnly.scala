package towerdefense.domain.ai

// The one catalog entry with 0 wins that still ranks fairly high in a round-robin: it has
// very few losses too — a genuine, reproducible defensive lockout, not matchmaking luck.
// Weight (resource=0, counter=0) means it never once considers whether a spend is
// sustainable — it builds Watchtowers as long as FreeformLayout's ranged-damage credit
// outscores everything else, drains Wood to exactly 0 with no Grove ever built to
// replenish it, and then freezes permanently with a small, apparently-effective
// Watchtower cluster that denies most opponents a win within maxTicks without ever
// mounting a real offense of its own.
object MazeOnly
    extends ComposedStrategy(
      FreeformLayout,
      WeightedSpending(resourceWeight = 0.0, counterWeight = 0.0),
      name = "maze-only"
    )
