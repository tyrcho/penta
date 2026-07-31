# Scala 3 patterns from the BuildingKind/UnitKind data-driven refactor

Patterns that came up while moving `BuildingSpecs`/`CreatureSpecs`/
`ResearchSpecs`'s hand-maintained `Map[Kind, Spec]` tables onto
`BuildingKind`/`UnitKind`/`Resource` themselves as literal enum-case fields,
splitting the `domain` package into subpackages, and replacing ad hoc
`(Int, Int)` tuples with a `Pos` case class. Kept here so the next
enum-with-data refactor in this codebase (or a similar one) doesn't have to
rediscover these the hard way.

## 1. A parameterized enum case initializes as an ordered `val`, not lazily

```scala
enum BuildingKind(
    val cost: Map[Resource, Double],
    val spawns: Option[(UnitKind, Double)] = None
):
  case Grove
      extends BuildingKind(cost = ..., spawns = Some(UnitKind.Elf -> ...))
  case Forest
      extends BuildingKind(cost = ..., spawns = Some(UnitKind.Elf -> ...))
```

Each case's constructor arguments are evaluated **eagerly, in declaration
order**, as the enum's companion object's single static initializer runs top
to bottom. This is the root fact everything below follows from — it is *not*
like a `lazy val` or a Java enum with per-constant class bodies.

## 2. Same-enum reference: safe backward, unsafe forward/self

- **Backward reference** (a case names an *earlier*-declared sibling) is a
  safe literal `val`: `BuildingKind.upgradeFrom` on `Forest` names `Grove`,
  already fully constructed by the time `Forest`'s own initializer runs.
- **Forward reference** (a case names a *later*-declared sibling) or a
  **self-reference** resolves to `null`: the sibling's field slot hasn't been
  assigned yet. This is what broke `UnitKind.spawns` for
  `Necromancer -> Soul` (forward) and `Tree -> Tree` (self).

Fix: make the field a `def` that pattern-matches on `this`, declared once,
after every case:

```scala
def spawns: Option[(UnitKind, Double)] = this match
  case UnitKind.Necromancer => Some(UnitKind.Soul -> Balance.SoulSummonIntervalMs)
  case UnitKind.Tree        => Some(UnitKind.Tree -> Balance.TreeCloneIntervalMs)
  case _                    => None
```

A `def` evaluates lazily on each call; by the time anything calls it, the
whole enum has finished initializing, so every case reference resolves to a
real object regardless of declaration order.

**Consequence for design order:** when a forward-direction relationship needs
to preserve a specific priority order (e.g. "try `LaboDeLaLoi` before the
other four upgrade targets"), and that order is derived from
`BuildingKind.values`' own declaration order, *reorder the enum cases* rather
than hand-sorting a derived collection. The order becomes visible,
load-bearing source, not an incidental side effect of a `groupMap` call.

## 3. Cross-enum reference: safe one-way, does not compile both ways

A literal reference from one enum's case to *another* enum's case is safe
**as long as only one direction is a literal `val`**:

```scala
// BuildingKind (safe): a plain cross-enum reference — UnitKind never
// references BuildingKind back
case Grove
    extends BuildingKind(spawns = Some(UnitKind.Elf -> Balance.ElfSpawnIntervalMs))
```

Adding a literal field on `UnitKind` that points back at `BuildingKind` (e.g.
`producedFrom: Option[BuildingKind]` set directly in each case's constructor
call) recreates the exact hazard from §2, just spread across two enums
instead of one. This was verified empirically, not just reasoned about — a
minimal two-enum repro with mutual literal cross-references:

```scala
enum A(val friend: Option[B]):
  case A1 extends A(Some(B.B1))

enum B(val friend: Option[A]):
  case B1 extends B(Some(A.A1))
```

fails to **compile at all** (`Recursive value A1 needs type`), a cyclic
type-inference error — not a silent runtime `null`. Scala 3 can't decide
`A1`'s type without first knowing `B1`'s type, which needs `A1`'s type.
Wrapping the field in `Option` doesn't help; neither does an explicit field
type annotation on the enum class.

Fix: same as §2 — the *reverse* direction of a cross-enum relationship stays
a `def`, pattern-matching on `this`:

```scala
def producedFrom: Option[BuildingKind] = this match
  case UnitKind.Elf    => Some(BuildingKind.Grove)
  case UnitKind.Goblin => Some(BuildingKind.Cave)
  ...
```

**When in doubt, don't guess — write a 10-line repro**
(`scala-cli run Test.scala`) with the same shape (same eager-vs-lazy field,
same direction of reference) before asserting a cycle is or isn't safe. It
settles the question in seconds and catches cases where intuition about JVM
class-init reentrancy is wrong in either direction.

## 4. Plain-data fields (no enum-to-enum reference) are always safe literals

A field whose value is a case class or primitive with no reference back into
either enum — e.g. `BuildingKind.researchSpec: Option[ResearchSpec]`,
`UnitKind.corruptionRatePerSec: Double` — has none of the above hazards.
These are ordinary literal `val`s regardless of which case declares them or
in what order.

## 5. Replace a hand-maintained inverse/grouped map with a derived one

When the *forward* direction of a relationship must stay external (§2/§3),
don't hand duplicate the *backward* direction as a second literal `Map` —
derive it from the now-literal field instead, so there is exactly one place
a contributor can add a new case without updating both:

```scala
// Before: two independent hand-maintained tables that could silently drift
val upgradeOptions: Map[BuildingKind, List[BuildingKind]] =
  Map(Grove -> List(Forest), ...)
val upgradeFrom: Map[BuildingKind, BuildingKind] =
  upgradeOptions.flatMap { case (s, ts) => ts.map(_ -> s) }

// After: upgradeFrom lives on the enum case itself (safe backward literal,
// see §2); upgradeOptions is derived from it, preserving BuildingKind.
// values' own declaration order
val upgradeOptions: Map[BuildingKind, List[BuildingKind]] =
  BuildingKind.values.toList.flatMap(k => k.upgradeFrom.map(_ -> k))
    .groupMap(_._1)(_._2)
```

This is exactly the class of bug the enum-field migration is meant to
prevent in the first place (see `BuildingKind.WarCamp` once being added to
the enum but missing from a separate hand-written `buildOrder` list,
silently making it unbuildable by one strategy).

## 6. A mandatory (no-default) literal field forces an explicit design call

Where a value used to be *computed* from a proxy heuristic (e.g. `tier`
ranked from a building's own cost against its faction siblings, with a small
override table for the few cases where the heuristic didn't match actual
design intent), consider replacing the heuristic with a plain mandatory
field instead:

```scala
enum BuildingKind(val tier: Int, ...):
  case Grove extends BuildingKind(tier = 1, ...)
```

Because `tier` has no default, the compiler refuses a new case that omits
it — the "forgot to update the override table" failure mode becomes a
compile error instead of a silently-wrong computed value. Reserve this for
fields where every case truly needs an explicit, hand-considered value; a
real default (`= 0.0`, `= None`) is still right for a field only a few cases
care about (`dps`, `corruptionRatePerSec`).

## 7. Introduce a small value type to replace a positional tuple

Replacing `(Int, Int)` grid-coordinate tuples with a
`case class Pos(col: Int, row: Int)` reads better (`.col`/`.row` vs
`._1`/`._2`) and turns a `(row, col)` transposition mistake into a compile
error. Migration notes:

- A bare tuple-pattern `val (col, row) = expr` only decompiles an actual
  `TupleN`; it does **not** work against a case class, even a 2-field one
  (`pattern's type (Any, Any) does not match ... Pos`, a real compiler
  warning that becomes a runtime `MatchError`). Use the case class's own
  extractor instead: `val Pos(col, row) = expr`.
- Add the natural overloads that take the new type alongside functions that
  already take the raw components, rather than a flag day forcing every
  call site to change shape at once: `GridConfig.cellCenter(col, row)` keeps
  working, and a `cellCenter(pos: Pos): Vec2 = cellCenter(pos.col, pos.row)`
  overload lets call sites that already hold a `Pos` avoid re-unpacking it.

## 8. Splitting a package into subpackages: import siblings explicitly

When a flat `domain` package (23+ files) is split into subpackages by
concern (`domain.grid`, `domain.combat`, `domain.economy`, `domain.ai`),
each file only needs:

```scala
import towerdefense.domain.*          // the shared entities (kinds, ...)
import towerdefense.domain.economy.*  // only if this file uses Balance/specs
```

Importing a subpackage's wildcard does **not** transitively pull in a
*different* subpackage — `import towerdefense.domain.*` only reaches members
of the `domain` package itself, not `domain.grid` or `domain.combat`. Each
file needs an explicit import per subpackage it actually touches; the
compiler's "not found" errors after a `git mv` + package-line update are the
fastest way to find every one (iterate `compile` until clean rather than
trying to enumerate them by hand).

## 9. Verification loop for a data-model refactor like this

1. Update tests first to express the target shape (project convention, not
   Scala-specific).
2. Make the enum/model change.
3. Recompile the whole project (`sbt compile Test/compile`) and iterate on
   "not found" / "recursive value" errors one batch at a time — each fixed
   import or field addition typically unlocks the next real error rather
   than needing to be planned up front.
4. Run the full test suite (`coreJVM/test coreJS/test sim/test`) —
   cross-compiled code (JVM + Scala.js) can diverge subtly, so both targets
   need a green run, not just one.
5. Link the browser bundle (`js/fastLinkJS`) as a final smoke check even
   when no JS-specific code changed, since it's the cheapest way to catch a
   missed import in the one module that isn't covered by `core`'s own test
   suite.
