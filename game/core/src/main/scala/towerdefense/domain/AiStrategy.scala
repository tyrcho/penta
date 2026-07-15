package towerdefense.domain

// A build-decision maker for one maze. Side-agnostic by design (see CLAUDE.md's
// symmetry rule): anything that can drive the `ai` slot in BattleState must be equally
// usable to drive the `player` slot, so this takes both mazes as plain MazeState and
// returns the (possibly unchanged) state for the side it's deciding for. `opponent` is
// unused by strategies that don't react to the other side (LinearStrategy).
trait AiStrategy:
  def maybeBuild(state: MazeState, opponent: MazeState): MazeState

  // Default no-op: every strategy shipped so far only ever adds buildings, never tears
  // one down. A future strategy could override this to reshape its own maze (e.g.
  // relocating a Forest into a better chokepoint — see CompositeStrategy's dangerScore),
  // using Demolition. Symmetric with maybeBuild and driven the same way by
  // BattleEngine.tick; unlike maybeBuild it isn't cooldown-throttled, since Demolition
  // itself has none either (see Demolition's doc).
  def maybeDestroy(state: MazeState, opponent: MazeState): MazeState = state

object AiStrategy:
  // Ordered weakest to strongest by measured head-to-head win rate (sim/run round-robin,
  // 15-20 matches per pairing). Re-measured after CompositeStrategy's maze component
  // started weighting aura-damage exposure (dangerScore), not just raw path length:
  // maze-only jumped from a middling tier to the outright strongest, beating linear,
  // resource-only, AND counter-only 15-0 each, since it now routes the enemy path past
  // Forests to kill units instead of merely delaying them — beating an opponent to death
  // prevents its plunder outright, which raw path length never could. It only stalemates
  // balanced, which inherits the same routing logic as one of its three components.
  // counter-only lost outright to both resource-only and maze-only; resource-only lost
  // outright to maze-only. Drives both the simulator's named presets (`all`) and the
  // browser's difficulty selector / auto-advance-on-win, so both walk this measured order.
  val ladder: Seq[(String, AiStrategy)] = Seq(
    "linear" -> LinearStrategy,
    "counter-only" -> CompositeStrategy(Weights(resource = 0.0, counter = 1.0, maze = 0.0)),
    "resource-only" -> CompositeStrategy(Weights(resource = 1.0, counter = 0.0, maze = 0.0)),
    "balanced" -> CompositeStrategy(Weights(resource = 1.0, counter = 1.0, maze = 1.0)),
    "maze-only" -> CompositeStrategy(Weights(resource = 0.0, counter = 0.0, maze = 1.0))
  )

  val all: Map[String, AiStrategy] = ladder.toMap
