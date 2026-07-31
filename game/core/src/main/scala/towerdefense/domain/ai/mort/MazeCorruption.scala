package towerdefense.domain.ai.mort

import towerdefense.domain.ai.*

// Mort's own racing strategy: forces Tomb (MortShared.opening), then plays
// CorruptionSpending atop FreeformLayout. Mort's Tomb/BlackCastle-racing counterpart to
// chaos.MazePlunder — tops the ladder at several speeds, but NOT via Mort's own corruption
// mechanic: spot-checked transcripts (`sim/run maze-corruption comb --log`, `sim/run
// resource-maze maze-corruption --log`) show 0 CORRUPT events. CorruptionSpending's
// 0.25*resourceScore fallback term happens to grab a cheap Cave early and that Cave's
// Goblins win the race to Chaos's own plunder target before the opponent mounts any
// defense — "CorruptionSpending's fallback economy, sped up by cheap buildings", not
// "corruption is viable". Mort's own mechanic (see Corruption.md) still needs a design
// revisit before a strategy can race it on purpose.
object MazeCorruption
    extends ComposedStrategy(
      ForcedOpeningLayout(MortShared.opening, FreeformLayout),
      CorruptionSpending,
      name = "maze-corruption"
    )
