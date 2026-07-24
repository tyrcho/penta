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
      buildingsCorrupted = m.buildingsCorrupted,
      researchLevels = encodeResearchLevels(m.researchLevels),
      nextId = m.nextId.toDouble
    )

  // One field per Science lab kind, keyed by its own toString — mirrors encodeResources'
  // shape (a flat JS object, not an array of pairs) since ResearchSpecs.orderedLabs is a
  // small, fixed set, same as Resource.values.
  private def encodeResearchLevels(levels: Map[BuildingKind, Int]): js.Dynamic =
    val obj = js.Dynamic.literal()
    ResearchSpecs.orderedLabs.foreach(lab => obj.updateDynamic(lab.toString)(levels.getOrElse(lab, 0)))
    obj

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
        case UnitKind.Elf         => "Elf"
        case UnitKind.Goblin      => "Goblin"
        case UnitKind.Minotaur    => "Minotaur"
        case UnitKind.Paladin     => "Paladin"
        case UnitKind.Wolf        => "Wolf"
        case UnitKind.Zombie      => "Zombie"
        case UnitKind.Vampire     => "Vampire"
        case UnitKind.Necromancer => "Necromancer"
        case UnitKind.Soul        => "Soul"
        case UnitKind.Tree        => "Tree",
      // Only Necromancer/Tree ever have a nonzero countdown/frozenMs (see CreatureSpec.
      // spawns/spawnFreezeMs), and only a Soul or a cloned Tree has a summonedBy — inert
      // (0.0/null/1.0) for every other kind, same "cheap to carry" choice as Building's
      // own spawnCountdownMs.
      spawnCountdownMs = c.spawnCountdownMs,
      summonedBy = c.summonedBy.map(_.toDouble).getOrElse(null).asInstanceOf[js.Any],
      frozenMs = c.frozenMs,
      sizeFraction = c.sizeFraction
    )

  private def encodeBuilding(b: Building): js.Dynamic =
    js.Dynamic.literal(
      id = b.id.toDouble,
      col = b.col,
      row = b.row,
      kind = b.kind.toString,
      spawnCountdownMs = b.spawnCountdownMs,
      corruptionPercent = b.corruptionPercent,
      flashMs = b.flashMs,
      damageCooldownMs = b.damageCooldownMs,
      constructionRemainingMs = b.constructionRemainingMs
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
      // Pre-Mort saves have no buildingsCorrupted field — default to 0.0, same fallback
      // shape as Shadow/Crystal's decodeResources migration above.
      buildingsCorrupted = if js.isUndefined(d.buildingsCorrupted) then 0.0 else asDouble(d.buildingsCorrupted),
      researchLevels = decodeResearchLevels(d.researchLevels),
      nextId = asDouble(d.nextId).toLong
    )

  // Pre-Science saves have no researchLevels field at all — every lab defaults to 0
  // (unresearched), same fallback shape as buildingsCorrupted above.
  private def decodeResearchLevels(d: js.Dynamic): Map[BuildingKind, Int] =
    if js.isUndefined(d) then Map.empty
    else
      ResearchSpecs.orderedLabs
        .flatMap { lab =>
          val level = d.selectDynamic(lab.toString)
          if js.isUndefined(level) then None else Some(lab -> asDouble(level).toInt)
        }
        .toMap

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
    // Pre-upgrade-chain saves' single-tier "forests" is today's Grove (the base tier).
    tagged(d.forests, BuildingKind.Grove, Some("elfSpawnInMs")) ++
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
        case "Elf"         => UnitKind.Elf
        case "Minotaur"    => UnitKind.Minotaur
        case "Paladin"     => UnitKind.Paladin
        case "Wolf"        => UnitKind.Wolf
        case "Zombie"      => UnitKind.Zombie
        case "Vampire"     => UnitKind.Vampire
        case "Necromancer" => UnitKind.Necromancer
        case "Soul"        => UnitKind.Soul
        case "Tree"        => UnitKind.Tree
        case _             => UnitKind.Goblin,
      // Pre-Necromancer saves have none of these fields — default to 0.0/None, same
      // fallback shape as buildingsCorrupted/researchLevels' migration elsewhere in this file.
      spawnCountdownMs = if js.isUndefined(d.spawnCountdownMs) then 0.0 else asDouble(d.spawnCountdownMs),
      summonedBy =
        if js.isUndefined(d.summonedBy) || d.summonedBy == null then None
        else Some(asDouble(d.summonedBy).toLong),
      frozenMs = if js.isUndefined(d.frozenMs) then 0.0 else asDouble(d.frozenMs),
      // Pre-Stonehenge saves have no sizeFraction at all — default to 1.0 (full size),
      // same fallback shape as spawnCountdownMs/frozenMs above.
      sizeFraction = if js.isUndefined(d.sizeFraction) then 1.0 else asDouble(d.sizeFraction)
    )

  private def decodeBuilding(d: js.Dynamic): Building =
    Building(
      id = asDouble(d.id).toLong,
      col = asDouble(d.col).toInt,
      row = asDouble(d.row).toInt,
      kind = d.kind.asInstanceOf[String] match
        case "Grove"           => BuildingKind.Grove
        case "Forest"          => BuildingKind.Forest
        case "Jungle"          => BuildingKind.Jungle
        case "Cave"            => BuildingKind.Cave
        case "Labyrinth"       => BuildingKind.Labyrinth
        case "Eglise"          => BuildingKind.Church
        case "Church"          => BuildingKind.Church
        case "Tomb"            => BuildingKind.Tomb
        case "BlackCastle"     => BuildingKind.BlackCastle
        case "DeathHouse"      => BuildingKind.DeathHouse
        case "LaboFondamental" => BuildingKind.LaboFondamental
        case "LaboNaturel"     => BuildingKind.LaboNaturel
        case "LaboSombre"      => BuildingKind.LaboSombre
        case "LaboDeRecherche" => BuildingKind.LaboDeRecherche
        case "LaboDeLaLoi"     => BuildingKind.LaboDeLaLoi
        case "LaboDuChaos"     => BuildingKind.LaboDuChaos
        case "Angel"           => BuildingKind.Angel
        case "Stonehenge"      => BuildingKind.Stonehenge
        case "PassingGate"     => BuildingKind.PassingGate
        case _                 => BuildingKind.Watchtower,
      spawnCountdownMs = if js.isUndefined(d.spawnCountdownMs) then 0.0 else asDouble(d.spawnCountdownMs),
      corruptionPercent = if js.isUndefined(d.corruptionPercent) then 0.0 else asDouble(d.corruptionPercent),
      // Pre-PassingGate saves have no flashMs at all — default to 0.0 (no flash), same
      // fallback shape as sizeFraction above.
      flashMs = if js.isUndefined(d.flashMs) then 0.0 else asDouble(d.flashMs),
      // Pre-once-per-second-damage saves have no damageCooldownMs at all — default to a
      // full DamageTickIntervalMs, matching the case class's own default for a building
      // that hasn't started counting down toward its next hit yet.
      damageCooldownMs =
        if js.isUndefined(d.damageCooldownMs) then Balance.DamageTickIntervalMs else asDouble(d.damageCooldownMs),
      // Pre-construction-time saves have no constructionRemainingMs at all — default to
      // 0.0 (already built), same fallback shape as flashMs above: a building saved before
      // this mechanic existed was always instantly functional, so it stays that way on load.
      constructionRemainingMs =
        if js.isUndefined(d.constructionRemainingMs) then 0.0 else asDouble(d.constructionRemainingMs)
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
