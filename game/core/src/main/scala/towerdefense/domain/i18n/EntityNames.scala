package towerdefense.domain.i18n

import towerdefense.domain.*

// A value in each of the two supported languages — the generic (not per-kind) piece of
// "translated text", used as a field type throughout this package instead of writing a
// two-armed match at every call site.
final case class I18nText(fr: String, en: String):
  def apply(lang: Lang): String = if lang == Lang.Fr then fr else en
  // Concatenates each language's text independently — e.g. "doesn't plunder — " ++
  // "shields adjacent allies from 2 dmg/s" — an FR fragment can never accidentally end up
  // appended to an EN one.
  def ++(other: I18nText): I18nText = I18nText(fr + other.fr, en + other.en)

object I18nText:
  // Joins several I18nText fragments (e.g. a produce line, a spawn line, an ability line)
  // into one paragraph-separated I18nText — the same shape EntityText's per-kind body
  // entries are built from, language by language, so a paragraph can never accidentally
  // pair an FR sentence with an EN one.
  def combine(parts: I18nText*): I18nText =
    I18nText(fr = parts.map(_.fr).mkString("\n\n"), en = parts.map(_.en).mkString("\n\n"))

final case class FactionInfo(
    name: I18nText, // also doubles as the vault folder name — see EntityNames.factionFolder's doc
    overviewFrFile: String // e.g. "Nature.md" — the hand-written FR overview page's file name
)

final case class BuildingKindInfo(faction: Faction, name: I18nText, fileName: I18nText, asset: String)

final case class UnitKindInfo(faction: Faction, name: I18nText, fileName: I18nText, asset: String)

// asset: None for Shadow — Resources/Mort/Ombre.md has always shipped without an image,
// and there's no ombre-reference.png in game/assets/ to give it one.
final case class ResourceKindInfo(faction: Faction, name: I18nText, fileName: I18nText, asset: Option[String])

// Display names, vault doc paths, and representative images for every Faction/Resource/
// BuildingKind/UnitKind, in both languages — the single naming source shared by
// DocGenerator (which needs a cross-link-safe file path and an asset per kind) and the
// game UI (which only needs the display name/asset). Every kind's full set of properties
// is defined once, right here, as plain data — nothing in this file branches on *which*
// kind it was given; a lookup into `buildingInfo`/`unitInfo`/`resourceInfo`/`factionInfo`
// always replaces what would otherwise be a per-kind match.
//
// FR file paths mirror the *existing* hand-written vault exactly (see Resources/), so
// regenerating those files in place never breaks a link written anywhere else in the repo
// (ADRs, other vault pages). EN file paths are this generator's own choice, under a
// sibling `Resources-en/` tree with the same Faction subfolders (renamed to their English
// spelling) and English file names, mirroring the FR tree's depth so both use the same
// "../../game/assets/..." relative asset path.
object EntityNames:

  val factionInfo: Map[Faction, FactionInfo] = Map(
    Faction.Nature -> FactionInfo(I18nText("Nature", "Nature"), "Nature.md"),
    Faction.Chaos -> FactionInfo(I18nText("Chaos", "Chaos"), "Chaos.md"),
    Faction.Loi -> FactionInfo(I18nText("Loi", "Law"), "Loi.md"),
    Faction.Mort -> FactionInfo(I18nText("Mort", "Death"), "Mort.md"),
    Faction.Science -> FactionInfo(I18nText("Science", "Science"), "Science.md")
  )

  val buildingInfo: Map[BuildingKind, BuildingKindInfo] = Map(
    BuildingKind.Grove -> BuildingKindInfo(
      Faction.Nature,
      I18nText("Bosquet", "Grove"),
      I18nText("Bosquet.md", "Grove.md"),
      "grove.png"
    ),
    BuildingKind.Forest -> BuildingKindInfo(
      Faction.Nature,
      I18nText("Forêt", "Forest"),
      I18nText("Foret.md", "Forest.md"),
      "forest.png"
    ),
    BuildingKind.Jungle -> BuildingKindInfo(
      Faction.Nature,
      I18nText("Jungle", "Jungle"),
      I18nText("Jungle.md", "Jungle.md"),
      "jungle.png"
    ),
    BuildingKind.Stonehenge -> BuildingKindInfo(
      Faction.Nature,
      I18nText("Stonehenge", "Stonehenge"),
      I18nText("Stonehenge.md", "Stonehenge.md"),
      "stonehenge.png"
    ),
    BuildingKind.Cave -> BuildingKindInfo(
      Faction.Chaos,
      I18nText("Cave", "Cave"),
      I18nText("Cave.md", "Cave.md"),
      "cave.png"
    ),
    BuildingKind.Labyrinth -> BuildingKindInfo(
      Faction.Chaos,
      I18nText("Labyrinthe", "Labyrinth"),
      I18nText("Labyrinthe.md", "Labyrinth.md"),
      "labyrinthe.png"
    ),
    BuildingKind.Church -> BuildingKindInfo(
      Faction.Loi,
      I18nText("Église", "Church"),
      I18nText("Eglise.md", "Church.md"),
      "eglise.png"
    ),
    BuildingKind.Watchtower -> BuildingKindInfo(
      Faction.Loi,
      I18nText("Tour de guet", "Watchtower"),
      I18nText("Tour de guet.md", "Watchtower.md"),
      "watchtower.png"
    ),
    BuildingKind.Angel -> BuildingKindInfo(
      Faction.Loi,
      I18nText("Ange", "Angel"),
      I18nText("Ange.md", "Angel.md"),
      "angel.png"
    ),
    BuildingKind.Tomb -> BuildingKindInfo(
      Faction.Mort,
      I18nText("Tombe", "Tomb"),
      I18nText("Tombe.md", "Tomb.md"),
      "tomb.png"
    ),
    BuildingKind.BlackCastle -> BuildingKindInfo(
      Faction.Mort,
      I18nText("Château Noir", "Black Castle"),
      I18nText("Chateau Noir.md", "Black Castle.md"),
      "chateau-noir.png"
    ),
    BuildingKind.DeathHouse -> BuildingKindInfo(
      Faction.Mort,
      I18nText("Maison de la Mort", "House of Death"),
      I18nText("Maison de la Mort.md", "House of Death.md"),
      "death-house.png"
    ),
    BuildingKind.PassingGate -> BuildingKindInfo(
      Faction.Mort,
      I18nText("Portail", "Passing Gate"),
      I18nText("Portail.md", "Passing Gate.md"),
      "passing-gate.png"
    ),
    BuildingKind.LaboFondamental -> BuildingKindInfo(
      Faction.Science,
      I18nText("Labo Fondamental", "Base Lab"),
      I18nText("Labo Fondamental.md", "Base Lab.md"),
      "labo-fondamental.png"
    ),
    BuildingKind.LaboNaturel -> BuildingKindInfo(
      Faction.Science,
      I18nText("Labo Naturel", "Nature Lab"),
      I18nText("Labo Naturel.md", "Nature Lab.md"),
      "labo-naturel.png"
    ),
    BuildingKind.LaboSombre -> BuildingKindInfo(
      Faction.Science,
      I18nText("Labo Sombre", "Shadow Lab"),
      I18nText("Labo Sombre.md", "Shadow Lab.md"),
      "labo-sombre.png"
    ),
    BuildingKind.LaboDeRecherche -> BuildingKindInfo(
      Faction.Science,
      I18nText("Labo de Recherche", "Research Lab"),
      I18nText("Labo de Recherche.md", "Research Lab.md"),
      "labo-de-recherche.png"
    ),
    BuildingKind.LaboDeLaLoi -> BuildingKindInfo(
      Faction.Science,
      I18nText("Labo de la Loi", "Law Lab"),
      I18nText("Labo de la Loi.md", "Law Lab.md"),
      "labo-de-la-loi.png"
    ),
    BuildingKind.LaboDuChaos -> BuildingKindInfo(
      Faction.Science,
      I18nText("Labo du Chaos", "Chaos Lab"),
      I18nText("Labo du Chaos.md", "Chaos Lab.md"),
      "labo-du-chaos.png"
    )
  )

  val unitInfo: Map[UnitKind, UnitKindInfo] = Map(
    UnitKind.Elf -> UnitKindInfo(Faction.Nature, I18nText("Elfe", "Elf"), I18nText("Elfe.md", "Elf.md"), "elf/front-walk-00.png"),
    UnitKind.Wolf -> UnitKindInfo(Faction.Nature, I18nText("Loup", "Wolf"), I18nText("Loup.md", "Wolf.md"), "wolf-reference.png"),
    UnitKind.Tree -> UnitKindInfo(
      Faction.Nature,
      I18nText("Arbre Animé", "Animated Tree"),
      I18nText("Arbre Animé.md", "Animated Tree.md"),
      "tree/front-walk-00.png"
    ),
    UnitKind.Goblin -> UnitKindInfo(
      Faction.Chaos,
      I18nText("Gobelin", "Goblin"),
      I18nText("Gobelin.md", "Goblin.md"),
      "goblin/front-walk-00.png"
    ),
    UnitKind.Minotaur -> UnitKindInfo(
      Faction.Chaos,
      I18nText("Minotaure", "Minotaur"),
      I18nText("Minotaure.md", "Minotaur.md"),
      "minotaur.png"
    ),
    UnitKind.Paladin -> UnitKindInfo(Faction.Loi, I18nText("Paladin", "Paladin"), I18nText("Paladin.md", "Paladin.md"), "paladin.png"),
    UnitKind.Zombie -> UnitKindInfo(
      Faction.Mort,
      I18nText("Zombie", "Zombie"),
      I18nText("Zombie.md", "Zombie.md"),
      "zombie/walk.gif"
    ),
    UnitKind.Vampire -> UnitKindInfo(Faction.Mort, I18nText("Vampire", "Vampire"), I18nText("Vampire.md", "Vampire.md"), "vampire.png"),
    UnitKind.Necromancer -> UnitKindInfo(
      Faction.Mort,
      I18nText("Nécromancien", "Necromancer"),
      I18nText("Necromancien.md", "Necromancer.md"),
      "necromancer/walk-00.png"
    ),
    UnitKind.Soul -> UnitKindInfo(Faction.Mort, I18nText("Âme", "Soul"), I18nText("Âme.md", "Soul.md"), "soul/walk-00.png")
  )

  val resourceInfo: Map[Resource, ResourceKindInfo] = Map(
    Resource.Wood -> ResourceKindInfo(
      Faction.Nature,
      I18nText("Bois", "Wood"),
      I18nText("Bois.md", "Wood.md"),
      Some("bois-reference.png")
    ),
    Resource.Fire -> ResourceKindInfo(
      Faction.Chaos,
      I18nText("Feu", "Fire"),
      I18nText("Feu.md", "Fire.md"),
      Some("feu-reference.png")
    ),
    Resource.Light -> ResourceKindInfo(
      Faction.Loi,
      I18nText("Lumière", "Light"),
      I18nText("Lumière.md", "Light.md"),
      Some("lumiere-reference.png")
    ),
    Resource.Shadow -> ResourceKindInfo(Faction.Mort, I18nText("Ombre", "Shadow"), I18nText("Ombre.md", "Shadow.md"), None),
    Resource.Crystal -> ResourceKindInfo(
      Faction.Science,
      I18nText("Crystal", "Crystal"),
      I18nText("Crystal.md", "Crystal.md"),
      Some("crystal-reference.png")
    )
  )

  def factionName(f: Faction, lang: Lang): String = factionInfo(f).name(lang)

  // The vault folder name per language is the same string as the display name in every
  // case (FR: bare Faction.toString; EN: "Law"/"Death" happen to double as both the
  // display name and the folder Loi/Mort get renamed to — see FactionInfo's doc) — no
  // separate field needed.
  def factionFolder(f: Faction, lang: Lang): String = factionInfo(f).name(lang)

  // The faction's own overview page, in the *FR* vault only — Nature.md/Chaos.md/Loi.md/
  // Mort.md/Science.md are hand-written narrative pages (victory flavor text, Relations
  // cross-links) outside this generator's scope (buildings/units/resources only — see
  // CLAUDE.md's task boundary). An EN doc still needs somewhere to send the "faction"
  // frontmatter link, so it points at the same FR overview page across the two vault
  // trees rather than a nonexistent EN one.
  def factionFrOverviewFile(f: Faction): String = factionInfo(f).overviewFrFile

  def resourceName(r: Resource, lang: Lang): String = resourceInfo(r).name(lang)
  def resourceFileName(r: Resource, lang: Lang): String = resourceInfo(r).fileName(lang)
  def buildingName(k: BuildingKind, lang: Lang): String = buildingInfo(k).name(lang)
  def buildingFileName(k: BuildingKind, lang: Lang): String = buildingInfo(k).fileName(lang)
  def unitName(k: UnitKind, lang: Lang): String = unitInfo(k).name(lang)
  def unitFileName(k: UnitKind, lang: Lang): String = unitInfo(k).fileName(lang)

  def vaultRoot(lang: Lang): String = if lang == Lang.Fr then "Resources" else "Resources-en"

  // Path (relative to the vault root, forward-slashed) to a building/unit/resource's own
  // doc page — used both to know where DocGenerator writes a file and to build a
  // Markdown link from a sibling doc (same faction subfolder, so a same-language,
  // same-faction cross-link is always just the bare file name — see relativeTo).
  def buildingPath(k: BuildingKind, lang: Lang): String =
    val info = buildingInfo(k)
    s"${factionFolder(info.faction, lang)}/${info.fileName(lang)}"

  def unitPath(k: UnitKind, lang: Lang): String =
    val info = unitInfo(k)
    s"${factionFolder(info.faction, lang)}/${info.fileName(lang)}"

  def resourcePath(r: Resource, lang: Lang): String =
    val info = resourceInfo(r)
    s"${factionFolder(info.faction, lang)}/${info.fileName(lang)}"

  // ── Cross-links between generated pages ─────────────────────────────────
  // Every generated page lives in its own faction subfolder (Resources/<Faction>/ or
  // Resources-en/<Faction>/) — a same-faction cross-link is just the bare file name, an
  // other-faction one needs a `../<OtherFaction>/` prefix. Kept as a single relative-path
  // rule here so DocGenerator's per-kind body text never hand-rolls "../X/" itself.
  private def relativeTo(from: Faction, to: Faction, fileName: String, lang: Lang): String =
    if from == to then fileName else s"../${factionFolder(to, lang)}/$fileName"

  // Matches the existing vault's own convention (e.g. Labo Fondamental.md's own links:
  // "[Labo Naturel](Labo%20Naturel.md)") — spaces in a link *target* are percent-encoded,
  // everything else (accented letters included) is left as-is. Only the href needs this;
  // the actual files on disk keep their literal spaces (see *FileName/DocGenerator, which
  // never route through this function).
  private def mdLink(text: String, path: String): String = s"[$text](${path.replace(" ", "%20")})"

  def buildingLink(from: Faction, k: BuildingKind, lang: Lang): String =
    val info = buildingInfo(k)
    mdLink(info.name(lang), relativeTo(from, info.faction, info.fileName(lang), lang))

  def unitLink(from: Faction, k: UnitKind, lang: Lang): String =
    val info = unitInfo(k)
    mdLink(info.name(lang), relativeTo(from, info.faction, info.fileName(lang), lang))

  def resourceLink(from: Faction, r: Resource, lang: Lang): String =
    val info = resourceInfo(r)
    mdLink(info.name(lang), relativeTo(from, info.faction, info.fileName(lang), lang))

  // A link from an EN page to an FR-only page (Corruption.md, the faction overview pages)
  // that this generator doesn't produce an English version of (out of scope — see
  // factionFrOverviewFile's doc) — crosses from Resources-en/<Faction>/ back into the FR
  // Resources/<Faction>/ tree, which sits at the same depth under the repo root.
  def frFallbackLink(text: String, faction: Faction, frFileName: String): String =
    mdLink(text, s"../../Resources/${factionFolder(faction, Lang.Fr)}/$frFileName")

  // A link to a vault page this generator doesn't itself produce (Corruption.md, the
  // Note/Recherche* Science pages) — an FR page links straight to the existing FR file
  // (possibly in another faction's folder, same as any other cross-link — see
  // `relativeTo`); an EN page, which has no translated version of that page, falls back
  // to the same FR file across trees (see frFallbackLink).
  def outOfScopeLink(text: String, from: Faction, target: Faction, frFileName: String, lang: Lang): String =
    if lang == Lang.Fr then mdLink(text, relativeTo(from, target, frFileName, Lang.Fr))
    else frFallbackLink(text, target, frFileName)

  // A page's link to its own faction's overview — FR pages link within their own tree
  // (Resources/Loi/Paladin.md -> Loi.md, same folder); EN pages have no translated
  // overview page (out of scope, see factionFrOverviewFile's doc) so they fall back to
  // the FR one, displayed under the faction's English name.
  def factionLink(f: Faction, lang: Lang): String =
    if lang == Lang.Fr then mdLink(factionName(f, lang), factionFrOverviewFile(f))
    else frFallbackLink(factionName(f, lang), f, factionFrOverviewFile(f))
