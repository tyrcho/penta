.PHONY: setup build serve

## Usage
#
# Vault notes live in Resources/ (+ Resources-en/, Controle.md/Engendre.md/Victoire.md),
# authored/generated as an Obsidian vault (see game/README.md's "Docs" section for how
# Resources-en/ and Resources/'s own numbers are regenerated from the game's balance
# code). The site is built with Quartz (https://github.com/jackyzha0/quartz), same
# vendoring approach as the japon repo:
#
#   make serve   # preview locally at http://localhost:8080
#   make build   # build static site into public/
#
# Both targets run `make setup` first, which stages content/ (see
# scripts/stage-content.sh), fetches the pinned Quartz engine, and installs
# dependencies/plugins. Quartz isn't published on npm, so it can't be a normal
# dependency: scripts/setup-quartz.sh clones the exact commit pinned in .quartz-version
# into a gitignored, locally cached .quartz-engine/, then copies the engine files
# (quartz/, quartz.ts, package.json, etc.) into the repo root before building. Bumping
# .quartz-version and rerunning `make setup` is how you upgrade Quartz.

setup:
	./scripts/stage-content.sh
	./scripts/setup-quartz.sh
	npm ci
	npx quartz plugin install --from-config

build: setup
	npx quartz build

serve: setup
	npx quartz build --serve
