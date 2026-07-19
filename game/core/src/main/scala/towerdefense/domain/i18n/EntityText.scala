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
object EntityText:
  import NumberFormat.{decimal, seconds, percentPoints}

  private def s(lines: String*): String = lines.mkString("\n\n")

  // ── Buildings ────────────────────────────────────────────────────────────

  def buildingBody(kind: BuildingKind, lang: Lang): String =
    val f = Faction.of(kind)
    kind match
      case BuildingKind.Grove =>
        s(
          produceLine(Resource.Wood, Balance.WoodPerSecPerGrove, f, lang),
          spawnLine(UnitKind.Elf, Balance.ElfSpawnIntervalMs, f, lang),
          groveHealLine(Balance.GroveCorruptionHealPercentPerSec, f, lang)
        )
      case BuildingKind.Forest =>
        s(
          produceLine(Resource.Wood, Balance.WoodPerSecPerForest, f, lang),
          upgradeOfLine(BuildingKind.Grove, f, lang),
          entAuraLine(Balance.AuraDamagePerSec, lang),
          spawnLine(UnitKind.Elf, Balance.ElfSpawnIntervalMs, f, lang),
          groveHealLine(Balance.ForestCorruptionHealPercentPerSec, f, lang)
        )
      case BuildingKind.Jungle =>
        s(
          produceLine(Resource.Wood, Balance.WoodPerSecPerJungle, f, lang),
          upgradeOfLine(BuildingKind.Forest, f, lang),
          entAuraLine(Balance.AuraDamagePerSec, lang),
          spawnLine(UnitKind.Wolf, Balance.WolfSpawnIntervalMs, f, lang),
          groveHealLine(Balance.JungleCorruptionHealPercentPerSec, f, lang)
        )
      case BuildingKind.Stonehenge =>
        val treeLink = EntityNames.unitLink(f, UnitKind.Tree, lang)
        lang match
          case Lang.Fr =>
            s(
              s"Génère un $treeLink toutes les ${seconds(Balance.StonehengeSpawnIntervalMs)} secondes, qui part " +
                "attaquer l'adversaire comme n'importe quelle autre unité — mais compte quand même pour la " +
                "condition de victoire de la Nature (Expansion Inarrêtable) tant qu'il est en vie, même pendant " +
                "qu'il attaque."
            )
          case Lang.En =>
            s(
              s"Spawns an $treeLink every ${seconds(Balance.StonehengeSpawnIntervalMs)} seconds, which heads out " +
                "to attack the opponent like any other unit — but still counts toward Nature's victory condition " +
                "(Unstoppable Expansion) for as long as it's alive, even while it's attacking."
            )
      case BuildingKind.Cave =>
        s(
          produceLine(Resource.Fire, Balance.FirePerSecPerCave, f, lang),
          spawnLine(UnitKind.Goblin, Balance.GoblinSpawnIntervalMs, f, lang)
        )
      case BuildingKind.Labyrinth =>
        s(spawnLine(UnitKind.Minotaur, Balance.MinotaurSpawnIntervalMs, f, lang))
      case BuildingKind.Church =>
        s(
          produceLine(Resource.Light, Balance.LightPerSecPerEglise, f, lang),
          spawnLine(UnitKind.Paladin, Balance.PaladinSpawnIntervalMs, f, lang)
        )
      case BuildingKind.Watchtower =>
        lang match
          case Lang.Fr =>
            s(
              produceLine(Resource.Light, Balance.LightPerSecPerWatchtower, f, lang),
              s"N'envoie aucune unité. Inflige ${decimal(Balance.WatchtowerDamagePerSec)} dégâts par seconde à " +
                s"l'ennemi le plus proche jusqu'à ${Balance.WatchtowerRangeCells} cases de distance."
            )
          case Lang.En =>
            s(
              produceLine(Resource.Light, Balance.LightPerSecPerWatchtower, f, lang),
              s"Spawns no unit. Deals ${decimal(Balance.WatchtowerDamagePerSec)} damage per second to the " +
                s"nearest enemy within ${Balance.WatchtowerRangeCells} cells."
            )
      case BuildingKind.Angel =>
        lang match
          case Lang.Fr =>
            s(
              produceLine(Resource.Light, Balance.LightPerSecPerAngel, f, lang),
              s"N'envoie aucune unité. Inflige ${decimal(Balance.AngelDamagePerSec)} dégâts par seconde aux " +
                s"unités adjacentes, et ralentit leur vitesse de déplacement de ${percentPoints(Balance.AngelSlowFraction * 100)}."
            )
          case Lang.En =>
            s(
              produceLine(Resource.Light, Balance.LightPerSecPerAngel, f, lang),
              s"Spawns no unit. Deals ${decimal(Balance.AngelDamagePerSec)} damage per second to adjacent " +
                s"units, and slows their movement speed by ${percentPoints(Balance.AngelSlowFraction * 100)}."
            )
      case BuildingKind.Tomb =>
        s(
          produceLine(Resource.Shadow, Balance.ShadowPerSecPerTomb, f, lang),
          spawnLine(UnitKind.Zombie, Balance.ZombieSpawnIntervalMs, f, lang)
        )
      case BuildingKind.BlackCastle =>
        s(
          produceLine(Resource.Shadow, Balance.ShadowPerSecPerBlackCastle, f, lang),
          spawnLine(UnitKind.Vampire, Balance.VampireSpawnIntervalMs, f, lang)
        )
      case BuildingKind.DeathHouse =>
        s(
          produceLine(Resource.Shadow, Balance.ShadowPerSecPerDeathHouse, f, lang),
          spawnLine(UnitKind.Necromancer, Balance.NecromancerSpawnIntervalMs, f, lang)
        )
      case BuildingKind.PassingGate =>
        val shadowLink = EntityNames.resourceLink(f, Resource.Shadow, lang)
        lang match
          case Lang.Fr =>
            s(
              "N'envoie aucune unité. Inflige " + s"${decimal(Balance.PassingGateDamagePerSec)}" +
                " dégâts par seconde à toute unité se trouvant sur l'une de ses 4 cases adjacentes.",
              s"Chaque fois qu'une unité meurt sur l'une de ces 4 cases (peu importe ce qui l'a tuée), le Portail " +
                s"draine ${percentPoints(Balance.PassingGateDeathShadowFraction * 100)} des ressources totales " +
                s"actuelles de son propriétaire et les convertit en $shadowLink bonus."
            )
          case Lang.En =>
            s(
              s"Spawns no unit. Deals ${decimal(Balance.PassingGateDamagePerSec)} damage per second to any unit " +
                "standing on one of its 4 adjacent cells.",
              "Whenever a unit dies on one of those 4 cells (no matter what killed it), the Passing Gate drains " +
                s"${percentPoints(Balance.PassingGateDeathShadowFraction * 100)} of its owner's current total " +
                s"resources and converts them into bonus $shadowLink."
            )
      case BuildingKind.LaboFondamental =>
        val crystalLink = EntityNames.resourceLink(f, Resource.Crystal, lang)
        val labLinks = List(
          BuildingKind.LaboNaturel,
          BuildingKind.LaboSombre,
          BuildingKind.LaboDeRecherche,
          BuildingKind.LaboDeLaLoi,
          BuildingKind.LaboDuChaos
        ).map(EntityNames.buildingLink(f, _, lang))
        val notePage = EntityNames.outOfScopeLink(labNoteText(lang), f, Faction.Science, "Note sur les laboratoires.md", lang)
        lang match
          case Lang.Fr =>
            s(
              "Le seul bâtiment de Science constructible directement — un laboratoire générique, sans ligne de " +
                "recherche propre.",
              s"Produit ${decimal(Balance.CrystalPerSecPerLaboFondamental)} $crystalLink par seconde, sans aucun " +
                "bonus de recherche.",
              s"Peut ensuite être amélioré vers l'un des cinq laboratoires spécifiques (${labLinks.mkString(", ")}), " +
                s"au coût habituel de ce laboratoire — voir $notePage. Cette mise à niveau donne immédiatement le " +
                "niveau 1 (gratuit) de la recherche associée.",
              s"Un bâtiment amélioré grandit de ${percentPoints(Balance.LaboSizeGrowthPerResearchLevel * 100)} par " +
                s"niveau de recherche (jusqu'à ${percentPoints(Balance.LaboSizeGrowthPerResearchLevel * Balance.MaxResearchLevel * 100)} " +
                s"de plus au niveau ${Balance.MaxResearchLevel}) — le Labo Fondamental lui-même reste toujours à sa taille de base."
            )
          case Lang.En =>
            s(
              "The only Science building buildable directly — a generic lab with no research line of its own.",
              s"Produces ${decimal(Balance.CrystalPerSecPerLaboFondamental)} $crystalLink per second, with no " +
                "research bonus.",
              s"Can then be upgraded into one of the five specific labs (${labLinks.mkString(", ")}), at that " +
                s"lab's usual cost — see $notePage. This upgrade immediately grants a free level 1 of the " +
                "matching research line.",
              s"An upgraded building grows ${percentPoints(Balance.LaboSizeGrowthPerResearchLevel * 100)} per " +
                s"research level (up to ${percentPoints(Balance.LaboSizeGrowthPerResearchLevel * Balance.MaxResearchLevel * 100)} " +
                s"larger at level ${Balance.MaxResearchLevel}) — the Base Lab itself always stays its base size."
            )
      case BuildingKind.LaboNaturel =>
        specificLabBody(f, kind, Balance.CrystalPerSecPerLaboNaturel, "Recherches naturelles.md", lang)
      case BuildingKind.LaboSombre =>
        specificLabBody(f, kind, Balance.CrystalPerSecPerLaboSombre, "Recherches Sombres.md", lang)
      case BuildingKind.LaboDeRecherche =>
        specificLabBody(f, kind, Balance.CrystalPerSecPerLaboDeRecherche, "Recherche fondamentale.md", lang)
      case BuildingKind.LaboDeLaLoi =>
        specificLabBody(f, kind, Balance.CrystalPerSecPerLaboDeLaLoi, "Recherches loyales.md", lang)
      case BuildingKind.LaboDuChaos =>
        specificLabBody(f, kind, Balance.CrystalPerSecPerLaboDuChaos, "Recherches chaotiques.md", lang)

  private def labNoteText(lang: Lang): String = lang match
    case Lang.Fr => "Note sur les laboratoires"
    case Lang.En => "Note on labs"

  private def specificLabBody(
      f: Faction,
      kind: BuildingKind,
      crystalPerSec: Double,
      researchFrFile: String,
      lang: Lang
  ): String =
    val crystalLink = EntityNames.resourceLink(f, Resource.Crystal, lang)
    val baseLabLink = EntityNames.buildingLink(f, BuildingKind.LaboFondamental, lang)
    val researchName = researchFrFile.stripSuffix(".md")
    val researchLink = EntityNames.outOfScopeLink(researchName, f, Faction.Science, researchFrFile, lang)
    lang match
      case Lang.Fr =>
        s(
          s"Amélioration du $baseLabLink — pas constructible directement.",
          s"Produit ${decimal(crystalPerSec)} $crystalLink par seconde, augmenté de " +
            s"${percentPoints(Balance.LaboCrystalBoostPerResearchLevel * 100)} par niveau de recherche.",
          s"Débloque $researchLink, au niveau 1 dès la mise à niveau (gratuit)."
        )
      case Lang.En =>
        s(
          s"Upgrade of the $baseLabLink — not directly buildable.",
          s"Produces ${decimal(crystalPerSec)} $crystalLink per second, boosted by " +
            s"${percentPoints(Balance.LaboCrystalBoostPerResearchLevel * 100)} per research level.",
          s"Unlocks $researchLink, at level 1 as soon as it's upgraded (free)."
        )

  private def produceLine(resource: Resource, perSec: Double, from: Faction, lang: Lang): String =
    val link = EntityNames.resourceLink(from, resource, lang)
    lang match
      case Lang.Fr => s"Produit ${decimal(perSec)} $link par seconde."
      case Lang.En => s"Produces ${decimal(perSec)} $link per second."

  // Every unit kind a building spawns happens to be grammatically masculine in French
  // ("un Elfe", "un Gobelin", "un Nécromancien"...), so "un" is safe to hardcode here
  // rather than needing a per-kind gender table.
  private def spawnLine(unit: UnitKind, intervalMs: Double, from: Faction, lang: Lang): String =
    val link = EntityNames.unitLink(from, unit, lang)
    lang match
      case Lang.Fr => s"Toutes les ${seconds(intervalMs)} secondes, génère un $link."
      case Lang.En =>
        val article = englishIndefiniteArticle(EntityNames.unitName(unit, Lang.En))
        s"Every ${seconds(intervalMs)} seconds, spawns $article $link."

  private def englishIndefiniteArticle(name: String): String =
    if name.nonEmpty && "AEIOU".contains(name.head.toUpper) then "an" else "a"

  private def upgradeOfLine(previous: BuildingKind, from: Faction, lang: Lang): String =
    val link = EntityNames.buildingLink(from, previous, lang)
    lang match
      // Every kind that can be an upgrade source is masculine except Forêt (feminine) —
      // a small hardcoded exception rather than a full gender table for two kinds.
      case Lang.Fr if previous == BuildingKind.Forest => s"Amélioration de la $link."
      case Lang.Fr                                    => s"Amélioration du $link."
      case Lang.En                                     => s"Upgrade of the $link."

  private def entAuraLine(dmgPerSec: Double, lang: Lang): String = lang match
    case Lang.Fr =>
      s"Contient des Ents qui attaquent les unités passant sur les cases adjacentes pour ${decimal(dmgPerSec)} dégâts/sec."
    case Lang.En =>
      s"Home to Ents that deal ${decimal(dmgPerSec)} damage/sec to units passing on adjacent cells."

  private def groveHealLine(healPercentPerSec: Double, from: Faction, lang: Lang): String =
    val corruptionLink = EntityNames.outOfScopeLink(corruptionText(lang), from, Faction.Mort, "Corruption.md", lang)
    lang match
      case Lang.Fr =>
        s"Soigne la corruption (voir $corruptionLink) de lui-même et des 8 bâtiments environnants de " +
          s"${percentPoints(healPercentPerSec)} par seconde."
      case Lang.En =>
        s"Heals corruption (see $corruptionLink) on itself and the 8 surrounding buildings by " +
          s"${percentPoints(healPercentPerSec)} per second."

  private def corruptionText(lang: Lang): String = lang match
    case Lang.Fr => "Corruption"
    case Lang.En => "Corruption"

  // ── Units ────────────────────────────────────────────────────────────────

  def unitBody(kind: UnitKind, lang: Lang): String =
    val f = Faction.of(kind)
    kind match
      case UnitKind.Elf =>
        s(
          spawnedByLine(BuildingKind.Grove, f, lang),
          plunderLine(List(Resource.Wood -> Balance.PlunderPerUnit), f, lang)
        )
      case UnitKind.Goblin =>
        s(
          spawnedByLine(BuildingKind.Cave, f, lang),
          plunderLine(List(Resource.Wood -> Balance.PlunderPerUnit, Resource.Fire -> Balance.PlunderPerUnit), f, lang)
        )
      case UnitKind.Minotaur =>
        s(
          spawnedByLine(BuildingKind.Labyrinth, f, lang),
          plunderLine(
            List(Resource.Wood -> Balance.MinotaurPlunderPerUnit, Resource.Fire -> Balance.MinotaurPlunderPerUnit),
            f,
            lang
          )
        )
      case UnitKind.Paladin =>
        val shield = decimal(Balance.PaladinAuraDamageReductionPerSec)
        val auraLine = lang match
          case Lang.Fr => s"Aura : protège les unités alliées adjacentes de $shield dégâts par seconde."
          case Lang.En => s"Aura: shields adjacent allied units from $shield damage per second."
        s(spawnedByLine(BuildingKind.Church, f, lang), auraLine)
      case UnitKind.Wolf =>
        val elfLink = EntityNames.unitLink(f, UnitKind.Elf, lang)
        val boost = percentPoints((Balance.WolfSpeedAuraMultiplier - 1) * 100)
        lang match
          case Lang.Fr =>
            s(
              s"Se déplace ${decimal(Balance.WolfSpeedAuraMultiplier)}x plus vite que les unités standard comme " +
                s"l'$elfLink, et augmente la vitesse de déplacement des unités alliées à " +
                s"${Balance.WolfSpeedAuraRangeCells} cases de distance de $boost."
            )
          case Lang.En =>
            s(
              s"Moves ${decimal(Balance.WolfSpeedAuraMultiplier)}x faster than standard units like the $elfLink, " +
                s"and speeds up allied units within ${Balance.WolfSpeedAuraRangeCells} cells by $boost."
            )
      case UnitKind.Tree =>
        val zombieLink = EntityNames.outOfScopeLink(EntityNames.unitName(UnitKind.Zombie, lang), f, Faction.Mort, "Zombie.md", lang)
        val cloneFraction = percentPoints((1.0 - Balance.TreeCloneSizeStepFraction) * 100)
        val minFraction = percentPoints(Balance.TreeMinCloneSizeFraction * 100)
        lang match
          case Lang.Fr =>
            s(
              spawnedByLine(BuildingKind.Stonehenge, f, lang),
              s"Se déplace lentement (moitié de la vitesse standard), comme un $zombieLink.",
              s"Toutes les ${seconds(Balance.TreeCloneIntervalMs)} secondes, il s'arrête pendant " +
                s"${seconds(Balance.TreeCloneFreezeMs)} secondes et invoque un clone de lui-même sur la case " +
                s"suivante de son chemin, puis reprend sa marche. Ce nouveau clone fait $cloneFraction de la " +
                s"taille et des PV de celui qui l'a créé, et peut lui-même se cloner de la même façon — jusqu'à " +
                s"un minimum de $minFraction de la taille d'origine.",
              "Part attaquer l'adversaire comme n'importe quelle autre unité, mais compte quand même pour la " +
                "condition de victoire de la Nature (Expansion Inarrêtable) de son propriétaire tant qu'il est " +
                "en vie, même pendant qu'il attaque."
            )
          case Lang.En =>
            s(
              spawnedByLine(BuildingKind.Stonehenge, f, lang),
              s"Moves slowly (half standard speed), like a $zombieLink.",
              s"Every ${seconds(Balance.TreeCloneIntervalMs)} seconds, it stops for " +
                s"${seconds(Balance.TreeCloneFreezeMs)} seconds and spawns a clone of itself on the next cell of " +
                s"its path, then resumes walking. The new clone is $cloneFraction the size and HP of the one " +
                s"that made it, and can itself clone the same way — down to a minimum of $minFraction of the " +
                "original size.",
              "Heads out to attack the opponent like any other unit, but still counts toward its owner's Nature " +
                "victory condition (Unstoppable Expansion) for as long as it's alive, even while it's attacking."
            )
      case UnitKind.Zombie =>
        val corruptionLink = EntityNames.outOfScopeLink(corruptionText(lang), f, Faction.Mort, "Corruption.md", lang)
        val rate = percentPoints(Balance.ZombieCorruptionPercentPerSec)
        val speedLine = lang match
          case Lang.Fr => "Se déplace lentement (moitié de la vitesse standard)."
          case Lang.En => "Moves slowly (half standard speed)."
        val corruptLine = lang match
          case Lang.Fr => s"Corrompt les bâtiments ennemis adjacents de $rate par seconde (voir $corruptionLink)."
          case Lang.En => s"Corrupts adjacent enemy buildings by $rate per second (see $corruptionLink)."
        s(spawnedByLine(BuildingKind.Tomb, f, lang), speedLine, corruptLine)
      case UnitKind.Vampire =>
        val corruptionLink = EntityNames.outOfScopeLink(corruptionText(lang), f, Faction.Mort, "Corruption.md", lang)
        val rate = percentPoints(Balance.VampireCorruptionPercentPerSec)
        val reduction = percentPoints(Balance.VampireDamageReductionFraction * 100)
        val paladinLink = EntityNames.unitLink(f, UnitKind.Paladin, lang)
        val speedMultiplier = decimal(Balance.VampireSpeedPerMs / Balance.ElfSpeedPerMs)
        val speedLine = lang match
          case Lang.Fr => s"Se déplace vite (${speedMultiplier}x la vitesse standard)."
          case Lang.En => s"Moves fast (${speedMultiplier}x standard speed)."
        val corruptLine = lang match
          case Lang.Fr => s"Corrompt les bâtiments ennemis adjacents de $rate par seconde (voir $corruptionLink)."
          case Lang.En => s"Corrupts adjacent enemy buildings by $rate per second (see $corruptionLink)."
        val reductionLine = lang match
          case Lang.Fr => s"Réduit les dégâts qu'il subit de $reduction (mais n'est pas protégé par l'aura du $paladinLink)."
          case Lang.En => s"Takes $reduction less damage (but isn't shielded by the $paladinLink's aura)."
        s(spawnedByLine(BuildingKind.BlackCastle, f, lang), speedLine, corruptLine, reductionLine)
      case UnitKind.Necromancer =>
        val zombieLink = EntityNames.outOfScopeLink(EntityNames.unitName(UnitKind.Zombie, lang), f, Faction.Mort, "Zombie.md", lang)
        val soulLink = EntityNames.unitLink(f, UnitKind.Soul, lang)
        lang match
          case Lang.Fr =>
            s(
              spawnedByLine(BuildingKind.DeathHouse, f, lang),
              s"Se déplace lentement (moitié de la vitesse standard), comme un $zombieLink.",
              s"Toutes les ${seconds(Balance.SoulSummonIntervalMs)} secondes, invoque une $soulLink — pendant " +
                s"${seconds(Balance.NecromancerSummonFreezeMs)} seconde, il reste immobile."
            )
          case Lang.En =>
            s(
              spawnedByLine(BuildingKind.DeathHouse, f, lang),
              s"Moves slowly (half standard speed), like a $zombieLink.",
              s"Every ${seconds(Balance.SoulSummonIntervalMs)} seconds, summons a $soulLink — for " +
                s"${seconds(Balance.NecromancerSummonFreezeMs)} second, it stands still."
            )
      case UnitKind.Soul =>
        val corruptionLink = EntityNames.outOfScopeLink(corruptionText(lang), f, Faction.Mort, "Corruption.md", lang)
        val necroLink = EntityNames.unitLink(f, UnitKind.Necromancer, lang)
        val rate = percentPoints(Balance.SoulCorruptionPercentPerSec)
        val heal = decimal(Balance.SoulHealPerSecPerBuilding)
        lang match
          case Lang.Fr =>
            s(
              s"Invoquée par le $necroLink.",
              "Se déplace à vitesse normale.",
              s"Corrompt les bâtiments ennemis adjacents de $rate par seconde (voir $corruptionLink).",
              s"Chaque fois qu'elle corrompt un bâtiment, elle soigne le Nécromancien qui l'a invoquée de $heal " +
                "PV par seconde et par bâtiment corrompu (jusqu'à son maximum)."
            )
          case Lang.En =>
            s(
              s"Summoned by the $necroLink.",
              "Moves at normal speed.",
              s"Corrupts adjacent enemy buildings by $rate per second (see $corruptionLink).",
              s"Every time it corrupts a building, it heals the Necromancer that summoned it $heal HP per second " +
                "per building corrupted (up to its maximum)."
            )

  private def spawnedByLine(building: BuildingKind, from: Faction, lang: Lang): String =
    val link = EntityNames.buildingLink(from, building, lang)
    lang match
      case Lang.Fr => s"Produit par $link."
      case Lang.En => s"Spawned by $link."

  private def plunderLine(amounts: List[(Resource, Double)], from: Faction, lang: Lang): String =
    val links = amounts.map { case (res, amount) => s"${decimal(amount)} ${EntityNames.resourceLink(from, res, lang)}" }
    lang match
      case Lang.Fr => s"Pille ${links.mkString(" et ")}."
      case Lang.En => s"Plunders ${links.mkString(" and ")}."
