#!/usr/bin/env python3
"""Generate or verify an AUTH04 Digital Asset Links deployment artifact."""

from __future__ import annotations

import argparse
import json
import os
import re
import tempfile
from pathlib import Path
from typing import Iterable

RELATION = "delegate_permission/common.handle_all_urls"
PACKAGE_PATTERN = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$")
HEX_PATTERN = re.compile(r"^[0-9A-Fa-f]{64}$")


def fail(message: str) -> "NoReturn":
    raise SystemExit(f"AUTH04_ASSETLINKS_ERROR={message}")


def normalize_fingerprint(raw: str) -> str:
    compact = raw.strip().replace(":", "")
    if not HEX_PATTERN.fullmatch(compact):
        fail("SHA256_CERT_FINGERPRINT_INVALID")
    upper = compact.upper()
    return ":".join(upper[index : index + 2] for index in range(0, 64, 2))


def validate_package_name(value: str) -> str:
    package_name = value.strip()
    if package_name != value or not PACKAGE_PATTERN.fullmatch(package_name):
        fail("PACKAGE_NAME_INVALID")
    return package_name


def normalized_fingerprints(values: Iterable[str]) -> list[str]:
    result: list[str] = []
    for value in values:
        fingerprint = normalize_fingerprint(value)
        if fingerprint not in result:
            result.append(fingerprint)
    if not result:
        fail("SHA256_CERT_FINGERPRINT_MISSING")
    return sorted(result)


def expected_document(package_name: str, fingerprints: list[str]) -> list[dict[str, object]]:
    return [
        {
            "relation": [RELATION],
            "target": {
                "namespace": "android_app",
                "package_name": package_name,
                "sha256_cert_fingerprints": fingerprints,
            },
        },
    ]


def atomic_write_json(output: Path, document: object) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        dir=output.parent,
        prefix=f".{output.name}.",
        suffix=".tmp",
        delete=False,
    ) as handle:
        json.dump(document, handle, indent=2, ensure_ascii=True)
        handle.write("\n")
        temporary = Path(handle.name)
    os.chmod(temporary, 0o644)
    os.replace(temporary, output)


def load_document(path: Path) -> object:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail("FILE_MISSING")
    except (UnicodeDecodeError, json.JSONDecodeError):
        fail("JSON_INVALID")


def command_generate(args: argparse.Namespace) -> None:
    package_name = validate_package_name(args.package_name)
    fingerprints = normalized_fingerprints(args.sha256_cert_fingerprint)
    output = Path(args.output).expanduser().resolve()
    atomic_write_json(output, expected_document(package_name, fingerprints))
    print(f"ASSETLINKS_OUTPUT={output}")
    print(f"ASSETLINKS_PACKAGE_NAME={package_name}")
    print(f"ASSETLINKS_FINGERPRINT_COUNT={len(fingerprints)}")
    print("ASSETLINKS_PUBLISHED=NO")
    print("RESULT=SUBSHELL_AUTH04_ASSETLINKS_GENERATED")


def command_verify(args: argparse.Namespace) -> None:
    package_name = validate_package_name(args.package_name)
    fingerprints = normalized_fingerprints(args.sha256_cert_fingerprint)
    path = Path(args.input).expanduser().resolve()
    document = load_document(path)
    if document != expected_document(package_name, fingerprints):
        fail("DOCUMENT_CONTENT_MISMATCH")
    if path.name != "assetlinks.json":
        fail("DEPLOYMENT_FILENAME_INVALID")
    print(f"ASSETLINKS_INPUT={path}")
    print(f"ASSETLINKS_PACKAGE_NAME={package_name}")
    print(f"ASSETLINKS_FINGERPRINT_COUNT={len(fingerprints)}")
    print("ASSETLINKS_RELATION=HANDLE_ALL_URLS")
    print("RESULT=SUBSHELL_AUTH04_ASSETLINKS_VERIFIED")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    generate = subparsers.add_parser("generate")
    generate.add_argument("--package-name", required=True)
    generate.add_argument(
        "--sha256-cert-fingerprint",
        action="append",
        required=True,
    )
    generate.add_argument("--output", required=True)
    generate.set_defaults(handler=command_generate)

    verify = subparsers.add_parser("verify")
    verify.add_argument("--package-name", required=True)
    verify.add_argument(
        "--sha256-cert-fingerprint",
        action="append",
        required=True,
    )
    verify.add_argument("--input", required=True)
    verify.set_defaults(handler=command_verify)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    args.handler(args)


if __name__ == "__main__":
    main()
