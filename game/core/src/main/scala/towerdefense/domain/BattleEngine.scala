package towerdefense.domain

// Two mazes, same rules — symmetric: either side can build any BuildingKind (see
// CLAUDE.md). Each building spawns its unit (per BuildingSpecs) into the *opponent's*
// maze — that's the whole game.
// aiBuildCooldownMs/playerBuildCooldownMs pace each side's own building rate (see
// Balance.AiBuildCooldownMs) — mirrored fields, not an AI-only concept, so a headless
// AI-vs-AI battle can throttle both sides identically to how the UI throttles `ai` today.
// outcome freezes the battle once a side has won.
case class BattleState(
    player: MazeState,
    ai: MazeState,
    aiBuildCooldownMs: Double = 0.0,
    playerBuildCooldownMs: Double = 0.0,
    outcome: Option[MatchResult] = None
)

object BattleState:
  val initial: BattleState = BattleState(MazeState.initial, MazeState.initial)

// Deaths/arrivals from both sides' CombatEngine.tick calls this BattleEngine.tick
// otherwise discards — purely observational (see MatchLog in the sim module, the one
// consumer), so it's returned alongside BattleState rather than folded into it.
case class TickEvents(
    playerDeaths: List[Death],
    aiDeaths: List[Death],
    playerArrivals: List[UnitKind],
    aiArrivals: List[UnitKind]
)

object TickEvents:
  val empty: TickEvents = TickEvents(Nil, Nil, Nil, Nil)

object BattleEngine:

  // `aiStrategy` defaults to today's behavior (the UI never passes one). `playerStrategy`
  // defaults to None so the human-controlled player slot keeps not auto-building — pass
  // Some(strategy) to drive it too, e.g. for a headless AI-vs-AI simulation.
  def tick(
      battle: BattleState,
      deltaMs: Double,
      aiStrategy: AiStrategy = LinearStrategy,
      playerStrategy: Option[AiStrategy] = None
  ): BattleState =
    tickDetailed(battle, deltaMs, aiStrategy, playerStrategy)._1

  // Same behavior as `tick`, plus the per-tick death/arrival events CombatEngine already
  // computes but `tick` alone throws away — `tick` is defined in terms of this rather than
  // duplicating the logic, so the two can never drift apart. Split out for the sim
  // module's match logger; the live browser game (GameApp.scala) keeps calling plain
  // `tick`, untouched.
  def tickDetailed(
      battle: BattleState,
      deltaMs: Double,
      aiStrategy: AiStrategy = LinearStrategy,
      playerStrategy: Option[AiStrategy] = None
  ): (BattleState, TickEvents) =
    if battle.outcome.isDefined then (battle, TickEvents.empty)
    else
      val playerResult = CombatEngine.tick(battle.player, deltaMs)
      val aiResult = CombatEngine.tick(battle.ai, deltaMs)

      val aiAfterDestroy = aiStrategy.maybeDestroy(aiResult.state, playerResult.state)
      val (aiBuilt, aiNextCooldown) = maybeActThrottled(
        aiAfterDestroy,
        playerResult.state,
        aiStrategy,
        battle.aiBuildCooldownMs - deltaMs
      )
      val (playerBuilt, playerNextCooldown) = playerStrategy match
        case Some(strategy) =>
          val playerAfterDestroy = strategy.maybeDestroy(playerResult.state, aiResult.state)
          maybeActThrottled(
            playerAfterDestroy,
            aiResult.state,
            strategy,
            battle.playerBuildCooldownMs - deltaMs
          )
        case None => (playerResult.state, 0.0)

      val aiFinal = deliverUnits(creditPlunder(aiBuilt, playerResult.stolen), playerResult.spawned)
      val playerFinal = deliverUnits(creditPlunder(playerBuilt, aiResult.stolen), aiResult.spawned)

      val next = BattleState(playerFinal, aiFinal, aiNextCooldown, playerNextCooldown)
      val events = TickEvents(
        playerDeaths = playerResult.deaths,
        aiDeaths = aiResult.deaths,
        playerArrivals = playerResult.arrivals,
        aiArrivals = aiResult.arrivals
      )
      (next.copy(outcome = VictoryConditions.evaluate(next)), events)

  // Wood/fire production compounds with building count, so without a pace limit a side
  // can tile its whole maze within seconds — capping it to at most one *attempt* per
  // Balance.AiBuildCooldownMs keeps it roughly as fast as a human tapping. The cooldown
  // resets whether or not the attempt actually did anything: a strategy's scan is a
  // Placement.tryPlace*/tryUpgradeBuilding call (with its own checks) per candidate cell,
  // and retrying that scan every tick while broke — rather than once per cooldown
  // window — is wasted work with no gameplay benefit, since resources barely move tick
  // to tick. Upgrade shares this same cooldown/slot with build (tried first) rather than
  // getting its own: upgrading compounds the economy just as building does, so pacing it
  // separately would just let a side do both every tick instead of one or the other.
  private def maybeActThrottled(
      state: MazeState,
      opponent: MazeState,
      strategy: AiStrategy,
      cooldownMs: Double
  ): (MazeState, Double) =
    if cooldownMs > 0 then (state, cooldownMs)
    else
      val upgraded = strategy.maybeUpgrade(state, opponent)
      (strategy.maybeBuild(upgraded, opponent), Balance.AiBuildCooldownMs)

  // Resources a Goblin plundered from the opponent land in the attacker's own economy
  // (Chaos.md: "arracher ses ressources"), and count toward the Chaos victory tally.
  private def creditPlunder(state: MazeState, stolen: Map[Resource, Double]): MazeState =
    val credited = stolen.foldLeft(state.resources) { case (acc, (res, amount)) =>
      acc.updated(res, acc.getOrElse(res, 0.0) + amount)
    }
    state.copy(
      resources = credited,
      resourcesPlundered = state.resourcesPlundered + stolen.values.sum
    )

  private def deliverUnits(state: MazeState, spawned: Map[UnitKind, Int]): MazeState =
    spawned.foldLeft(state) { case (s, (kind, count)) =>
      (0 until count).foldLeft(s)((s2, _) => spawnCreature(s2, kind))
    }

  private def spawnCreature(state: MazeState, kind: UnitKind): MazeState =
    val spec = CreatureSpecs.all(kind)
    val spawnPos = GridConfig.cellCenter(GridConfig.spawnCell._1, GridConfig.spawnCell._2)
    val creature = Creature(state.nextId, spawnPos, spec.maxHp, spec.maxHp, spec.speedPerMs, kind)
    state.copy(creatures = creature :: state.creatures, nextId = state.nextId + 1)
