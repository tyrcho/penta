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
"""

import re
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
        yield m.group("name"), i + 1, span


def main(paths: list[str]) -> int:
    violations = []
    for path in paths:
        with open(path, encoding="utf-8", errors="ignore") as f:
            lines = f.read().splitlines()
        for name, start_line, span in method_spans(lines):
            if span > MAX_LINES:
                violations.append((path, start_line, name, span))
    for path, start_line, name, span in violations:
        print(f"{path}:{start_line}: method '{name}' spans {span} lines (limit {MAX_LINES})")
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
