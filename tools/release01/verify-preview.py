#!/usr/bin/env python3
# SUBSHELL_RELEASETOOL01A_PREVIEW_VERIFIER_V1

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re
import subprocess


EXPECTED_RECORD_KEYS = {
    "RELEASETOOL01_RECORD_VERSION",
    "SOURCE_COMMIT",
    "SOURCE_TREE",
    "BASE_COMMIT",
    "QUALIFICATION_RECORD_SHA256",
    "QUALIFICATION_MANIFEST_PATH",
    "QUALIFICATION_MANIFEST_SHA256",
    "VERSION_NAME",
    "VERSION_CODE",
    "APPLICATION_ID",
    "SIGNER_CERT_SHA256",
    "APK_SHA256",
    "APK_SIZE_BYTES",
    "PREVIEW_APK",
    "BUILD_LOG_SHA256",
    "AUTH05D2_APK_ARTIFACT_SHA256",
    "RELEASE_GATE",
}


def fail(message: str) -> None:
    print(f"STOP=RELEASETOOL01_PREVIEW_REJECTED reason={message}")
    raise SystemExit(1)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def run(*args: str) -> str:
    result = subprocess.run(
        list(args),
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    return result.stdout


def parse_env_record(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw:
            continue
        if "=" not in raw:
            fail(f"record_line_{number}_missing_equals")
        key, value = raw.split("=", 1)
        if not key or key in result:
            fail(f"record_line_{number}_duplicate_or_empty_key")
        result[key] = value
    return result


def normalize_fingerprint(raw: str) -> str:
    compact = raw.replace(":", "").upper()
    if not re.fullmatch(r"[0-9A-F]{64}", compact):
        fail("signer_fingerprint_not_sha256")
    return ":".join(compact[i:i + 2] for i in range(0, 64, 2))


def apk_identity(
    apk: Path,
    aapt2: Path,
    apksigner: Path,
) -> tuple[str, str, int, str]:
    badging = run(str(aapt2), "dump", "badging", str(apk))
    first = next(
        (line for line in badging.splitlines() if line.startswith("package: ")),
        "",
    )
    match = re.search(
        r"name='([^']+)'.*versionCode='([^']+)'.*versionName='([^']*)'",
        first,
    )
    if not match:
        fail("aapt2_package_metadata_unparseable")

    package = match.group(1)
    version_code_raw = match.group(2)
    version_name = match.group(3)
    if not version_code_raw.isdigit():
        fail("apk_version_code_not_integer")

    certs = run(str(apksigner), "verify", "--print-certs", str(apk))
    digests = re.findall(
        r"^Signer #\d+ certificate SHA-256 digest: ([0-9A-Fa-f:]+)$",
        certs,
        flags=re.MULTILINE,
    )
    if len(digests) != 1:
        fail(f"apk_signer_count_not_one_{len(digests)}")

    return (
        package,
        version_name,
        int(version_code_raw),
        normalize_fingerprint(digests[0]),
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--aapt2", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--application-id", required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--expected-fingerprint", required=True)
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--qualification-record", required=True)
    parser.add_argument("--qualification-sha256", required=True)
    parser.add_argument("--build-log", required=True)
    parser.add_argument("--auth05d2-apk-artifact", required=True)
    parser.add_argument("--release-record", required=True)
    args = parser.parse_args()

    source = Path(args.source_root).resolve()
    apk = Path(args.apk).resolve()
    aapt2 = Path(args.aapt2).resolve()
    apksigner = Path(args.apksigner).resolve()
    qualification = Path(args.qualification_record).resolve()
    build_log = Path(args.build_log).resolve()
    auth_artifact = Path(args.auth05d2_apk_artifact).resolve()
    release_record = Path(args.release_record).resolve()

    for label, path in (
        ("apk", apk),
        ("aapt2", aapt2),
        ("apksigner", apksigner),
        ("qualification_record", qualification),
        ("build_log", build_log),
        ("auth05d2_apk_artifact", auth_artifact),
    ):
        if not path.is_file():
            fail(f"{label}_not_file")

    if apk.is_relative_to(source):
        fail("preview_apk_must_be_external")
    if qualification.is_relative_to(source):
        fail("qualification_record_must_be_external")
    if release_record.is_relative_to(source):
        fail("release_record_must_be_external")

    qualification_values = parse_env_record(qualification)
    for key in (
        "SOURCE_COMMIT",
        "SOURCE_TREE",
        "BASE_COMMIT",
        "MANIFEST_PATH",
        "MANIFEST_SHA256",
        "RELEASE_GATE",
    ):
        if key not in qualification_values:
            fail(f"qualification_key_missing_{key}")

    if qualification_values["RELEASE_GATE"] != "PASS":
        fail("qualification_release_gate_not_pass")

    if sha256(qualification) != args.qualification_sha256:
        fail("qualification_sha256_mismatch")

    git_status = run("git", "-C", str(source), "status", "--porcelain").strip()
    if git_status:
        fail("source_worktree_not_clean")

    source_commit = run("git", "-C", str(source), "rev-parse", "HEAD").strip()
    source_tree = run(
        "git",
        "-C",
        str(source),
        "rev-parse",
        "HEAD^{tree}",
    ).strip()

    if qualification_values["SOURCE_COMMIT"] != source_commit:
        fail("qualification_source_commit_mismatch")
    if qualification_values["SOURCE_TREE"] != source_tree:
        fail("qualification_source_tree_mismatch")

    package, version_name, version_code, fingerprint = apk_identity(
        apk,
        aapt2,
        apksigner,
    )

    expected_fingerprint = normalize_fingerprint(args.expected_fingerprint)

    if package != args.application_id:
        fail("application_id_mismatch")
    if version_name != args.version_name:
        fail("version_name_mismatch")
    if version_code != args.version_code:
        fail("version_code_mismatch")
    if fingerprint != expected_fingerprint:
        fail("signer_fingerprint_mismatch")

    build_text = build_log.read_text(encoding="utf-8", errors="replace")
    if "BUILD SUCCESSFUL" not in build_text:
        fail("build_log_not_successful")
    if (
        ":app:subshellContracts" in build_text
        or ":app:subshellQualityCheck" in build_text
    ):
        fail("legacy_quality_reentry_present")

    values = {
        "RELEASETOOL01_RECORD_VERSION": "1",
        "SOURCE_COMMIT": source_commit,
        "SOURCE_TREE": source_tree,
        "BASE_COMMIT": qualification_values["BASE_COMMIT"],
        "QUALIFICATION_RECORD_SHA256": args.qualification_sha256,
        "QUALIFICATION_MANIFEST_PATH": qualification_values["MANIFEST_PATH"],
        "QUALIFICATION_MANIFEST_SHA256": qualification_values["MANIFEST_SHA256"],
        "VERSION_NAME": args.version_name,
        "VERSION_CODE": str(args.version_code),
        "APPLICATION_ID": args.application_id,
        "SIGNER_CERT_SHA256": expected_fingerprint,
        "APK_SHA256": sha256(apk),
        "APK_SIZE_BYTES": str(apk.stat().st_size),
        "PREVIEW_APK": str(apk),
        "BUILD_LOG_SHA256": sha256(build_log),
        "AUTH05D2_APK_ARTIFACT_SHA256": sha256(auth_artifact),
        "RELEASE_GATE": "PASS",
    }

    if set(values) != EXPECTED_RECORD_KEYS:
        fail("internal_record_schema_mismatch")

    release_record.write_text(
        "".join(f"{key}={values[key]}\n" for key in values),
        encoding="utf-8",
    )

    print("PASS=RELEASETOOL01_PREVIEW_SOURCE_IDENTITY")
    print("PASS=RELEASETOOL01_PREVIEW_QUALIFICATION_IDENTITY")
    print("PASS=RELEASETOOL01_PREVIEW_PACKAGE_VERSION")
    print("PASS=RELEASETOOL01_PREVIEW_SIGNER_IDENTITY")
    print("PASS=RELEASETOOL01_PREVIEW_LEGACY_QUALITY_ABSENT")
    print(f"APK_SHA256={values['APK_SHA256']}")
    print(f"APK_SIZE_BYTES={values['APK_SIZE_BYTES']}")
    print(f"RELEASE_RECORD={release_record}")
    print("RESULT=SUBSHELL_RELEASETOOL01A_PREVIEW_VERIFICATION_PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        fail(f"external_command_failed_{exc.returncode}")
