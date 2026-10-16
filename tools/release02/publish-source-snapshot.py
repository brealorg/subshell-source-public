#!/usr/bin/env python3
"""
RELEASETOOL02E: the public source snapshot of a published release (Stage C).

Hard rule 7: private history never goes public. The public source repository
gets one snapshot commit per published release, after Stage B is COMPLETE:

- its tree is byte-identical to the qualified SOURCE_COMMIT (same tree id),
- it is authored and committed by brealorg, its parent is the previous
  snapshot, and it carries the tag v<VERSION_NAME>,
- the private SOURCE_COMMIT is never reachable from the public repository,
- the snapshot passes a gitleaks scan with the repository's .gitleaks.toml,
- the push is atomic and never forced.

A rerun after a stop either repeats the whole stage (nothing reached the
public repository) or finds the tag on the same tree and only verifies it
(SNAPSHOT_REUSED=YES). Only git and gitleaks are used, so the selftest runs
against local repositories without network.
"""

from __future__ import annotations

import argparse
import io
import os
from pathlib import Path
import re
import subprocess
import sys
import tarfile

STATE_VERSION = 1

SNAPSHOT_AUTHOR_NAME = "brealorg"
SNAPSHOT_AUTHOR_EMAIL = "6815997+brealorg@users.noreply.github.com"
SECRET_SCAN_CONFIG = ".gitleaks.toml"

HEX40 = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
VERSION_RE = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$")
VERSION_CODE_RE = re.compile(r"^[1-9][0-9]{0,9}$")


class SnapshotError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise SnapshotError(message)


def run(
    argv: list[str],
    *,
    cwd: Path | None = None,
    check: bool = True,
    env: dict[str, str] | None = None,
    stdin: bytes | None = None,
) -> subprocess.CompletedProcess[bytes]:
    result = subprocess.run(
        argv,
        cwd=str(cwd) if cwd else None,
        input=stdin,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=env,
    )
    if check and result.returncode != 0:
        command = Path(argv[0]).name
        fail(
            f"COMMAND_FAILED_{command}_RC_{result.returncode}:"
            f"{result.stderr.decode(errors='replace').strip()[:400]}"
        )
    return result


def git(root: Path, *args: str, check: bool = True) -> str:
    return run(["git", "-C", str(root), *args], check=check).stdout.decode().strip()


def git_ok(root: Path, *args: str) -> bool:
    return run(["git", "-C", str(root), *args], check=False).returncode == 0


def parse_env(path: Path, missing: str) -> dict[str, str]:
    if not path.is_file():
        fail(missing)
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
    required = {
        "RELEASETOOL02_ORCHESTRATOR_RECORD_VERSION": "1",
        "STATE": "COMPLETE",
        "PRIVATE_APP_SOURCE_PUBLIC": "NO",
        "RELEASE_BLOCKING_FAILURE": "NO",
    }
    if state.get("STATE") != "COMPLETE":
        fail("RELEASE_STATE_NOT_COMPLETE")
    for key, value in required.items():
        if state.get(key) != value:
            fail(f"RELEASE_STATE_INVALID_{key}")
    if not HEX40.fullmatch(state.get("SOURCE_COMMIT", "")):
        fail("SOURCE_COMMIT_INVALID")
    if not HEX40.fullmatch(state.get("SOURCE_TREE", "")):
        fail("SOURCE_TREE_INVALID")
    if not VERSION_RE.fullmatch(state.get("VERSION_NAME", "")):
        fail("VERSION_NAME_INVALID")
    if not VERSION_CODE_RE.fullmatch(state.get("VERSION_CODE", "")):
        fail("VERSION_CODE_INVALID")
    if not SHA256_RE.fullmatch(state.get("APK_SHA256", "")):
        fail("APK_SHA256_INVALID")
    if state.get("TAG") != f"v{state['VERSION_NAME']}":
        fail("TAG_DOES_NOT_MATCH_VERSION_NAME")


def ensure_qualified_source(root: Path, state: dict[str, str]) -> None:
    if not root.is_dir():
        fail("QUALIFIED_SOURCE_ROOT_MISSING")
    if git(root, "branch", "--show-current") != "main":
        fail("QUALIFIED_SOURCE_NOT_MAIN")
    if git(root, "status", "--porcelain", "--untracked-files=all"):
        fail("QUALIFIED_SOURCE_NOT_CLEAN")
    if git(root, "rev-parse", "HEAD") != state["SOURCE_COMMIT"]:
        fail("QUALIFIED_SOURCE_COMMIT_MISMATCH")
    if git(root, "rev-parse", "HEAD^{tree}") != state["SOURCE_TREE"]:
        fail("QUALIFIED_SOURCE_TREE_MISMATCH")
    # A tracked file the repository itself ignores (a stray .pyc once) would
    # be dropped by `git add -A` and break the tree identity; refuse it here
    # with its own reason.
    if git(root, "ls-files", "--cached", "--ignored", "--exclude-standard"):
        fail("TRACKED_FILE_IS_IGNORED")
    if not git_ok(root, "cat-file", "-e", f"HEAD:{SECRET_SCAN_CONFIG}"):
        fail("SECRET_SCAN_CONFIG_MISSING")


def private_commit_is_reachable(clone: Path, source_commit: str) -> bool:
    return git_ok(clone, "cat-file", "-e", f"{source_commit}^{{commit}}")


def snapshot_env() -> dict[str, str]:
    env = dict(os.environ)
    env.update(
        {
            "GIT_AUTHOR_NAME": SNAPSHOT_AUTHOR_NAME,
            "GIT_AUTHOR_EMAIL": SNAPSHOT_AUTHOR_EMAIL,
            "GIT_COMMITTER_NAME": SNAPSHOT_AUTHOR_NAME,
            "GIT_COMMITTER_EMAIL": SNAPSHOT_AUTHOR_EMAIL,
        }
    )
    return env


def replace_tree(clone: Path, source_root: Path, source_commit: str) -> None:
    git(clone, "rm", "-r", "-q", "--ignore-unmatch", "--", ".")
    archive = run(["git", "-C", str(source_root), "archive", source_commit]).stdout
    with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
        for member in tar.getmembers():
            name = member.name
            if name.startswith("/") or ".." in Path(name).parts or name.split("/")[0] == ".git":
                fail("SOURCE_ARCHIVE_UNSAFE_PATH")
        tar.extractall(clone, filter="tar")
    git(clone, "add", "-A")


def run_secret_scan(gitleaks: Path, clone: Path, output: Path) -> None:
    config = clone / SECRET_SCAN_CONFIG
    if not config.is_file():
        fail("SECRET_SCAN_CONFIG_MISSING")
    report = output / "gitleaks-report.json"
    result = run(
        [
            str(gitleaks),
            "dir",
            ".",
            "--config",
            SECRET_SCAN_CONFIG,
            "--redact=100",
            "--no-banner",
            "--exit-code",
            "1",
            "--report-format",
            "json",
            "--report-path",
            str(report),
        ],
        cwd=clone,
        check=False,
    )
    if result.returncode == 1:
        fail(f"SECRET_SCAN_FINDINGS:{report}")
    if result.returncode != 0:
        fail(f"SECRET_SCAN_FAILED_RC_{result.returncode}")


def verify_remote(
    *,
    remote: str,
    output: Path,
    state: dict[str, str],
    snapshot: str,
) -> None:
    verify = output / "verify-clone"
    run(["git", "clone", "--quiet", remote, str(verify)])
    if git(verify, "rev-parse", "refs/remotes/origin/main") != snapshot:
        fail("REMOTE_MAIN_IS_NOT_THE_SNAPSHOT")
    tag = state["TAG"]
    if git(verify, "rev-parse", f"refs/tags/{tag}^{{commit}}", check=False) != snapshot:
        fail("REMOTE_TAG_IS_NOT_THE_SNAPSHOT")
    if git(verify, "rev-parse", f"{snapshot}^{{tree}}") != state["SOURCE_TREE"]:
        fail("REMOTE_SNAPSHOT_TREE_MISMATCH")
    if private_commit_is_reachable(verify, state["SOURCE_COMMIT"]):
        fail("PRIVATE_APP_SOURCE_COMMIT_IS_PUBLIC")


def verify_snapshot_identity(clone: Path, snapshot: str, state: dict[str, str]) -> None:
    if git(clone, "rev-parse", f"{snapshot}^{{tree}}") != state["SOURCE_TREE"]:
        fail("SNAPSHOT_TREE_MISMATCH")
    identity = git(clone, "log", "-1", "--format=%an|%ae|%cn|%ce", snapshot)
    expected = "|".join([SNAPSHOT_AUTHOR_NAME, SNAPSHOT_AUTHOR_EMAIL] * 2)
    if identity != expected:
        fail("SNAPSHOT_AUTHOR_NOT_BREALORG")


def write_state(output: Path, fields: list[tuple[str, str]]) -> Path:
    target = output / "source-snapshot-state.env"
    temp = target.with_suffix(".tmp")
    temp.write_text("".join(f"{k}={v}\n" for k, v in fields))
    temp.replace(target)
    return target


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser()
    p.add_argument("--release-state", required=True,
                   help="release-beta-state.env written by RELEASETOOL02C (STATE=COMPLETE)")
    p.add_argument("--qualified-source-root", required=True)
    p.add_argument("--source-remote", required=True,
                   help="the public source repository (URL or local path)")
    p.add_argument("--source-main-before", required=True,
                   help="the public source main before this release's snapshot")
    p.add_argument("--gitleaks", required=True)
    p.add_argument("--output", required=True)
    return p


def main() -> int:
    args = parser().parse_args()
    release_state = Path(args.release_state)
    source_root = Path(args.qualified_source_root)
    gitleaks = Path(args.gitleaks)
    output = Path(args.output)
    for path, name in (
        (release_state, "RELEASE_STATE"),
        (source_root, "QUALIFIED_SOURCE_ROOT"),
        (gitleaks, "GITLEAKS"),
        (output, "OUTPUT"),
    ):
        if not path.is_absolute():
            fail(f"{name}_NOT_ABSOLUTE")
    if not HEX40.fullmatch(args.source_main_before):
        fail("SOURCE_MAIN_BEFORE_INVALID")
    if not gitleaks.is_file():
        fail("GITLEAKS_MISSING")

    state = parse_env(release_state, "RELEASE_STATE_MISSING")
    validate_release_state(state)
    ensure_qualified_source(source_root, state)

    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        fail("OUTPUT_NOT_EMPTY")

    clone = output / "public-source"
    run(["git", "clone", "--quiet", args.source_remote, str(clone)])
    if private_commit_is_reachable(clone, state["SOURCE_COMMIT"]):
        fail("PRIVATE_APP_SOURCE_COMMIT_IS_PUBLIC")

    tag = state["TAG"]
    tagged = git(clone, "rev-parse", "--verify", "--quiet", f"refs/tags/{tag}^{{commit}}", check=False)
    remote_main = git(clone, "rev-parse", "refs/remotes/origin/main", check=False)

    if tagged:
        # Resume: the snapshot reached the public repository on an earlier run.
        if git(clone, "rev-parse", f"{tagged}^{{tree}}") != state["SOURCE_TREE"]:
            fail("TAG_EXISTS_ON_ANOTHER_TREE")
        if not git_ok(clone, "merge-base", "--is-ancestor", tagged, "refs/remotes/origin/main"):
            fail("TAGGED_SNAPSHOT_NOT_ON_MAIN")
        verify_snapshot_identity(clone, tagged, state)
        snapshot = tagged
        reused = True
    else:
        if remote_main != args.source_main_before:
            fail("SOURCE_PUBLIC_MAIN_MOVED")
        git(clone, "checkout", "--quiet", "-B", "main", "refs/remotes/origin/main")
        replace_tree(clone, source_root, state["SOURCE_COMMIT"])
        run_secret_scan(gitleaks, clone, output)
        message = (
            f"subshell {state['VERSION_NAME']}\n\n"
            f"Source of the published release {tag}: versionCode "
            f"{state['VERSION_CODE']}, APK sha256 {state['APK_SHA256']}.\n"
            "GPL-3.0-or-later. Read-only; issues go to brealorg/subshell.\n"
        )
        env = snapshot_env()
        run(
            ["git", "-C", str(clone), "-c", "commit.gpgsign=false", "commit",
             "--quiet", "-F", "-"],
            env=env,
            stdin=message.encode(),
        )
        snapshot = git(clone, "rev-parse", "HEAD")
        if git(clone, "rev-parse", f"{snapshot}^") != args.source_main_before:
            fail("SNAPSHOT_PARENT_IS_NOT_THE_PREVIOUS_SNAPSHOT")
        verify_snapshot_identity(clone, snapshot, state)
        if private_commit_is_reachable(clone, state["SOURCE_COMMIT"]):
            fail("PRIVATE_APP_SOURCE_COMMIT_IS_PUBLIC")
        run(
            ["git", "-C", str(clone), "-c", "tag.gpgsign=false", "tag", "-a", tag,
             "-m", f"subshell {state['VERSION_NAME']}", snapshot],
            env=env,
        )
        # Atomic and never forced: both refs land together or neither does,
        # and a moved main is refused by git itself.
        git(clone, "push", "--atomic", "--quiet", "origin",
            "refs/heads/main:refs/heads/main", f"refs/tags/{tag}:refs/tags/{tag}")
        reused = False

    verify_remote(remote=args.source_remote, output=output, state=state, snapshot=snapshot)

    target = write_state(
        output,
        [
            ("RELEASETOOL02E_SOURCE_SNAPSHOT_RECORD_VERSION", str(STATE_VERSION)),
            ("STATE", "COMPLETE"),
            ("SOURCE_TREE", state["SOURCE_TREE"]),
            ("VERSION_NAME", state["VERSION_NAME"]),
            ("VERSION_CODE", state["VERSION_CODE"]),
            ("APK_SHA256", state["APK_SHA256"]),
            ("SOURCE_REMOTE", args.source_remote),
            ("SOURCE_MAIN_BEFORE", args.source_main_before),
            ("SNAPSHOT_COMMIT", snapshot),
            ("TAG", tag),
            ("SNAPSHOT_REUSED", "YES" if reused else "NO"),
            ("SNAPSHOT_TREE_IDENTITY", "PASS"),
            ("PRIVATE_APP_SOURCE_PUBLIC", "NO"),
        ],
    )
    print(f"SNAPSHOT_COMMIT={snapshot}")
    print(f"TAG={tag}")
    print(f"SNAPSHOT_REUSED={'YES' if reused else 'NO'}")
    print("SNAPSHOT_TREE_IDENTITY=PASS")
    print("PRIVATE_APP_SOURCE_PUBLIC=NO")
    print(f"SOURCE_SNAPSHOT_STATE={target}")
    print("RESULT=SUBSHELL_RELEASETOOL02E_SOURCE_SNAPSHOT_PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SnapshotError as exc:
        print(f"STOP={exc}", file=sys.stderr)
        print("RELEASETOOL02E_FAILURE=SOURCE_SNAPSHOT_OR_INFRA", file=sys.stderr)
        raise SystemExit(1)
