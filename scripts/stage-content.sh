#!/usr/bin/env bash
# Stages the Quartz content/ directory from this repo's own sources of truth. Unlike
# japon (whose whole repo *is* the Obsidian vault, committed directly as content/),
# penta's vault (Resources/, Resources-en/, the top-level Engendre/Controle/Victoire
# pages) shares a repo with unrelated ScalaJS game source (game/), so content/ here is a
# generated, gitignored mirror rather than a second source of truth — rerun this script
# any time the vault or game/assets changes.
#
# game/assets/ is mirrored at the same relative depth (content/game/assets/) as the real
# game/assets/ sits relative to Resources/ at the repo root, so every existing vault page's
# "../../game/assets/foo.png" reference keeps resolving unchanged — no link rewriting.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

rm -rf content
mkdir -p content/game

cp -r Resources content/Resources
cp -r Resources-en content/Resources-en
cp Controle.md Engendre.md Victoire.md content/
cp -r game/assets content/game/assets

cp scripts/index.md content/index.md
