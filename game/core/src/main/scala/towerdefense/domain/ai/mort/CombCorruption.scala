package towerdefense.domain.ai.mort

import towerdefense.domain.ai.*
import towerdefense.domain.grid.MazeTemplate

// Mort's own racing strategy atop MazeTemplate.comb's fixed wall, instead of
// MazeCorruption's FreeformLayout — see MazeCorruption's own doc for why its win rate
// doesn't mean what the name implies.
object CombCorruption
    extends ComposedStrategy(
      ForcedOpeningLayout(MortShared.opening, TemplateLayout(MazeTemplate.comb)),
      CorruptionSpending,
      name = "comb-corruption"
    )
