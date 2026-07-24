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

// Deaths/arrivals/corruptions from both sides' CombatEngine.tick calls this
// BattleEngine.tick otherwise discards — purely observational (see MatchLog in the sim
// module, the one consumer), so it's returned alongside BattleState rather than folded
// into it. playerCorrupted/aiCorrupted are buildings destroyed *in* that side's own maze
// this tick (by the opponent's Zombie/Vampire) — same "named after the maze it happened
// in" convention as playerDeaths/aiDeaths.
case class TickEvents(
    playerDeaths: List[Death],
    aiDeaths: List[Death],
    playerArrivals: List[UnitKind],
    aiArrivals: List[UnitKind],
    playerCorrupted: List[Corrosion] = Nil,
    aiCorrupted: List[Corrosion] = Nil
)

object TickEvents:
  val empty: TickEvents = TickEvents(Nil, Nil, Nil, Nil, Nil, Nil)

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
      // Each side's creatures walking the *other* maze carry their owner's chaotiques
      // bonus with them — see CombatEngine.tick's attackerResearchLevels doc.
      val playerResult = CombatEngine.tick(battle.player, deltaMs, battle.ai.researchLevels)
      val aiResult = CombatEngine.tick(battle.ai, deltaMs, battle.player.researchLevels)

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

      val aiCredited = creditCorruption(creditPlunder(aiBuilt, playerResult.stolen), playerResult.corrupted)
      val playerCredited = creditCorruption(creditPlunder(playerBuilt, aiResult.stolen), aiResult.corrupted)
      val aiFinal = deliverUnits(aiCredited, playerResult.spawned)
      val playerFinal = deliverUnits(playerCredited, aiResult.spawned)

      val next = BattleState(playerFinal, aiFinal, aiNextCooldown, playerNextCooldown)
      val events = TickEvents(
        playerDeaths = playerResult.deaths,
        aiDeaths = aiResult.deaths,
        playerArrivals = playerResult.arrivals,
        aiArrivals = aiResult.arrivals,
        playerCorrupted = playerResult.corrupted,
        aiCorrupted = aiResult.corrupted
      )
      (next.copy(outcome = VictoryConditions.evaluate(next)), events)

  // Wood/fire production compounds with building count, so without a pace limit a side
  // can tile its whole maze within seconds — capping it to at most one *attempt* per
  // strategy.buildCooldownMs (Balance.AiBuildCooldownMs by default — see AiStrategy's doc,
  // and RateLimited for overriding it per-strategy) keeps it roughly as fast as a human
  // tapping. The cooldown resets whether or not the attempt actually did anything: a
  // strategy's scan is a Placement.tryPlace*/tryUpgradeBuilding call (with its own checks)
  // per candidate cell, and retrying that scan every tick while broke — rather than once
  // per cooldown window — is wasted work with no gameplay benefit, since resources barely
  // move tick to tick. Upgrade and research share this same cooldown/slot with build (tried
  // in that order) rather than getting their own: each compounds a maze's economy/defense
  // just as building does, so pacing them separately would just let a side do all three
  // every tick instead of one.
  private def maybeActThrottled(
      state: MazeState,
      opponent: MazeState,
      strategy: AiStrategy,
      cooldownMs: Double
  ): (MazeState, Double) =
    if cooldownMs > 0 then (state, cooldownMs)
    else
      val upgraded = strategy.maybeUpgrade(state, opponent)
      val researched = strategy.maybeResearch(upgraded, opponent)
      (strategy.maybeBuild(researched, opponent), strategy.buildCooldownMs)

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

  // A corrupted-to-death building's full cost (Corruption.md) lands in the corrupting
  // creature's owner's economy — the same "attacker's own state gets credited" shape as
  // creditPlunder, plus one point per building toward this side's own Mort victory tally
  // (buildingsCorrupted, symmetric to resourcesPlundered).
  private def creditCorruption(state: MazeState, corrupted: List[Corrosion]): MazeState =
    if corrupted.isEmpty then state
    else
      val credited = corrupted.foldLeft(state.resources) { case (acc, corrosion) =>
        corrosion.cost.foldLeft(acc) { case (acc2, (res, amount)) =>
          acc2.updated(res, acc2.getOrElse(res, 0.0) + amount)
        }
      }
      state.copy(resources = credited, buildingsCorrupted = state.buildingsCorrupted + corrupted.size)

  private def deliverUnits(state: MazeState, spawned: Map[UnitKind, Int]): MazeState =
    spawned.foldLeft(state) { case (s, (kind, count)) =>
      (0 until count).foldLeft(s)((s2, _) => spawnCreature(s2, kind))
    }

  private def spawnCreature(state: MazeState, kind: UnitKind): MazeState =
    val spec = CreatureSpecs.all(kind)
    val spawnPos = GridConfig.cellCenter(GridConfig.spawnCell._1, GridConfig.spawnCell._2)
    // A fresh Necromancer starts with a full Soul-summon countdown (spec.spawns' interval),
    // not 0 — it doesn't fire instantly the moment it's spawned. Inert (0.0) for every
    // other kind, which has no CreatureSpec.spawns at all.
    val initialCountdown = spec.spawns.map(_._2).getOrElse(0.0)
    val creature =
      Creature(state.nextId, spawnPos, spec.maxHp, spec.maxHp, spec.speedPerMs, kind, spawnCountdownMs = initialCountdown)
    state.copy(creatures = creature :: state.creatures, nextId = state.nextId + 1)
