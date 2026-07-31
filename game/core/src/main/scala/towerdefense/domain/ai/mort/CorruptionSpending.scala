package towerdefense.domain.ai.mort

import towerdefense.domain.*
import towerdefense.domain.ai.SpendingPolicy

// Always favors Mort (Tomb/BlackCastle) regardless of the opponent's own faction mix,
// racing the Mort/corruption victory condition the same way chaos.PlunderSpending races
// Chaos's.
case object CorruptionSpending extends SpendingPolicy:
  private val mortKinds = Set(BuildingKind.Tomb, BuildingKind.BlackCastle, BuildingKind.DeathHouse)

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    (if mortKinds.contains(kind) then 1.0 else 0.0) + 0.25 * SpendingPolicy.resourceScore(
      state,
      kind
    )
