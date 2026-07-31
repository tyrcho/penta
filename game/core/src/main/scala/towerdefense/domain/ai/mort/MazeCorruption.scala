package towerdefense.domain.ai.mort

import towerdefense.domain.ai.*

// Mort's own racing strategy: forces Tomb (MortShared.opening), then plays
// CorruptionSpending atop FreeformLayout. Mort's Tomb/BlackCastle-racing counterpart to
// chaos.MazePlunder — historically topped the ladder at several speeds, but NOT via Mort's
// own corruption mechanic: spot-checked transcripts (`sim/run maze-corruption comb --log`,
// `sim/run resource-maze maze-corruption --log`) showed 0 CORRUPT events. CorruptionSpending's
// 0.25*resourceScore fallback term happened to grab a cheap Cave early and that Cave's
// Goblins won the race to Chaos's own plunder target before the opponent mounted any
// defense — "CorruptionSpending's fallback economy, sped up by cheap buildings", not
// "corruption is viable".
//
// spendingWeight=3.0 (up from ComposedStrategy's default 1.0): diagnosed via `sim/run
// maze-nature maze-corruption --log` that CorruptionSpending's own kind preference was
// getting drowned out by FreeformLayout's danger-maximizing cell score at the default 1:1
// weighting — Watchtower/Angel candidates get a large self-bonus in FreeformLayout.
// dangerScore (their own WatchtowerDamagePerSec/AuraDamagePerSec along the path,
// Balance-scaled and much larger than the flat categorical bonuses SpendingPolicy scores
// deal in), and once ComposedStrategy normalizes+sums layout and spending scores at equal
// weight, that self-bonus regularly outscored CorruptionSpending's own flat Mort-kind
// bonus — the transcript showed this maze building Watchtower/Angel/Grove/Barracks (0
// Mort-kind synergy at all) for most of a match instead of Tomb/BlackCastle/DeathHouse.
// Raising spendingWeight only changes which KIND wins the cross-kind comparison (kind
// candidates of the same type still tie on spending score, so FreeformLayout's own
// per-cell danger score still decides WHERE the chosen kind goes, same as before) — see
// ComposedStrategy.maybeBuild's own doc. With this raised, the maze builds
// Tomb/BlackCastle/DeathHouse far more consistently and produces real CORRUPT events where
// it produced none before.
//
// A hard cap on Tomb (mirroring loi.MazeLaw's own CountCapLayout use, to try to redirect
// Mort's own fixed, non-renewable Wood budget — no Mort building produces Wood at all,
// every Wood-costing spend draws down the one-time StartingGold and never comes back short
// of landing a corrosion, see BattleEngine.creditCorruption — away from cheap, weak
// Zombies and toward BlackCastle's own Vampire) was tried and reverted: measured via the
// same transcript, it backfired. Once Tomb (the one Mort kind cheap enough to almost
// always be an affordable candidate) was capped away, the maze hit stretches with NO
// affordable Mort-kind candidate at all far more often, and fell back to building
// Angel/Barracks/Watchtower/even LaboFondamental for most of the match instead (0 CORRUPT
// events, worse than the uncapped run) — Tomb's own cheapness turned out to be
// load-bearing as a reliable filler that keeps SOME Mort building affordable at all times,
// not dead weight to be squeezed out. Left uncapped.
object MazeCorruption
    extends ComposedStrategy(
      ForcedOpeningLayout(MortShared.opening, FreeformLayout),
      CorruptionSpending,
      spendingWeight = 3.0,
      name = "maze-corruption"
    )
