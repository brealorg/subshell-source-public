#!/usr/bin/env python3
"""ISSUE259_BASELINE_PROFILE_IS_A_RELEASE_STEP_V1: merges a fresh baseline
profile recording into the shipped profile.

    merge-baseline-profile.py --current app/src/main/baseline-prof.txt \
        --recorded NEW.txt --classes DIR [--classes DIR ...] --output OUT.txt

One cold start does not reach every path the last recording did, so the result
is the union, the new recording's lines first:
- D8 synthetic classes (`$$ExternalSynthetic`) are numbered per build: only the
  new recording's are kept.
- R classes are not in the compiler output: always kept.
- Any other app class is kept only while it still compiles (a .class file in
  one of --classes); library classes are always kept (R8 ignores unknown ones).
"""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys

APP_PREFIX = "app/subshell/prototype/"
RULE = re.compile(r"^[HSP]*L([^;]+);")


def lines(path: Path) -> list[str]:
    return [line.rstrip("\n") for line in path.read_text().splitlines() if line.strip()]


def merge(current: list[str], recorded: list[str], class_dirs: list[Path]) -> tuple[list[str], set[str]]:
    stale: set[str] = set()

    def keep(line: str, from_recording: bool) -> bool:
        match = RULE.match(line)
        if not match or not match.group(1).startswith(APP_PREFIX):
            return True
        name = match.group(1)
        if "$$ExternalSynthetic" in name:
            return from_recording
        if name == APP_PREFIX + "R" or name.startswith(APP_PREFIX + "R$"):
            return True
        if any((root / (name + ".class")).is_file() for root in class_dirs):
            return True
        stale.add(name)
        return False

    seen: set[str] = set()
    merged: list[str] = []
    for source, from_recording in ((recorded, True), (current, False)):
        for line in source:
            if line not in seen and keep(line, from_recording):
                seen.add(line)
                merged.append(line)
    return merged, stale


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--current", type=Path, required=True)
    parser.add_argument("--recorded", type=Path, required=True)
    parser.add_argument("--classes", type=Path, action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    for root in args.classes:
        if not (root / APP_PREFIX).is_dir():
            print(f"STOP=CLASSES_DIR_HAS_NO_APP_CLASSES {root}")
            return 1
    recorded = lines(args.recorded)
    if not any(RULE.match(line) and APP_PREFIX in line for line in recorded):
        print("STOP=RECORDING_HAS_NO_APP_RULES")
        return 1
    current = lines(args.current) if args.current.is_file() else []

    merged, stale = merge(current, recorded, args.classes)
    args.output.write_text("\n".join(merged) + "\n")
    print(f"CURRENT_LINES={len(current)}")
    print(f"RECORDED_LINES={len(recorded)}")
    print(f"MERGED_LINES={len(merged)}")
    print(f"DROPPED_CLASSES={len(stale)}")
    print("RESULT=ISSUE259_BASELINE_PROFILE_MERGED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
