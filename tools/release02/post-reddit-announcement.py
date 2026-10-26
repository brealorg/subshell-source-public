#!/usr/bin/env python3
"""RELEASETOOL02F_REDDIT_ANNOUNCEMENT_POSTED_ONCE_V1 (#255, part A): posts the
announcement part C wrote, as the announcing account, at most once.

Input: the part C output folder (announce-state.env STATE=READY_TO_POST and
reddit-announcement.md). Credentials come only from the file named by
$SUBSHELL_REDDIT_ANNOUNCE_CREDENTIALS_FILE (KEY=VALUE lines: CLIENT_ID,
CLIENT_SECRET, USERNAME, PASSWORD — a Reddit "script" app of that account).
They are never printed, logged or written anywhere.

- Before posting it reads the account's newest submissions: a post in
  r/subshell with the same title means it was announced (ALREADY_POSTED).
- One submit, never retried. A lost response is "outcome unknown": it reads
  back once; found = POSTED, not found = OUTCOME_UNKNOWN, and it does not post
  again (the owner looks, and posts by part C's link if needed).
- Reddit refusing the post (json errors) stops it with Reddit's error code.
- Non-blocking: the release is published whatever happens here; a rerun with
  a new --output only reads back if the first run posted.

It writes post-state.env (STATE=POSTED|ALREADY_POSTED|OUTCOME_UNKNOWN,
POST_URL). Reddit is closing public API access (press: March 2027); then this
stops with a network/authorization error and part C's link remains.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
from pathlib import Path
import re
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen

CREDENTIALS_ENV = "SUBSHELL_REDDIT_ANNOUNCE_CREDENTIALS_FILE"
SUBREDDIT = "subshell"
AUTH_BASE = "https://www.reddit.com"
API_BASE = "https://oauth.reddit.com"
TIMEOUT_SECONDS = 30
READ_BACK_WAIT_SECONDS = 5.0
USERNAME_RE = re.compile(r"^[A-Za-z0-9_-]{3,20}$")


class PostError(RuntimeError):
    pass


class OutcomeUnknown(RuntimeError):
    pass


def fail(code: str) -> None:
    raise PostError(code)


def allowed_base(base: str, real: str) -> str:
    """The real Reddit host, or a loopback stand-in for the selftest."""
    if base == real:
        return base
    parts = urlsplit(base)
    if parts.scheme == "http" and parts.hostname == "127.0.0.1" and parts.path in ("", "/"):
        return base.rstrip("/")
    fail("API_BASE_NOT_ALLOWED")
    return ""


def parse_env(path: Path, missing: str) -> dict[str, str]:
    if not path.is_file():
        fail(missing)
    result: dict[str, str] = {}
    for raw in path.read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def credentials() -> dict[str, str]:
    name = os.environ.get(CREDENTIALS_ENV, "")
    if not name:
        fail("CREDENTIALS_NOT_CONFIGURED")
    values = parse_env(Path(name), "CREDENTIALS_FILE_MISSING")
    for key in ("CLIENT_ID", "CLIENT_SECRET", "USERNAME", "PASSWORD"):
        if not values.get(key):
            fail(f"CREDENTIALS_{key}_MISSING")
    if not USERNAME_RE.fullmatch(values["USERNAME"]):
        fail("CREDENTIALS_USERNAME_INVALID")
    return values


def read_announcement(folder: Path) -> tuple[dict[str, str], str, str]:
    state = parse_env(folder / "announce-state.env", "ANNOUNCE_STATE_MISSING")
    if state.get("STATE") != "READY_TO_POST":
        fail("ANNOUNCE_STATE_NOT_READY")
    if state.get("SUBREDDIT") != SUBREDDIT:
        fail("ANNOUNCE_SUBREDDIT_MISMATCH")
    text_file = folder / "reddit-announcement.md"
    if not text_file.is_file():
        fail("ANNOUNCEMENT_TEXT_MISSING")
    content = text_file.read_text()
    if not content.startswith("# ") or "\n\n" not in content:
        fail("ANNOUNCEMENT_TEXT_MALFORMED")
    title_line, body = content.split("\n\n", 1)
    return state, title_line[2:].strip(), body.rstrip("\n")


def user_agent(username: str) -> str:
    return f"script:io.github.brealorg.subshell.announcer:v1 (by /u/{username})"


def http(request: Request) -> dict:
    # One request, no retry (mutations never use a retrying client).
    with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
        return json.loads(response.read().decode())


def access_token(auth_base: str, creds: dict[str, str]) -> str:
    basic = base64.b64encode(f"{creds['CLIENT_ID']}:{creds['CLIENT_SECRET']}".encode()).decode()
    request = Request(
        f"{auth_base}/api/v1/access_token",
        data=urlencode({
            "grant_type": "password",
            "username": creds["USERNAME"],
            "password": creds["PASSWORD"],
        }).encode(),
        headers={"Authorization": f"Basic {basic}", "User-Agent": user_agent(creds["USERNAME"])},
        method="POST",
    )
    try:
        answer = http(request)
    except HTTPError as error:
        fail(f"TOKEN_HTTP_{error.code}")
    except (URLError, OSError, ValueError):
        fail("TOKEN_UNREACHABLE")
    token = answer.get("access_token")
    if not isinstance(token, str) or not token:
        fail("TOKEN_REFUSED")
    return token


def existing_post(api_base: str, token: str, username: str, title: str) -> str | None:
    request = Request(
        f"{api_base}/user/{username}/submitted?sort=new&limit=25&raw_json=1",
        headers={"Authorization": f"Bearer {token}", "User-Agent": user_agent(username)},
    )
    try:
        answer = http(request)
    except HTTPError as error:
        fail(f"READ_BACK_HTTP_{error.code}")
    except (URLError, OSError, ValueError):
        fail("READ_BACK_UNREACHABLE")
    for child in answer.get("data", {}).get("children", []):
        data = child.get("data", {}) if isinstance(child, dict) else {}
        if data.get("subreddit", "").lower() == SUBREDDIT and data.get("title") == title:
            permalink = data.get("permalink", "")
            return f"https://www.reddit.com{permalink}" if permalink.startswith("/") else str(data.get("url", ""))
    return None


def submit(api_base: str, token: str, username: str, title: str, text: str) -> str:
    request = Request(
        f"{api_base}/api/submit",
        data=urlencode({
            "sr": SUBREDDIT, "kind": "self", "title": title, "text": text,
            "api_type": "json", "sendreplies": "true", "resubmit": "false",
        }).encode(),
        headers={"Authorization": f"Bearer {token}", "User-Agent": user_agent(username)},
        method="POST",
    )
    try:
        answer = http(request)
    except HTTPError as error:
        if error.code >= 500:
            raise OutcomeUnknown(f"SUBMIT_HTTP_{error.code}")
        fail(f"SUBMIT_HTTP_{error.code}")
    except (URLError, OSError, ValueError) as error:
        raise OutcomeUnknown("SUBMIT_NO_ANSWER") from error
    errors = answer.get("json", {}).get("errors") or []
    if errors:
        code = str(errors[0][0]) if isinstance(errors[0], list) and errors[0] else "UNKNOWN"
        fail(f"REDDIT_REFUSED_{re.sub(r'[^A-Z0-9_]', '_', code.upper())}")
    url = answer.get("json", {}).get("data", {}).get("url")
    if not isinstance(url, str) or not url:
        raise OutcomeUnknown("SUBMIT_ANSWER_WITHOUT_URL")
    return url


def write_state(output: Path, state: str, version: str, url: str = "", reason: str = "") -> None:
    output.mkdir(parents=True)
    lines = [
        "RELEASETOOL02F_POST_RECORD_VERSION=1",
        f"STATE={state}",
        f"SUBREDDIT={SUBREDDIT}",
        f"VERSION_NAME={version}",
        f"POST_URL={url}",
    ]
    if reason:
        lines.append(f"REASON={reason}")
    (output / "post-state.env").write_text("\n".join(lines) + "\n")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--announcement", required=True, help="part C's output folder")
    parser.add_argument("--output", required=True)
    parser.add_argument("--auth-base", default=AUTH_BASE)
    parser.add_argument("--api-base", default=API_BASE)
    parser.add_argument("--read-back-wait", type=float, default=READ_BACK_WAIT_SECONDS)
    args = parser.parse_args(argv)
    output = Path(args.output)
    try:
        if output.exists():
            fail("OUTPUT_EXISTS")
        auth_base = allowed_base(args.auth_base, AUTH_BASE)
        api_base = allowed_base(args.api_base, API_BASE)
        state, title, text = read_announcement(Path(args.announcement))
        creds = credentials()
        token = access_token(auth_base, creds)
        found = existing_post(api_base, token, creds["USERNAME"], title)
        if found is not None:
            write_state(output, "ALREADY_POSTED", state["VERSION_NAME"], found)
            print(f"POST_URL={found}")
            print("RESULT=RELEASETOOL02F_ALREADY_POSTED")
            return 0
        try:
            url = submit(api_base, token, creds["USERNAME"], title, text)
        except OutcomeUnknown as unknown:
            time.sleep(max(0.0, args.read_back_wait))
            found = existing_post(api_base, token, creds["USERNAME"], title)
            if found is None:
                write_state(output, "OUTCOME_UNKNOWN", state["VERSION_NAME"], reason=str(unknown))
                print(f"REASON={unknown}")
                print("RESULT=RELEASETOOL02F_OUTCOME_UNKNOWN_NOT_POSTED_AGAIN")
                return 2
            url = found
        write_state(output, "POSTED", state["VERSION_NAME"], url)
        print(f"POST_URL={url}")
        print("RESULT=RELEASETOOL02F_POSTED")
        return 0
    except PostError as error:
        print(f"STOP={error}")
        print("RESULT=RELEASETOOL02F_POST_STOPPED")
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
