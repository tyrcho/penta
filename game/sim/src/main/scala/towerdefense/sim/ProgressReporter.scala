package towerdefense.sim

// CLAUDE.md: "any job running more than a few seconds should report an ETA to stderr" —
// this is that, for the sim CLI's batch commands (run/tournament/tune). stderr, not
// stdout, so it never mixes into piped/redirected result output (formatTallyTable etc).
object ProgressReporter:

  // Always print on the very first unit of completed work (confirms the job actually
  // started, instead of leaving the caller wondering for up to 2s) and once the work is
  // done (the final line shouldn't be swallowed by the throttle either) — otherwise at
  // most once every 2s, frequent enough that a multi-minute job never looks hung,
  // infrequent enough that a fast one doesn't spam a line per unit of work. Pure so it's
  // testable without depending on wall-clock timing.
  private[sim] def shouldPrint(nowNanos: Long, lastPrintNanos: Long, completed: Int, total: Int): Boolean =
    completed <= 1 || completed >= total || (nowNanos - lastPrintNanos) >= 2_000_000_000L

  // ETA is a flat linear extrapolation from elapsed/completed — good enough for a rough
  // "is this almost done" signal, not a promise.
  private[sim] def formatLine(label: String, completed: Int, total: Int, elapsedSeconds: Double): String =
    val etaSeconds = if completed == 0 then Double.NaN else elapsedSeconds / completed * (total - completed)
    val eta = if etaSeconds.isNaN then "unknown" else f"${etaSeconds}%.0fs"
    f"[$label] $completed%d/$total%d done, elapsed ${elapsedSeconds}%.0fs, ETA $eta"

final class ProgressReporter(label: String, total: Int):
  private val startNanos = System.nanoTime()
  private var lastPrintNanos = startNanos

  def tick(completed: Int): Unit =
    val now = System.nanoTime()
    if ProgressReporter.shouldPrint(now, lastPrintNanos, completed, total) then
      lastPrintNanos = now
      val elapsedSeconds = (now - startNanos) / 1e9
      System.err.println(ProgressReporter.formatLine(label, completed, total, elapsedSeconds))
