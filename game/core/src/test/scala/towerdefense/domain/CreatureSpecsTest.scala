package towerdefense.domain

class CreatureSpecsTest extends munit.FunSuite:

  test("spawningBuilding covers every UnitKind, and every mapped BuildingKind is real") {
    UnitKind.values.foreach { kind =>
      assert(CreatureSpecs.spawningBuilding.contains(kind), s"spawningBuilding is missing an entry for $kind")
      val building = CreatureSpecs.spawningBuilding(kind)
      assert(BuildingSpecs.all.contains(building), s"$kind maps to $building, which isn't a real BuildingKind")
    }
  }

  test("every spawningBuilding entry's cost is nonzero, so Passing Gate always has something to harvest") {
    UnitKind.values.foreach { kind =>
      val building = CreatureSpecs.spawningBuilding(kind)
      val cost = BuildingSpecs.all(building).cost.values.sum
      assert(cost > 0.0, s"$kind's spawning building ($building) has a zero total cost: $cost")
    }
  }

  test("a unit's tier always matches its spawning building's own tier") {
    UnitKind.values.foreach { kind =>
      val building = CreatureSpecs.spawningBuilding(kind)
      assertEquals(CreatureSpecs.all(kind).tier, BuildingSpecs.all(building).tier)
    }
  }
