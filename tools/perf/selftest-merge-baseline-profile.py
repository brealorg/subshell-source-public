#!/usr/bin/env python3
"""ISSUE259_BASELINE_PROFILE_IS_A_RELEASE_STEP_V1 selftest: the profile merge,
on throwaway files."""

from __future__ import annotations

from pathlib import Path
import subprocess
import sys
import tempfile

TOOL = Path(__file__).resolve().parent / "merge-baseline-profile.py"

CURRENT = """\
Landroidx/core/Old;
HSPLapp/subshell/prototype/Kept;->run()V
HSPLapp/subshell/prototype/Removed;->run()V
HSPLapp/subshell/prototype/MainActivity$$ExternalSynthetic1;->run()V
Lapp/subshell/prototype/R$drawable;
"""

RECORDED = """\
HSPLapp/subshell/prototype/MainActivity$$ExternalSynthetic7;->run()V
HSPLapp/subshell/prototype/New;->run()V
HSPLapp/subshell/prototype/Kept;->run()V
"""


def run(tmp: Path, recorded: str) -> tuple[int, str, list[str]]:
    classes = tmp / "classes"
    for name in ("Kept", "New"):
        path = classes / "app/subshell/prototype" / (name + ".class")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"")
    (tmp / "current.txt").write_text(CURRENT)
    (tmp / "recorded.txt").write_text(recorded)
    out = tmp / "out.txt"
    if out.exists():
        out.unlink()
    result = subprocess.run(
        [sys.executable, str(TOOL), "--current", str(tmp / "current.txt"),
         "--recorded", str(tmp / "recorded.txt"), "--classes", str(classes),
         "--output", str(out)],
        capture_output=True, text=True,
    )
    merged = out.read_text().splitlines() if out.exists() else []
    return result.returncode, result.stdout, merged


def main() -> int:
    with tempfile.TemporaryDirectory() as raw:
        tmp = Path(raw)
        code, stdout, merged = run(tmp, RECORDED)
        assert code == 0, stdout
        assert merged == [
            "HSPLapp/subshell/prototype/MainActivity$$ExternalSynthetic7;->run()V",
            "HSPLapp/subshell/prototype/New;->run()V",
            "HSPLapp/subshell/prototype/Kept;->run()V",
            "Landroidx/core/Old;",
            "Lapp/subshell/prototype/R$drawable;",
        ], merged
        assert "DROPPED_CLASSES=1" in stdout, stdout

        # A recording without app code (signed out, wrong package) is refused.
        code, stdout, merged = run(tmp, "Landroidx/core/Only;\n")
        assert code == 1 and "RECORDING_HAS_NO_APP_RULES" in stdout and merged == [], stdout
    print("RESULT=ISSUE259_MERGE_SELFTEST_PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
