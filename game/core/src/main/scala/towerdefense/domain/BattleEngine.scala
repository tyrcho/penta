package towerdefense.domain

// Two mazes, same rules, one human-controlled and one AI-controlled. Each Foret
// sends its Elfe into the *opponent's* maze, not its own — that's the whole game.
// aiBuildCooldownMs paces the AI's own building rate — see Balance.AiBuildCooldownMs.
case class BattleState(player: MazeState, ai: MazeState, aiBuildCooldownMs: Double = 0.0)

object BattleState:
  val initial: BattleState = BattleState(MazeState.initial, MazeState.initial)

object BattleEngine:

  def tick(battle: BattleState, deltaMs: Double): BattleState =
    val (playerAfterCombat, playerSpawned) = CombatEngine.tick(battle.player, deltaMs)
    val (aiAfterCombat, aiSpawned) = CombatEngine.tick(battle.ai, deltaMs)
    val (aiBuilt, nextCooldown) = maybeBuildThrottled(aiAfterCombat, battle.aiBuildCooldownMs - deltaMs)
    val aiFinal = deliverElfe(aiBuilt, playerSpawned)
    val playerFinal = deliverElfe(playerAfterCombat, aiSpawned)
    BattleState(playerFinal, aiFinal, nextCooldown)

  // Bois production compounds (more Forets → more bois/sec → more Forets), so without
  // a pace limit the AI can tile its whole maze within seconds — capping it to at most
  // one build per Balance.AiBuildCooldownMs keeps it roughly as fast as a human tapping.
  private def maybeBuildThrottled(state: MazeState, cooldownMs: Double): (MazeState, Double) =
    if cooldownMs > 0 then (state, cooldownMs)
    else
      val built = AiController.maybeBuild(state)
      if built.forets.size > state.forets.size then (built, Balance.AiBuildCooldownMs) else (built, 0.0)

  private def deliverElfe(state: MazeState, count: Int): MazeState =
    (0 until count).foldLeft(state)((s, _) => spawnElfe(s))

  private def spawnElfe(state: MazeState): MazeState =
    val spawnPos = GridConfig.cellCenter(GridConfig.spawnCell._1, GridConfig.spawnCell._2)
    val elfe = Enemy(state.nextId, spawnPos, Balance.ElfeMaxHp, Balance.ElfeMaxHp, Balance.ElfeSpeedPerMs)
    state.copy(enemies = elfe :: state.enemies, nextId = state.nextId + 1)
