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

  def save(battle: BattleState): Unit =
    try dom.window.localStorage.setItem(StorageKey, js.JSON.stringify(encodeBattle(battle)))
    catch case _: Throwable => ()

  def load(): Option[BattleState] =
    try
      Option(dom.window.localStorage.getItem(StorageKey)).flatMap { raw =>
        try Some(decodeBattle(js.JSON.parse(raw).asInstanceOf[js.Dynamic]))
        catch case _: Throwable => None
      }
    catch case _: Throwable => None

  def clear(): Unit =
    try dom.window.localStorage.removeItem(StorageKey)
    catch case _: Throwable => ()

  // ── Encode ───────────────────────────────────────────────────────────────

  private def encodeBattle(b: BattleState): js.Object =
    js.Dynamic
      .literal(
        player = encodeMaze(b.player),
        ai = encodeMaze(b.ai),
        aiBuildCooldownMs = b.aiBuildCooldownMs,
        outcome = b.outcome.map(encodeOutcome).getOrElse(null)
      )
      .asInstanceOf[js.Object]

  private def encodeMaze(m: MazeState): js.Dynamic =
    js.Dynamic.literal(
      enemies = js.Array(m.enemies.map(encodeEnemy)*),
      forests = js.Array(m.forests.map(encodeForest)*),
      caves = js.Array(m.caves.map(encodeCave)*),
      labyrinths = js.Array(m.labyrinths.map(encodeLabyrinthe)*),
      wood = m.wood,
      fire = m.fire,
      resourcesPlundered = m.resourcesPlundered,
      nextId = m.nextId.toDouble
    )

  private def encodeEnemy(e: Enemy): js.Dynamic =
    js.Dynamic.literal(
      id = e.id.toDouble,
      x = e.pos.x,
      y = e.pos.y,
      hp = e.hp,
      maxHp = e.maxHp,
      speedPerMs = e.speedPerMs,
      kind = e.kind match
        case UnitKind.Elf      => "Elf"
        case UnitKind.Goblin   => "Goblin"
        case UnitKind.Minotaur => "Minotaur"
    )

  private def encodeForest(f: Forest): js.Dynamic =
    js.Dynamic.literal(id = f.id.toDouble, col = f.col, row = f.row, elfSpawnInMs = f.elfSpawnInMs)

  private def encodeCave(c: Cave): js.Dynamic =
    js.Dynamic.literal(
      id = c.id.toDouble,
      col = c.col,
      row = c.row,
      goblinSpawnInMs = c.goblinSpawnInMs
    )

  private def encodeLabyrinthe(l: Labyrinth): js.Dynamic =
    js.Dynamic.literal(
      id = l.id.toDouble,
      col = l.col,
      row = l.row,
      minotaurSpawnInMs = l.minotaurSpawnInMs
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
      outcome = decodeOutcome(d.outcome)
    )

  private def decodeMaze(d: js.Dynamic): MazeState =
    MazeState(
      enemies = decodeArray(d.enemies, decodeEnemy),
      forests = decodeArray(d.forests, decodeForest),
      caves = decodeArray(d.caves, decodeCave),
      labyrinths = decodeArray(d.labyrinths, decodeLabyrinthe),
      wood = asDouble(d.wood),
      fire = asDouble(d.fire),
      resourcesPlundered = asDouble(d.resourcesPlundered),
      nextId = asDouble(d.nextId).toLong
    )

  private def decodeArray[A](arr: js.Dynamic, decode: js.Dynamic => A): List[A] =
    arr.asInstanceOf[js.Array[js.Dynamic]].toList.map(decode)

  private def decodeEnemy(d: js.Dynamic): Enemy =
    Enemy(
      id = asDouble(d.id).toLong,
      pos = Vec2(asDouble(d.x), asDouble(d.y)),
      hp = asDouble(d.hp),
      maxHp = asDouble(d.maxHp),
      speedPerMs = asDouble(d.speedPerMs),
      kind = d.kind.asInstanceOf[String] match
        case "Elf"      => UnitKind.Elf
        case "Minotaur" => UnitKind.Minotaur
        case _          => UnitKind.Goblin
    )

  private def decodeForest(d: js.Dynamic): Forest =
    Forest(
      id = asDouble(d.id).toLong,
      col = asDouble(d.col).toInt,
      row = asDouble(d.row).toInt,
      elfSpawnInMs = asDouble(d.elfSpawnInMs)
    )

  private def decodeCave(d: js.Dynamic): Cave =
    Cave(
      id = asDouble(d.id).toLong,
      col = asDouble(d.col).toInt,
      row = asDouble(d.row).toInt,
      goblinSpawnInMs = asDouble(d.goblinSpawnInMs)
    )

  private def decodeLabyrinthe(d: js.Dynamic): Labyrinth =
    Labyrinth(
      id = asDouble(d.id).toLong,
      col = asDouble(d.col).toInt,
      row = asDouble(d.row).toInt,
      minotaurSpawnInMs = asDouble(d.minotaurSpawnInMs)
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
