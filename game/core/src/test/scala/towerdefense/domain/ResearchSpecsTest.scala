package towerdefense.domain

class ResearchSpecsTest extends munit.FunSuite:

  test("costAtLevel triples the base cost each level") {
    val spec = ResearchSpecs.all(BuildingKind.LaboNaturel)
    assertEquals(spec.costAtLevel(1), spec.baseCost)
    assertEquals(spec.costAtLevel(2), spec.baseCost.view.mapValues(_ * 3.0).toMap)
    assertEquals(spec.costAtLevel(3), spec.baseCost.view.mapValues(_ * 9.0).toMap)
    assertEquals(spec.costAtLevel(5), spec.baseCost.view.mapValues(_ * 81.0).toMap)
  }

  test("effectAtLevel returns 0 before any research, and the right magnitude per level") {
    val spec = ResearchSpecs.all(BuildingKind.LaboNaturel)
    assertEquals(spec.effectAtLevel(0), 0.0)
    assertEquals(spec.effectAtLevel(1), Balance.NaturellesCostReductionByLevel.head)
    assertEquals(spec.effectAtLevel(5), Balance.NaturellesCostReductionByLevel.last)
  }

  test("Recherche fondamentale has no numeric effect list — its effect is the victory check itself") {
    assertEquals(ResearchSpecs.all(BuildingKind.LaboDeRecherche).effectByLevel, Nil)
  }

  test("Recherche fondamentale's base cost differs from Labo de Recherche's own building cost") {
    assertEquals(
      ResearchSpecs.all(BuildingKind.LaboDeRecherche).baseCost,
      Map(Resource.Crystal -> Balance.RechercheFondamentaleCostCrystal)
    )
    assertEquals(Balance.RechercheFondamentaleCostCrystal, 20.0)
    assertEquals(BuildingSpecs.all(BuildingKind.LaboDeRecherche).cost, Map(Resource.Crystal -> 15.0))
  }

  test("otherLabKinds excludes exactly LaboDeRecherche") {
    assertEquals(ResearchSpecs.otherLabKinds, Set(
      BuildingKind.LaboNaturel,
      BuildingKind.LaboSombre,
      BuildingKind.LaboDeLaLoi,
      BuildingKind.LaboDuChaos
    ))
  }

  test("all five labs have their own research line, all costing crystal alongside a distinct second resource") {
    assertEquals(ResearchSpecs.all.keySet, ResearchSpecs.otherLabKinds + BuildingKind.LaboDeRecherche)
    ResearchSpecs.all.foreach { case (_, spec) => assert(spec.baseCost.contains(Resource.Crystal)) }
  }

  test("magnitudeAtLevel matches effectAtLevel for every lab except Recherche fondamentale") {
    ResearchSpecs.otherLabKinds.foreach { kind =>
      (1 to Balance.MaxResearchLevel).foreach { level =>
        assertEquals(ResearchSpecs.magnitudeAtLevel(kind, level), ResearchSpecs.all(kind).effectAtLevel(level))
      }
    }
  }

  test("magnitudeAtLevel reads Recherche fondamentale's magnitude from FondamentaleRequiredOtherLabLevel instead") {
    (1 to Balance.MaxResearchLevel).foreach { level =>
      assertEquals(
        ResearchSpecs.magnitudeAtLevel(BuildingKind.LaboDeRecherche, level),
        Balance.FondamentaleRequiredOtherLabLevel(level - 1).toDouble
      )
    }
  }
