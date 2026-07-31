package towerdefense.domain.ai

import towerdefense.domain.*
import towerdefense.domain.combat.*
import towerdefense.domain.economy.*
import towerdefense.domain.grid.*

// The "which building kind" half of an AiStrategy — how resources get spent, independent
// of where the result goes (that's LayoutPolicy's job). ComposedStrategy blends a
// SpendingPolicy's score with a LayoutPolicy's score per (kind, cell) candidate.
trait SpendingPolicy:
  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double

object SpendingPolicy:

  // Average affordability margin left over the currencies this kind consumes, penalized
  // per currency that has no active producer on the board yet (see marginFor) — moved
  // from CompositeStrategy.resourceScore's raw component, now growth-aware.
  private[domain] def rawMargin(state: MazeState, kind: BuildingKind): Double =
    val margins = kind.cost.map { case (res, amount) => marginFor(state, res, amount) }
    margins.sum / margins.size

  // Plain affordability margin ((available - amount) / available) once this resource has
  // some ongoing production — spending it down is safe, it'll refill. Without any active
  // producer, the spend is a one-way trip: nothing on the board will ever bring this
  // currency back up, so a spend that consumes a big fraction of the remaining stock (a
  // small fraction still available afterward) risks locking every future building that
  // needs this resource out permanently. The penalty term (amount / available, i.e. the
  // fraction of the current stock this spend consumes) grows toward 1.0 as the spend
  // approaches the entire stock, and is 0 when the spend is negligible relative to what's
  // on hand.
  // A large-but-finite floor for "this resource's stock is exactly 0 and the cost isn't" —
  // every maze's own starting stock now IS exactly 0 for every named resource (Balance.
  // StartingResources), so this used to be a rare late-game edge case and is now the
  // literal opening position. The true (available - amount) / available limit as
  // available -> 0 is -Infinity, but an actual Infinity here is just as poisonous as the
  // 0.0/0.0 = NaN case right below guards against: ComposedStrategy multiplies this by
  // spendingWeight (its own doc), and a weight-grid search (Simulator.searchWeights/
  // tournamentStandings) tries spendingWeight = 0.0 too, where 0.0 * -Infinity = NaN,
  // silently emptying the "tied" candidate set (NaN == NaN is false) and crashing
  // random.nextInt(0). Comfortably below any real (finite) margin this formula produces.
  private val UnaffordableMarginFloor: Double = -1_000_000.0

  private def marginFor(state: MazeState, res: Resource, amount: Double): Double =
    // A zero-cost entry (e.g. Balance.CaveCostWood = 0.0) never penalizes the margin,
    // regardless of how depleted that resource's stock is — this also sidesteps a
    // 0.0/0.0 = NaN divide when the stock has independently dropped to exactly zero too.
    if amount == 0.0 then 1.0
    else
      val available = state.resources.getOrElse(res, 0.0)
      if available <= 0.0 then
        // Zero stock doesn't mean actually unaffordable — Placement.canAfford (what
        // really gates whether a build succeeds) lets Gold cover any shortfall 1-for-1,
        // but this margin used to ignore that entirely, so a candidate Gold could easily
        // pay for (e.g. Grove's Wood cost, from the real starting position of 0 Wood/100
        // Gold) scored the same UnaffordableMarginFloor as one nothing on the board could
        // ever afford — a real lockout: Cave (whose Wood cost happens to be exactly 0.0,
        // sidestepping this branch entirely) was the only building that ever competed,
        // confirmed via a full tournament run's per-match transcripts (see AiStrategy.
        // ladder's doc). Scored the same as a zero-cost term (1.0, "not what's
        // constraining this candidate") rather than penalized — growthBonus, not this
        // term, is what should keep preferring a candidate that'd actually establish
        // production over one Gold merely bankrolls forever.
        if state.resources.getOrElse(Resource.Gold, 0.0) >= amount then 1.0
        else UnaffordableMarginFloor
      else
        val plainMargin = (available - amount) / available
        val rate = CombatEngine.productionPerSec(state, res)
        if rate > 0.0 then plainMargin else plainMargin - amount / available

  // rawMargin's penalty alone isn't enough to avoid a lockout: it discourages *spending*
  // a no-production resource, but a kind that costs that same resource without producing
  // it (e.g. Watchtower, which shares Grove's Wood cost) still often out-scores the kind
  // that would actually fix the shortage (Grove, Wood's only producer) — Watchtower's
  // margin gets pulled up by averaging in an abundant currency (Light) it also spends,
  // while Grove's lone Wood term has nothing to average against and sinks with it. This
  // flat bonus — one point per currently-unproduced resource `kind` would start producing
  // — directly credits *fixing* the shortage, not just avoiding worsening it, so Grove
  // wins that comparison once Wood production has actually hit zero.
  private def growthBonus(state: MazeState, kind: BuildingKind): Double =
    kind.produces.keySet.count(res => CombatEngine.productionPerSec(state, res) == 0.0).toDouble

  // Divides rawMargin (plus the growth bonus) by one plus how many of that kind are
  // already built — see CompositeStrategy's original doc: without this, a pure
  // resource-margin strategy locks onto whichever single kind has the best margin and
  // never reconsiders, since margins barely move build to build. `1 + count` keeps the
  // first building of any kind scored at its full raw margin, only discounting repeats.
  private[domain] def resourceScore(state: MazeState, kind: BuildingKind): Double =
    val existingCount = state.buildings.count(_.kind == kind)
    (rawMargin(state, kind) + growthBonus(state, kind)) / (1.0 + existingCount)

  // Mirrors whichever of Nature (Grove/Forest/Jungle), Chaos (Cave/Labyrinth), or Mort
  // (Tomb/BlackCastle) the opponent invests in more — moved verbatim from
  // CompositeStrategy.counterScore, now with Mort added alongside the original two once
  // its own victory condition (buildingsCorrupted) existed to counter. Loi (Church/
  // Watchtower) never scores here: it feeds no VictoryConditions target (still a genuinely
  // unwired gap — see BuildingSpecs' doc). Science (the five Labo* kinds) is also excluded,
  // even though Recherche fondamentale IS a real victory condition now: countering it means
  // researching your OWN labs' levels, not building more copies of a maxPerMaze: Some(1)
  // building, so it doesn't fit this "build more of what they're building" heuristic.
  private val natureBuildingKinds: Set[BuildingKind] =
    Set(BuildingKind.Grove, BuildingKind.Forest, BuildingKind.Jungle, BuildingKind.Stonehenge)
  private val chaosBuildingKinds: Set[BuildingKind] =
    Set(BuildingKind.Cave, BuildingKind.Labyrinth, BuildingKind.DragonsLair, BuildingKind.WarCamp)
  private val mortBuildingKinds: Set[BuildingKind] =
    Set(BuildingKind.Tomb, BuildingKind.BlackCastle, BuildingKind.DeathHouse)

  private[domain] def counterScore(opponent: MazeState, kind: BuildingKind): Double =
    val natureCount = opponent.buildings.count(b => natureBuildingKinds.contains(b.kind))
    val chaosCount = opponent.buildings.count(b => chaosBuildingKinds.contains(b.kind))
    val mortCount = opponent.buildings.count(b => mortBuildingKinds.contains(b.kind))
    val leaderCount = natureCount.max(chaosCount).max(mortCount)
    val ownFactionCount =
      if natureBuildingKinds.contains(kind) then Some(natureCount)
      else if chaosBuildingKinds.contains(kind) then Some(chaosCount)
      else if mortBuildingKinds.contains(kind) then Some(mortCount)
      else None
    if ownFactionCount.contains(leaderCount) then 1.0 else 0.0

// Blends resourceScore and counterScore — subsumes today's resource-only (1,0),
// counter-only (0,1), and balanced (1,1) spending halves.
case class WeightedSpending(resourceWeight: Double, counterWeight: Double) extends SpendingPolicy:
  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    resourceWeight * SpendingPolicy.resourceScore(state, kind) +
      counterWeight * SpendingPolicy.counterScore(opponent, kind)

// Always favors Chaos (every one of its 4 buildings — Cave/Labyrinth/WarCamp/DragonsLair)
// regardless of the opponent's own faction mix, racing the Chaos/plunder victory condition
// instead of reacting to what the opponent builds the way WeightedSpending's counter term
// does. Ties among Chaos kinds (and among non-Chaos fallbacks, if none is affordable)
// break by margin. WarCamp's Orc (see Balance.OrcMaxHp's doc) is what lets this policy
// actually survive a Watchtower-defended opponent (e.g. maze-science) long enough to
// complete its own plunder race; DragonsLair's Dragon (rock-paper-scissors tuning pass —
// missing here until audited against every Chaos building) is the other half: its 40 Gold
// plunder alone very nearly secures the whole victory (target 50) in a single successful
// raid — but a flat, equal bonus for every Chaos kind alone never actually got it built in
// practice (confirmed via `sim/run maze-plunder maze-science --log`): Cave's near-zero
// cost always won the fallback margin tie-break, so a real match just spammed Cave
// forever. DragonsLair gets its own priority tier instead (mirroring ScienceSpending's
// tiered bonuses), guaranteeing at least one gets built once some Chaos economy already
// exists — capped at 1 (its whole value is one raid's magnitude, not volume), and gated on
// already owning a Cave/WarCamp: an UNgated priority bonus was confirmed (via the same
// transcript) to spend this policy's entire starting Gold on a turn-1 DragonsLair — a
// building with zero economic return — the exact Gold-starvation lockout
// ScienceSpending's own producer bonuses exist to avoid.
//
// A Watchtower-defense tier (mirroring ScienceSpending's) was tried and reverted here —
// it overcorrected Law-vs-Chaos into a total Chaos sweep without fixing Chaos-vs-Science
// at all, net-negative across the 5-leg cycle. Left as a note for a future, more careful
// pass rather than re-attempted blindly.
case object PlunderSpending extends SpendingPolicy:
  private val chaosKinds =
    Set(BuildingKind.Cave, BuildingKind.Labyrinth, BuildingKind.WarCamp, BuildingKind.DragonsLair)
  private val chaosEconomyKinds = Set(BuildingKind.Cave, BuildingKind.WarCamp)
  private val dragonsLairCap = 1

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    val chaosBonus = if chaosKinds.contains(kind) then 1.0 else 0.0
    val hasEconomy = state.buildings.exists(b => chaosEconomyKinds.contains(b.kind))
    val dragonsLairCount = state.buildings.count(_.kind == BuildingKind.DragonsLair)
    val dragonsLairBonus =
      if kind == BuildingKind.DragonsLair && dragonsLairCount < dragonsLairCap && hasEconomy then
        3.0
      else 0.0
    chaosBonus + dragonsLairBonus + 0.25 * SpendingPolicy.resourceScore(state, kind)

// Always favors Mort (Tomb/BlackCastle) regardless of the opponent's own faction mix,
// racing the Mort/corruption victory condition the same way PlunderSpending races Chaos's.
case object CorruptionSpending extends SpendingPolicy:
  private val mortKinds = Set(BuildingKind.Tomb, BuildingKind.BlackCastle, BuildingKind.DeathHouse)

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    (if mortKinds.contains(kind) then 1.0 else 0.0) + 0.25 * SpendingPolicy.resourceScore(
      state,
      kind
    )

// Races Science's "Recherche fondamentale" victory condition (VictoryConditions.
// hasWonViaFondamentale) the same way PlunderSpending/CorruptionSpending race Chaos/Mort's
// — but Science's win condition isn't "build more of one kind forever": it needs exactly 5
// Science-lab buildings (one LaboFondamental placed per slot, each maxPerMaze: Some(1) once
// upgraded into LaboDeRecherche or one of the 4 "other" kinds — see BuildingSpecs.
// upgradeOptions), then research levels on each, not more labs. So the flat bonus targets
// LaboFondamental specifically (the only directly-buildable Science kind) and only while
// fewer than 5 Science-lab buildings exist yet; past that, AiStrategy.upgradeAnyAffordable
// and researchAnyAffordable (already wired into every ComposedStrategy unconditionally —
// see its doc) take over diversifying and leveling them, and this policy falls back to the
// same 0.25*resourceScore economy term PlunderSpending/CorruptionSpending use once their own
// racing kind is no longer scarce.
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

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    val missingProducerBonus = producers
      .collectFirst {
        case (producerKind, res)
            if kind == producerKind && CombatEngine.productionPerSec(state, res) == 0.0 =>
          2.0
      }
      .getOrElse(0.0)
    val watchtowerCount = state.buildings.count(_.kind == BuildingKind.Watchtower)
    // A real penalty above the cap, not just "no bonus" (same fix applied to LawSpending's
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
    missingProducerBonus + defenseBonus + labBonus + naturePenalty + 0.25 * SpendingPolicy
      .resourceScore(state, kind)

// Races Loi's "Paix Éternelle" victory condition (VictoryConditions.hasWonViaLoi) the same
// way PlunderSpending/CorruptionSpending race Chaos/Mort's: build as many of the four Loi
// kinds (Church/Watchtower/Angel/Barracks) as possible before Balance.
// LoiVictoryTickThreshold elapses, since it's a pure building-count comparison at that
// instant, not a resource total. Watchtower doubles as this strategy's own defense (10
// dmg/sec, 2-cell range — same mechanic ScienceSpending borrows via a separate bonus
// tier, see its own doc) at zero extra cost here, since Watchtower is already one of the
// four counted kinds.
//
// Also penalizes incidental Grove/Forest/Jungle past a small cap (same mechanism
// ScienceSpending uses for itself) — diagnosed via a real transcript: none of the 4 Loi
// kinds produce Wood, so this policy's fallback kept building Grove for Wood income, pure
// waste for a strategy whose victory condition is a building-COUNT tally that Grove never
// contributes to. Capped, not banned outright, since Law still needs *some* ongoing Wood
// income to keep affording more Loi buildings.
case object LawSpending extends SpendingPolicy:
  private val loiKinds =
    Set(BuildingKind.Church, BuildingKind.Watchtower, BuildingKind.Angel, BuildingKind.Barracks)
  private val natureKinds = Set(BuildingKind.Grove, BuildingKind.Forest, BuildingKind.Jungle)
  private val natureBuildingCap = 2
  // Extra priority on top of loiBonus (Watchtower already counts toward Law's own win
  // condition just like Church/Angel/Barracks, so this only reorders WHICH of the 4 gets
  // built first, never hurts the building-count race itself) — diagnosed via transcript
  // (`sim/run maze-law maze-plunder --log`): with every loiKind scoring an equal flat
  // bonus, margin-based tie-breaking let Law drift toward spamming Barracks (cheapest)
  // for raw volume, leaving only 1-2 Watchtowers up against a sustained Goblin/Orc/
  // Minotaur rush — Chaos's plunder target (a small, fixed number) kept getting hit well
  // before Balance.LoiVictoryTickThreshold, regardless of how large Law's own building
  // lead would eventually have been.
  //
  // No cap at all (unlike Nature/Science's own 2-4 defense tiers): raised from an initial
  // 6 after confirming via transcript that Chaos's raiders scale UP over the whole match
  // (WarCamp/Labyrinth/DragonsLair compound its own economy), so Law's kill-throughput
  // needs to keep growing right alongside it, not plateau early and drift back to
  // Church/Angel/Barracks once "enough" Watchtowers exist. Watchtower still costs real
  // Light (Barracks/Church stay the only producers of it), so this doesn't starve Law's
  // own income outright — it just means Watchtower wins the fallback tie-break too, not
  // only the capped-priority tier.
  private val watchtowerDefenseCap = Int.MaxValue
  // Recherches loyales.md: LaboDeLaLoi's own research speeds up every Loi-faction damage
  // dealer's attack INTERVAL (CombatEngine's intervalFor — level 5 gives a 2.2x rate, not
  // just 2.2x damage), which multiplies Watchtower's kill throughput at a FIXED building
  // count instead of needing an ever-larger number of towers — the real bottleneck found
  // via transcript: each Watchtower only ever kills one nearest creature per second, and
  // Chaos's own raider volume keeps scaling with its economy. Added at the project owner's
  // explicit direction ("consider adding angels and lab upgrades to the law strat").
  // laboFondamentalCap=1: Law only needs the one Fondamental -> LaboDeLaLoi chain, not a
  // full Science-style lab spread — see BuildingSpecs.upgradeOptions' LaboDeLaLoi-first
  // reordering for the "which of the 5 it upgrades into" half of this. Gated on already
  // having 2+ Watchtowers up so this doesn't derail the early defense rush itself.
  //
  // The cap itself is enforced by AiStrategy's own CountCapLayout wrapping (a hard veto),
  // not a SpendingPolicy penalty here — diagnosed via transcript that even a -1_000 flat
  // penalty on a second LaboFondamental still lost the tie-break during a genuine Wood
  // drought (none of Law's own 4 kinds produce Wood): every OTHER candidate hit
  // SpendingPolicy.marginFor's own UnaffordableMarginFloor (-1_000_000), so a
  // merely-very-negative LaboFondamental still won by comparison. See CountCapLayout's own
  // doc for why a layout-level veto (immune to every other candidate's score) was the fix.
  // This policy still needs its own labCount check for the BONUS below the cap — the veto
  // only stops going OVER it, it doesn't make Law want to build one in the first place.
  private val laboFondamentalCap = 1
  private val allLabKinds = ResearchSpecs.all.keySet + BuildingKind.LaboFondamental

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    val loiBonus = if loiKinds.contains(kind) then 1.0 else 0.0
    val watchtowerCount = state.buildings.count(_.kind == BuildingKind.Watchtower)
    val defenseBonus =
      if kind == BuildingKind.Watchtower && watchtowerCount < watchtowerDefenseCap then 2.0 else 0.0
    val labCount = state.buildings.count(b => allLabKinds.contains(b.kind))
    val labBonus =
      if kind == BuildingKind.LaboFondamental && watchtowerCount >= 2 && labCount < laboFondamentalCap
      then 1.5
      else 0.0
    val natureCount = state.buildings.count(b => natureKinds.contains(b.kind))
    val naturePenalty =
      if natureKinds.contains(kind) && natureCount >= natureBuildingCap then -1.0 else 0.0
    loiBonus + defenseBonus + labBonus + naturePenalty + 0.25 * SpendingPolicy.resourceScore(
      state,
      kind
    )

// Races Nature's own forest-count victory condition (VictoryConditions.forestCount) the
// same way PlunderSpending/CorruptionSpending/LawSpending race their factions' —
// GrovePriority (below) only ever chases Grove itself via a very different flat-1000/
// rawMargin shape, so it isn't a fair "pure Nature rush" comparable to the other three;
// this targets the whole Grove/Forest/Jungle upgrade chain plus Stonehenge instead.
//
// Also builds a little defense (Watchtower, same capped tier ScienceSpending/LawSpending
// use for themselves) — diagnosed via a real transcript: unlike Chaos/Science/Law's win
// conditions (cumulative stats that don't undo), a Forest corrupted to death is REMOVED
// from Nature's own forestCount, so Mort's corruption doesn't just race its own target,
// it actively reverses Nature's progress. Forest/Jungle's own passive corruption
// self-heal (0.1-0.5%/sec, well below a Zombie/Vampire's 1.15-3.0%/sec corruption rate)
// never stops a sustained assault alone.
case object NatureSpending extends SpendingPolicy:
  private val natureKinds =
    Set(BuildingKind.Grove, BuildingKind.Forest, BuildingKind.Jungle, BuildingKind.Stonehenge)
  // Raised from 2 (rock-paper-scissors tuning pass): 2 Watchtowers let far too many
  // Zombie/Vampire raiders reach a Grove/Forest cluster before dying, and every raider
  // that gets adjacent starts corroding a building whether or not the heal cluster can
  // out-pace it (see HealClusterLayout's own doc) — killing more raiders BEFORE they reach
  // a building is a more robust lever than trying to out-heal an open-ended number of them
  // once they're already there.
  // Raised again from 4 (project owner's explicit direction — "nature strat should also
  // build some defenses"): diagnosed via transcript that once the old cap of 4 was
  // reached, Nature dumped everything into cheap Grove spam (its own forestCount race) and
  // let a Science opponent's incidental Cave-spawned Goblins/Minotaurs accumulate enough
  // stolen Gold to win via Chaos's plunder condition first — an unrelated confound winning
  // before either side's own intended race did.
  private val watchtowerDefenseCap = 8

  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    val natureBonus = if natureKinds.contains(kind) then 1.0 else 0.0
    val watchtowerCount = state.buildings.count(_.kind == BuildingKind.Watchtower)
    val defenseBonus =
      if kind == BuildingKind.Watchtower && watchtowerCount < watchtowerDefenseCap then 2.0 else 0.0
    natureBonus + defenseBonus + 0.25 * SpendingPolicy.resourceScore(state, kind)

// Prefers Grove outright (Nature's only directly-buildable tier — see BuildingSpecs.
// buildableDirectly) whenever it's a candidate at all, falling back to plain affordability
// margin among the rest otherwise — moved from TemplateStrategy's Grove-first/
// margin-fallback behavior. The 1000.0 constant is arbitrary but must dominate every
// realistic rawMargin value (bounded well below that) so a LayoutPolicy's own score is
// still what breaks ties among several affordable Grove cells.
case object GrovePriority extends SpendingPolicy:
  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    if kind == BuildingKind.Grove then 1000.0 else SpendingPolicy.rawMargin(state, kind)

// Fixed try-order, ignoring both affordability margin and the opponent — same idea as
// LinearStrategy's buildOrder, made pluggable. Earlier entries score higher; a kind absent
// from `order` scores below every listed kind.
case class FixedOrderSpending(order: Seq[BuildingKind]) extends SpendingPolicy:
  def score(state: MazeState, opponent: MazeState, kind: BuildingKind): Double =
    val idx = order.indexOf(kind)
    if idx < 0 then -1.0 else (order.size - idx).toDouble
