#!/usr/bin/env python3
"""RELEASETOOL02E selftest: the public source snapshot against local repositories.

No network: the "public" repository is a local bare repository, the qualified
source a throwaway main, and gitleaks a stand-in script whose exit code the
case chooses. Every refusal must leave the public repository unchanged.
"""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys
import tempfile

TOOL = Path(__file__).resolve().parent / "publish-source-snapshot.py"
AUTHOR = "brealorg|6815997+brealorg@users.noreply.github.com"
# SECAUDIT18: the test world scans with the repository's real config, so the
# pinned sha256 is checked here exactly as in a release.
SCAN_CONFIG = (Path(__file__).resolve().parents[2] / ".gitleaks.toml").read_text()


def sh(*argv: str, cwd: Path | None = None) -> str:
    env = dict(os.environ)
    env.update(
        GIT_AUTHOR_NAME="Private Dev", GIT_AUTHOR_EMAIL="dev@example.invalid",
        GIT_COMMITTER_NAME="Private Dev", GIT_COMMITTER_EMAIL="dev@example.invalid",
    )
    return subprocess.run(
        argv, cwd=cwd, env=env, check=True, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    ).stdout.strip()


def git(repo: Path, *args: str) -> str:
    return sh("git", "-C", str(repo), "-c", "commit.gpgsign=false", "-c", "tag.gpgsign=false", *args)


class World:
    def __init__(self, base: Path) -> None:
        self.base = base
        self.private = base / "private"
        self.public = base / "public.git"
        self.seed = base / "seed"
        self.gitleaks = base / "gitleaks"
        self.runs = 0

        sh("git", "init", "-q", "-b", "main", str(self.private))
        self.write(".gitleaks.toml", SCAN_CONFIG)
        self.write(".gitignore", "__pycache__/\n")
        self.write("README.md", "subshell\n")
        self.write("app/Main.kt", "fun main() {}\n")
        git(self.private, "add", "-A")
        git(self.private, "commit", "-q", "-m", "private one")
        self.write("app/Main.kt", "fun main() { println() }\n")
        git(self.private, "add", "-A")
        git(self.private, "commit", "-q", "-m", "private two")

        sh("git", "init", "-q", "--bare", "-b", "main", str(self.public))
        sh("git", "init", "-q", "-b", "main", str(self.seed))
        (self.seed / "README.md").write_text("clean start\n")
        git(self.seed, "add", "-A")
        git(self.seed, "commit", "-q", "-m", "clean start")
        git(self.seed, "push", "-q", str(self.public), "main")
        self.main_before = git(self.public, "rev-parse", "main")

        self.set_gitleaks(0)

    def write(self, rel: str, text: str) -> None:
        path = self.private / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def set_gitleaks(self, code: int) -> None:
        self.gitleaks.write_text(f"#!/bin/sh\nexit {code}\n")
        self.gitleaks.chmod(0o755)

    def state(self, **override: str) -> Path:
        head = git(self.private, "rev-parse", "HEAD")
        fields = {
            "RELEASETOOL02_ORCHESTRATOR_RECORD_VERSION": "1",
            "STATE": "COMPLETE",
            "SOURCE_COMMIT": head,
            "SOURCE_TREE": git(self.private, "rev-parse", "HEAD^{tree}"),
            "VERSION_NAME": "0.82-beta",
            "VERSION_CODE": "246",
            "APK_SHA256": "a" * 64,
            "TAG": "v0.82-beta",
            "PRIVATE_APP_SOURCE_PUBLIC": "NO",
            "RELEASE_BLOCKING_FAILURE": "NO",
        }
        fields.update(override)
        self.runs += 1
        path = self.base / f"release-beta-state-{self.runs}.env"
        path.write_text("".join(f"{k}={v}\n" for k, v in fields.items()))
        return path

    def publish(self, state: Path, main_before: str | None = None) -> subprocess.CompletedProcess[str]:
        self.runs += 1
        return subprocess.run(
            [
                sys.executable, str(TOOL),
                "--release-state", str(state),
                "--qualified-source-root", str(self.private),
                "--source-remote", str(self.public),
                "--source-main-before", main_before or self.main_before,
                "--gitleaks", str(self.gitleaks),
                "--output", str(self.base / f"out-{self.runs}"),
            ],
            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
        )

    def public_refs(self) -> str:
        return git(self.public, "for-each-ref", "--format=%(refname) %(objectname)")


FAILURES: list[str] = []


def check(name: str, condition: bool, detail: str = "") -> None:
    if condition:
        print(f"PASS={name}")
    else:
        print(f"FAIL={name} {detail}".rstrip())
        FAILURES.append(name)


def expect_stop(name: str, world: World, result: subprocess.CompletedProcess[str], stop: str, refs_before: str) -> None:
    check(
        name,
        result.returncode == 1 and f"STOP={stop}" in result.stderr and world.public_refs() == refs_before,
        f"rc={result.returncode} stderr={result.stderr.strip()[:300]}",
    )


def good_case(base: Path) -> None:
    w = World(base)
    tree = git(w.private, "rev-parse", "HEAD^{tree}")
    head = git(w.private, "rev-parse", "HEAD")
    first = w.publish(w.state())
    snapshot = git(w.public, "rev-parse", "main")
    check("GOOD_RELEASE_PUBLISHES_A_SNAPSHOT",
          first.returncode == 0 and "SNAPSHOT_REUSED=NO" in first.stdout, first.stderr[:300])
    check("SNAPSHOT_TREE_IS_THE_SOURCE_TREE", git(w.public, "rev-parse", "main^{tree}") == tree)
    check("SNAPSHOT_PARENT_IS_THE_PREVIOUS_SNAPSHOT", git(w.public, "rev-parse", "main^") == w.main_before)
    check("SNAPSHOT_IS_AUTHORED_BY_BREALORG",
          git(w.public, "log", "-1", "--format=%an|%ae", "main") == AUTHOR
          and git(w.public, "log", "-1", "--format=%cn|%ce", "main") == AUTHOR)
    check("TAG_POINTS_AT_THE_SNAPSHOT", git(w.public, "rev-parse", "v0.82-beta^{commit}") == snapshot)
    check("PRIVATE_HISTORY_STAYS_PRIVATE",
          subprocess.run(["git", "-C", str(w.public), "cat-file", "-e", f"{head}^{{commit}}"],
                         capture_output=True).returncode != 0
          and git(w.public, "rev-list", "--count", "main") == "2")

    refs = w.public_refs()
    again = w.publish(w.state())
    check("RERUN_REUSES_THE_SNAPSHOT",
          again.returncode == 0 and "SNAPSHOT_REUSED=YES" in again.stdout and w.public_refs() == refs,
          again.stderr[:300])


def refusal_cases(base: Path) -> None:
    w = World(base / "refusals")
    refs = w.public_refs()

    expect_stop("STAGE_B_NOT_COMPLETE_IS_REFUSED", w,
                w.publish(w.state(STATE="REMOTE_APK_VERIFIED")), "RELEASE_STATE_NOT_COMPLETE", refs)
    expect_stop("TAG_MUST_MATCH_THE_VERSION", w,
                w.publish(w.state(TAG="v0.81-beta")), "TAG_DOES_NOT_MATCH_VERSION_NAME", refs)
    expect_stop("MOVED_PUBLIC_MAIN_IS_REFUSED", w,
                w.publish(w.state(), main_before="b" * 40), "SOURCE_PUBLIC_MAIN_MOVED", refs)

    w.set_gitleaks(1)
    expect_stop("SECRET_SCAN_FINDING_STOPS_BEFORE_PUSH", w, w.publish(w.state()), "SECRET_SCAN_FINDINGS:", refs)
    w.set_gitleaks(2)
    expect_stop("SECRET_SCAN_ERROR_STOPS_BEFORE_PUSH", w, w.publish(w.state()), "SECRET_SCAN_FAILED_RC_2", refs)
    w.set_gitleaks(0)

    w.write("scratch.txt", "dirty\n")
    expect_stop("DIRTY_QUALIFIED_SOURCE_IS_REFUSED", w, w.publish(w.state()), "QUALIFIED_SOURCE_NOT_CLEAN", refs)
    (w.private / "scratch.txt").unlink()

    w.write("tools/__pycache__/x.pyc", "cache\n")
    git(w.private, "add", "-f", "tools/__pycache__/x.pyc")
    git(w.private, "commit", "-q", "-m", "stray cache")
    expect_stop("TRACKED_IGNORED_FILE_IS_REFUSED", w, w.publish(w.state()), "TRACKED_FILE_IS_IGNORED", refs)
    git(w.private, "rm", "-q", "--cached", "tools/__pycache__/x.pyc")
    git(w.private, "commit", "-q", "-m", "drop cache")
    (w.private / "tools/__pycache__/x.pyc").unlink()

    w.write(".gitattributes", "README.md export-ignore\n")
    git(w.private, "add", "-A")
    git(w.private, "commit", "-q", "-m", "export-ignore")
    expect_stop("SNAPSHOT_THAT_DIFFERS_FROM_THE_SOURCE_IS_REFUSED", w,
                w.publish(w.state()), "SNAPSHOT_TREE_MISMATCH", refs)
    git(w.private, "rm", "-q", ".gitattributes")
    git(w.private, "commit", "-q", "-m", "no export-ignore")

    git(w.private, "rm", "-q", ".gitleaks.toml")
    git(w.private, "commit", "-q", "-m", "no scan config")
    expect_stop("MISSING_SCAN_CONFIG_IS_REFUSED", w, w.publish(w.state()), "SECRET_SCAN_CONFIG_MISSING", refs)
    w.write(".gitleaks.toml", SCAN_CONFIG)
    git(w.private, "add", "-A")
    git(w.private, "commit", "-q", "-m", "scan config back")

    w.write(".gitleaks.toml", SCAN_CONFIG + '\n[[allowlists]]\npaths = [".*"]\n')
    git(w.private, "add", "-A")
    git(w.private, "commit", "-q", "-m", "widened scan config")
    expect_stop("CHANGED_SCAN_CONFIG_IS_REFUSED", w, w.publish(w.state()), "SECRET_SCAN_CONFIG_CHANGED", refs)
    w.write(".gitleaks.toml", SCAN_CONFIG)
    git(w.private, "add", "-A")
    git(w.private, "commit", "-q", "-m", "scan config as pinned")

    git(w.private, "push", "-q", str(w.public), "main:refs/heads/leak")
    leaked = w.public_refs()
    expect_stop("REACHABLE_PRIVATE_COMMIT_IS_REFUSED", w, w.publish(w.state()),
                "PRIVATE_APP_SOURCE_COMMIT_IS_PUBLIC", leaked)
    git(w.public, "update-ref", "-d", "refs/heads/leak")
    git(w.public, "gc", "-q", "--prune=now")

    git(w.seed, "tag", "-a", "v0.82-beta", "-m", "other", "main")
    git(w.seed, "push", "-q", str(w.public), "v0.82-beta")
    tagged = w.public_refs()
    expect_stop("TAG_ON_ANOTHER_TREE_IS_REFUSED", w, w.publish(w.state()), "TAG_EXISTS_ON_ANOTHER_TREE", tagged)


def pin_case() -> None:
    import hashlib
    import importlib.util

    spec = importlib.util.spec_from_file_location("release02e", TOOL)
    assert spec and spec.loader
    tool = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(tool)
    check(
        "SCAN_CONFIG_PIN_MATCHES_THE_REPOSITORY",
        hashlib.sha256(SCAN_CONFIG.encode()).hexdigest() == tool.SECRET_SCAN_CONFIG_SHA256,
        "update SECRET_SCAN_CONFIG_SHA256 with .gitleaks.toml",
    )


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="releasetool02e-") as raw:
        base = Path(raw)
        pin_case()
        good_case(base / "good")
        refusal_cases(base)
    if FAILURES:
        print("RESULT=SUBSHELL_RELEASETOOL02E_SELFTEST_FAIL")
        return 1
    print("RESULT=SUBSHELL_RELEASETOOL02E_SELFTEST_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
