package towerdefense.domain.ai.loi

import towerdefense.domain.BuildingKind
import towerdefense.domain.economy.ResearchSpecs

// Values shared by every Loi rush strategy (MazeLaw/CombLaw).
private[loi] object LoiShared:
  val opening: Seq[BuildingKind] = Seq(BuildingKind.Barracks, BuildingKind.Watchtower)

  // Every kind a Science-lab building can ever be (LaboFondamental itself, before its
  // first upgrade, plus the 5 kinds it upgrades into) — used to give CountCapLayout the
  // full "counts as" set for Law's own one-lab cap: see CountCapLayout's own doc for why a
  // layout-level veto (not just a SpendingPolicy penalty) was needed to make this cap
  // actually hold under a genuine resource drought.
  val labKinds: Set[BuildingKind] = ResearchSpecs.all.keySet + BuildingKind.LaboFondamental
