package towerdefense.domain.ai.mort

import towerdefense.domain.BuildingKind

// Values shared by every Mort rush strategy (MazeCorruption/CombCorruption).
//
// opening = [Tomb] alone: a [Tomb, BlackCastle] forced double-opening was tried in round 2
// (front-loading the one raider — Vampire — that beats Nature's own building self-heal,
// see CorruptionSpending's own doc) and reverted. Measured via `sim/run maze-nature
// maze-corruption 6 3500`: 0/6, and matches resolved much FASTER than round 1's baseline
// (avgTicks ~854 vs ~2300+) — a logged replay showed why: spending 75 of the 100
// Balance.StartingGold on the first two buildings back-to-back left this maze with almost
// no further build capacity for a long stretch afterward (no Tomb/Grove filler, no
// second-wave raider), so Nature's own Elves walked an under-built, barely-obstructed maze
// largely unimpeded and raced to ITS OWN resourcesPlundered target before our own
// corruption pressure ever mattered. Committing that much Gold that fast was a net loss —
// left as the single forced Tomb opening, same as round 1.
private[mort] object MortShared:
  val opening: Seq[BuildingKind] = Seq(BuildingKind.Tomb)
