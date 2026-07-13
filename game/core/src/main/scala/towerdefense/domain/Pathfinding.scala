package towerdefense.domain

import scala.collection.mutable

// Grid BFS: shortest route (in cells) from start to goal, treating tower
// cells as obstacles. Recomputed fresh every tick — no cached/stale paths.
object Pathfinding:
  private val neighborOffsets = List((1, 0), (-1, 0), (0, 1), (0, -1))

  def shortestPath(start: (Int, Int), goal: (Int, Int), blocked: Set[(Int, Int)]): Option[List[(Int, Int)]] =
    if start == goal then Some(List(start))
    else bfs(start, goal, blocked)

  def isReachable(start: (Int, Int), goal: (Int, Int), blocked: Set[(Int, Int)]): Boolean =
    shortestPath(start, goal, blocked).isDefined

  private def bfs(start: (Int, Int), goal: (Int, Int), blocked: Set[(Int, Int)]): Option[List[(Int, Int)]] =
    val visited = mutable.Set(start)
    val queue = mutable.Queue((start, List(start)))
    var found: Option[List[(Int, Int)]] = None
    while queue.nonEmpty && found.isEmpty do
      val (cell, path) = queue.dequeue()
      neighbors(cell).filterNot(visited).foreach { next =>
        visited += next
        val nextPath = path :+ next
        if next == goal then found = Some(nextPath)
        else if !blocked(next) then queue.enqueue((next, nextPath))
      }
    found

  def neighbors(cell: (Int, Int)): List[(Int, Int)] =
    val (col, row) = cell
    neighborOffsets
      .map { case (dx, dy) => (col + dx, row + dy) }
      .filter(GridConfig.isInBounds.tupled)
