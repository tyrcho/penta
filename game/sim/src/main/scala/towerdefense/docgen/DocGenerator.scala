package towerdefense.docgen

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import towerdefense.domain.*
import towerdefense.domain.economy.*
import towerdefense.domain.grid.*
import towerdefense.domain.i18n.*

// Regenerates the vault's per-building/per-unit/per-resource pages from the game's own
// balance data (Balance/BuildingKind/UnitKind — see core's i18n package for the
// text/asset tables this reads), in both French and English:
//
//   - French pages are written *in place* under Resources/<Faction>/, at the exact same
//     paths the hand-written vault already uses (see EntityNames' doc) — every number a
//     generated page states is guaranteed current with Balance, unlike the original
//     hand-written pages (which had drifted — e.g. Loup.md's "PV: 40" vs the actual 30).
//   - English pages are written under a sibling Resources-en/<Faction>/ tree this
//     generator owns outright (a fresh set of files, not previously hand-written).
//
// Scope is deliberately just buildings/units/resources (BuildingKind.values/UnitKind.
// values/Resource.values) — the faction overview pages (Nature.md etc.), the Relations/
// cross-influence pages, and Science's Note sur les laboratoires.md are hand-written
// narrative content outside what Balance can drive, and stay untouched (generated pages
// link to the FR originals for those — see EntityNames.outOfScopeLink/factionLink). The
// five specific labs' own per-level cost/effect breakdown lives in their *building* page's
// body instead (EntityText.specificLabBody) — there's no separate "research" page/type any
// more (see that function's doc for why: it's the same building-upgrade mechanism as
// Grove -> Forest -> Jungle, not a distinct concept).
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
        written += writePage(
          vaultRoot.resolve(EntityNames.buildingPath(kind, lang)),
          buildingPage(kind, lang)
        )
      for kind <- UnitKind.values do
        written += writePage(
          vaultRoot.resolve(EntityNames.unitPath(kind, lang)),
          unitPage(kind, lang)
        )
      // Gold (ResourceKindInfo.faction = None) gets no dedicated wiki page — a faction-less
      // wildcard currency doesn't fit the vault's per-faction structure any of the other 5
      // resources live in (see ResourceKindInfo's own doc). Binding `faction` here (instead
      // of an `isDefined` filter + a `.get` below) gives resourcePath/resourcePage a proven
      // Faction directly — Gold simply can't reach either call.
      for
        res <- Resource.values
        faction <- EntityNames.resourceInfo(res).faction
      do
        written += writePage(
          vaultRoot.resolve(EntityNames.resourcePath(res, faction, lang)),
          resourcePage(res, faction, lang)
        )
    Console.err.println(s"DocGenerator: wrote $written pages under $repoRoot")

  private def writePage(path: Path, content: String): Int =
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    1

  // ── Frontmatter vocabulary — one entry per resource/page-type, not a match ─

  private def yamlQuoted(s: String): String = "\"" + s.replace("\"", "\\\"") + "\""

  // A YAML flow-sequence of quoted strings ("ameliore vers" can have up to 5 targets —
  // LaboFondamental's own upgradeOptions entry — so a scalar value won't do), always used
  // even for a single-target chain (Grove -> Forest) so the property's type never
  // depends on how many targets a given building happens to have.
  private def yamlList(items: List[String]): String =
    "[" + items.map(yamlQuoted).mkString(", ") + "]"

  private val costKeys: Map[Resource, I18nText] = Map(
    Resource.Wood -> I18nText("cout en bois", "cost in wood"),
    Resource.Fire -> I18nText("cout en feu", "cost in fire"),
    Resource.Light -> I18nText("cout en lumiere", "cost in light"),
    Resource.Shadow -> I18nText("cout en ombre", "cost in shadow"),
    Resource.Crystal -> I18nText("cout en crystal", "cost in crystal")
  )

  private val producesKeys: Map[Resource, I18nText] = Map(
    Resource.Wood -> I18nText("produit en bois", "produces in wood"),
    Resource.Fire -> I18nText("produit en feu", "produces in fire"),
    Resource.Light -> I18nText("produit en lumiere", "produces in light"),
    Resource.Shadow -> I18nText("produit en ombre", "produces in shadow"),
    Resource.Crystal -> I18nText("produit en crystal", "produces in crystal")
  )

  // Gold is the only resource a unit ever plunders that no building ever produces
  // (Goblin/Minotaur/Dragon steal it directly — see UnitKind.plunder) — producesKeys above
  // has no Gold entry for exactly that reason, but plunderKeys needs one.
  private val plunderKeys: Map[Resource, I18nText] = Map(
    Resource.Wood -> I18nText("pillage en bois", "plunder in wood"),
    Resource.Fire -> I18nText("pillage en feu", "plunder in fire"),
    Resource.Light -> I18nText("pillage en lumiere", "plunder in light"),
    Resource.Shadow -> I18nText("pillage en ombre", "plunder in shadow"),
    Resource.Crystal -> I18nText("pillage en crystal", "plunder in crystal"),
    Resource.Gold -> I18nText("pillage en or", "plunder in gold")
  )

  private val buildingTypeValue = I18nText("batiment", "building")
  private val unitTypeValue = I18nText("unite", "unit")
  private val resourceTypeValue = I18nText("ressource", "resource")
  private val hpKey = I18nText("PV", "HP")
  private val dpsKey = I18nText("degats par seconde", "damage per second")
  private val upgradeFromKey = I18nText("amelioration de", "upgrade of")
  private val upgradeToKey = I18nText("ameliore vers", "upgrades to")
  private val spawnedByKey = I18nText(
    "produit par",
    "produced by"
  ) // matches the vault's own existing body prose (e.g. Gobelin.md: "Produit par [Cave]")
  private val speedKey = I18nText("vitesse (cases/sec)", "speed (cells/sec)")

  private def frontmatter(fields: List[(String, String)]): String =
    val body = fields.map { case (k, v) => s"$k: $v" }.mkString("\n")
    s"---\n$body\n---\n"

  // ── Buildings ────────────────────────────────────────────────────────────

  private def buildingPage(kind: BuildingKind, lang: Lang): String =
    val info = EntityNames.buildingInfo(kind)
    // Skips a resource entirely at cost 0 (e.g. Cave's Wood -> 0.0, kept in the enum only
    // for map-shape uniformity) — matches the original vault's own convention of just not
    // mentioning a cost that doesn't apply, rather than showing a "cout en bois: 0" line no
    // hand-written page ever had.
    val costFields = Resource.values.toList.flatMap(res =>
      kind.cost
        .get(res)
        .filter(_ > 0.0)
        .map(amount => costKeys(res)(lang) -> NumberFormat.decimal(amount))
    )
    val producesFields = Resource.values.toList.flatMap(res =>
      kind.produces
        .get(res)
        .filter(_ > 0.0)
        .map(amount => producesKeys(res)(lang) -> NumberFormat.decimal(amount))
    )
    val dpsField = Option.when(kind.dps > 0.0)(dpsKey(lang) -> NumberFormat.decimal(kind.dps))
    val upgradeFromField = kind.upgradeFrom
      .map(source =>
        upgradeFromKey(lang) -> yamlQuoted(EntityNames.buildingLink(kind.faction, source, lang))
      )
    val upgradeToField = BuildingSpecs.upgradeOptions
      .get(kind)
      .map(targets =>
        upgradeToKey(lang) -> yamlList(targets.map(EntityNames.buildingLink(kind.faction, _, lang)))
      )
    val fm = frontmatter(
      List(
        "type" -> buildingTypeValue(lang),
        "faction" -> yamlQuoted(EntityNames.factionLink(kind.faction, lang)),
        "tier" -> kind.tier.toString
      ) ++ costFields ++ producesFields ++ dpsField.toList ++ upgradeFromField.toList ++ upgradeToField.toList
    )
    val imageLine = s"![${info.name(lang)}](../../game/assets/${info.asset})"
    s"$fm\n$imageLine\n\n${EntityText.buildingBody(kind, lang)}\n"

  // ── Units ────────────────────────────────────────────────────────────────

  private def unitPage(kind: UnitKind, lang: Lang): String =
    val info = EntityNames.unitInfo(kind)
    val spawnedByField = kind.producedFrom.map(building =>
      spawnedByKey(lang) -> yamlQuoted(EntityNames.buildingLink(kind.faction, building, lang))
    )
    val plunderFields = Resource.values.toList.flatMap(res =>
      kind.plunder
        .get(res)
        .filter(_ > 0.0)
        .map(amount => plunderKeys(res)(lang) -> NumberFormat.decimal(amount))
    )
    // speedPerMs is px/ms; GridConfig.cellSize (px/cell) converts it to the same
    // cells/sec unit the vault's own hand-written prose already uses (e.g. Ame.md:
    // "vitesse normale (1 case/sec)") — see GridConfig.cellSize's doc.
    val cellsPerSec = kind.speedPerMs * 1000.0 / GridConfig.cellSize
    val fm = frontmatter(
      List(
        "type" -> unitTypeValue(lang),
        "faction" -> yamlQuoted(EntityNames.factionLink(kind.faction, lang)),
        hpKey(lang) -> yamlQuoted(NumberFormat.decimal(kind.maxHp)),
        "tier" -> kind.tier.toString,
        speedKey(lang) -> NumberFormat.decimal(cellsPerSec)
      ) ++ spawnedByField.toList ++ plunderFields
    )
    val imageLine = s"![${info.name(lang)}](../../game/assets/${info.asset})"
    s"$fm\n$imageLine\n\n${EntityText.unitBody(kind, lang)}\n"

  // ── Resources ────────────────────────────────────────────────────────────
  // Minimal by design — the original vault pages are frontmatter + a reference image and
  // nothing else (a resource has no behavior of its own to describe; every building that
  // produces/costs it already links back here). Shadow has no image at all (see
  // ResourceKindInfo's doc) — Ombre.md has always shipped without one.

  // faction is a parameter (the `generate` loop's own proven Faction), not re-derived from
  // ResourceKindInfo.faction here — see that loop's doc.
  private def resourcePage(res: Resource, faction: Faction, lang: Lang): String =
    val info = EntityNames.resourceInfo(res)
    val fm = frontmatter(
      List(
        "type" -> resourceTypeValue(lang),
        "faction" -> yamlQuoted(EntityNames.factionLink(faction, lang))
      )
    )
    info.asset.fold(fm)(image => s"$fm\n![${info.name(lang)}](../../game/assets/$image)\n")
