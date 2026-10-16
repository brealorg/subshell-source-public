#!/usr/bin/env python3
"""Generate and verify a deterministic AUTH05C application-ID migration artifact."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
from pathlib import Path

SCHEMA = "subshell.auth05c.application_id_migration.v1"
PACKAGE_SEGMENT = re.compile(r"[a-z][a-z0-9_]{0,62}")


class MigrationError(ValueError):
    pass


def canonical_package(value: str, label: str) -> str:
    if value != value.strip():
        raise MigrationError(f"{label}_WHITESPACE")
    if len(value) > 255:
        raise MigrationError(f"{label}_TOO_LONG")
    segments = value.split(".")
    if len(segments) < 3 or any(
        PACKAGE_SEGMENT.fullmatch(segment) is None for segment in segments
    ):
        raise MigrationError(f"{label}_NOT_CANONICAL")
    return value


def validated_values(
    application_id: str,
    debug_application_id: str,
    test_application_id: str,
    component_namespace: str,
) -> tuple[str, str, str, str]:
    application_id = canonical_package(application_id, "APPLICATION_ID")
    debug_application_id = canonical_package(
        debug_application_id,
        "DEBUG_APPLICATION_ID",
    )
    test_application_id = canonical_package(
        test_application_id,
        "TEST_APPLICATION_ID",
    )
    component_namespace = canonical_package(
        component_namespace,
        "COMPONENT_NAMESPACE",
    )

    if application_id != "io.github.brealorg.subshell":
        raise MigrationError("APPLICATION_ID_NOT_SELECTED_IDENTITY")
    if debug_application_id != application_id + ".debug":
        raise MigrationError("DEBUG_APPLICATION_ID_RELATION_MISMATCH")
    if test_application_id != debug_application_id + ".test":
        raise MigrationError("TEST_APPLICATION_ID_RELATION_MISMATCH")
    if component_namespace != "app.subshell.prototype":
        raise MigrationError("COMPONENT_NAMESPACE_MISMATCH")
    if component_namespace == application_id:
        raise MigrationError("NAMESPACE_MIGRATION_WAS_NOT_DEFERRED")

    return (
        application_id,
        debug_application_id,
        test_application_id,
        component_namespace,
    )


def payload(
    application_id: str,
    debug_application_id: str,
    test_application_id: str,
    component_namespace: str,
) -> dict[str, object]:
    return {
        "application_id": application_id,
        "application_id_applied": True,
        "component_namespace": component_namespace,
        "debug_application_id": debug_application_id,
        "debug_suffix_applied": True,
        "namespace_migration_deferred": True,
        "oauth_client_id_included": False,
        "production_app_link_deployed": False,
        "published": False,
        "release_signing_configured_at_auth05c": False,
        "release_signing_configured_by_auth05d2": True,
        "schema": SCHEMA,
        "signing_key_included": False,
        "test_application_id": test_application_id,
        "test_application_id_locked": True,
    }


def atomic_write(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = json.dumps(value, indent=2, sort_keys=True) + "\n"
    fd, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.",
        suffix=".tmp",
        dir=path.parent,
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def add_identity_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--application-id", required=True)
    parser.add_argument("--debug-application-id", required=True)
    parser.add_argument("--test-application-id", required=True)
    parser.add_argument("--component-namespace", required=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)

    generate = sub.add_parser("generate")
    add_identity_args(generate)
    generate.add_argument("--output", type=Path, required=True)

    verify = sub.add_parser("verify")
    add_identity_args(verify)
    verify.add_argument("--input", type=Path, required=True)

    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        values = validated_values(
            args.application_id,
            args.debug_application_id,
            args.test_application_id,
            args.component_namespace,
        )
        expected = payload(*values)

        if args.command == "generate":
            atomic_write(args.output, expected)
            print(f"AUTH05C_MIGRATION_OUTPUT={args.output}")
            print("AUTH05C_APPLICATION_ID_APPLIED=YES")
            print("AUTH05C_RELEASE_SIGNING_CONFIGURED_AT_AUTH05C=NO")
            print("AUTH05C_RELEASE_SIGNING_CONFIGURED_BY_AUTH05D2=YES")
            print("AUTH05C_MIGRATION_PUBLISHED=NO")
            print("RESULT=SUBSHELL_AUTH05C_MIGRATION_ARTIFACT_GENERATED")
            return 0

        actual = json.loads(args.input.read_text(encoding="utf-8"))
        if actual != expected:
            raise MigrationError("MIGRATION_ARTIFACT_CONTENT_MISMATCH")
        print(f"AUTH05C_MIGRATION_INPUT={args.input}")
        print("AUTH05C_APPLICATION_ID_APPLIED=YES")
        print("AUTH05C_RELEASE_SIGNING_CONFIGURED_AT_AUTH05C=NO")
        print("AUTH05C_RELEASE_SIGNING_CONFIGURED_BY_AUTH05D2=YES")
        print("AUTH05C_MIGRATION_PUBLISHED=NO")
        print("RESULT=SUBSHELL_AUTH05C_MIGRATION_ARTIFACT_VERIFIED")
        return 0
    except (MigrationError, OSError, json.JSONDecodeError) as error:
        print(f"FAIL=AUTH05C_{error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
