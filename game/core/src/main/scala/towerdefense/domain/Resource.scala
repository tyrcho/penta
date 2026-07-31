package towerdefense.domain

// The 5 resources across the vault's factions, plus Gold — a special, faction-less
// wildcard currency (see Placement.canAfford/debit): it substitutes 1-for-1 for any
// shortfall in the other 5 when paying a cost, so it never appears as a listed cost
// itself on any BuildingKind.cost/ResearchSpec.
enum Resource derives CanEqual:
  case Wood, Fire, Light, Shadow, Crystal, Gold

  // Engendre.md's resource-generation cycle: the resource whose producer-buildings boost
  // THIS resource's own production rate (see Balance.EngendreBoostPerBuilding's doc) —
  // Wood's boost comes from Light producers, Fire's from Wood producers, Shadow's from
  // Fire producers, Crystal's from Shadow producers, Light's from Crystal producers, the
  // same 5-cycle Engendre.md itself describes. None for Gold — it isn't part of this cycle
  // at all (see CombatEngine.engendreBoost's own None-handling), only the vault's original
  // 5 resources are.
  // A `def` pattern-matching on `this`, not a literal per-case constructor argument: this
  // is a same-enum reference, and two of the five links (Wood -> Light, Light -> Crystal)
  // point FORWARD to a case declared later than the one referencing it — Scala 3 enum
  // cases initialize as ordered vals, so a forward reference from inside a `val` at
  // case-construction time would resolve to null (see UnitKind.spawns' own doc for the
  // same underlying issue). A `def` is evaluated lazily on each call, by which point the
  // whole enum is already fully initialized.
  def engendreSource: Option[Resource] = this match
    case Resource.Fire    => Some(Resource.Wood)
    case Resource.Shadow  => Some(Resource.Fire)
    case Resource.Crystal => Some(Resource.Shadow)
    case Resource.Light   => Some(Resource.Crystal)
    case Resource.Wood    => Some(Resource.Light)
    case Resource.Gold    => None
