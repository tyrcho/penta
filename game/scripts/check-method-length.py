#!/usr/bin/env python3
"""Flags Scala methods that span more than MAX_LINES lines.

Stands in for scalastyle's MethodLengthChecker, which cannot be used here:
scalastyle was never published past Scala 2.12 (its parser predates Dotty and
cannot read this project's Scala 3 optional-braces syntax at all — every file
fails with a raw parse error, not a real finding).

This is a line-based heuristic, not a real parser: a method's span is measured
from its `def` line to the next line (skipping blank/comment-only lines) whose
indentation is <= the `def` line's own indentation, or end of file — except
that a candidate boundary line is skipped (scanning resumes past it) if it
itself still contains the signature-closing `=` (not `==`/`=>`/`<=`/`>=`/`!=`),
since scalafmt routinely dedents a wrapped multi-line signature's closing
`)`/return-type/`=` back to the `def` line's own indent, which would otherwise
look like the method already ended right there. A `def` nested inside another
`def` (e.g. a local helper) is measured independently — both are fine for this
script's one job: catching a method that's grown well past the project's own
"<= 30 lines per method" rule, not exactly reproducing what a real Scala
parser would say.

Only reports a violation whose line range overlaps lines actually staged in
this commit (`git diff --cached -U0`) — this codebase has plenty of
pre-existing oversized methods nobody asked this pass to fix (GameApp.scala's
UI setup functions, Simulator.scala's CLI arg parsing, ...); the rule that
matters when *introducing* a length check into a legacy file is "don't make
it worse in what you touch", not "retroactively fix everything in any file
you happen to edit". A brand-new (untracked) file has no diff to scope
against, so every method in it is checked unconditionally.
"""

import re
import subprocess
import sys

MAX_LINES = 30
MODIFIERS = r"(?:private|protected|override|final|implicit|inline|lazy)\s+"
DEF_RE = re.compile(rf"^(?P<indent>\s*)(?:{MODIFIERS})*def\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)")
# Any new binding/type starting at this line — a genuine sibling, never a
# continuation of the current method's own signature, regardless of whether it
# contains "=" (a one-line `val`/`def` does).
NEW_DECL_RE = re.compile(rf"^\s*(?:{MODIFIERS})*(?:def|val|var|object|trait|class|case class|given|extension)\s+\w")
# A bare `=` (not `==`, `=>`, `<=`, `>=`, `!=`) anywhere in the line — present on
# a would-be boundary line only when that line is still closing the signature
# (e.g. "  ): Seq[Standing] =") rather than starting something new.
ASSIGN_RE = re.compile(r"(?<![=<>!])=(?!=|>)")


def indent_of(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def method_spans(lines: list[str]):
    for i, line in enumerate(lines):
        m = DEF_RE.match(line)
        if not m:
            continue
        def_indent = len(m.group("indent"))
        end = len(lines)
        j = i + 1
        while j < len(lines):
            candidate = lines[j]
            stripped = candidate.strip()
            if stripped == "" or stripped.startswith("//"):
                j += 1
                continue
            if indent_of(candidate) <= def_indent:
                # A line back at (or above) the def's own indent that does NOT
                # start any new declaration but still contains a bare `=` is
                # the tail of a wrapped multi-line signature (e.g.
                # "  ): Seq[Standing] =") — keep scanning past it. A line that
                # starts a new def/val/etc. always ends the previous method,
                # even if it's itself a one-liner containing "=".
                if not NEW_DECL_RE.match(candidate) and ASSIGN_RE.search(candidate):
                    j += 1
                    continue
                end = j
                break
            j += 1
        # end - i (raw line distance) would count a trailing comment/blank
        # run — often documentation for the *next* declaration, not this
        # method's own body — as if it were part of this method. Count only
        # non-blank, non-comment-only lines in [i, end) instead.
        span = sum(
            1
            for k in range(i, end)
            if lines[k].strip() != "" and not lines[k].strip().startswith("//")
        )
        # end (0-indexed, exclusive) is the boundary line; the method's own last physical
        # line is 1-based `end` — yielded alongside the trimmed `span` so the caller can
        # check diff-overlap against the method's real extent, not just its counted lines.
        yield m.group("name"), i + 1, end, span


HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")


def changed_lines(path: str) -> set[int] | None:
    """1-based line numbers added/modified in the staged diff for `path` (new-file line
    numbers, matching method_spans' own numbering). None if the file has no staged diff
    at all — a brand-new file's first commit has one hunk covering everything, so None in
    practice only means "not actually staged", and the caller treats that as "check it
    unconditionally" rather than silently skipping a file that slipped through."""
    result = subprocess.run(["git", "diff", "--cached", "-U0", "--", path], capture_output=True, text=True)
    if result.returncode != 0 or not result.stdout:
        return None
    touched: set[int] = set()
    for line in result.stdout.splitlines():
        m = HUNK_RE.match(line)
        if not m:
            continue
        count = int(m.group(2)) if m.group(2) is not None else 1
        if count == 0:
            continue  # pure deletion hunk — nothing added to flag here
        start = int(m.group(1))
        touched.update(range(start, start + count))
    return touched


def main(paths: list[str]) -> int:
    violations = []
    for path in paths:
        with open(path, encoding="utf-8", errors="ignore") as f:
            lines = f.read().splitlines()
        touched = changed_lines(path)
        for name, start_line, end_line, span in method_spans(lines):
            if span <= MAX_LINES:
                continue
            if touched is not None and not any(start_line <= n <= end_line for n in touched):
                continue
            violations.append((path, start_line, name, span))
    for path, start_line, name, span in violations:
        print(f"{path}:{start_line}: method '{name}' spans {span} lines (limit {MAX_LINES})")
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
