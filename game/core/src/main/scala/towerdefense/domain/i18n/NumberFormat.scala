package towerdefense.domain.i18n

// Shared number formatting for both the game UI (GameApp's tooltips) and the doc
// generator, so a rate/percentage/duration is never spelled out two different ways in
// the two places that show it.
object NumberFormat:
  // Whole numbers print bare ("2"), fractional ones keep their decimal ("0.2") — used for
  // per-second rates and multipliers, so a sub-1 rate (e.g. Balance.FirePerSecPerCave)
  // never gets silently truncated to "0" by a plain `.toInt`. Rounded to 2 significant
  // digits first so a derived rate (e.g. count * perUnitRate) never shows raw
  // floating-point noise (e.g. 0.6000000000000001 from three 0.2 forests).
  def decimal(d: Double): String =
    val rounded = roundToSignificantDigits(d, digits = 2)
    if rounded == rounded.toInt then rounded.toInt.toString else rounded.toString

  private def roundToSignificantDigits(value: Double, digits: Int): Double =
    if value == 0.0 then 0.0
    else
      val magnitude = math.pow(10, digits - 1 - math.floor(math.log10(math.abs(value))).toInt)
      math.round(value * magnitude) / magnitude

  def seconds(ms: Double): Int = (ms / 1000).toInt

  def percent(fraction: Double): String = decimal(fraction * 100) + "%"

  def percentPoints(points: Double): String = decimal(points) + "%"
