#!/usr/bin/env python3
"""
RELEASETOOL02C — end-to-end beta-release orchestrator.

Full mode:
    RELEASETOOL02A -> REMOTE_APK_VERIFIED
    RELEASETOOL02B -> COMPLETE

Resume mode:
    accept an already verified RELEASETOOL02A promotion-state.env and continue
    with RELEASETOOL02B.

Existing-only mode:
    guarantees the orchestrator itself will not enter a create/upload/push
    path. It is intended for historical replay and post-release verification.
    In resume mode, both public repositories must already be ahead of the
    supplied pre-promotion base commits before RELEASETOOL02B is invoked.

The orchestrator performs no build, signing, or device mutation.
"""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import sys


HEX40 = re.compile(r"^[0-9a-f]{40}$")
REPO_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")


class OrchestratorError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise OrchestratorError(message)


def run(
    argv: list[str],
    *,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        argv,
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


def run_stage(
    argv: list[str],
    log_path: Path,
    stage_name: str,
) -> None:
    result = subprocess.run(
        argv,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    log_path.write_text(result.stdout)
    print(result.stdout, end="")
    if result.returncode != 0:
        fail(f"{stage_name}_FAILED_RC_{result.returncode}")


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def parse_env(path: Path) -> dict[str, str]:
    if not path.is_file():
        fail("STATE_FILE_MISSING")
    result: dict[str, str] = {}
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"STATE_MALFORMED_LINE_{number}")
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key:
            fail(f"STATE_EMPTY_KEY_{number}")
        if key in result:
            fail(f"STATE_DUPLICATE_KEY_{key}")
        result[key] = value
    return result


def require_abs(path: Path, marker: str) -> None:
    if not path.is_absolute():
        fail(f"{marker}_NOT_ABSOLUTE")


def remote_main(repo: str) -> str:
    return run(
        ["gh", "api", f"repos/{repo}/commits/main", "--jq", ".sha"]
    ).stdout.strip()


def existing_release(repo: str, tag: str) -> bool:
    result = run(
        ["gh", "api", f"repos/{repo}/releases/tags/{tag}", "--jq", ".tag_name"],
        check=False,
    )
    if result.returncode == 0:
        return result.stdout.strip() == tag
    combined = f"{result.stdout}\n{result.stderr}".lower()
    if "404" in combined or "not found" in combined:
        return False
    fail("REMOTE_RELEASE_LOOKUP_FAILED")


def validate_common(args: argparse.Namespace) -> None:
    if not REPO_RE.fullmatch(args.public_repo):
        fail("PUBLIC_REPO_INVALID")
    if not REPO_RE.fullmatch(args.pages_repo):
        fail("PAGES_REPO_INVALID")
    if not HEX40.fullmatch(args.public_main_before):
        fail("PUBLIC_MAIN_BEFORE_INVALID")
    if not HEX40.fullmatch(args.pages_main_before):
        fail("PAGES_MAIN_BEFORE_INVALID")

    for value, marker in (
        (Path(args.qualified_source_root), "QUALIFIED_SOURCE_ROOT"),
        (Path(args.output), "OUTPUT"),
    ):
        require_abs(value, marker)


def ensure_output_empty(output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        fail("OUTPUT_NOT_EMPTY")


def validate_promotion_state_for_resume(
    state_path: Path,
    public_repo: str,
) -> dict[str, str]:
    state = parse_env(state_path)
    required = {
        "RELEASETOOL02_PUBLIC_PROMOTION_RECORD_VERSION": "1",
        "STATE": "REMOTE_APK_VERIFIED",
        "REMOTE_APK_VERIFIED": "YES",
        "PRIVATE_APP_SOURCE_PUBLIC": "NO",
        "NEXT_STAGE": "PUBLIC_SITE_PROMOTION",
        "PUBLIC_REPOSITORY": public_repo,
    }
    for key, value in required.items():
        if state.get(key) != value:
            fail(f"PROMOTION_STATE_INVALID_{key}")
    if not state.get("TAG"):
        fail("PROMOTION_STATE_TAG_MISSING")
    return state


def existing_only_resume_preflight(
    *,
    promotion_state: dict[str, str],
    public_repo: str,
    public_main_before: str,
    pages_repo: str,
    pages_main_before: str,
) -> None:
    tag = promotion_state["TAG"]
    if not existing_release(public_repo, tag):
        fail("EXISTING_ONLY_RELEASE_NOT_PRESENT")

    public_now = remote_main(public_repo)
    pages_now = remote_main(pages_repo)

    # If either repository is still at its base, RELEASETOOL02B would enter a
    # push path. existing-only must stop before invoking it.
    if public_now == public_main_before:
        fail("EXISTING_ONLY_PUBLIC_SITE_NOT_ALREADY_PROMOTED")
    if pages_now == pages_main_before:
        fail("EXISTING_ONLY_PAGES_MANIFEST_NOT_ALREADY_PROMOTED")


def build_stage_a_command(
    *,
    tool_root: Path,
    args: argparse.Namespace,
    output: Path,
) -> list[str]:
    driver = tool_root / "promote-github-beta.py"
    return [
        sys.executable,
        str(driver),
        "--release-record",
        args.release_record,
        "--qualified-source-root",
        args.qualified_source_root,
        "--repo",
        args.public_repo,
        "--public-target",
        args.public_target,
        "--title",
        args.title,
        "--notes-file",
        args.notes_file,
        "--output",
        str(output),
    ]


def build_stage_b_command(
    *,
    tool_root: Path,
    promotion_state: Path,
    args: argparse.Namespace,
    output: Path,
) -> list[str]:
    driver = tool_root / "promote-public-site-manifest.py"
    return [
        sys.executable,
        str(driver),
        "--promotion-state",
        str(promotion_state),
        "--qualified-source-root",
        args.qualified_source_root,
        "--public-repo",
        args.public_repo,
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


def write_orchestrator_state(
    *,
    output: Path,
    mode: str,
    existing_only: bool,
    promotion_state: Path,
    publication_state: Path,
) -> Path:
    pub = parse_env(promotion_state)
    final = parse_env(publication_state)

    if final.get("STATE") != "COMPLETE":
        fail("PUBLICATION_STATE_NOT_COMPLETE")
    if final.get("SOURCE_COMMIT") != pub.get("SOURCE_COMMIT"):
        fail("FINAL_SOURCE_COMMIT_MISMATCH")
    if final.get("APK_SHA256") != pub.get("APK_SHA256"):
        fail("FINAL_APK_SHA_MISMATCH")
    if final.get("PRIVATE_APP_SOURCE_PUBLIC") != "NO":
        fail("FINAL_PRIVATE_SOURCE_BOUNDARY_FAILED")
    if final.get("RELEASE_BLOCKING_FAILURE") != "NO":
        fail("FINAL_RELEASE_BLOCKING_FAILURE")

    target = output / "release-beta-state.env"
    lines = [
        "RELEASETOOL02_ORCHESTRATOR_RECORD_VERSION=1",
        "STATE=COMPLETE",
        f"MODE={mode}",
        f"EXISTING_ONLY={'YES' if existing_only else 'NO'}",
        f"PROMOTION_STATE={promotion_state}",
        f"PROMOTION_STATE_SHA256={sha256_file(promotion_state)}",
        f"PUBLICATION_STATE={publication_state}",
        f"PUBLICATION_STATE_SHA256={sha256_file(publication_state)}",
        f"SOURCE_COMMIT={pub.get('SOURCE_COMMIT','')}",
        f"SOURCE_TREE={pub.get('SOURCE_TREE','')}",
        f"VERSION_NAME={pub.get('VERSION_NAME','')}",
        f"VERSION_CODE={pub.get('VERSION_CODE','')}",
        f"APK_SHA256={pub.get('APK_SHA256','')}",
        f"PUBLIC_REPOSITORY={pub.get('PUBLIC_REPOSITORY','')}",
        f"TAG={pub.get('TAG','')}",
        "PRIVATE_APP_SOURCE_PUBLIC=NO",
        "PRIMARY_FAILURE=NO",
        "PREDECESSOR_FAILURE=NO",
        "RELEASE_BLOCKING_FAILURE=NO",
        "",
    ]
    target.write_text("\n".join(lines))
    return target


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser()
    mode = p.add_mutually_exclusive_group(required=True)
    mode.add_argument("--release-record")
    mode.add_argument("--promotion-state")

    p.add_argument("--qualified-source-root", required=True)
    p.add_argument("--public-repo", required=True)
    p.add_argument("--public-main-before", required=True)
    p.add_argument("--pages-repo", required=True)
    p.add_argument("--pages-main-before", required=True)
    p.add_argument("--previous-version", required=True)
    p.add_argument("--manifest-url", required=True)
    p.add_argument("--site-url", required=True)
    p.add_argument("--output", required=True)

    # Full-mode-only inputs.
    p.add_argument("--public-target")
    p.add_argument("--title")
    p.add_argument("--notes-file")

    p.add_argument("--existing-only", action="store_true")
    p.add_argument("--propagation-attempts", type=int, default=36)
    p.add_argument("--propagation-delay-seconds", type=float, default=5.0)
    return p


def main() -> int:
    args = parser().parse_args()
    validate_common(args)

    output = Path(args.output)
    ensure_output_empty(output)

    tool_root = Path(__file__).resolve().parent
    stage_a_output = output / "stage-a-public-release"
    stage_b_output = output / "stage-b-site-manifest"
    stage_a_log = output / "stage-a.log"
    stage_b_log = output / "stage-b.log"

    if args.release_record:
        mode = "FULL"
        for value, marker in (
            (args.public_target, "PUBLIC_TARGET"),
            (args.title, "TITLE"),
            (args.notes_file, "NOTES_FILE"),
        ):
            if not value:
                fail(f"FULL_MODE_{marker}_MISSING")

        if not HEX40.fullmatch(args.public_target):
            fail("FULL_MODE_PUBLIC_TARGET_INVALID")
        require_abs(Path(args.release_record), "RELEASE_RECORD")
        require_abs(Path(args.notes_file), "NOTES_FILE")

        if args.existing_only:
            # Existing-only full replay is intentionally conservative. We only
            # enter Stage A when the release already exists. Stage A itself
            # still validates the exact remote asset set and will never
            # overwrite an existing asset.
            record = parse_env(Path(args.release_record))
            version = record.get("VERSION_NAME")
            if not version or not existing_release(
                args.public_repo, f"v{version}"
            ):
                fail("EXISTING_ONLY_RELEASE_NOT_PRESENT")

        command_a = build_stage_a_command(
            tool_root=tool_root,
            args=args,
            output=stage_a_output,
        )
        run_stage(command_a, stage_a_log, "RELEASETOOL02A")
        promotion_state = stage_a_output / "promotion-state.env"

    else:
        mode = "RESUME"
        promotion_state = Path(args.promotion_state)
        require_abs(promotion_state, "PROMOTION_STATE")
        state = validate_promotion_state_for_resume(
            promotion_state,
            args.public_repo,
        )
        if args.existing_only:
            existing_only_resume_preflight(
                promotion_state=state,
                public_repo=args.public_repo,
                public_main_before=args.public_main_before,
                pages_repo=args.pages_repo,
                pages_main_before=args.pages_main_before,
            )

    if not promotion_state.is_file():
        fail("PROMOTION_STATE_MISSING_AFTER_STAGE_A_OR_RESUME")

    command_b = build_stage_b_command(
        tool_root=tool_root,
        promotion_state=promotion_state,
        args=args,
        output=stage_b_output,
    )
    run_stage(command_b, stage_b_log, "RELEASETOOL02B")

    publication_state = stage_b_output / "publication-state.env"
    if not publication_state.is_file():
        fail("PUBLICATION_STATE_MISSING")

    final_state = write_orchestrator_state(
        output=output,
        mode=mode,
        existing_only=args.existing_only,
        promotion_state=promotion_state,
        publication_state=publication_state,
    )

    print("RELEASETOOL02C_ORCHESTRATOR_VERSION=1")
    print(f"MODE={mode}")
    print(f"EXISTING_ONLY={'YES' if args.existing_only else 'NO'}")
    print(f"PROMOTION_STATE={promotion_state}")
    print(f"PUBLICATION_STATE={publication_state}")
    print(f"ORCHESTRATOR_STATE={final_state}")
    print("STATE=COMPLETE")
    print("PRIMARY_FAILURE=NO")
    print("PREDECESSOR_FAILURE=NO")
    print("RELEASE_BLOCKING_FAILURE=NO")
    print("RESULT=SUBSHELL_RELEASETOOL02C_END_TO_END_PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except OrchestratorError as exc:
        print(f"STOP={exc}", file=sys.stderr)
        print("RELEASETOOL02C_FAILURE=ORCHESTRATION_OR_INFRA", file=sys.stderr)
        raise SystemExit(1)
