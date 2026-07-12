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

private object AssetPaths:
  val Tower = "./assets/tower.png"
  val Enemy = "./assets/enemy.png"
  val Projectile = "./assets/projectile.png"
  val Flames = List("./assets/flame1.png", "./assets/flame2.png", "./assets/flame3.png", "./assets/flame4.png")
  val All: List[String] = List(Tower, Enemy, Projectile) ++ Flames

// Sprites are drawn facing a default direction; rotation math adds this offset
// to atan2(dy, dx) so `rotation = 0` matches how the artwork was drawn.
private val FacingUp = math.Pi / 2
private val FacingRight = 0.0

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
  val world = new Container()
  app.stage.addChild(world)
  world.addChild(drawGrid())

  val flameFrames = js.Array(AssetPaths.Flames.map(textures(_))*)
  var state = GameState.initial
  val enemySprites = mutable.Map.empty[Long, Sprite]
  val towerSprites = mutable.Map.empty[Long, Sprite]
  val projectileSprites = mutable.Map.empty[Long, Sprite]

  app.stage.eventMode = "static"
  app.stage.on("pointerdown", (e: FederatedPointerEvent) => {
    state = handleTap(app, state, e)
  })

  app.ticker.add { t =>
    state = CombatEngine.tick(state, t.deltaMS)
    syncEnemies(world, state, enemySprites, textures, flameFrames)
    syncTowers(world, state, towerSprites, textures)
    syncProjectiles(world, state, projectileSprites, textures, flameFrames)
    applyViewTransform(app, world)
    updateOverlay(state)
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

// ── Input ───────────────────────────────────────────────────────────────

private def handleTap(app: Application, state: GameState, e: FederatedPointerEvent): GameState =
  val vt = computeViewTransform(app.screen.width, app.screen.height)
  val localX = (e.globalX - vt.offsetX) / vt.scale
  val localY = (e.globalY - vt.offsetY) / vt.scale
  val col = (localX / GridConfig.cellSize).toInt
  val row = (localY / GridConfig.cellSize).toInt
  Placement.tryPlaceTower(state, col, row).getOrElse(state)

// ── Responsive scale-to-fit ────────────────────────────────────────────

private def computeViewTransform(screenW: Double, screenH: Double): ViewTransform =
  val scale = math.min(screenW / GridConfig.width, screenH / GridConfig.height)
  val offsetX = (screenW - GridConfig.width * scale) / 2
  val offsetY = (screenH - GridConfig.height * scale) / 2
  ViewTransform(scale, offsetX, offsetY)

private def applyViewTransform(app: Application, world: Container): Unit =
  val vt = computeViewTransform(app.screen.width, app.screen.height)
  world.scale.set(vt.scale)
  world.x = vt.offsetX
  world.y = vt.offsetY

// ── Sprite sync (domain GameState → Pixi sprites) ───────────────────────

private def syncEnemies(
  world: Container, state: GameState, sprites: mutable.Map[Long, Sprite],
  textures: js.Dictionary[Texture], flames: js.Array[Texture],
): Unit =
  val blocked = state.towers.map(t => (t.col, t.row)).toSet
  removeStaleWithEffect(world, sprites, state.enemies.map(_.id).toSet, flames, scale = 1.0)
  state.enemies.foreach { e =>
    val g = sprites.getOrElseUpdate(e.id, addTo(world, newSprite(textures(AssetPaths.Enemy), GridConfig.cellSize * 0.8)))
    setPos(g, e.pos)
    enemyFacingAngle(e, blocked).foreach(a => g.rotation = a)
  }

private def syncTowers(
  world: Container, state: GameState, sprites: mutable.Map[Long, Sprite], textures: js.Dictionary[Texture],
): Unit =
  state.towers.foreach { t =>
    val g = sprites.getOrElseUpdate(t.id, addTo(world, newSprite(textures(AssetPaths.Tower), GridConfig.cellSize * 0.9)))
    setPos(g, GridConfig.cellCenter(t.col, t.row))
    towerAimAngle(t, state.enemies).foreach(a => g.rotation = a)
  }

private def syncProjectiles(
  world: Container, state: GameState, sprites: mutable.Map[Long, Sprite],
  textures: js.Dictionary[Texture], flames: js.Array[Texture],
): Unit =
  removeStaleWithEffect(world, sprites, state.projectiles.map(_.id).toSet, flames, scale = 0.6)
  state.projectiles.foreach { p =>
    val isNew = !sprites.contains(p.id)
    val g = sprites.getOrElseUpdate(p.id, addTo(world, newSprite(textures(AssetPaths.Projectile), GridConfig.cellSize * 0.5)))
    setPos(g, p.pos)
    state.enemies.find(_.id == p.targetId).foreach(target => g.rotation = angleTo(p.pos, target.pos) + FacingUp)
    if isNew then spawnEffect(world, p.pos, flames, scale = 0.5)
  }

private def removeStaleWithEffect(
  world: Container, sprites: mutable.Map[Long, Sprite], liveIds: Set[Long], flames: js.Array[Texture], scale: Double,
): Unit =
  sprites.keySet.diff(liveIds).foreach { id =>
    val g = sprites(id)
    spawnEffect(world, Vec2(g.x, g.y), flames, scale)
    world.removeChild(g)
    sprites.remove(id)
  }

// ── Facing angles ────────────────────────────────────────────────────────

private def angleTo(from: Vec2, to: Vec2): Double =
  math.atan2(to.y - from.y, to.x - from.x)

private def enemyFacingAngle(e: Enemy, blocked: Set[(Int, Int)]): Option[Double] =
  val currentCell = GridConfig.cellOf(e.pos)
  if currentCell == GridConfig.goalCell then None
  else
    Pathfinding.shortestPath(currentCell, GridConfig.goalCell, blocked).collect { case path if path.size > 1 =>
      angleTo(e.pos, GridConfig.cellCenter(path(1)._1, path(1)._2)) + FacingRight
    }

private def towerAimAngle(tower: Tower, enemies: List[Enemy]): Option[Double] =
  val towerPos = GridConfig.cellCenter(tower.col, tower.row)
  enemies
    .filter(e => towerPos.distanceTo(e.pos) <= tower.rangePx)
    .minByOption(e => towerPos.distanceTo(e.pos))
    .map(e => angleTo(towerPos, e.pos) + FacingUp)

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

private def updateOverlay(state: GameState): Unit =
  document.getElementById("gold-value").textContent = state.gold.toString
  document.getElementById("lives-value").textContent = state.lives.toString
