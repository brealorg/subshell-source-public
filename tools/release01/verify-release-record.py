#!/usr/bin/env python3
# SUBSHELL_RELEASETOOL01B_RELEASE_RECORD_VERIFIER_V1

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re
import subprocess


REQUIRED_KEYS = {
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


def fail(reason: str) -> None:
    print(f"STOP=RELEASETOOL01_RELEASE_RECORD_REJECTED reason={reason}")
    raise SystemExit(1)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def command(*args: str) -> str:
    result = subprocess.run(
        list(args),
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    return result.stdout


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
    extra = sorted(values.keys() - REQUIRED_KEYS)
    if missing:
        fail("record_missing_keys_" + ",".join(missing))
    if extra:
        fail("record_extra_keys_" + ",".join(extra))
    return values


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
    badging = command(str(aapt2), "dump", "badging", str(apk))
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

    if not match.group(2).isdigit():
        fail("apk_version_code_not_integer")

    certs = command(str(apksigner), "verify", "--print-certs", str(apk))
    digests = re.findall(
        r"^Signer #\d+ certificate SHA-256 digest: ([0-9A-Fa-f:]+)$",
        certs,
        flags=re.MULTILINE,
    )
    if len(digests) != 1:
        fail(f"apk_signer_count_not_one_{len(digests)}")

    return (
        match.group(1),
        match.group(3),
        int(match.group(2)),
        normalize_fingerprint(digests[0]),
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--record", required=True)
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--aapt2", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument(
        "--field",
        choices=[
            "PREVIEW_APK",
            "APPLICATION_ID",
            "VERSION_NAME",
            "VERSION_CODE",
            "APK_SHA256",
            "APK_SIZE_BYTES",
            "SOURCE_COMMIT",
            "SOURCE_TREE",
        ],
    )
    args = parser.parse_args()

    record = Path(args.record).expanduser()
    if not record.is_absolute():
        fail("record_must_be_absolute")
    record = record.resolve()
    if not record.is_file():
        fail("record_not_found")

    source = Path(args.source_root).resolve()
    if record.is_relative_to(source):
        fail("record_must_be_external_to_source_tree")

    aapt2 = Path(args.aapt2).resolve()
    apksigner = Path(args.apksigner).resolve()
    if not aapt2.is_file():
        fail("aapt2_not_file")
    if not apksigner.is_file():
        fail("apksigner_not_file")

    values = parse_record(record)

    if values["RELEASETOOL01_RECORD_VERSION"] != "1":
        fail("unsupported_record_version")
    if values["RELEASE_GATE"] != "PASS":
        fail("release_gate_not_pass")

    apk = Path(values["PREVIEW_APK"])
    if not apk.is_absolute():
        fail("preview_apk_path_not_absolute")
    apk = apk.resolve()
    if not apk.is_file():
        fail("preview_apk_not_found")
    if apk.is_relative_to(source):
        fail("preview_apk_must_be_external_to_source_tree")

    if sha256(apk) != values["APK_SHA256"]:
        fail("apk_sha256_mismatch")

    try:
        recorded_size = int(values["APK_SIZE_BYTES"])
        recorded_code = int(values["VERSION_CODE"])
    except ValueError:
        fail("record_numeric_field_invalid")

    if apk.stat().st_size != recorded_size:
        fail("apk_size_mismatch")
    if recorded_size <= 0 or recorded_code <= 0:
        fail("record_numeric_field_nonpositive")

    package, version_name, version_code, fingerprint = apk_identity(
        apk,
        aapt2,
        apksigner,
    )

    if package != values["APPLICATION_ID"]:
        fail("apk_application_id_mismatch")
    if version_name != values["VERSION_NAME"]:
        fail("apk_version_name_mismatch")
    if version_code != recorded_code:
        fail("apk_version_code_mismatch")
    if fingerprint != normalize_fingerprint(values["SIGNER_CERT_SHA256"]):
        fail("apk_signer_fingerprint_mismatch")

    status = command("git", "-C", str(source), "status", "--porcelain").strip()
    if status:
        fail("source_worktree_not_clean")

    current_tree = command(
        "git",
        "-C",
        str(source),
        "rev-parse",
        "HEAD^{tree}",
    ).strip()
    if current_tree != values["SOURCE_TREE"]:
        fail("source_tree_mismatch")

    print("PASS=RELEASETOOL01_RELEASE_RECORD_SCHEMA")
    print("PASS=RELEASETOOL01_RELEASE_RECORD_SOURCE_TREE")
    print("PASS=RELEASETOOL01_RELEASE_RECORD_APK_BYTES")
    print("PASS=RELEASETOOL01_RELEASE_RECORD_APK_IDENTITY")
    print("PASS=RELEASETOOL01_RELEASE_RECORD_SIGNER_IDENTITY")
    print(f"RELEASE_RECORD={record}")
    print(f"PREVIEW_APK={apk}")
    print(f"APK_SHA256={values['APK_SHA256']}")
    print(f"VERSION_NAME={values['VERSION_NAME']}")
    print(f"VERSION_CODE={values['VERSION_CODE']}")
    print(f"APPLICATION_ID={values['APPLICATION_ID']}")

    if args.field:
        print(f"FIELD_VALUE={values[args.field]}")

    print("RESULT=SUBSHELL_RELEASETOOL01_RELEASE_RECORD_VERIFICATION_PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        fail(f"external_command_failed_{exc.returncode}")
