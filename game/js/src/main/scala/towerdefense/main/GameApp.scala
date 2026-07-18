package towerdefense.main

import org.scalajs.dom
import org.scalajs.dom.document
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.util.Random
import towerdefense.domain.*
import towerdefense.domain.geometry.Vec2
import towerdefense.pixi.*

private case class ViewTransform(scale: Double, offsetX: Double, offsetY: Double)

private enum HoverKind derives CanEqual:
  case EnemyH
  case BuildingH(kind: BuildingKind)

private case class HoverTarget(isPlayer: Boolean, kind: HoverKind, id: Long)

// Idle/attract-mode vs. the human actually playing. On first load (nothing saved — see
// Persistence.load) the site defaults to Spectating: two random AiStrategy.ladder entries
// duel each other on both mazes (playerStrategy is driven too, see BattleEngine.tick),
// nobody's taps do anything. leftIdx drives the player-side maze, rightIdx the ai-side
// maze — same left/right split as the rest of the UI (playerWorld/aiWorld). Playing is
// today's original mode: the player-side maze is human-controlled, ai-side follows the
// difficulty ladder via aiLevelIndex.
private enum Mode derives CanEqual:
  case Spectating(leftIdx: Int, rightIdx: Int)
  case Playing

// How long a finished spectate match's game-over banner stays up before the mazes reset
// for the next pairing — long enough to read "X wins", short enough the attract loop
// doesn't stall.
private val SpectateRestartDelayMs = 3000.0

// How long a finished *Playing* match's game-over banner sits idle before the app gives
// up on the human coming back and drops into the attract-mode AI duel — an abandoned
// game-over screen would otherwise just sit there forever with nothing watchable on it.
private val PlayingIdleToSpectateDelayMs = 30000.0

// How long a building's spawn-preview ghost (see spawnUnitPreview) stays on screen —
// real wall-clock time, same as the flame burst it replaces (Pixi's own animation
// ticker, unaffected by GameSpeed's pause/fast-forward).
private val UnitPreviewDurationMs = 1000.0

private def randomLadderIndex(): Int = Random.nextInt(AiStrategy.ladder.length)

// Picks a random ladder index different from `exclude` (when the ladder has more than one
// entry) — used both for the initial random pairing (so the two AIs facing off aren't
// always the same one) and for picking the loser's replacement (so "another random AI" is
// never just the strategy that already lost).
private def randomOtherLadderIndex(exclude: Int): Int =
  if AiStrategy.ladder.length <= 1 then exclude
  else
    val idx = Random.nextInt(AiStrategy.ladder.length - 1)
    if idx >= exclude then idx + 1 else idx

private def randomSpectatingPair(): Mode.Spectating =
  val left = randomLadderIndex()
  Mode.Spectating(left, randomOtherLadderIndex(left))

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

// Per-maze sprite state. One instance for the player's maze, one for the AI's. One map
// per entity category (not per BuildingKind) — see BuildingVisuals/syncBuildings for how
// a single generic sync function replaces what used to be 5 near-identical ones.
private class MazeSprites:
  val creatures = mutable.Map.empty[Long, Container]
  // Which of a directional creature's (Goblin, Elf) 4 frame sets is currently applied —
  // avoids resetting the walk-cycle animation every tick, only when the facing changes.
  val directionalFacing = mutable.Map.empty[Long, String]
  // Necromancer ids currently showing the sheet's "Summon" animation (Creature.frozenMs >
  // 0) rather than their normal walk cycle — same "only swap textures on an actual state
  // change" guard as directionalFacing, avoiding restarting the animation every tick.
  val necromancerSummoning = mutable.Set.empty[Long]
  val buildings = mutable.Map.empty[Long, Sprite]
  // Only populated for kinds with a spawn timer (BuildingSpecs.all(_).spawns.isDefined) —
  // Watchtower is naturally absent, no special-casing needed.
  val buildingTimers = mutable.Map.empty[Long, Double]
  // Which BuildingKind each sprite is currently textured/sized/tinted for — Placement's
  // upgrade path keeps the same id/cell but changes kind, so this is how syncBuildings
  // notices a Grove just became a Forest and needs to re-skin the existing sprite,
  // instead of only ever skinning it once at creation (see syncBuildings).
  val buildingKinds = mutable.Map.empty[Long, BuildingKind]

private object AssetPaths:
  // One distinct icon per Nature tier — see Bosquet.md/Foret.md/Jungle.md, each embeds
  // its own reference image (see LICENSE-grove/forest/jungle.txt for sourcing).
  val Grove = "./assets/grove.png"
  val Forest = "./assets/forest.png"
  val Jungle = "./assets/jungle.png"
  val CaveRock = "./assets/cave.png"
  val LabyrintheIcon = "./assets/labyrinthe.png"
  val EgliseIcon = "./assets/eglise.png"
  val WatchtowerIcon = "./assets/watchtower.png"
  // Ange.md's own reference image, supplied directly by the project owner — see
  // LICENSE-angel.txt.
  val AngelIcon = "./assets/angel.png"
  val Minotaur = "./assets/minotaur.png"
  val Paladin = "./assets/paladin.png"
  // Mort/Science buildings and Vampire have no dedicated art pack — these are the vault
  // design docs' own embedded reference images (Tombe.md/Chateau Noir.md/Vampire.md/
  // Labo *.md), downscaled to sprite size, same "reuse the doc's own image" fallback
  // already used for Nature's *-reference.png assets.
  val TombIcon = "./assets/tomb.png"
  val BlackCastleIcon = "./assets/chateau-noir.png"
  val Vampire = "./assets/vampire.png"
  val LaboNaturelIcon = "./assets/labo-naturel.png"
  val LaboSombreIcon = "./assets/labo-sombre.png"
  val LaboDeRechercheIcon = "./assets/labo-de-recherche.png"
  val LaboDeLaLoiIcon = "./assets/labo-de-la-loi.png"
  val LaboDuChaosIcon = "./assets/labo-du-chaos.png"
  // Maison de la Mort.md's own reference image, supplied directly by the project owner —
  // see LICENSE-death-house.txt.
  val DeathHouseIcon = "./assets/death-house.png"
  val Flames =
    List("./assets/flame1.png", "./assets/flame2.png", "./assets/flame3.png", "./assets/flame4.png")
  val Wolf = List("./assets/wolf/run-0.png", "./assets/wolf/run-1.png", "./assets/wolf/run-2.png")
  // Single-facing 10-frame walk cycle (see LICENSE-zombie.txt: supplied directly, no
  // source page) — animated like Wolf, not direction-swapped like Goblin/Elf's 4 sets.
  val ZombieFrames: List[String] = (0 until 10).map(i => f"./assets/zombie/walk-$i%02d.png").toList
  // Necromancien.md/Ame.md: cropped from a labeled reference sheet supplied directly by
  // the project owner (see LICENSE-necromancer.txt) — single-facing walk cycles, same
  // rotate-to-face treatment as Wolf/Zombie. Necromancer's frames are the sheet's "Idle"
  // row (per the project owner's instruction), Soul's are its "Soul: Walk" row.
  val NecromancerFrames: List[String] = (0 until 6).map(i => f"./assets/necromancer/walk-$i%02d.png").toList
  val SoulFrames: List[String] = (0 until 5).map(i => f"./assets/soul/walk-$i%02d.png").toList
  // The sheet's "Summon" row — shown instead of NecromancerFrames while
  // Creature.frozenMs > 0 (Necromancien.md: rooted in place for 1s while it invokes an Ame).
  val NecromancerSummonFrames: List[String] = (0 until 8).map(i => f"./assets/necromancer/summon-$i%02d.png").toList
  private val Directions = List("front", "back", "left", "right")
  // 4-direction walk-cycle frame sets, keyed by direction — shared shape for any
  // creature animated this way (see newDirectionalFrames/syncCreatures' facing logic).
  private def directionalFrames(folder: String, frameCount: Int): Map[String, List[String]] =
    Directions.map(d => d -> (0 until frameCount).map(i => f"./assets/$folder/$d-walk-$i%02d.png").toList).toMap
  val GoblinFrames: Map[String, List[String]] = directionalFrames("goblin", frameCount = 10)
  // See LICENSE-elf.txt: cropped from CraftPix's Free Base 4-Direction Male Character
  // Pixel Art pack's unarmed walk-cycle sheet (6 frames per direction).
  val ElfFrames: Map[String, List[String]] = directionalFrames("elf", frameCount = 6)
  val All: List[String] =
    List(
      Grove, Forest, Jungle, CaveRock, LabyrintheIcon, EgliseIcon, WatchtowerIcon, AngelIcon, Minotaur, Paladin,
      TombIcon, BlackCastleIcon, Vampire, LaboNaturelIcon, LaboSombreIcon, LaboDeRechercheIcon,
      LaboDeLaLoiIcon, LaboDuChaosIcon, DeathHouseIcon
    ) ++ GoblinFrames.values.flatten ++ ElfFrames.values.flatten ++ Flames ++ Wolf ++ ZombieFrames ++
      NecromancerFrames ++ SoulFrames ++ NecromancerSummonFrames

private val CaveTint = 0xff7a45 // warm/fiery recolor for an otherwise cool-gray rock tile

// Per-BuildingKind rendering data — the JS-side mirror of BuildingSpecs, driving the one
// generic syncBuildings instead of what used to be 5 near-identical sync functions.
private case class BuildingVisual(texturePath: String, renderSize: Double, tint: Option[Int])

private object BuildingVisuals:
  // A distinct icon per Nature tier (see AssetPaths.Grove/Forest/Jungle), each matching
  // the reference image embedded in its own vault doc — see LICENSE-grove/forest/jungle.txt.
  val all: Map[BuildingKind, BuildingVisual] = Map(
    BuildingKind.Grove -> BuildingVisual(AssetPaths.Grove, GridConfig.cellSize * 0.75, None),
    BuildingKind.Forest -> BuildingVisual(AssetPaths.Forest, GridConfig.cellSize * 0.9, None),
    BuildingKind.Jungle -> BuildingVisual(AssetPaths.Jungle, GridConfig.cellSize * 1.15, None),
    BuildingKind.Cave -> BuildingVisual(AssetPaths.CaveRock, GridConfig.cellSize * 0.9, Some(CaveTint)),
    BuildingKind.Labyrinth -> BuildingVisual(AssetPaths.LabyrintheIcon, GridConfig.cellSize * 0.9, None),
    BuildingKind.Church -> BuildingVisual(AssetPaths.EgliseIcon, GridConfig.cellSize * 0.9, None),
    BuildingKind.Watchtower -> BuildingVisual(AssetPaths.WatchtowerIcon, GridConfig.cellSize * 0.9, None),
    BuildingKind.Angel -> BuildingVisual(AssetPaths.AngelIcon, GridConfig.cellSize * 1.1, None),
    BuildingKind.Tomb -> BuildingVisual(AssetPaths.TombIcon, GridConfig.cellSize * 0.9, None),
    BuildingKind.BlackCastle -> BuildingVisual(AssetPaths.BlackCastleIcon, GridConfig.cellSize * 1.1, None),
    BuildingKind.LaboNaturel -> BuildingVisual(AssetPaths.LaboNaturelIcon, GridConfig.cellSize * 0.8, None),
    BuildingKind.LaboSombre -> BuildingVisual(AssetPaths.LaboSombreIcon, GridConfig.cellSize * 0.8, None),
    BuildingKind.LaboDeRecherche -> BuildingVisual(AssetPaths.LaboDeRechercheIcon, GridConfig.cellSize * 0.8, None),
    BuildingKind.LaboDeLaLoi -> BuildingVisual(AssetPaths.LaboDeLaLoiIcon, GridConfig.cellSize * 0.8, None),
    BuildingKind.LaboDuChaos -> BuildingVisual(AssetPaths.LaboDuChaosIcon, GridConfig.cellSize * 0.8, None),
    BuildingKind.DeathHouse -> BuildingVisual(AssetPaths.DeathHouseIcon, GridConfig.cellSize * 1.1, None)
  )

// DOM id suffix per kind (index.html's #build-<slug> buttons and #<prefix>-<slug>
// stats) — separate from BuildingKind.toString since Labyrinth's French asset/DOM naming
// ("labyrinthe") diverges from the enum case spelling. Forest/Jungle have no build button
// (see buildableKinds — they're upgrade-only) but still need a slug for consistency.
private def domSlug(kind: BuildingKind): String = kind match
  case BuildingKind.Grove           => "grove"
  case BuildingKind.Forest          => "forest"
  case BuildingKind.Jungle          => "jungle"
  case BuildingKind.Cave            => "cave"
  case BuildingKind.Labyrinth       => "labyrinthe"
  case BuildingKind.Church          => "eglise"
  case BuildingKind.Watchtower      => "watchtower"
  case BuildingKind.Angel           => "angel"
  case BuildingKind.Tomb            => "tomb"
  case BuildingKind.BlackCastle     => "chateau-noir"
  case BuildingKind.DeathHouse      => "death-house"
  case BuildingKind.LaboNaturel     => "labo-naturel"
  case BuildingKind.LaboSombre      => "labo-sombre"
  case BuildingKind.LaboDeRecherche => "labo-de-recherche"
  case BuildingKind.LaboDeLaLoi     => "labo-de-la-loi"
  case BuildingKind.LaboDuChaos     => "labo-du-chaos"

// Only these can be placed fresh via a toolbar button — Forest/Jungle are reached only
// by upgrading an existing Grove/Forest (see Placement.tryUpgradeBuilding), so they get
// no build-<slug> button at all (see wireBuildingButtons/updateBuildButtonsAffordability).
private def buildableKinds: List[BuildingKind] =
  BuildingKind.values.toList.filter(BuildingSpecs.all(_).buildableDirectly)

private def resourceName(res: Resource): String = res match
  case Resource.Wood    => "wood"
  case Resource.Fire    => "fire"
  case Resource.Light   => "light"
  case Resource.Shadow  => "shadow"
  case Resource.Crystal => "crystal"

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
  // resizeTo the *container*, not the window: the header (now several rows of concept
  // info) eats real vertical space, so app.screen.width/height must reflect what's
  // actually left for the canvas — computeViewTransform's scale-to-fit math is only as
  // accurate as these dimensions, and sizing to the full window overstated the available
  // height, letting the maze render partly outside the visible area.
  val options = js.Dynamic.literal(
    resizeTo = document.getElementById("game-container"),
    backgroundColor = 0x0f172a,
    antialias = true
  )
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
  val wolfFrames = js.Array(AssetPaths.Wolf.map(textures(_))*)
  val zombieFrames = js.Array(AssetPaths.ZombieFrames.map(textures(_))*)
  val necromancerFrames = js.Array(AssetPaths.NecromancerFrames.map(textures(_))*)
  val soulFrames = js.Array(AssetPaths.SoulFrames.map(textures(_))*)
  val necromancerSummonFrames = js.Array(AssetPaths.NecromancerSummonFrames.map(textures(_))*)
  val goblinFrames: Map[String, js.Array[Texture]] =
    AssetPaths.GoblinFrames.map { case (dir, paths) => dir -> js.Array(paths.map(textures(_))*) }
  val elfFrames: Map[String, js.Array[Texture]] =
    AssetPaths.ElfFrames.map { case (dir, paths) => dir -> js.Array(paths.map(textures(_))*) }
  // No saved game (first-ever visit, or a cleared one) means there's nothing to resume
  // the player into — default to the attract-mode AI duel instead of an empty player-
  // controlled maze. A saved game always resumes straight into Playing.
  val savedGame = Persistence.load()
  var battle = savedGame.map(_._1).getOrElse(BattleState.initial)
  var aiLevelIndex = savedGame.map(_._2).getOrElse(0)
  var mode: Mode = savedGame match
    case Some(_) => Mode.Playing
    case None    => randomSpectatingPair()
  var spectateRestartCountdownMs = 0.0
  var playingIdleCountdownMs = 0.0
  var selectedBuilding: BuildingKind = BuildingKind.Grove
  var hovered: Option[HoverTarget] = None
  // Set by clicking a building (not just hovering it) — takes priority over `hovered`
  // for the tooltip/destroy button, and its position is set once at click time and never
  // updated again, so the button holds still long enough to actually reach and click
  // (a hover-follows-cursor tooltip means the button keeps re-anchoring itself just out
  // of reach as the cursor approaches it).
  var selectedTarget: Option[HoverTarget] = None
  var hoveringButton = false
  var speed = new GameSpeed
  var msSinceLastSave = 0.0
  val playerSprites = new MazeSprites
  val aiSprites = new MazeSprites

  // Always lands in Playing, whether triggered from a finished/paused game (the original
  // behavior) or from the attract-mode AI duel (see wireNewGameButton) — that's how a
  // spectator becomes a player.
  def resetGame(): Unit =
    clearSprites(playerWorld, playerSprites)
    clearSprites(aiWorld, aiSprites)
    battle = BattleState.initial
    mode = Mode.Playing
    updateModeUi(mode)
    speed.paused = false
    updateSpeedLabel(speed)
    hovered = None
    selectedTarget = None
    playingIdleCountdownMs = 0.0
    Persistence.clear()

  updateModeUi(mode)

  wireBuildingButtons(
    choice =>
      if mode == Mode.Playing then
        selectedBuilding = choice
        // A previously clicked building's tooltip (with its Destroy/Upgrade/Research
        // buttons) otherwise stays stuck on screen after the player picks a different
        // building to place next — clearing it here lets the ticker's hover self-heal
        // fall back to whatever the cursor is actually over (or nothing).
        selectedTarget = None,
    choice => mode == Mode.Playing && canAfford(battle.player, choice),
    active => hoveringButton = active
  )
  wireSpeedControls(speed)
  wireNewGameButton(() => resetGame())
  wireFullscreenButton()
  wireAiLevelSelect(aiLevelIndex, index => aiLevelIndex = index)
  wireDestroyButton((col, row) => battle = destroyPlayerBuilding(battle, mode, col, row))
  wireUpgradeButton((col, row) => battle = upgradePlayerBuilding(battle, mode, col, row))
  wireResearchButton((col, row) => battle = researchPlayerBuilding(battle, mode, col, row))

  app.stage.eventMode = "static"
  app.stage.on(
    "pointerdown",
    (e: FederatedPointerEvent) => {
      val clickedBuilding =
        cellAt(app, e).flatMap { case (col, row) => buildingAt(battle.player, isPlayer = true, col, row) }
      clickedBuilding match
        case Some(target) =>
          selectedTarget = Some(target)
          val canvasRect = app.canvas.getBoundingClientRect()
          positionTooltip(canvasRect.left + e.globalX, canvasRect.top + e.globalY)
        case None =>
          selectedTarget = None
          if mode == Mode.Playing then battle = handleTap(app, battle, e, selectedBuilding)
    }
  )
  app.stage.on(
    "pointermove",
    (e: FederatedPointerEvent) => {
      // Only track the cursor while nothing is hovered or selected — once a target locks
      // in, the tooltip (and its Destroy button, when shown) must hold still, or the
      // button keeps re-anchoring itself just out of reach as the cursor moves toward it.
      if hovered.isEmpty && selectedTarget.isEmpty then
        val canvasRect = app.canvas.getBoundingClientRect()
        positionTooltip(canvasRect.left + e.globalX, canvasRect.top + e.globalY)
    }
  )

  app.ticker.add { t =>
    val wasUnresolved = battle.outcome.isEmpty
    val (tickAiStrategy, tickPlayerStrategy) = mode match
      case Mode.Spectating(leftIdx, rightIdx) =>
        (AiStrategy.ladder(rightIdx)._2, Some(AiStrategy.ladder(leftIdx)._2))
      case Mode.Playing => (AiStrategy.ladder(aiLevelIndex)._2, None)
    battle = BattleEngine.tick(
      battle,
      speed.effectiveDeltaMs(t.deltaMS),
      aiStrategy = tickAiStrategy,
      playerStrategy = tickPlayerStrategy
    )
    if wasUnresolved then
      battle.outcome.foreach { outcome =>
        mode match
          case Mode.Playing =>
            outcome match
              case MatchResult.PlayerWins(_) if aiLevelIndex < AiStrategy.ladder.length - 1 =>
                aiLevelIndex += 1
                updateAiLevelSelect(aiLevelIndex)
              case _ => ()
            // Starts the idle clock the moment a Playing match ends — see the ticker's
            // matching countdown below, which drops back to Spectating once it lapses
            // without the human clicking New Game.
            playingIdleCountdownMs = PlayingIdleToSpectateDelayMs
          // The winning side keeps its ladder index for the next pairing; the losing side
          // gets replaced with another random entry (see randomOtherLadderIndex) — the
          // mazes themselves stay showing the finished match for SpectateRestartDelayMs so
          // the "X wins" banner is actually readable before the next duel starts.
          case Mode.Spectating(leftIdx, rightIdx) =>
            val leftWon = outcome.isInstanceOf[MatchResult.PlayerWins]
            mode =
              if leftWon then Mode.Spectating(leftIdx, randomOtherLadderIndex(leftIdx))
              else Mode.Spectating(randomOtherLadderIndex(rightIdx), rightIdx)
            spectateRestartCountdownMs = SpectateRestartDelayMs
      }
    mode match
      // Paced by the same effective (speed/pause-aware) delta as the battle itself, not
      // raw wall-clock time — pausing to read the "X wins" banner actually holds it up,
      // and fast-forwarding shortens the wait the same way it shortens everything else.
      case Mode.Spectating(_, _) if battle.outcome.isDefined =>
        spectateRestartCountdownMs -= speed.effectiveDeltaMs(t.deltaMS)
        if spectateRestartCountdownMs <= 0 then battle = BattleState.initial
      // An abandoned game-over screen (human never clicked New Game) falls back to the
      // attract-mode duel after PlayingIdleToSpectateDelayMs, same as a fresh page load
      // with no saved game — see randomSpectatingPair. Pausing holds this off too (same
      // effective-delta pacing as the branch above), so a paused game-over screen doesn't
      // get yanked away out from under someone still reading it.
      case Mode.Playing if battle.outcome.isDefined =>
        playingIdleCountdownMs -= speed.effectiveDeltaMs(t.deltaMS)
        if playingIdleCountdownMs <= 0 then
          mode = randomSpectatingPair()
          battle = BattleState.initial
      case _ => ()
    syncMaze(
      playerWorld,
      battle.player,
      playerSprites,
      textures,
      goblinFrames,
      elfFrames,
      wolfFrames,
      zombieFrames,
      necromancerFrames,
      soulFrames,
      necromancerSummonFrames,
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
      elfFrames,
      wolfFrames,
      zombieFrames,
      necromancerFrames,
      soulFrames,
      necromancerSummonFrames,
      flameFrames,
      isPlayer = false,
      h => hovered = h
    )
    applyViewTransform(app, battleWorld, aiWorld)
    updateModeUi(mode)
    updateOverlay(battle)
    if mode == Mode.Playing then updateBuildButtonsAffordability(battle.player)
    else disableAllBuildButtons()
    // Self-heal each independently (e.g. a selected building just got destroyed, or a
    // hovered enemy died) rather than picking one "effective" target and healing that —
    // otherwise losing the prioritized one would incorrectly wipe the other too.
    if selectedTarget.exists(t => hoverText(t, battle).isEmpty) then selectedTarget = None
    if hovered.exists(t => hoverText(t, battle).isEmpty) then hovered = None
    updateTooltip(selectedTarget.orElse(hovered), battle, mode, hoveringButton)
    updateNewGameButtonVisibility(mode, battle.outcome.isDefined || speed.paused)
    // Only an in-progress Playing match is worth resuming on refresh — a spectate pairing
    // regenerates randomly on every fresh load anyway (see savedGame above), so persisting
    // it would just freeze the attract loop on whatever two AIs happened to be dueling at
    // last save. A *finished* Playing match isn't resumable either (BattleEngine.tick
    // no-ops once outcome is set) — leaving it saved would otherwise reload straight back
    // into a stale game-over screen that the idle countdown above immediately yanks away
    // again, a jarring flash for no benefit.
    if mode == Mode.Playing && battle.outcome.isEmpty then
      msSinceLastSave += t.deltaMS
      if msSinceLastSave >= 1000.0 then
        Persistence.save(battle, aiLevelIndex)
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

private val GroveTooltip =
  s"Bosquet — cost ${Balance.GroveCostWood.toInt} wood. " +
    s"+${formatDecimal(Balance.WoodPerSecPerGrove)} wood/s, spawns an Elf every " +
    s"${(Balance.ElfSpawnIntervalMs / 1000).toInt}s. Upgrades into a Forêt, then a Jungle."

private val CaveTooltip =
  s"Cave — cost ${Balance.CaveCostWood.toInt} wood + ${Balance.CaveCostFire.toInt} fire. " +
    s"+${formatDecimal(Balance.FirePerSecPerCave)} fire/s, spawns a Goblin every ${(Balance.GoblinSpawnIntervalMs / 1000).toInt}s"

private val LabyrintheTooltip =
  s"Labyrinthe — cost ${Balance.LabyrintheCostWood.toInt} wood + ${Balance.LabyrintheCostFire.toInt} fire. " +
    s"Spawns a Minotaur every ${(Balance.MinotaurSpawnIntervalMs / 1000).toInt}s, " +
    s"which plunders ${Balance.MinotaurPlunderPerUnit.toInt} wood + ${Balance.MinotaurPlunderPerUnit.toInt} fire on arrival"

private val EgliseTooltip =
  s"Eglise — cost ${Balance.EgliseCostWood.toInt} wood + ${Balance.EgliseCostLight.toInt} light. " +
    s"+${formatDecimal(Balance.LightPerSecPerEglise)} light/s, spawns a Paladin every ${(Balance.PaladinSpawnIntervalMs / 1000).toInt}s, " +
    s"which shields adjacent allies from ${Balance.PaladinAuraDamageReductionPerSec.toInt} dmg/s (doesn't plunder itself)"

private val WatchtowerTooltip =
  s"Tour de guet — cost ${Balance.WatchtowerCostWood.toInt} wood + ${Balance.WatchtowerCostLight.toInt} light. " +
    s"+${formatDecimal(Balance.LightPerSecPerWatchtower)} light/s, spawns no unit — instead inflicts " +
    s"${Balance.WatchtowerDamagePerSec.toInt} dmg/s to the nearest enemy within ${Balance.WatchtowerRangeCells} cells"

private val AngelTooltip =
  s"Ange — cost ${Balance.AngelCostLight.toInt} light. +${formatDecimal(Balance.LightPerSecPerAngel)} light/s, " +
    s"spawns no unit — instead inflicts ${Balance.AngelDamagePerSec.toInt} dmg/s to adjacent enemies and " +
    s"slows them by ${(Balance.AngelSlowFraction * 100).toInt}%"

private val TombTooltip =
  s"Tombe — cost ${Balance.TombCostWood.toInt} wood + ${Balance.TombCostShadow.toInt} shadow. " +
    s"+${formatDecimal(Balance.ShadowPerSecPerTomb)} shadow/s, spawns a Zombie every " +
    s"${(Balance.ZombieSpawnIntervalMs / 1000).toInt}s"

private val BlackCastleTooltip =
  s"Château Noir — cost ${Balance.BlackCastleCostWood.toInt} wood + ${Balance.BlackCastleCostShadow.toInt} shadow. " +
    s"+${formatDecimal(Balance.ShadowPerSecPerBlackCastle)} shadow/s, spawns a Vampire every " +
    s"${(Balance.VampireSpawnIntervalMs / 1000).toInt}s"

private val DeathHouseTooltip =
  s"Maison de la Mort — cost ${Balance.DeathHouseCostWood.toInt} wood + ${Balance.DeathHouseCostShadow.toInt} shadow. " +
    s"+${formatDecimal(Balance.ShadowPerSecPerDeathHouse)} shadow/s, spawns a Necromancer every " +
    s"${(Balance.NecromancerSpawnIntervalMs / 1000).toInt}s"

private val LaboNaturelTooltip =
  s"Labo Naturel — cost ${Balance.LaboNaturelCostWood.toInt} wood + ${Balance.LaboNaturelCostCrystal.toInt} crystal. " +
    s"+${formatDecimal(Balance.CrystalPerSecPerLaboNaturel)} crystal/s"

private val LaboSombreTooltip =
  s"Labo Sombre — cost ${Balance.LaboSombreCostShadow.toInt} shadow + ${Balance.LaboSombreCostCrystal.toInt} crystal. " +
    s"+${formatDecimal(Balance.CrystalPerSecPerLaboSombre)} crystal/s"

private val LaboDeRechercheTooltip =
  s"Labo de Recherche — cost ${Balance.LaboDeRechercheCostCrystal.toInt} crystal. " +
    s"+${formatDecimal(Balance.CrystalPerSecPerLaboDeRecherche)} crystal/s"

private val LaboDeLaLoiTooltip =
  s"Labo de la Loi — cost ${Balance.LaboDeLaLoiCostLight.toInt} light + ${Balance.LaboDeLaLoiCostCrystal.toInt} crystal. " +
    s"+${formatDecimal(Balance.CrystalPerSecPerLaboDeLaLoi)} crystal/s"

private val LaboDuChaosTooltip =
  s"Labo du Chaos — cost ${Balance.LaboDuChaosCostFire.toInt} fire + ${Balance.LaboDuChaosCostCrystal.toInt} crystal. " +
    s"+${formatDecimal(Balance.CrystalPerSecPerLaboDuChaos)} crystal/s"

private val BuildingTooltips: Map[BuildingKind, String] = Map(
  BuildingKind.Grove -> GroveTooltip,
  BuildingKind.Cave -> CaveTooltip,
  BuildingKind.Labyrinth -> LabyrintheTooltip,
  BuildingKind.Church -> EgliseTooltip,
  BuildingKind.Watchtower -> WatchtowerTooltip,
  BuildingKind.Angel -> AngelTooltip,
  BuildingKind.Tomb -> TombTooltip,
  BuildingKind.BlackCastle -> BlackCastleTooltip,
  BuildingKind.DeathHouse -> DeathHouseTooltip,
  BuildingKind.LaboNaturel -> LaboNaturelTooltip,
  BuildingKind.LaboSombre -> LaboSombreTooltip,
  BuildingKind.LaboDeRecherche -> LaboDeRechercheTooltip,
  BuildingKind.LaboDeLaLoi -> LaboDeLaLoiTooltip,
  BuildingKind.LaboDuChaos -> LaboDuChaosTooltip
)

// canAfford is read at click time (not baked into the closure) since the player's
// wood/fire change every tick — see updateBuildButtonsAffordability for the matching
// visual (disabled) state, kept in sync from the same BuildingSpecs costs.
private def wireBuildingButtons(
    onSelect: BuildingKind => Unit,
    canAfford: BuildingKind => Boolean,
    setHoveringButton: Boolean => Unit
): Unit =
  val buttons = buildableKinds.map(kind => kind -> document.getElementById(s"build-${domSlug(kind)}")).toMap
  val allButtons = buttons.values.toList
  buildableKinds.foreach { kind =>
    wireButtonTooltip(buttons(kind), BuildingTooltips(kind), setHoveringButton)
    wireBuildClick(buttons(kind), kind, allButtons, canAfford, onSelect)
  }

private def wireBuildClick(
    btn: dom.Element,
    kind: BuildingKind,
    allButtons: List[dom.Element],
    canAfford: BuildingKind => Boolean,
    onSelect: BuildingKind => Unit
): Unit =
  btn.addEventListener(
    "click",
    (_: dom.Event) => if canAfford(kind) then selectBuilding(kind, btn, allButtons, onSelect)
  )

private def canAfford(maze: MazeState, kind: BuildingKind): Boolean =
  Placement.canAfford(maze.resources, BuildingSpecs.all(kind).cost)

// Reflects afford-ability in real time, since the player's resources move every tick
// even without any click — see wireBuildClick for the matching input gate.
private def updateBuildButtonsAffordability(maze: MazeState): Unit =
  buildableKinds.foreach(kind => updateButtonDisabled(s"build-${domSlug(kind)}", canAfford(maze, kind)))

// While Spectating, buildableKinds' buttons are hidden entirely (see #controlbar's
// body.mode-spectating CSS) but keep them in the disabled state under the hood too, so
// there's no stale "affordable" look left over if the player had one showing right before
// a Spectating pairing kicked back in (see updateModeUi/resetGame).
private def disableAllBuildButtons(): Unit =
  buildableKinds.foreach(kind => updateButtonDisabled(s"build-${domSlug(kind)}", affordable = false))

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
  document.getElementById("tooltip-text").textContent = text
  document.getElementById("tooltip").classList.add("visible")
  document.getElementById("tooltip-destroy").classList.remove("visible")
  document.getElementById("tooltip-upgrade").classList.remove("visible")
  document.getElementById("tooltip-upgrade-preview").classList.remove("visible")

private def selectBuilding(
    kind: BuildingKind,
    active: dom.Element,
    all: List[dom.Element],
    onSelect: BuildingKind => Unit
): Unit =
  onSelect(kind)
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
    if speed.paused then "Paused" else s"${formatDecimal(speed.multiplier)}x"
  document.getElementById("pause-btn").textContent = if speed.paused then "Play" else "Pause"

// Whole numbers print bare ("2"), fractional ones keep their decimal ("0.2") — used for
// the speed multiplier and every per-second rate shown in tooltips/stats, so a sub-1
// rate (e.g. Balance.FirePerSecPerCave) never gets silently truncated to "0" by `.toInt`.
// Rounded to 2 significant digits first so summed rates (count * perUnitRate) never show
// raw floating-point noise (e.g. 0.6000000000000001 from three 0.2 forests).
private def formatDecimal(d: Double): String =
  val rounded = roundToSignificantDigits(d, digits = 2)
  if rounded == rounded.toInt then rounded.toInt.toString else rounded.toString

private def roundToSignificantDigits(value: Double, digits: Int): Double =
  if value == 0.0 then 0.0
  else
    val magnitude = math.pow(10, digits - 1 - math.floor(math.log10(math.abs(value))).toInt)
    math.round(value * magnitude) / magnitude

// ── AI difficulty ladder (see AiStrategy.ladder — same ranking the simulator uses) ──

private def wireAiLevelSelect(initialIndex: Int, onSelect: Int => Unit): Unit =
  val select = document.getElementById("ai-level-select").asInstanceOf[dom.html.Select]
  select.replaceChildren()
  AiStrategy.ladder.zipWithIndex.foreach { case ((name, _), i) =>
    val option = document.createElement("option").asInstanceOf[dom.html.Option]
    option.value = i.toString
    option.text = s"${i + 1}. $name"
    select.appendChild(option)
  }
  select.selectedIndex = initialIndex
  select.addEventListener(
    "change",
    (_: dom.Event) => onSelect(select.selectedIndex)
  )

private def updateAiLevelSelect(index: Int): Unit =
  document.getElementById("ai-level-select").asInstanceOf[dom.html.Select].selectedIndex = index

// ── Spectate/Play mode ──────────────────────────────────────────────────

// Drives the body.mode-spectating CSS hook (hides the build toolbar and the difficulty
// select, shows #spectate-label instead — see index.html) and that label's text. Called
// every tick rather than only on transitions: it's a handful of idempotent DOM writes,
// far cheaper than tracking "did mode just change" separately, and it guarantees the DOM
// can never drift out of sync with `mode`.
private def updateModeUi(mode: Mode): Unit =
  document.body.classList.toggle("mode-spectating", mode.isInstanceOf[Mode.Spectating])
  mode match
    case Mode.Spectating(leftIdx, rightIdx) =>
      val leftName = AiStrategy.ladder(leftIdx)._1
      val rightName = AiStrategy.ladder(rightIdx)._1
      document.getElementById("spectate-label").textContent = s"AI duel: $leftName vs $rightName"
    case Mode.Playing => ()

// ── New game (visible while Spectating — it's how a spectator becomes a player — or,
// once Playing, while paused or once the match has ended) ──────────────

private def wireNewGameButton(onNewGame: () => Unit): Unit =
  document.getElementById("new-game-btn").addEventListener("click", (_: dom.Event) => onNewGame())

private def updateNewGameButtonVisibility(mode: Mode, playingButNotLive: Boolean): Unit =
  val btn = document.getElementById("new-game-btn")
  val visible = mode.isInstanceOf[Mode.Spectating] || playingButNotLive
  if visible then btn.classList.add("visible") else btn.classList.remove("visible")
  btn.textContent = if mode.isInstanceOf[Mode.Spectating] then "Play" else "New Game"

// ── Fullscreen (hides browser chrome — address bar, tab strip — leaving more room for
// the maze; the resizeTo target in setup() reacts automatically once the browser fires
// the resize this triggers, so no extra layout code is needed here) ────────────────────

private def wireFullscreenButton(): Unit =
  val btn = document.getElementById("fullscreen-btn")
  btn.addEventListener("click", (_: dom.Event) => toggleFullscreen())
  document.addEventListener("fullscreenchange", (_: dom.Event) => updateFullscreenLabel())
  updateFullscreenLabel()

private def toggleFullscreen(): Unit =
  if fullscreenElement().isEmpty then
    document.documentElement.asInstanceOf[js.Dynamic].requestFullscreen()
  else document.asInstanceOf[js.Dynamic].exitFullscreen()

private def updateFullscreenLabel(): Unit =
  document.getElementById("fullscreen-btn").textContent =
    if fullscreenElement().isDefined then "Exit Fullscreen" else "Fullscreen"

private def fullscreenElement(): Option[dom.Element] =
  val el = document.asInstanceOf[js.Dynamic].fullscreenElement
  if js.isUndefined(el) || el == null then None else Some(el.asInstanceOf[dom.Element])

private def clearSprites(world: Container, sprites: MazeSprites): Unit =
  (sprites.creatures.values ++ sprites.buildings.values).foreach(world.removeChild)
  sprites.creatures.clear()
  sprites.directionalFacing.clear()
  sprites.buildings.clear()
  sprites.buildingTimers.clear()
  sprites.buildingKinds.clear()

// The player's maze occupies local x/y in [0, GridConfig.width)/[0, GridConfig.height) —
// see currentLayout/computeViewTransform. None outside that (including clicks on the
// AI's maze, which the player never directly interacts with).
private def cellAt(app: Application, e: FederatedPointerEvent): Option[(Int, Int)] =
  val layout = currentLayout(app.screen.width, app.screen.height)
  val vt = computeViewTransform(app.screen.width, app.screen.height, layout)
  val localX = (e.globalX - vt.offsetX) / vt.scale
  val localY = (e.globalY - vt.offsetY) / vt.scale
  if localX < 0 || localX >= GridConfig.width || localY < 0 || localY >= GridConfig.height then None
  else Some(((localX / GridConfig.cellSize).toInt, (localY / GridConfig.cellSize).toInt))

private def handleTap(
    app: Application,
    battle: BattleState,
    e: FederatedPointerEvent,
    choice: BuildingKind
): BattleState =
  if battle.outcome.isDefined then battle
  else
    cellAt(app, e) match
      case None => battle
      case Some((col, row)) =>
        battle.copy(
          player = Placement.tryPlaceBuilding(battle.player, choice, col, row).getOrElse(battle.player)
        )

// The inverse of Demolition.tryDestroy's lookup: which building (if any) occupies a
// cell, as a HoverTarget the tooltip/destroy-button machinery already knows how to show.
private def buildingAt(maze: MazeState, isPlayer: Boolean, col: Int, row: Int): Option[HoverTarget] =
  maze.buildings
    .find(b => b.col == col && b.row == row)
    .map(b => HoverTarget(isPlayer, HoverKind.BuildingH(b.kind), b.id))

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
  // Mirrors aiWorld's own side-by-side/stacked choice above, so the WON/LOST banners
  // (see updateGameOverBanner, index.html's .game-over-side) split the same way.
  document.getElementById("game-container").classList.toggle("layout-portrait", layout.portrait)

// ── Sprite sync (one maze's GameState → its Pixi sprites) ──────────────

private def syncMaze(
    world: Container,
    maze: MazeState,
    sprites: MazeSprites,
    textures: js.Dictionary[Texture],
    goblinFrames: Map[String, js.Array[Texture]],
    elfFrames: Map[String, js.Array[Texture]],
    wolfFrames: js.Array[Texture],
    zombieFrames: js.Array[Texture],
    necromancerFrames: js.Array[Texture],
    soulFrames: js.Array[Texture],
    necromancerSummonFrames: js.Array[Texture],
    flames: js.Array[Texture],
    isPlayer: Boolean,
    setHovered: Option[HoverTarget] => Unit
): Unit =
  val blocked = maze.buildingCells
  syncCreatures(
    world,
    maze,
    sprites,
    textures,
    goblinFrames,
    elfFrames,
    wolfFrames,
    zombieFrames,
    necromancerFrames,
    soulFrames,
    necromancerSummonFrames,
    flames,
    blocked,
    isPlayer,
    setHovered
  )
  syncBuildings(
    world,
    maze,
    sprites,
    textures,
    goblinFrames,
    elfFrames,
    wolfFrames,
    zombieFrames,
    necromancerFrames,
    soulFrames,
    flames,
    isPlayer,
    setHovered
  )

private def syncCreatures(
    world: Container,
    maze: MazeState,
    sprites: MazeSprites,
    textures: js.Dictionary[Texture],
    goblinFrames: Map[String, js.Array[Texture]],
    elfFrames: Map[String, js.Array[Texture]],
    wolfFrames: js.Array[Texture],
    zombieFrames: js.Array[Texture],
    necromancerFrames: js.Array[Texture],
    soulFrames: js.Array[Texture],
    necromancerSummonFrames: js.Array[Texture],
    flames: js.Array[Texture],
    blocked: Set[(Int, Int)],
    isPlayer: Boolean,
    setHovered: Option[HoverTarget] => Unit
): Unit =
  val liveIds = maze.creatures.map(_.id).toSet
  removeStaleWithEffect(world, sprites.creatures, liveIds, flames)
  sprites.directionalFacing.filterInPlace((id, _) => liveIds.contains(id))
  sprites.necromancerSummoning.filterInPlace(liveIds.contains)
  maze.creatures.foreach { c =>
    val g = sprites.creatures.getOrElseUpdate(
      c.id,
      newCreatureSprite(
        world,
        c.kind,
        textures,
        goblinFrames,
        elfFrames,
        wolfFrames,
        zombieFrames,
        necromancerFrames,
        soulFrames,
        HoverTarget(isPlayer, HoverKind.EnemyH, c.id),
        setHovered
      )
    )
    setPos(g, c.pos)
    val angle = creatureFacingAngle(c, blocked)
    c.kind match
      case UnitKind.Necromancer =>
        angle.foreach(a => g.rotation = a)
        applyNecromancerAnimation(sprites, c.id, g, isSummoning = c.frozenMs > 0, necromancerFrames, necromancerSummonFrames)
      case UnitKind.Minotaur | UnitKind.Paladin | UnitKind.Wolf | UnitKind.Vampire | UnitKind.Zombie | UnitKind.Soul =>
        angle.foreach(a => g.rotation = a)
      case UnitKind.Goblin =>
        applyFacing(sprites, c.id, g, angle, goblinFrames)
      case UnitKind.Elf =>
        applyFacing(sprites, c.id, g, angle, elfFrames)
  }

// Swaps the Necromancer's AnimatedSprite between its normal walk cycle and the sheet's
// "Summon" row (Necromancien.md: rooted in place for 1s while it invokes an Ame — see
// Creature.frozenMs), only when that state actually changes — same "avoid resetting the
// animation every tick" guard as applyFacing below.
private def applyNecromancerAnimation(
    sprites: MazeSprites,
    id: Long,
    g: Container,
    isSummoning: Boolean,
    walkFrames: js.Array[Texture],
    summonFrames: js.Array[Texture]
): Unit =
  val wasSummoning = sprites.necromancerSummoning.contains(id)
  if isSummoning != wasSummoning then
    if isSummoning then sprites.necromancerSummoning.add(id) else sprites.necromancerSummoning.remove(id)
    val anim = g.asInstanceOf[AnimatedSprite]
    anim.textures = if isSummoning then summonFrames else walkFrames
    anim.play()

// Swaps a directional creature's AnimatedSprite to the frame set matching its current
// facing, only when the facing actually changes — avoids resetting the walk-cycle
// animation (and the visible stutter that causes) every tick. Shared by Goblin and Elf,
// which differ only in their frame set (10 vs 6 frames per direction — see AssetPaths).
private def applyFacing(
    sprites: MazeSprites,
    id: Long,
    g: Container,
    angle: Option[Double],
    frames: Map[String, js.Array[Texture]]
): Unit =
  angle.map(facingDirection).foreach { dir =>
    if !sprites.directionalFacing.get(id).contains(dir) then
      sprites.directionalFacing(id) = dir
      val anim = g.asInstanceOf[AnimatedSprite]
      anim.textures = frames(dir)
      anim.play()
  }

private def newCreatureSprite(
    world: Container,
    kind: UnitKind,
    textures: js.Dictionary[Texture],
    goblinFrames: Map[String, js.Array[Texture]],
    elfFrames: Map[String, js.Array[Texture]],
    wolfFrames: js.Array[Texture],
    zombieFrames: js.Array[Texture],
    necromancerFrames: js.Array[Texture],
    soulFrames: js.Array[Texture],
    target: HoverTarget,
    setHovered: Option[HoverTarget] => Unit
): Container = kind match
  case UnitKind.Elf =>
    val s = newAnimatedSprite(elfFrames("front"), GridConfig.cellSize * 0.8)
    wireHover(s, target, setHovered)
    addTo(world, s)
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
  case UnitKind.Vampire =>
    // Single static reference image (Vampire.md), same treatment as Minotaur/Paladin —
    // heavier raider than the Zombie (Vampire.md: 50 HP vs 15), a bigger sprite reflects that.
    newHoverSprite(world, textures(AssetPaths.Vampire), GridConfig.cellSize * 1.1, target, setHovered)
  case UnitKind.Goblin =>
    val s = newAnimatedSprite(goblinFrames("front"), GridConfig.cellSize * 0.8)
    wireHover(s, target, setHovered)
    addTo(world, s)
  case UnitKind.Wolf =>
    // Only one facing/frame set (see AssetPaths.Wolf, cropped from the sprite sheet's
    // first row) — rotated to face its movement direction, same as the single-icon
    // Minotaur/Paladin sprites, rather than direction-swapped like the Goblin/Elf's 4 sets.
    val s = newAnimatedSprite(wolfFrames, GridConfig.cellSize * 1.0)
    wireHover(s, target, setHovered)
    addTo(world, s)
  case UnitKind.Zombie =>
    // Single-facing 10-frame walk cycle (see AssetPaths.ZombieFrames) — same
    // rotate-to-face treatment as Wolf, not direction-swapped like Goblin/Elf.
    val s = newAnimatedSprite(zombieFrames, GridConfig.cellSize * 0.8)
    wireHover(s, target, setHovered)
    addTo(world, s)
  case UnitKind.Necromancer =>
    // Single-facing 6-frame walk cycle (see AssetPaths.NecromancerFrames) — same
    // rotate-to-face treatment as Wolf/Zombie.
    val s = newAnimatedSprite(necromancerFrames, GridConfig.cellSize * 0.9)
    wireHover(s, target, setHovered)
    addTo(world, s)
  case UnitKind.Soul =>
    // Single-facing 5-frame walk cycle (see AssetPaths.SoulFrames) — small, floating
    // spectral minion (Ame.md: 10 HP vs the Necromancer's 40), same rotate-to-face
    // treatment as Wolf/Zombie/Necromancer.
    val s = newAnimatedSprite(soulFrames, GridConfig.cellSize * 0.55)
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

private def syncBuildings(
    world: Container,
    maze: MazeState,
    sprites: MazeSprites,
    textures: js.Dictionary[Texture],
    goblinFrames: Map[String, js.Array[Texture]],
    elfFrames: Map[String, js.Array[Texture]],
    wolfFrames: js.Array[Texture],
    zombieFrames: js.Array[Texture],
    necromancerFrames: js.Array[Texture],
    soulFrames: js.Array[Texture],
    flames: js.Array[Texture],
    isPlayer: Boolean,
    setHovered: Option[HoverTarget] => Unit
): Unit =
  val liveIds = maze.buildings.map(_.id).toSet
  removeStaleWithEffect(world, sprites.buildings, liveIds, flames)
  sprites.buildingTimers.filterInPlace((id, _) => liveIds.contains(id))
  sprites.buildingKinds.filterInPlace((id, _) => liveIds.contains(id))
  maze.buildings.foreach { b =>
    val visual = BuildingVisuals.all(b.kind)
    val g = sprites.buildings.getOrElseUpdate(
      b.id,
      newHoverSprite(
        world,
        textures(visual.texturePath),
        visual.renderSize,
        HoverTarget(isPlayer, HoverKind.BuildingH(b.kind), b.id),
        setHovered
      )
    )
    // Re-skin an existing sprite whose building just upgraded (same id/cell, new kind) —
    // getOrElseUpdate above only runs its block for a *new* id, so without this a Grove
    // sprite would keep Grove's texture/size/tint forever even after becoming a Forest.
    if !sprites.buildingKinds.get(b.id).contains(b.kind) then
      sprites.buildingKinds(b.id) = b.kind
      g.texture = textures(visual.texturePath)
      g.width = visual.renderSize
      g.height = visual.renderSize
      g.tint = visual.tint.getOrElse(0xffffff)
    setPos(g, GridConfig.cellCenter(b.col, b.row))
    BuildingSpecs.all(b.kind).spawns.foreach { (unitKind, _) =>
      val previousCountdown = sprites.buildingTimers.getOrElse(b.id, b.spawnCountdownMs)
      sprites.buildingTimers(b.id) = b.spawnCountdownMs
      if hasWrapped(previousCountdown, b.spawnCountdownMs) then
        spawnUnitPreview(
          world,
          Vec2(g.x, g.y),
          unitKind,
          textures,
          goblinFrames,
          elfFrames,
          wolfFrames,
          zombieFrames,
          necromancerFrames,
          soulFrames
        )
    }
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

private def removeStaleWithEffect[T <: Container](
    world: Container,
    sprites: mutable.Map[Long, T],
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

private def creatureFacingAngle(c: Creature, blocked: Set[(Int, Int)]): Option[Double] =
  val currentCell = GridConfig.cellOf(c.pos)
  if currentCell == GridConfig.goalCell then None
  else
    Pathfinding.shortestPath(currentCell, GridConfig.goalCell, blocked).collect {
      case path if path.size > 1 =>
        angleTo(c.pos, GridConfig.cellCenter(path(1)._1, path(1)._2))
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

// A translucent "ghost" of the unit a building just produced, shown briefly at the
// building's own position instead of the generic flame burst (spawnEffect above still
// covers the destroy/death case) — same per-kind texture/frame choice as
// newCreatureSprite, minus its hover wiring, since this is a transient visual cue, not a
// real entity worth inspecting.
private def unitPreviewContainer(
    kind: UnitKind,
    textures: js.Dictionary[Texture],
    goblinFrames: Map[String, js.Array[Texture]],
    elfFrames: Map[String, js.Array[Texture]],
    wolfFrames: js.Array[Texture],
    zombieFrames: js.Array[Texture],
    necromancerFrames: js.Array[Texture],
    soulFrames: js.Array[Texture]
): Container = kind match
  case UnitKind.Elf         => newAnimatedSprite(elfFrames("front"), GridConfig.cellSize * 0.8)
  case UnitKind.Minotaur    => newSprite(textures(AssetPaths.Minotaur), GridConfig.cellSize * 1.1)
  case UnitKind.Paladin     => newSprite(textures(AssetPaths.Paladin), GridConfig.cellSize * 1.0)
  case UnitKind.Vampire     => newSprite(textures(AssetPaths.Vampire), GridConfig.cellSize * 1.1)
  case UnitKind.Goblin      => newAnimatedSprite(goblinFrames("front"), GridConfig.cellSize * 0.8)
  case UnitKind.Wolf        => newAnimatedSprite(wolfFrames, GridConfig.cellSize * 1.0)
  case UnitKind.Zombie      => newAnimatedSprite(zombieFrames, GridConfig.cellSize * 0.8)
  case UnitKind.Necromancer => newAnimatedSprite(necromancerFrames, GridConfig.cellSize * 0.9)
  case UnitKind.Soul        => newAnimatedSprite(soulFrames, GridConfig.cellSize * 0.55)

private def spawnUnitPreview(
    world: Container,
    pos: Vec2,
    kind: UnitKind,
    textures: js.Dictionary[Texture],
    goblinFrames: Map[String, js.Array[Texture]],
    elfFrames: Map[String, js.Array[Texture]],
    wolfFrames: js.Array[Texture],
    zombieFrames: js.Array[Texture],
    necromancerFrames: js.Array[Texture],
    soulFrames: js.Array[Texture]
): Unit =
  val ghost = unitPreviewContainer(
    kind,
    textures,
    goblinFrames,
    elfFrames,
    wolfFrames,
    zombieFrames,
    necromancerFrames,
    soulFrames
  )
  ghost.x = pos.x
  ghost.y = pos.y
  ghost.alpha = 0.5
  world.addChild(ghost)
  dom.window.setTimeout(() => world.removeChild(ghost), UnitPreviewDurationMs)

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
// the tooltip stay accurate. `target` is whatever the caller decided takes priority
// (selectedTarget over hovered) and has already been self-healed — this function only
// renders, it doesn't decide what's showing or clear any state.
// buttonTooltipActive: a build-button hover tooltip is currently showing (see
// wireButtonTooltip) — this function must not clear it, since both hover sources share
// the same #tooltip element and this runs unconditionally every tick.
private def updateTooltip(
    target: Option[HoverTarget],
    battle: BattleState,
    mode: Mode,
    buttonTooltipActive: Boolean
): Unit =
  val tooltip = document.getElementById("tooltip")
  val destroyBtn = document.getElementById("tooltip-destroy").asInstanceOf[dom.html.Button]
  val upgradeBtn = document.getElementById("tooltip-upgrade").asInstanceOf[dom.html.Button]
  val upgradePreview = document.getElementById("tooltip-upgrade-preview")
  val researchBtn = document.getElementById("tooltip-research").asInstanceOf[dom.html.Button]
  target.flatMap(t => hoverText(t, battle).map(text => (t, text))) match
    case Some((target, text)) =>
      document.getElementById("tooltip-text").textContent = text
      tooltip.classList.add("visible")
      val maze = if target.isPlayer then battle.player else battle.ai
      // Both mazes are AI-driven while Spectating (see CLAUDE.md's symmetry rule — the
      // destroy/upgrade/research affordance is a player action, not a game rule, so it's
      // withheld here rather than in destroyInfo/upgradeInfo/researchInfo themselves) —
      // hovering still shows read-only stats, it just can't act on them.
      (if mode == Mode.Playing then destroyInfo(target, maze) else None) match
        case Some((col, row, label)) =>
          destroyBtn.textContent = label
          destroyBtn.setAttribute("data-col", col.toString)
          destroyBtn.setAttribute("data-row", row.toString)
          destroyBtn.classList.add("visible")
        case None => destroyBtn.classList.remove("visible")
      (if mode == Mode.Playing then upgradeInfo(target, maze) else None) match
        case Some((col, row, label, affordable, preview)) =>
          upgradeBtn.textContent = label
          upgradeBtn.setAttribute("data-col", col.toString)
          upgradeBtn.setAttribute("data-row", row.toString)
          upgradeBtn.classList.add("visible")
          if affordable then upgradeBtn.classList.remove("disabled") else upgradeBtn.classList.add("disabled")
          upgradePreview.textContent = s"→ $preview"
          upgradePreview.classList.add("visible")
        case None =>
          upgradeBtn.classList.remove("visible")
          upgradePreview.classList.remove("visible")
      (if mode == Mode.Playing then researchInfo(target, maze) else None) match
        case Some((col, row, label, affordable)) =>
          researchBtn.textContent = label
          researchBtn.setAttribute("data-col", col.toString)
          researchBtn.setAttribute("data-row", row.toString)
          researchBtn.classList.add("visible")
          if affordable then researchBtn.classList.remove("disabled") else researchBtn.classList.add("disabled")
        case None => researchBtn.classList.remove("visible")
    case None =>
      if !buttonTooltipActive then
        tooltip.classList.remove("visible")
        destroyBtn.classList.remove("visible")
        upgradeBtn.classList.remove("visible")
        upgradePreview.classList.remove("visible")
        researchBtn.classList.remove("visible")

// Only the player's own buildings are destroyable from the UI (the AI destroying its own
// is driven by AiStrategy.maybeDestroy instead, not this hover affordance) — see
// Demolition for the refund amounts shown in the button label. Fully generic over
// BuildingSpecs' cost map, unlike hoverText's building branch below (which keeps
// hand-written per-kind ability text, per the refactor's confirmed scope).
private def destroyInfo(target: HoverTarget, maze: MazeState): Option[(Int, Int, String)] =
  if !target.isPlayer then None
  else
    target.kind match
      case HoverKind.EnemyH => None
      // b.kind, not the pattern-matched kind: HoverKind.BuildingH's kind is frozen at the
      // moment the tooltip was opened, but Placement.tryUpgradeBuilding can change a
      // selected building's kind in place afterward (same id, same cell) — reading the
      // live building avoids showing a stale cost after an upgrade.
      case HoverKind.BuildingH(_) =>
        maze.buildings.find(_.id == target.id).map { b =>
          val cost = BuildingSpecs.all(b.kind).cost
          val refundText = cost.toList
            .sortBy(_._1.ordinal)
            .map { case (res, amount) =>
              s"+${formatDecimal(amount * Balance.DemolishRefundFraction)} ${resourceName(res)}"
            }
            .mkString(", ")
          (b.col, b.row, s"Destroy ($refundText)")
        }

private def wireDestroyButton(onDestroy: (Int, Int) => Unit): Unit =
  val btn = document.getElementById("tooltip-destroy").asInstanceOf[dom.html.Button]
  btn.addEventListener(
    "click",
    (_: dom.Event) => {
      val col = btn.getAttribute("data-col")
      val row = btn.getAttribute("data-row")
      if col != null && row != null then onDestroy(col.toInt, row.toInt)
    }
  )

private def destroyPlayerBuilding(battle: BattleState, mode: Mode, col: Int, row: Int): BattleState =
  if mode != Mode.Playing || battle.outcome.isDefined then battle
  else Demolition.tryDestroy(battle.player, col, row).map(p => battle.copy(player = p)).getOrElse(battle)

// Only the player's own Grove/Forest can be upgraded from the UI (the AI upgrades via
// AiStrategy.maybeUpgrade instead) — mirrors destroyInfo's shape/wiring exactly. The
// affordable flag drives the button's disabled look (see updateTooltip) — Placement.
// tryUpgradeBuilding already rejects an unaffordable upgrade server-side regardless, this
// is purely the same visual affordance the build-<slug> buttons get from canAfford. The
// preview string is the *next* tier's own hover text (see buildingHoverText) computed
// against a synthetic just-upgraded Building, so players see what they're buying before
// they click — reusing Placement.upgradeBuilding's own countdown-reset rule
// (spec.spawns.map(_._2).getOrElse(0.0)) so "next Elf in Xs" previews the real value.
private def upgradeInfo(target: HoverTarget, maze: MazeState): Option[(Int, Int, String, Boolean, String)] =
  if !target.isPlayer then None
  else
    target.kind match
      case HoverKind.EnemyH => None
      // b.kind, not the pattern-matched kind — see destroyInfo's comment on the same issue.
      case HoverKind.BuildingH(_) =>
        maze.buildings.find(_.id == target.id).flatMap { b =>
          BuildingSpecs.upgradesTo.get(b.kind).map { nextKind =>
            val nextSpec = BuildingSpecs.all(nextKind)
            val costText = nextSpec.cost.toList
              .sortBy(_._1.ordinal)
              .map { case (res, amount) => s"${formatDecimal(amount)} ${resourceName(res)}" }
              .mkString(", ")
            val previewCountdown = nextSpec.spawns.map(_._2).getOrElse(0.0)
            val preview =
              buildingHoverText(nextKind, b.copy(kind = nextKind, spawnCountdownMs = previewCountdown), maze)
            (
              b.col,
              b.row,
              s"Upgrade to $nextKind ($costText)",
              Placement.canAfford(maze.resources, nextSpec.cost),
              preview
            )
          }
        }

private def wireUpgradeButton(onUpgrade: (Int, Int) => Unit): Unit =
  val btn = document.getElementById("tooltip-upgrade").asInstanceOf[dom.html.Button]
  btn.addEventListener(
    "click",
    (_: dom.Event) => {
      val col = btn.getAttribute("data-col")
      val row = btn.getAttribute("data-row")
      if col != null && row != null && !btn.classList.contains("disabled") then onUpgrade(col.toInt, row.toInt)
    }
  )

private def upgradePlayerBuilding(battle: BattleState, mode: Mode, col: Int, row: Int): BattleState =
  if mode != Mode.Playing || battle.outcome.isDefined then battle
  else Placement.tryUpgradeBuilding(battle.player, col, row).map(p => battle.copy(player = p)).getOrElse(battle)

// Only the player's own Science lab can be researched from the UI (the AI researches via
// AiStrategy.maybeResearch instead) — mirrors upgradeInfo's shape/wiring, but keyed by
// BuildingKind rather than cell/id: ResearchSpecs.all has no entry for non-lab kinds, and
// Placement.tryResearch itself operates on "the maze's one building of this kind" (labs
// are capped at 1 — see BuildingSpecs' maxPerMaze), not a specific building instance.
// None once maxed (Balance.MaxResearchLevel), same as upgradeInfo's None once there's no
// further tier.
private def researchInfo(target: HoverTarget, maze: MazeState): Option[(Int, Int, String, Boolean)] =
  if !target.isPlayer then None
  else
    target.kind match
      case HoverKind.EnemyH => None
      // b.kind, not the pattern-matched kind — see destroyInfo's comment on the same issue.
      case HoverKind.BuildingH(_) =>
        maze.buildings.find(_.id == target.id).flatMap { b =>
          ResearchSpecs.all.get(b.kind).flatMap { spec =>
            val currentLevel = maze.researchLevels.getOrElse(b.kind, 0)
            if currentLevel >= Balance.MaxResearchLevel then None
            else
              val nextLevel = currentLevel + 1
              val cost = spec.costAtLevel(nextLevel)
              val costText = cost.toList
                .sortBy(_._1.ordinal)
                .map { case (res, amount) => s"${formatDecimal(amount)} ${resourceName(res)}" }
                .mkString(", ")
              Some(
                (
                  b.col,
                  b.row,
                  s"Research level $nextLevel/${Balance.MaxResearchLevel} ($costText)",
                  Placement.canAfford(maze.resources, cost)
                )
              )
          }
        }

private def wireResearchButton(onResearch: (Int, Int) => Unit): Unit =
  val btn = document.getElementById("tooltip-research").asInstanceOf[dom.html.Button]
  btn.addEventListener(
    "click",
    (_: dom.Event) => {
      val col = btn.getAttribute("data-col")
      val row = btn.getAttribute("data-row")
      if col != null && row != null && !btn.classList.contains("disabled") then onResearch(col.toInt, row.toInt)
    }
  )

private def researchPlayerBuilding(battle: BattleState, mode: Mode, col: Int, row: Int): BattleState =
  if mode != Mode.Playing || battle.outcome.isDefined then battle
  else
    battle.player.buildings.find(b => b.col == col && b.row == row) match
      case None => battle
      case Some(b) =>
        Placement.tryResearch(battle.player, b.kind).map(p => battle.copy(player = p)).getOrElse(battle)

private def hoverText(target: HoverTarget, battle: BattleState): Option[String] =
  val maze = if target.isPlayer then battle.player else battle.ai
  target.kind match
    case HoverKind.EnemyH =>
      maze.creatures.find(_.id == target.id).map { c =>
        c.kind match
          case UnitKind.Paladin =>
            s"Paladin — HP ${c.hp.toInt}/${c.maxHp.toInt}, doesn't plunder; shields adjacent allies " +
              s"from ${Balance.PaladinAuraDamageReductionPerSec.toInt} dmg/s"
          case UnitKind.Wolf =>
            s"Wolf — HP ${c.hp.toInt}/${c.maxHp.toInt}, doesn't plunder; speeds up allies within " +
              s"${Balance.WolfSpeedAuraRangeCells} cells by ${((Balance.WolfSpeedAuraMultiplier - 1) * 100).toInt}%"
          case UnitKind.Zombie =>
            s"Zombie — HP ${c.hp.toInt}/${c.maxHp.toInt}, doesn't plunder; corrupts adjacent enemy " +
              s"buildings by ${Balance.ZombieCorruptionPercentPerSec.toInt}%/s"
          case UnitKind.Vampire =>
            s"Vampire — HP ${c.hp.toInt}/${c.maxHp.toInt}, doesn't plunder; corrupts adjacent enemy " +
              s"buildings by ${Balance.VampireCorruptionPercentPerSec.toInt}%/s, takes " +
              s"${(Balance.VampireDamageReductionFraction * 100).toInt}% less damage (not shielded by Paladins)"
          case UnitKind.Necromancer =>
            s"Necromancien — HP ${c.hp.toInt}/${c.maxHp.toInt}, doesn't plunder; invokes an Ame every " +
              s"${(Balance.SoulSummonIntervalMs / 1000).toInt}s"
          case UnitKind.Soul =>
            s"Ame — HP ${c.hp.toInt}/${c.maxHp.toInt}, doesn't plunder; corrupts adjacent enemy buildings " +
              s"by ${Balance.SoulCorruptionPercentPerSec.toInt}%/s, healing its Necromancien " +
              s"${Balance.SoulHealPerSecPerBuilding.toInt} HP/s per building corrupted"
          case _ =>
            val (name, plunders) = c.kind match
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
            s"$name — HP ${c.hp.toInt}/${c.maxHp.toInt}, plunders $plunders on arrival"
      }
    // b.kind, not the pattern-matched kind — see destroyInfo's comment on the same issue.
    case HoverKind.BuildingH(_) =>
      maze.buildings.find(_.id == target.id).map(b => buildingHoverText(b.kind, b, maze))

// Kept as hand-written per-kind text (not derived from BuildingSpecs), same as the
// tooltip constants above — the ability sentences (Forest's aura, Watchtower's ranged
// damage) describe combat behavior that lives outside BuildingSpec by design (see the
// refactor's confirmed scope), so there's nothing generic left to derive them from.
// `maze` is needed (not just `b`) so every "+X resource/s" line can show the *live*
// Engendre-boosted rate (see effectiveRate) rather than the flat Balance constant, which
// understates production once any Engendre-source building exists on the board.
// corruptionSuffix is appended after the per-kind text below (see its doc) — every
// building kind is a fair corruption target (Corruption.md gives no kind restriction),
// so it isn't part of the per-kind match itself.
private def buildingHoverText(kind: BuildingKind, b: Building, maze: MazeState): String =
  perKindHoverText(kind, b, maze) + corruptionSuffix(b)

// Zombie/Vampire corrupt buildings gradually toward Balance.CorruptionMaxPercent (see
// CombatEngine's corruption mechanic) — shown only once corruption has actually started,
// so an untouched building's tooltip stays exactly as it read before Death existed.
private def corruptionSuffix(b: Building): String =
  if b.corruptionPercent > 0 then f" — corrupted ${b.corruptionPercent}%.0f%%/${Balance.CorruptionMaxPercent.toInt}%%"
  else ""

// This one kind's live per-second rate, including whatever Engendre boost `maze`'s other
// buildings currently grant it (CombatEngine.engendreBoost — same multiplier
// CombatEngine.productionPerSec itself applies, so this can never silently drift from
// the actual number the top summary panel and the real tick both use).
private def effectiveRate(maze: MazeState, kind: BuildingKind, resource: Resource): Double =
  BuildingSpecs.all(kind).produces.getOrElse(resource, 0.0) *
    CombatEngine.researchProductionMultiplier(maze, kind, resource) *
    (1.0 + CombatEngine.engendreBoost(maze, resource))

private def perKindHoverText(kind: BuildingKind, b: Building, maze: MazeState): String = kind match
  case BuildingKind.Grove =>
    val nextElfS = (b.spawnCountdownMs / 1000).ceil.toInt
    s"Bosquet — +${formatDecimal(effectiveRate(maze, kind, Resource.Wood))} wood/s, next Elf in ${nextElfS}s"
  case BuildingKind.Forest =>
    val nextElfS = (b.spawnCountdownMs / 1000).ceil.toInt
    s"Forêt — ${Balance.AuraDamagePerSec.toInt} dmg/s to adjacent enemies, +${formatDecimal(effectiveRate(maze, kind, Resource.Wood))} wood/s, next Elf in ${nextElfS}s"
  case BuildingKind.Jungle =>
    val nextWolfS = (b.spawnCountdownMs / 1000).ceil.toInt
    s"Jungle — ${Balance.AuraDamagePerSec.toInt} dmg/s to adjacent enemies, +${formatDecimal(effectiveRate(maze, kind, Resource.Wood))} wood/s, next Wolf in ${nextWolfS}s"
  case BuildingKind.Cave =>
    val nextGoblinS = (b.spawnCountdownMs / 1000).ceil.toInt
    s"Cave — +${formatDecimal(effectiveRate(maze, kind, Resource.Fire))} fire/s, next Goblin in ${nextGoblinS}s"
  case BuildingKind.Labyrinth =>
    val nextMinotaurS = (b.spawnCountdownMs / 1000).ceil.toInt
    s"Labyrinthe — next Minotaur in ${nextMinotaurS}s"
  case BuildingKind.Church =>
    val nextPaladinS = (b.spawnCountdownMs / 1000).ceil.toInt
    s"Eglise — +${formatDecimal(effectiveRate(maze, kind, Resource.Light))} light/s, next Paladin in ${nextPaladinS}s"
  case BuildingKind.Watchtower =>
    s"Tour de guet — +${formatDecimal(effectiveRate(maze, kind, Resource.Light))} light/s, " +
      s"${Balance.WatchtowerDamagePerSec.toInt} dmg/s to the nearest enemy within ${Balance.WatchtowerRangeCells} cells"
  case BuildingKind.Angel =>
    s"Ange — +${formatDecimal(effectiveRate(maze, kind, Resource.Light))} light/s, " +
      s"${Balance.AngelDamagePerSec.toInt} dmg/s to adjacent enemies, slows them ${(Balance.AngelSlowFraction * 100).toInt}%"
  case BuildingKind.Tomb =>
    val nextZombieS = (b.spawnCountdownMs / 1000).ceil.toInt
    s"Tombe — +${formatDecimal(effectiveRate(maze, kind, Resource.Shadow))} shadow/s, next Zombie in ${nextZombieS}s"
  case BuildingKind.BlackCastle =>
    val nextVampireS = (b.spawnCountdownMs / 1000).ceil.toInt
    s"Château Noir — +${formatDecimal(effectiveRate(maze, kind, Resource.Shadow))} shadow/s, next Vampire in ${nextVampireS}s"
  case BuildingKind.DeathHouse =>
    val nextNecromancerS = (b.spawnCountdownMs / 1000).ceil.toInt
    s"Maison de la Mort — +${formatDecimal(effectiveRate(maze, kind, Resource.Shadow))} shadow/s, " +
      s"next Necromancer in ${nextNecromancerS}s"
  case BuildingKind.LaboNaturel =>
    s"Labo Naturel — +${formatDecimal(effectiveRate(maze, kind, Resource.Crystal))} crystal/s"
  case BuildingKind.LaboSombre =>
    s"Labo Sombre — +${formatDecimal(effectiveRate(maze, kind, Resource.Crystal))} crystal/s"
  case BuildingKind.LaboDeRecherche =>
    s"Labo de Recherche — +${formatDecimal(effectiveRate(maze, kind, Resource.Crystal))} crystal/s"
  case BuildingKind.LaboDeLaLoi =>
    s"Labo de la Loi — +${formatDecimal(effectiveRate(maze, kind, Resource.Crystal))} crystal/s"
  case BuildingKind.LaboDuChaos =>
    s"Labo du Chaos — +${formatDecimal(effectiveRate(maze, kind, Resource.Crystal))} crystal/s"

// ── HTML overlay ────────────────────────────────────────────────────────

private def updateOverlay(battle: BattleState): Unit =
  updateMazePanel("player", battle.player, opponent = battle.ai)
  updateMazePanel("ai", battle.ai, opponent = battle.player)
  updateGameOverBanner(battle)

// Same fields for both panels — the game is symmetric, either side may hold either
// resource or be chasing either victory condition (see CLAUDE.md). Targets are shown
// live since they track the opponent's own count (see VictoryConditions).
private def updateMazePanel(prefix: String, maze: MazeState, opponent: MazeState): Unit =
  document.getElementById(s"$prefix-wood").textContent = maze.resources.getOrElse(Resource.Wood, 0.0).toInt.toString
  document.getElementById(s"$prefix-fire").textContent = maze.resources.getOrElse(Resource.Fire, 0.0).toInt.toString
  document.getElementById(s"$prefix-light").textContent =
    maze.resources.getOrElse(Resource.Light, 0.0).toInt.toString
  document.getElementById(s"$prefix-shadow").textContent =
    maze.resources.getOrElse(Resource.Shadow, 0.0).toInt.toString
  document.getElementById(s"$prefix-crystal").textContent =
    maze.resources.getOrElse(Resource.Crystal, 0.0).toInt.toString
  // Same CombatEngine function that actually applies production each tick — see its
  // doc for why (a hand-rolled `count * rate` here could silently drift out of sync).
  document.getElementById(s"$prefix-wood-rate").textContent =
    s"(+${formatDecimal(CombatEngine.productionPerSec(maze, Resource.Wood))}/s)"
  document.getElementById(s"$prefix-fire-rate").textContent =
    s"(+${formatDecimal(CombatEngine.productionPerSec(maze, Resource.Fire))}/s)"
  document.getElementById(s"$prefix-light-rate").textContent =
    s"(+${formatDecimal(CombatEngine.productionPerSec(maze, Resource.Light))}/s)"
  document.getElementById(s"$prefix-shadow-rate").textContent =
    s"(+${formatDecimal(CombatEngine.productionPerSec(maze, Resource.Shadow))}/s)"
  document.getElementById(s"$prefix-crystal-rate").textContent =
    s"(+${formatDecimal(CombatEngine.productionPerSec(maze, Resource.Crystal))}/s)"
  // Only Forest/Jungle count as real forests (see VictoryConditions.realForestKinds) — a
  // Grove is still just a bush (Bosquet.md's own asset), not yet a forest.
  val realForestKinds = Set(BuildingKind.Forest, BuildingKind.Jungle)
  val forestCount = maze.buildings.count(b => realForestKinds.contains(b.kind))
  val forestTarget = VictoryConditions.forestTarget(opponent)
  val plunderTarget = VictoryConditions.plunderTarget(opponent)
  val corruptionTarget = VictoryConditions.corruptionTarget(opponent)
  document.getElementById(s"$prefix-forests").textContent = s"$forestCount/${forestTarget.toInt}"
  document.getElementById(s"$prefix-plundered").textContent =
    s"${maze.resourcesPlundered.toInt}/${plunderTarget.toInt}"
  document.getElementById(s"$prefix-corrupted").textContent =
    s"${maze.buildingsCorrupted.toInt}/${corruptionTarget.toInt}"
  updateProgressBar(s"$prefix-forests-bar", forestCount, forestTarget)
  updateProgressBar(s"$prefix-plundered-bar", maze.resourcesPlundered, plunderTarget)
  updateProgressBar(s"$prefix-corrupted-bar", maze.buildingsCorrupted, corruptionTarget)

// Visual companion to the "current/target" text above — lets you compare at a glance
// how close each maze is to winning via the same (opponent-relative) condition.
private def updateProgressBar(id: String, current: Double, target: Double): Unit =
  val pct = if target <= 0 then 100.0 else math.min(100.0, current / target * 100.0)
  document.getElementById(id).asInstanceOf[dom.html.Element].style.width = s"$pct%"

// One WON/LOST banner per maze half (not a single "You win!"/"AI wins!" overlay) —
// each stays confined to pointer-events: none (see index.html's .game-over-side), so
// buildings on either side stay hoverable for their info tooltip after the match ends.
private def updateGameOverBanner(battle: BattleState): Unit =
  battle.outcome match
    case Some(result) =>
      val playerWon = result.isInstanceOf[MatchResult.PlayerWins]
      setGameOverSide("player", won = playerWon, result.reason)
      setGameOverSide("ai", won = !playerWon, result.reason)
    case None =>
      document.getElementById("game-over-player").classList.remove("visible")
      document.getElementById("game-over-ai").classList.remove("visible")

private def setGameOverSide(prefix: String, won: Boolean, reason: String): Unit =
  val title = document.getElementById(s"game-over-$prefix-title")
  title.textContent = if won then "WON" else "LOST"
  title.classList.remove("won")
  title.classList.remove("lost")
  title.classList.add(if won then "won" else "lost")
  document.getElementById(s"game-over-$prefix-reason").textContent = reason
  document.getElementById(s"game-over-$prefix").classList.add("visible")
