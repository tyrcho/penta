package towerdefense.domain.i18n

class NumberFormatTest extends munit.FunSuite:

  test("decimal prints a whole number bare") {
    assertEquals(NumberFormat.decimal(2.0), "2")
    assertEquals(NumberFormat.decimal(40.0), "40")
  }

  test("decimal keeps a genuine large whole-number cost exact, not rounded to 2 significant digits") {
    // ResearchSpec.costAtLevel spirals well past 2 significant digits by level 4/5
    // (5 * 3^3 = 135, 5 * 3^4 = 405) — these are exact Doubles (no floating-point noise),
    // so they must print exactly, not as the misleadingly-rounded "140"/"410".
    assertEquals(NumberFormat.decimal(135.0), "135")
    assertEquals(NumberFormat.decimal(405.0), "405")
    assertEquals(NumberFormat.decimal(1215.0), "1215")
  }

  test("decimal still cleans up genuine floating-point noise in a non-integer rate") {
    // Three 0.2 forests summed is exactly the kind of noise this rounding exists for.
    val noisy = 0.2 + 0.2 + 0.2
    assert(noisy != 0.6, "test fixture should reproduce real floating-point noise")
    assertEquals(NumberFormat.decimal(noisy), "0.6")
  }

  test("decimal keeps a sub-1 rate visible rather than truncating it to 0") {
    assertEquals(NumberFormat.decimal(0.2), "0.2")
  }

  test("percent multiplies by 100 and appends the sign") {
    assertEquals(NumberFormat.percent(0.10), "10%")
    assertEquals(NumberFormat.percent(1.20), "120%")
  }
