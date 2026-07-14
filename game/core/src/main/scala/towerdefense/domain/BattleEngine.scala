package towerdefense.domain

// Two mazes, same rules, one human-controlled and one AI-controlled — symmetric: either
// side can build a Forest, a Cave, or a Labyrinthe (see CLAUDE.md). Each Forest sends its
// Elf, each Cave its Goblin, and each Labyrinthe its Minotaur, into the *opponent's*
// maze — that's the whole game.
// aiBuildCooldownMs paces the AI's own building rate (see Balance.AiBuildCooldownMs).
// outcome freezes the battle once a side has won.
case class BattleState(
    player: MazeState,
    ai: MazeState,
    aiBuildCooldownMs: Double = 0.0,
    outcome: Option[MatchResult] = None
)

object BattleState:
  val initial: BattleState = BattleState(MazeState.initial, MazeState.initial)

object BattleEngine:

  def tick(battle: BattleState, deltaMs: Double): BattleState =
    if battle.outcome.isDefined then battle
    else
      val playerResult = CombatEngine.tick(battle.player, deltaMs)
      val aiResult = CombatEngine.tick(battle.ai, deltaMs)
      val (aiBuilt, nextCooldown) =
        maybeBuildThrottled(aiResult.state, battle.aiBuildCooldownMs - deltaMs)

      val aiFinal = deliverUnits(
        creditPlunder(aiBuilt, playerResult.stolenWood, playerResult.stolenFire),
        playerResult.spawnedElf,
        playerResult.spawnedGoblin,
        playerResult.spawnedMinotaur
      )
      val playerFinal = deliverUnits(
        creditPlunder(playerResult.state, aiResult.stolenWood, aiResult.stolenFire),
        aiResult.spawnedElf,
        aiResult.spawnedGoblin,
        aiResult.spawnedMinotaur
      )

      val next = BattleState(playerFinal, aiFinal, nextCooldown)
      next.copy(outcome = VictoryConditions.evaluate(next))

  // Wood/fire production compounds with building count, so without a pace limit the AI
  // can tile its whole maze within seconds — capping it to at most one build per
  // Balance.AiBuildCooldownMs keeps it roughly as fast as a human tapping.
  private def maybeBuildThrottled(state: MazeState, cooldownMs: Double): (MazeState, Double) =
    if cooldownMs > 0 then (state, cooldownMs)
    else
      val built = AiController.maybeBuild(state)
      val didBuild = built.forests.size > state.forests.size ||
        built.caves.size > state.caves.size ||
        built.labyrinths.size > state.labyrinths.size
      if didBuild then (built, Balance.AiBuildCooldownMs) else (built, 0.0)

  // Resources a Goblin plundered from the opponent land in the attacker's own economy
  // (Chaos.md: "arracher ses ressources"), and count toward the Chaos victory tally.
  private def creditPlunder(state: MazeState, wood: Double, fire: Double): MazeState =
    state.copy(
      wood = state.wood + wood,
      fire = state.fire + fire,
      resourcesPlundered = state.resourcesPlundered + wood + fire
    )

  private def deliverUnits(
      state: MazeState,
      elfCount: Int,
      goblinCount: Int,
      minotaurCount: Int
  ): MazeState =
    val withElf = (0 until elfCount).foldLeft(state)((s, _) =>
      spawnUnit(s, UnitKind.Elf, Balance.ElfMaxHp, Balance.ElfSpeedPerMs)
    )
    val withGoblin = (0 until goblinCount).foldLeft(withElf)((s, _) =>
      spawnUnit(s, UnitKind.Goblin, Balance.GoblinMaxHp, Balance.GoblinSpeedPerMs)
    )
    (0 until minotaurCount).foldLeft(withGoblin)((s, _) =>
      spawnUnit(s, UnitKind.Minotaur, Balance.MinotaurMaxHp, Balance.MinotaurSpeedPerMs)
    )

  private def spawnUnit(
      state: MazeState,
      kind: UnitKind,
      maxHp: Double,
      speedPerMs: Double
  ): MazeState =
    val spawnPos = GridConfig.cellCenter(GridConfig.spawnCell._1, GridConfig.spawnCell._2)
    val unit = Enemy(state.nextId, spawnPos, maxHp, maxHp, speedPerMs, kind)
    state.copy(enemies = unit :: state.enemies, nextId = state.nextId + 1)
