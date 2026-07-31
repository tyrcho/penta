package towerdefense.domain.ai.science

import towerdefense.domain.*
import towerdefense.domain.ai.SpendingPolicy
import towerdefense.domain.combat.*
import towerdefense.domain.economy.*

// Races Science's "Recherche fondamentale" victory condition (VictoryConditions.
// hasWonViaFondamentale) the same way chaos.PlunderSpending/mort.CorruptionSpending race
// Chaos/Mort's — but Science's win condition isn't "build more of one kind forever": it
// needs exactly 5 Science-lab buildings (one LaboFondamental placed per slot, each
// maxPerMaze: Some(1) once upgraded into LaboDeRecherche or one of the 4 "other" kinds —
// see BuildingSpecs.upgradeOptions), then research levels on each, not more labs. So the
// flat bonus targets LaboFondamental specifically (the only directly-buildable Science
// kind) and only while fewer than 5 Science-lab buildings exist yet; past that,
// AiStrategy.upgradeAnyAffordable and researchAnyAffordable (already wired into every
// ComposedStrategy unconditionally — see its doc) take over diversifying and leveling
// them, and this policy falls back to the same 0.25*resourceScore economy term
// PlunderSpending/CorruptionSpending use once their own racing kind is no longer scarce.
//
// A pure lab rush alone starves itself: every research level on LaboSombre/LaboDuChaos/
// LaboDeLaLoi also costs that lab's own currency (Shadow/Fire/Light respectively — see
// ResearchSpecs.all's baseCost), and nothing else in this policy would ever build a Tomb/
// Cave/Church to start producing them. Placement.canAfford lets Gold cover a zero-stock
// resource's cost once (confirmed via `sim/run maze-science maze-science 1 --log`: every
// early Labo* purchase paid entirely out of Gold, since Crystal production doesn't exist
// yet either), but once Gold is fully drained — which a Labo-only build order does fast,
// there being no cheaper candidate ever competing for it — any resource that still has zero
// ongoing production becomes permanently unaffordable (SpendingPolicy.marginFor's
// UnaffordableMarginFloor), locking LaboSombre/LaboDuChaos/LaboDeLaLoi at whatever level
// they'd already reached and LaboDuChaos/LaboDeLaLoi's own upgrade (Fire/Light cost) out
// entirely if it hadn't happened yet. So this policy also credits Tomb/Cave/Church — the
// cheapest tier-1 producer of Shadow/Fire/Light respectively — a bonus *above*
// LaboFondamental's own, but only while that resource still has no producer at all: once
// each currency is flowing, its producer's bonus turns off and Wood (already handled by the
// shared growthBonus/Grove mechanism every other policy relies on) is the only currency this
// policy doesn't also chase directly.
//
// Securing the economy still isn't enough: even with all 5 labs built and researching,
// Fondamentale's cheapest win (level 5, ~2,420 Crystal — Balance.
// RechercheFondamentaleCostCrystal x3 per level) takes far longer than a Chaos plunder race
// to 2x this maze's own plunder — confirmed via a full-ladder tournament, where
// maze-science lost almost every match to Chaos plunder specifically, never on its own
// economy stalling. Watchtower (10 dmg/sec, 2-cell range — one-shots the Elf/Goblin plunder
// raiders and kills a Minotaur in ~5s, see Balance.WatchtowerDamagePerSec/creature HP
// constants) buys the time that win needs: cheap (10 Wood/20 Light), and it doubles as a
// Light producer alongside Church rather than competing with it. Given the same bonus tier
// as the currency producers above (both are "secure the position before racing further"),
// capped at 2 — FreeformLayout already scores ranged candidates by how much of the enemy's
// path they'd cover (see LayoutPolicy's own doc), so placement needs no extra logic here,
// only making Watchtower worth building at all.
case object ScienceSpending extends SpendingPolicy:
  // Every kind a Science-lab building can ever be: LaboFondamental itself (before its first
  // upgrade) plus the 5 kinds it upgrades into (ResearchSpecs.all's keys, which already
  // includes LaboDeRecherche alongside the 4 "other" lab kinds) — a building's `kind`
  // changes in place on upgrade (same id, see Placement.tryUpgradeBuilding's doc), so
  // counting buildings whose *current* kind is in this set, regardless of upgrade stage,
  // counts each of the 5 physical lab slots exactly once.
  private val scienceLabKinds: Set[BuildingKind] =
    ResearchSpecs.all.keySet + BuildingKind.LaboFondamental

  // (producer kind, the resource it produces) for each of the 3 currencies a Science rush
  // needs besides Wood/Crystal — the cheapest tier-1 building that produces each, so this
  // policy spends the least possible on infrastructure that isn't a lab itself.
  private val producers: Seq[(BuildingKind, Resource)] =
    Seq(
      BuildingKind.Cave -> Resource.Fire,
      BuildingKind.Church -> Resource.Light,
      BuildingKind.Tomb -> Resource.Shadow
    )

  // 2, not 1: a single Watchtower dies to nothing (creatures don't target buildings), but
  // two overlapping the path gives margin against a sustained rush (Goblin every 5s,
  // Minotaur/Elf every 10s per Balance.scala's spawn-interval constants) outpacing one
  // tower's fire rate, without over-investing in defense at the expense of the labs.
  private val watchtowerDefenseCap = 2

  // Grove/Forest/Jungle: not one of this policy's own producers/lab kinds, but the plain
  // fallback (0.25*resourceScore) alone doesn't discourage building well past what
  // Science's own economy needs for Wood — see the penalty's own doc below for the real
  // transcript that surfaced this.
  private val natureKinds = Set(BuildingKind.Grove, BuildingKind.Forest, BuildingKind.Jungle)
  private val natureBuildingCap = 2

  // StasisField (Balance.CrystalPerSecPerStasisField = 0.4/sec, double LaboFondamental's
  // own 0.2/sec, and never capped by scienceLabKinds/maxPerMaze — Note sur les
  // laboratoires.md's "one lab per kind" rule applies only to the five specific Labo*
  // kinds) is this policy's second Crystal-producing avenue, but with no bonus of its own
  // it only ever competed via the plain 0.25*resourceScore fallback, which structurally
  // favors spending whichever currency has the most slack (SpendingPolicy.marginFor) —
  // exactly backwards once Crystal, not Wood/Fire/Light/Shadow, is what's actually gating
  // every Labo* upgrade AND every research level (confirmed via transcript: Fire/Shadow
  // piled up past 200-300 unspent while Crystal stayed under 30 the whole match — see
  // ScienceShared.researchFondamentaleFirst's own doc for the same transcript's research
  // side). A flat bonus, same tier as the producers/Watchtower above, keeps this policy
  // investing in Crystal throughput for as long as a candidate remains affordable, purely
  // additive (StasisField was never penalized before, so this can only raise how often it
  // wins a build slot, never remove a candidate or a slot from anything else already
  // working — unlike a cap/penalty on OTHER kinds, which a seeded `sim/run maze-plunder
  // maze-science --seed 1` A/B showed costs this policy real, hard-won defensive matches
  // against Chaos: Cave/Tomb/Church/Barracks/WarCamp/Labyrinth/BlackCastle/DeathHouse all
  // also spawn a raiding unit that lands in the OPPONENT's maze, so capping how many of
  // them this policy builds also caps how much incidental pressure Science puts on
  // whichever opponent it's facing, even though none of that pressure serves Science's own
  // win condition on purpose. Left alone here rather than chased further).
  private val crystalBonus = 1.5

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    val missingProducerBonus = producers
      .collectFirst {
        case (producerKind, res)
            if kind == producerKind && CombatEngine.productionPerSec(state, res) == 0.0 =>
          2.0
      }
      .getOrElse(0.0)
    val watchtowerCount = state.buildings.count(_.kind == BuildingKind.Watchtower)
    // A real penalty above the cap, not just "no bonus" (same fix applied to loi.LawSpending's
    // own Watchtower/lab tiers — see its doc): diagnosed via transcript that the bonus
    // alone only stopped ADDING a reason to build Watchtower, but didn't stop the plain
    // 0.25*resourceScore fallback from picking it anyway once Wood/Light were abundant —
    // one measured match built 23 Watchtowers (a wall dense enough to kill literally every
    // Chaos raider that ever spawned), instead of the intended 2, starving maze-plunder's
    // entire win condition as a side effect.
    val defenseBonus =
      if kind == BuildingKind.Watchtower then
        if watchtowerCount < watchtowerDefenseCap then 2.0 else -2.0
      else 0.0
    // Diagnosed via a real transcript (see the test's own doc): left unchecked, this
    // policy's fallback happily upgraded Grove into Forest well past 10 copies for Wood
    // income alone, incidentally building a full Nature-tier aura wall for free on top of
    // its own Watchtower defense. A flat penalty (not a hard ban — a truly desperate
    // Wood shortage can still outscore it) once the cap is cleared keeps Science's own
    // economy from silently doubling as Nature's.
    val natureCount = state.buildings.count(b => natureKinds.contains(b.kind))
    val naturePenalty =
      if natureKinds.contains(kind) && natureCount >= natureBuildingCap then -1.0 else 0.0
    val labCount = state.buildings.count(b => scienceLabKinds.contains(b.kind))
    val labBonus =
      if kind == BuildingKind.LaboFondamental && labCount < scienceLabKinds.size - 1 then 1.0
      else 0.0
    val crystalScoreBonus = if kind == BuildingKind.StasisField then crystalBonus else 0.0
    missingProducerBonus + defenseBonus + labBonus + naturePenalty + crystalScoreBonus +
      0.25 * SpendingPolicy.resourceScore(state, kind)
