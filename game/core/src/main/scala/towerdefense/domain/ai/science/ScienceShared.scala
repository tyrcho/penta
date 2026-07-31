package towerdefense.domain.ai.science

import towerdefense.domain.{BuildingKind, MazeState}
import towerdefense.domain.ai.AiStrategy
import towerdefense.domain.grid.Placement

// Wraps any AiStrategy to override only maybeResearch (delegating everything else —
// maybeBuild/maybeDestroy/maybeUpgrade/buildCooldownMs/name — to `inner` unchanged), same
// delegation shape as AiStrategy.RateLimited (shared, not ours to edit) wrapping only
// buildCooldownMs. A plain `object MazeScience extends ComposedStrategy(...):` with an
// overridden def wouldn't survive `reseed`: ComposedStrategy.reseed is inherited and
// implemented as `copy(random = ...)`, and a case class's generated `copy` always returns
// a plain value of the case class's own type — so reseeding would silently drop back to
// undecorated ComposedStrategy (losing the Fondamentale-first override) the instant a
// caller passes a seed (Simulator.runMatch/runLoggedMatch's own `seed` param — used by
// every rock-paper-scissors/tournament measurement, not just ad-hoc `sim/run --seed`
// calls). Wrapping instead of subclassing keeps the override intact through reseed, since
// reseed here delegates to `inner.reseed` and re-wraps the result in the same type.
private[science] case class FondamentaleFirstResearch(inner: AiStrategy) extends AiStrategy:
  val name: String = inner.name
  def maybeBuild(state: MazeState, opponent: MazeState): MazeState =
    inner.maybeBuild(state, opponent)
  override def maybeDestroy(state: MazeState, opponent: MazeState): MazeState =
    inner.maybeDestroy(state, opponent)
  override def maybeUpgrade(state: MazeState, opponent: MazeState): MazeState =
    inner.maybeUpgrade(state, opponent)
  override def maybeResearch(state: MazeState, opponent: MazeState): MazeState =
    ScienceShared.researchFondamentaleFirst(state)
  override def buildCooldownMs: Double = inner.buildCooldownMs
  override def reseed(seed: Long): AiStrategy = copy(inner = inner.reseed(seed))

// Values/logic shared by every Science rush strategy (MazeScience/CombScience).
private[science] object ScienceShared:
  // Shortened from an initial [Cave, Church, Tomb] (rock-paper-scissors tuning pass): a
  // 3-item forced opening made Science's own economy dramatically slower to get going
  // than every other faction's 1-2 item opening, losing almost every match on pure
  // opening speed before its labs/defense ever mattered — confirmed via `rockPaperScissors
  // 9` (Science lost 0-9 to both Chaos and Nature). One forced item (Cave, cheapest
  // producer) is now enough to kick off Fire income; ScienceSpending's own
  // missingProducerBonus tier already prioritizes Church/Tomb dynamically right after,
  // just without forcing a specific (possibly not cost-optimal) order for them.
  val opening: Seq[BuildingKind] = Seq(BuildingKind.Cave)

  // Recherche fondamentale's own level (the LaboDeRecherche building's researchLevels
  // entry) is the ONE lever that lowers the bar for all 4 "other" labs at once — Balance.
  // FondamentaleRequiredOtherLabLevel is a strictly decreasing list (level 1 demands the
  // other four at 5, level 5 needs them only at 1), and the other four already sit at a
  // free level 1 the instant their own LaboFondamental upgrades into them (BuildingKind's
  // own doc: "The upgrade itself grants the chosen kind an instant, free research level
  // 1"). So racing Fondamentale's own level is structurally what shortens this race, not
  // spreading research evenly across all 5.
  //
  // AiStrategy.researchAnyAffordable (shared, not ours to edit) instead tries
  // ResearchSpecs.orderedLabs in a FIXED order that puts LaboDeRecherche third, behind
  // LaboNaturel/LaboSombre, and stops at the first lab whose NEXT level happens to be
  // affordable that tick — so whenever Naturel or Sombre's next level (half the Crystal
  // price of LaboDeRecherche's own: 10 base vs 20) is affordable, Fondamentale's own level
  // never even gets tried that tick, regardless of whether ITS next level would also be
  // affordable right then.
  //
  // Confirmed via transcript (`sim/run maze-science maze-nature --seed 1 --log`):
  // LaboDeRecherche stalled at level 2 for the match's entire final stretch (tick 1861
  // onward, of a 2281-tick match) while LaboSombre/LaboDeLaLoi/LaboDuChaos kept climbing
  // to level 3 — a wasted spend, since level-2 Fondamentale demands the other four at
  // level 4 (Balance.FondamentaleRequiredOtherLabLevel(1)), a bar level 3 doesn't clear
  // either way. The match timed out (lost to Nature's own forest count) without
  // VictoryConditions.hasWonViaFondamentale ever firing, even though more total research
  // got spent on the four "other" labs combined than on Fondamentale itself.
  //
  // Trying LaboDeRecherche first, every tick, before falling back to the shared order for
  // the other four, fixes exactly this starvation without touching AiStrategy/
  // ResearchSpecs.orderedLabs (shared files, not ours to edit) — it doesn't change how the
  // other four get leveled when Fondamentale's own next level isn't affordable, only
  // guarantees Fondamentale is never skipped purely because a cheaper lab happened to be
  // checked first. Measured neutral (not negative) on the defensive Chaos matchup — those
  // matches resolve (~1000-1100 ticks either way) long before research pace ever matters —
  // via a seeded `sim/run maze-plunder maze-science 12 3500 --seed 1` A/B (1/12 either
  // way), so this is pure upside for the favored Nature matchup with no measured downside
  // elsewhere.
  def researchFondamentaleFirst(state: MazeState): MazeState =
    Placement
      .tryResearch(state, BuildingKind.LaboDeRecherche)
      .toOption
      .getOrElse(AiStrategy.researchAnyAffordable(state))
