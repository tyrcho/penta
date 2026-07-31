package towerdefense.domain

// A grid cell coordinate — replaces the (Int, Int) tuples used throughout pathfinding,
// layout, and placement so `.col`/`.row` reads self-document instead of `._1`/`._2`, and a
// (row, col) transposition mistake is a compile error (Pos(col, row) vs Pos(row, col)) rather
// than a silently-swapped tuple.
case class Pos(col: Int, row: Int) derives CanEqual
