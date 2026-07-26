package towerdefense.sim

import towerdefense.domain.*

// Pure formatting/diffing logic, no simulation needed: every case is a hand-built pair of
// consecutive BattleState snapshots (plus a hand-built TickEvents for deaths/arrivals,
// which can't be reconstructed by diffing alone — see MatchLog's doc), matching the
// hand-built-fixture style used throughout CompositeStrategyTest/PlacementTest.
class MatchLogTest extends munit.FunSuite:

  private val noEvents = TickEvents.empty

  private def maze(
      buildings: List[Building] = Nil,
      resources: Map[Resource, Double] = Map.empty,
      resourcesPlundered: Double = 0.0,
      buildingsCorrupted: Double = 0.0
  ): MazeState =
    MazeState.initial.copy(
      buildings = buildings,
      resources = resources,
      resourcesPlundered = resourcesPlundered,
      buildingsCorrupted = buildingsCorrupted
    )

  private def battle(player: MazeState, ai: MazeState): BattleState =
    BattleState.initial.copy(player = player, ai = ai)

  test("a new building id on side a produces a BUILD line") {
    val before = battle(maze(), maze())
    val grove = Building(1, 3, 5, BuildingKind.Grove, 0.0)
    val after = battle(maze(buildings = List(grove)), maze())
    val lines = MatchLog.diff(tick = 0, before, after, noEvents)
    assertEquals(
      lines,
      Seq(s"tick 0  a  BUILD  Grove (3,5) cost ${MatchLog.fmtResources(BuildingSpecs.all(BuildingKind.Grove).cost)}")
    )
  }

  test("same id, changed kind on side b produces an UPGRADE line, not a BUILD+DESTROY pair") {
    val grove = Building(1, 3, 5, BuildingKind.Grove, 0.0)
    val forest = grove.copy(kind = BuildingKind.Forest)
    val before = battle(maze(), maze(buildings = List(grove)))
    val after = battle(maze(), maze(buildings = List(forest)))
    val lines = MatchLog.diff(tick = 10, before, after, noEvents)
    assertEquals(
      lines,
      Seq(s"tick 10  b  UPGRADE  Grove→Forest (3,5) cost ${MatchLog.fmtResources(BuildingSpecs.all(BuildingKind.Forest).cost)}")
    )
  }

  test("a disappeared id on side a produces a DESTROY line with the refund") {
    val cave = Building(1, 8, 2, BuildingKind.Cave, 0.0)
    val before = battle(maze(buildings = List(cave)), maze())
    val after = battle(maze(), maze())
    val lines = MatchLog.diff(tick = 20, before, after, noEvents)
    val refund = BuildingSpecs.all(BuildingKind.Cave).cost.view.mapValues(_ * Balance.DemolishRefundFraction).toMap
    assertEquals(lines, Seq(s"tick 20  a  DESTROY  Cave (8,2) refund ${MatchLog.fmtResources(refund)}"))
  }

  test("an increase in resourcesPlundered on side b produces a PLUNDER line") {
    val before = battle(maze(), maze(resourcesPlundered = 5.0))
    val after = battle(maze(), maze(resourcesPlundered = 12.0))
    val lines = MatchLog.diff(tick = 30, before, after, noEvents)
    assertEquals(lines, Seq("tick 30  b  PLUNDER  +7.0 (total 12.0)"))
  }

  test("no change at all produces no lines") {
    val state = maze(buildings = List(Building(1, 3, 5, BuildingKind.Grove, 0.0)), resourcesPlundered = 4.0)
    val before = battle(state, state)
    val lines = MatchLog.diff(tick = 40, before, before, noEvents)
    assertEquals(lines, Seq.empty[String])
  }

  test("a death is reported with its cause") {
    val events = TickEvents(
      playerDeaths = Nil,
      aiDeaths = List(Death(1, UnitKind.Goblin, DeathCause.Aura)),
      playerArrivals = Nil,
      aiArrivals = Nil
    )
    val before = battle(maze(), maze())
    val lines = MatchLog.diff(tick = 50, before, before, events)
    assertEquals(lines, Seq("tick 50  b  DEATH  Goblin killed by Aura"))
  }

  test("an arrival with no plunder ability (Wolf) produces an ARRIVE line") {
    val events = TickEvents(
      playerDeaths = Nil,
      aiDeaths = Nil,
      playerArrivals = List(UnitKind.Wolf),
      aiArrivals = Nil
    )
    val before = battle(maze(), maze())
    val lines = MatchLog.diff(tick = 60, before, before, events)
    assertEquals(lines, Seq("tick 60  a  ARRIVE  Wolf reached goal, no plunder"))
  }

  test("an arrival with plunder ability (Elf) produces no ARRIVE line, only whatever PLUNDER the diff shows") {
    val events = TickEvents(
      playerDeaths = Nil,
      aiDeaths = Nil,
      playerArrivals = List(UnitKind.Elf),
      aiArrivals = Nil
    )
    val before = battle(maze(resourcesPlundered = 0.0), maze())
    val after = battle(maze(resourcesPlundered = Balance.PlunderPerUnit), maze())
    val lines = MatchLog.diff(tick = 70, before, after, events)
    assertEquals(
      lines,
      Seq(f"tick 70  a  PLUNDER  +${Balance.PlunderPerUnit}%.1f (total ${Balance.PlunderPerUnit}%.1f)")
    )
  }

  test("a corrupted-to-death building on side a produces a CORRUPT line, not a DESTROY line") {
    val grove = Building(1, 3, 5, BuildingKind.Grove, 0.0)
    val before = battle(maze(buildings = List(grove)), maze())
    val after = battle(maze(buildingsCorrupted = 0.0), maze())
    val corrosion = Corrosion(1, BuildingKind.Grove, 3, 5, BuildingSpecs.all(BuildingKind.Grove).cost)
    val events = TickEvents(Nil, Nil, Nil, Nil, playerCorrupted = List(corrosion), aiCorrupted = Nil)
    val lines = MatchLog.diff(tick = 80, before, after, events)
    assertEquals(
      lines,
      Seq(
        s"tick 80  a  CORRUPT  Grove (3,5) corrupted to dust, opponent refunded " +
          s"${MatchLog.fmtResources(BuildingSpecs.all(BuildingKind.Grove).cost)}"
      )
    )
  }

  test("an increase in buildingsCorrupted on side b produces a CORRUPTED_TOTAL line") {
    val before = battle(maze(), maze(buildingsCorrupted = 1.0))
    val after = battle(maze(), maze(buildingsCorrupted = 2.0))
    val lines = MatchLog.diff(tick = 85, before, after, noEvents)
    assertEquals(lines, Seq("tick 85  b  CORRUPTED_TOTAL  +1 (total 2)"))
  }

  test("snapshotLine reports both sides' forest/plunder progress and resources") {
    val forest = Building(1, 3, 5, BuildingKind.Forest, 0.0)
    val playerState = maze(buildings = List(forest), resources = Map(Resource.Wood -> 45.0), resourcesPlundered = 0.0)
    val aiState = maze(resources = Map(Resource.Fire -> 30.0), resourcesPlundered = 12.0)
    val line = MatchLog.snapshotLine(tick = 500, battle(playerState, aiState))
    val playerForestTarget = VictoryConditions.forestTarget(playerState, aiState)
    val aiPlunderTarget = VictoryConditions.plunderTarget(playerState)
    assert(line.startsWith("tick 500  SNAPSHOT  "), line)
    assert(line.contains(s"a: forests 1/${playerForestTarget.toInt}"), line)
    assert(line.contains(s"plunder 12.0/${aiPlunderTarget.toInt}"), line)
  }

  test("finalLine reports the winning side and the human-readable reason") {
    val line = MatchLog.finalLine(tick = 812, MatchResult.PlayerWins("Nature's unstoppable expansion."))
    assertEquals(line, "tick 812  a  WINS  Nature's unstoppable expansion.")
  }
