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

  test("rate shows the resource's icon, not its written-out name") {
    assertEquals(TooltipText.rate(Resource.Shadow, 0.4, Lang.En), "+0.4 🌑/s")
  }

  test("icon exposes the same glyph costIcons/rate use, for callers building their own cost strings") {
    assertEquals(TooltipText.icon(Resource.Crystal), "💎")
  }
