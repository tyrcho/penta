package towerdefense.docgen

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import towerdefense.domain.*
import towerdefense.domain.i18n.*

// Regenerates the vault's per-building/per-unit/per-resource pages from the game's own
// balance data (Balance/BuildingSpecs/CreatureSpecs — see core's i18n package for the
// text/asset tables this reads), in both French and English:
//
//   - French pages are written *in place* under Resources/<Faction>/, at the exact same
//     paths the hand-written vault already uses (see EntityNames' doc) — every number a
//     generated page states is guaranteed current with Balance, unlike the original
//     hand-written pages (which had drifted — e.g. Loup.md's "PV: 40" vs the actual 30).
//   - English pages are written under a sibling Resources-en/<Faction>/ tree this
//     generator owns outright (a fresh set of files, not previously hand-written).
//
// Scope is deliberately just buildings/units/resources/research (BuildingSpecs.all/
// CreatureSpecs.all/Resource.values/ResearchSpecs.all) — the faction overview pages
// (Nature.md etc.), the Relations/cross-influence pages, and Science's Note sur les
// laboratoires.md are hand-written narrative content outside what Balance can drive, and
// stay untouched (generated pages link to the FR originals for those — see EntityNames.
// outOfScopeLink/factionLink). Science's five Recherche*/Recherches*.md pages, by
// contrast, are entirely numbers Balance/ResearchSpecs already own — a hand-written copy
// of those numbers is exactly what let every one of them drift to "double" while the code
// had already moved to tripling (Balance.ResearchCostMultiplierPerLevel), so they're
// generated too, in place, French only (no EN translation exists for these — an EN
// building page's link to one still falls back to the FR original, unchanged).
//
// Run via `make docs` (sbt "sim/runMain towerdefense.docgen.generate" — Scala 3's @main
// names the generated entry point after the annotated method, not this enclosing object).
object DocGenerator:

  @main def generate(args: String*): Unit =
    // sbt's runMain has its cwd at the build root (`game/`) — the vault lives one level
    // up. Overridable (first arg) for running this from somewhere else / in a test.
    val repoRoot = Paths.get(args.headOption.getOrElse("..")).toAbsolutePath.normalize()
    var written = 0
    for lang <- List(Lang.Fr, Lang.En) do
      val vaultRoot = repoRoot.resolve(EntityNames.vaultRoot(lang))
      for kind <- BuildingKind.values do
        written += writePage(vaultRoot.resolve(EntityNames.buildingPath(kind, lang)), buildingPage(kind, lang))
      for kind <- UnitKind.values do
        written += writePage(vaultRoot.resolve(EntityNames.unitPath(kind, lang)), unitPage(kind, lang))
      for res <- Resource.values do
        written += writePage(vaultRoot.resolve(EntityNames.resourcePath(res, lang)), resourcePage(res, lang))
    val frScienceRoot = repoRoot.resolve(EntityNames.vaultRoot(Lang.Fr)).resolve(EntityNames.factionFolder(Faction.Science, Lang.Fr))
    for kind <- ResearchSpecs.orderedLabs do
      written += writePage(frScienceRoot.resolve(researchFileNames(kind)), researchPage(kind))
    Console.err.println(s"DocGenerator: wrote $written pages under $repoRoot")

  private def writePage(path: Path, content: String): Int =
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    1

  // ── Frontmatter vocabulary — one entry per resource/page-type, not a match ─

  private def yamlQuoted(s: String): String = "\"" + s.replace("\"", "\\\"") + "\""

  private val costKeys: Map[Resource, I18nText] = Map(
    Resource.Wood -> I18nText("cout en bois", "cost in wood"),
    Resource.Fire -> I18nText("cout en feu", "cost in fire"),
    Resource.Light -> I18nText("cout en lumiere", "cost in light"),
    Resource.Shadow -> I18nText("cout en ombre", "cost in shadow"),
    Resource.Crystal -> I18nText("cout en crystal", "cost in crystal")
  )

  private val buildingTypeValue = I18nText("batiment", "building")
  private val unitTypeValue = I18nText("unite", "unit")
  private val resourceTypeValue = I18nText("ressource", "resource")
  private val hpKey = I18nText("PV", "HP")

  private def frontmatter(fields: List[(String, String)]): String =
    val body = fields.map { case (k, v) => s"$k: $v" }.mkString("\n")
    s"---\n$body\n---\n"

  // ── Buildings ────────────────────────────────────────────────────────────

  private def buildingPage(kind: BuildingKind, lang: Lang): String =
    val info = EntityNames.buildingInfo(kind)
    val spec = BuildingSpecs.all(kind)
    // Skips a resource entirely at cost 0 (e.g. BuildingSpecs.Cave's Wood -> 0.0, kept in
    // the data model only for map-shape uniformity) — matches the original vault's own
    // convention of just not mentioning a cost that doesn't apply, rather than showing
    // a "cout en bois: 0" line no hand-written page ever had.
    val costFields = Resource.values.toList.flatMap(res =>
      spec.cost.get(res).filter(_ > 0.0).map(amount => costKeys(res)(lang) -> NumberFormat.decimal(amount))
    )
    val fm = frontmatter(
      List("type" -> buildingTypeValue(lang), "faction" -> yamlQuoted(EntityNames.factionLink(info.faction, lang))) ++
        costFields
    )
    val imageLine = s"![${info.name(lang)}](../../game/assets/${info.asset})"
    s"$fm\n$imageLine\n\n${EntityText.buildingBody(kind, lang)}\n"

  // ── Units ────────────────────────────────────────────────────────────────

  private def unitPage(kind: UnitKind, lang: Lang): String =
    val info = EntityNames.unitInfo(kind)
    val spec = CreatureSpecs.all(kind)
    val fm = frontmatter(
      List(
        "type" -> unitTypeValue(lang),
        "faction" -> yamlQuoted(EntityNames.factionLink(info.faction, lang)),
        hpKey(lang) -> yamlQuoted(NumberFormat.decimal(spec.maxHp))
      )
    )
    val imageLine = s"![${info.name(lang)}](../../game/assets/${info.asset})"
    s"$fm\n$imageLine\n\n${EntityText.unitBody(kind, lang)}\n"

  // ── Resources ────────────────────────────────────────────────────────────
  // Minimal by design — the original vault pages are frontmatter + a reference image and
  // nothing else (a resource has no behavior of its own to describe; every building that
  // produces/costs it already links back here). Shadow has no image at all (see
  // ResourceKindInfo's doc) — Ombre.md has always shipped without one.

  private def resourcePage(res: Resource, lang: Lang): String =
    val info = EntityNames.resourceInfo(res)
    val fm = frontmatter(
      List("type" -> resourceTypeValue(lang), "faction" -> yamlQuoted(EntityNames.factionLink(info.faction, lang)))
    )
    info.asset.fold(fm)(image => s"$fm\n![${info.name(lang)}](../../game/assets/$image)\n")

  // ── Science research lines (Recherche(s)*.md) ───────────────────────────
  // French only — no EN translation of these pages exists (an EN building page's link to
  // one still falls back to the FR original via EntityNames.outOfScopeLink/frFallbackLink,
  // unchanged). Every number below traces straight back to ResearchSpecs/Balance, closing
  // the exact gap that once let every one of these pages say "double" while the code had
  // already moved to tripling (see this file's header doc and Balance.
  // ResearchCostMultiplierPerLevel's doc).

  private val researchTypeValue = "recherche"

  // The vault's own file-naming convention for these five pages doesn't derive from
  // BuildingKind's own name (LaboNaturel's line lives in "Recherches naturelles.md", not
  // anything built from "Labo Naturel") — its own small map rather than folded into
  // EntityNames' building-name tables, since it's specific to this FR-only doc set.
  private val researchFileNames: Map[BuildingKind, String] = Map(
    BuildingKind.LaboNaturel -> "Recherches naturelles.md",
    BuildingKind.LaboSombre -> "Recherches Sombres.md",
    BuildingKind.LaboDeLaLoi -> "Recherches loyales.md",
    BuildingKind.LaboDuChaos -> "Recherches chaotiques.md",
    BuildingKind.LaboDeRecherche -> "Recherche fondamentale.md"
  )

  // The one hand-written sentence each page still needs (what the research line actually
  // does) — every number after it is generated below instead.
  private val researchIntros: Map[BuildingKind, String] = Map(
    BuildingKind.LaboNaturel -> "Diminue le cout des batiments de",
    BuildingKind.LaboSombre -> "Augmente les conditions de victoire de l'adversaire de :",
    BuildingKind.LaboDeLaLoi -> "Augmente les dégats infligés par les batiments de",
    BuildingKind.LaboDuChaos ->
      "Augmente l'efficacité du pillage de chaque unite (même celles qui ne pillent pas initialement) dans chaque ressource de :",
    BuildingKind.LaboDeRecherche -> "Donne la victoire si les 4 autres laboratoires sont au niveau :"
  )

  // The per-level numbered list itself — straight off the same Balance lists CombatEngine/
  // VictoryConditions/Placement.effectiveCost read to actually apply each effect, except
  // Fondamentale's (Balance.FondamentaleRequiredOtherLabLevel), whose entries read "N ou
  // plus" unless N is already the max level (5), where "ou plus" would be meaningless.
  private def researchEffectLines(kind: BuildingKind): List[String] = kind match
    case BuildingKind.LaboNaturel     => Balance.NaturellesCostReductionByLevel.map(NumberFormat.percent)
    case BuildingKind.LaboSombre      => Balance.SombresOpponentTargetIncreaseByLevel.map(NumberFormat.percent)
    case BuildingKind.LaboDeLaLoi     => Balance.LoyalesBuildingDamageIncreaseByLevel.map(NumberFormat.percent)
    case BuildingKind.LaboDuChaos     => Balance.ChaotiquesPlunderBonusByLevel.map(NumberFormat.decimal)
    case BuildingKind.LaboDeRecherche =>
      Balance.FondamentaleRequiredOtherLabLevel.map(n => if n >= Balance.MaxResearchLevel then n.toString else s"$n ou plus")
    case other => sys.error(s"$other has no research line — not in ResearchSpecs.all")

  // Spells out Balance.ResearchCostMultiplierPerLevel in French rather than a bare number,
  // matching every page's existing "Chaque niveau coute le X du precedent" phrasing —
  // falls back to a plain "xN" for any value the vault's own vocabulary doesn't have a
  // word for, so a future rebalance can't silently leave stale wrong prose behind the way
  // the hand-written pages once did.
  private def costMultiplierWord(multiplier: Double): String = multiplier match
    case 2.0    => "double"
    case 3.0    => "triple"
    case 4.0    => "quadruple"
    case 5.0    => "quintuple"
    case other  => s"x${NumberFormat.decimal(other)}"

  private def researchPage(kind: BuildingKind): String =
    val spec = ResearchSpecs.all(kind)
    val faction = EntityNames.buildingInfo(kind).faction
    val costFields = Resource.values.toList.flatMap(res =>
      spec.baseCost.get(res).filter(_ > 0.0).map(amount => costKeys(res)(Lang.Fr) -> NumberFormat.decimal(amount))
    )
    val fm = frontmatter(
      List("type" -> researchTypeValue, "faction" -> yamlQuoted(EntityNames.factionLink(faction, Lang.Fr))) ++ costFields
    )
    val items = researchEffectLines(kind).zipWithIndex.map { case (line, i) => s"${i + 1}. $line" }.mkString("\n")
    val multiplierWord = costMultiplierWord(Balance.ResearchCostMultiplierPerLevel)
    s"$fm${researchIntros(kind)}\n$items\n\nChaque niveau coute le $multiplierWord du precedent.\n"
