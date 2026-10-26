#!/usr/bin/env python3
"""RELEASETOOL02F_REDDIT_ANNOUNCEMENT_TEXT_V1 (#255, part C): the r/subshell
announcement of a published release, written for the owner to post.

Runs after Stage B is COMPLETE (input: release-beta-state.env). It reads the
release's entry in the bundled changelog of the qualified source
(app/src/main/assets/changelog/releases.json: headline, the approved
highlights, releaseUrl) and writes, into a new output folder:

- reddit-announcement.md: the title and the post text, ready to paste;
- announce-state.env: STATE=READY_TO_POST, the version and prefilled
  r/subshell submit links (www, old.reddit as fallback; opening one posts
  nothing).

No network, no credentials, no Reddit account: posting stays the owner's
(part A, posting as subshell_dev, needs its own decision). The release
report records "Announced in r/subshell: yes/no" with the post's link.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys
from urllib.parse import quote

SUBREDDIT = "subshell"
# Both fill the form from the link (owner-checked 2026-10-26). Reddit is
# restricting old.reddit (announced 2026-09-30), so www is the link and
# old.reddit only the fallback.
SUBMIT_BASE = f"https://www.reddit.com/r/{SUBREDDIT}/submit"
OLD_SUBMIT_BASE = f"https://old.reddit.com/r/{SUBREDDIT}/submit"
SITE_URL = "https://brealorg.github.io/subshell/"
ALL_RELEASES_URL = "https://brealorg.github.io/subshell/changelog/"
CHANGELOG_PATH = "app/src/main/assets/changelog/releases.json"
VERSION_RE = re.compile(r"^[0-9]+\.[0-9]+(\.[0-9]+)?(-[0-9A-Za-z.]+)?$")
VERSION_CODE_RE = re.compile(r"^[1-9][0-9]{0,8}$")
RELEASE_URL_RE = re.compile(r"^https://github\.com/brealorg/subshell/releases/tag/v[0-9A-Za-z.\-]+$")
# Reddit limits: a title is at most 300 characters.
TITLE_MAX = 300


class AnnounceError(RuntimeError):
    pass


def fail(code: str) -> None:
    raise AnnounceError(code)


def parse_env(path: Path) -> dict[str, str]:
    if not path.is_file():
        fail("RELEASE_STATE_MISSING")
    result: dict[str, str] = {}
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"RELEASE_STATE_MALFORMED_LINE_{number}")
        key, value = line.split("=", 1)
        if key in result:
            fail(f"RELEASE_STATE_DUPLICATE_KEY_{key}")
        result[key] = value
    return result


def validate_release_state(state: dict[str, str]) -> None:
    if state.get("STATE") != "COMPLETE":
        fail("RELEASE_STATE_NOT_COMPLETE")
    if state.get("RELEASE_BLOCKING_FAILURE") != "NO":
        fail("RELEASE_STATE_HAS_A_BLOCKING_FAILURE")
    if not VERSION_RE.fullmatch(state.get("VERSION_NAME", "")):
        fail("VERSION_NAME_INVALID")
    if not VERSION_CODE_RE.fullmatch(state.get("VERSION_CODE", "")):
        fail("VERSION_CODE_INVALID")


def release_entry(changelog: dict, version_name: str, version_code: int) -> dict:
    releases = changelog.get("releases")
    if not isinstance(releases, list):
        fail("CHANGELOG_MALFORMED")
    matches = [r for r in releases if isinstance(r, dict) and r.get("versionCode") == version_code]
    if len(matches) != 1:
        fail("CHANGELOG_ENTRY_MISSING")
    entry = matches[0]
    if entry.get("versionName") != version_name:
        fail("CHANGELOG_ENTRY_VERSION_MISMATCH")
    headline = entry.get("headline")
    highlights = entry.get("highlights")
    release_url = entry.get("releaseUrl")
    if not isinstance(headline, str) or not headline.strip() or len(headline) > TITLE_MAX:
        fail("CHANGELOG_HEADLINE_INVALID")
    if not isinstance(highlights, list) or not highlights or \
            not all(isinstance(h, str) and h.strip() for h in highlights):
        fail("CHANGELOG_HIGHLIGHTS_INVALID")
    if not isinstance(release_url, str) or not RELEASE_URL_RE.fullmatch(release_url):
        fail("CHANGELOG_RELEASE_URL_INVALID")
    return entry


def announcement(entry: dict) -> tuple[str, str]:
    """The post's title and Markdown text."""
    title = entry["headline"].strip()
    lines = [f"Subshell {entry['versionName']} is out.", ""]
    lines += [f"- {highlight.strip()}" for highlight in entry["highlights"]]
    lines += [
        "",
        f"The app offers the update itself; the APK is here: {entry['releaseUrl']}",
        "",
        f"Website: {SITE_URL} · All releases: {ALL_RELEASES_URL}",
    ]
    return title, "\n".join(lines)


def submit_url(title: str, text: str) -> str:
    return (
        f"{SUBMIT_BASE}?type=TEXT"
        f"&title={quote(title, safe='')}"
        f"&text={quote(text, safe='')}"
    )


def old_submit_url(title: str, text: str) -> str:
    return (
        f"{OLD_SUBMIT_BASE}?selftext=true"
        f"&title={quote(title, safe='')}"
        f"&text={quote(text, safe='')}"
    )


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--release-state", required=True)
    parser.add_argument("--qualified-source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args(argv)
    try:
        output = Path(args.output)
        if output.exists():
            fail("OUTPUT_EXISTS")
        state = parse_env(Path(args.release_state))
        validate_release_state(state)
        changelog_file = Path(args.qualified_source_root) / CHANGELOG_PATH
        if not changelog_file.is_file():
            fail("CHANGELOG_MISSING")
        try:
            changelog = json.loads(changelog_file.read_text())
        except json.JSONDecodeError:
            fail("CHANGELOG_MALFORMED")
        entry = release_entry(changelog, state["VERSION_NAME"], int(state["VERSION_CODE"]))
        title, text = announcement(entry)
        url = submit_url(title, text)
        output.mkdir(parents=True)
        (output / "reddit-announcement.md").write_text(f"# {title}\n\n{text}\n")
        (output / "announce-state.env").write_text("\n".join([
            "RELEASETOOL02F_ANNOUNCE_RECORD_VERSION=1",
            "STATE=READY_TO_POST",
            f"SUBREDDIT={SUBREDDIT}",
            f"VERSION_NAME={state['VERSION_NAME']}",
            f"VERSION_CODE={state['VERSION_CODE']}",
            f"SUBMIT_URL={url}",
            f"OLD_REDDIT_SUBMIT_URL={old_submit_url(title, text)}",
            "POSTED_BY_TOOL=NO",
            "",
        ]))
    except AnnounceError as error:
        print(f"STOP={error}")
        print("RESULT=RELEASETOOL02F_ANNOUNCEMENT_STOPPED")
        return 1
    print(f"ANNOUNCEMENT_FILE={output / 'reddit-announcement.md'}")
    print(f"SUBMIT_URL={url}")
    print("RESULT=RELEASETOOL02F_ANNOUNCEMENT_READY_TO_POST")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
