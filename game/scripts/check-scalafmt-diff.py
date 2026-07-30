#!/usr/bin/env python3
"""Diff-aware scalafmt check: only fails if scalafmt would reformat a line actually
staged in this commit — not pre-existing drift elsewhere in the file.

scalafmt has no line-range/diff-scoping of its own; `scalafmt --check`/`--stdout`
always evaluates the whole file. This codebase went without a working pre-commit hook
for a while, so several files have pre-existing formatting drift nobody asked this
pass to fix (see check-method-length.py's own doc for the same problem on a different
check). This script runs scalafmt read-only (--stdout, no re-stage), computes its own
would-be reformat diff via difflib, and keeps only the hunks whose *current-file* line
range overlaps lines this commit's staged diff actually touched (git diff --cached
-U0) — the same overlap technique check-method-length.py uses.
"""

import difflib
import os
import re
import subprocess
import sys

GIT_HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")

# scalafmt discovers .scalafmt.conf relative to its own working directory, not the
# target file's path — it must be invoked from game/ (where that config lives), even
# though every path this script receives is repo-root-relative (matching git's own
# convention, since the pre-commit hook runs everything from the repo root).
GAME_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def staged_touched_lines(path: str) -> set[int] | None:
    """1-based line numbers added/modified in the staged diff for `path` (new-file line
    numbers). None if the file has no staged diff at all — treated by the caller as
    "check unconditionally" rather than silently skipping a file that slipped through."""
    result = subprocess.run(["git", "diff", "--cached", "-U0", "--", path], capture_output=True, text=True)
    if result.returncode != 0 or not result.stdout:
        return None
    touched: set[int] = set()
    for line in result.stdout.splitlines():
        m = GIT_HUNK_RE.match(line)
        if not m:
            continue
        count = int(m.group(2)) if m.group(2) is not None else 1
        if count == 0:
            continue
        start = int(m.group(1))
        touched.update(range(start, start + count))
    return touched


def reformat_touched_ranges(path: str) -> list[tuple[int, int]]:
    """[start, end) 1-based line ranges (in the file's *current* content) that scalafmt
    would rewrite right now, or [] if scalafmt itself failed (parse error, not found,
    etc.) — not this script's job to diagnose a broken scalafmt run.

    A pure "insert" opcode (scalafmt wants to add line(s) — e.g. a required blank line —
    without touching any existing line) has i1 == i2 by difflib's own definition, since no
    original lines are consumed. That collapses to an empty [start, end) range under the
    plain (i1+1, i2+1) formula used for replace/delete, so it could never overlap anything
    and was previously dropped by the `i1 != i2` filter entirely — silently letting a real
    pure-insertion reformat pass this check. Anchored instead to the pair of current-file
    lines immediately surrounding the insertion point (i1, i1+2), so it overlaps the
    touched set whenever either neighboring line was part of this commit's diff.
    """
    with open(path, encoding="utf-8") as f:
        current_lines = f.read().splitlines()
    game_relative = os.path.relpath(os.path.abspath(path), GAME_DIR)
    formatted = subprocess.run(
        ["scalafmt", "--stdout", game_relative], cwd=GAME_DIR, capture_output=True, text=True
    )
    if formatted.returncode != 0:
        return []
    formatted_lines = formatted.stdout.splitlines()
    matcher = difflib.SequenceMatcher(None, current_lines, formatted_lines, autojunk=False)
    ranges = []
    for tag, i1, i2, _, _ in matcher.get_opcodes():
        if tag == "equal":
            continue
        ranges.append((i1, i1 + 2) if i1 == i2 else (i1 + 1, i2 + 1))
    return ranges


def main(paths: list[str]) -> int:
    violations = []
    for path in paths:
        touched = staged_touched_lines(path)
        for start, end in reformat_touched_ranges(path):
            if touched is not None and not any(start <= n < end for n in touched):
                continue
            violations.append((path, start, end - 1))
    for path, start, end in violations:
        line_desc = f"line {start}" if start == end else f"lines {start}-{end}"
        print(f"{path}: needs reformatting at {line_desc} (run scalafmt and re-stage)")
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
