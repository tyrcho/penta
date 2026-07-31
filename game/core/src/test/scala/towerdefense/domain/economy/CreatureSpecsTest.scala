package towerdefense.domain.economy

import towerdefense.domain.*

// UnitKind's own maxHp/speedPerMs/plunder/faction/spawns/producedFrom now live directly on
// the enum case (see model.scala) — this file tests the derived tier relationship: a unit's
// tier isn't its own literal, it's always whatever its producing building's own tier is.
class CreatureSpecsTest extends munit.FunSuite:

  test("producedFrom is defined for every UnitKind") {
    UnitKind.values.foreach { kind =>
      assert(kind.producedFrom.isDefined, s"producedFrom is missing an entry for $kind")
    }
  }

  test(
    "every producedFrom building's cost is nonzero, so Passing Gate always has something to harvest"
  ) {
    UnitKind.values.foreach { kind =>
      val cost = kind.producedFrom.map(_.cost.values.sum).getOrElse(0.0)
      assert(cost > 0.0, s"$kind's producing building has a zero total cost: $cost")
    }
  }

  test("a unit's tier always matches its producing building's own tier") {
    UnitKind.values.foreach { kind =>
      assertEquals(Some(kind.tier), kind.producedFrom.map(_.tier))
    }
  }
