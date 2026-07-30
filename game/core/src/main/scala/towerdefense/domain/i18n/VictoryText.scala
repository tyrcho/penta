package towerdefense.domain.i18n

import towerdefense.domain.*

// A localized version of VictoryConditions.winReason. That function's own return value is
// baked into MatchResult.reason and persisted verbatim (Persistence.encodeOutcome, the
// sim module's MatchLog) — always English, left untouched so save files and match logs
// never change shape. This is purely a *display*-side recomputation: it mirrors
// winReason's own precedence (forest, then plunder, then corruption, then Loi, then
// fondamentale) but reads the same public VictoryConditions numbers live, in whichever
// language the UI is currently showing, rather than replaying the frozen English sentence.
object VictoryText:
  import NumberFormat.decimal

  def reason(state: MazeState, opponent: MazeState, battle: BattleState, lang: Lang): String =
    val forestCount = VictoryConditions.forestCount(state, opponent)
    val forestTarget = VictoryConditions.forestTarget(state, opponent)
    if forestCount >= forestTarget then natureReason(forestCount, forestTarget, lang)
    else
      val plunderTarget = VictoryConditions.plunderTarget(opponent)
      if state.resourcesPlundered >= plunderTarget then
        chaosReason(state.resourcesPlundered, plunderTarget, lang)
      else
        val corruptionTarget = VictoryConditions.corruptionTarget(opponent)
        if state.buildingsCorrupted >= corruptionTarget then
          mortReason(state.buildingsCorrupted, corruptionTarget, lang)
        else
          val loiCount = VictoryConditions.loiBuildingCount(state)
          val opponentLoiCount = VictoryConditions.loiBuildingCount(opponent)
          if battle.elapsedTicks >= Balance.LoiVictoryTickThreshold && loiCount > opponentLoiCount
          then loiReason(loiCount, opponentLoiCount, lang)
          else scienceReason(state.researchLevels.getOrElse(BuildingKind.LaboDeRecherche, 0), lang)

  private def natureReason(count: Int, target: Double, lang: Lang): String = lang match
    case Lang.Fr =>
      s"Expansion Inarrêtable de la Nature : $count Forêts construites (objectif ${target.toInt})."
    case Lang.En =>
      s"Nature's unstoppable expansion: $count Forests built (target ${target.toInt})."

  private def chaosReason(plundered: Double, target: Double, lang: Lang): String = lang match
    case Lang.Fr =>
      s"Pillage du Chaos : ${plundered.toInt} ressources volées (objectif ${target.toInt})."
    case Lang.En => s"Chaos plunder: ${plundered.toInt} resources stolen (target ${target.toInt})."

  private def mortReason(corrupted: Double, target: Double, lang: Lang): String = lang match
    case Lang.Fr =>
      s"Corruption Totale de la Mort : ${corrupted.toInt} bâtiments ennemis corrompus jusqu'à disparition " +
        s"(objectif ${target.toInt})."
    case Lang.En =>
      s"Death's total corruption: ${corrupted.toInt} enemy buildings corrupted to dust (target ${target.toInt})."

  private def loiReason(count: Int, opponentCount: Int, lang: Lang): String = lang match
    case Lang.Fr =>
      s"Paix Éternelle de la Loi : $count bâtiments Loi (adversaire : $opponentCount)."
    case Lang.En => s"Loi's eternal peace: $count Loi buildings (opponent had $opponentCount)."

  private def scienceReason(level: Int, lang: Lang): String = lang match
    case Lang.Fr =>
      s"Maîtrise de la Science : Recherche fondamentale au niveau $level, chaque autre labo au niveau requis."
    case Lang.En =>
      s"Science mastery: Recherche fondamentale reached level $level, every other lab at the required depth."
