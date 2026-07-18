package towerdefense.domain

enum MatchResult(val reason: String) derives CanEqual:
  case PlayerWins(override val reason: String) extends MatchResult(reason)
  case AiWins(override val reason: String) extends MatchResult(reason)

// Symmetric: either maze can win via either faction's condition from Victoire.md,
// since either side may have built Forests and/or Caves (see CLAUDE.md). Whichever
// maze meets a condition first wins — checked player-first, so a simultaneous
// double-win (same tick) favors the player, which only matters for tie-breaking.
// No "lives"/overrun fallback: that was never a vault concept, only the two
// per-faction conditions below count for now (a match can run indefinitely otherwise).
//
// Each target is relative to the opponent (see Balance.VictoryMultiplierOverOpponent):
// you must reach the floor AND double whatever the opponent has, so a lead built up
// early doesn't win the game outright once the opponent has caught back up.
object VictoryConditions:

  def evaluate(battle: BattleState): Option[MatchResult] =
    if hasWon(battle.player, battle.ai) then
      Some(MatchResult.PlayerWins(winReason(battle.player, battle.ai)))
    else if hasWon(battle.ai, battle.player) then
      Some(MatchResult.AiWins(winReason(battle.ai, battle.player)))
    else None

  private def hasWon(state: MazeState, opponent: MazeState): Boolean =
    forestCount(state, opponent) >= forestTarget(state, opponent) ||
      state.resourcesPlundered >= plunderTarget(opponent) ||
      state.buildingsCorrupted >= corruptionTarget(opponent) ||
      hasWonViaFondamentale(state)

  // Only Forest and Jungle count as "real forests" — Bosquet.md's own asset is a bush,
  // not a tree, so a Grove hasn't grown into a forest yet and shouldn't count toward
  // "the unstoppable expansion" even though it's Nature's own upgrade-chain kin.
  private val realForestKinds: Set[BuildingKind] = Set(BuildingKind.Forest, BuildingKind.Jungle)

  // Exposed (not just private) alongside forestTarget/plunderTarget below, for the same
  // reason: any external reader (the UI, the sim module's match logger) that wants to
  // display live progress needs the exact number `hasWon` itself compares against,
  // instead of re-deriving "which kinds count as a forest" and risking it drift.
  // `opponent`: Arbre Anime.md/Stonehenge.md's Tree still belongs to whoever's Stonehenge
  // made it even after it crosses into the opponent's maze to raid — a Creature alone
  // can't say whose it is, but every creature in a maze's own creature list is, by this
  // game's own invariant, always "sent by the opponent of that maze" (see Creature's doc),
  // so a Tree sitting in `opponent`'s creatures can only be `state`'s own raider.
  def forestCount(state: MazeState, opponent: MazeState): Int =
    state.buildings.count(b => realForestKinds.contains(b.kind)) +
      opponent.creatures.count(_.kind == UnitKind.Tree)

  // Exposed (not just private) so the UI can display the live target, which moves
  // with the opponent's own count — see the module doc above. `state` here is only used
  // to read the OPPONENT's raiding Trees (a Tree in `state`'s own creature list belongs to
  // `opponent`, symmetric to forestCount's own reasoning) — the target itself is about
  // what `opponent` has, same as every other *Target function below.
  def forestTarget(state: MazeState, opponent: MazeState): Double =
    withSombresBonus(
      opponent,
      math.max(Balance.NatureVictoryForestTarget.toDouble, opponentTarget(forestCount(opponent, state)))
    )

  def plunderTarget(opponent: MazeState): Double =
    withSombresBonus(opponent, math.max(Balance.ChaosVictoryPlunderTarget, opponentTarget(opponent.resourcesPlundered)))

  // Victoire.md "B: Corruption Totale" — Corrompre ou detruire XX unites/batiments
  // ennemis. Only buildings corrupted-to-destruction count (not creatures killed by an
  // unrelated Forest aura/Watchtower) — same shape as Chaos's condition tracking only
  // Elf/Goblin/Minotaur plunder, not every way resources move.
  def corruptionTarget(opponent: MazeState): Double =
    withSombresBonus(opponent, math.max(Balance.MortVictoryCorruptionTarget, opponentTarget(opponent.buildingsCorrupted)))

  private def opponentTarget(opponentCount: Double): Double =
    Balance.VictoryMultiplierOverOpponent * opponentCount

  // Recherches Sombres.md: "Augmente les conditions de victoire de l'adversaire" — inflates
  // the *whole* target (floor included, not just the doubling-over-opponent component), so
  // it still bites even when the opponent hasn't built/plundered/corrupted anything yet and
  // the floor alone is what's being compared against. Read from `opponent` (the side that
  // benefits from making this harder for `state`) since every target function above already
  // takes it.
  private def withSombresBonus(opponent: MazeState, target: Double): Double =
    val sombresLevel = opponent.researchLevels.getOrElse(BuildingKind.LaboSombre, 0)
    target * (1.0 + ResearchSpecs.all(BuildingKind.LaboSombre).effectAtLevel(sombresLevel))

  // Recherche fondamentale.md: at research level N, wins outright once the 4 other labs
  // are all at level (6-N) or higher — see Balance.FondamentaleRequiredOtherLabLevel's doc.
  // No opponent involved (unlike the other three conditions): this is purely about `state`'s
  // own lab levels, mirroring how the vault describes it as an internal milestone, not a
  // race against what the opponent has.
  def hasWonViaFondamentale(state: MazeState): Boolean =
    val fondamentaleLevel = state.researchLevels.getOrElse(BuildingKind.LaboDeRecherche, 0)
    fondamentaleLevel > 0 &&
      ResearchSpecs.otherLabKinds.forall { lab =>
        state.researchLevels.getOrElse(lab, 0) >= Balance.FondamentaleRequiredOtherLabLevel(fondamentaleLevel - 1)
      }

  // Exposed for the UI's own progress display (Science's "Recherche fondamentale" row),
  // mirroring forestCount/plunderTarget/corruptionTarget's "external reader needs the
  // exact number" reasoning.
  def fondamentaleLevel(state: MazeState): Int =
    state.researchLevels.getOrElse(BuildingKind.LaboDeRecherche, 0)

  def fondamentaleReadyLabCount(state: MazeState): Int =
    val level = fondamentaleLevel(state)
    if level <= 0 then 0
    else
      ResearchSpecs.otherLabKinds.count(lab =>
        state.researchLevels.getOrElse(lab, 0) >= Balance.FondamentaleRequiredOtherLabLevel(level - 1)
      )

  private def winReason(state: MazeState, opponent: MazeState): String =
    if forestCount(state, opponent) >= forestTarget(state, opponent) then
      s"Nature's unstoppable expansion: ${forestCount(state, opponent)} Forests built " +
        s"(target ${forestTarget(state, opponent).toInt})."
    else if state.resourcesPlundered >= plunderTarget(opponent) then
      s"Chaos plunder: ${state.resourcesPlundered.toInt} resources stolen (target ${plunderTarget(opponent).toInt})."
    else if state.buildingsCorrupted >= corruptionTarget(opponent) then
      s"Mort corruption: ${state.buildingsCorrupted.toInt} enemy buildings corrupted to dust " +
        s"(target ${corruptionTarget(opponent).toInt})."
    else
      val level = state.researchLevels.getOrElse(BuildingKind.LaboDeRecherche, 0)
      s"Science mastery: Recherche fondamentale reached level $level, every other lab at the required depth."
