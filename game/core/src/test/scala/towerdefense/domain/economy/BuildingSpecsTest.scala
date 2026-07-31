package towerdefense.domain.economy

import towerdefense.domain.*

// BuildingKind's own cost/produces/spawns/dps/buildableDirectly/maxPerMaze/faction/tier now
// live directly on the enum case (see model.scala) — this file asserts the resulting tier
// invariants (still true now that tier is a hand-picked literal instead of a cost-ranked
// derivation) and tests what's left in BuildingSpecs itself: the forward direction of the
// upgrade-chain relation, which can't safely be a literal field on the enum (see
// BuildingSpecs.scala's own doc).
class BuildingSpecsTest extends munit.FunSuite:

  test("every BuildingKind has a tier in 1..3") {
    BuildingKind.values.foreach { kind =>
      val tier = kind.tier
      assert(tier >= 1 && tier <= 3, s"$kind has tier $tier, expected 1..3")
    }
  }

  test("the three explicitly design-tiered buildings keep their own tier") {
    assertEquals(BuildingKind.DragonsLair.tier, 3)
    assertEquals(BuildingKind.Barracks.tier, 1)
    assertEquals(BuildingKind.StasisField.tier, 2)
  }

  test("Nature's upgrade chain has strictly increasing tiers, cheapest to priciest") {
    val grove = BuildingKind.Grove.tier
    val forest = BuildingKind.Forest.tier
    val jungle = BuildingKind.Jungle.tier
    assert(
      grove < forest && forest <= jungle,
      s"expected grove < forest <= jungle, got $grove, $forest, $jungle"
    )
  }

  test("buildings costing the same within a faction land in the same tier (the 5 specific labs)") {
    val tiers = Set(
      BuildingKind.LaboNaturel,
      BuildingKind.LaboSombre,
      BuildingKind.LaboDeRecherche,
      BuildingKind.LaboDeLaLoi,
      BuildingKind.LaboDuChaos
    ).map(_.tier)
    assertEquals(tiers.size, 1, s"expected every specific lab at the same tier, got $tiers")
  }

  test("dps is 0 for buildings with no combat ability") {
    val noCombat = BuildingKind.values.toSet -- Set(
      BuildingKind.Forest,
      BuildingKind.Jungle,
      BuildingKind.Angel,
      BuildingKind.PassingGate,
      BuildingKind.Watchtower
    )
    noCombat.foreach(kind => assertEquals(kind.dps, 0.0, s"$kind should have no dps"))
  }

  test("dps matches the same Balance constant CombatEngine deals in a real match") {
    assertEquals(BuildingKind.Forest.dps, Balance.AuraDamagePerSec)
    assertEquals(BuildingKind.Jungle.dps, Balance.AuraDamagePerSec)
    assertEquals(BuildingKind.Angel.dps, Balance.AngelDamagePerSec)
    assertEquals(BuildingKind.PassingGate.dps, Balance.PassingGateDamagePerSec)
    assertEquals(BuildingKind.Watchtower.dps, Balance.WatchtowerDamagePerSec)
  }

  test("every upgradeOptions target's own upgradeFrom points back to its source") {
    BuildingSpecs.upgradeOptions.foreach { case (source, targets) =>
      targets.foreach(target => assertEquals(target.upgradeFrom, Some(source)))
    }
    val everyTarget = BuildingSpecs.upgradeOptions.values.flatten.toSet
    val everyKindWithUpgradeFrom = BuildingKind.values.filter(_.upgradeFrom.isDefined).toSet
    assertEquals(everyKindWithUpgradeFrom, everyTarget)
  }

  // AiStrategy.upgradeAnyAffordable always takes the first affordable target in this list
  // — LawSpending's own rush relies on LaboDeLaLoi being tried first (see its own doc), not
  // just present somewhere in the 5.
  test("LaboFondamental's upgrade options try LaboDeLaLoi first") {
    assertEquals(
      BuildingSpecs.upgradeOptions(BuildingKind.LaboFondamental).head,
      BuildingKind.LaboDeLaLoi
    )
  }
