package towerdefense.sim

// Pure, timing-independent logic only — ProgressReporter itself just glues these to
// System.nanoTime()/println, which isn't worth mocking a clock for.
class ProgressReporterTest extends munit.FunSuite:

  test("shouldPrint always fires once work is complete, regardless of throttle window") {
    assert(ProgressReporter.shouldPrint(nowNanos = 0L, lastPrintNanos = 0L, completed = 5, total = 5))
  }

  test("shouldPrint throttles: no second print inside the 2s window, one once it elapses") {
    assert(!ProgressReporter.shouldPrint(nowNanos = 1_000_000_000L, lastPrintNanos = 0L, completed = 2, total = 10))
    assert(ProgressReporter.shouldPrint(nowNanos = 2_000_000_000L, lastPrintNanos = 0L, completed = 2, total = 10))
  }

  test("shouldPrint always fires on the very first tick (lastPrintNanos == startNanos == now)") {
    assert(ProgressReporter.shouldPrint(nowNanos = 0L, lastPrintNanos = 0L, completed = 0, total = 10))
  }

  test("formatLine extrapolates an ETA from elapsed time and completed/total ratio") {
    val line = ProgressReporter.formatLine("test", completed = 5, total = 10, elapsedSeconds = 10.0)
    assertEquals(line, "[test] 5/10 done, elapsed 10s, ETA 10s")
  }

  test("formatLine reports an unknown ETA before any work has completed") {
    val line = ProgressReporter.formatLine("test", completed = 0, total = 10, elapsedSeconds = 1.0)
    assertEquals(line, "[test] 0/10 done, elapsed 1s, ETA unknown")
  }
