package towerdefense.domain.i18n

import towerdefense.domain.*

class TooltipTextTest extends munit.FunSuite:

  test("costIcons shows a resource icon, not its written-out name") {
    val cost = Map(Resource.Wood -> 5.0, Resource.Crystal -> 10.0)
    assertEquals(TooltipText.costIcons(cost), "5 🪵 + 10 💎")
  }

  test("costIcons is the same regardless of language — icons don't need translating") {
    val cost = Map(Resource.Fire -> 5.0)
    assertEquals(TooltipText.costIcons(cost), "5 🔥")
  }

  test("costIcons drops zero-amount resources instead of showing a bare 0") {
    val cost = Map(Resource.Wood -> 0.0, Resource.Fire -> 10.0)
    assertEquals(TooltipText.costIcons(cost), "10 🔥")
  }

  test("costIcons is empty when every cost is zero") {
    assertEquals(TooltipText.costIcons(Map(Resource.Wood -> 0.0)), "")
  }

  test("costText (used by the wiki) still spells resource names out, unaffected by costIcons") {
    val cost = Map(Resource.Wood -> 5.0)
    assertEquals(TooltipText.costText(cost, Lang.En), "5 Wood")
    assertEquals(TooltipText.costText(cost, Lang.Fr), "5 Bois")
  }

  test("buildingButtonTooltip shows cost and production as icons, not resource names") {
    val text = TooltipText.buildingButtonTooltip(
      BuildingKind.Grove,
      cost = Map(Resource.Wood -> 5.0),
      produces = Map(Resource.Wood -> 0.5),
      Lang.En
    )
    assert(text.contains("🪵"), s"expected a wood icon in: $text")
    assert(!text.contains("Wood"), s"expected no written-out resource name in: $text")
  }

  test("buildingButtonTooltip follows the 'Name (cost) - rate' template") {
    val text = TooltipText.buildingButtonTooltip(
      BuildingKind.Cave,
      cost = Map(Resource.Wood -> 0.0, Resource.Fire -> 10.0),
      produces = Map(Resource.Fire -> 0.2),
      Lang.En
    )
    assertEquals(text, "Cave (10 🔥) - +0.2 🔥/s")
  }

  test("buildingButtonTooltip omits the parens entirely when every cost is zero") {
    val text = TooltipText.buildingButtonTooltip(
      BuildingKind.Cave,
      cost = Map(Resource.Wood -> 0.0),
      produces = Map.empty,
      Lang.En
    )
    assertEquals(text, "Cave")
  }

  test("buildingButtonTooltip omits the dash entirely when there's no production") {
    val text = TooltipText.buildingButtonTooltip(
      BuildingKind.Cave,
      cost = Map(Resource.Fire -> 10.0),
      produces = Map.empty,
      Lang.En
    )
    assertEquals(text, "Cave (10 🔥)")
  }

  test("rate shows the resource's icon, not its written-out name") {
    assertEquals(TooltipText.rate(Resource.Shadow, 0.4, Lang.En), "+0.4 🌑/s")
  }

  test("icon exposes the same glyph costIcons/rate use, for callers building their own cost strings") {
    assertEquals(TooltipText.icon(Resource.Crystal), "💎")
  }
