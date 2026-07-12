package towerdefense.domain

import towerdefense.domain.geometry.Vec2

object GridConfig:
  val cols: Int = 16
  val rows: Int = 9
  val cellSize: Double = 50.0
  val width: Double = cols * cellSize
  val height: Double = rows * cellSize

  val spawnCell: (Int, Int) = (0, 0)
  val goalCell: (Int, Int) = (cols - 1, rows - 1)

  def cellCenter(col: Int, row: Int): Vec2 =
    Vec2((col + 0.5) * cellSize, (row + 0.5) * cellSize)

  def cellOf(pos: Vec2): (Int, Int) =
    val col = (pos.x / cellSize).toInt.max(0).min(cols - 1)
    val row = (pos.y / cellSize).toInt.max(0).min(rows - 1)
    (col, row)

  def isInBounds(col: Int, row: Int): Boolean =
    col >= 0 && col < cols && row >= 0 && row < rows
