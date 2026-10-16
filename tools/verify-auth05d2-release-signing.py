#!/usr/bin/env python3
"""Verify AUTH05D2 external JKS inputs and signed release APKs without secrets."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
import tempfile
from pathlib import Path

SCHEMA_KEYSTORE = "subshell.auth05d2.release_signing_identity.v1"
SCHEMA_APK = "subshell.auth05d2.release_apk.v1"
FINGERPRINT = re.compile(r"(?:[0-9A-F]{2}:){31}[0-9A-F]{2}")
SHA256 = re.compile(r"[0-9a-f]{64}")


class SigningError(ValueError):
    pass


def canonical_fingerprint(value: str) -> str:
    compact = value.replace(":", "").upper()
    if re.fullmatch(r"[0-9A-F]{64}", compact) is None:
        raise SigningError("FINGERPRINT_NOT_SHA256")
    return ":".join(compact[index : index + 2] for index in range(0, 64, 2))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require_external_private_file(path: Path, source_root: Path, label: str) -> Path:
    if not path.is_absolute():
        raise SigningError(f"{label}_NOT_ABSOLUTE")
    canonical = path.resolve(strict=True)
    source = source_root.resolve(strict=True)
    if canonical == source or source in canonical.parents:
        raise SigningError(f"{label}_INSIDE_SOURCE")
    if not canonical.is_file():
        raise SigningError(f"{label}_NOT_REGULAR_FILE")
    mode = stat.S_IMODE(canonical.stat().st_mode)
    if mode & 0o077:
        raise SigningError(f"{label}_MODE_NOT_PRIVATE")
    return canonical


def read_password(path: Path, label: str) -> str:
    value = path.read_text(encoding="utf-8")
    if len(value) < 24:
        raise SigningError(f"{label}_TOO_SHORT")
    if any(character in value for character in ("\n", "\r", "\x00")):
        raise SigningError(f"{label}_NOT_SINGLE_LINE")
    return value


def run(command: list[str]) -> subprocess.CompletedProcess[str]:
    environment = os.environ.copy()
    environment["LC_ALL"] = "C"
    completed = subprocess.run(
        command,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=environment,
    )
    if completed.returncode != 0:
        raise SigningError(
            "COMMAND_FAILED_" + Path(command[0]).name.upper().replace("-", "_")
        )
    return completed


def atomic_json(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded = json.dumps(value, indent=2, sort_keys=True) + "\n"
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(encoded)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def verify_keystore(args: argparse.Namespace) -> dict[str, object]:
    source_root = args.source_root.resolve(strict=True)
    keystore = require_external_private_file(args.keystore, source_root, "KEYSTORE")
    store_password_file = require_external_private_file(
        args.store_password_file, source_root, "STORE_PASSWORD_FILE"
    )
    key_password_file = require_external_private_file(
        args.key_password_file, source_root, "KEY_PASSWORD_FILE"
    )
    if store_password_file == key_password_file:
        raise SigningError("PASSWORD_FILES_NOT_SEPARATE")
    store_password = read_password(store_password_file, "STORE_PASSWORD")
    key_password = read_password(key_password_file, "KEY_PASSWORD")
    if store_password == key_password:
        raise SigningError("PASSWORDS_NOT_DISTINCT")

    expected_fingerprint = canonical_fingerprint(args.expected_fingerprint)
    if FINGERPRINT.fullmatch(expected_fingerprint) is None:
        raise SigningError("EXPECTED_FINGERPRINT_NOT_CANONICAL")
    if SHA256.fullmatch(args.expected_keystore_sha256) is None:
        raise SigningError("EXPECTED_KEYSTORE_SHA256_NOT_CANONICAL")

    actual_keystore_sha256 = sha256_file(keystore)
    if actual_keystore_sha256 != args.expected_keystore_sha256:
        raise SigningError("KEYSTORE_SHA256_MISMATCH")

    listing = run(
        [
            "keytool",
            "-list",
            "-v",
            "-alias",
            args.key_alias,
            "-keystore",
            str(keystore),
            "-storetype",
            "JKS",
            "-storepass:file",
            str(store_password_file),
        ]
    ).stdout
    if "Entry type: PrivateKeyEntry" not in listing:
        raise SigningError("ALIAS_NOT_PRIVATE_KEY_ENTRY")
    fingerprint_match = re.search(r"^\s*SHA256:\s*(.+?)\s*$", listing, re.MULTILINE)
    if fingerprint_match is None:
        raise SigningError("KEYTOOL_FINGERPRINT_MISSING")
    keytool_fingerprint = canonical_fingerprint(fingerprint_match.group(1))
    if keytool_fingerprint != expected_fingerprint:
        raise SigningError("KEYTOOL_FINGERPRINT_MISMATCH")

    with tempfile.TemporaryDirectory(prefix="subshell-auth05d2-") as temporary_name:
        temporary = Path(temporary_name)
        csr = temporary / "signing.csr.pem"
        certificate = temporary / "signing-cert.pem"
        run(
            [
                "keytool",
                "-certreq",
                "-rfc",
                "-alias",
                args.key_alias,
                "-keystore",
                str(keystore),
                "-storetype",
                "JKS",
                "-storepass:file",
                str(store_password_file),
                "-keypass:file",
                str(key_password_file),
                "-file",
                str(csr),
            ]
        )
        run(["openssl", "req", "-in", str(csr), "-noout", "-verify"])
        run(
            [
                "keytool",
                "-exportcert",
                "-rfc",
                "-alias",
                args.key_alias,
                "-keystore",
                str(keystore),
                "-storetype",
                "JKS",
                "-storepass:file",
                str(store_password_file),
                "-file",
                str(certificate),
            ]
        )
        openssl_fingerprint_output = run(
            ["openssl", "x509", "-in", str(certificate), "-noout", "-fingerprint", "-sha256"]
        ).stdout.strip()
        openssl_fingerprint = canonical_fingerprint(openssl_fingerprint_output.split("=", 1)[-1])
        if openssl_fingerprint != expected_fingerprint:
            raise SigningError("OPENSSL_FINGERPRINT_MISMATCH")
        certificate_text = run(
            ["openssl", "x509", "-in", str(certificate), "-noout", "-text"]
        ).stdout
        if "Public-Key: (4096 bit)" not in certificate_text:
            raise SigningError("PUBLIC_KEY_SIZE_NOT_RSA_4096")
        subject = run(
            ["openssl", "x509", "-in", str(certificate), "-noout", "-subject", "-nameopt", "RFC2253"]
        ).stdout.strip().removeprefix("subject=")
        serial = run(
            ["openssl", "x509", "-in", str(certificate), "-noout", "-serial"]
        ).stdout.strip().removeprefix("serial=")

    return {
        "application_id": args.application_id,
        "certificate_sha256_fingerprint": expected_fingerprint,
        "key_alias": args.key_alias,
        "key_algorithm": "RSA",
        "key_size": 4096,
        "keystore_file_sha256": actual_keystore_sha256,
        "keystore_path_included": False,
        "passwords_included": False,
        "private_key_access_verified": True,
        "published": False,
        "schema": SCHEMA_KEYSTORE,
        "serial": serial,
        "subject": subject,
    }


def verify_apk(args: argparse.Namespace) -> dict[str, object]:
    apk = args.apk.resolve(strict=True)
    expected_fingerprint = canonical_fingerprint(args.expected_fingerprint)
    badging = run([str(args.aapt2), "dump", "badging", str(apk)]).stdout
    package_match = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
    if package_match is None:
        raise SigningError("APK_APPLICATION_ID_MISSING")
    application_id = package_match.group(1)
    if application_id != args.application_id:
        raise SigningError("APK_APPLICATION_ID_MISMATCH")

    signature_output = run(
        [str(args.apksigner), "verify", "--verbose", "--print-certs", str(apk)]
    ).stdout
    digests = re.findall(
        r"^Signer #(\d+) certificate SHA-256 digest: (.+?)\s*$",
        signature_output,
        re.MULTILINE,
    )
    if len(digests) != 1 or digests[0][0] != "1":
        raise SigningError("APK_SIGNER_COUNT_NOT_ONE")
    actual_fingerprint = canonical_fingerprint(digests[0][1])
    if actual_fingerprint != expected_fingerprint:
        raise SigningError("APK_CERTIFICATE_FINGERPRINT_MISMATCH")

    return {
        "apk_file_sha256": sha256_file(apk),
        "application_id": application_id,
        "certificate_sha256_fingerprint": actual_fingerprint,
        "path_included": False,
        "published": False,
        "schema": SCHEMA_APK,
        "signer_count": 1,
        "signature_verified": True,
    }


def verify_artifact(args: argparse.Namespace) -> dict[str, object]:
    actual = json.loads(args.input.read_text(encoding="utf-8"))
    schema = actual.get("schema")
    if schema not in {SCHEMA_KEYSTORE, SCHEMA_APK}:
        raise SigningError("ARTIFACT_SCHEMA_MISMATCH")
    if actual.get("passwords_included") is True:
        raise SigningError("ARTIFACT_PASSWORD_FIELD_TRUE")
    if actual.get("keystore_path_included") is True or actual.get("path_included") is True:
        raise SigningError("ARTIFACT_PATH_FIELD_TRUE")
    if actual.get("published") is not False:
        raise SigningError("ARTIFACT_PUBLICATION_STATE_INVALID")
    return actual


def parser() -> argparse.ArgumentParser:
    top = argparse.ArgumentParser()
    commands = top.add_subparsers(dest="command", required=True)

    key = commands.add_parser("keystore")
    key.add_argument("--keystore", type=Path, required=True)
    key.add_argument("--store-password-file", type=Path, required=True)
    key.add_argument("--key-password-file", type=Path, required=True)
    key.add_argument("--key-alias", required=True)
    key.add_argument("--expected-fingerprint", required=True)
    key.add_argument("--expected-keystore-sha256", required=True)
    key.add_argument("--application-id", required=True)
    key.add_argument("--source-root", type=Path, required=True)
    key.add_argument("--output", type=Path, required=True)

    apk = commands.add_parser("apk")
    apk.add_argument("--apk", type=Path, required=True)
    apk.add_argument("--aapt2", type=Path, required=True)
    apk.add_argument("--apksigner", type=Path, required=True)
    apk.add_argument("--application-id", required=True)
    apk.add_argument("--expected-fingerprint", required=True)
    apk.add_argument("--output", type=Path, required=True)

    artifact = commands.add_parser("verify-artifact")
    artifact.add_argument("--input", type=Path, required=True)
    return top


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command == "keystore":
            value = verify_keystore(args)
            atomic_json(args.output, value)
            print("AUTH05D2_KEYSTORE_FILE_SHA256=" + str(value["keystore_file_sha256"]))
            print("AUTH05D2_RELEASE_CERT_SHA256=" + str(value["certificate_sha256_fingerprint"]))
            print("AUTH05D2_PRIVATE_KEY_ACCESS=PASS")
            print("AUTH05D2_PASSWORDS_IN_ARTIFACT=NO")
            print("RESULT=SUBSHELL_AUTH05D2_RELEASE_SIGNING_IDENTITY_PASS")
            return 0
        if args.command == "apk":
            value = verify_apk(args)
            atomic_json(args.output, value)
            print("AUTH05D2_RELEASE_APK_APPLICATION_ID=" + str(value["application_id"]))
            print("AUTH05D2_RELEASE_APK_CERT_SHA256=" + str(value["certificate_sha256_fingerprint"]))
            print("AUTH05D2_RELEASE_APK_SHA256=" + str(value["apk_file_sha256"]))
            print("RESULT=SUBSHELL_AUTH05D2_RELEASE_APK_PASS")
            return 0
        value = verify_artifact(args)
        print("AUTH05D2_ARTIFACT_SCHEMA=" + str(value["schema"]))
        print("AUTH05D2_ARTIFACT_PUBLIC_ONLY=PASS")
        print("RESULT=SUBSHELL_AUTH05D2_ARTIFACT_VERIFIED")
        return 0
    except (SigningError, OSError, UnicodeError, json.JSONDecodeError) as error:
        print(f"FAIL=AUTH05D2_{error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
