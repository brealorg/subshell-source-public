#!/usr/bin/env python3
"""RELEASETOOL02F selftest: the r/subshell announcement text, no network.

A throwaway qualified source with a changelog and a release state; every
refusal must write nothing.
"""

from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys
import tempfile
from urllib.parse import parse_qs, urlsplit

TOOL = Path(__file__).resolve().parent / "announce-reddit.py"

ENTRY = {
    "versionCode": 250,
    "versionName": "0.86-beta",
    "date": "2026-10-30",
    "headline": "Subshell 0.86: tidier tabs & a new start",
    "highlights": ["Videos pause when you leave the tab.", "Faster cold start? Yes: 100%."],
    "releaseUrl": "https://github.com/brealorg/subshell/releases/tag/v0.86-beta",
}

STATE = {
    "RELEASETOOL02_ORCHESTRATOR_RECORD_VERSION": "1",
    "STATE": "COMPLETE",
    "VERSION_NAME": "0.86-beta",
    "VERSION_CODE": "250",
    "RELEASE_BLOCKING_FAILURE": "NO",
}


def run(base: Path, state: dict[str, str], entries: list[dict], output: str = "out") -> tuple[int, str]:
    source = base / "src"
    changelog = source / "app/src/main/assets/changelog/releases.json"
    changelog.parent.mkdir(parents=True, exist_ok=True)
    changelog.write_text(json.dumps({"schemaVersion": 1, "releases": entries}))
    state_file = base / "release-beta-state.env"
    state_file.write_text("".join(f"{k}={v}\n" for k, v in state.items()))
    result = subprocess.run(
        [sys.executable, str(TOOL), "--release-state", str(state_file),
         "--qualified-source-root", str(source), "--output", str(base / output)],
        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    )
    return result.returncode, result.stdout


def check(name: str, condition: bool, detail: str = "") -> None:
    if not condition:
        print(f"FAIL={name} {detail}")
        sys.exit(1)
    print(f"PASS={name}")


def main() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        base = Path(tmp) / "ready"
        base.mkdir()
        older = dict(ENTRY, versionCode=249, versionName="0.85-beta",
                     releaseUrl="https://github.com/brealorg/subshell/releases/tag/v0.85-beta")
        code, out = run(base, STATE, [ENTRY, older])
        check("READY_TO_POST", code == 0 and "RESULT=RELEASETOOL02F_ANNOUNCEMENT_READY_TO_POST" in out, out)
        text = (base / "out/reddit-announcement.md").read_text()
        check("TITLE_IS_THE_HEADLINE", text.startswith("# Subshell 0.86: tidier tabs & a new start\n"))
        check("EVERY_HIGHLIGHT", all(f"- {h}" in text for h in ENTRY["highlights"]))
        check("LINKS_THE_RELEASE", ENTRY["releaseUrl"] in text)
        check("NOT_THE_OLDER_RELEASE", "0.85-beta" not in text)
        state = (base / "out/announce-state.env").read_text()
        check("NEVER_POSTED_BY_THE_TOOL", "POSTED_BY_TOOL=NO" in state and "STATE=READY_TO_POST" in state)
        url = next(line.split("=", 1)[1] for line in state.splitlines() if line.startswith("SUBMIT_URL="))
        parts = urlsplit(url)
        query = parse_qs(parts.query)
        check("SUBMIT_LINK_IS_R_SUBSHELL", parts.netloc == "www.reddit.com" and parts.path == "/r/subshell/submit")
        check("SUBMIT_LINK_CARRIES_THE_TEXT",
              query.get("title") == [ENTRY["headline"]] and query.get("type") == ["TEXT"] and
              query.get("text", [""])[0] in text and "?" in query.get("text", [""])[0])
        old_url = next(line.split("=", 1)[1] for line in state.splitlines() if line.startswith("OLD_REDDIT_SUBMIT_URL="))
        old_parts = urlsplit(old_url)
        old_query = parse_qs(old_parts.query)
        check("OLD_REDDIT_FALLBACK",
              old_parts.netloc == "old.reddit.com" and old_query.get("selftext") == ["true"] and
              old_query.get("title") == [ENTRY["headline"]] and old_query.get("text") == query.get("text"))

        refusals = [
            ("STAGE_B_NOT_COMPLETE", dict(STATE, STATE="RUNNING"), [ENTRY], "RELEASE_STATE_NOT_COMPLETE"),
            ("BLOCKING_FAILURE", dict(STATE, RELEASE_BLOCKING_FAILURE="YES"), [ENTRY], "RELEASE_STATE_HAS_A_BLOCKING_FAILURE"),
            ("NO_ENTRY", STATE, [older], "CHANGELOG_ENTRY_MISSING"),
            ("OTHER_NAME", dict(STATE, VERSION_NAME="0.86.1-beta"), [ENTRY], "CHANGELOG_ENTRY_VERSION_MISMATCH"),
            ("NO_HIGHLIGHTS", STATE, [dict(ENTRY, highlights=[])], "CHANGELOG_HIGHLIGHTS_INVALID"),
            ("FOREIGN_RELEASE_URL", STATE, [dict(ENTRY, releaseUrl="https://example.com/v0.86-beta")], "CHANGELOG_RELEASE_URL_INVALID"),
        ]
        for name, state_values, entries, stop in refusals:
            case = Path(tmp) / name
            case.mkdir()
            code, out = run(case, state_values, entries)
            check(f"REFUSES_{name}", code == 1 and f"STOP={stop}" in out and not (case / "out").exists(), out)

        code, out = run(base, STATE, [ENTRY])
        check("REFUSES_AN_EXISTING_OUTPUT", code == 1 and "STOP=OUTPUT_EXISTS" in out, out)
    print("RESULT=RELEASETOOL02F_SELFTEST_PASS")


if __name__ == "__main__":
    main()
