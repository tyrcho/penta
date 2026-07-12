package towerdefense.domain.geometry

case class Vec2(x: Double, y: Double):
  def +(o: Vec2): Vec2 = Vec2(x + o.x, y + o.y)
  def -(o: Vec2): Vec2 = Vec2(x - o.x, y - o.y)
  def *(k: Double): Vec2 = Vec2(x * k, y * k)
  def length: Double = math.sqrt(x * x + y * y)
  def distanceTo(o: Vec2): Double = (o - this).length
  def normalized: Vec2 =
    val len = length
    if len == 0 then Vec2(0, 0) else Vec2(x / len, y / len)
