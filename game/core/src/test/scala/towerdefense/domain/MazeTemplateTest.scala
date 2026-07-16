package towerdefense.domain

// Approval tests: the ASCII renders below are the human-reviewed source of truth for
// what each template actually looks like on the game's real 12x12 grid — diff the
// render, not the wall coordinates, if a future change alters the shape. Each template
// is also checked structurally (reachable, and forces a materially longer path than the
// open board) so a shape change that breaks the maze fails even if nobody re-reads the
// picture.
class MazeTemplateTest extends munit.FunSuite:

  private val cols = GridConfig.cols
  private val rows = GridConfig.rows
  private val spawn = GridConfig.spawnCell
  private val goal = GridConfig.goalCell

  private val openBoardLength =
    Pathfinding.shortestPath(spawn, goal, Set.empty).map(_.length).get

  test("comb: approved shape") {
    val walls = MazeTemplate.comb(cols, rows)
    val approved =
      """S...........
        |xxxxxxxxxxx.
        |............
        |.xxxxxxxxxxx
        |............
        |xxxxxxxxxxx.
        |............
        |.xxxxxxxxxxx
        |............
        |xxxxxxxxxxx.
        |............
        |...........G""".stripMargin
    assertEquals(MazeTemplate.render(cols, rows, walls, spawn, goal), approved)
  }

  test("comb: fully built maze is reachable and forces a much longer path") {
    val walls = MazeTemplate.comb(cols, rows).toSet
    assert(Pathfinding.isReachable(spawn, goal, walls))
    val length = Pathfinding.shortestPath(spawn, goal, walls).map(_.length).get
    assert(length > openBoardLength * 2, s"expected a much longer path than $openBoardLength, got $length")
  }

  test("combVertical: approved shape") {
    val walls = MazeTemplate.combVertical(cols, rows)
    val approved =
      """Sx...x...x..
        |.x.x.x.x.x..
        |.x.x.x.x.x..
        |.x.x.x.x.x..
        |.x.x.x.x.x..
        |.x.x.x.x.x..
        |.x.x.x.x.x..
        |.x.x.x.x.x..
        |.x.x.x.x.x..
        |.x.x.x.x.x..
        |.x.x.x.x.x..
        |...x...x...G""".stripMargin
    assertEquals(MazeTemplate.render(cols, rows, walls, spawn, goal), approved)
  }

  test("combVertical: fully built maze is reachable and forces a much longer path") {
    val walls = MazeTemplate.combVertical(cols, rows).toSet
    assert(Pathfinding.isReachable(spawn, goal, walls))
    val length = Pathfinding.shortestPath(spawn, goal, walls).map(_.length).get
    assert(length > openBoardLength * 2, s"expected a much longer path than $openBoardLength, got $length")
  }

  test("neither template ever touches spawn or goal") {
    assert(!MazeTemplate.comb(cols, rows).contains(spawn))
    assert(!MazeTemplate.comb(cols, rows).contains(goal))
    assert(!MazeTemplate.combVertical(cols, rows).contains(spawn))
    assert(!MazeTemplate.combVertical(cols, rows).contains(goal))
  }

  test("comb orders cells row-major: an earlier tooth row's cells all come before a later one's") {
    val rowOf = MazeTemplate.comb(cols, rows).map(_._2)
    assertEquals(rowOf, rowOf.sorted, "comb's cells must already be sorted by row")
  }

  test("combVertical orders cells column-major: an earlier tooth column's cells all come first") {
    val colOf = MazeTemplate.combVertical(cols, rows).map(_._1)
    assertEquals(colOf, colOf.sorted, "combVertical's cells must already be sorted by column")
  }

  // Building walls only ever removes passable cells, so a fully-connected end state
  // proves every partial subset is connected too (fewer walls built = strictly more open
  // cells than the end state) — this is what makes it safe for TemplateStrategy to build
  // template cells in any order, one per tick, without ever tripping Placement's
  // WouldBlockPath check. Exercised directly here instead of just asserted in a comment.
  test("every subset of a reachable template's walls is also reachable") {
    val allWalls = MazeTemplate.comb(cols, rows).toList
    val rng = new scala.util.Random(42)
    for _ <- 1 to 20 do
      val subset = rng.shuffle(allWalls).take(rng.nextInt(allWalls.size + 1)).toSet
      assert(
        Pathfinding.isReachable(spawn, goal, subset),
        s"subset of size ${subset.size} disconnected spawn from goal"
      )
  }
