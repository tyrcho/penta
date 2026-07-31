package towerdefense.domain

import towerdefense.domain.economy.*

// Replaces the old per-faction Forest/Cave/Labyrinth/Eglise/Watchtower case classes —
// see BuildingSpecs for what each kind costs/produces/spawns. spawnCountdownMs is
// inert (0.0, never read) for kinds whose spec has no `spawns` (only Watchtower and the
// Science labs today) rather than modeled as an Option — cheaper than threading an
// unwrap through every fold/copy site for the sake of a few kinds out of many.
// corruptionPercent (Corruption.md, Mort's mechanic — see CombatEngine): 0-100, how far a
// Zombie/Vampire standing adjacent has corrupted this building; defaults to 0.0 so every
// existing call site (none of which involves Mort) is unaffected. Inert for any maze this
// faction never touches, same reasoning as spawnCountdownMs above.
// flashMs (Portail.md's PassingGate only): counts down from Balance.PassingGateFlashMs
// whenever a creature dies adjacent to this gate (see CombatEngine.applyPassingGateHarvest)
// — purely a UI cue (GameApp.scala tints the sprite while it's positive), read by nothing
// else in the domain. Inert (0.0, never set) for every other kind, same reasoning as
// spawnCountdownMs/corruptionPercent above.
// damageCooldownMs: counts down toward this building's next full damage hit, for any
// kind dealt via CombatEngine.applyDamageSources (Watchtower, Forest/Jungle/Angel/
// PassingGate) — see Balance.DamageTickIntervalMs's doc. Meaningless (never read) for
// every other kind. Defaults to a full DamageTickIntervalMs, same as spawnCountdownMs's
// own "the first hit/spawn only lands after waiting out half of one interval, not
// instantly" convention (see Placement's spawnCountdownMs initialization) — a
// freshly-placed Watchtower doesn't get a free instant snipe against whatever's already
// standing adjacent to it the moment it's built.
// constructionRemainingMs: how much longer this building takes to finish being built
// (Balance.ConstructionMsPerCostUnit's doc — "1 sec / 5 resources" of whatever it cost),
// counted down by CombatEngine each tick, floored at 0.0 once construction is done. While
// positive, this building is inert — it produces nothing, spawns nothing, and deals no
// damage/harvests nothing (Forest/Jungle/Angel/PassingGate/Watchtower), even though it
// already occupies its cell and blocks pathing like any other building. 0.0 (already
// built) for every existing call site that constructs a Building directly rather than
// through Placement, same "inert field, cheap to carry" default as spawnCountdownMs
// elsewhere in this file. Placement halves spawnCountdownMs's usual first-interval wait
// (see its own doc) to partially compensate for this added delay.
// constructionTotalMs: the construction time this building's current timer started at
// (set once, alongside constructionRemainingMs, by Placement's placeBuilding/upgradeBuilding
// — never itself decremented). Only read by the UI (GameApp's radial construction-progress
// wipe) to turn the remaining/total pair into a 0..1 fraction; the domain layer only ever
// needs the remaining half. Defaults to 0.0, same as constructionRemainingMs, for any
// direct (non-Placement) Building construction — the UI treats total <= 0.0 as "no
// progress to draw" rather than dividing by zero.
case class Building(
    id: Long,
    col: Int,
    row: Int,
    kind: BuildingKind,
    spawnCountdownMs: Double,
    corruptionPercent: Double = 0.0,
    flashMs: Double = 0.0,
    damageCooldownMs: Double = Balance.DamageTickIntervalMs,
    constructionRemainingMs: Double = 0.0,
    constructionTotalMs: Double = 0.0
)
