package towerdefense.domain.i18n

import towerdefense.domain.*

// Short, in-game tooltip/hover fragments — the terse counterpart to EntityText's doc-page
// prose. Kept as its own file (not reusing EntityText's sentences) because the two have
// genuinely different jobs: a vault page has room for a full paragraph, but the game's
// #tooltip div is a 260px-wide box that also has to fit live per-tick numbers (a
// building's *current* production rate including research/Engendre boosts, a live spawn
// countdown) that a static doc page never needs. Every number is still passed in from
// GameApp (which already computes it against Balance/BuildingSpecs/CombatEngine), not
// hardcoded here — this module only owns the phrasing, in both languages.
object TooltipText:
  import NumberFormat.decimal

  private def costText(cost: Map[Resource, Double], lang: Lang): String =
    cost.toList
      .sortBy(_._1.ordinal)
      .map { case (res, amount) => s"${decimal(amount)} ${EntityNames.resourceName(res, lang)}" }
      .mkString(" + ")

  // The static build-button tooltip, shown before anything is placed — cost plus, for a
  // producing kind, its rate; the unit it spawns (if any) is appended separately by the
  // caller via unitAbilitySummary, same split GameApp already made (spawnAbilitySuffix).
  def buildingButtonTooltip(kind: BuildingKind, cost: Map[Resource, Double], produces: Map[Resource, Double], lang: Lang): String =
    val name = EntityNames.buildingName(kind, lang)
    val cText = costText(cost, lang)
    val rateText = produces.toList.sortBy(_._1.ordinal) match
      case Nil => ""
      case rates =>
        val joined = rates.map { case (res, rate) => s"+${decimal(rate)} ${EntityNames.resourceName(res, lang)}/s" }.mkString(", ")
        lang match
          case Lang.Fr => s" $joined."
          case Lang.En => s" $joined."
    lang match
      case Lang.Fr => s"$name — coût $cText.$rateText"
      case Lang.En => s"$name — cost $cText.$rateText"

  def costLabel(lang: Lang): String = lang match
    case Lang.Fr => "coût"
    case Lang.En => "cost"

  // Appended to a building's tooltip (button or live hover) whenever it spawns a unit, so
  // the unit's own value proposition is visible before it's even placed — mirrors
  // GameApp's original spawnAbilitySuffix/unitAbilitySummary split.
  def unitAbilitySummary(kind: UnitKind, lang: Lang): String = kind match
    case UnitKind.Elf =>
      plunders(List(Resource.Wood -> Balance.PlunderPerUnit), lang)
    case UnitKind.Goblin =>
      plunders(List(Resource.Wood -> Balance.PlunderPerUnit, Resource.Fire -> Balance.PlunderPerUnit), lang)
    case UnitKind.Minotaur =>
      plunders(List(Resource.Wood -> Balance.MinotaurPlunderPerUnit, Resource.Fire -> Balance.MinotaurPlunderPerUnit), lang)
    case UnitKind.Paladin =>
      noPlunder(lang) + shields(Balance.PaladinAuraDamageReductionPerSec, lang)
    case UnitKind.Wolf =>
      noPlunder(lang) + speedsUp(Balance.WolfSpeedAuraRangeCells, (Balance.WolfSpeedAuraMultiplier - 1) * 100, lang)
    case UnitKind.Zombie =>
      noPlunder(lang) + corrupts(Balance.ZombieCorruptionPercentPerSec, lang)
    case UnitKind.Vampire =>
      noPlunder(lang) + corrupts(Balance.VampireCorruptionPercentPerSec, lang) +
        takesLessDamage(Balance.VampireDamageReductionFraction * 100, lang)
    case UnitKind.Necromancer =>
      noPlunder(lang) + invokesSoul(Balance.SoulSummonIntervalMs, lang)
    case UnitKind.Soul =>
      noPlunder(lang) + corrupts(Balance.SoulCorruptionPercentPerSec, lang)
    case UnitKind.Tree =>
      noPlunder(lang) + clones(Balance.TreeCloneIntervalMs, Balance.TreeMinCloneSizeFraction * 100, lang)

  private def plunders(amounts: List[(Resource, Double)], lang: Lang): String =
    val text = amounts.map { case (res, amt) => s"${decimal(amt)} ${EntityNames.resourceName(res, lang)}" }.mkString(" + ")
    lang match
      case Lang.Fr => s"pille $text à l'arrivée"
      case Lang.En => s"plunders $text on arrival"

  private def noPlunder(lang: Lang): String = lang match
    case Lang.Fr => "ne pille pas — "
    case Lang.En => "doesn't plunder — "

  private def shields(dmgPerSec: Double, lang: Lang): String = lang match
    case Lang.Fr => s"protège les alliés adjacents de ${decimal(dmgPerSec)} dégâts/sec"
    case Lang.En => s"shields adjacent allies from ${decimal(dmgPerSec)} dmg/s"

  private def speedsUp(rangeCells: Int, boostPercent: Double, lang: Lang): String = lang match
    case Lang.Fr => s"augmente la vitesse des alliés à $rangeCells cases de ${decimal(boostPercent)}%"
    case Lang.En => s"speeds up allies within $rangeCells cells by ${decimal(boostPercent)}%"

  private def corrupts(percentPerSec: Double, lang: Lang): String = lang match
    case Lang.Fr => s"corrompt les bâtiments ennemis adjacents de ${decimal(percentPerSec)}%/sec"
    case Lang.En => s"corrupts adjacent enemy buildings by ${decimal(percentPerSec)}%/s"

  private def takesLessDamage(reductionPercent: Double, lang: Lang): String = lang match
    case Lang.Fr => s", subit ${decimal(reductionPercent)}% de dégâts en moins"
    case Lang.En => s", takes ${decimal(reductionPercent)}% less damage"

  private def invokesSoul(intervalMs: Double, lang: Lang): String =
    val secs = NumberFormat.seconds(intervalMs)
    lang match
      case Lang.Fr => s"invoque une Âme toutes les ${secs}s"
      case Lang.En => s"invokes a Soul every ${secs}s"

  private def clones(intervalMs: Double, minSizePercent: Double, lang: Lang): String =
    val secs = NumberFormat.seconds(intervalMs)
    lang match
      case Lang.Fr =>
        s"se clone (en plus petit) toutes les ${secs}s (jusqu'à ${decimal(minSizePercent)}% de taille), " +
          "compte pour la victoire de son propriétaire tant qu'il est en vie"
      case Lang.En =>
        s"clones a smaller copy of itself every ${secs}s (down to ${decimal(minSizePercent)}% size), " +
          "counting toward its owner's victory the whole time"

  // A live creature's hover text — same numbers as unitAbilitySummary, plus its current
  // HP and (for a Tree) its current clone size, since those vary per-instance.
  def creatureHoverText(kind: UnitKind, lang: Lang, hp: Int, maxHp: Int, sizePercent: Option[Int]): String =
    val name = EntityNames.unitName(kind, lang)
    val sizeNote = sizePercent.filter(_ < 100).map(p => s" ($p%)").getOrElse("")
    val ability = unitAbilitySummary(kind, lang)
    s"$name$sizeNote — HP $hp/$maxHp, $ability"

  // Zombie/Vampire/Soul corrupt buildings gradually (Balance.CorruptionMaxPercent) —
  // shown only once corruption has actually started, same trigger GameApp always used.
  def corruptionSuffix(corruptionPercent: Double, maxPercent: Double, lang: Lang): String =
    if corruptionPercent <= 0 then ""
    else
      val pct = corruptionPercent.round
      val max = maxPercent.toInt
      lang match
        case Lang.Fr => s" — corrompu à $pct%/$max%"
        case Lang.En => s" — corrupted $pct%/$max%"

  def destroyLabel(refundText: String, lang: Lang): String = lang match
    case Lang.Fr => s"Détruire ($refundText)"
    case Lang.En => s"Destroy ($refundText)"

  def upgradeLabel(kind: BuildingKind, costText: String, lang: Lang): String =
    val name = EntityNames.buildingName(kind, lang)
    lang match
      case Lang.Fr => s"Améliorer en $name ($costText)"
      case Lang.En => s"Upgrade to $name ($costText)"

  def researchLabel(nextLevel: Int, maxLevel: Int, costText: String, effect: String, lang: Lang): String = lang match
    case Lang.Fr => s"Recherche niveau $nextLevel/$maxLevel ($costText) → $effect"
    case Lang.En => s"Research level $nextLevel/$maxLevel ($costText) → $effect"

  def researchLevelText(level: Int, maxLevel: Int, effect: Option[String], lang: Lang): String =
    (level, effect) match
      case (l, _) if l <= 0 =>
        lang match
          case Lang.Fr => s"recherche niveau 0/$maxLevel (aucun bonus)"
          case Lang.En => s"research level 0/$maxLevel (no bonus yet)"
      case (l, Some(e)) =>
        lang match
          case Lang.Fr => s"recherche niveau $l/$maxLevel ($e)"
          case Lang.En => s"research level $l/$maxLevel ($e)"
      case (l, None) =>
        lang match
          case Lang.Fr => s"recherche niveau $l/$maxLevel"
          case Lang.En => s"research level $l/$maxLevel"

  def researchEffectSummary(labKind: BuildingKind, magnitude: Double, lang: Lang): String =
    labKind match
      case BuildingKind.LaboNaturel =>
        val v = decimal(magnitude * 100)
        lang match
          case Lang.Fr => s"-$v% coût des bâtiments"
          case Lang.En => s"-$v% building cost"
      case BuildingKind.LaboSombre =>
        val v = decimal(magnitude * 100)
        lang match
          case Lang.Fr => s"+$v% conditions de victoire adverses"
          case Lang.En => s"+$v% opponent's victory targets"
      case BuildingKind.LaboDuChaos =>
        val v = decimal(magnitude)
        lang match
          case Lang.Fr => s"+$v pillage par ressource, chaque unité"
          case Lang.En => s"+$v plunder per resource, every unit"
      case BuildingKind.LaboDeLaLoi =>
        val v = decimal(magnitude * 100)
        lang match
          case Lang.Fr => s"+$v% dégâts des bâtiments"
          case Lang.En => s"+$v% building damage"
      case BuildingKind.LaboDeRecherche =>
        val level = magnitude.toInt
        lang match
          case Lang.Fr => s"victoire automatique une fois chaque autre labo au niveau $level+"
          case Lang.En => s"wins outright once every other lab reaches level $level+"
      case _ => ""

  def spawnsNothing(lang: Lang): String = lang match
    case Lang.Fr => "n'envoie aucune unité"
    case Lang.En => "spawns no unit"

  // ── Building live-hover building blocks ─────────────────────────────────
  // Small composable fragments `perKindHoverText` (GameApp) strings together per kind —
  // kept granular rather than one big per-kind template, since which fragments a given
  // kind needs (rate? aura? a spawn countdown? a research level?) varies too much for a
  // single shape (see BuildingSpecs' doc on why combat abilities stay kind-based).

  def rate(resource: Resource, amountPerSec: Double, lang: Lang): String =
    s"+${decimal(amountPerSec)} ${EntityNames.resourceName(resource, lang)}/s"

  def nextSpawnIn(unitKind: UnitKind, seconds: Int, lang: Lang): String =
    val name = EntityNames.unitName(unitKind, lang)
    lang match
      case Lang.Fr => s"prochain $name dans ${seconds}s"
      case Lang.En => s"next $name in ${seconds}s"

  def adjacentDamage(dmgPerSec: Double, lang: Lang): String = lang match
    case Lang.Fr => s"${decimal(dmgPerSec)} dégâts/sec aux ennemis adjacents"
    case Lang.En => s"${decimal(dmgPerSec)} dmg/s to adjacent enemies"

  def adjacentDamageAndSlow(dmgPerSec: Double, slowPercent: Double, lang: Lang): String = lang match
    case Lang.Fr => s"${decimal(dmgPerSec)} dégâts/sec aux ennemis adjacents, ralentis de ${decimal(slowPercent)}%"
    case Lang.En => s"${decimal(dmgPerSec)} dmg/s to adjacent enemies, slows them ${decimal(slowPercent)}%"

  def rangedDamage(dmgPerSec: Double, rangeCells: Int, lang: Lang): String = lang match
    case Lang.Fr => s"${decimal(dmgPerSec)} dégâts/sec à l'ennemi le plus proche jusqu'à $rangeCells cases"
    case Lang.En => s"${decimal(dmgPerSec)} dmg/s to the nearest enemy within $rangeCells cells"

  def passingGateAbility(dmgPerSec: Double, harvestPercent: Double, lang: Lang): String = lang match
    case Lang.Fr =>
      s"${decimal(dmgPerSec)} dégâts/sec sur ses 4 cases adjacentes, récolte ${decimal(harvestPercent)}% de vos " +
        "ressources totales en ombre à chaque mort à proximité"
    case Lang.En =>
      s"${decimal(dmgPerSec)} dmg/s to enemies on its 4 adjacent cells, harvests ${decimal(harvestPercent)}% " +
        "of your own total resources as shadow on every nearby death"

  def noResearchBonusYet(lang: Lang): String = lang match
    case Lang.Fr => "aucun bonus de recherche — améliorez-le en un labo spécifique ci-dessous"
    case Lang.En => "no research bonus — upgrade it into a specific lab below"

  def noSpawnLabel(lang: Lang): String = lang match
    case Lang.Fr => "n'envoie aucune ressource"
    case Lang.En => "spawns no resource"

  // The extra sentence a building's own tooltip needs beyond cost/production/spawn —
  // either a combat ability BuildingSpec doesn't model (Watchtower/Angel/PassingGate's
  // aura or ranged damage — see BuildingSpecs' doc on why those stay hand-coded special
  // cases), or Grove's own upgrade-chain hint. Empty for every other kind, whose tooltip
  // is already complete from buildingButtonTooltip + unitAbilitySummary alone.
  def buildingOwnAbility(kind: BuildingKind, lang: Lang): String = kind match
    case BuildingKind.Grove =>
      val forest = EntityNames.buildingName(BuildingKind.Forest, lang)
      val jungle = EntityNames.buildingName(BuildingKind.Jungle, lang)
      lang match
        case Lang.Fr => s" Devient $forest puis $jungle en s'améliorant."
        case Lang.En => s" Upgrades into a $forest, then a $jungle."
    case BuildingKind.Watchtower =>
      val dmg = decimal(Balance.WatchtowerDamagePerSec)
      lang match
        case Lang.Fr =>
          s" ${spawnsNothing(lang).capitalize} — inflige plutôt $dmg dégâts/sec à l'ennemi le plus proche " +
            s"jusqu'à ${Balance.WatchtowerRangeCells} cases"
        case Lang.En =>
          s" ${spawnsNothing(lang).capitalize} — instead inflicts $dmg dmg/s to the nearest enemy within " +
            s"${Balance.WatchtowerRangeCells} cells"
    case BuildingKind.Angel =>
      val dmg = decimal(Balance.AngelDamagePerSec)
      val slow = decimal(Balance.AngelSlowFraction * 100)
      lang match
        case Lang.Fr =>
          s" ${spawnsNothing(lang).capitalize} — inflige plutôt $dmg dégâts/sec aux unités adjacentes et " +
            s"ralentit leur vitesse de $slow%"
        case Lang.En =>
          s" ${spawnsNothing(lang).capitalize} — instead inflicts $dmg dmg/s to adjacent enemies and slows " +
            s"them by $slow%"
    case BuildingKind.PassingGate =>
      val dmg = decimal(Balance.PassingGateDamagePerSec)
      val harvest = decimal(Balance.PassingGateDeathShadowFraction * 100)
      lang match
        case Lang.Fr =>
          s" ${spawnsNothing(lang).capitalize} — inflige $dmg dégâts/sec aux ennemis sur ses 4 cases " +
            s"adjacentes, et récolte $harvest% de vos ressources totales en ombre bonus à chaque mort sur " +
            "l'une de ces cases"
        case Lang.En =>
          s" ${spawnsNothing(lang).capitalize} — inflicts $dmg dmg/s to enemies on its 4 adjacent cells, " +
            s"and harvests $harvest% of your own total resources as bonus shadow whenever any creature dies " +
            "on one of those cells"
    case BuildingKind.LaboFondamental =>
      lang match
        case Lang.Fr =>
          " sans bonus de recherche propre. Améliorez-le en un labo spécifique pour débloquer sa ligne de " +
            "recherche (niveau 1 gratuit) — un seul labo de chaque type spécifique par maze à la fois"
        case Lang.En =>
          " with no research bonus of its own. Upgrade it into a specific lab to unlock that lab's own " +
            "research line (starting at a free level 1) — only one lab of each specific kind per maze at a time"
    case _ => ""
