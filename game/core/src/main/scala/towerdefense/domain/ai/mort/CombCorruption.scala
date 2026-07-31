package towerdefense.domain.ai.mort

import towerdefense.domain.ai.*
import towerdefense.domain.grid.MazeTemplate

// Mort's own racing strategy atop MazeTemplate.comb's fixed wall, instead of
// MazeCorruption's FreeformLayout — see MazeCorruption's own doc for why its win rate used
// to not mean what the name implies, and for the blackCastleBonus/spendingWeight fix (and
// the tombCap that was tried and reverted there). TemplateLayout's own score never carries
// a per-kind self-bonus the way FreeformLayout's dangerScore does (it only ranks candidate
// CELLS by template position, identically for every kind), so the specific Watchtower/Angel
// drift MazeCorruption's own doc describes wasn't reproduced here — spendingWeight is still
// raised to match, purely to keep this variant's tuning coherent with its FreeformLayout
// sibling rather than because a matching bug was independently diagnosed on this
// template-wall path.
object CombCorruption
    extends ComposedStrategy(
      ForcedOpeningLayout(MortShared.opening, TemplateLayout(MazeTemplate.comb)),
      CorruptionSpending,
      spendingWeight = 3.0,
      name = "comb-corruption"
    )
