package towerdefense.domain.ai.mort

import towerdefense.domain.*
import towerdefense.domain.ai.SpendingPolicy

// Always favors Mort (Tomb/BlackCastle) regardless of the opponent's own faction mix,
// racing the Mort/corruption victory condition the same way chaos.PlunderSpending races
// Chaos's.
//
// blackCastleBonus: an extra premium on top of the flat mortBonus that makes BlackCastle
// (Vampire) outscore Tomb/DeathHouse whenever it's an affordable candidate, instead of the
// three tying and falling back to whichever is cheapest (usually Tomb). Diagnosed via
// `sim/run maze-nature maze-corruption --log`: only Vampire's own numbers actually beat
// Nature's own building self-heal in isolation — Vampire's corruption rate
// (Balance.VampireCorruptionPercentPerSec = 2.5%/sec) clears even a lone Jungle's heal
// (Balance.JungleCorruptionHealPercentPerSec = 1.8%/sec), while a Zombie's rate
// (Balance.ZombieCorruptionPercentPerSec = 1.0%/sec) is only breakeven against a lone
// Forest (1.0%/sec) and loses outright to a Jungle or any multi-building heal cluster (see
// Balance.GroveCorruptionHealPercentPerSec's own doc, which documents heal rates being
// tuned specifically against a Zombie assault). Vampire's HP (50, vs Zombie's 15) also
// survives Forest/Jungle's own AuraDamagePerSec (2.0/sec) for ~25s instead of ~7.5s, and
// its speed (1.5x Elf, same as Wolf) gets it there faster — all three of "does it out-heal
// them", "does it survive their aura", and "does it even arrive" favor BlackCastle over
// Tomb once it's affordable, not just a marginal preference.
case object CorruptionSpending extends SpendingPolicy:
  private val mortKinds = Set(BuildingKind.Tomb, BuildingKind.BlackCastle, BuildingKind.DeathHouse)
  private val blackCastleBonus = 1.5

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    val mortBonus = if mortKinds.contains(kind) then 1.0 else 0.0
    val vampireBonus = if kind == BuildingKind.BlackCastle then blackCastleBonus else 0.0
    mortBonus + vampireBonus + 0.25 * SpendingPolicy.resourceScore(state, kind)
