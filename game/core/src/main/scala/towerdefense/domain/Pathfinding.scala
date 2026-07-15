package towerdefense.domain

import scala.collection.mutable

// Grid BFS: shortest route (in cells) from start to goal, treating tower
// cells as obstacles. Recomputed fresh every tick — no cached/stale paths.
object Pathfinding:
  private val neighborOffsets = List((1, 0), (-1, 0), (0, 1), (0, -1))

  def shortestPath(
      start: (Int, Int),
      goal: (Int, Int),
      blocked: Set[(Int, Int)]
  ): Option[List[(Int, Int)]] =
    if start == goal then Some(List(start))
    else bfs(start, goal, blocked)

  def isReachable(start: (Int, Int), goal: (Int, Int), blocked: Set[(Int, Int)]): Boolean =
    shortestPath(start, goal, blocked).isDefined

  // Tracks one predecessor per visited cell instead of growing a full path per queue
  // entry — reconstructing via predecessors at the end is O(path length), whereas
  // appending to a List with `:+` at every step made each call O(cells visited squared).
  private def bfs(
      start: (Int, Int),
      goal: (Int, Int),
      blocked: Set[(Int, Int)]
  ): Option[List[(Int, Int)]] =
    val visited = mutable.Set(start)
    val queue = mutable.Queue(start)
    val predecessor = mutable.Map.empty[(Int, Int), (Int, Int)]
    var found = false
    while queue.nonEmpty && !found do
      val cell = queue.dequeue()
      neighbors(cell).filterNot(visited).foreach { next =>
        visited += next
        predecessor(next) = cell
        if next == goal then found = true
        else if !blocked(next) then queue.enqueue(next)
      }
    if !found then None else Some(reconstructPath(start, goal, predecessor))

  private def reconstructPath(
      start: (Int, Int),
      goal: (Int, Int),
      predecessor: mutable.Map[(Int, Int), (Int, Int)]
  ): List[(Int, Int)] =
    // Walk backwards from goal to start following predecessors, then reverse once —
    // O(path length) total, instead of appending to a List (O(n) each) along the way.
    Iterator.iterate(goal)(predecessor).takeWhile(_ != start).toList.reverse.prepended(start)

  def neighbors(cell: (Int, Int)): List[(Int, Int)] =
    val (col, row) = cell
    neighborOffsets
      .map { case (dx, dy) => (col + dx, row + dy) }
      .filter(GridConfig.isInBounds.tupled)
