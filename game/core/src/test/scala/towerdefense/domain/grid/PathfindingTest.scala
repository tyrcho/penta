package towerdefense.domain.grid

import towerdefense.domain.*

class PathfindingTest extends munit.FunSuite:

  test("start equals goal returns a single-cell path") {
    assertEquals(Pathfinding.shortestPath(Pos(3, 3), Pos(3, 3), Set.empty), Some(List(Pos(3, 3))))
  }

  test("finds a straight shortest path with no obstacles") {
    val path = Pathfinding.shortestPath(Pos(0, 0), Pos(2, 0), Set.empty)
    assertEquals(path, Some(List(Pos(0, 0), Pos(1, 0), Pos(2, 0))))
  }

  test("routes around a blocked cell") {
    val path = Pathfinding.shortestPath(Pos(0, 0), Pos(2, 0), Set(Pos(1, 0)))
    assert(path.isDefined)
    assert(!path.get.contains(Pos(1, 0)))
    assertEquals(path.get.head, Pos(0, 0))
    assertEquals(path.get.last, Pos(2, 0))
  }

  test("returns None when every route is blocked") {
    val blocked = Pathfinding.neighbors(Pos(0, 0)).toSet
    assertEquals(Pathfinding.shortestPath(Pos(0, 0), Pos(5, 5), blocked), None)
  }

  // Regression guard: BFS must stay linear in the number of visited cells. A previous
  // implementation rebuilt each path with List's O(n) `:+`, making one call ~O(cells^2)
  // and a simulator running thousands of these per tick unusably slow (multi-minute
  // hangs on a 12x12 grid). Many calls across the full grid must stay fast.
  test("many repeated shortest-path calls across the grid complete quickly") {
    val start = System.nanoTime()
    for
      _ <- 0 until 2000
      row <- 0 until GridConfig.rows
    do Pathfinding.shortestPath(Pos(0, 0), Pos(GridConfig.cols - 1, row), Set.empty)
    val elapsedMs = (System.nanoTime() - start) / 1_000_000
    assert(elapsedMs < 5_000, s"expected under 5s, took ${elapsedMs}ms")
  }
