package towerdefense.main

import org.scalajs.dom
import org.scalajs.dom.document
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import towerdefense.domain.*
import towerdefense.domain.geometry.Vec2
import towerdefense.pixi.*

private case class ViewTransform(scale: Double, offsetX: Double, offsetY: Double)

// Per-maze sprite state. One instance for the player's maze, one for the AI's.
private class MazeSprites:
  val enemies = mutable.Map.empty[Long, Sprite]
  val forets = mutable.Map.empty[Long, Sprite]
  val foretTimers = mutable.Map.empty[Long, Double]

private object AssetPaths:
  val Forest = "./assets/forest.png"
  val Enemy = "./assets/enemy.png"
  val Flames = List("./assets/flame1.png", "./assets/flame2.png", "./assets/flame3.png", "./assets/flame4.png")
  val All: List[String] = List(Forest, Enemy) ++ Flames

private val MazeGapPx = GridConfig.cellSize
private val BattleWidth = GridConfig.width * 2 + MazeGapPx
private val BattleHeight = GridConfig.height

@main def main(): Unit =
  if document.readyState == "loading" then
    document.addEventListener("DOMContentLoaded", (_: dom.Event) => setup())
  else
    setup()

def setup(): Unit =
  val app = new Application()
  val options = js.Dynamic.literal(resizeTo = dom.window, backgroundColor = 0x0f172a, antialias = true)
  app.init(options.asInstanceOf[js.Object]).toFuture
    .flatMap(_ => Assets.load(js.Array(AssetPaths.All*)).toFuture)
    .foreach(textures => onReady(app, textures))

def onReady(app: Application, textures: js.Dictionary[Texture]): Unit =
  document.getElementById("game-container").appendChild(app.canvas)
  val battleWorld = new Container()
  app.stage.addChild(battleWorld)

  val playerWorld = new Container()
  val aiWorld = new Container()
  aiWorld.x = GridConfig.width + MazeGapPx
  battleWorld.addChild(playerWorld)
  battleWorld.addChild(aiWorld)
  playerWorld.addChild(drawGrid())
  aiWorld.addChild(drawGrid())

  val flameFrames = js.Array(AssetPaths.Flames.map(textures(_))*)
  var battle = BattleState.initial
  val playerSprites = new MazeSprites
  val aiSprites = new MazeSprites

  app.stage.eventMode = "static"
  app.stage.on("pointerdown", (e: FederatedPointerEvent) => {
    battle = handleTap(app, battle, e)
  })

  app.ticker.add { t =>
    battle = BattleEngine.tick(battle, t.deltaMS)
    syncMaze(playerWorld, battle.player, playerSprites, textures, flameFrames)
    syncMaze(aiWorld, battle.ai, aiSprites, textures, flameFrames)
    applyViewTransform(app, battleWorld)
    updateOverlay(battle)
  }

// ── Static grid ─────────────────────────────────────────────────────────

private def drawGrid(): Graphics =
  val g = new Graphics()
  for
    row <- 0 until GridConfig.rows
    col <- 0 until GridConfig.cols
  do
    val color = cellColor(col, row)
    g.rect(col * GridConfig.cellSize, row * GridConfig.cellSize, GridConfig.cellSize - 1, GridConfig.cellSize - 1)
      .fill(color)
  g

private def cellColor(col: Int, row: Int): Int =
  val cell = (col, row)
  if cell == GridConfig.spawnCell then 0x22c55e
  else if cell == GridConfig.goalCell then 0xef4444
  else 0x1e2140

// ── Input (only the left/player maze is tappable) ───────────────────────

private def handleTap(app: Application, battle: BattleState, e: FederatedPointerEvent): BattleState =
  val vt = computeViewTransform(app.screen.width, app.screen.height)
  val localX = (e.globalX - vt.offsetX) / vt.scale
  val localY = (e.globalY - vt.offsetY) / vt.scale
  if localX < 0 || localX >= GridConfig.width || localY < 0 || localY >= GridConfig.height then battle
  else
    val col = (localX / GridConfig.cellSize).toInt
    val row = (localY / GridConfig.cellSize).toInt
    battle.copy(player = Placement.tryPlaceForet(battle.player, col, row).getOrElse(battle.player))

// ── Responsive scale-to-fit ────────────────────────────────────────────

private def computeViewTransform(screenW: Double, screenH: Double): ViewTransform =
  val scale = math.min(screenW / BattleWidth, screenH / BattleHeight)
  val offsetX = (screenW - BattleWidth * scale) / 2
  val offsetY = (screenH - BattleHeight * scale) / 2
  ViewTransform(scale, offsetX, offsetY)

private def applyViewTransform(app: Application, battleWorld: Container): Unit =
  val vt = computeViewTransform(app.screen.width, app.screen.height)
  battleWorld.scale.set(vt.scale)
  battleWorld.x = vt.offsetX
  battleWorld.y = vt.offsetY

// ── Sprite sync (one maze's GameState → its Pixi sprites) ──────────────

private def syncMaze(
  world: Container, maze: MazeState, sprites: MazeSprites, textures: js.Dictionary[Texture], flames: js.Array[Texture],
): Unit =
  val blocked = maze.forets.map(f => (f.col, f.row)).toSet
  removeStaleWithEffect(world, sprites.enemies, maze.enemies.map(_.id).toSet, flames)
  maze.enemies.foreach { e =>
    val g = sprites.enemies.getOrElseUpdate(e.id, addTo(world, newSprite(textures(AssetPaths.Enemy), GridConfig.cellSize * 0.8)))
    setPos(g, e.pos)
    enemyFacingAngle(e, blocked).foreach(a => g.rotation = a)
  }
  maze.forets.foreach { f =>
    val g = sprites.forets.getOrElseUpdate(f.id, addTo(world, newSprite(textures(AssetPaths.Forest), GridConfig.cellSize * 0.9)))
    setPos(g, GridConfig.cellCenter(f.col, f.row))
    if justSpawnedElfe(f, sprites.foretTimers) then spawnEffect(world, Vec2(g.x, g.y), flames, scale = 0.6)
  }

private def justSpawnedElfe(foret: Foret, timers: mutable.Map[Long, Double]): Boolean =
  val previous = timers.getOrElse(foret.id, foret.elfeSpawnInMs)
  timers(foret.id) = foret.elfeSpawnInMs
  foret.elfeSpawnInMs > previous

private def removeStaleWithEffect(
  world: Container, sprites: mutable.Map[Long, Sprite], liveIds: Set[Long], flames: js.Array[Texture],
): Unit =
  sprites.keySet.diff(liveIds).foreach { id =>
    val g = sprites(id)
    spawnEffect(world, Vec2(g.x, g.y), flames, scale = 1.0)
    world.removeChild(g)
    sprites.remove(id)
  }

// ── Facing angle ─────────────────────────────────────────────────────────

private def angleTo(from: Vec2, to: Vec2): Double =
  math.atan2(to.y - from.y, to.x - from.x)

private def enemyFacingAngle(e: Enemy, blocked: Set[(Int, Int)]): Option[Double] =
  val currentCell = GridConfig.cellOf(e.pos)
  if currentCell == GridConfig.goalCell then None
  else
    Pathfinding.shortestPath(currentCell, GridConfig.goalCell, blocked).collect { case path if path.size > 1 =>
      angleTo(e.pos, GridConfig.cellCenter(path(1)._1, path(1)._2))
    }

// ── Sprite/effect helpers ───────────────────────────────────────────────

private def newSprite(texture: Texture, size: Double): Sprite =
  val s = new Sprite(texture)
  s.anchor.set(0.5)
  s.width = size
  s.height = size
  s

private def spawnEffect(world: Container, pos: Vec2, frames: js.Array[Texture], scale: Double): Unit =
  val fx = new AnimatedSprite(frames)
  fx.anchor.set(0.5)
  fx.width = GridConfig.cellSize * scale
  fx.height = GridConfig.cellSize * scale
  fx.x = pos.x
  fx.y = pos.y
  fx.loop = false
  fx.animationSpeed = 0.3
  fx.onComplete = () => world.removeChild(fx)
  world.addChild(fx)
  fx.play()

private def addTo(world: Container, s: Sprite): Sprite =
  world.addChild(s)
  s

private def setPos(g: Sprite, pos: Vec2): Unit =
  g.x = pos.x
  g.y = pos.y

// ── HTML overlay ────────────────────────────────────────────────────────

private def updateOverlay(battle: BattleState): Unit =
  document.getElementById("player-bois").textContent = battle.player.bois.toInt.toString
  document.getElementById("player-lives").textContent = battle.player.lives.toString
  document.getElementById("ai-bois").textContent = battle.ai.bois.toInt.toString
  document.getElementById("ai-lives").textContent = battle.ai.lives.toString
