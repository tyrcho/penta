package towerdefense.domain

class BuildingSpecsTest extends munit.FunSuite:

  test("every BuildingKind has a tier in 1..3") {
    BuildingKind.values.foreach { kind =>
      val tier = BuildingSpecs.all(kind).tier
      assert(tier >= 1 && tier <= 3, s"$kind has tier $tier, expected 1..3")
    }
  }

  test("the three explicitly design-tiered buildings keep their own tier") {
    assertEquals(BuildingSpecs.all(BuildingKind.DragonsLair).tier, 3)
    assertEquals(BuildingSpecs.all(BuildingKind.Barracks).tier, 1)
    assertEquals(BuildingSpecs.all(BuildingKind.StasisField).tier, 2)
  }

  test("Nature's upgrade chain has strictly increasing tiers, cheapest to priciest") {
    val grove = BuildingSpecs.all(BuildingKind.Grove).tier
    val forest = BuildingSpecs.all(BuildingKind.Forest).tier
    val jungle = BuildingSpecs.all(BuildingKind.Jungle).tier
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
    ).map(BuildingSpecs.all(_).tier)
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
    noCombat.foreach(kind =>
      assertEquals(BuildingSpecs.all(kind).dps, 0.0, s"$kind should have no dps")
    )
  }

  test("dps matches the same Balance constant CombatEngine deals in a real match") {
    assertEquals(BuildingSpecs.all(BuildingKind.Forest).dps, Balance.AuraDamagePerSec)
    assertEquals(BuildingSpecs.all(BuildingKind.Jungle).dps, Balance.AuraDamagePerSec)
    assertEquals(BuildingSpecs.all(BuildingKind.Angel).dps, Balance.AngelDamagePerSec)
    assertEquals(BuildingSpecs.all(BuildingKind.PassingGate).dps, Balance.PassingGateDamagePerSec)
    assertEquals(BuildingSpecs.all(BuildingKind.Watchtower).dps, Balance.WatchtowerDamagePerSec)
  }

  test("upgradeFrom is the exact inverse of upgradeOptions") {
    BuildingSpecs.upgradeOptions.foreach { case (source, targets) =>
      targets.foreach(target => assertEquals(BuildingSpecs.upgradeFrom.get(target), Some(source)))
    }
    val everyTarget = BuildingSpecs.upgradeOptions.values.flatten.toSet
    assertEquals(BuildingSpecs.upgradeFrom.keySet, everyTarget)
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
