package towerdefense.domain

// Precomputed wall layouts that force a long, winding enemy path, instead of scoring
// candidate cells one build at a time like CompositeStrategy's maze component does (see
// its dangerScore doc). A concentric-ring spiral — the shape a human would first reach
// for — turns out not to work on this grid: since spawn and goal both sit on the outer
// boundary, BFS can always shortcut past any inner ring by walking the outer one alone,
// so inner rings never end up on the shortest path (verified empirically before writing
// this). Only walls that fully span the grid, leaving exactly one gap, actually force a
// detour — a "comb" of alternating full-width teeth is the simplest shape with that
// property, so that's what these are.
object MazeTemplate:

  // Horizontal teeth: every other row is walled edge-to-edge except one gap, alternating
  // ends, forcing the path to fully cross the grid on each lane before it can drop to the
  // next one. Row 0 (spawn's row) and the last row (goal's row) are never teeth, so the
  // template never needs to touch spawn or goal directly. Returned as an ordered List, not
  // a Set: a single tooth row only starts forcing anything once it's *entirely* built
  // (any row with more than its one designated gap open is just as bypassable as no wall
  // at all — verified in `make sim`, see TemplateStrategy's doc), so TemplateStrategy needs
  // to finish one row before starting the next, not spread builds evenly across all of
  // them. This order — row 1 first, in ascending column order, then row 3, etc. — is
  // exactly that build sequence; TemplateStrategy just walks it as given.
  def comb(cols: Int, rows: Int): List[(Int, Int)] =
    val toothRows = 1 until (rows - 1) by 2
    toothRows.zipWithIndex.flatMap { case (r, i) =>
      val gapCol = if i % 2 == 0 then cols - 1 else 0
      (0 until cols).filterNot(_ == gapCol).map(c => (c, r))
    }.toList

  // Same shape, rotated 90°: vertical teeth on alternating columns instead of horizontal
  // teeth on alternating rows. Order is column-major for the same reason `comb` is
  // row-major: finish one column's tooth before starting the next.
  def combVertical(cols: Int, rows: Int): List[(Int, Int)] =
    val toothCols = 1 until (cols - 1) by 2
    toothCols.zipWithIndex.flatMap { case (c, i) =>
      val gapRow = if i % 2 == 0 then rows - 1 else 0
      (0 until rows).filterNot(_ == gapRow).map(r => (c, r))
    }.toList

  // ASCII render for approval tests and debugging: 'x' wall, '.' open, 'S'/'G' spawn/goal.
  def render(
      cols: Int,
      rows: Int,
      walls: Iterable[(Int, Int)],
      spawn: (Int, Int),
      goal: (Int, Int)
  ): String =
    val wallSet = walls.toSet
    (0 until rows)
      .map { r =>
        (0 until cols)
          .map { c =>
            val cell = (c, r)
            if cell == spawn then 'S'
            else if cell == goal then 'G'
            else if wallSet.contains(cell) then 'x'
            else '.'
          }
          .mkString
      }
      .mkString("\n")
