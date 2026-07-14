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

private enum BuildingChoice derives CanEqual:
  case Forest, Cave, Labyrinthe, Eglise

private enum HoverKind derives CanEqual:
  case EnemyH, ForestH, CaveH, LabyrintheH, EgliseH

private case class HoverTarget(isPlayer: Boolean, kind: HoverKind, id: Long)

// Pause/speed-up/slow-down: a multiplier applied to each tick's real elapsed time
// before it reaches BattleEngine — the simulation itself has no notion of speed.
private class GameSpeed:
  private val Min = 0.25
  private val Max = 4.0
  var paused: Boolean = false
  var multiplier: Double = 1.0

  def effectiveDeltaMs(realDeltaMs: Double): Double =
    if paused then 0.0 else realDeltaMs * multiplier
  def slower(): Unit = multiplier = (multiplier / 2).max(Min)
  def faster(): Unit = multiplier = (multiplier * 2).min(Max)
  def togglePause(): Unit = paused = !paused

// Per-maze sprite state. One instance for the player's maze, one for the AI's.
private class MazeSprites:
  val enemies = mutable.Map.empty[Long, Container]
  // Which of the goblin's 4 direction frame sets is currently applied — avoids
  // resetting the walk-cycle animation every tick, only when the facing changes.
  val goblinFacing = mutable.Map.empty[Long, String]
  val forests = mutable.Map.empty[Long, Sprite]
  val forestTimers = mutable.Map.empty[Long, Double]
  val caves = mutable.Map.empty[Long, Sprite]
  val caveTimers = mutable.Map.empty[Long, Double]
  val labyrinths = mutable.Map.empty[Long, Sprite]
  val labyrintheTimers = mutable.Map.empty[Long, Double]
  val eglises = mutable.Map.empty[Long, Sprite]
  val egliseTimers = mutable.Map.empty[Long, Double]

private object AssetPaths:
  val Forest = "./assets/forest.png"
  val CaveRock = "./assets/cave.png"
  val LabyrintheIcon = "./assets/labyrinthe.png"
  val EgliseIcon = "./assets/eglise.png"
  val Elf = "./assets/enemy.png"
  val Minotaur = "./assets/minotaur.png"
  val Paladin = "./assets/paladin.png"
  val Flames =
    List("./assets/flame1.png", "./assets/flame2.png", "./assets/flame3.png", "./assets/flame4.png")
  val GoblinFrameCount = 10
  val GoblinDirections = List("front", "back", "left", "right")
  val GoblinFrames: Map[String, List[String]] =
    GoblinDirections
      .map(d => d -> (0 until GoblinFrameCount).map(i => f"./assets/goblin/$d-walk-$i%02d.png").toList)
      .toMap
  val All: List[String] =
    List(Forest, CaveRock, LabyrintheIcon, EgliseIcon, Elf, Minotaur, Paladin) ++
      GoblinFrames.values.flatten ++ Flames

private val CaveTint = 0xff7a45 // warm/fiery recolor for an otherwise cool-gray rock tile

private val MazeGapPx = GridConfig.cellSize

// Side-by-side on wide screens, stacked (player maze above the AI's) on narrow/portrait
// ones — e.g. a phone on WiFi (see game/CLAUDE.md: both mazes always keep equal billing,
// so "stacked" here is purely a layout choice, not a capability difference).
private case class Layout(portrait: Boolean, battleWidth: Double, battleHeight: Double)

private def currentLayout(screenW: Double, screenH: Double): Layout =
  if screenH > screenW then
    Layout(portrait = true, GridConfig.width, GridConfig.height * 2 + MazeGapPx)
  else Layout(portrait = false, GridConfig.width * 2 + MazeGapPx, GridConfig.height)

@main def main(): Unit =
  if document.readyState == "loading" then
    document.addEventListener("DOMContentLoaded", (_: dom.Event) => setup())
  else setup()

def setup(): Unit =
  val app = new Application()
  val options =
    js.Dynamic.literal(resizeTo = dom.window, backgroundColor = 0x0f172a, antialias = true)
  app
    .init(options.asInstanceOf[js.Object])
    .toFuture
    .flatMap(_ => Assets.load(js.Array(AssetPaths.All*)).toFuture)
    .foreach(textures => onReady(app, textures))

def onReady(app: Application, textures: js.Dictionary[Texture]): Unit =
  document.getElementById("game-container").appendChild(app.canvas)
  val battleWorld = new Container()
  app.stage.addChild(battleWorld)

  val playerWorld = new Container()
  val aiWorld = new Container()
  battleWorld.addChild(playerWorld)
  battleWorld.addChild(aiWorld)
  playerWorld.addChild(drawGrid())
  aiWorld.addChild(drawGrid())

  val flameFrames = js.Array(AssetPaths.Flames.map(textures(_))*)
  val goblinFrames: Map[String, js.Array[Texture]] =
    AssetPaths.GoblinFrames.map { case (dir, paths) => dir -> js.Array(paths.map(textures(_))*) }
  var battle = Persistence.load().getOrElse(BattleState.initial)
  var selectedBuilding: BuildingChoice = BuildingChoice.Forest
  var hovered: Option[HoverTarget] = None
  var hoveringButton = false
  var speed = new GameSpeed
  var msSinceLastSave = 0.0
  val playerSprites = new MazeSprites
  val aiSprites = new MazeSprites

  def resetGame(): Unit =
    clearSprites(playerWorld, playerSprites)
    clearSprites(aiWorld, aiSprites)
    battle = BattleState.initial
    speed.paused = false
    updateSpeedLabel(speed)
    hovered = None
    Persistence.clear()

  wireBuildingButtons(
    choice => selectedBuilding = choice,
    choice => canAfford(battle.player, choice),
    active => hoveringButton = active
  )
  wireSpeedControls(speed)
  wireNewGameButton(() => resetGame())

  app.stage.eventMode = "static"
  app.stage.on(
    "pointerdown",
    (e: FederatedPointerEvent) => {
      battle = handleTap(app, battle, e, selectedBuilding)
    }
  )
  app.stage.on(
    "pointermove",
    (e: FederatedPointerEvent) => {
      val canvasRect = app.canvas.getBoundingClientRect()
      positionTooltip(canvasRect.left + e.globalX, canvasRect.top + e.globalY)
    }
  )

  app.ticker.add { t =>
    battle = BattleEngine.tick(battle, speed.effectiveDeltaMs(t.deltaMS))
    syncMaze(
      playerWorld,
      battle.player,
      playerSprites,
      textures,
      goblinFrames,
      flameFrames,
      isPlayer = true,
      h => hovered = h
    )
    syncMaze(
      aiWorld,
      battle.ai,
      aiSprites,
      textures,
      goblinFrames,
      flameFrames,
      isPlayer = false,
      h => hovered = h
    )
    applyViewTransform(app, battleWorld, aiWorld)
    updateOverlay(battle)
    updateBuildButtonsAffordability(battle.player)
    hovered = updateTooltip(hovered, battle, hoveringButton)
    updateNewGameButtonVisibility(battle.outcome.isDefined || speed.paused)
    msSinceLastSave += t.deltaMS
    if msSinceLastSave >= 1000.0 then
      Persistence.save(battle)
      msSinceLastSave = 0.0
  }

// ── Static grid ─────────────────────────────────────────────────────────

private def drawGrid(): Graphics =
  val g = new Graphics()
  for
    row <- 0 until GridConfig.rows
    col <- 0 until GridConfig.cols
  do
    val color = cellColor(col, row)
    g.rect(
      col * GridConfig.cellSize,
      row * GridConfig.cellSize,
      GridConfig.cellSize - 1,
      GridConfig.cellSize - 1
    ).fill(color)
  g

private def cellColor(col: Int, row: Int): Int =
  val cell = (col, row)
  if cell == GridConfig.spawnCell then 0x22c55e
  else if cell == GridConfig.goalCell then 0xef4444
  else 0x1e2140

// ── Input (only the left/player maze is tappable; both buildings are available —
// symmetric game, see CLAUDE.md — so the player picks one via the toolbar buttons) ──

private val ForestTooltip =
  s"Forêt — cost ${Balance.ForestCostWood.toInt} wood. " +
    s"+${Balance.WoodPerSecPerForest} wood/s, ${Balance.AuraDamagePerSec.toInt} dmg/s to adjacent enemies, " +
    s"spawns an Elf every ${(Balance.ElfSpawnIntervalMs / 1000).toInt}s"

private val CaveTooltip =
  s"Cave — cost ${Balance.CaveCostWood.toInt} wood + ${Balance.CaveCostFire.toInt} fire. " +
    s"+${Balance.FirePerSecPerCave.toInt} fire/s, spawns a Goblin every ${(Balance.GoblinSpawnIntervalMs / 1000).toInt}s"

private val LabyrintheTooltip =
  s"Labyrinthe — cost ${Balance.LabyrintheCostWood.toInt} wood + ${Balance.LabyrintheCostFire.toInt} fire. " +
    s"Spawns a Minotaur every ${(Balance.MinotaurSpawnIntervalMs / 1000).toInt}s, " +
    s"which plunders ${Balance.MinotaurPlunderPerUnit.toInt} wood + ${Balance.MinotaurPlunderPerUnit.toInt} fire on arrival"

private val EgliseTooltip =
  s"Eglise — cost ${Balance.EgliseCostWood.toInt} wood + ${Balance.EgliseCostLight.toInt} light. " +
    s"+${Balance.LightPerSecPerEglise.toInt} light/s, spawns a Paladin every ${(Balance.PaladinSpawnIntervalMs / 1000).toInt}s, " +
    s"which shields adjacent allies from ${Balance.PaladinAuraDamageReductionPerSec.toInt} dmg/s (doesn't plunder itself)"

// canAfford is read at click time (not baked into the closure) since the player's
// wood/fire change every tick — see updateBuildButtonsAffordability for the matching
// visual (disabled) state, kept in sync from the same Balance costs.
private def wireBuildingButtons(
    onSelect: BuildingChoice => Unit,
    canAfford: BuildingChoice => Boolean,
    setHoveringButton: Boolean => Unit
): Unit =
  val forestBtn = document.getElementById("build-forest")
  val caveBtn = document.getElementById("build-cave")
  val labyrintheBtn = document.getElementById("build-labyrinthe")
  val egliseBtn = document.getElementById("build-eglise")
  val buttons = List(forestBtn, caveBtn, labyrintheBtn, egliseBtn)
  wireButtonTooltip(forestBtn, ForestTooltip, setHoveringButton)
  wireButtonTooltip(caveBtn, CaveTooltip, setHoveringButton)
  wireButtonTooltip(labyrintheBtn, LabyrintheTooltip, setHoveringButton)
  wireButtonTooltip(egliseBtn, EgliseTooltip, setHoveringButton)
  wireBuildClick(forestBtn, BuildingChoice.Forest, buttons, canAfford, onSelect)
  wireBuildClick(caveBtn, BuildingChoice.Cave, buttons, canAfford, onSelect)
  wireBuildClick(labyrintheBtn, BuildingChoice.Labyrinthe, buttons, canAfford, onSelect)
  wireBuildClick(egliseBtn, BuildingChoice.Eglise, buttons, canAfford, onSelect)

private def wireBuildClick(
    btn: dom.Element,
    choice: BuildingChoice,
    allButtons: List[dom.Element],
    canAfford: BuildingChoice => Boolean,
    onSelect: BuildingChoice => Unit
): Unit =
  btn.addEventListener(
    "click",
    (_: dom.Event) => if canAfford(choice) then selectBuilding(choice, btn, allButtons, onSelect)
  )

// Eglise spends a different second currency (light, not fire) than Cave/Labyrinthe,
// so this is a direct match rather than a generic (wood, fire) tuple.
private def canAfford(maze: MazeState, choice: BuildingChoice): Boolean = choice match
  case BuildingChoice.Forest => maze.wood >= Balance.ForestCostWood
  case BuildingChoice.Cave   => maze.wood >= Balance.CaveCostWood && maze.fire >= Balance.CaveCostFire
  case BuildingChoice.Labyrinthe =>
    maze.wood >= Balance.LabyrintheCostWood && maze.fire >= Balance.LabyrintheCostFire
  case BuildingChoice.Eglise =>
    maze.wood >= Balance.EgliseCostWood && maze.light >= Balance.EgliseCostLight

// Reflects afford-ability in real time, since the player's resources move every tick
// even without any click — see wireBuildClick for the matching input gate.
private def updateBuildButtonsAffordability(maze: MazeState): Unit =
  updateButtonDisabled("build-forest", canAfford(maze, BuildingChoice.Forest))
  updateButtonDisabled("build-cave", canAfford(maze, BuildingChoice.Cave))
  updateButtonDisabled("build-labyrinthe", canAfford(maze, BuildingChoice.Labyrinthe))
  updateButtonDisabled("build-eglise", canAfford(maze, BuildingChoice.Eglise))

private def updateButtonDisabled(id: String, affordable: Boolean): Unit =
  val btn = document.getElementById(id)
  if affordable then btn.classList.remove("disabled") else btn.classList.add("disabled")

// #tooltip is position:fixed, so it takes plain viewport coordinates regardless of
// where in the DOM the hovered element lives — buttons in the header (clientX/Y are
// already viewport-relative) and in-canvas sprites (see the pointermove handler in
// onReady, which converts canvas-local globalX/Y to viewport coordinates) both feed
// the same positionTooltip.
// setHoveringButton tells the ticker's updateTooltip (which otherwise clears the
// shared #tooltip every frame when no *canvas* sprite is hovered) to leave it alone
// while a button tooltip is showing — the two hover sources share one DOM element.
private def wireButtonTooltip(
    btn: dom.Element,
    text: String,
    setHoveringButton: Boolean => Unit
): Unit =
  btn.addEventListener(
    "mouseenter",
    (_: dom.Event) => { showButtonTooltip(text); setHoveringButton(true) }
  )
  btn.addEventListener(
    "mousemove",
    (e: dom.Event) => {
      val m = e.asInstanceOf[dom.MouseEvent]
      positionTooltip(m.clientX, m.clientY)
    }
  )
  btn.addEventListener(
    "mouseleave",
    (_: dom.Event) => {
      document.getElementById("tooltip").classList.remove("visible")
      setHoveringButton(false)
    }
  )

private def showButtonTooltip(text: String): Unit =
  val tooltip = document.getElementById("tooltip")
  tooltip.textContent = text
  tooltip.classList.add("visible")

private def selectBuilding(
    choice: BuildingChoice,
    active: dom.Element,
    all: List[dom.Element],
    onSelect: BuildingChoice => Unit
): Unit =
  onSelect(choice)
  all.foreach(_.classList.remove("selected"))
  active.classList.add("selected")

// ── Speed controls (pause / slow down / speed up) ───────────────────────

private def wireSpeedControls(speed: GameSpeed): Unit =
  document
    .getElementById("slow-btn")
    .addEventListener("click", (_: dom.Event) => { speed.slower(); updateSpeedLabel(speed) })
  document
    .getElementById("fast-btn")
    .addEventListener("click", (_: dom.Event) => { speed.faster(); updateSpeedLabel(speed) })
  document
    .getElementById("pause-btn")
    .addEventListener("click", (_: dom.Event) => { speed.togglePause(); updateSpeedLabel(speed) })

private def updateSpeedLabel(speed: GameSpeed): Unit =
  document.getElementById("speed-label").textContent =
    if speed.paused then "Paused" else s"${formatMultiplier(speed.multiplier)}x"
  document.getElementById("pause-btn").textContent = if speed.paused then "Play" else "Pause"

private def formatMultiplier(m: Double): String =
  if m == m.toInt then m.toInt.toString else m.toString

// ── New game (visible while paused or once the match has ended) ────────

private def wireNewGameButton(onNewGame: () => Unit): Unit =
  document.getElementById("new-game-btn").addEventListener("click", (_: dom.Event) => onNewGame())

private def updateNewGameButtonVisibility(visible: Boolean): Unit =
  val btn = document.getElementById("new-game-btn")
  if visible then btn.classList.add("visible") else btn.classList.remove("visible")

private def clearSprites(world: Container, sprites: MazeSprites): Unit =
  (sprites.enemies.values ++ sprites.forests.values ++ sprites.caves.values ++
    sprites.labyrinths.values ++ sprites.eglises.values).foreach(world.removeChild)
  sprites.enemies.clear()
  sprites.goblinFacing.clear()
  sprites.forests.clear()
  sprites.forestTimers.clear()
  sprites.caves.clear()
  sprites.caveTimers.clear()
  sprites.labyrinths.clear()
  sprites.labyrintheTimers.clear()
  sprites.eglises.clear()
  sprites.egliseTimers.clear()

private def handleTap(
    app: Application,
    battle: BattleState,
    e: FederatedPointerEvent,
    choice: BuildingChoice
): BattleState =
  if battle.outcome.isDefined then battle
  else
    val layout = currentLayout(app.screen.width, app.screen.height)
    val vt = computeViewTransform(app.screen.width, app.screen.height, layout)
    val localX = (e.globalX - vt.offsetX) / vt.scale
    val localY = (e.globalY - vt.offsetY) / vt.scale
    if localX < 0 || localX >= GridConfig.width || localY < 0 || localY >= GridConfig.height then
      battle
    else
      val col = (localX / GridConfig.cellSize).toInt
      val row = (localY / GridConfig.cellSize).toInt
      val tryPlace = choice match
        case BuildingChoice.Forest     => Placement.tryPlaceForest
        case BuildingChoice.Cave       => Placement.tryPlaceCave
        case BuildingChoice.Labyrinthe => Placement.tryPlaceLabyrinthe
        case BuildingChoice.Eglise     => Placement.tryPlaceEglise
      battle.copy(player = tryPlace(battle.player, col, row).getOrElse(battle.player))

// ── Responsive scale-to-fit ────────────────────────────────────────────

private def computeViewTransform(screenW: Double, screenH: Double, layout: Layout): ViewTransform =
  val scale = math.min(screenW / layout.battleWidth, screenH / layout.battleHeight)
  val offsetX = (screenW - layout.battleWidth * scale) / 2
  val offsetY = (screenH - layout.battleHeight * scale) / 2
  ViewTransform(scale, offsetX, offsetY)

private def applyViewTransform(app: Application, battleWorld: Container, aiWorld: Container): Unit =
  val layout = currentLayout(app.screen.width, app.screen.height)
  aiWorld.x = if layout.portrait then 0 else GridConfig.width + MazeGapPx
  aiWorld.y = if layout.portrait then GridConfig.height + MazeGapPx else 0
  val vt = computeViewTransform(app.screen.width, app.screen.height, layout)
  battleWorld.scale.set(vt.scale)
  battleWorld.x = vt.offsetX
  battleWorld.y = vt.offsetY

// ── Sprite sync (one maze's GameState → its Pixi sprites) ──────────────

private def syncMaze(
    world: Container,
    maze: MazeState,
    sprites: MazeSprites,
    textures: js.Dictionary[Texture],
    goblinFrames: Map[String, js.Array[Texture]],
    flames: js.Array[Texture],
    isPlayer: Boolean,
    setHovered: Option[HoverTarget] => Unit
): Unit =
  val blocked = maze.buildingCells
  syncEnemies(world, maze, sprites, textures, goblinFrames, flames, blocked, isPlayer, setHovered)
  syncForests(world, maze, sprites, textures, flames, isPlayer, setHovered)
  syncCaves(world, maze, sprites, textures, flames, isPlayer, setHovered)
  syncLabyrinths(world, maze, sprites, textures, flames, isPlayer, setHovered)
  syncEglises(world, maze, sprites, textures, flames, isPlayer, setHovered)

private def syncEnemies(
    world: Container,
    maze: MazeState,
    sprites: MazeSprites,
    textures: js.Dictionary[Texture],
    goblinFrames: Map[String, js.Array[Texture]],
    flames: js.Array[Texture],
    blocked: Set[(Int, Int)],
    isPlayer: Boolean,
    setHovered: Option[HoverTarget] => Unit
): Unit =
  val liveIds = maze.enemies.map(_.id).toSet
  removeStaleWithEffect(world, sprites.enemies, liveIds, flames)
  sprites.goblinFacing.filterInPlace((id, _) => liveIds.contains(id))
  maze.enemies.foreach { e =>
    val g = sprites.enemies.getOrElseUpdate(
      e.id,
      newEnemySprite(
        world,
        e.kind,
        textures,
        goblinFrames,
        HoverTarget(isPlayer, HoverKind.EnemyH, e.id),
        setHovered
      )
    )
    setPos(g, e.pos)
    val angle = enemyFacingAngle(e, blocked)
    e.kind match
      case UnitKind.Elf | UnitKind.Minotaur | UnitKind.Paladin => angle.foreach(a => g.rotation = a)
      case UnitKind.Goblin =>
        angle.map(facingDirection).foreach { dir =>
          if !sprites.goblinFacing.get(e.id).contains(dir) then
            sprites.goblinFacing(e.id) = dir
            val anim = g.asInstanceOf[AnimatedSprite]
            anim.textures = goblinFrames(dir)
            anim.play()
        }
  }

private def newEnemySprite(
    world: Container,
    kind: UnitKind,
    textures: js.Dictionary[Texture],
    goblinFrames: Map[String, js.Array[Texture]],
    target: HoverTarget,
    setHovered: Option[HoverTarget] => Unit
): Container = kind match
  case UnitKind.Elf =>
    newHoverSprite(world, textures(AssetPaths.Elf), GridConfig.cellSize * 0.8, target, setHovered)
  case UnitKind.Minotaur =>
    // Heavier raider than the Goblin (Minotaure.md: 50 HP vs 5) — a bigger sprite reflects that.
    newHoverSprite(
      world,
      textures(AssetPaths.Minotaur),
      GridConfig.cellSize * 1.1,
      target,
      setHovered
    )
  case UnitKind.Paladin =>
    newHoverSprite(world, textures(AssetPaths.Paladin), GridConfig.cellSize * 1.0, target, setHovered)
  case UnitKind.Goblin =>
    val s = newAnimatedSprite(goblinFrames("front"), GridConfig.cellSize * 0.8)
    wireHover(s, target, setHovered)
    addTo(world, s)

// Which of the 4 walk-cycle frame sets to show, from the enemy's facing angle
// (Pixi's y-axis points down, so "front" = walking toward the viewer, i.e. down).
private def facingDirection(angle: Double): String =
  val deg = ((math.toDegrees(angle) % 360) + 360) % 360
  if deg < 45 || deg >= 315 then "right"
  else if deg < 135 then "front"
  else if deg < 225 then "left"
  else "back"

private def syncForests(
    world: Container,
    maze: MazeState,
    sprites: MazeSprites,
    textures: js.Dictionary[Texture],
    flames: js.Array[Texture],
    isPlayer: Boolean,
    setHovered: Option[HoverTarget] => Unit
): Unit =
  maze.forests.foreach { f =>
    val g = sprites.forests.getOrElseUpdate(
      f.id,
      newHoverSprite(
        world,
        textures(AssetPaths.Forest),
        GridConfig.cellSize * 0.9,
        HoverTarget(isPlayer, HoverKind.ForestH, f.id),
        setHovered
      )
    )
    setPos(g, GridConfig.cellCenter(f.col, f.row))
    val previousCountdown = sprites.forestTimers.getOrElse(f.id, f.elfSpawnInMs)
    sprites.forestTimers(f.id) = f.elfSpawnInMs
    if hasWrapped(previousCountdown, f.elfSpawnInMs) then
      spawnEffect(world, Vec2(g.x, g.y), flames, scale = 0.6)
  }

private def syncCaves(
    world: Container,
    maze: MazeState,
    sprites: MazeSprites,
    textures: js.Dictionary[Texture],
    flames: js.Array[Texture],
    isPlayer: Boolean,
    setHovered: Option[HoverTarget] => Unit
): Unit =
  maze.caves.foreach { c =>
    val g = sprites.caves.getOrElseUpdate(
      c.id,
      newHoverCaveSprite(world, textures, HoverTarget(isPlayer, HoverKind.CaveH, c.id), setHovered)
    )
    setPos(g, GridConfig.cellCenter(c.col, c.row))
    val previousCountdown = sprites.caveTimers.getOrElse(c.id, c.goblinSpawnInMs)
    sprites.caveTimers(c.id) = c.goblinSpawnInMs
    if hasWrapped(previousCountdown, c.goblinSpawnInMs) then
      spawnEffect(world, Vec2(g.x, g.y), flames, scale = 0.6)
  }

private def syncLabyrinths(
    world: Container,
    maze: MazeState,
    sprites: MazeSprites,
    textures: js.Dictionary[Texture],
    flames: js.Array[Texture],
    isPlayer: Boolean,
    setHovered: Option[HoverTarget] => Unit
): Unit =
  maze.labyrinths.foreach { l =>
    val g = sprites.labyrinths.getOrElseUpdate(
      l.id,
      newHoverSprite(
        world,
        textures(AssetPaths.LabyrintheIcon),
        GridConfig.cellSize * 0.9,
        HoverTarget(isPlayer, HoverKind.LabyrintheH, l.id),
        setHovered
      )
    )
    setPos(g, GridConfig.cellCenter(l.col, l.row))
    val previousCountdown = sprites.labyrintheTimers.getOrElse(l.id, l.minotaurSpawnInMs)
    sprites.labyrintheTimers(l.id) = l.minotaurSpawnInMs
    if hasWrapped(previousCountdown, l.minotaurSpawnInMs) then
      spawnEffect(world, Vec2(g.x, g.y), flames, scale = 0.6)
  }

private def syncEglises(
    world: Container,
    maze: MazeState,
    sprites: MazeSprites,
    textures: js.Dictionary[Texture],
    flames: js.Array[Texture],
    isPlayer: Boolean,
    setHovered: Option[HoverTarget] => Unit
): Unit =
  maze.eglises.foreach { e =>
    val g = sprites.eglises.getOrElseUpdate(
      e.id,
      newHoverSprite(
        world,
        textures(AssetPaths.EgliseIcon),
        GridConfig.cellSize * 0.9,
        HoverTarget(isPlayer, HoverKind.EgliseH, e.id),
        setHovered
      )
    )
    setPos(g, GridConfig.cellCenter(e.col, e.row))
    val previousCountdown = sprites.egliseTimers.getOrElse(e.id, e.paladinSpawnInMs)
    sprites.egliseTimers(e.id) = e.paladinSpawnInMs
    if hasWrapped(previousCountdown, e.paladinSpawnInMs) then
      spawnEffect(world, Vec2(g.x, g.y), flames, scale = 0.6)
  }

private def newHoverSprite(
    world: Container,
    texture: Texture,
    size: Double,
    target: HoverTarget,
    setHovered: Option[HoverTarget] => Unit
): Sprite =
  val s = newSprite(texture, size)
  wireHover(s, target, setHovered)
  addTo(world, s)

private def newHoverCaveSprite(
    world: Container,
    textures: js.Dictionary[Texture],
    target: HoverTarget,
    setHovered: Option[HoverTarget] => Unit
): Sprite =
  val s = newHoverSprite(
    world,
    textures(AssetPaths.CaveRock),
    GridConfig.cellSize * 0.9,
    target,
    setHovered
  )
  s.tint = CaveTint
  s

// Hovering a sprite shows a tooltip with that entity's live stats (see updateTooltip);
// the sprite itself just reports "I'm hovered" / "I'm not" — the ticker reads current
// stats from the latest BattleState each frame so the tooltip stays live while it's up.
private def wireHover(
    g: Container,
    target: HoverTarget,
    setHovered: Option[HoverTarget] => Unit
): Unit =
  g.eventMode = "static"
  g.on("pointerover", (_: FederatedPointerEvent) => setHovered(Some(target)))
  g.on("pointerout", (_: FederatedPointerEvent) => setHovered(None))

// True the tick a building's spawn countdown wraps around (i.e. it just launched a unit).
// Pure query (CQS) — the caller is responsible for recording the new value (see call sites).
private def hasWrapped(previous: Double, current: Double): Boolean = current > previous

private def removeStaleWithEffect(
    world: Container,
    sprites: mutable.Map[Long, Container],
    liveIds: Set[Long],
    flames: js.Array[Texture]
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
    Pathfinding.shortestPath(currentCell, GridConfig.goalCell, blocked).collect {
      case path if path.size > 1 =>
        angleTo(e.pos, GridConfig.cellCenter(path(1)._1, path(1)._2))
    }

// ── Sprite/effect helpers ───────────────────────────────────────────────

private def newSprite(texture: Texture, size: Double): Sprite =
  val s = new Sprite(texture)
  s.anchor.set(0.5)
  s.width = size
  s.height = size
  s

private def newAnimatedSprite(frames: js.Array[Texture], size: Double): AnimatedSprite =
  val s = new AnimatedSprite(frames)
  s.anchor.set(0.5)
  s.width = size
  s.height = size
  s.loop = true
  s.animationSpeed = 0.15
  s.play()
  s

private def spawnEffect(
    world: Container,
    pos: Vec2,
    frames: js.Array[Texture],
    scale: Double
): Unit =
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

private def addTo[T <: Container](world: Container, s: T): T =
  world.addChild(s)
  s

private def setPos(g: Container, pos: Vec2): Unit =
  g.x = pos.x
  g.y = pos.y

// ── Hover tooltip (building/unit stats — see wireHover) ─────────────────

private def positionTooltip(x: Double, y: Double): Unit =
  val el = document.getElementById("tooltip").asInstanceOf[dom.html.Element]
  el.style.left = s"${x + 12}px"
  el.style.top = s"${y + 12}px"

// Re-reads live stats from the current BattleState every frame, so HP/timers shown in
// the tooltip stay accurate while the pointer holds still. Self-heals (hides, clears
// the target) if the hovered entity died or was removed since the last frame.
// buttonTooltipActive: a button-hover tooltip is currently showing (see
// wireButtonTooltip) — this function must not clear it, since both hover sources
// share the same #tooltip element and this runs unconditionally every tick.
private def updateTooltip(
    hovered: Option[HoverTarget],
    battle: BattleState,
    buttonTooltipActive: Boolean
): Option[HoverTarget] =
  val tooltip = document.getElementById("tooltip")
  hovered.flatMap(t => hoverText(t, battle).map(text => (t, text))) match
    case Some((target, text)) =>
      tooltip.textContent = text
      tooltip.classList.add("visible")
      Some(target)
    case None =>
      if !buttonTooltipActive then tooltip.classList.remove("visible")
      None

private def hoverText(target: HoverTarget, battle: BattleState): Option[String] =
  val maze = if target.isPlayer then battle.player else battle.ai
  target.kind match
    case HoverKind.EnemyH =>
      maze.enemies.find(_.id == target.id).map { e =>
        e.kind match
          case UnitKind.Paladin =>
            s"Paladin — HP ${e.hp.toInt}/${e.maxHp.toInt}, doesn't plunder; shields adjacent allies " +
              s"from ${Balance.PaladinAuraDamageReductionPerSec.toInt} dmg/s"
          case _ =>
            val (name, plunders) = e.kind match
              case UnitKind.Elf => ("Elf", s"${Balance.PlunderPerUnit.toInt} wood")
              case UnitKind.Goblin =>
                (
                  "Goblin",
                  s"${Balance.PlunderPerUnit.toInt} wood + ${Balance.PlunderPerUnit.toInt} fire"
                )
              case _ =>
                (
                  "Minotaur",
                  s"${Balance.MinotaurPlunderPerUnit.toInt} wood + ${Balance.MinotaurPlunderPerUnit.toInt} fire"
                )
            s"$name — HP ${e.hp.toInt}/${e.maxHp.toInt}, plunders $plunders on arrival"
      }
    case HoverKind.ForestH =>
      maze.forests.find(_.id == target.id).map { f =>
        val nextElfS = (f.elfSpawnInMs / 1000).ceil.toInt
        s"Forest — ${Balance.AuraDamagePerSec.toInt} dmg/s to adjacent enemies, +${Balance.WoodPerSecPerForest} wood/s, next Elf in ${nextElfS}s"
      }
    case HoverKind.CaveH =>
      maze.caves.find(_.id == target.id).map { c =>
        val nextGoblinS = (c.goblinSpawnInMs / 1000).ceil.toInt
        s"Cave — +${Balance.FirePerSecPerCave.toInt} fire/s, next Goblin in ${nextGoblinS}s"
      }
    case HoverKind.LabyrintheH =>
      maze.labyrinths.find(_.id == target.id).map { l =>
        val nextMinotaurS = (l.minotaurSpawnInMs / 1000).ceil.toInt
        s"Labyrinthe — next Minotaur in ${nextMinotaurS}s"
      }
    case HoverKind.EgliseH =>
      maze.eglises.find(_.id == target.id).map { e =>
        val nextPaladinS = (e.paladinSpawnInMs / 1000).ceil.toInt
        s"Eglise — +${Balance.LightPerSecPerEglise.toInt} light/s, next Paladin in ${nextPaladinS}s"
      }

// ── HTML overlay ────────────────────────────────────────────────────────

private def updateOverlay(battle: BattleState): Unit =
  updateMazePanel("player", battle.player, opponent = battle.ai)
  updateMazePanel("ai", battle.ai, opponent = battle.player)
  updateGameOverBanner(battle)

// Same fields for both panels — the game is symmetric, either side may hold either
// resource or be chasing either victory condition (see CLAUDE.md). Targets are shown
// live since they track the opponent's own count (see VictoryConditions).
private def updateMazePanel(prefix: String, maze: MazeState, opponent: MazeState): Unit =
  document.getElementById(s"$prefix-wood").textContent = maze.wood.toInt.toString
  document.getElementById(s"$prefix-fire").textContent = maze.fire.toInt.toString
  document.getElementById(s"$prefix-light").textContent = maze.light.toInt.toString
  val forestTarget = VictoryConditions.forestTarget(opponent)
  val plunderTarget = VictoryConditions.plunderTarget(opponent)
  document.getElementById(s"$prefix-forests").textContent =
    s"${maze.forests.size}/${forestTarget.toInt}"
  document.getElementById(s"$prefix-plundered").textContent =
    s"${maze.resourcesPlundered.toInt}/${plunderTarget.toInt}"
  updateProgressBar(s"$prefix-forests-bar", maze.forests.size, forestTarget)
  updateProgressBar(s"$prefix-plundered-bar", maze.resourcesPlundered, plunderTarget)

// Visual companion to the "current/target" text above — lets you compare at a glance
// how close each maze is to winning via the same (opponent-relative) condition.
private def updateProgressBar(id: String, current: Double, target: Double): Unit =
  val pct = if target <= 0 then 100.0 else math.min(100.0, current / target * 100.0)
  document.getElementById(id).asInstanceOf[dom.html.Element].style.width = s"$pct%"

private def updateGameOverBanner(battle: BattleState): Unit =
  val banner = document.getElementById("game-over")
  battle.outcome match
    case Some(result) =>
      val title = result match
        case _: MatchResult.PlayerWins => "You win!"
        case _: MatchResult.AiWins     => "AI wins!"
      document.getElementById("game-over-title").textContent = title
      document.getElementById("game-over-reason").textContent = result.reason
      banner.classList.add("visible")
    case None =>
      banner.classList.remove("visible")
