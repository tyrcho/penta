# Building/unit ideas backlog

Leftover ideas from a design-proposal pass that suggested ~5 buildings per faction
(3-tier framework: tier 1 = cheap resource + weak unit/defense, tier 2 = more resource +
stronger unit/defense, tier 3 = no resource production, very strong effect). Three were
picked and fully implemented; the rest are recorded here so they aren't lost.

## Already implemented

- **Antre du Dragon / Dragon's Lair** (Chaos, T3) — spawns **Dragon**: fast (+50% speed),
  fragile (low HP), plunders a very high amount of Gold (near the Chaos plunder victory
  target on its own).
- **Caserne / Barracks** (Loi, T1) — spawns **Soldat**: "Rang serré" (Close Ranks), takes
  reduced aura damage while paired with another living Soldier nearby.
- **Champ de Stase / Stasis Field** (Science, T2) — no unit, slows adjacent enemy units by 50%.
- **Elf** (existing unit, Nature) — new "swarm tactics" mechanic: +2% HP per other living
  Elf already in the maze it's raiding, fixed at spawn time.

## Not yet built

- **Nature**
  - Clairière (T1) — no unit; reduces the corruption rate on adjacent Nature buildings.
  - Ménagerie → Ours "Bear" (T2) — self-regeneration (heals a flat HP/s while alive).
  - Cercle des Druides (T3) — no unit; passive buff/regen to all other Nature buildings.
- **Chaos**
  - Camp de Guerre → Orc (T1) — "siege strike": deals direct partial damage to whatever
    building it reaches, instead of plundering or corroding over time.
  - Forge des Nains → Nain Forgeron "Dwarf" (T2) — "death-burst": explodes on death,
    dealing area damage to nearby enemy units.
- **Loi**
  - Sanctuaire des Pégases (T3) — no unit; strong maze-wide shield/damage-reduction aura
    for all Loi buildings.
- **Mort**
  - Crypte → Squelette "Skeleton" (T1) — "reconstitution": one chance to reform after dying.
- **Science**
  - Miroir Quantique (T3) — each cycle, copies the opponent's single most expensive
    unit-spawning building's output (tinted blue) into their own maze, at that building's
    cadence. Needs real engine design: `BuildingSpec.spawns` is static today, this one's
    spawned kind/interval are chosen dynamically from the opponent's state.
  - **Ice Dragon** (deferred from Dragon's original design, no building assigned yet) —
    freezes buildings in a 2-cell radius, slowing their abilities by 50%.
