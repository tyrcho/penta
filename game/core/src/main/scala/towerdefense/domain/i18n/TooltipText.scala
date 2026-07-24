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
//
// Every unit's ability summary and every building's "own ability beyond cost/production"
// sentence is static per kind (it only ever reads fixed Balance constants, never anything
// per-instance), so each is written once as a value in `unitAbilities`/`buildingOwnAbilities`
// below — a Map literal, not a `kind match`. `researchEffectSummary` is the one exception
// that still needs a per-call parameter (a lab's current research level's magnitude, which
// varies at runtime) — it's a Map of small functions instead, keyed the same way.
object TooltipText:
  import NumberFormat.decimal

  // Not private: EntityText's per-lab wiki page reuses this exact formatting for its
  // per-level cost table, so a cost string never reads differently in the wiki than in
  // the in-game tooltip.
  def costText(cost: Map[Resource, Double], lang: Lang): String =
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
    val rates = produces.toList.sortBy(_._1.ordinal)
    val rateText =
      if rates.isEmpty then ""
      else " " + rates.map { case (res, rate) => s"+${decimal(rate)} ${EntityNames.resourceName(res, lang)}/s" }.mkString(", ") + "."
    val costWord = if lang == Lang.Fr then "coût" else "cost"
    s"$name — $costWord $cText.$rateText"

  // ── Unit ability summaries — one entry per kind, all static (Balance-only) data ──────

  private def plunders(amounts: List[(Resource, Double)]): I18nText =
    val frText = amounts.map { case (res, amt) => s"${decimal(amt)} ${EntityNames.resourceName(res, Lang.Fr)}" }.mkString(" + ")
    val enText = amounts.map { case (res, amt) => s"${decimal(amt)} ${EntityNames.resourceName(res, Lang.En)}" }.mkString(" + ")
    I18nText(fr = s"pille $frText à l'arrivée", en = s"plunders $enText on arrival")

  private val noPlunder: I18nText = I18nText(fr = "ne pille pas — ", en = "doesn't plunder — ")

  private def shields(dmgPerSec: Double): I18nText = I18nText(
    fr = s"protège les alliés adjacents de ${decimal(dmgPerSec)} dégâts/sec",
    en = s"shields adjacent allies from ${decimal(dmgPerSec)} dmg/s"
  )

  private def speedsUp(rangeCells: Int, boostPercent: Double): I18nText = I18nText(
    fr = s"augmente la vitesse des alliés à $rangeCells cases de ${decimal(boostPercent)}%",
    en = s"speeds up allies within $rangeCells cells by ${decimal(boostPercent)}%"
  )

  private def corrupts(percentPerSec: Double): I18nText = I18nText(
    fr = s"corrompt les bâtiments ennemis adjacents de ${decimal(percentPerSec)}%/sec",
    en = s"corrupts adjacent enemy buildings by ${decimal(percentPerSec)}%/s"
  )

  private def takesLessDamage(reductionPercent: Double): I18nText = I18nText(
    fr = s", subit ${decimal(reductionPercent)}% de dégâts en moins",
    en = s", takes ${decimal(reductionPercent)}% less damage"
  )

  private def invokesSoul(intervalMs: Double): I18nText =
    val secs = NumberFormat.seconds(intervalMs)
    I18nText(fr = s"invoque une Âme toutes les ${secs}s", en = s"invokes a Soul every ${secs}s")

  private def clones(intervalMs: Double, minSizePercent: Double): I18nText =
    val secs = NumberFormat.seconds(intervalMs)
    I18nText(
      fr = s"se clone (en plus petit) toutes les ${secs}s (jusqu'à ${decimal(minSizePercent)}% de taille), " +
        "compte pour la victoire de son propriétaire tant qu'il est en vie",
      en = s"clones a smaller copy of itself every ${secs}s (down to ${decimal(minSizePercent)}% size), " +
        "counting toward its owner's victory the whole time"
    )

  // Appended to a building's tooltip (button or live hover) whenever it spawns a unit, so
  // the unit's own value proposition is visible before it's even placed — mirrors
  // GameApp's original spawnAbilitySuffix/unitAbilitySummary split.
  val unitAbilities: Map[UnitKind, I18nText] = Map(
    UnitKind.Elf -> plunders(List(Resource.Wood -> Balance.PlunderPerUnit)),
    UnitKind.Goblin -> plunders(List(Resource.Wood -> Balance.PlunderPerUnit, Resource.Fire -> Balance.PlunderPerUnit)),
    UnitKind.Minotaur ->
      plunders(List(Resource.Wood -> Balance.MinotaurPlunderPerUnit, Resource.Fire -> Balance.MinotaurPlunderPerUnit)),
    UnitKind.Paladin -> (noPlunder ++ shields(Balance.PaladinAuraDamageReductionPerSec)),
    UnitKind.Wolf -> (noPlunder ++ speedsUp(Balance.WolfSpeedAuraRangeCells, (Balance.WolfSpeedAuraMultiplier - 1) * 100)),
    UnitKind.Zombie -> (noPlunder ++ corrupts(Balance.ZombieCorruptionPercentPerSec)),
    UnitKind.Vampire ->
      (noPlunder ++ corrupts(Balance.VampireCorruptionPercentPerSec) ++ takesLessDamage(Balance.VampireDamageReductionFraction * 100)),
    UnitKind.Necromancer -> (noPlunder ++ invokesSoul(Balance.SoulSummonIntervalMs)),
    UnitKind.Soul -> (noPlunder ++ corrupts(Balance.SoulCorruptionPercentPerSec)),
    UnitKind.Tree -> (noPlunder ++ clones(Balance.TreeCloneIntervalMs, Balance.TreeMinCloneSizeFraction * 100))
  )

  def unitAbilitySummary(kind: UnitKind, lang: Lang): String = unitAbilities(kind)(lang)

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
      if lang == Lang.Fr then s" — corrompu à $pct%/$max%" else s" — corrupted $pct%/$max%"

  // Balance.ConstructionMsPerCostUnit's doc — shown only while a building is still under
  // construction (Building.constructionRemainingMs > 0), same "only once it's actually
  // relevant" trigger as corruptionSuffix above; ceil'd to seconds so a nearly-finished
  // building doesn't read "0s" until it's truly done.
  def constructionSuffix(constructionRemainingMs: Double, lang: Lang): String =
    if constructionRemainingMs <= 0 then ""
    else
      val secs = math.ceil(constructionRemainingMs / 1000.0).toInt
      if lang == Lang.Fr then s" — en construction (${secs}s)" else s" — under construction (${secs}s)"

  def destroyLabel(refundText: String, lang: Lang): String =
    if lang == Lang.Fr then s"Détruire ($refundText)" else s"Destroy ($refundText)"

  def upgradeLabel(kind: BuildingKind, costText: String, lang: Lang): String =
    val name = EntityNames.buildingName(kind, lang)
    if lang == Lang.Fr then s"Améliorer en $name ($costText)" else s"Upgrade to $name ($costText)"

  // A specific lab's further-leveling-up option — folded into the same upgrade-button
  // tooltip machinery as an ordinary tier-upgrade (Grove -> Forest, Labo Fondamental -> a
  // specific lab), not a separate "Research" affordance — see GameApp's
  // upgradeOptionsInfo/levelUpOptionFor.
  def levelUpLabel(nextLevel: Int, maxLevel: Int, costText: String, effect: String, lang: Lang): String =
    val word = if lang == Lang.Fr then "Améliorer (niveau" else "Upgrade (level"
    s"$word $nextLevel/$maxLevel) ($costText) → $effect"

  def levelText(level: Int, maxLevel: Int, effect: Option[String], lang: Lang): String =
    if level <= 0 then (if lang == Lang.Fr then s"niveau 0/$maxLevel (aucun bonus)" else s"level 0/$maxLevel (no bonus yet)")
    else
      val word = if lang == Lang.Fr then "niveau" else "level"
      effect match
        case Some(e) => s"$word $level/$maxLevel ($e)"
        case None    => s"$word $level/$maxLevel"

  // The magnitude a lab's research level actually gives (ResearchSpecs.effectAtLevel, or
  // for Recherche fondamentale, the required other-lab level) varies at runtime, so this
  // is a Map of small functions rather than a Map of already-baked I18nText — everything
  // *except* that one number is still fixed per kind, defined once below.
  private val researchEffectTemplates: Map[BuildingKind, Double => I18nText] = Map(
    BuildingKind.LaboNaturel -> { magnitude =>
      val v = decimal(magnitude * 100)
      I18nText(fr = s"-$v% coût des bâtiments", en = s"-$v% building cost")
    },
    BuildingKind.LaboSombre -> { magnitude =>
      val v = decimal(magnitude * 100)
      I18nText(fr = s"+$v% conditions de victoire adverses", en = s"+$v% opponent's victory targets")
    },
    BuildingKind.LaboDuChaos -> { magnitude =>
      val v = decimal(magnitude)
      I18nText(fr = s"+$v pillage par ressource, chaque unité", en = s"+$v plunder per resource, every unit")
    },
    BuildingKind.LaboDeLaLoi -> { magnitude =>
      val v = decimal(magnitude * 100)
      I18nText(fr = s"+$v% dégâts des bâtiments", en = s"+$v% building damage")
    },
    BuildingKind.LaboDeRecherche -> { magnitude =>
      val level = magnitude.toInt
      I18nText(
        fr = s"victoire automatique une fois chaque autre labo au niveau $level+",
        en = s"wins outright once every other lab reaches level $level+"
      )
    }
  )

  def researchEffectSummary(labKind: BuildingKind, magnitude: Double, lang: Lang): String =
    researchEffectTemplates.get(labKind).map(_(magnitude)(lang)).getOrElse("")

  def spawnsNothing(lang: Lang): String = if lang == Lang.Fr then "n'envoie aucune unité" else "spawns no unit"

  // ── Building live-hover building blocks ─────────────────────────────────
  // Small composable fragments `perKindHoverText` (GameApp) strings together per kind —
  // kept granular rather than one big per-kind template, since which fragments a given
  // kind needs (rate? aura? a spawn countdown? a research level?) varies too much for a
  // single shape (see BuildingSpecs' doc on why combat abilities stay kind-based).

  def rate(resource: Resource, amountPerSec: Double, lang: Lang): String =
    s"+${decimal(amountPerSec)} ${EntityNames.resourceName(resource, lang)}/s"

  def nextSpawnIn(unitKind: UnitKind, seconds: Int, lang: Lang): String =
    val name = EntityNames.unitName(unitKind, lang)
    if lang == Lang.Fr then s"prochain $name dans ${seconds}s" else s"next $name in ${seconds}s"

  def adjacentDamage(dmgPerSec: Double, lang: Lang): String =
    if lang == Lang.Fr then s"${decimal(dmgPerSec)} dégâts/sec aux ennemis adjacents"
    else s"${decimal(dmgPerSec)} dmg/s to adjacent enemies"

  def adjacentDamageAndSlow(dmgPerSec: Double, slowPercent: Double, lang: Lang): String =
    if lang == Lang.Fr then s"${decimal(dmgPerSec)} dégâts/sec aux ennemis adjacents, ralentis de ${decimal(slowPercent)}%"
    else s"${decimal(dmgPerSec)} dmg/s to adjacent enemies, slows them ${decimal(slowPercent)}%"

  def rangedDamage(dmgPerSec: Double, rangeCells: Int, lang: Lang): String =
    if lang == Lang.Fr then s"${decimal(dmgPerSec)} dégâts/sec à l'ennemi le plus proche jusqu'à $rangeCells cases"
    else s"${decimal(dmgPerSec)} dmg/s to the nearest enemy within $rangeCells cells"

  def passingGateAbility(dmgPerSec: Double, harvestPercent: Double, lang: Lang): String =
    if lang == Lang.Fr then
      s"${decimal(dmgPerSec)} dégâts/sec sur ses 4 cases adjacentes, récolte ${decimal(harvestPercent)}% de vos " +
        "ressources totales en ombre à chaque mort à proximité"
    else
      s"${decimal(dmgPerSec)} dmg/s to enemies on its 4 adjacent cells, harvests ${decimal(harvestPercent)}% " +
        "of your own total resources as shadow on every nearby death"

  def noBonusYet(lang: Lang): String =
    if lang == Lang.Fr then "aucun bonus propre — améliorez-le en un labo spécifique ci-dessous"
    else "no bonus of its own — upgrade it into a specific lab below"

  def noSpawnLabel(lang: Lang): String = if lang == Lang.Fr then "n'envoie aucune ressource" else "spawns no resource"

  // ── Building "own ability" sentences — one entry per kind that has one, all static ───

  private def upperFirst(text: String): String = if text.isEmpty then text else text.charAt(0).toUpper.toString + text.substring(1)

  private val groveUpgradeHint: I18nText =
    val forestFr = EntityNames.buildingName(BuildingKind.Forest, Lang.Fr)
    val forestEn = EntityNames.buildingName(BuildingKind.Forest, Lang.En)
    val jungleFr = EntityNames.buildingName(BuildingKind.Jungle, Lang.Fr)
    val jungleEn = EntityNames.buildingName(BuildingKind.Jungle, Lang.En)
    I18nText(fr = s" Devient $forestFr puis $jungleFr en s'améliorant.", en = s" Upgrades into a $forestEn, then a $jungleEn.")

  private val watchtowerOwnAbility: I18nText =
    val dmg = decimal(Balance.WatchtowerDamagePerSec)
    I18nText(
      fr = s" ${upperFirst(spawnsNothing(Lang.Fr))} — inflige plutôt $dmg dégâts/sec à l'ennemi le plus proche " +
        s"jusqu'à ${Balance.WatchtowerRangeCells} cases",
      en = s" ${upperFirst(spawnsNothing(Lang.En))} — instead inflicts $dmg dmg/s to the nearest enemy within " +
        s"${Balance.WatchtowerRangeCells} cells"
    )

  private val angelOwnAbility: I18nText =
    val dmg = decimal(Balance.AngelDamagePerSec)
    val slow = decimal(Balance.AngelSlowFraction * 100)
    I18nText(
      fr = s" ${upperFirst(spawnsNothing(Lang.Fr))} — inflige plutôt $dmg dégâts/sec aux unités adjacentes et " +
        s"ralentit leur vitesse de $slow%",
      en = s" ${upperFirst(spawnsNothing(Lang.En))} — instead inflicts $dmg dmg/s to adjacent enemies and slows " +
        s"them by $slow%"
    )

  private val passingGateOwnAbility: I18nText =
    val dmg = decimal(Balance.PassingGateDamagePerSec)
    val harvest = decimal(Balance.PassingGateDeathShadowFraction * 100)
    I18nText(
      fr = s" ${upperFirst(spawnsNothing(Lang.Fr))} — inflige $dmg dégâts/sec aux ennemis sur ses 4 cases " +
        s"adjacentes, et récolte $harvest% de vos ressources totales en ombre bonus à chaque mort sur " +
        "l'une de ces cases",
      en = s" ${upperFirst(spawnsNothing(Lang.En))} — inflicts $dmg dmg/s to enemies on its 4 adjacent cells, " +
        s"and harvests $harvest% of your own total resources as bonus shadow whenever any creature dies " +
        "on one of those cells"
    )

  private val laboFondamentalOwnAbility: I18nText = I18nText(
    fr = " sans bonus propre. Améliorez-le en un labo spécifique pour débloquer ses niveaux (niveau 1 gratuit, " +
      "puis d'autres améliorations sur place) — un seul labo de chaque type spécifique par maze à la fois",
    en = " with no bonus of its own. Upgrade it into a specific lab to unlock its levels (starting at a free " +
      "level 1, then further upgrades in place) — only one lab of each specific kind per maze at a time"
  )

  // The extra sentence a building's own tooltip needs beyond cost/production/spawn —
  // either a combat ability BuildingSpec doesn't model (Watchtower/Angel/PassingGate's
  // aura or ranged damage — see BuildingSpecs' doc on why those stay hand-coded special
  // cases), or Grove's own upgrade-chain hint. Absent for every other kind, whose tooltip
  // is already complete from buildingButtonTooltip + unitAbilitySummary alone.
  val buildingOwnAbilities: Map[BuildingKind, I18nText] = Map(
    BuildingKind.Grove -> groveUpgradeHint,
    BuildingKind.Watchtower -> watchtowerOwnAbility,
    BuildingKind.Angel -> angelOwnAbility,
    BuildingKind.PassingGate -> passingGateOwnAbility,
    BuildingKind.LaboFondamental -> laboFondamentalOwnAbility
  )

  def buildingOwnAbility(kind: BuildingKind, lang: Lang): String = buildingOwnAbilities.get(kind).fold("")(_(lang))
