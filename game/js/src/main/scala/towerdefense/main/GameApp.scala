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
  case Forest, Cave

private enum HoverKind derives CanEqual:
  case EnemyH, ForestH, CaveH

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
  val enemies = mutable.Map.empty[Long, Sprite]
  val forests = mutable.Map.empty[Long, Sprite]
  val forestTimers = mutable.Map.empty[Long, Double]
  val caves = mutable.Map.empty[Long, Sprite]
  val caveTimers = mutable.Map.empty[Long, Double]

private object AssetPaths:
  val Forest = "./assets/forest.png"
  val CaveRock = "./assets/cave.png"
  val Elf = "./assets/enemy.png"
  val Goblin = "./assets/goblin.png"
  val Flames =
    List("./assets/flame1.png", "./assets/flame2.png", "./assets/flame3.png", "./assets/flame4.png")
  val All: List[String] = List(Forest, CaveRock, Elf, Goblin) ++ Flames

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
  var battle = Persistence.load().getOrElse(BattleState.initial)
  var selectedBuilding: BuildingChoice = BuildingChoice.Forest
  var hovered: Option[HoverTarget] = None
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

  wireBuildingButtons(choice => selectedBuilding = choice)
  wireSpeedControls(speed)
  wireNewGameButton(() => resetGame())

  app.stage.eventMode = "static"
  app.stage.on(
    "pointerdown",
    (e: FederatedPointerEvent) => {
      battle = handleTap(app, battle, e, selectedBuilding)
    }
  )
  app.stage.on("pointermove", (e: FederatedPointerEvent) => positionTooltip(e.globalX, e.globalY))

  app.ticker.add { t =>
    battle = BattleEngine.tick(battle, speed.effectiveDeltaMs(t.deltaMS))
    syncMaze(
      playerWorld,
      battle.player,
      playerSprites,
      textures,
      flameFrames,
      isPlayer = true,
      h => hovered = h
    )
    syncMaze(
      aiWorld,
      battle.ai,
      aiSprites,
      textures,
      flameFrames,
      isPlayer = false,
      h => hovered = h
    )
    applyViewTransform(app, battleWorld, aiWorld)
    updateOverlay(battle)
    hovered = updateTooltip(hovered, battle)
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

private def wireBuildingButtons(onSelect: BuildingChoice => Unit): Unit =
  val forestBtn = document.getElementById("build-forest")
  val caveBtn = document.getElementById("build-cave")
  wireButtonTooltip(forestBtn, ForestTooltip)
  wireButtonTooltip(caveBtn, CaveTooltip)
  forestBtn.addEventListener(
    "click",
    (_: dom.Event) => selectBuilding(BuildingChoice.Forest, forestBtn, caveBtn, onSelect)
  )
  caveBtn.addEventListener(
    "click",
    (_: dom.Event) => selectBuilding(BuildingChoice.Cave, caveBtn, forestBtn, onSelect)
  )

// Buttons live outside #game-container (the tooltip's positioned ancestor), so unlike
// in-canvas hover (see wireHover/positionTooltip) coordinates must be translated from
// viewport space to that container before reusing the same #tooltip element.
private def wireButtonTooltip(btn: dom.Element, text: String): Unit =
  btn.addEventListener("mouseenter", (_: dom.Event) => showButtonTooltip(text))
  btn.addEventListener(
    "mousemove",
    (e: dom.Event) => positionButtonTooltip(e.asInstanceOf[dom.MouseEvent])
  )
  btn.addEventListener(
    "mouseleave",
    (_: dom.Event) => document.getElementById("tooltip").classList.remove("visible")
  )

private def showButtonTooltip(text: String): Unit =
  val tooltip = document.getElementById("tooltip")
  tooltip.textContent = text
  tooltip.classList.add("visible")

private def positionButtonTooltip(e: dom.MouseEvent): Unit =
  val containerRect = document.getElementById("game-container").getBoundingClientRect()
  positionTooltip(e.clientX - containerRect.left, e.clientY - containerRect.top)

private def selectBuilding(
    choice: BuildingChoice,
    active: dom.Element,
    inactive: dom.Element,
    onSelect: BuildingChoice => Unit
): Unit =
  onSelect(choice)
  active.classList.add("selected")
  inactive.classList.remove("selected")

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
  (sprites.enemies.values ++ sprites.forests.values ++ sprites.caves.values).foreach(
    world.removeChild
  )
  sprites.enemies.clear()
  sprites.forests.clear()
  sprites.forestTimers.clear()
  sprites.caves.clear()
  sprites.caveTimers.clear()

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
      val tryPlace =
        if choice == BuildingChoice.Forest then Placement.tryPlaceForest else Placement.tryPlaceCave
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
    flames: js.Array[Texture],
    isPlayer: Boolean,
    setHovered: Option[HoverTarget] => Unit
): Unit =
  val blocked = maze.buildingCells
  syncEnemies(world, maze, sprites, textures, flames, blocked, isPlayer, setHovered)
  syncForests(world, maze, sprites, textures, flames, isPlayer, setHovered)
  syncCaves(world, maze, sprites, textures, flames, isPlayer, setHovered)

private def syncEnemies(
    world: Container,
    maze: MazeState,
    sprites: MazeSprites,
    textures: js.Dictionary[Texture],
    flames: js.Array[Texture],
    blocked: Set[(Int, Int)],
    isPlayer: Boolean,
    setHovered: Option[HoverTarget] => Unit
): Unit =
  removeStaleWithEffect(world, sprites.enemies, maze.enemies.map(_.id).toSet, flames)
  maze.enemies.foreach { e =>
    val g = sprites.enemies.getOrElseUpdate(
      e.id,
      newHoverSprite(
        world,
        enemyTexture(e.kind, textures),
        GridConfig.cellSize * 0.8,
        HoverTarget(isPlayer, HoverKind.EnemyH, e.id),
        setHovered
      )
    )
    setPos(g, e.pos)
    enemyFacingAngle(e, blocked).foreach(a => g.rotation = a)
  }

private def enemyTexture(kind: UnitKind, textures: js.Dictionary[Texture]): Texture = kind match
  case UnitKind.Elf    => textures(AssetPaths.Elf)
  case UnitKind.Goblin => textures(AssetPaths.Goblin)

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
    g: Sprite,
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
    sprites: mutable.Map[Long, Sprite],
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

private def addTo(world: Container, s: Sprite): Sprite =
  world.addChild(s)
  s

private def setPos(g: Sprite, pos: Vec2): Unit =
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
private def updateTooltip(hovered: Option[HoverTarget], battle: BattleState): Option[HoverTarget] =
  val tooltip = document.getElementById("tooltip")
  hovered.flatMap(t => hoverText(t, battle).map(text => (t, text))) match
    case Some((target, text)) =>
      tooltip.textContent = text
      tooltip.classList.add("visible")
      Some(target)
    case None =>
      tooltip.classList.remove("visible")
      None

private def hoverText(target: HoverTarget, battle: BattleState): Option[String] =
  val maze = if target.isPlayer then battle.player else battle.ai
  target.kind match
    case HoverKind.EnemyH =>
      maze.enemies.find(_.id == target.id).map { e =>
        val (name, plunders) =
          if e.kind == UnitKind.Elf then ("Elf", s"${Balance.PlunderPerUnit.toInt} wood")
          else
            (
              "Goblin",
              s"${Balance.PlunderPerUnit.toInt} wood + ${Balance.PlunderPerUnit.toInt} fire"
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

// ── HTML overlay ────────────────────────────────────────────────────────

private def updateOverlay(battle: BattleState): Unit =
  updateMazePanel("player", battle.player)
  updateMazePanel("ai", battle.ai)
  updateGameOverBanner(battle)

// Same fields for both panels — the game is symmetric, either side may hold either
// resource or be chasing either victory condition (see CLAUDE.md).
private def updateMazePanel(prefix: String, maze: MazeState): Unit =
  document.getElementById(s"$prefix-wood").textContent = maze.wood.toInt.toString
  document.getElementById(s"$prefix-fire").textContent = maze.fire.toInt.toString
  document.getElementById(s"$prefix-forests").textContent =
    s"${maze.forests.size}/${Balance.NatureVictoryForestTarget}"
  document.getElementById(s"$prefix-plundered").textContent =
    s"${maze.resourcesPlundered.toInt}/${Balance.ChaosVictoryPlunderTarget.toInt}"

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
