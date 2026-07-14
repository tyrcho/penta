# ADR 0003 - Chaos Faction, Plunder, Victory Conditions

## Context

[ADR 0002](0002-nature-two-maze-battle.md) implemented Nature as a symmetric Nature-vs-Nature battle (both mazes ran the same Forêt/Elf rules) with no way to actually win or lose beyond an ever-decreasing life count. This iteration adds the vault's Chaos faction (`../../Resources/Chaos/`) as what the AI plays, its plunder mechanic, and the two matching victory conditions from `../../Victoire.md`.

## Decisions

### The match becomes asymmetric: player = Nature, AI = Chaos

`Victoire.md` defines one victory condition *per faction* ("Nature: build XX buildings", "Chaos: plunder XX resources") — there's no generic "whoever's maze survives longest" condition. The clearest reading of "add Chaos and implement the two victory conditions for Nature and Chaos" is one condition per side of the same match, not two Nature mazes with no way to actually resolve. `AiController.maybeBuildCave` replaces the earlier `maybeBuild` (which built Forêts); the human still plays Nature via taps.

### Cave mirrors Forest's shape but not its behavior

`Cave` (cost 5 wood + 10 fire, produces 2 fire/sec, spawns a Goblin every 5s) is a separate case class from `Forest`, not a shared "Building" base type. They differ enough — Cave has no combat aura at all (`Cave.md` gives it none, unlike Forêt's Ents), and Cave's cost spans two resources while Forêt's is one — that a shared abstraction would need to accommodate both shapes anyway, buying nothing (rule of three: two shapes this different aren't worth unifying yet). Both still block pathfinding and placement identically, via a small `buildingCells` helper duplicated between `CombatEngine` and `Placement` (single-purpose, two call sites, acceptable per the same rule).

### Goblin plunders, Elf doesn't — because only one of them is specified to

`Elf.md` gives Elf no combat ability; `Goblin.md`'s only defining line is "Pille une ressource de chaque type" (plunders one resource of each type). Both still cost a life on arrival (the original POC's generic "leak" rule, not vault-specified either way) — plunder is an *additional* effect layered on Goblin specifically, not a replacement, since nothing says otherwise and it's simpler to keep the arrival rule uniform. The plunder amount (1 wood + 1 fire, clamped to what the defending maze actually has) is a POC default — `Goblin.md` says "one resource of each type" but not how much of each.

### Plunder credit happens in BattleEngine, not CombatEngine

`CombatEngine.tick` stays single-maze and pure: it deducts the stolen resources from the maze being plundered and reports the amounts in `TickResult`, but has no notion of "the opponent" to credit them to. `BattleEngine.creditPlunder` — which already had this cross-maze responsibility for delivering Elf/Goblin — adds the stolen amounts to the attacking maze's own economy and its `resourcesPlundered` tally. Same shape as unit delivery: single-maze engine reports an effect, the battle-level engine is what actually crosses the boundary.

### Victory conditions and the fallback loss, evaluated once per tick

`VictoryConditions.evaluate` checks, in order: Nature target reached → player wins; Chaos plunder target reached → AI wins; either maze's lives ≤ 0 → the other side wins by default. That fallback isn't in `Victoire.md` (which doesn't define what happens if neither target is hit) but without it a slow match could run forever with negative lives, which was an open gap since [ADR 0002](0002-nature-two-maze-battle.md). `BattleState.outcome` is set once `evaluate` returns a result, and `BattleEngine.tick` short-circuits to return the battle unchanged forever after — verified live in the browser (the AI plundered its way to a win, the board froze, the UI showed the reason) as well as with a regression test asserting a second tick is a no-op once `outcome` is set.

### Both victory targets are POC defaults, same as the earlier unspecified numbers

`Victoire.md` literally says "construire **XX** batiments" and "piller **XX** ressources" — neither number was ever filled in. `Balance.NatureVictoryForestTarget` (10) and `Balance.ChaosVictoryPlunderTarget` (20) are POC defaults chosen to be reachable within a few minutes at the production/plunder rates already tuned, following the same "cite the source, say POC default when the source doesn't specify" convention as every other undefined number in `Balance.scala`.

### Sprites: reused the rotation/effect machinery, added tinting for the one weak asset match

Cave and Goblin reuse the exact same sprite/rotation/flame-effect code path as Forêt/Elf (`syncCaves`/`syncEnemies` are structurally identical to their Nature counterparts). The only new facade surface was `Sprite.tint`: the closest available Kenney tile for "cave" is a cool gray-blue rock, which reads wrong for Chaos, so it's recolored warm/orange at render time instead of hunting for a better-matching tile in the pack.
