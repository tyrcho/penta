package towerdefense.domain

class PathfindingTest extends munit.FunSuite:

  test("start equals goal returns a single-cell path") {
    assertEquals(Pathfinding.shortestPath((3, 3), (3, 3), Set.empty), Some(List((3, 3))))
  }

  test("finds a straight shortest path with no obstacles") {
    val path = Pathfinding.shortestPath((0, 0), (2, 0), Set.empty)
    assertEquals(path, Some(List((0, 0), (1, 0), (2, 0))))
  }

  test("routes around a blocked cell") {
    val path = Pathfinding.shortestPath((0, 0), (2, 0), Set((1, 0)))
    assert(path.isDefined)
    assert(!path.get.contains((1, 0)))
    assertEquals(path.get.head, (0, 0))
    assertEquals(path.get.last, (2, 0))
  }

  test("returns None when every route is blocked") {
    val blocked = Pathfinding.neighbors((0, 0)).toSet
    assertEquals(Pathfinding.shortestPath((0, 0), (5, 5), blocked), None)
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
    do Pathfinding.shortestPath((0, 0), (GridConfig.cols - 1, row), Set.empty)
    val elapsedMs = (System.nanoTime() - start) / 1_000_000
    assert(elapsedMs < 5_000, s"expected under 5s, took ${elapsedMs}ms")
  }
