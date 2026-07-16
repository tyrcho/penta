# tower-defense-poc — project instructions

## The game must be symmetric

Both mazes (player and AI) must always have identical capabilities: the same building
menu, the same costs, the same victory conditions available to either side. Never
hard-code one side to a single faction or give one side an option the other lacks.

If a new faction/building/unit is added, it must be placeable and winnable by *either*
side, not bolted on as "the AI's thing" or "the player's thing". `VictoryConditions`,
`AiController.maybeBuild`, and the player's build-selector UI must all stay in sync:
whatever one can attempt, the other must be able to attempt too.

## Art asset licensing is not a current concern

We use free icon/sprite packs (Kenney, CraftPix freebies, etc.) without tracking
licensing carefully right now. This is a deliberate, temporary tradeoff: if the game
ever gets real traction, we'll swap in properly licensed or commissioned art then. Don't
spend time chasing down attribution requirements or picking assets based on license
terms — just use whatever free asset fits, and keep a `LICENSE-<name>.txt` next to it
for whatever the source page says, on a best-effort basis only.

Symmetry means player vs AI have the same rules and options — it does NOT mean every
number has to equal every other number. `Balance.StartingWood` (20) and `StartingFire`
(10) are intentionally different from each other; both mazes still get the identical
pair, which is what symmetry actually requires here.

## When changing a feature or fixing a bug, update the tests first

Before editing implementation code for a feature change or bug fix, update or add the
relevant test(s) first so they express the desired behavior, then make the code change.
Don't write the fix first and backfill tests afterward.

## Metals MCP server

This project has a `.mcp.json` pointing at a local Metals MCP server (started via
`metals-mcp --workspace . --client claude`), giving structural, compiler-aware access
to the Scala code instead of plain-text file reads. When starting `claude` in this
directory, approve the `metals` server if prompted.

Prefer its tools over grepping/reading source when you need compiler-verified
information: use it to compile the project and read diagnostics, and to look up
type/symbol info, instead of guessing from source text.

If the server isn't running (no response on the port in `.mcp.json`), restart it with:

```
metals-mcp --workspace . --client claude
```

## sbt not on PATH?

In a network-restricted sandbox (no `sbt` binary, `repo.scala-sbt.org`/`github.com`
downloads blocked), see `docs/setup.md` for how to get `sbt "coreJVM/test"` running
via a bare `sbt-launch.jar` fetched from Maven Central.
