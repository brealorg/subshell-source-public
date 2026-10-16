#!/usr/bin/env python3
# Hermetic process-level integration test for RELEASETOOL02D.
#
# No real build, signing key, adb device, GitHub API, network, push, release,
# or Pages mutation is used. A temporary git repository supplies controlled
# fake predecessor tools while the real RELEASETOOL02D operator is exercised
# as a subprocess.

from __future__ import annotations

import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import textwrap


ROOT = Path(__file__).resolve().parents[2]
OPERATOR = ROOT / "tools/release02/release-beta-operator.py"


def run(
    argv: list[str],
    *,
    cwd: Path | None = None,
    env: dict[str, str] | None = None,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    merged = os.environ.copy()
    merged["PYTHONDONTWRITEBYTECODE"] = "1"
    if env:
        merged.update(env)
    result = subprocess.run(
        argv,
        cwd=str(cwd) if cwd else None,
        env=merged,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if check and result.returncode != 0:
        raise AssertionError(
            f"command failed rc={result.returncode}: {' '.join(argv)}\n"
            f"{result.stdout}"
        )
    return result


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_env(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in path.read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, value = line.split("=", 1)
        result[key] = value
    return result


def write_executable(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(text).lstrip())
    path.chmod(0o755)


def fake_qualification_script() -> str:
    return r'''
        #!/usr/bin/env bash
        set -euo pipefail
        BASE=''
        MANIFEST=''
        OUTPUT=''
        while [[ $# -gt 0 ]]; do
            case "$1" in
                --base) BASE="$2"; shift 2 ;;
                --manifest) MANIFEST="$2"; shift 2 ;;
                --output) OUTPUT="$2"; shift 2 ;;
                *) echo "STOP=UNKNOWN_ARG_$1" >&2; exit 64 ;;
            esac
        done
        [[ -n "$BASE" && -n "$MANIFEST" && -n "$OUTPUT" ]]
        mkdir -p "$OUTPUT"
        HEAD="$(git rev-parse HEAD)"
        TREE="$(git rev-parse 'HEAD^{tree}')"
        cat >"$OUTPUT/qualification-record.env" <<EOF
        CONTRACT02_QUALIFICATION_RECORD_VERSION=1
        SOURCE_COMMIT=$HEAD
        SOURCE_TREE=$TREE
        BASE_COMMIT=$BASE
        PRIMARY_FAILURES=0
        PREDECESSOR_FAILURES=0
        RELEASE_BLOCKING_FAILURES=0
        CONTRACT_GATE=PASS
        UNIT_TESTS=PASS
        RELEASE_GATE=PASS
        EOF
        echo "QUALIFICATION_RECORD=$OUTPUT/qualification-record.env"
        echo 'RESULT=SUBSHELL_CONTRACT02_DELTA_QUALIFICATION_PASS'
    '''


def fake_signed_script() -> str:
    return r'''
        #!/usr/bin/env bash
        set -euo pipefail
        Q=''
        VERSION_NAME=''
        VERSION_CODE=''
        OUTPUT=''
        while [[ $# -gt 0 ]]; do
            case "$1" in
                --qualification-record) Q="$2"; shift 2 ;;
                --version-name) VERSION_NAME="$2"; shift 2 ;;
                --version-code) VERSION_CODE="$2"; shift 2 ;;
                --output) OUTPUT="$2"; shift 2 ;;
                *) echo "STOP=UNKNOWN_ARG_$1" >&2; exit 64 ;;
            esac
        done
        if [[ "${SUBSHELL_SIM_FAIL_SIGNED:-NO}" == YES ]]; then
            echo 'SIMULATED_SIGNED_FAILURE=YES'
            exit 23
        fi
        mkdir -p "$OUTPUT"
        HEAD="$(git rev-parse HEAD)"
        TREE="$(git rev-parse 'HEAD^{tree}')"
        APK="$OUTPUT/subshell-$VERSION_NAME.apk"
        printf 'hermetic-apk:%s:%s\n' "$VERSION_NAME" "$VERSION_CODE" >"$APK"
        APK_SHA="$(sha256sum "$APK" | awk '{print $1}')"
        cat >"$OUTPUT/release-record.env" <<EOF
        RELEASETOOL01_RELEASE_RECORD_VERSION=1
        SOURCE_COMMIT=$HEAD
        SOURCE_TREE=$TREE
        VERSION_NAME=$VERSION_NAME
        VERSION_CODE=$VERSION_CODE
        APPLICATION_ID=io.github.brealorg.subshell
        SIGNER_CERT_SHA256=FA:KE
        APK_SHA256=$APK_SHA
        EOF
        echo "RELEASE_RECORD=$OUTPUT/release-record.env"
        echo "PREVIEW_APK=$APK"
        echo "APK_SHA256=$APK_SHA"
        echo 'RESULT=SUBSHELL_RELEASETOOL01A_SIGNED_PREVIEW_PASS'
    '''


def fake_smoke_script() -> str:
    return r'''
        #!/usr/bin/env bash
        set -euo pipefail
        RECORD=''
        ROOT=''
        SERIAL=''
        while [[ $# -gt 0 ]]; do
            case "$1" in
                --release-record) RECORD="$2"; shift 2 ;;
                --qualified-source-root) ROOT="$2"; shift 2 ;;
                --serial) SERIAL="$2"; shift 2 ;;
                *) echo "STOP=UNKNOWN_ARG_$1" >&2; exit 64 ;;
            esac
        done
        [[ -f "$RECORD" && -d "$ROOT" ]]
        [[ -n "$SERIAL" ]] || SERIAL='SIMULATED'
        echo "SERIAL=$SERIAL"
        echo "SMOKE_RELEASE_RECORD=$RECORD"
        echo 'INSTALLED_PACKAGE_VERSION=PASS'
        echo 'RESULT=SUBSHELL_RELEASETOOL01B_EXACT_PREVIEW_DEVICE_SMOKE_PASS'
    '''


def fake_publication_script() -> str:
    return r'''
        #!/usr/bin/env python3
        import argparse
        from pathlib import Path
        import os

        p = argparse.ArgumentParser()
        p.add_argument("--release-record", required=True)
        p.add_argument("--qualified-source-root", required=True)
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
        p.add_argument("--propagation-attempts", required=True)
        p.add_argument("--propagation-delay-seconds", required=True)
        args = p.parse_args()

        if os.environ.get("SUBSHELL_SIM_FAIL_PUBLICATION") == "YES":
            print("SIMULATED_PUBLICATION_FAILURE=YES")
            raise SystemExit(31)

        record = {}
        for raw in Path(args.release_record).read_text().splitlines():
            if not raw.strip() or raw.startswith("#"):
                continue
            k, v = raw.split("=", 1)
            record[k] = v

        output = Path(args.output)
        output.mkdir(parents=True, exist_ok=True)
        target = output / "release-beta-state.env"
        target.write_text(
            "\n".join(
                [
                    "RELEASETOOL02_ORCHESTRATOR_RECORD_VERSION=1",
                    "STATE=COMPLETE",
                    f"SOURCE_COMMIT={record['SOURCE_COMMIT']}",
                    f"SOURCE_TREE={record['SOURCE_TREE']}",
                    f"VERSION_NAME={record['VERSION_NAME']}",
                    f"VERSION_CODE={record['VERSION_CODE']}",
                    f"APK_SHA256={record['APK_SHA256']}",
                    "PRIVATE_APP_SOURCE_PUBLIC=NO",
                    "PRIMARY_FAILURE=NO",
                    "PREDECESSOR_FAILURE=NO",
                    "RELEASE_BLOCKING_FAILURE=NO",
                    "",
                ]
            )
        )
        print(f"PUBLICATION_RELEASE_RECORD={args.release_record}")
        print(f"ORCHESTRATOR_STATE={target}")
        print("STATE=COMPLETE")
        print("RESULT=SUBSHELL_RELEASETOOL02C_END_TO_END_PASS")
    '''


def operator_args(
    *,
    source: Path,
    base: str,
    notes: Path,
    output: Path,
    title: str = "Subshell simulated beta",
    resume: bool = False,
) -> list[str]:
    args = [
        sys.executable,
        str(OPERATOR),
        "--qualified-source-root",
        str(source),
        "--base",
        base,
        "--qualification-manifest",
        "tools/contract02/manifests/sim-release.tsv",
        "--version-name",
        "0.99.0-beta.99",
        "--version-code",
        "999",
        "--serial",
        "SIM123",
        "--public-repo",
        "example/subshell",
        "--public-target",
        "3" * 40,
        "--title",
        title,
        "--notes-file",
        str(notes),
        "--public-main-before",
        "4" * 40,
        "--pages-repo",
        "example/example.github.io",
        "--pages-main-before",
        "5" * 40,
        "--previous-version",
        "0.98.0-beta.98",
        "--manifest-url",
        "https://example.invalid/subshell-update-beta.json",
        "--site-url",
        "https://example.invalid/subshell/",
        "--output",
        str(output),
        "--propagation-attempts",
        "1",
        "--propagation-delay-seconds",
        "0",
    ]
    if resume:
        args.append("--resume")
    return args


def file_set(root: Path) -> set[str]:
    return {
        path.relative_to(root).as_posix()
        for path in root.rglob("*")
        if path.is_file()
    }


def main() -> int:
    if not OPERATOR.is_file():
        raise AssertionError("operator missing")

    with tempfile.TemporaryDirectory(prefix="subshell-release02d-integration-") as tmp:
        work = Path(tmp)
        source = work / "source"
        source.mkdir()

        run(["git", "init", "-b", "main"], cwd=source)
        run(["git", "config", "user.name", "ReleaseTool02D Integration"], cwd=source)
        run(
            ["git", "config", "user.email", "releasetool02d@example.invalid"],
            cwd=source,
        )

        (source / "README.md").write_text("hermetic release operator source\n")
        run(["git", "add", "README.md"], cwd=source)
        run(["git", "commit", "-m", "base"], cwd=source)
        base = run(["git", "rev-parse", "HEAD"], cwd=source).stdout.strip()

        write_executable(
            source / "tools/contract02/qualify-delta.sh",
            fake_qualification_script(),
        )
        write_executable(
            source / "tools/release01/prepare-signed-preview.sh",
            fake_signed_script(),
        )
        write_executable(
            source / "tools/release01/smoke-preview-device.sh",
            fake_smoke_script(),
        )
        write_executable(
            source / "tools/release02/release-beta.py",
            fake_publication_script(),
        )
        manifest = source / "tools/contract02/manifests/sim-release.tsv"
        manifest.parent.mkdir(parents=True, exist_ok=True)
        manifest.write_text("# hermetic simulation manifest\n")

        run(["git", "add", "."], cwd=source)
        run(["git", "commit", "-m", "add fake release predecessors"], cwd=source)

        source_head = run(["git", "rev-parse", "HEAD"], cwd=source).stdout.strip()
        source_tree = run(
            ["git", "rev-parse", "HEAD^{tree}"],
            cwd=source,
        ).stdout.strip()
        assert not run(["git", "status", "--porcelain"], cwd=source).stdout.strip()

        notes = work / "notes.md"
        notes.write_text("Hermetic beta release notes.\n")
        output = work / "operator"

        first = run(
            operator_args(
                source=source,
                base=base,
                notes=notes,
                output=output,
            ),
            env={"SUBSHELL_SIM_FAIL_SIGNED": "YES"},
            check=False,
        )
        assert first.returncode != 0
        assert "FAILED_STAGE=SIGNED" in first.stdout
        assert "PREDECESSOR_FAILURE=YES_RELEASETOOL01A" in first.stdout

        state = parse_env(output / "operator-state.env")
        assert state["STATE"] == "QUALIFIED"
        q_record = Path(state["QUALIFICATION_RECORD"])
        assert q_record.is_file()

        sign_log_1 = output / "signed-preview-attempt-001.log"
        assert sign_log_1.is_file()
        assert not (output / "signed-preview-attempt-001").exists()
        sign_log_1_sha = sha256(sign_log_1)

        print("PASS=RELEASETOOL02D_HERMETIC_QUALIFIED_CHECKPOINT_AFTER_SIGN_FAILURE")

        second = run(
            operator_args(
                source=source,
                base=base,
                notes=notes,
                output=output,
                resume=True,
            ),
            env={"SUBSHELL_SIM_FAIL_PUBLICATION": "YES"},
            check=False,
        )
        assert second.returncode != 0
        assert "FAILED_STAGE=PUBLICATION" in second.stdout
        assert "PREDECESSOR_FAILURE=YES_RELEASETOOL02C" in second.stdout

        assert sha256(sign_log_1) == sign_log_1_sha
        signed_2 = output / "signed-preview-attempt-002"
        assert signed_2.is_dir()
        release_record = signed_2 / "release-record.env"
        preview_apk = signed_2 / "subshell-0.99.0-beta.99.apk"
        assert release_record.is_file()
        assert preview_apk.is_file()

        state = parse_env(output / "operator-state.env")
        assert state["STATE"] == "DEVICE_SMOKED"
        assert state["SOURCE_COMMIT"] == source_head
        assert state["SOURCE_TREE"] == source_tree
        assert Path(state["RELEASE_RECORD"]) == release_record

        smoke_log = Path(state["DEVICE_SMOKE_LOG"])
        assert smoke_log.is_file()
        assert f"SMOKE_RELEASE_RECORD={release_record}" in smoke_log.read_text()

        publication_log_1 = output / "publication-attempt-001.log"
        assert publication_log_1.is_file()
        assert not (output / "publication-attempt-001").exists()
        publication_log_1_sha = sha256(publication_log_1)

        print("PASS=RELEASETOOL02D_HERMETIC_SIGNED_ATTEMPT_LOG_PRESERVED")
        print("PASS=RELEASETOOL02D_HERMETIC_DEVICE_SMOKED_CHECKPOINT")

        third = run(
            operator_args(
                source=source,
                base=base,
                notes=notes,
                output=output,
                resume=True,
            )
        )
        assert "RESULT=SUBSHELL_RELEASETOOL02D_FULL_OPERATOR_PASS" in third.stdout
        assert sha256(publication_log_1) == publication_log_1_sha

        publication_2 = output / "publication-attempt-002"
        publication_log_2 = output / "publication-attempt-002.log"
        assert publication_2.is_dir()
        assert publication_log_2.is_file()
        assert (
            f"PUBLICATION_RELEASE_RECORD={release_record}"
            in publication_log_2.read_text()
        )

        final_state = parse_env(output / "operator-state.env")
        assert final_state["STATE"] == "COMPLETE"
        assert final_state["RELEASE_RECORD"] == str(release_record)
        assert final_state["DEVICE_SMOKE_SERIAL"] == "SIM123"
        release_beta = Path(final_state["RELEASE_BETA_STATE"])
        assert release_beta.is_file()
        assert parse_env(release_beta)["STATE"] == "COMPLETE"

        print("PASS=RELEASETOOL02D_HERMETIC_PUBLICATION_ATTEMPT_LOG_PRESERVED")
        print("PASS=RELEASETOOL02D_HERMETIC_SAME_RELEASE_RECORD_SMOKE_TO_PUBLICATION")
        print("PASS=RELEASETOOL02D_HERMETIC_RESUME_TO_COMPLETE")

        before_files = file_set(output)
        fourth = run(
            operator_args(
                source=source,
                base=base,
                notes=notes,
                output=output,
                resume=True,
            )
        )
        assert (
            "RESULT=SUBSHELL_RELEASETOOL02D_FULL_OPERATOR_ALREADY_COMPLETE"
            in fourth.stdout
        )
        assert file_set(output) == before_files

        print("PASS=RELEASETOOL02D_HERMETIC_COMPLETE_RESUME_IDEMPOTENT")

        mismatch_before = file_set(output)
        mismatch = run(
            operator_args(
                source=source,
                base=base,
                notes=notes,
                output=output,
                title="Changed title must fail",
                resume=True,
            ),
            check=False,
        )
        assert mismatch.returncode != 0
        assert "RESUME_INPUT_MISMATCH_TITLE" in mismatch.stdout
        assert file_set(output) == mismatch_before

        print("PASS=RELEASETOOL02D_HERMETIC_IMMUTABLE_INPUT_FAILS_CLOSED")

        assert run(["git", "rev-parse", "HEAD"], cwd=source).stdout.strip() == source_head
        assert (
            run(["git", "rev-parse", "HEAD^{tree}"], cwd=source).stdout.strip()
            == source_tree
        )
        assert not run(["git", "status", "--porcelain"], cwd=source).stdout.strip()

        print("PASS=RELEASETOOL02D_HERMETIC_SOURCE_INTEGRITY_PRESERVED")
        print("REMOTE_MUTATIONS=NONE_HERMETIC_FAKE_PREDECESSORS")
        print("DEVICE_MUTATIONS=NONE_HERMETIC_FAKE_PREDECESSOR")

    print("RESULT=SUBSHELL_RELEASETOOL02D_HERMETIC_INTEGRATION_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
