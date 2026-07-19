#!/usr/bin/env bash
# Vendors the pinned Quartz engine (see .quartz-version) into the repo root.
# Quartz isn't published as an npm package, so this stands in for `npm install`:
# it caches a clone of jackyzha0/quartz in .quartz-engine/ and only re-copies
# the engine files when the pinned version actually changes.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

VERSION=$(tr -d '[:space:]' < .quartz-version)
CACHE_DIR=".quartz-engine"
MARKER="$CACHE_DIR/.checked-out-version"
ENGINE_PATHS=(
  quartz
  quartz.ts
  package.json
  package-lock.json
  tsconfig.json
  globals.d.ts
  index.d.ts
  LICENSE.txt
  .node-version
  .npmrc
  .prettierrc
  .prettierignore
  .gitattributes
)

if [ -f "$MARKER" ] && [ "$(cat "$MARKER")" = "$VERSION" ]; then
  echo "Quartz engine already at $VERSION, skipping fetch"
else
  if [ ! -d "$CACHE_DIR/.git" ]; then
    git clone https://github.com/jackyzha0/quartz.git "$CACHE_DIR"
  fi
  git -C "$CACHE_DIR" fetch --depth 1 origin "$VERSION"
  git -C "$CACHE_DIR" checkout --detach "$VERSION"
  echo "$VERSION" > "$MARKER"
fi

for path in "${ENGINE_PATHS[@]}"; do
  rm -rf "$path"
  cp -R "$CACHE_DIR/$path" "$path"
done

# Local customizations to vendored engine files (e.g. favicon) live in
# overrides/, mirroring the paths above, and are copied on top last.
if [ -d overrides ]; then
  cp -R overrides/. .
fi
