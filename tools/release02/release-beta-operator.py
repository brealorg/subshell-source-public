#!/usr/bin/env python3
"""
RELEASETOOL02D — single-entry resumable beta-release operator pipeline.

Stage order:
    INIT
      -> qualify-delta.sh
    QUALIFIED
      -> prepare-signed-preview.sh
    SIGNED
      -> smoke-preview-device.sh
    DEVICE_SMOKED
      -> RELEASETOOL02C release-beta.py
    COMPLETE

The operator owns orchestration and immutable state only. It does not directly
build, sign, install, upload, push, or create a release. Those mutations remain
inside their already-qualified predecessor tools.
"""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import sys
from typing import Iterable


STATE_VERSION = 1
STATES = ("INIT", "QUALIFIED", "SIGNED", "DEVICE_SMOKED", "COMPLETE")
STATE_RANK = {state: rank for rank, state in enumerate(STATES)}
HEX40 = re.compile(r"^[0-9a-f]{40}$")
VERSION_RE = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$")
REPO_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")


class OperatorError(RuntimeError):
    pass


class StageFailure(OperatorError):
    def __init__(self, stage: str, rc: int, log: Path):
        super().__init__(f"{stage}_FAILED_RC_{rc}")
        self.stage = stage
        self.rc = rc
        self.log = log


def fail(message: str) -> None:
    raise OperatorError(message)


def run(
    argv: list[str],
    *,
    cwd: Path | None = None,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        argv,
        cwd=str(cwd) if cwd else None,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check and result.returncode != 0:
        fail(
            f"COMMAND_FAILED_{Path(argv[0]).name}_RC_{result.returncode}:"
            f"{result.stderr.strip()[:500]}"
        )
    return result


def stream_stage(
    argv: list[str],
    *,
    cwd: Path,
    log: Path,
    stage: str,
) -> None:
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open("w") as sink:
        proc = subprocess.Popen(
            argv,
            cwd=str(cwd),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            bufsize=1,
        )
        assert proc.stdout is not None
        for line in proc.stdout:
            print(line, end="")
            sink.write(line)
        rc = proc.wait()
    if rc != 0:
        raise StageFailure(stage, rc, log)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def parse_env(path: Path) -> dict[str, str]:
    if not path.is_file():
        fail(f"STATE_OR_RECORD_MISSING_{path.name}")
    result: dict[str, str] = {}
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"ENV_MALFORMED_LINE_{path.name}_{number}")
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key or key in result:
            fail(f"ENV_DUPLICATE_OR_EMPTY_KEY_{path.name}_{number}")
        if "\n" in value or "\r" in value or "\x00" in value:
            fail(f"ENV_UNSAFE_VALUE_{path.name}_{key}")
        result[key] = value
    return result


def atomic_write_env(path: Path, fields: Iterable[tuple[str, str]]) -> None:
    temp = path.with_name(path.name + ".tmp")
    temp.write_text("".join(f"{key}={value}\n" for key, value in fields))
    temp.replace(path)


def git(root: Path, *args: str) -> str:
    return run(["git", "-C", str(root), *args]).stdout.strip()


def require_abs(path: Path, marker: str) -> Path:
    if not path.is_absolute():
        fail(f"{marker}_NOT_ABSOLUTE")
    return path.resolve()


def relative_inside(root: Path, candidate: Path, marker: str) -> str:
    try:
        return candidate.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        fail(f"{marker}_OUTSIDE_SOURCE_ROOT")


def resolve_manifest(root: Path, value: str) -> tuple[Path, str]:
    raw = Path(value).expanduser()
    path = raw if raw.is_absolute() else root / raw
    path = path.resolve()
    rel = relative_inside(root, path, "QUALIFICATION_MANIFEST")
    if not path.is_file():
        fail("QUALIFICATION_MANIFEST_MISSING")
    return path, rel


def next_attempt_dir(output: Path, prefix: str) -> Path:
    for index in range(1, 1000):
        candidate = output / f"{prefix}-attempt-{index:03d}"
        candidate_log = output / f"{prefix}-attempt-{index:03d}.log"
        # Directory-producing stages keep their console log as a sibling.
        # Either artifact reserves the attempt number so a failed predecessor
        # cannot have its forensic log overwritten on resume.
        if not candidate.exists() and not candidate_log.exists():
            return candidate
    fail(f"ATTEMPT_SPACE_EXHAUSTED_{prefix}")


def next_attempt_log(output: Path, prefix: str) -> Path:
    for index in range(1, 1000):
        candidate = output / f"{prefix}-attempt-{index:03d}.log"
        if not candidate.exists():
            return candidate
    fail(f"ATTEMPT_LOG_SPACE_EXHAUSTED_{prefix}")


def validate_source(root: Path) -> tuple[str, str]:
    if not root.is_dir():
        fail("QUALIFIED_SOURCE_ROOT_MISSING")
    if git(root, "branch", "--show-current") != "main":
        fail("QUALIFIED_SOURCE_NOT_MAIN")
    if git(root, "status", "--porcelain", "--untracked-files=all"):
        fail("QUALIFIED_SOURCE_NOT_CLEAN")
    return git(root, "rev-parse", "HEAD"), git(root, "rev-parse", "HEAD^{tree}")


def resolve_base(root: Path, base: str) -> str:
    result = run(
        ["git", "-C", str(root), "rev-parse", f"{base}^{{commit}}"],
        check=False,
    )
    if result.returncode != 0:
        fail("BASE_COMMIT_NOT_FOUND")
    resolved = result.stdout.strip()
    if not HEX40.fullmatch(resolved):
        fail("BASE_COMMIT_INVALID")
    check = run(
        ["git", "-C", str(root), "merge-base", "--is-ancestor", resolved, "HEAD"],
        check=False,
    )
    if check.returncode != 0:
        fail("BASE_NOT_ANCESTOR_OF_SOURCE")
    return resolved


def input_fields(
    *,
    source_commit: str,
    source_tree: str,
    base_commit: str,
    manifest_rel: str,
    manifest_sha: str,
    args: argparse.Namespace,
    notes_sha: str,
) -> list[tuple[str, str]]:
    return [
        ("SOURCE_COMMIT", source_commit),
        ("SOURCE_TREE", source_tree),
        ("BASE_COMMIT", base_commit),
        ("QUALIFICATION_MANIFEST", manifest_rel),
        ("QUALIFICATION_MANIFEST_SHA256", manifest_sha),
        ("VERSION_NAME", args.version_name),
        ("VERSION_CODE", str(args.version_code)),
        ("REQUESTED_SERIAL", args.serial or ""),
        ("PUBLIC_REPOSITORY", args.public_repo),
        ("PUBLIC_TARGET_COMMIT", args.public_target),
        ("PUBLIC_MAIN_BEFORE", args.public_main_before),
        ("PAGES_REPOSITORY", args.pages_repo),
        ("PAGES_MAIN_BEFORE", args.pages_main_before),
        ("PREVIOUS_VERSION", args.previous_version),
        ("TITLE", args.title),
        ("NOTES_FILE", str(Path(args.notes_file).resolve())),
        ("NOTES_SHA256", notes_sha),
        ("MANIFEST_URL", args.manifest_url),
        ("SITE_URL", args.site_url),
        ("PROPAGATION_ATTEMPTS", str(args.propagation_attempts)),
        ("PROPAGATION_DELAY_SECONDS", str(args.propagation_delay_seconds)),
    ]


def expected_input_map(fields: list[tuple[str, str]]) -> dict[str, str]:
    return dict(fields)


def write_state(
    *,
    state_path: Path,
    state: str,
    immutable: list[tuple[str, str]],
    dynamic: dict[str, str],
) -> None:
    if state not in STATE_RANK:
        fail("STATE_INVALID")
    fields = [
        ("RELEASETOOL02_OPERATOR_RECORD_VERSION", str(STATE_VERSION)),
        ("STATE", state),
        *immutable,
    ]
    for key in sorted(dynamic):
        fields.append((key, dynamic[key]))
    atomic_write_env(state_path, fields)


def validate_resume_state(
    *,
    state_path: Path,
    immutable: list[tuple[str, str]],
) -> tuple[str, dict[str, str]]:
    values = parse_env(state_path)
    if values.get("RELEASETOOL02_OPERATOR_RECORD_VERSION") != "1":
        fail("RESUME_STATE_VERSION_UNSUPPORTED")
    state = values.get("STATE", "")
    if state not in STATE_RANK:
        fail("RESUME_STATE_INVALID")
    expected = expected_input_map(immutable)
    for key, value in expected.items():
        if values.get(key) != value:
            fail(f"RESUME_INPUT_MISMATCH_{key}")
    return state, values


def require_record_identity(
    *,
    record: Path,
    source_commit: str,
    source_tree: str,
    version_name: str | None = None,
    version_code: int | None = None,
) -> dict[str, str]:
    values = parse_env(record)
    if values.get("SOURCE_COMMIT") != source_commit:
        fail(f"RECORD_SOURCE_COMMIT_MISMATCH_{record.name}")
    if values.get("SOURCE_TREE") != source_tree:
        fail(f"RECORD_SOURCE_TREE_MISMATCH_{record.name}")
    if version_name is not None and values.get("VERSION_NAME") != version_name:
        fail(f"RECORD_VERSION_NAME_MISMATCH_{record.name}")
    if version_code is not None and values.get("VERSION_CODE") != str(version_code):
        fail(f"RECORD_VERSION_CODE_MISMATCH_{record.name}")
    return values


def validate_success_artifacts(
    *,
    state: str,
    values: dict[str, str],
    args: argparse.Namespace,
) -> None:
    rank = STATE_RANK[state]

    if rank >= STATE_RANK["QUALIFIED"]:
        record = Path(values.get("QUALIFICATION_RECORD", ""))
        if not record.is_file():
            fail("RESUME_QUALIFICATION_RECORD_MISSING")
        if sha256_file(record) != values.get("QUALIFICATION_RECORD_SHA256"):
            fail("RESUME_QUALIFICATION_RECORD_SHA_MISMATCH")
        q = require_record_identity(
            record=record,
            source_commit=values["SOURCE_COMMIT"],
            source_tree=values["SOURCE_TREE"],
        )
        if q.get("RELEASE_GATE") != "PASS":
            fail("RESUME_QUALIFICATION_GATE_NOT_PASS")

    if rank >= STATE_RANK["SIGNED"]:
        record = Path(values.get("RELEASE_RECORD", ""))
        apk = Path(values.get("PREVIEW_APK", ""))
        if not record.is_file() or not apk.is_file():
            fail("RESUME_SIGNED_ARTIFACT_MISSING")
        if sha256_file(record) != values.get("RELEASE_RECORD_SHA256"):
            fail("RESUME_RELEASE_RECORD_SHA_MISMATCH")
        if sha256_file(apk) != values.get("PREVIEW_APK_SHA256"):
            fail("RESUME_PREVIEW_APK_SHA_MISMATCH")
        r = require_record_identity(
            record=record,
            source_commit=values["SOURCE_COMMIT"],
            source_tree=values["SOURCE_TREE"],
            version_name=args.version_name,
            version_code=args.version_code,
        )
        if r.get("APK_SHA256") != values.get("PREVIEW_APK_SHA256"):
            fail("RESUME_RELEASE_RECORD_APK_SHA_MISMATCH")

    if rank >= STATE_RANK["DEVICE_SMOKED"]:
        log = Path(values.get("DEVICE_SMOKE_LOG", ""))
        if not log.is_file():
            fail("RESUME_DEVICE_SMOKE_LOG_MISSING")
        if sha256_file(log) != values.get("DEVICE_SMOKE_LOG_SHA256"):
            fail("RESUME_DEVICE_SMOKE_LOG_SHA_MISMATCH")
        if "RESULT=SUBSHELL_RELEASETOOL01B_EXACT_PREVIEW_DEVICE_SMOKE_PASS" not in log.read_text():
            fail("RESUME_DEVICE_SMOKE_RESULT_MISSING")

    if rank >= STATE_RANK["COMPLETE"]:
        final = Path(values.get("RELEASE_BETA_STATE", ""))
        if not final.is_file():
            fail("RESUME_RELEASE_BETA_STATE_MISSING")
        if sha256_file(final) != values.get("RELEASE_BETA_STATE_SHA256"):
            fail("RESUME_RELEASE_BETA_STATE_SHA_MISMATCH")
        f = parse_env(final)
        if f.get("STATE") != "COMPLETE":
            fail("RESUME_RELEASE_BETA_NOT_COMPLETE")
        if f.get("RELEASE_BLOCKING_FAILURE") != "NO":
            fail("RESUME_RELEASE_BETA_BLOCKING")


def build_qualification_command(
    *,
    source_root: Path,
    base: str,
    manifest: Path,
    output: Path,
) -> list[str]:
    return [
        "bash",
        str(source_root / "tools/contract02/qualify-delta.sh"),
        "--base",
        base,
        "--manifest",
        str(manifest),
        "--output",
        str(output),
    ]


def build_signed_command(
    *,
    source_root: Path,
    qualification_record: Path,
    version_name: str,
    version_code: int,
    output: Path,
) -> list[str]:
    return [
        "bash",
        str(source_root / "tools/release01/prepare-signed-preview.sh"),
        "--qualification-record",
        str(qualification_record),
        "--version-name",
        version_name,
        "--version-code",
        str(version_code),
        "--output",
        str(output),
    ]


def build_smoke_command(
    *,
    source_root: Path,
    release_record: Path,
    serial: str | None,
) -> list[str]:
    command = [
        "bash",
        str(source_root / "tools/release01/smoke-preview-device.sh"),
        "--release-record",
        str(release_record),
        "--qualified-source-root",
        str(source_root),
    ]
    if serial:
        command.extend(["--serial", serial])
    return command


def build_publication_command(
    *,
    source_root: Path,
    release_record: Path,
    args: argparse.Namespace,
    output: Path,
) -> list[str]:
    return [
        sys.executable,
        str(source_root / "tools/release02/release-beta.py"),
        "--release-record",
        str(release_record),
        "--qualified-source-root",
        str(source_root),
        "--public-repo",
        args.public_repo,
        "--public-target",
        args.public_target,
        "--title",
        args.title,
        "--notes-file",
        str(Path(args.notes_file).resolve()),
        "--public-main-before",
        args.public_main_before,
        "--pages-repo",
        args.pages_repo,
        "--pages-main-before",
        args.pages_main_before,
        "--previous-version",
        args.previous_version,
        "--manifest-url",
        args.manifest_url,
        "--site-url",
        args.site_url,
        "--output",
        str(output),
        "--propagation-attempts",
        str(args.propagation_attempts),
        "--propagation-delay-seconds",
        str(args.propagation_delay_seconds),
    ]


def extract_line_value(path: Path, prefix: str) -> str:
    for line in path.read_text().splitlines():
        if line.startswith(prefix):
            return line[len(prefix):]
    return ""


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser()
    p.add_argument("--qualified-source-root", required=True)
    p.add_argument("--base", required=True)
    p.add_argument("--qualification-manifest", required=True)
    p.add_argument("--version-name", required=True)
    p.add_argument("--version-code", required=True, type=int)
    p.add_argument("--serial")

    p.add_argument("--public-repo", required=True)
    p.add_argument("--public-target", required=True)
    p.add_argument("--title", required=True)
    p.add_argument("--notes-file", required=True)
    p.add_argument("--public-main-before", required=True)
    p.add_argument("--pages-repo", required=True)
    p.add_argument("--pages-main-before", required=True)
    p.add_argument("--previous-version", required=True)
    p.add_argument("--manifest-url", required=True)
    p.add_argument("--site-url", required=True)

    p.add_argument("--output", required=True)
    p.add_argument("--resume", action="store_true")
    p.add_argument("--propagation-attempts", type=int, default=36)
    p.add_argument("--propagation-delay-seconds", type=float, default=5.0)
    return p


def validate_args(args: argparse.Namespace) -> None:
    if not VERSION_RE.fullmatch(args.version_name):
        fail("VERSION_NAME_INVALID")
    if args.version_code <= 0:
        fail("VERSION_CODE_INVALID")
    if not VERSION_RE.fullmatch(args.previous_version):
        fail("PREVIOUS_VERSION_INVALID")
    if args.previous_version == args.version_name:
        fail("PREVIOUS_VERSION_EQUALS_CURRENT")
    for value, marker in (
        (args.public_repo, "PUBLIC_REPO"),
        (args.pages_repo, "PAGES_REPO"),
    ):
        if not REPO_RE.fullmatch(value):
            fail(f"{marker}_INVALID")
    for value, marker in (
        (args.public_target, "PUBLIC_TARGET"),
        (args.public_main_before, "PUBLIC_MAIN_BEFORE"),
        (args.pages_main_before, "PAGES_MAIN_BEFORE"),
    ):
        if not HEX40.fullmatch(value):
            fail(f"{marker}_INVALID")
    if "\n" in args.title or "\r" in args.title or not args.title.strip():
        fail("TITLE_INVALID")
    if args.propagation_attempts <= 0:
        fail("PROPAGATION_ATTEMPTS_INVALID")
    if args.propagation_delay_seconds < 0:
        fail("PROPAGATION_DELAY_INVALID")


def classify_stage_failure(exc: StageFailure, output: Path) -> None:
    print(f"STOP={exc}", file=sys.stderr)
    print(f"FAILED_STAGE={exc.stage}", file=sys.stderr)
    print(f"STAGE_LOG={exc.log}", file=sys.stderr)
    print("PRIMARY_FAILURE=NO", file=sys.stderr)

    if exc.stage == "QUALIFICATION":
        record = exc.log.parent / "qualification-record.env"
        if record.is_file():
            values = parse_env(record)
            print(
                f"QUALIFICATION_PRIMARY_FAILURES={values.get('PRIMARY_FAILURES','UNKNOWN')}",
                file=sys.stderr,
            )
            print(
                f"QUALIFICATION_PREDECESSOR_FAILURES={values.get('PREDECESSOR_FAILURES','UNKNOWN')}",
                file=sys.stderr,
            )
            print(
                f"QUALIFICATION_RELEASE_BLOCKING_FAILURES={values.get('RELEASE_BLOCKING_FAILURES','UNKNOWN')}",
                file=sys.stderr,
            )
        print("PREDECESSOR_FAILURE=YES_QUALIFICATION_GATE", file=sys.stderr)
    elif exc.stage == "SIGNED":
        print("PREDECESSOR_FAILURE=YES_RELEASETOOL01A", file=sys.stderr)
    elif exc.stage == "DEVICE_SMOKE":
        print("PREDECESSOR_FAILURE=YES_RELEASETOOL01B", file=sys.stderr)
    elif exc.stage == "PUBLICATION":
        print("PREDECESSOR_FAILURE=YES_RELEASETOOL02C", file=sys.stderr)
    else:
        print("PREDECESSOR_FAILURE=YES_UNKNOWN_STAGE", file=sys.stderr)

    print("RELEASE_BLOCKING_FAILURE=YES", file=sys.stderr)
    print(f"OUTPUT={output}", file=sys.stderr)


def main() -> int:
    args = parser().parse_args()
    validate_args(args)

    source_root = require_abs(
        Path(args.qualified_source_root).expanduser(),
        "QUALIFIED_SOURCE_ROOT",
    )
    output = require_abs(Path(args.output).expanduser(), "OUTPUT")
    notes = require_abs(Path(args.notes_file).expanduser(), "NOTES_FILE")
    if not notes.is_file():
        fail("NOTES_FILE_MISSING")

    source_commit, source_tree = validate_source(source_root)
    base_commit = resolve_base(source_root, args.base)
    manifest_path, manifest_rel = resolve_manifest(
        source_root,
        args.qualification_manifest,
    )

    immutable = input_fields(
        source_commit=source_commit,
        source_tree=source_tree,
        base_commit=base_commit,
        manifest_rel=manifest_rel,
        manifest_sha=sha256_file(manifest_path),
        args=args,
        notes_sha=sha256_file(notes),
    )

    state_path = output / "operator-state.env"

    if args.resume:
        if not output.is_dir():
            fail("RESUME_OUTPUT_MISSING")
        state, values = validate_resume_state(
            state_path=state_path,
            immutable=immutable,
        )
        validate_success_artifacts(state=state, values=values, args=args)
        dynamic = {
            key: value
            for key, value in values.items()
            if key not in {
                "RELEASETOOL02_OPERATOR_RECORD_VERSION",
                "STATE",
                *expected_input_map(immutable).keys(),
            }
        }
    else:
        if output.exists() and any(output.iterdir()):
            fail("FRESH_OUTPUT_NOT_EMPTY")
        output.mkdir(parents=True, exist_ok=True)
        state = "INIT"
        dynamic: dict[str, str] = {}
        write_state(
            state_path=state_path,
            state=state,
            immutable=immutable,
            dynamic=dynamic,
        )

    print("RELEASETOOL02D_OPERATOR_VERSION=1")
    print(f"RESUME={'YES' if args.resume else 'NO'}")
    print(f"START_STATE={state}")
    print(f"SOURCE_COMMIT={source_commit}")
    print(f"SOURCE_TREE={source_tree}")
    print(f"BASE_COMMIT={base_commit}")
    print(f"QUALIFICATION_MANIFEST={manifest_rel}")
    print(f"OUTPUT={output}")

    if state == "COMPLETE":
        print("STATE=COMPLETE")
        print("PRIMARY_FAILURE=NO")
        print("PREDECESSOR_FAILURE=NO")
        print("RELEASE_BLOCKING_FAILURE=NO")
        print("RESULT=SUBSHELL_RELEASETOOL02D_FULL_OPERATOR_ALREADY_COMPLETE")
        return 0

    if STATE_RANK[state] < STATE_RANK["QUALIFIED"]:
        attempt = next_attempt_dir(output, "qualification")
        log = attempt / "console.log"
        command = build_qualification_command(
            source_root=source_root,
            base=base_commit,
            manifest=manifest_path,
            output=attempt,
        )
        stream_stage(command, cwd=source_root, log=log, stage="QUALIFICATION")
        qualification_record = attempt / "qualification-record.env"
        if not qualification_record.is_file():
            fail("QUALIFICATION_RECORD_MISSING_AFTER_SUCCESS")
        q = require_record_identity(
            record=qualification_record,
            source_commit=source_commit,
            source_tree=source_tree,
        )
        if q.get("RELEASE_GATE") != "PASS":
            fail("QUALIFICATION_RECORD_GATE_NOT_PASS")
        dynamic.update(
            {
                "QUALIFICATION_RECORD": str(qualification_record),
                "QUALIFICATION_RECORD_SHA256": sha256_file(qualification_record),
                "QUALIFICATION_CONSOLE_LOG": str(log),
                "QUALIFICATION_CONSOLE_LOG_SHA256": sha256_file(log),
            }
        )
        state = "QUALIFIED"
        write_state(
            state_path=state_path,
            state=state,
            immutable=immutable,
            dynamic=dynamic,
        )
        print("STATE=QUALIFIED")

    qualification_record = Path(dynamic["QUALIFICATION_RECORD"])

    if STATE_RANK[state] < STATE_RANK["SIGNED"]:
        attempt = next_attempt_dir(output, "signed-preview")
        log = output / (attempt.name + ".log")
        command = build_signed_command(
            source_root=source_root,
            qualification_record=qualification_record,
            version_name=args.version_name,
            version_code=args.version_code,
            output=attempt,
        )
        stream_stage(command, cwd=source_root, log=log, stage="SIGNED")
        release_record = attempt / "release-record.env"
        preview_apk = attempt / f"subshell-{args.version_name}.apk"
        if not release_record.is_file() or not preview_apk.is_file():
            fail("SIGNED_OUTPUT_MISSING_AFTER_SUCCESS")
        r = require_record_identity(
            record=release_record,
            source_commit=source_commit,
            source_tree=source_tree,
            version_name=args.version_name,
            version_code=args.version_code,
        )
        apk_sha = sha256_file(preview_apk)
        if r.get("APK_SHA256") != apk_sha:
            fail("SIGNED_RELEASE_RECORD_APK_SHA_MISMATCH")
        dynamic.update(
            {
                "RELEASE_RECORD": str(release_record),
                "RELEASE_RECORD_SHA256": sha256_file(release_record),
                "PREVIEW_APK": str(preview_apk),
                "PREVIEW_APK_SHA256": apk_sha,
                "SIGNED_CONSOLE_LOG": str(log),
                "SIGNED_CONSOLE_LOG_SHA256": sha256_file(log),
            }
        )
        state = "SIGNED"
        write_state(
            state_path=state_path,
            state=state,
            immutable=immutable,
            dynamic=dynamic,
        )
        print("STATE=SIGNED")

    release_record = Path(dynamic["RELEASE_RECORD"])

    if STATE_RANK[state] < STATE_RANK["DEVICE_SMOKED"]:
        log = next_attempt_log(output, "device-smoke")
        command = build_smoke_command(
            source_root=source_root,
            release_record=release_record,
            serial=args.serial,
        )
        stream_stage(command, cwd=source_root, log=log, stage="DEVICE_SMOKE")
        text = log.read_text()
        if "RESULT=SUBSHELL_RELEASETOOL01B_EXACT_PREVIEW_DEVICE_SMOKE_PASS" not in text:
            fail("DEVICE_SMOKE_PASS_MARKER_MISSING")
        actual_serial = extract_line_value(log, "SERIAL=")
        dynamic.update(
            {
                "DEVICE_SMOKE_LOG": str(log),
                "DEVICE_SMOKE_LOG_SHA256": sha256_file(log),
                "DEVICE_SMOKE_SERIAL": actual_serial,
            }
        )
        state = "DEVICE_SMOKED"
        write_state(
            state_path=state_path,
            state=state,
            immutable=immutable,
            dynamic=dynamic,
        )
        print("STATE=DEVICE_SMOKED")

    if STATE_RANK[state] < STATE_RANK["COMPLETE"]:
        attempt = next_attempt_dir(output, "publication")
        log = output / (attempt.name + ".log")
        command = build_publication_command(
            source_root=source_root,
            release_record=release_record,
            args=args,
            output=attempt,
        )
        stream_stage(command, cwd=source_root, log=log, stage="PUBLICATION")
        release_beta_state = attempt / "release-beta-state.env"
        if not release_beta_state.is_file():
            fail("RELEASE_BETA_STATE_MISSING_AFTER_SUCCESS")
        final = parse_env(release_beta_state)
        if final.get("STATE") != "COMPLETE":
            fail("RELEASE_BETA_STATE_NOT_COMPLETE")
        if final.get("SOURCE_COMMIT") != source_commit:
            fail("RELEASE_BETA_SOURCE_COMMIT_MISMATCH")
        if final.get("APK_SHA256") != dynamic.get("PREVIEW_APK_SHA256"):
            fail("RELEASE_BETA_APK_SHA_MISMATCH")
        if final.get("PRIVATE_APP_SOURCE_PUBLIC") != "NO":
            fail("RELEASE_BETA_PRIVATE_SOURCE_BOUNDARY_FAILED")
        if final.get("RELEASE_BLOCKING_FAILURE") != "NO":
            fail("RELEASE_BETA_BLOCKING_FAILURE")
        dynamic.update(
            {
                "RELEASE_BETA_STATE": str(release_beta_state),
                "RELEASE_BETA_STATE_SHA256": sha256_file(release_beta_state),
                "PUBLICATION_CONSOLE_LOG": str(log),
                "PUBLICATION_CONSOLE_LOG_SHA256": sha256_file(log),
            }
        )
        state = "COMPLETE"
        write_state(
            state_path=state_path,
            state=state,
            immutable=immutable,
            dynamic=dynamic,
        )
        print("STATE=COMPLETE")

    validate_success_artifacts(state=state, values=parse_env(state_path), args=args)

    print(f"OPERATOR_STATE={state_path}")
    print(f"QUALIFICATION_RECORD={dynamic['QUALIFICATION_RECORD']}")
    print(f"RELEASE_RECORD={dynamic['RELEASE_RECORD']}")
    print(f"PREVIEW_APK={dynamic['PREVIEW_APK']}")
    print(f"PREVIEW_APK_SHA256={dynamic['PREVIEW_APK_SHA256']}")
    print(f"DEVICE_SMOKE_SERIAL={dynamic.get('DEVICE_SMOKE_SERIAL','')}")
    print(f"RELEASE_BETA_STATE={dynamic['RELEASE_BETA_STATE']}")
    print("PRIMARY_FAILURE=NO")
    print("PREDECESSOR_FAILURE=NO")
    print("RELEASE_BLOCKING_FAILURE=NO")
    print("RESULT=SUBSHELL_RELEASETOOL02D_FULL_OPERATOR_PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except StageFailure as exc:
        classify_stage_failure(exc, Path(sys.argv[sys.argv.index("--output") + 1]))
        raise SystemExit(1)
    except OperatorError as exc:
        print(f"STOP={exc}", file=sys.stderr)
        print("PRIMARY_FAILURE=YES_RELEASETOOL02D_ORCHESTRATION", file=sys.stderr)
        print("PREDECESSOR_FAILURE=NO", file=sys.stderr)
        print("RELEASE_BLOCKING_FAILURE=YES", file=sys.stderr)
        raise SystemExit(1)
