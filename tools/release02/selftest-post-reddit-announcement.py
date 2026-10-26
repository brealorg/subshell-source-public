#!/usr/bin/env python3
"""RELEASETOOL02F part A selftest: posting the announcement at most once,
against a stand-in Reddit on 127.0.0.1 (no network, fake credentials)."""

from __future__ import annotations

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
from urllib.parse import parse_qs

TOOL = Path(__file__).resolve().parent / "post-reddit-announcement.py"
TITLE = "Subshell 0.86: tidier tabs"
SECRET = "s3cret-pass-word"
CLIENT_SECRET = "client-secret-value"


class FakeReddit:
    def __init__(self) -> None:
        self.posts: list[dict] = []
        self.submits = 0
        self.mode = "ok"  # ok | lose_answer_after_posting | lose_answer_without_posting | refuse
        self.requests: list[str] = []

    def handler(self):
        fake = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args) -> None:
                pass

            def answer(self, body: dict, status: int = 200) -> None:
                data = json.dumps(body).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def do_POST(self) -> None:
                fake.requests.append(self.path)
                length = int(self.headers.get("Content-Length", "0"))
                form = parse_qs(self.rfile.read(length).decode())
                if self.path == "/api/v1/access_token":
                    ok = form.get("password") == [SECRET]
                    self.answer({"access_token": "token-1"} if ok else {"error": "invalid_grant"})
                    return
                if self.path == "/api/submit":
                    fake.submits += 1
                    if fake.mode == "refuse":
                        self.answer({"json": {"errors": [["SUBREDDIT_NOTALLOWED", "not allowed", "sr"]]}})
                        return
                    if fake.mode != "lose_answer_without_posting":
                        fake.posts.append({"subreddit": form["sr"][0], "title": form["title"][0],
                                           "permalink": "/r/subshell/comments/abc/x/"})
                    if fake.mode.startswith("lose_answer"):
                        self.answer({"error": "upstream"}, status=502)
                        return
                    self.answer({"json": {"errors": [], "data": {"url": "https://www.reddit.com/r/subshell/comments/abc/x/"}}})
                    return
                self.answer({}, status=404)

            def do_GET(self) -> None:
                fake.requests.append(self.path)
                if self.path.startswith("/user/announcer/submitted"):
                    self.answer({"data": {"children": [{"data": p} for p in fake.posts]}})
                    return
                self.answer({}, status=404)

        return Handler


def check(name: str, condition: bool, detail: str = "") -> None:
    if not condition:
        print(f"FAIL={name} {detail}")
        sys.exit(1)
    print(f"PASS={name}")


def announcement(folder: Path, state: str = "READY_TO_POST") -> Path:
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "announce-state.env").write_text(f"STATE={state}\nSUBREDDIT=subshell\nVERSION_NAME=0.86-beta\n")
    (folder / "reddit-announcement.md").write_text(f"# {TITLE}\n\nSubshell 0.86-beta is out.\n\n- One.\n")
    return folder


def run(base: Path, server: str, folder: Path, output: str, creds: Path | None, api: str | None = None) -> tuple[int, str]:
    env = dict(os.environ)
    env.pop("SUBSHELL_REDDIT_ANNOUNCE_CREDENTIALS_FILE", None)
    if creds is not None:
        env["SUBSHELL_REDDIT_ANNOUNCE_CREDENTIALS_FILE"] = str(creds)
    result = subprocess.run(
        [sys.executable, str(TOOL), "--announcement", str(folder), "--output", str(base / output),
         "--auth-base", server, "--api-base", api or server, "--read-back-wait", "0"],
        env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    )
    return result.returncode, result.stdout


def main() -> None:
    fake = FakeReddit()
    httpd = ThreadingHTTPServer(("127.0.0.1", 0), fake.handler())
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    server = f"http://127.0.0.1:{httpd.server_address[1]}"
    try:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            creds = base / "credentials.env"
            creds.write_text(f"CLIENT_ID=id\nCLIENT_SECRET={CLIENT_SECRET}\nUSERNAME=announcer\nPASSWORD={SECRET}\n")
            folder = announcement(base / "c")

            code, out = run(base, server, folder, "a1", creds)
            check("POSTS_ONCE", code == 0 and fake.submits == 1 and "RESULT=RELEASETOOL02F_POSTED" in out, out)
            state = (base / "a1/post-state.env").read_text()
            check("RECORDS_THE_POST", "STATE=POSTED" in state and "POST_URL=https://www.reddit.com/r/subshell/comments/abc/x/" in state)

            code, out = run(base, server, folder, "a2", creds)
            check("A_RERUN_FINDS_IT_AND_DOES_NOT_POST", code == 0 and fake.submits == 1 and "ALREADY_POSTED" in out, out)

            fake.posts.clear(); fake.submits = 0; fake.mode = "lose_answer_after_posting"
            code, out = run(base, server, folder, "b1", creds)
            check("LOST_ANSWER_READS_BACK_THE_POST", code == 0 and fake.submits == 1 and "RESULT=RELEASETOOL02F_POSTED" in out, out)

            fake.posts.clear(); fake.submits = 0; fake.mode = "lose_answer_without_posting"
            code, out = run(base, server, folder, "b2", creds)
            check("LOST_ANSWER_NOT_FOUND_IS_NOT_POSTED_AGAIN",
                  code == 2 and fake.submits == 1 and "STATE=OUTCOME_UNKNOWN" in (base / "b2/post-state.env").read_text(), out)

            fake.posts.clear(); fake.submits = 0; fake.mode = "refuse"
            code, out = run(base, server, folder, "b3", creds)
            check("REDDIT_REFUSAL_STOPS_WITH_ITS_CODE",
                  code == 1 and fake.submits == 1 and "STOP=REDDIT_REFUSED_SUBREDDIT_NOTALLOWED" in out, out)

            fake.mode = "ok"; fake.requests.clear()
            code, out = run(base, server, folder, "c1", None)
            check("NO_CREDENTIALS_NO_REQUEST",
                  code == 1 and "STOP=CREDENTIALS_NOT_CONFIGURED" in out and not fake.requests, out)

            wrong = base / "wrong.env"
            wrong.write_text("CLIENT_ID=id\nCLIENT_SECRET=x\nUSERNAME=announcer\nPASSWORD=nope\n")
            fake.submits = 0
            code, out = run(base, server, folder, "c2", wrong)
            check("A_REFUSED_SIGN_IN_POSTS_NOTHING", code == 1 and "STOP=TOKEN_REFUSED" in out and fake.submits == 0, out)

            code, out = run(base, server, folder, "c3", creds, api="https://example.com")
            check("ONLY_REDDIT_OR_LOOPBACK", code == 1 and "STOP=API_BASE_NOT_ALLOWED" in out, out)

            code, out = run(base, server, announcement(base / "d", state="RUNNING"), "c4", creds)
            check("ONLY_A_READY_ANNOUNCEMENT", code == 1 and "STOP=ANNOUNCE_STATE_NOT_READY" in out, out)

            code, out = run(base, server, folder, "a1", creds)
            check("REFUSES_AN_EXISTING_OUTPUT", code == 1 and "STOP=OUTPUT_EXISTS" in out, out)

            written = "".join(p.read_text() for p in base.rglob("post-state.env"))
            check("CREDENTIALS_NEVER_WRITTEN_OR_PRINTED",
                  SECRET not in written and CLIENT_SECRET not in written and SECRET not in out)
    finally:
        httpd.shutdown()
    print("RESULT=RELEASETOOL02F_POST_SELFTEST_PASS")


if __name__ == "__main__":
    main()
