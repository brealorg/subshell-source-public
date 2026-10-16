#!/usr/bin/env python3
# SUBSHELL_CONTRACT02F_RELEASE_QUALIFICATION_VERIFIER_V1

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import subprocess


REQUIRED_KEYS = {
    "CONTRACT02_QUALIFICATION_RECORD_VERSION",
    "SOURCE_COMMIT",
    "SOURCE_TREE",
    "BASE_COMMIT",
    "COMMIT_COUNT",
    "CHANGED_PATH_COUNT",
    "MANIFEST_PATH",
    "MANIFEST_SHA256",
    "PRIMARY_FAILURES",
    "PREDECESSOR_FAILURES",
    "RELEASE_BLOCKING_FAILURES",
    "CONTRACT_GATE",
    "UNIT_TESTS",
    "UNIT_TEST_RC",
    "UNIT_KOTLIN_WARNING_COUNT",
    "RELEASE_GATE",
}


def fail(message: str) -> None:
    print(f"STOP=CONTRACT02_RELEASE_QUALIFICATION_REJECTED reason={message}")
    raise SystemExit(1)


def git(source: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(source), *args],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return result.stdout.strip()


def parse_record(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw:
            continue
        if "=" not in raw:
            fail(f"record_line_{number}_missing_equals")
        key, value = raw.split("=", 1)
        if not key or key in values:
            fail(f"record_line_{number}_duplicate_or_empty_key")
        values[key] = value

    missing = sorted(REQUIRED_KEYS - values.keys())
    if missing:
        fail("record_missing_keys_" + ",".join(missing))
    return values


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--record", required=True)
    parser.add_argument("--source-root", required=True)
    args = parser.parse_args()

    if not args.record:
        fail("qualification_record_not_configured")

    source = Path(args.source_root).resolve()
    record = Path(args.record).expanduser()

    if not record.is_absolute():
        fail("qualification_record_must_be_absolute")
    record = record.resolve()
    if not record.is_file():
        fail("qualification_record_not_found")
    if record.is_relative_to(source):
        fail("qualification_record_must_be_external_to_source_tree")

    values = parse_record(record)

    if values["CONTRACT02_QUALIFICATION_RECORD_VERSION"] != "1":
        fail("unsupported_record_version")

    if git(source, "status", "--porcelain"):
        fail("source_worktree_not_clean")

    head = git(source, "rev-parse", "HEAD")
    tree = git(source, "rev-parse", "HEAD^{tree}")

    if values["SOURCE_COMMIT"] != head:
        fail("source_commit_mismatch")
    if values["SOURCE_TREE"] != tree:
        fail("source_tree_mismatch")

    try:
        subprocess.run(
            [
                "git",
                "-C",
                str(source),
                "merge-base",
                "--is-ancestor",
                values["BASE_COMMIT"],
                head,
            ],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    except subprocess.CalledProcessError:
        fail("base_not_ancestor_of_current_source")

    manifest_rel = Path(values["MANIFEST_PATH"])
    if manifest_rel.is_absolute() or ".." in manifest_rel.parts:
        fail("manifest_path_not_canonical_relative")

    manifest = (source / manifest_rel).resolve()
    if not manifest.is_relative_to(source) or not manifest.is_file():
        fail("manifest_not_inside_current_source")

    if sha256(manifest) != values["MANIFEST_SHA256"]:
        fail("manifest_sha256_mismatch")

    for key in (
        "COMMIT_COUNT",
        "CHANGED_PATH_COUNT",
        "PRIMARY_FAILURES",
        "PREDECESSOR_FAILURES",
        "RELEASE_BLOCKING_FAILURES",
        "UNIT_TEST_RC",
        "UNIT_KOTLIN_WARNING_COUNT",
    ):
        try:
            value = int(values[key])
        except ValueError:
            fail(f"{key.lower()}_not_integer")
        if value < 0:
            fail(f"{key.lower()}_negative")

    if values["CONTRACT_GATE"] != "PASS":
        fail("contract_gate_not_pass")
    if values["RELEASE_BLOCKING_FAILURES"] != "0":
        fail("release_blocking_failures_nonzero")
    if values["UNIT_TESTS"] != "PASS":
        fail("unit_tests_not_pass")
    if values["UNIT_TEST_RC"] != "0":
        fail("unit_test_rc_nonzero")
    if values["UNIT_KOTLIN_WARNING_COUNT"] != "0":
        fail("unit_kotlin_warnings_nonzero")
    if values["RELEASE_GATE"] != "PASS":
        fail("release_gate_not_pass")

    print("PASS=CONTRACT02_RELEASE_QUALIFICATION_RECORD_VERSION")
    print("PASS=CONTRACT02_RELEASE_QUALIFICATION_SOURCE_IDENTITY")
    print("PASS=CONTRACT02_RELEASE_QUALIFICATION_MANIFEST_IDENTITY")
    print("PASS=CONTRACT02_RELEASE_QUALIFICATION_CONTRACT_GATE")
    print("PASS=CONTRACT02_RELEASE_QUALIFICATION_UNIT_TESTS")
    print("PASS=CONTRACT02_RELEASE_QUALIFICATION_RELEASE_GATE")
    print(f"QUALIFIED_SOURCE_COMMIT={head}")
    print(f"QUALIFIED_SOURCE_TREE={tree}")
    print(f"QUALIFICATION_RECORD={record}")
    print("RESULT=SUBSHELL_CONTRACT02_RELEASE_QUALIFICATION_VERIFICATION_PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        fail(f"git_command_failed_{exc.returncode}")
