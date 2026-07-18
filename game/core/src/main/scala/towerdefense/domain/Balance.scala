package towerdefense.domain

// Single source of truth for gameplay tuning. Numbers marked "per Forest.md/Elf.md" or
// "per Cave.md/Goblin.md" come straight from the vault's faction definitions
// (Resources/Nature/, Resources/Chaos/); numbers marked "POC default" aren't
// specified there and were chosen for playability, tuned after actually running the game.
object Balance:

  // ── Nature (player) ─────────────────────────────────────────────────────
  // Three-tier upgrade chain (Bosquet.md/Foret.md/Jungle.md) — only Grove is directly
  // buildable; Forest and Jungle are reached by upgrading in place (see
  // Placement.tryUpgradeBuilding). Each tier's cost is the upgrade cost paid at that
  // step, not a cumulative total.
  val GroveCostWood: Double = 5.0 // Bosquet.md: "cout en bois: 10"
  val WoodPerSecPerGrove: Double = 0.2 // Bosquet.md: "produit 1 bois / 5 sec"
  val ElfSpawnIntervalMs: Double = 10_000.0 // Bosquet.md: "toutes les 10 sec genere un Elf"

  val ForestUpgradeCostWood: Double = 20.0 // Foret.md: "cout en bois: 30"
  val WoodPerSecPerForest: Double = 0.5 // Foret.md: "produit 1 bois / 2 sec"
  // Foret.md: Ents deal 2 dmg/sec to adjacent units — Jungle inherits this (upgrades are
  // cumulative: Jungle.md doesn't repeat the ability, but "Amelioration" implies it keeps
  // what the prior tier had, only Grove/Bosquet lacks it).
  val AuraDamagePerSec: Double = 2.0

  val JungleUpgradeCostWood: Double = 50.0 // Jungle.md: "cout en bois: 60"
  val WoodPerSecPerJungle: Double = 1.0 // Jungle.md: "produit 1 bois / sec"
  val WolfSpawnIntervalMs: Double = 10_000.0 // Jungle.md: "toutes les 5 sec"

  val ElfMaxHp: Double = 5.0
  val ElfSpeedPerMs: Double = 0.05 // POC default: 50 px/s

  val WolfMaxHp: Double = 30.0 // Loup.md: "PV: 40"
  val WolfSpeedPerMs: Double = ElfSpeedPerMs * 1.5 // Loup.md: "1.5x plus vite que les unites standard"
  // Loup.md: "augmente la vitesse de deplacement des unites a 2 cases de 50%" — a
  // multiplier (1.5x), not a flat addition, applied to any other creature within range.
  val WolfSpeedAuraMultiplier: Double = 1.5
  val WolfSpeedAuraRangeCells: Int = 2

  // Not from the vault's own numbers — added at the project owner's explicit request:
  // each Nature building heals corruption (Corruption.md's mechanic) from itself and its 8
  // surrounding buildings (a Chebyshev-distance-1 block, unlike the aura-damage/corruption
  // rules elsewhere which only reach the 4 orthogonal neighbors), at a rate depending on
  // its own tier. Multiple nearby healers stack, same "summed, not just present/absent" as
  // multiple corrupting creatures on the same building — see
  // CombatEngine.healBuildingCorruption.
  val GroveCorruptionHealPercentPerSec: Double = 0.1
  val ForestCorruptionHealPercentPerSec: Double = 0.3
  val JungleCorruptionHealPercentPerSec: Double = 0.5

  // Stonehenge.md: Nature's fourth building, wood-only like Grove/Forest/Jungle but at a
  // much steeper cost — spawns a self-cloning Arbre Anime (Tree) that stays in its OWN
  // maze (unlike every other building's spawn, which crosses into the opponent's — see
  // BattleEngine.stayHomeUnitKinds) and counts toward this maze's own forest-victory tally
  // while alive (VictoryConditions.forestCount).
  val StonehengeCostWood: Double = 150.0
  val StonehengeSpawnIntervalMs: Double = 10_000.0
  val TreeMaxHp: Double = 100.0
  // "lent comme un Zombie" — same formula as Balance.ZombieSpeedPerMs (defined later, in
  // the Mort section — inlined here to avoid a forward reference within this object).
  val TreeSpeedPerMs: Double = ElfSpeedPerMs * 0.5
  // Every TreeCloneIntervalMs a Tree freezes for TreeCloneFreezeMs and spawns a full-HP
  // clone of itself one cell further along its own path, then resumes walking — same
  // freeze+spawn shape as the Necromancer (CreatureSpec.spawnFreezeMs), except the clone
  // appears at the *next* path cell rather than the summoner's own position (see
  // CreatureSpec.spawnAtNextCell / CombatEngine.advanceCreatureSummons).
  val TreeCloneIntervalMs: Double = 10_000.0
  val TreeCloneFreezeMs: Double = 3_000.0

  // ── Chaos (AI) ───────────────────────────────────────────────────────────
  val CaveCostWood: Double = 0.0 // Cave.md: "cout en wood: 5"
  val CaveCostFire: Double = 10.0 // Cave.md: "cout en fire: 10"
  val FirePerSecPerCave: Double = 0.2 // Cave.md: "produit 1 feu / 5 sec"
  val GoblinSpawnIntervalMs: Double = 5_000.0 // Cave.md: "toutes les 5 sec elle genere un Goblin"

  val GoblinMaxHp: Double = 5.0
  val GoblinSpeedPerMs: Double = 0.05 // POC default, matches Elf

  // Goblin.md: "Pille une ressource de chaque type" — POC default: 1 unit of wood
  // and 1 unit of fire per unit that reaches the goal (Elf included: symmetric, see
  // CLAUDE.md), clamped to what's available.
  val PlunderPerUnit: Double = 1.0

  val LabyrintheCostWood: Double = 10.0 // Labyrinthe.md: "cout en bois: 20"
  val LabyrintheCostFire: Double = 50.0 // Labyrinthe.md: "cout en feu: 40"
  val MinotaurSpawnIntervalMs: Double =
    10_000.0 // Labyrinthe.md: "toutes les 10 secondes genere un Minotaure"

  val MinotaurMaxHp: Double = 50.0 // Minotaure.md: "PV: 50"
  val MinotaurSpeedPerMs: Double = 0.05 // POC default, matches Elf/Goblin

  // Minotaure.md: "Pille 10 ressources de chaque type" — a heavier, slower-to-produce
  // raider than the Goblin's PlunderPerUnit.
  val MinotaurPlunderPerUnit: Double = 10.0

  // ── Loi ──────────────────────────────────────────────────────────────────
  val EgliseCostWood: Double = 20.0 // Eglise.md: "cout en bois: 40"
  val EgliseCostLight: Double = 40.0 // Eglise.md: "cout en lumiere: 20"
  val LightPerSecPerEglise: Double = 0.3 // Eglise.md: "Produit 0.3 Lumiere par seconde"
  val PaladinSpawnIntervalMs: Double =
    10_000.0 // Eglise.md: "toutes les 10 secondes genere un Paladin"

  val PaladinMaxHp: Double = 50.0 // Paladin.md: "PV: 50"
  val PaladinSpeedPerMs: Double = 0.05 // POC default, matches Elf/Goblin/Minotaur

  // Paladin.md: "Aura: protege les unites adjacentes de 2 degats" — fully cancels the
  // Forest's AuraDamagePerSec (also 2.0) for any adjacent unit, POC default. The
  // Paladin itself has no plunder ability (Paladin.md doesn't mention one, unlike
  // Elf/Goblin/Minotaur) — see CombatEngine.plunderAmounts.
  val PaladinAuraDamageReductionPerSec: Double = 2.0

  // Tour de guet.md: Loi's second building — no unit spawn, produces Light like Eglise
  // and deals direct single-target damage instead of a passive adjacency aura.
  val WatchtowerCostWood: Double = 10.0 // "cout en bois: 10"
  val WatchtowerCostLight: Double = 20.0 // "cout en lumiere: 5"
  val LightPerSecPerWatchtower: Double = 0.3 // POC tuning: raised from Tour de guet.md's 0.1/sec
  val WatchtowerDamagePerSec: Double = 10.0 // "Inflige 10 degats chaque seconde a une cible"
  // "jusqu'a 2 cases de distance" — Chebyshev (king-move) distance in cells, the usual
  // reading of tower range on a grid: any cell within a 5x5 block centered on the tower.
  val WatchtowerRangeCells: Int = 2

  // Ange.md: Loi's third building — no unit spawn, produces Light like Eglise/Watchtower,
  // and (like Forest/Jungle's Ent aura) deals passive damage to every adjacent enemy, plus
  // a slow debuff Forest/Jungle don't have.
  val AngelCostLight: Double = 50.0 // Ange.md: "cout en lumiere: 50"
  val LightPerSecPerAngel: Double = 0.5 // Ange.md: "Produit 0.5 Lumiere par seconde"
  val AngelDamagePerSec: Double = 5.0 // Ange.md: "Inflige 5 degats par seconde aux unites adjacentes"
  // Ange.md: "ralentit leur vitesse de deplacement de 25%" — a multiplier (0.75x) applied
  // to any enemy creature adjacent to an Angel, same adjacency rule as its damage (see
  // CombatEngine.effectiveSpeedPerMs), stacking multiplicatively with Wolf's speed boost.
  val AngelSlowFraction: Double = 0.25

  // ── Mort (Death) ─────────────────────────────────────────────────────────
  val TombCostWood: Double = 5.0 // Tombe.md: "cout en bois: 5"
  val TombCostShadow: Double = 10.0 // Tombe.md: "cout en ombre: 10"
  val ShadowPerSecPerTomb: Double = 0.2 // Tombe.md: "Produit 0.2 ombre / sec"
  val ZombieSpawnIntervalMs: Double = 10_000.0 // Tombe.md: "Envoie un Zombie toutes les 10s"

  val BlackCastleCostWood: Double = 20.0 // Chateau Noir.md: "cout en bois: 20"
  val BlackCastleCostShadow: Double = 40.0 // Chateau Noir.md: "cout en ombre: 40"
  val ShadowPerSecPerBlackCastle: Double = 0.5 // Chateau Noir.md: "Produit 0.5 ombre / sec"
  val VampireSpawnIntervalMs: Double = 10_000.0

  val ZombieMaxHp: Double = 15.0 // Zombie.md: "PV: 15"
  // Zombie.md: "Se deplace lentement (1 case en 2 sec)" — half Elf's 1 cell/sec pace.
  val ZombieSpeedPerMs: Double = ElfSpeedPerMs * 0.5
  // Zombie.md: "Corrompt les batiments adjacents de 1% par seconde" — see CombatEngine's
  // corruption mechanic (Corruption.md): a Zombie/Vampire standing adjacent to an enemy
  // building raises its corruptionPercent each tick; at CorruptionMaxPercent the building
  // is destroyed and its cost refunded to the corrupting unit's owner (not the building's
  // own owner, unlike Demolition).
  val ZombieCorruptionPercentPerSec: Double = 1.0

  val VampireMaxHp: Double = 50.0 // Vampire.md: "PV: 50"
  val VampireSpeedPerMs: Double = ElfSpeedPerMs * 1.5 // Vampire.md: "Se deplace vite (1.5 case/ sec)" — matches Wolf's pace
  val VampireCorruptionPercentPerSec: Double = 2.0 // Vampire.md: "Corrompt les batiments adjacents de 2% par seconde"
  // Vampire.md: "Reduit les degats qu'il subit de 50% (mais n'est pas protege par l'aura du
  // Paladin)" — an unconditional flat reduction applied in CombatEngine, explicitly instead
  // of (not stacked with) Paladin's shield: a Vampire is excluded from paladinShieldedIds
  // even when standing adjacent to a Paladin.
  val VampireDamageReductionFraction: Double = 0.5

  // Maison de la Mort.md: Mort's third building — no upgrade tier, unlike Tomb/BlackCastle
  // it sends a *unit that itself spawns another unit* (Necromancien -> Ame) instead of a
  // single kind directly.
  val DeathHouseCostWood: Double = 10.0 // "cout en bois: 10"
  val DeathHouseCostShadow: Double = 40.0 // "cout en ombre: 40"
  val ShadowPerSecPerDeathHouse: Double = 0.5 // "Produit 0.5 ombre / sec"
  val NecromancerSpawnIntervalMs: Double = 10_000.0 // "Envoie un Necromancien toutes les 10 secondes"

  val NecromancerMaxHp: Double = 40.0 // Necromancien.md: "PV: 40"
  // Necromancien.md: "Se deplace lentement... comme un Zombie" — same pace, not just the
  // same wording, so it stays derived rather than a second copy of the same number.
  val NecromancerSpeedPerMs: Double = ZombieSpeedPerMs
  // Necromancien.md: "Toutes les 5 secondes, invoque une Ame" — a *creature* spawning
  // another creature into the same maze it's currently walking, unlike every other spawn
  // in the game (always building -> opponent's maze) — see CombatEngine.advanceCreatureSummons.
  val SoulSummonIntervalMs: Double = 5_000.0
  // Necromancien.md: "pendant 1 seconde, il reste immobile" — the instant it summons a
  // Soul, it stops advancing toward the goal for this long (see CreatureSpec.spawnFreezeMs/
  // Creature.frozenMs, CombatEngine.stepCreature).
  val NecromancerSummonFreezeMs: Double = 1_000.0

  val SoulMaxHp: Double = 10.0 // Ame.md: "PV: 10"
  // Ame.md: "Se deplace a vitesse normale (1 case/sec)" — Elf's own pace is the game's
  // baseline "normal" speed (every other unit is defined relative to it: Zombie is half,
  // Wolf/Vampire are 1.5x).
  val SoulSpeedPerMs: Double = ElfSpeedPerMs
  // Ame.md: "Corrompt les batiments adjacents de 1% par seconde" — same rate as a Zombie,
  // reusing CombatEngine's existing corruption mechanic (Corruption.md).
  val SoulCorruptionPercentPerSec: Double = 1.0
  // Ame.md: "Chaque fois qu'elle corrompt un batiment, elle soigne le Necromancien... de 1
  // PV... Si sa corruption touche plusieurs batiments a la fois, elle soigne davantage" — a
  // per-second rate (like the corruption percentage itself) *per building* the Soul is
  // currently corrupting, credited to the specific Necromancer that summoned it (see
  // Creature.summonedBy) rather than any Necromancer present in the maze.
  val SoulHealPerSecPerBuilding: Double = 1.0

  val CorruptionMaxPercent: Double = 100.0 // Corruption.md: "corrompu a 100% il disparait"

  // Victoire.md leaves Mort's "B: Corrompre ou detruire XX unites/batiments ennemis" as an
  // unfilled "XX" — POC default, deliberately small: each point requires fully corrupting
  // and destroying a whole enemy building (removing it outright and refunding its cost),
  // a far heavier swing than one Elf/Goblin plunder, so the target sits far below
  // ChaosVictoryPlunderTarget. Tunable via tournament iteration like the other targets.
  val MortVictoryCorruptionTarget: Double = 8.0

  // ── Science ──────────────────────────────────────────────────────────────
  // Five labs, one per other faction (Note sur les laboratoires.md: "un seul laboratoire de
  // chaque type" — see Placement's maxOnePerKind check), each producing Crystal and (not yet
  // implemented — see BuildingSpecs' doc) unlocking a matching leveled research line. No
  // UnitKind for Science: the vault defines none, only these five buildings.
  val LaboNaturelCostWood: Double = 5.0 // Labo Naturel.md: "cout en bois: 5"
  val LaboNaturelCostCrystal: Double = 10.0 // Labo Naturel.md: "cout en crystal: 10"
  val CrystalPerSecPerLaboNaturel: Double = 0.2 // Labo Naturel.md: "Produit 0.2 Crystal par sec"

  val LaboSombreCostShadow: Double = 5.0 // Labo Sombre.md: "cout en ombre: 5"
  val LaboSombreCostCrystal: Double = 10.0 // Labo Sombre.md: "cout en crystal: 10"
  val CrystalPerSecPerLaboSombre: Double = 0.2 // Labo Sombre.md: "Produit 0.2 Crystal par sec"

  val LaboDeRechercheCostCrystal: Double = 15.0 // Labo de Recherche.md: "cout en crystal: 15"
  val CrystalPerSecPerLaboDeRecherche: Double = 0.3 // Labo de Recherche.md: "Produit 0.3 Crystal par sec"

  val LaboDeLaLoiCostLight: Double = 5.0 // Labo de la Loi.md: "cout en lumiere: 5"
  val LaboDeLaLoiCostCrystal: Double = 10.0 // Labo de la Loi.md: "cout en crystal: 10"
  val CrystalPerSecPerLaboDeLaLoi: Double = 0.2 // Labo de la Loi.md: "Produit 0.2 Crystal par sec"

  val LaboDuChaosCostFire: Double = 5.0 // Labo du Chaos.md: "cout en feu: 5"
  val LaboDuChaosCostCrystal: Double = 10.0 // Labo du Chaos.md: "cout en crystal: 10"
  val CrystalPerSecPerLaboDuChaos: Double = 0.2 // Labo du Chaos.md: "Produit 0.2 Crystal par sec"

  // Note sur les laboratoires.md: "Chaque amelioration (recherche) dans un labo augmente sa
  // production de crystal de 75% par rapport au niveau precedent" — a lab's own research
  // level compounds ONLY its own Crystal output (CombatEngine.researchProductionMultiplier:
  // (1+this)^level), separate from that same lab's other researched *effect* (cost
  // reduction/opponent target/plunder/damage) below, which shares the same researchLevels
  // entry but is a different number (ResearchSpecs.effectAtLevel).
  val LaboCrystalBoostPerResearchLevel: Double = 0.75

  // Leveled research, one line per lab (ResearchSpecs.all pairs these with their
  // BuildingKind) — 5 levels each, level N costing 2^(N-1) times the level-1 (base) cost
  // below ("Chaque niveau coute le double du precedent", every Recherches*.md file).
  // Requires owning the matching lab — see Placement.tryResearch.
  val RecherchesNaturellesCostWood: Double = 5.0 // Recherches naturelles.md: "cout en bois: 5"
  val RecherchesNaturellesCostCrystal: Double = 10.0 // "cout en crystal: 10"
  // "Diminue le cout des batiments de: 1. 10% 2. 20% 3. 35% 4. 55% 5. 80%" — applied to
  // every OTHER building this maze places (Placement.effectiveCost), not to research costs
  // themselves (the vault only ever says "batiments", buildings).
  val NaturellesCostReductionByLevel: List[Double] = List(0.10, 0.20, 0.35, 0.55, 0.80)

  val RecherchesSombresCostShadow: Double = 5.0 // Recherches Sombres.md: "cout en ombre: 5"
  val RecherchesSombresCostCrystal: Double = 10.0 // "cout en crystal: 10"
  // "Augmente les conditions de victoire de l'adversaire de: 1. 10% 2. 25% 3. 45% 4. 75%
  // 5. 120%" — read from the *opponent's* researchLevels wherever a victory target is
  // computed (VictoryConditions.forestTarget/plunderTarget/corruptionTarget already take
  // `opponent`, so this needs no new plumbing — see their doc).
  val SombresOpponentTargetIncreaseByLevel: List[Double] = List(0.10, 0.25, 0.45, 0.75, 1.20)

  val RecherchesChaotiquesCostFire: Double = 5.0 // Recherches chaotiques.md: "cout en feu: 5"
  val RecherchesChaotiquesCostCrystal: Double = 10.0 // "cout en crystal: 10"
  // "Augmente l'efficacite du pillage de chaque unite (meme celles qui ne pillent pas
  // initialement) dans chaque ressource de: 1. 1 2. 2 3. 4 4. 7 5. 12" — a flat bonus added
  // to *every* resource for *every* arriving unit (even Paladin/Wolf/Zombie/Vampire, whose
  // CreatureSpec.plunder is otherwise empty), read from the attacking side's own
  // researchLevels (CombatEngine.tick's attackerResearchLevels param — see its doc, since
  // the attacker's research isn't visible from the defender's MazeState alone).
  val ChaotiquesPlunderBonusByLevel: List[Double] = List(1.0, 2.0, 4.0, 7.0, 12.0)

  val RecherchesLoyalesCostLight: Double = 5.0 // Recherches loyales.md: "cout en lumiere: 5"
  val RecherchesLoyalesCostCrystal: Double = 10.0 // "cout en crystal: 10"
  // "Augmente les degats infliges par les batiments de: 1. 10% 2. 20% 3. 40% 4. 70%
  // 5. 120%" — purely local: multiplies Forest-aura/Watchtower damage this maze's own
  // buildings deal (CombatEngine.applyDamageSources), read from state's own researchLevels.
  val LoyalesBuildingDamageIncreaseByLevel: List[Double] = List(0.10, 0.20, 0.40, 0.70, 1.20)

  // Recherche fondamentale.md's own base cost (20 crystal) deliberately differs from Labo
  // de Recherche's building cost (15 crystal) — the one research line whose level-1 cost
  // isn't identical to its lab's own price, unlike the other four.
  val RechercheFondamentaleCostCrystal: Double = 20.0
  // Recherche fondamentale.md's numbered list is per level of fondamentale itself: at level
  // N, the other 4 labs must all be at level (6-N) or higher — level 1 demands every other
  // lab maxed at 5, level 5 needs them only at 1+, trading fondamentale's own (doubling)
  // cost against the other labs'. See VictoryConditions.hasWonViaFondamentale.
  val FondamentaleRequiredOtherLabLevel: List[Int] = List(5, 4, 3, 2, 1)

  val MaxResearchLevel: Int = 5

  // ── Engendre (resource-generation cycle) ────────────────────────────────
  // Not from the vault's own numbers — added at the project owner's explicit request,
  // riding on Engendre.md's existing resource cycle (Bois -> Feu -> Ombre -> Cristal ->
  // Lumiere -> Bois): each building producing resource R gives a +5% boost to the SAME
  // maze's production rate of Engendre's next resource after R, once per such building
  // (see CombatEngine.engendreBoost). Applies identically to both mazes — a shared-world
  // ecosystem effect, not a competitive lever like Recherches Sombres.
  val EngendreBoostPerBuilding: Double = 0.05

  // ── Shared / meta ────────────────────────────────────────────────────────

  // POC default, not required to match each other — both mazes still get the identical
  // pair (see CLAUDE.md: symmetry is about player vs AI having the same rules, not
  // about every number being equal to every other number).
  val StartingWood: Double = 50.0
  val StartingFire: Double = 30.0

  // Light has no producer besides the Eglise itself, so without a starting amount at
  // least EgliseCostLight, the very first Eglise could never be built. Raised to 50
  // (project owner's explicit request) so the first Angel (cost 50 Light) is also
  // immediately affordable, same relationship as StartingFire to CaveCostFire.
  val StartingLight: Double = 50.0

  // Same reasoning as StartingLight: Shadow has no producer besides Tomb itself, which
  // also costs Shadow, so the very first Tomb needs a starting stock — just enough for one.
  val StartingShadow: Double = 20.0

  // Same reasoning again: every Science lab costs Crystal and only labs produce it, so
  // bootstrapping needs at least the cheapest lab's cost up front — the four 10-crystal
  // labs (Naturel/Sombre/de la Loi/du Chaos), not the pricier 15-crystal Labo de Recherche,
  // mirroring how Cave (cheaper) is buildable from StartingFire while Labyrinth (pricier)
  // isn't yet.
  val StartingCrystal: Double = 40.0

  val StartingResources: Map[Resource, Double] = Map(
    Resource.Wood -> StartingWood,
    Resource.Fire -> StartingFire,
    Resource.Light -> StartingLight,
    Resource.Shadow -> StartingShadow,
    Resource.Crystal -> StartingCrystal
  )

  // POC default: wood/fire production compounds with building count, so without a pace
  // limit the AI can tile its maze within seconds. This caps it to roughly a human's
  // tapping speed. Found by actually running the game, not decided upfront.
  val AiBuildCooldownMs: Double = 3_000.0

  // Victoire.md leaves both targets as an unfilled "XX" — POC defaults, tuned to be
  // reachable within a few minutes of play at the rates above. Each is a floor: the
  // actual target is whichever is higher between this floor and
  // VictoryMultiplierOverOpponent times the opponent's own count, so leading by a
  // fixed margin early in the match doesn't win the game outright once the opponent
  // has caught up.
  val NatureVictoryForestTarget: Int = 40
  val ChaosVictoryPlunderTarget: Double = 50.0 // Victoire.md, "R: Plunder — piller XX ressources"

  // Must double the opponent's own count (not just clear a fixed floor) to win.
  val VictoryMultiplierOverOpponent: Double = 2.0

  // POC default: tearing a building down returns half of what it cost, so reshaping a
  // maze isn't free (discourages build/destroy spam) but also isn't punitive.
  val DemolishRefundFraction: Double = 0.5
