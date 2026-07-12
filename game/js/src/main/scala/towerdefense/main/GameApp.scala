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

@main def main(): Unit =
  if document.readyState == "loading" then
    document.addEventListener("DOMContentLoaded", (_: dom.Event) => setup())
  else
    setup()

def setup(): Unit =
  val app = new Application()
  val options = js.Dynamic.literal(resizeTo = dom.window, backgroundColor = 0x0f172a, antialias = true)
  app.init(options.asInstanceOf[js.Object]).toFuture.foreach(_ => onReady(app))

def onReady(app: Application): Unit =
  document.getElementById("game-container").appendChild(app.canvas)
  val world = new Container()
  app.stage.addChild(world)
  world.addChild(drawGrid())

  var state = GameState.initial
  val enemySprites = mutable.Map.empty[Long, Graphics]
  val towerSprites = mutable.Map.empty[Long, Graphics]
  val projectileSprites = mutable.Map.empty[Long, Graphics]

  app.stage.eventMode = "static"
  app.stage.on("pointerdown", (e: FederatedPointerEvent) => {
    state = handleTap(app, state, e)
  })

  app.ticker.add { t =>
    state = CombatEngine.tick(state, t.deltaMS)
    syncEnemies(world, state, enemySprites)
    syncTowers(world, state, towerSprites)
    syncProjectiles(world, state, projectileSprites)
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

// ── Sprite sync (domain GameState → Pixi Graphics) ─────────────────────

private def syncEnemies(world: Container, state: GameState, sprites: mutable.Map[Long, Graphics]): Unit =
  removeStale(world, sprites, state.enemies.map(_.id).toSet)
  state.enemies.foreach { e =>
    val g = sprites.getOrElseUpdate(e.id, addTo(world, new Graphics().circle(0, 0, 12).fill(0xf97316)))
    setPos(g, e.pos)
  }

private def syncTowers(world: Container, state: GameState, sprites: mutable.Map[Long, Graphics]): Unit =
  state.towers.foreach { t =>
    val g = sprites.getOrElseUpdate(t.id, addTo(world, new Graphics().circle(0, 0, 16).fill(0x60a5fa)))
    setPos(g, GridConfig.cellCenter(t.col, t.row))
  }

private def syncProjectiles(world: Container, state: GameState, sprites: mutable.Map[Long, Graphics]): Unit =
  removeStale(world, sprites, state.projectiles.map(_.id).toSet)
  state.projectiles.foreach { p =>
    val g = sprites.getOrElseUpdate(p.id, addTo(world, new Graphics().circle(0, 0, 4).fill(0xfacc15)))
    setPos(g, p.pos)
  }

private def removeStale(world: Container, sprites: mutable.Map[Long, Graphics], liveIds: Set[Long]): Unit =
  sprites.keySet.diff(liveIds).foreach { id =>
    world.removeChild(sprites(id))
    sprites.remove(id)
  }

private def addTo(world: Container, g: Graphics): Graphics =
  world.addChild(g)
  g

private def setPos(g: Graphics, pos: Vec2): Unit =
  g.x = pos.x
  g.y = pos.y

// ── HTML overlay ────────────────────────────────────────────────────────

private def updateOverlay(state: GameState): Unit =
  document.getElementById("gold-value").textContent = state.gold.toString
  document.getElementById("lives-value").textContent = state.lives.toString
