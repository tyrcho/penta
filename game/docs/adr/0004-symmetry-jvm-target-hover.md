# ADR 0004 - Enforce Symmetry, JVM Target, Hover Tooltips

## Context

[ADR 0003](0003-chaos-plunder-victory.md) made the match asymmetric (player fixed to Nature, AI fixed to Chaos) to get *a* win condition working end to end. The user then explicitly required the game be symmetric instead, and asked for that rule to be written down so it isn't re-broken. This iteration fixes that, adds a JVM target for faster iteration on AI strategies, and adds hover tooltips for costs/stats.

## Decisions

### Symmetry is now a standing project rule, not just a code review comment

Recorded in `CLAUDE.md`: either maze must be able to build either building, either maze can win via either victory condition, and shared numbers that aren't tied to one specific building (like starting resources) must not favor one strategy. `AiController.maybeBuild` now tries Forêt then Cave (was Cave-only); `VictoryConditions.evaluate` checks both mazes against both conditions generically (was player-must-be-Nature, AI-must-be-Chaos); the player's build-selector UI (two toolbar buttons) lets them build either, same as the AI can. Also fixed a real asymmetry `CLAUDE.md` flagged retroactively: `Balance.StartingWood` was `ForestCostWood*2` (20) while `StartingFire` was `CaveCostFire` (10) — unequal starting resources bias the cheaper single-resource building (Forêt) even though both mazes get the identical split. Both are now `Balance.StartingResource` (10).

### Elf can plunder too — it and Goblin are now mechanically identical

The user's direct instruction. Previously only Goblin plundered on arrival, which was itself a defensible reading of `Goblin.md`'s "Pille une ressource de chaque type" with no equivalent line in `Elf.md` — but once buildings became symmetric (either side can build a Forêt), having Forêt-spawned Elf *not* plunder while Cave-spawned Goblin does would asymmetrically favor whoever builds Caves. `CombatEngine.moveEnemies` no longer filters by `UnitKind` before pillaging — any arrival steals `Balance.PlunderPerUnit` (renamed from `PlunderPerGoblin`). `UnitKind` is now purely cosmetic (which sprite to draw); the only real difference between Elf and Goblin is which building spawns them and at what rate.

### `core` gets a JVM target back, for fast AI iteration

[ADR 0002](0002-nature-two-maze-battle.md) deliberately dropped the JVM target ("this POC has no non-browser consumer"). That's no longer true: the user wants to iterate on `AiController` strategies quickly, and a JVM run/test loop is much faster than round-tripping through Node or a browser. `core` is `crossProject(JSPlatform, JVMPlatform)` again (`CrossType.Pure`, so no source-directory changes — `core/src/main/scala` stays shared). `js` depends on `coreJS`. `make test` now runs `coreJVM/test` (the fast default); `make test-js` runs `coreJS/test` as a parity check that the browser build still behaves the same.

### Hover tooltips read live state every frame, not just at hover-start

Cost tooltips on the two build buttons are a plain HTML `title` attribute set once from `Balance` constants — simplest possible, native browser tooltip, no custom code needed since costs don't change.

Building/unit tooltips can't use `title` (they're canvas sprites, not DOM elements). Each sprite is wired for `pointerover`/`pointerout` (`wireHover`) that only records *which* entity (maze side + kind + id) is hovered; the actual tooltip text is recomputed from the current `BattleState` every tick (`updateTooltip`) for as long as something is hovered, so HP and spawn countdowns shown in it stay accurate without needing to re-trigger `pointerover`. This also makes it self-healing: if the hovered entity dies or is removed between frames, `updateTooltip` finds no match and hides the tooltip instead of showing stale data. Position follows the pointer via a single stage-level `pointermove` listener rather than one per sprite.
