package towerdefense.domain.i18n

import towerdefense.domain.*

// Long-form, doc-page prose for every building and unit, in French and English — what
// DocGenerator embeds as each generated page's body. Every number here is read straight
// off `Balance` (via its interpolated string, not copied by hand), so a generated page
// can never state a rate/duration/percentage the actual simulation doesn't use — unlike
// several of the original hand-written vault pages, which had drifted from Balance after
// tuning (e.g. Loup.md's "PV: 40" vs Balance.WolfMaxHp's actual 30, or Jungle.md's
// "toutes les 5 sec" vs Balance.WolfSpawnIntervalMs's actual 10s).
//
// Ability sentences that aren't captured by BuildingSpec/CreatureSpec at all (auras,
// shields, corruption, cloning — these stay hand-coded per-kind special cases in
// CombatEngine by design, see BuildingSpecs' doc) are necessarily hand-written here too,
// same as they were in the original vault pages — there's no generic data table to derive
// "Forest's Ents deal aura damage" from. Only their *numbers* are pulled from Balance.
//
// Every kind's body text is written once, as a value in `buildingBodies`/`unitBodies`
// below (a Map literal), the same "data, not a switch" shape BuildingSpecs.all/
// CreatureSpecs.all already use — there's no `kind match { ... }` anywhere in this file.
// Cross-links to another kind's name/file (e.g. Grove's body linking to Elf) read
// EntityNames' info tables directly, which is always already fully built by the time any
// entry here is evaluated (this file only *depends on* that data, nothing in EntityNames
// depends back on this file, so there's no initialization-order cycle).
object EntityText:
  import NumberFormat.{decimal, seconds, percentPoints}

  private def s(lines: String*): String = lines.mkString("\n\n")

  def buildingBody(kind: BuildingKind, lang: Lang): String = buildingBodies(kind)(lang)
  def unitBody(kind: UnitKind, lang: Lang): String = unitBodies(kind)(lang)

  // ── Buildings ────────────────────────────────────────────────────────────

  private def faction(kind: BuildingKind): Faction = EntityNames.buildingInfo(kind).faction

  val buildingBodies: Map[BuildingKind, I18nText] = Map(
    BuildingKind.Grove -> I18nText.combine(
      produceLine(Resource.Wood, Balance.WoodPerSecPerGrove, faction(BuildingKind.Grove)),
      spawnLine(UnitKind.Elf, Balance.ElfSpawnIntervalMs, faction(BuildingKind.Grove)),
      groveHealLine(Balance.GroveCorruptionHealPercentPerSec, faction(BuildingKind.Grove))
    ),
    BuildingKind.Forest -> I18nText.combine(
      produceLine(Resource.Wood, Balance.WoodPerSecPerForest, faction(BuildingKind.Forest)),
      upgradeOfLine(BuildingKind.Grove, faction(BuildingKind.Forest), feminine = false),
      entAuraLine(Balance.AuraDamagePerSec),
      spawnLine(UnitKind.Elf, Balance.ElfSpawnIntervalMs, faction(BuildingKind.Forest)),
      groveHealLine(Balance.ForestCorruptionHealPercentPerSec, faction(BuildingKind.Forest))
    ),
    BuildingKind.Jungle -> I18nText.combine(
      produceLine(Resource.Wood, Balance.WoodPerSecPerJungle, faction(BuildingKind.Jungle)),
      // Forêt is the one feminine upgrade source (see upgradeOfLine's doc).
      upgradeOfLine(BuildingKind.Forest, faction(BuildingKind.Jungle), feminine = true),
      entAuraLine(Balance.AuraDamagePerSec),
      spawnLine(UnitKind.Wolf, Balance.WolfSpawnIntervalMs, faction(BuildingKind.Jungle)),
      groveHealLine(Balance.JungleCorruptionHealPercentPerSec, faction(BuildingKind.Jungle))
    ),
    BuildingKind.Stonehenge -> stonehengeBody,
    BuildingKind.Cave -> I18nText.combine(
      produceLine(Resource.Fire, Balance.FirePerSecPerCave, faction(BuildingKind.Cave)),
      spawnLine(UnitKind.Goblin, Balance.GoblinSpawnIntervalMs, faction(BuildingKind.Cave))
    ),
    BuildingKind.Labyrinth ->
      I18nText.combine(spawnLine(UnitKind.Minotaur, Balance.MinotaurSpawnIntervalMs, faction(BuildingKind.Labyrinth))),
    BuildingKind.Church -> I18nText.combine(
      produceLine(Resource.Light, Balance.LightPerSecPerEglise, faction(BuildingKind.Church)),
      spawnLine(UnitKind.Paladin, Balance.PaladinSpawnIntervalMs, faction(BuildingKind.Church))
    ),
    BuildingKind.Watchtower -> I18nText.combine(
      produceLine(Resource.Light, Balance.LightPerSecPerWatchtower, faction(BuildingKind.Watchtower)),
      watchtowerAbilityLine
    ),
    BuildingKind.Angel -> I18nText.combine(
      produceLine(Resource.Light, Balance.LightPerSecPerAngel, faction(BuildingKind.Angel)),
      angelAbilityLine
    ),
    BuildingKind.Tomb -> I18nText.combine(
      produceLine(Resource.Shadow, Balance.ShadowPerSecPerTomb, faction(BuildingKind.Tomb)),
      spawnLine(UnitKind.Zombie, Balance.ZombieSpawnIntervalMs, faction(BuildingKind.Tomb))
    ),
    BuildingKind.BlackCastle -> I18nText.combine(
      produceLine(Resource.Shadow, Balance.ShadowPerSecPerBlackCastle, faction(BuildingKind.BlackCastle)),
      spawnLine(UnitKind.Vampire, Balance.VampireSpawnIntervalMs, faction(BuildingKind.BlackCastle))
    ),
    BuildingKind.DeathHouse -> I18nText.combine(
      produceLine(Resource.Shadow, Balance.ShadowPerSecPerDeathHouse, faction(BuildingKind.DeathHouse)),
      spawnLine(UnitKind.Necromancer, Balance.NecromancerSpawnIntervalMs, faction(BuildingKind.DeathHouse))
    ),
    BuildingKind.PassingGate -> passingGateBody,
    BuildingKind.LaboFondamental -> laboFondamentalBody,
    BuildingKind.LaboNaturel ->
      specificLabBody(faction(BuildingKind.LaboNaturel), Balance.CrystalPerSecPerLaboNaturel, BuildingKind.LaboNaturel),
    BuildingKind.LaboSombre ->
      specificLabBody(faction(BuildingKind.LaboSombre), Balance.CrystalPerSecPerLaboSombre, BuildingKind.LaboSombre),
    BuildingKind.LaboDeRecherche -> specificLabBody(
      faction(BuildingKind.LaboDeRecherche),
      Balance.CrystalPerSecPerLaboDeRecherche,
      BuildingKind.LaboDeRecherche
    ),
    BuildingKind.LaboDeLaLoi ->
      specificLabBody(faction(BuildingKind.LaboDeLaLoi), Balance.CrystalPerSecPerLaboDeLaLoi, BuildingKind.LaboDeLaLoi),
    BuildingKind.LaboDuChaos ->
      specificLabBody(faction(BuildingKind.LaboDuChaos), Balance.CrystalPerSecPerLaboDuChaos, BuildingKind.LaboDuChaos)
  )

  private def stonehengeBody: I18nText =
    val f = faction(BuildingKind.Stonehenge)
    val treeLinkFr = EntityNames.unitLink(f, UnitKind.Tree, Lang.Fr)
    val treeLinkEn = EntityNames.unitLink(f, UnitKind.Tree, Lang.En)
    I18nText(
      fr = s(
        s"Génère un $treeLinkFr toutes les ${seconds(Balance.StonehengeSpawnIntervalMs)} secondes, qui part " +
          "attaquer l'adversaire comme n'importe quelle autre unité — mais compte quand même pour la " +
          "condition de victoire de la Nature (Expansion Inarrêtable) tant qu'il est en vie, même pendant " +
          "qu'il attaque."
      ),
      en = s(
        s"Spawns an $treeLinkEn every ${seconds(Balance.StonehengeSpawnIntervalMs)} seconds, which heads out " +
          "to attack the opponent like any other unit — but still counts toward Nature's victory condition " +
          "(Unstoppable Expansion) for as long as it's alive, even while it's attacking."
      )
    )

  private def watchtowerAbilityLine: I18nText = I18nText(
    fr = s"N'envoie aucune unité. Inflige ${decimal(Balance.WatchtowerDamagePerSec)} dégâts par seconde à " +
      s"l'ennemi le plus proche jusqu'à ${Balance.WatchtowerRangeCells} cases de distance.",
    en = s"Spawns no unit. Deals ${decimal(Balance.WatchtowerDamagePerSec)} damage per second to the " +
      s"nearest enemy within ${Balance.WatchtowerRangeCells} cells."
  )

  private def angelAbilityLine: I18nText = I18nText(
    fr = s"N'envoie aucune unité. Inflige ${decimal(Balance.AngelDamagePerSec)} dégâts par seconde aux " +
      s"unités adjacentes, et ralentit leur vitesse de déplacement de ${percentPoints(Balance.AngelSlowFraction * 100)}.",
    en = s"Spawns no unit. Deals ${decimal(Balance.AngelDamagePerSec)} damage per second to adjacent " +
      s"units, and slows their movement speed by ${percentPoints(Balance.AngelSlowFraction * 100)}."
  )

  private def passingGateBody: I18nText =
    val f = faction(BuildingKind.PassingGate)
    val shadowLinkFr = EntityNames.resourceLink(f, Resource.Shadow, Lang.Fr)
    val shadowLinkEn = EntityNames.resourceLink(f, Resource.Shadow, Lang.En)
    I18nText(
      fr = s(
        s"N'envoie aucune unité. Inflige ${decimal(Balance.PassingGateDamagePerSec)} dégâts par seconde à toute " +
          "unité se trouvant sur l'une de ses 4 cases adjacentes.",
        s"Chaque fois qu'une unité meurt sur l'une de ces 4 cases (peu importe ce qui l'a tuée), le Portail " +
          s"draine ${percentPoints(Balance.PassingGateDeathShadowFraction * 100)} des ressources totales " +
          s"actuelles de son propriétaire et les convertit en $shadowLinkFr bonus."
      ),
      en = s(
        s"Spawns no unit. Deals ${decimal(Balance.PassingGateDamagePerSec)} damage per second to any unit " +
          "standing on one of its 4 adjacent cells.",
        "Whenever a unit dies on one of those 4 cells (no matter what killed it), the Passing Gate drains " +
          s"${percentPoints(Balance.PassingGateDeathShadowFraction * 100)} of its owner's current total " +
          s"resources and converts them into bonus $shadowLinkEn."
      )
    )

  private def laboFondamentalBody: I18nText =
    val f = faction(BuildingKind.LaboFondamental)
    val specificLabs =
      List(
        BuildingKind.LaboNaturel,
        BuildingKind.LaboSombre,
        BuildingKind.LaboDeRecherche,
        BuildingKind.LaboDeLaLoi,
        BuildingKind.LaboDuChaos
      )
    val labLinksFr = specificLabs.map(EntityNames.buildingLink(f, _, Lang.Fr)).mkString(", ")
    val labLinksEn = specificLabs.map(EntityNames.buildingLink(f, _, Lang.En)).mkString(", ")
    val crystalLinkFr = EntityNames.resourceLink(f, Resource.Crystal, Lang.Fr)
    val crystalLinkEn = EntityNames.resourceLink(f, Resource.Crystal, Lang.En)
    val notePageFr = EntityNames.outOfScopeLink("Note sur les laboratoires", f, Faction.Science, "Note sur les laboratoires.md", Lang.Fr)
    val notePageEn = EntityNames.outOfScopeLink("Note on labs", f, Faction.Science, "Note sur les laboratoires.md", Lang.En)
    val growthPerLevel = percentPoints(Balance.LaboSizeGrowthPerResearchLevel * 100)
    val growthAtMax = percentPoints(Balance.LaboSizeGrowthPerResearchLevel * Balance.MaxResearchLevel * 100)
    I18nText(
      fr = s(
        "Le seul bâtiment de Science constructible directement — un laboratoire générique, sans ligne de " +
          "recherche propre.",
        s"Produit ${decimal(Balance.CrystalPerSecPerLaboFondamental)} $crystalLinkFr par seconde, sans aucun " +
          "bonus de recherche.",
        s"Peut ensuite être amélioré vers l'un des cinq laboratoires spécifiques ($labLinksFr), " +
          s"au coût habituel de ce laboratoire — voir $notePageFr. Cette mise à niveau donne immédiatement le " +
          "niveau 1 (gratuit) de la recherche associée.",
        s"Un bâtiment amélioré grandit de $growthPerLevel par niveau de recherche (jusqu'à $growthAtMax " +
          s"de plus au niveau ${Balance.MaxResearchLevel}) — le Labo Fondamental lui-même reste toujours à sa taille de base."
      ),
      en = s(
        "The only Science building buildable directly — a generic lab with no research line of its own.",
        s"Produces ${decimal(Balance.CrystalPerSecPerLaboFondamental)} $crystalLinkEn per second, with no " +
          "research bonus.",
        s"Can then be upgraded into one of the five specific labs ($labLinksEn), at that " +
          s"lab's usual cost — see $notePageEn. This upgrade immediately grants a free level 1 of the " +
          "matching research line.",
        s"An upgraded building grows $growthPerLevel per research level (up to $growthAtMax " +
          s"larger at level ${Balance.MaxResearchLevel}) — the Base Lab itself always stays its base size."
      )
    )

  private def specificLabBody(f: Faction, crystalPerSec: Double, researchKind: BuildingKind): I18nText =
    val baseLabLinkFr = EntityNames.buildingLink(f, BuildingKind.LaboFondamental, Lang.Fr)
    val baseLabLinkEn = EntityNames.buildingLink(f, BuildingKind.LaboFondamental, Lang.En)
    val researchLinkFr = EntityNames.researchLineLink(f, researchKind, Lang.Fr)
    val researchLinkEn = EntityNames.researchLineLink(f, researchKind, Lang.En)
    val boost = percentPoints(Balance.LaboCrystalBoostPerResearchLevel * 100)
    I18nText(
      fr = s(
        s"Amélioration du $baseLabLinkFr — pas constructible directement.",
        s"Produit ${decimal(crystalPerSec)} ${EntityNames.resourceLink(f, Resource.Crystal, Lang.Fr)} par seconde, " +
          s"augmenté de $boost par niveau de recherche.",
        s"Débloque $researchLinkFr, au niveau 1 dès la mise à niveau (gratuit)."
      ),
      en = s(
        s"Upgrade of the $baseLabLinkEn — not directly buildable.",
        s"Produces ${decimal(crystalPerSec)} ${EntityNames.resourceLink(f, Resource.Crystal, Lang.En)} per second, " +
          s"boosted by $boost per research level.",
        s"Unlocks $researchLinkEn, at level 1 as soon as it's upgraded (free)."
      )
    )

  private def produceLine(resource: Resource, perSec: Double, from: Faction): I18nText = I18nText(
    fr = s"Produit ${decimal(perSec)} ${EntityNames.resourceLink(from, resource, Lang.Fr)} par seconde.",
    en = s"Produces ${decimal(perSec)} ${EntityNames.resourceLink(from, resource, Lang.En)} per second."
  )

  // Every unit kind a building spawns happens to be grammatically masculine in French
  // ("un Elfe", "un Gobelin", "un Nécromancien"...), so "un" is safe to hardcode here
  // rather than needing a per-kind gender table.
  private def spawnLine(unit: UnitKind, intervalMs: Double, from: Faction): I18nText =
    val article = englishIndefiniteArticle(EntityNames.unitName(unit, Lang.En))
    I18nText(
      fr = s"Toutes les ${seconds(intervalMs)} secondes, génère un ${EntityNames.unitLink(from, unit, Lang.Fr)}.",
      en = s"Every ${seconds(intervalMs)} seconds, spawns $article ${EntityNames.unitLink(from, unit, Lang.En)}."
    )

  private def englishIndefiniteArticle(name: String): String =
    if name.nonEmpty && "AEIOU".contains(name.head.toUpper) then "an" else "a"

  // `feminine` is data the caller states explicitly (Jungle passes true for its upgrade
  // source, Forêt) rather than this function guessing from the source kind — the only
  // French-grammar exception among the upgrade chain's sources (Bosquet/Labo Fondamental
  // are both masculine).
  private def upgradeOfLine(previous: BuildingKind, from: Faction, feminine: Boolean): I18nText =
    val article = if feminine then "de la" else "du"
    I18nText(
      fr = s"Amélioration $article ${EntityNames.buildingLink(from, previous, Lang.Fr)}.",
      en = s"Upgrade of the ${EntityNames.buildingLink(from, previous, Lang.En)}."
    )

  private def entAuraLine(dmgPerSec: Double): I18nText = I18nText(
    fr = s"Contient des Ents qui attaquent les unités passant sur les cases adjacentes pour ${decimal(dmgPerSec)} dégâts/sec.",
    en = s"Home to Ents that deal ${decimal(dmgPerSec)} damage/sec to units passing on adjacent cells."
  )

  private def groveHealLine(healPercentPerSec: Double, from: Faction): I18nText =
    val corruptionLinkFr = EntityNames.outOfScopeLink("Corruption", from, Faction.Mort, "Corruption.md", Lang.Fr)
    val corruptionLinkEn = EntityNames.outOfScopeLink("Corruption", from, Faction.Mort, "Corruption.md", Lang.En)
    val healText = percentPoints(healPercentPerSec)
    I18nText(
      fr = s"Soigne la corruption (voir $corruptionLinkFr) de lui-même et des 8 bâtiments environnants de " +
        s"$healText par seconde.",
      en = s"Heals corruption (see $corruptionLinkEn) on itself and the 8 surrounding buildings by " +
        s"$healText per second."
    )

  // ── Units ────────────────────────────────────────────────────────────────

  private def faction(kind: UnitKind): Faction = EntityNames.unitInfo(kind).faction

  val unitBodies: Map[UnitKind, I18nText] = Map(
    UnitKind.Elf -> I18nText.combine(
      spawnedByLine(BuildingKind.Grove, faction(UnitKind.Elf)),
      plunderLine(List(Resource.Wood -> Balance.PlunderPerUnit), faction(UnitKind.Elf))
    ),
    UnitKind.Goblin -> I18nText.combine(
      spawnedByLine(BuildingKind.Cave, faction(UnitKind.Goblin)),
      plunderLine(
        List(Resource.Wood -> Balance.PlunderPerUnit, Resource.Fire -> Balance.PlunderPerUnit),
        faction(UnitKind.Goblin)
      )
    ),
    UnitKind.Minotaur -> I18nText.combine(
      spawnedByLine(BuildingKind.Labyrinth, faction(UnitKind.Minotaur)),
      plunderLine(
        List(Resource.Wood -> Balance.MinotaurPlunderPerUnit, Resource.Fire -> Balance.MinotaurPlunderPerUnit),
        faction(UnitKind.Minotaur)
      )
    ),
    UnitKind.Paladin -> I18nText.combine(spawnedByLine(BuildingKind.Church, faction(UnitKind.Paladin)), paladinAuraLine),
    UnitKind.Wolf -> wolfBody,
    UnitKind.Tree -> treeBody,
    UnitKind.Zombie -> I18nText.combine(
      spawnedByLine(BuildingKind.Tomb, faction(UnitKind.Zombie)),
      slowMovementLine,
      corruptsLine(Balance.ZombieCorruptionPercentPerSec, faction(UnitKind.Zombie))
    ),
    UnitKind.Vampire -> vampireBody,
    UnitKind.Necromancer -> necromancerBody,
    UnitKind.Soul -> soulBody
  )

  private def paladinAuraLine: I18nText =
    val shield = decimal(Balance.PaladinAuraDamageReductionPerSec)
    I18nText(
      fr = s"Aura : protège les unités alliées adjacentes de $shield dégâts par seconde.",
      en = s"Aura: shields adjacent allied units from $shield damage per second."
    )

  private def wolfBody: I18nText =
    val f = faction(UnitKind.Wolf)
    val elfLinkFr = EntityNames.unitLink(f, UnitKind.Elf, Lang.Fr)
    val elfLinkEn = EntityNames.unitLink(f, UnitKind.Elf, Lang.En)
    val speedMultiplier = decimal(Balance.WolfSpeedAuraMultiplier)
    val boost = percentPoints((Balance.WolfSpeedAuraMultiplier - 1) * 100)
    I18nText(
      fr = s(
        s"Se déplace ${speedMultiplier}x plus vite que les unités standard comme l'$elfLinkFr, et augmente la " +
          s"vitesse de déplacement des unités alliées à ${Balance.WolfSpeedAuraRangeCells} cases de distance de $boost."
      ),
      en = s(
        s"Moves ${speedMultiplier}x faster than standard units like the $elfLinkEn, and speeds up allied units " +
          s"within ${Balance.WolfSpeedAuraRangeCells} cells by $boost."
      )
    )

  private def treeBody: I18nText =
    val f = faction(UnitKind.Tree)
    val zombieLinkFr = EntityNames.outOfScopeLink(EntityNames.unitName(UnitKind.Zombie, Lang.Fr), f, Faction.Mort, "Zombie.md", Lang.Fr)
    val zombieLinkEn = EntityNames.outOfScopeLink(EntityNames.unitName(UnitKind.Zombie, Lang.En), f, Faction.Mort, "Zombie.md", Lang.En)
    val cloneFraction = percentPoints((1.0 - Balance.TreeCloneSizeStepFraction) * 100)
    val minFraction = percentPoints(Balance.TreeMinCloneSizeFraction * 100)
    I18nText(
      fr = s(
        spawnedByLine(BuildingKind.Stonehenge, f).fr,
        s"Se déplace lentement (moitié de la vitesse standard), comme un $zombieLinkFr.",
        s"Toutes les ${seconds(Balance.TreeCloneIntervalMs)} secondes, il s'arrête pendant " +
          s"${seconds(Balance.TreeCloneFreezeMs)} secondes et invoque un clone de lui-même sur la case " +
          s"suivante de son chemin, puis reprend sa marche. Ce nouveau clone fait $cloneFraction de la " +
          s"taille et des PV de celui qui l'a créé, et peut lui-même se cloner de la même façon — jusqu'à " +
          s"un minimum de $minFraction de la taille d'origine.",
        "Part attaquer l'adversaire comme n'importe quelle autre unité, mais compte quand même pour la " +
          "condition de victoire de la Nature (Expansion Inarrêtable) de son propriétaire tant qu'il est " +
          "en vie, même pendant qu'il attaque."
      ),
      en = s(
        spawnedByLine(BuildingKind.Stonehenge, f).en,
        s"Moves slowly (half standard speed), like a $zombieLinkEn.",
        s"Every ${seconds(Balance.TreeCloneIntervalMs)} seconds, it stops for " +
          s"${seconds(Balance.TreeCloneFreezeMs)} seconds and spawns a clone of itself on the next cell of " +
          s"its path, then resumes walking. The new clone is $cloneFraction the size and HP of the one " +
          s"that made it, and can itself clone the same way — down to a minimum of $minFraction of the " +
          "original size.",
        "Heads out to attack the opponent like any other unit, but still counts toward its owner's Nature " +
          "victory condition (Unstoppable Expansion) for as long as it's alive, even while it's attacking."
      )
    )

  private def slowMovementLine: I18nText = I18nText(
    fr = "Se déplace lentement (moitié de la vitesse standard).",
    en = "Moves slowly (half standard speed)."
  )

  private def corruptsLine(percentPerSec: Double, from: Faction): I18nText =
    val corruptionLinkFr = EntityNames.outOfScopeLink("Corruption", from, Faction.Mort, "Corruption.md", Lang.Fr)
    val corruptionLinkEn = EntityNames.outOfScopeLink("Corruption", from, Faction.Mort, "Corruption.md", Lang.En)
    val rate = percentPoints(percentPerSec)
    I18nText(
      fr = s"Corrompt les bâtiments ennemis adjacents de $rate par seconde (voir $corruptionLinkFr).",
      en = s"Corrupts adjacent enemy buildings by $rate per second (see $corruptionLinkEn)."
    )

  private def vampireBody: I18nText =
    val f = faction(UnitKind.Vampire)
    val reduction = percentPoints(Balance.VampireDamageReductionFraction * 100)
    val speedMultiplier = decimal(Balance.VampireSpeedPerMs / Balance.ElfSpeedPerMs)
    val paladinLinkFr = EntityNames.unitLink(f, UnitKind.Paladin, Lang.Fr)
    val paladinLinkEn = EntityNames.unitLink(f, UnitKind.Paladin, Lang.En)
    I18nText.combine(
      spawnedByLine(BuildingKind.BlackCastle, f),
      I18nText(
        fr = s"Se déplace vite (${speedMultiplier}x la vitesse standard).",
        en = s"Moves fast (${speedMultiplier}x standard speed)."
      ),
      corruptsLine(Balance.VampireCorruptionPercentPerSec, f),
      I18nText(
        fr = s"Réduit les dégâts qu'il subit de $reduction (mais n'est pas protégé par l'aura du $paladinLinkFr).",
        en = s"Takes $reduction less damage (but isn't shielded by the $paladinLinkEn's aura)."
      )
    )

  private def necromancerBody: I18nText =
    val f = faction(UnitKind.Necromancer)
    val zombieLinkFr = EntityNames.outOfScopeLink(EntityNames.unitName(UnitKind.Zombie, Lang.Fr), f, Faction.Mort, "Zombie.md", Lang.Fr)
    val zombieLinkEn = EntityNames.outOfScopeLink(EntityNames.unitName(UnitKind.Zombie, Lang.En), f, Faction.Mort, "Zombie.md", Lang.En)
    val soulLinkFr = EntityNames.unitLink(f, UnitKind.Soul, Lang.Fr)
    val soulLinkEn = EntityNames.unitLink(f, UnitKind.Soul, Lang.En)
    I18nText(
      fr = s(
        spawnedByLine(BuildingKind.DeathHouse, f).fr,
        s"Se déplace lentement (moitié de la vitesse standard), comme un $zombieLinkFr.",
        s"Toutes les ${seconds(Balance.SoulSummonIntervalMs)} secondes, invoque une $soulLinkFr — pendant " +
          s"${seconds(Balance.NecromancerSummonFreezeMs)} seconde, il reste immobile."
      ),
      en = s(
        spawnedByLine(BuildingKind.DeathHouse, f).en,
        s"Moves slowly (half standard speed), like a $zombieLinkEn.",
        s"Every ${seconds(Balance.SoulSummonIntervalMs)} seconds, summons a $soulLinkEn — for " +
          s"${seconds(Balance.NecromancerSummonFreezeMs)} second, it stands still."
      )
    )

  private def soulBody: I18nText =
    val f = faction(UnitKind.Soul)
    val necroLinkFr = EntityNames.unitLink(f, UnitKind.Necromancer, Lang.Fr)
    val necroLinkEn = EntityNames.unitLink(f, UnitKind.Necromancer, Lang.En)
    val corruptionLinkFr = EntityNames.outOfScopeLink("Corruption", f, Faction.Mort, "Corruption.md", Lang.Fr)
    val corruptionLinkEn = EntityNames.outOfScopeLink("Corruption", f, Faction.Mort, "Corruption.md", Lang.En)
    val rate = percentPoints(Balance.SoulCorruptionPercentPerSec)
    val heal = decimal(Balance.SoulHealPerSecPerBuilding)
    I18nText(
      fr = s(
        s"Invoquée par le $necroLinkFr.",
        "Se déplace à vitesse normale.",
        s"Corrompt les bâtiments ennemis adjacents de $rate par seconde (voir $corruptionLinkFr).",
        s"Chaque fois qu'elle corrompt un bâtiment, elle soigne le Nécromancien qui l'a invoquée de $heal " +
          "PV par seconde et par bâtiment corrompu (jusqu'à son maximum)."
      ),
      en = s(
        s"Summoned by the $necroLinkEn.",
        "Moves at normal speed.",
        s"Corrupts adjacent enemy buildings by $rate per second (see $corruptionLinkEn).",
        s"Every time it corrupts a building, it heals the Necromancer that summoned it $heal HP per second " +
          "per building corrupted (up to its maximum)."
      )
    )

  private def spawnedByLine(building: BuildingKind, from: Faction): I18nText = I18nText(
    fr = s"Produit par ${EntityNames.buildingLink(from, building, Lang.Fr)}.",
    en = s"Spawned by ${EntityNames.buildingLink(from, building, Lang.En)}."
  )

  private def plunderLine(amounts: List[(Resource, Double)], from: Faction): I18nText =
    val frLinks = amounts.map { case (res, amount) => s"${decimal(amount)} ${EntityNames.resourceLink(from, res, Lang.Fr)}" }
    val enLinks = amounts.map { case (res, amount) => s"${decimal(amount)} ${EntityNames.resourceLink(from, res, Lang.En)}" }
    I18nText(fr = s"Pille ${frLinks.mkString(" et ")}.", en = s"Plunders ${enLinks.mkString(" and ")}.")
