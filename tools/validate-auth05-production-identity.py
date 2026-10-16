#!/usr/bin/env python3
"""Validate and emit a deterministic AUTH05 production identity decision."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
from pathlib import Path

SCHEMA = "subshell.auth05.production_identity_decision.v1"
APPLICATION_ID_RE = re.compile(r"[a-z][a-z0-9_]{0,62}")
FINGERPRINT_RE = re.compile(r"(?:[0-9A-F]{2}:){31}[0-9A-F]{2}")


class IdentityError(ValueError):
    pass


def validate_application_id(value: str) -> str:
    if value != value.strip():
        raise IdentityError("APPLICATION_ID_WHITESPACE")
    if len(value) > 255:
        raise IdentityError("APPLICATION_ID_TOO_LONG")
    segments = value.split(".")
    if len(segments) < 3 or any(
        APPLICATION_ID_RE.fullmatch(segment) is None for segment in segments
    ):
        raise IdentityError("APPLICATION_ID_NOT_CANONICAL")
    if (
        value == "app.subshell.prototype"
        or value.startswith("android.")
        or value.startswith("com.example.")
        or value.startswith("org.example.")
        or value.startswith("net.example.")
        or value.endswith(
            (".prototype", ".debug", ".dev", ".test", ".example", ".invalid")
        )
    ):
        raise IdentityError("APPLICATION_ID_RESERVED_OR_DEVELOPMENT")
    return value


def validate_fingerprint(value: str) -> str:
    if value != value.strip():
        raise IdentityError("SIGNING_CERT_SHA256_WHITESPACE")
    if FINGERPRINT_RE.fullmatch(value) is None:
        raise IdentityError("SIGNING_CERT_SHA256_NOT_CANONICAL")
    return value


def validate_identity(
    application_id: str,
    fingerprint: str,
    forbidden_application_id: str | None,
    forbidden_fingerprint: str | None,
) -> tuple[str, str]:
    canonical_application_id = validate_application_id(application_id)
    canonical_fingerprint = validate_fingerprint(fingerprint)

    if (
        forbidden_application_id is not None
        and canonical_application_id == forbidden_application_id
    ):
        raise IdentityError("APPLICATION_ID_MATCHES_FORBIDDEN_IDENTITY")
    if (
        forbidden_fingerprint is not None
        and canonical_fingerprint == forbidden_fingerprint
    ):
        raise IdentityError("SIGNING_CERT_MATCHES_FORBIDDEN_DEBUG_IDENTITY")

    return canonical_application_id, canonical_fingerprint


def decision_payload(application_id: str, fingerprint: str) -> dict[str, object]:
    return {
        "application_id": application_id,
        "application_id_applied": False,
        "callback_uri_included": False,
        "oauth_client_id_included": False,
        "published": False,
        "release_signing_key_included": False,
        "schema": SCHEMA,
        "sha256_cert_fingerprints": [fingerprint],
        "status": "decision_only_not_applied",
    }


def atomic_write_json(path: Path, payload: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    serialized = json.dumps(payload, indent=2, sort_keys=True) + "\n"
    fd, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(serialized)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    def add_identity_arguments(command: argparse.ArgumentParser) -> None:
        command.add_argument("--application-id", required=True)
        command.add_argument("--sha256-cert-fingerprint", required=True)
        command.add_argument("--forbid-application-id")
        command.add_argument("--forbid-sha256-cert-fingerprint")

    validate = subparsers.add_parser("validate")
    add_identity_arguments(validate)

    generate = subparsers.add_parser("generate")
    add_identity_arguments(generate)
    generate.add_argument("--output", type=Path, required=True)

    verify = subparsers.add_parser("verify")
    add_identity_arguments(verify)
    verify.add_argument("--input", type=Path, required=True)

    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        application_id, fingerprint = validate_identity(
            args.application_id,
            args.sha256_cert_fingerprint,
            args.forbid_application_id,
            args.forbid_sha256_cert_fingerprint,
        )
        expected = decision_payload(application_id, fingerprint)

        if args.command == "validate":
            print("AUTH05_APPLICATION_ID_VALID=YES")
            print("AUTH05_SIGNING_CERT_SHA256_VALID=YES")
            print("RESULT=SUBSHELL_AUTH05_PRODUCTION_IDENTITY_VALID")
            return 0

        if args.command == "generate":
            atomic_write_json(args.output, expected)
            print(f"AUTH05_IDENTITY_DECISION_OUTPUT={args.output}")
            print("AUTH05_IDENTITY_APPLIED=NO")
            print("AUTH05_SIGNING_KEY_INCLUDED=NO")
            print("AUTH05_IDENTITY_DECISION_PUBLISHED=NO")
            print("RESULT=SUBSHELL_AUTH05_IDENTITY_DECISION_GENERATED")
            return 0

        actual = json.loads(args.input.read_text(encoding="utf-8"))
        if actual != expected:
            raise IdentityError("IDENTITY_DECISION_CONTENT_MISMATCH")
        print(f"AUTH05_IDENTITY_DECISION_INPUT={args.input}")
        print("AUTH05_IDENTITY_APPLIED=NO")
        print("AUTH05_SIGNING_KEY_INCLUDED=NO")
        print("AUTH05_IDENTITY_DECISION_PUBLISHED=NO")
        print("RESULT=SUBSHELL_AUTH05_IDENTITY_DECISION_VERIFIED")
        return 0
    except (IdentityError, OSError, json.JSONDecodeError) as error:
        print(f"FAIL=AUTH05_{error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
