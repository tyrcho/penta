package towerdefense.main

import org.scalajs.dom
import scala.scalajs.js
import towerdefense.domain.*
import towerdefense.domain.geometry.Vec2

// Saves/restores BattleState across page refreshes via localStorage. Best-effort: any
// failure (storage disabled, quota exceeded, malformed JSON from an older save format)
// just falls back to a fresh game rather than crashing the app.
private object Persistence:
  private val StorageKey = "towerdefense-save"

  // aiLevelIndex is a position in AiStrategy.ladder, not domain state (it picks which
  // strategy drives BattleEngine.tick's aiStrategy param) — saved alongside the battle
  // so the difficulty ladder survives a refresh too, not just the in-progress match.
  // Persisted by the strategy's *name*, not its raw index: the ladder gets reordered as
  // strategies are measured/re-ranked (see AiStrategy.ladder's history), and a raw index
  // would silently point at a different strategy after a reorder — resolved back to
  // whatever index that name holds in the *current* ladder at load time instead.
  def save(battle: BattleState, aiLevelIndex: Int): Unit =
    try
      val name = AiStrategy.ladder(aiLevelIndex)._1
      dom.window.localStorage.setItem(StorageKey, js.JSON.stringify(encodeSave(battle, name)))
    catch case _: Throwable => ()

  def load(): Option[(BattleState, Int)] =
    try
      Option(dom.window.localStorage.getItem(StorageKey)).flatMap { raw =>
        try
          val d = js.JSON.parse(raw).asInstanceOf[js.Dynamic]
          Some((decodeBattle(d), decodeAiLevelIndex(d)))
        catch case _: Throwable => None
      }
    catch case _: Throwable => None

  def clear(): Unit =
    try dom.window.localStorage.removeItem(StorageKey)
    catch case _: Throwable => ()

  // ── Encode ───────────────────────────────────────────────────────────────

  private def encodeSave(b: BattleState, aiLevelName: String): js.Object =
    js.Dynamic
      .literal(
        player = encodeMaze(b.player),
        ai = encodeMaze(b.ai),
        aiBuildCooldownMs = b.aiBuildCooldownMs,
        playerBuildCooldownMs = b.playerBuildCooldownMs,
        outcome = b.outcome.map(encodeOutcome).getOrElse(null),
        aiLevelName = aiLevelName
      )
      .asInstanceOf[js.Object]

  private def encodeMaze(m: MazeState): js.Dynamic =
    js.Dynamic.literal(
      enemies = js.Array(m.creatures.map(encodeCreature)*),
      buildings = js.Array(m.buildings.map(encodeBuilding)*),
      resources = encodeResources(m.resources),
      resourcesPlundered = m.resourcesPlundered,
      nextId = m.nextId.toDouble
    )

  private def encodeResources(r: Map[Resource, Double]): js.Dynamic =
    js.Dynamic.literal(
      wood = r.getOrElse(Resource.Wood, 0.0),
      fire = r.getOrElse(Resource.Fire, 0.0),
      light = r.getOrElse(Resource.Light, 0.0),
      shadow = r.getOrElse(Resource.Shadow, 0.0),
      crystal = r.getOrElse(Resource.Crystal, 0.0)
    )

  private def encodeCreature(c: Creature): js.Dynamic =
    js.Dynamic.literal(
      id = c.id.toDouble,
      x = c.pos.x,
      y = c.pos.y,
      hp = c.hp,
      maxHp = c.maxHp,
      speedPerMs = c.speedPerMs,
      kind = c.kind match
        case UnitKind.Elf      => "Elf"
        case UnitKind.Goblin   => "Goblin"
        case UnitKind.Minotaur => "Minotaur"
        case UnitKind.Paladin  => "Paladin"
    )

  private def encodeBuilding(b: Building): js.Dynamic =
    js.Dynamic.literal(
      id = b.id.toDouble,
      col = b.col,
      row = b.row,
      kind = b.kind.toString,
      spawnCountdownMs = b.spawnCountdownMs
    )

  private def encodeOutcome(m: MatchResult): js.Dynamic = m match
    case MatchResult.PlayerWins(reason) => js.Dynamic.literal(kind = "PlayerWins", reason = reason)
    case MatchResult.AiWins(reason)     => js.Dynamic.literal(kind = "AiWins", reason = reason)

  // ── Decode ───────────────────────────────────────────────────────────────

  private def decodeBattle(d: js.Dynamic): BattleState =
    BattleState(
      player = decodeMaze(d.player),
      ai = decodeMaze(d.ai),
      aiBuildCooldownMs = asDouble(d.aiBuildCooldownMs),
      playerBuildCooldownMs = if js.isUndefined(d.playerBuildCooldownMs) then 0.0 else asDouble(d.playerBuildCooldownMs),
      outcome = decodeOutcome(d.outcome)
    )

  // Old saves (before the difficulty ladder existed) have no aiLevelName — default to
  // the first, weakest rung rather than fail to load. A name that no longer exists in the
  // *current* ladder (removed, or an old save predating a reorder that renamed things)
  // falls back the same way, rather than silently resolving to some unrelated strategy.
  private def decodeAiLevelIndex(d: js.Dynamic): Int =
    if js.isUndefined(d.aiLevelName) then 0
    else AiStrategy.ladder.indexWhere(_._1 == d.aiLevelName.asInstanceOf[String]).max(0)

  private def decodeMaze(d: js.Dynamic): MazeState =
    val buildings =
      if js.isUndefined(d.buildings) then decodeLegacyBuildings(d) else decodeArray(d.buildings, decodeBuilding)
    MazeState(
      creatures = decodeArray(d.enemies, decodeCreature),
      buildings = buildings,
      resources = decodeResources(d),
      resourcesPlundered = asDouble(d.resourcesPlundered),
      nextId = asDouble(d.nextId).toLong
    )

  // Pre-refactor saves have flat wood/fire/light fields, not a `resources` object — light
  // has no Shadow/Crystal history to migrate (those factions don't exist yet), so a
  // legacy save simply gets 0.0 for both.
  private def decodeResources(d: js.Dynamic): Map[Resource, Double] =
    if js.isUndefined(d.resources) then
      Map(
        Resource.Wood -> asDouble(d.wood),
        Resource.Fire -> asDouble(d.fire),
        Resource.Light -> asDouble(d.light)
      )
    else
      val r = d.resources
      Map(
        Resource.Wood -> asDouble(r.wood),
        Resource.Fire -> asDouble(r.fire),
        Resource.Light -> asDouble(r.light),
        Resource.Shadow -> (if js.isUndefined(r.shadow) then 0.0 else asDouble(r.shadow)),
        Resource.Crystal -> (if js.isUndefined(r.crystal) then 0.0 else asDouble(r.crystal))
      )

  // Pre-refactor saves have 5 separate building arrays (forests/caves/labyrinths/eglises/
  // watchtowers) instead of one `buildings` array, each with its own spawn-countdown
  // field name (or none, for watchtowers — see Placement.md's Watchtower doc). Tagged
  // with the right BuildingKind and folded into one list.
  private def decodeLegacyBuildings(d: js.Dynamic): List[Building] =
    def tagged(arr: js.Dynamic, kind: BuildingKind, timerField: Option[String]): List[Building] =
      if js.isUndefined(arr) then Nil
      else
        decodeArray(
          arr,
          dd =>
            val countdown = timerField
              .map(f => dd.selectDynamic(f))
              .filterNot(js.isUndefined)
              .map(asDouble)
              .getOrElse(0.0)
            Building(asDouble(dd.id).toLong, asDouble(dd.col).toInt, asDouble(dd.row).toInt, kind, countdown)
        )
    tagged(d.forests, BuildingKind.Forest, Some("elfSpawnInMs")) ++
      tagged(d.caves, BuildingKind.Cave, Some("goblinSpawnInMs")) ++
      tagged(d.labyrinths, BuildingKind.Labyrinth, Some("minotaurSpawnInMs")) ++
      tagged(d.eglises, BuildingKind.Church, Some("paladinSpawnInMs")) ++
      tagged(d.watchtowers, BuildingKind.Watchtower, None)

  private def decodeArray[A](arr: js.Dynamic, decode: js.Dynamic => A): List[A] =
    arr.asInstanceOf[js.Array[js.Dynamic]].toList.map(decode)

  private def decodeCreature(d: js.Dynamic): Creature =
    Creature(
      id = asDouble(d.id).toLong,
      pos = Vec2(asDouble(d.x), asDouble(d.y)),
      hp = asDouble(d.hp),
      maxHp = asDouble(d.maxHp),
      speedPerMs = asDouble(d.speedPerMs),
      kind = d.kind.asInstanceOf[String] match
        case "Elf"      => UnitKind.Elf
        case "Minotaur" => UnitKind.Minotaur
        case "Paladin"  => UnitKind.Paladin
        case _          => UnitKind.Goblin
    )

  private def decodeBuilding(d: js.Dynamic): Building =
    Building(
      id = asDouble(d.id).toLong,
      col = asDouble(d.col).toInt,
      row = asDouble(d.row).toInt,
      kind = d.kind.asInstanceOf[String] match
        case "Forest"     => BuildingKind.Forest
        case "Cave"       => BuildingKind.Cave
        case "Labyrinth"  => BuildingKind.Labyrinth
        case "Eglise"     => BuildingKind.Church
        case _            => BuildingKind.Watchtower,
      spawnCountdownMs = if js.isUndefined(d.spawnCountdownMs) then 0.0 else asDouble(d.spawnCountdownMs)
    )

  private def decodeOutcome(d: js.Dynamic): Option[MatchResult] =
    if js.isUndefined(d) || d == null then None
    else
      val reason = d.reason.asInstanceOf[String]
      Some(
        if d.kind.asInstanceOf[String] == "PlayerWins" then MatchResult.PlayerWins(reason)
        else MatchResult.AiWins(reason)
      )

  private def asDouble(v: js.Dynamic): Double = v.asInstanceOf[Double]
