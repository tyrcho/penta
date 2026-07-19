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
// Scope is deliberately just buildings/units/resources (BuildingSpecs.all/CreatureSpecs.
// all/Resource.values) — the faction overview pages (Nature.md etc.), the Relations/
// cross-influence pages, and Science's Note/Recherche* pages are hand-written narrative
// content outside what Balance can drive, and stay untouched (generated pages link to the
// FR originals for those — see EntityNames.outOfScopeLink/factionLink).
//
// Run via `make docs` (sbt "sim/runMain towerdefense.docgen.DocGenerator").
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
    Console.err.println(s"DocGenerator: wrote $written pages under $repoRoot")

  private def writePage(path: Path, content: String): Int =
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    1

  // ── Frontmatter ──────────────────────────────────────────────────────────

  private def yamlQuoted(s: String): String = "\"" + s.replace("\"", "\\\"") + "\""

  private def costKey(res: Resource, lang: Lang): String = (res, lang) match
    case (Resource.Wood, Lang.Fr)    => "cout en bois"
    case (Resource.Wood, Lang.En)    => "cost in wood"
    case (Resource.Fire, Lang.Fr)    => "cout en feu"
    case (Resource.Fire, Lang.En)    => "cost in fire"
    case (Resource.Light, Lang.Fr)   => "cout en lumiere"
    case (Resource.Light, Lang.En)   => "cost in light"
    case (Resource.Shadow, Lang.Fr)  => "cout en ombre"
    case (Resource.Shadow, Lang.En)  => "cost in shadow"
    case (Resource.Crystal, Lang.Fr) => "cout en crystal"
    case (Resource.Crystal, Lang.En) => "cost in crystal"

  private def buildingTypeValue(lang: Lang): String = lang match
    case Lang.Fr => "batiment"
    case Lang.En => "building"

  private def unitTypeValue(lang: Lang): String = lang match
    case Lang.Fr => "unite"
    case Lang.En => "unit"

  private def resourceTypeValue(lang: Lang): String = lang match
    case Lang.Fr => "ressource"
    case Lang.En => "resource"

  private def hpKey(lang: Lang): String = lang match
    case Lang.Fr => "PV"
    case Lang.En => "HP"

  private def frontmatter(fields: List[(String, String)]): String =
    val body = fields.map { case (k, v) => s"$k: $v" }.mkString("\n")
    s"---\n$body\n---\n"

  // ── Buildings ────────────────────────────────────────────────────────────

  private def buildingPage(kind: BuildingKind, lang: Lang): String =
    val faction = Faction.of(kind)
    val spec = BuildingSpecs.all(kind)
    // Skips a resource entirely at cost 0 (e.g. BuildingSpecs.Cave's Wood -> 0.0, kept in
    // the data model only for map-shape uniformity) — matches the original vault's own
    // convention of just not mentioning a cost that doesn't apply, rather than showing
    // a "cout en bois: 0" line no hand-written page ever had.
    val costFields = Resource.values.toList.flatMap(res =>
      spec.cost.get(res).filter(_ > 0.0).map(amount => costKey(res, lang) -> NumberFormat.decimal(amount))
    )
    val fm = frontmatter(
      List("type" -> buildingTypeValue(lang), "faction" -> yamlQuoted(EntityNames.factionLink(faction, lang))) ++
        costFields
    )
    val image = AssetPaths.building(kind)
    val imageLine = s"![${EntityNames.buildingName(kind, lang)}](../../game/assets/$image)"
    s"$fm\n$imageLine\n\n${EntityText.buildingBody(kind, lang)}\n"

  // ── Units ────────────────────────────────────────────────────────────────

  private def unitPage(kind: UnitKind, lang: Lang): String =
    val faction = Faction.of(kind)
    val spec = CreatureSpecs.all(kind)
    val fm = frontmatter(
      List(
        "type" -> unitTypeValue(lang),
        "faction" -> yamlQuoted(EntityNames.factionLink(faction, lang)),
        hpKey(lang) -> yamlQuoted(NumberFormat.decimal(spec.maxHp))
      )
    )
    val image = AssetPaths.unit(kind)
    val imageLine = s"![${EntityNames.unitName(kind, lang)}](../../game/assets/$image)"
    s"$fm\n$imageLine\n\n${EntityText.unitBody(kind, lang)}\n"

  // ── Resources ────────────────────────────────────────────────────────────
  // Minimal by design — the original vault pages are frontmatter + a reference image and
  // nothing else (a resource has no behavior of its own to describe; every building that
  // produces/costs it already links back here). Shadow has no image at all (see
  // AssetPaths.resource's doc) — Ombre.md has always shipped without one.

  private def resourcePage(res: Resource, lang: Lang): String =
    val faction = Faction.of(res)
    val fm = frontmatter(
      List("type" -> resourceTypeValue(lang), "faction" -> yamlQuoted(EntityNames.factionLink(faction, lang)))
    )
    AssetPaths.resource(res) match
      case Some(image) => s"$fm\n![${EntityNames.resourceName(res, lang)}](../../game/assets/$image)\n"
      case None         => s"$fm"
