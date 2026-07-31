package towerdefense.domain

import towerdefense.domain.grid.Vec2

// A unit currently walking this maze. From this maze owner's point of view it's
// always hostile — sent by one of the opponent's buildings. See UnitKind for per-kind
// stats/plunder, and CombatEngine for combat abilities (Paladin's shield, Forest's aura,
// Watchtower's ranged damage), which stay kind-based special cases.
// spawnCountdownMs: inert (0.0) for every kind whose UnitKind.spawns is None (see
// CombatEngine.advanceCreatureSummons) — same "inert field, cheap to carry" choice as
// Building.spawnCountdownMs.
// summonedBy: the id of the creature that invoked this one via UnitKind.spawns (a
// Soul's Necromancer — see Ame.md — or a Tree's parent clone — see Arbre Anime.md) — None
// for a creature that arrived via a building's own spawn instead (every other kind, and
// an "original" Tree). Used to credit Ame.md's heal to the *specific* Necromancer that
// summoned a given Soul, not any Necromancer present.
// frozenMs: how much longer this creature is rooted in place, not advancing toward the
// goal — set by CombatEngine.advanceCreatureSummons the instant a summon triggers (see
// UnitKind.spawnFreezeMs), inert (0.0) for every kind with no freeze.
// sizeFraction: what fraction of its kind's base maxHp/render size this particular
// creature has — 1.0 (full size) for every kind except a self-cloned Tree (Arbre Anime.md:
// each clone is TreeCloneSizeStepFraction smaller than the parent that made it, floored at
// TreeMinCloneSizeFraction — see CombatEngine.advanceCreatureSummons).
// hasCheatedDeath: false for every kind except Orc (Camp de Guerre — see Balance.
// OrcMaxHp's doc), whose first lethal hit clamps it to 1 HP instead of removing it,
// exactly once per creature (see CombatEngine.applyDamageSources) — inert for every other
// kind, same "cheap to carry" choice as summonedBy/frozenMs/sizeFraction above.
case class Creature(
    id: Long,
    pos: Vec2,
    hp: Double,
    maxHp: Double,
    speedPerMs: Double,
    kind: UnitKind,
    spawnCountdownMs: Double = 0.0,
    summonedBy: Option[Long] = None,
    frozenMs: Double = 0.0,
    sizeFraction: Double = 1.0,
    hasCheatedDeath: Boolean = false
)
