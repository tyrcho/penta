package towerdefense.domain.i18n

import towerdefense.domain.*

// Display names and vault doc paths for every Faction/Resource/BuildingKind/UnitKind, in
// both languages — the single naming source shared by DocGenerator (which needs a
// cross-link-safe file path per kind) and the game UI (which only needs the display name).
//
// FR file paths mirror the *existing* hand-written vault exactly (see Resources/), so
// regenerating those files in place never breaks a link written anywhere else in the repo
// (ADRs, other vault pages). EN file paths are this generator's own choice, under a
// sibling `Resources-en/` tree with the same Faction subfolders (renamed to their English
// spelling) and English file names, mirroring the FR tree's depth so both use the same
// "../../game/assets/..." relative asset path.
object EntityNames:

  def factionName(f: Faction, lang: Lang): String = (f, lang) match
    case (Faction.Nature, Lang.Fr)  => "Nature"
    case (Faction.Nature, Lang.En)  => "Nature"
    case (Faction.Chaos, Lang.Fr)   => "Chaos"
    case (Faction.Chaos, Lang.En)   => "Chaos"
    case (Faction.Loi, Lang.Fr)     => "Loi"
    case (Faction.Loi, Lang.En)     => "Law"
    case (Faction.Mort, Lang.Fr)    => "Mort"
    case (Faction.Mort, Lang.En)    => "Death"
    case (Faction.Science, Lang.Fr) => "Science"
    case (Faction.Science, Lang.En) => "Science"

  // Vault folder name per language — FR matches Resources/'s existing subfolders exactly;
  // EN is this generator's own Resources-en/ layout (see the module doc above).
  def factionFolder(f: Faction, lang: Lang): String = (f, lang) match
    case (Faction.Loi, Lang.En)  => "Law"
    case (Faction.Mort, Lang.En) => "Death"
    case (other, _)              => other.toString

  // The faction's own overview page, in the *FR* vault only — Nature.md/Chaos.md/Loi.md/
  // Mort.md/Science.md are hand-written narrative pages (victory flavor text, Relations
  // cross-links) outside this generator's scope (buildings/units/resources only — see
  // CLAUDE.md's task boundary). An EN doc still needs somewhere to send the "faction"
  // frontmatter link, so it points at the same FR overview page across the two vault
  // trees rather than a nonexistent EN one.
  def factionFrOverviewFile(f: Faction): String = s"${f.toString}.md"

  def resourceName(r: Resource, lang: Lang): String = (r, lang) match
    case (Resource.Wood, Lang.Fr)    => "Bois"
    case (Resource.Wood, Lang.En)    => "Wood"
    case (Resource.Fire, Lang.Fr)    => "Feu"
    case (Resource.Fire, Lang.En)    => "Fire"
    case (Resource.Light, Lang.Fr)   => "Lumière"
    case (Resource.Light, Lang.En)   => "Light"
    case (Resource.Shadow, Lang.Fr)  => "Ombre"
    case (Resource.Shadow, Lang.En)  => "Shadow"
    case (Resource.Crystal, Lang.Fr) => "Crystal"
    case (Resource.Crystal, Lang.En) => "Crystal"

  // FR file name matches the existing vault file exactly (accents and all); EN is this
  // generator's own choice.
  def resourceFileName(r: Resource, lang: Lang): String = (r, lang) match
    case (Resource.Wood, Lang.Fr)    => "Bois.md"
    case (Resource.Wood, Lang.En)    => "Wood.md"
    case (Resource.Fire, Lang.Fr)    => "Feu.md"
    case (Resource.Fire, Lang.En)    => "Fire.md"
    case (Resource.Light, Lang.Fr)   => "Lumière.md"
    case (Resource.Light, Lang.En)   => "Light.md"
    case (Resource.Shadow, Lang.Fr)  => "Ombre.md"
    case (Resource.Shadow, Lang.En)  => "Shadow.md"
    case (Resource.Crystal, Lang.Fr) => "Crystal.md"
    case (Resource.Crystal, Lang.En) => "Crystal.md"

  def buildingName(k: BuildingKind, lang: Lang): String = (k, lang) match
    case (BuildingKind.Grove, Lang.Fr)           => "Bosquet"
    case (BuildingKind.Grove, Lang.En)           => "Grove"
    case (BuildingKind.Forest, Lang.Fr)          => "Forêt"
    case (BuildingKind.Forest, Lang.En)          => "Forest"
    case (BuildingKind.Jungle, Lang.Fr)          => "Jungle"
    case (BuildingKind.Jungle, Lang.En)          => "Jungle"
    case (BuildingKind.Stonehenge, Lang.Fr)      => "Stonehenge"
    case (BuildingKind.Stonehenge, Lang.En)      => "Stonehenge"
    case (BuildingKind.Cave, Lang.Fr)            => "Cave"
    case (BuildingKind.Cave, Lang.En)            => "Cave"
    case (BuildingKind.Labyrinth, Lang.Fr)       => "Labyrinthe"
    case (BuildingKind.Labyrinth, Lang.En)       => "Labyrinth"
    case (BuildingKind.Church, Lang.Fr)          => "Église"
    case (BuildingKind.Church, Lang.En)          => "Church"
    case (BuildingKind.Watchtower, Lang.Fr)      => "Tour de guet"
    case (BuildingKind.Watchtower, Lang.En)      => "Watchtower"
    case (BuildingKind.Angel, Lang.Fr)           => "Ange"
    case (BuildingKind.Angel, Lang.En)           => "Angel"
    case (BuildingKind.Tomb, Lang.Fr)            => "Tombe"
    case (BuildingKind.Tomb, Lang.En)            => "Tomb"
    case (BuildingKind.BlackCastle, Lang.Fr)     => "Château Noir"
    case (BuildingKind.BlackCastle, Lang.En)     => "Black Castle"
    case (BuildingKind.DeathHouse, Lang.Fr)      => "Maison de la Mort"
    case (BuildingKind.DeathHouse, Lang.En)      => "House of Death"
    case (BuildingKind.PassingGate, Lang.Fr)     => "Portail"
    case (BuildingKind.PassingGate, Lang.En)     => "Passing Gate"
    case (BuildingKind.LaboFondamental, Lang.Fr) => "Labo Fondamental"
    case (BuildingKind.LaboFondamental, Lang.En) => "Base Lab"
    case (BuildingKind.LaboNaturel, Lang.Fr)     => "Labo Naturel"
    case (BuildingKind.LaboNaturel, Lang.En)     => "Nature Lab"
    case (BuildingKind.LaboSombre, Lang.Fr)      => "Labo Sombre"
    case (BuildingKind.LaboSombre, Lang.En)      => "Shadow Lab"
    case (BuildingKind.LaboDeRecherche, Lang.Fr) => "Labo de Recherche"
    case (BuildingKind.LaboDeRecherche, Lang.En) => "Research Lab"
    case (BuildingKind.LaboDeLaLoi, Lang.Fr)     => "Labo de la Loi"
    case (BuildingKind.LaboDeLaLoi, Lang.En)     => "Law Lab"
    case (BuildingKind.LaboDuChaos, Lang.Fr)     => "Labo du Chaos"
    case (BuildingKind.LaboDuChaos, Lang.En)     => "Chaos Lab"

  // FR file name matches the existing vault file exactly; EN is this generator's own choice.
  def buildingFileName(k: BuildingKind, lang: Lang): String = (k, lang) match
    case (BuildingKind.Grove, Lang.Fr)           => "Bosquet.md"
    case (BuildingKind.Grove, Lang.En)           => "Grove.md"
    case (BuildingKind.Forest, Lang.Fr)          => "Foret.md"
    case (BuildingKind.Forest, Lang.En)          => "Forest.md"
    case (BuildingKind.Jungle, Lang.Fr)          => "Jungle.md"
    case (BuildingKind.Jungle, Lang.En)          => "Jungle.md"
    case (BuildingKind.Stonehenge, Lang.Fr)      => "Stonehenge.md"
    case (BuildingKind.Stonehenge, Lang.En)      => "Stonehenge.md"
    case (BuildingKind.Cave, Lang.Fr)            => "Cave.md"
    case (BuildingKind.Cave, Lang.En)            => "Cave.md"
    case (BuildingKind.Labyrinth, Lang.Fr)       => "Labyrinthe.md"
    case (BuildingKind.Labyrinth, Lang.En)       => "Labyrinth.md"
    case (BuildingKind.Church, Lang.Fr)          => "Eglise.md"
    case (BuildingKind.Church, Lang.En)          => "Church.md"
    case (BuildingKind.Watchtower, Lang.Fr)      => "Tour de guet.md"
    case (BuildingKind.Watchtower, Lang.En)      => "Watchtower.md"
    case (BuildingKind.Angel, Lang.Fr)           => "Ange.md"
    case (BuildingKind.Angel, Lang.En)           => "Angel.md"
    case (BuildingKind.Tomb, Lang.Fr)            => "Tombe.md"
    case (BuildingKind.Tomb, Lang.En)            => "Tomb.md"
    case (BuildingKind.BlackCastle, Lang.Fr)     => "Chateau Noir.md"
    case (BuildingKind.BlackCastle, Lang.En)     => "Black Castle.md"
    case (BuildingKind.DeathHouse, Lang.Fr)      => "Maison de la Mort.md"
    case (BuildingKind.DeathHouse, Lang.En)      => "House of Death.md"
    case (BuildingKind.PassingGate, Lang.Fr)     => "Portail.md"
    case (BuildingKind.PassingGate, Lang.En)     => "Passing Gate.md"
    case (BuildingKind.LaboFondamental, Lang.Fr) => "Labo Fondamental.md"
    case (BuildingKind.LaboFondamental, Lang.En) => "Base Lab.md"
    case (BuildingKind.LaboNaturel, Lang.Fr)     => "Labo Naturel.md"
    case (BuildingKind.LaboNaturel, Lang.En)     => "Nature Lab.md"
    case (BuildingKind.LaboSombre, Lang.Fr)      => "Labo Sombre.md"
    case (BuildingKind.LaboSombre, Lang.En)      => "Shadow Lab.md"
    case (BuildingKind.LaboDeRecherche, Lang.Fr) => "Labo de Recherche.md"
    case (BuildingKind.LaboDeRecherche, Lang.En) => "Research Lab.md"
    case (BuildingKind.LaboDeLaLoi, Lang.Fr)     => "Labo de la Loi.md"
    case (BuildingKind.LaboDeLaLoi, Lang.En)     => "Law Lab.md"
    case (BuildingKind.LaboDuChaos, Lang.Fr)     => "Labo du Chaos.md"
    case (BuildingKind.LaboDuChaos, Lang.En)     => "Chaos Lab.md"

  def unitName(k: UnitKind, lang: Lang): String = (k, lang) match
    case (UnitKind.Elf, Lang.Fr)         => "Elfe"
    case (UnitKind.Elf, Lang.En)         => "Elf"
    case (UnitKind.Goblin, Lang.Fr)      => "Gobelin"
    case (UnitKind.Goblin, Lang.En)      => "Goblin"
    case (UnitKind.Minotaur, Lang.Fr)    => "Minotaure"
    case (UnitKind.Minotaur, Lang.En)    => "Minotaur"
    case (UnitKind.Paladin, Lang.Fr)     => "Paladin"
    case (UnitKind.Paladin, Lang.En)     => "Paladin"
    case (UnitKind.Wolf, Lang.Fr)        => "Loup"
    case (UnitKind.Wolf, Lang.En)        => "Wolf"
    case (UnitKind.Zombie, Lang.Fr)      => "Zombie"
    case (UnitKind.Zombie, Lang.En)      => "Zombie"
    case (UnitKind.Vampire, Lang.Fr)     => "Vampire"
    case (UnitKind.Vampire, Lang.En)     => "Vampire"
    case (UnitKind.Necromancer, Lang.Fr) => "Nécromancien"
    case (UnitKind.Necromancer, Lang.En) => "Necromancer"
    case (UnitKind.Soul, Lang.Fr)        => "Âme"
    case (UnitKind.Soul, Lang.En)        => "Soul"
    case (UnitKind.Tree, Lang.Fr)        => "Arbre Animé"
    case (UnitKind.Tree, Lang.En)        => "Animated Tree"

  // FR file name matches the existing vault file exactly; EN is this generator's own choice.
  def unitFileName(k: UnitKind, lang: Lang): String = (k, lang) match
    case (UnitKind.Elf, Lang.Fr)         => "Elfe.md"
    case (UnitKind.Elf, Lang.En)         => "Elf.md"
    case (UnitKind.Goblin, Lang.Fr)      => "Gobelin.md"
    case (UnitKind.Goblin, Lang.En)      => "Goblin.md"
    case (UnitKind.Minotaur, Lang.Fr)    => "Minotaure.md"
    case (UnitKind.Minotaur, Lang.En)    => "Minotaur.md"
    case (UnitKind.Paladin, Lang.Fr)     => "Paladin.md"
    case (UnitKind.Paladin, Lang.En)     => "Paladin.md"
    case (UnitKind.Wolf, Lang.Fr)        => "Loup.md"
    case (UnitKind.Wolf, Lang.En)        => "Wolf.md"
    case (UnitKind.Zombie, Lang.Fr)      => "Zombie.md"
    case (UnitKind.Zombie, Lang.En)      => "Zombie.md"
    case (UnitKind.Vampire, Lang.Fr)     => "Vampire.md"
    case (UnitKind.Vampire, Lang.En)     => "Vampire.md"
    case (UnitKind.Necromancer, Lang.Fr) => "Necromancien.md"
    case (UnitKind.Necromancer, Lang.En) => "Necromancer.md"
    case (UnitKind.Soul, Lang.Fr)        => "Âme.md"
    case (UnitKind.Soul, Lang.En)        => "Soul.md"
    case (UnitKind.Tree, Lang.Fr)        => "Arbre Animé.md"
    case (UnitKind.Tree, Lang.En)        => "Animated Tree.md"

  def vaultRoot(lang: Lang): String = lang match
    case Lang.Fr => "Resources"
    case Lang.En => "Resources-en"

  // Path (relative to the vault root, forward-slashed) to a building/unit/resource's own
  // doc page — used both to know where DocGenerator writes a file and to build a
  // Markdown link from a sibling doc (same faction subfolder, so a same-language,
  // same-faction cross-link is always just the bare file name — see DocGenerator).
  def buildingPath(k: BuildingKind, lang: Lang): String =
    s"${factionFolder(Faction.of(k), lang)}/${buildingFileName(k, lang)}"

  def unitPath(k: UnitKind, lang: Lang): String =
    s"${factionFolder(Faction.of(k), lang)}/${unitFileName(k, lang)}"

  def resourcePath(r: Resource, lang: Lang): String =
    s"${factionFolder(Faction.of(r), lang)}/${resourceFileName(r, lang)}"

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
  // the actual files on disk keep their literal spaces (see EntityNames.*FileName/
  // DocGenerator, which never route through this function).
  private def mdLink(text: String, path: String): String = s"[$text](${path.replace(" ", "%20")})"

  def buildingLink(from: Faction, k: BuildingKind, lang: Lang): String =
    mdLink(buildingName(k, lang), relativeTo(from, Faction.of(k), buildingFileName(k, lang), lang))

  def unitLink(from: Faction, k: UnitKind, lang: Lang): String =
    mdLink(unitName(k, lang), relativeTo(from, Faction.of(k), unitFileName(k, lang), lang))

  def resourceLink(from: Faction, r: Resource, lang: Lang): String =
    mdLink(resourceName(r, lang), relativeTo(from, Faction.of(r), resourceFileName(r, lang), lang))

  // A link from an EN page to an FR-only page (Corruption.md, the faction overview pages)
  // that this generator doesn't produce an English version of (out of scope — see
  // factionFrOverviewFile's doc) — crosses from Resources-en/<Faction>/ back into the FR
  // Resources/<Faction>/ tree, which sits at the same depth under the repo root.
  def frFallbackLink(text: String, faction: Faction, frFileName: String): String =
    mdLink(text, s"../../Resources/${faction.toString}/$frFileName")

  // A link to a vault page this generator doesn't itself produce (Corruption.md, the
  // Note/Recherche* Science pages) — an FR page links straight to the existing FR file
  // (possibly in another faction's folder, same as any other cross-link — see
  // `relativeTo`); an EN page, which has no translated version of that page, falls back
  // to the same FR file across trees (see frFallbackLink).
  def outOfScopeLink(text: String, from: Faction, target: Faction, frFileName: String, lang: Lang): String =
    lang match
      case Lang.Fr => mdLink(text, relativeTo(from, target, frFileName, Lang.Fr))
      case Lang.En => frFallbackLink(text, target, frFileName)

  // A page's link to its own faction's overview — FR pages link within their own tree
  // (Resources/Loi/Paladin.md -> Loi.md, same folder); EN pages have no translated
  // overview page (out of scope, see factionFrOverviewFile's doc) so they fall back to
  // the FR one, displayed under the faction's English name.
  def factionLink(f: Faction, lang: Lang): String = lang match
    case Lang.Fr => mdLink(factionName(f, lang), factionFrOverviewFile(f))
    case Lang.En => frFallbackLink(factionName(f, lang), f, factionFrOverviewFile(f))
