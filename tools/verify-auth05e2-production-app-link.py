#!/usr/bin/env python3
"""Verify AUTH05E2 release App Link, Digital Asset Links, and device state."""

from __future__ import annotations

import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import NoReturn

ANDROID = "{http://schemas.android.com/apk/res/android}"
EXPECTED_APPLICATION_ID = "io.github.brealorg.subshell"
EXPECTED_COMPONENT_NAMESPACE = "app.subshell.prototype"
EXPECTED_ALIAS = EXPECTED_COMPONENT_NAMESPACE + ".auth.AuthProductionCallback"
EXPECTED_CALLBACK_ACTIVITY = EXPECTED_COMPONENT_NAMESPACE + ".auth.AuthCallbackActivity"
EXPECTED_HOST = "brealorg.github.io"
EXPECTED_PATH = "/oauth/callback"
EXPECTED_CALLBACK_URI = "https://brealorg.github.io/oauth/callback"
EXPECTED_CERT_FINGERPRINT = (
    "52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:"
    "39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84"
)


def fail(marker: str) -> NoReturn:
    raise SystemExit(f"AUTH05E2_ERROR={marker}")


def normalized_component(package_name: str, value: str | None) -> str:
    if not value:
        fail("COMPONENT_NAME_MISSING")
    if value.startswith("."):
        return package_name + value
    if "." not in value:
        return package_name + "." + value
    return value


def bool_text(value: str | None) -> bool:
    if value == "true":
        return True
    if value == "false":
        return False
    fail("BOOLEAN_ATTRIBUTE_INVALID")


def verify_manifest(path: Path) -> None:
    try:
        root = ET.parse(path).getroot()
    except (FileNotFoundError, ET.ParseError):
        fail("MERGED_RELEASE_MANIFEST_UNREADABLE")

    package_name = root.attrib.get("package", "")
    if package_name != EXPECTED_APPLICATION_ID:
        fail("RELEASE_APPLICATION_ID_MISMATCH")

    application = root.find("application")
    if application is None:
        fail("APPLICATION_ELEMENT_MISSING")

    callback_activities = []
    for activity in application.findall("activity"):
        name = normalized_component(
            package_name,
            activity.attrib.get(ANDROID + "name"),
        )
        if name == EXPECTED_CALLBACK_ACTIVITY:
            callback_activities.append(activity)
    if len(callback_activities) != 1:
        fail("CALLBACK_ACTIVITY_COUNT_INVALID")
    callback_activity = callback_activities[0]
    if bool_text(callback_activity.attrib.get(ANDROID + "exported")) is not False:
        fail("CALLBACK_ACTIVITY_MUST_BE_NON_EXPORTED")
    if bool_text(callback_activity.attrib.get(ANDROID + "noHistory")) is not True:
        fail("CALLBACK_ACTIVITY_NO_HISTORY_MISSING")
    if callback_activity.findall("intent-filter"):
        fail("CALLBACK_ACTIVITY_DIRECT_FILTER_PRESENT")

    aliases = []
    for alias in application.findall("activity-alias"):
        name = normalized_component(
            package_name,
            alias.attrib.get(ANDROID + "name"),
        )
        if name == EXPECTED_ALIAS:
            aliases.append(alias)
    if len(aliases) != 1:
        fail("PRODUCTION_ALIAS_COUNT_INVALID")
    alias = aliases[0]

    if bool_text(alias.attrib.get(ANDROID + "enabled")) is not True:
        fail("PRODUCTION_ALIAS_NOT_ENABLED")
    if bool_text(alias.attrib.get(ANDROID + "exported")) is not True:
        fail("PRODUCTION_ALIAS_NOT_EXPORTED")
    target = normalized_component(
        package_name,
        alias.attrib.get(ANDROID + "targetActivity"),
    )
    if target != EXPECTED_CALLBACK_ACTIVITY:
        fail("PRODUCTION_ALIAS_TARGET_MISMATCH")

    filters = alias.findall("intent-filter")
    if len(filters) != 1:
        fail("PRODUCTION_FILTER_COUNT_INVALID")
    intent_filter = filters[0]
    if intent_filter.attrib.get(ANDROID + "autoVerify") != "true":
        fail("PRODUCTION_AUTOVERIFY_MISSING")

    actions = {
        node.attrib.get(ANDROID + "name")
        for node in intent_filter.findall("action")
    }
    if actions != {"android.intent.action.VIEW"}:
        fail("PRODUCTION_ACTION_INVALID")

    categories = {
        node.attrib.get(ANDROID + "name")
        for node in intent_filter.findall("category")
    }
    if categories != {
        "android.intent.category.DEFAULT",
        "android.intent.category.BROWSABLE",
    }:
        fail("PRODUCTION_CATEGORIES_INVALID")

    data_items = intent_filter.findall("data")
    if len(data_items) != 1:
        fail("PRODUCTION_DATA_COUNT_INVALID")
    data = data_items[0]
    expected = {
        ANDROID + "scheme": "https",
        ANDROID + "host": EXPECTED_HOST,
        ANDROID + "path": EXPECTED_PATH,
    }
    if data.attrib != expected:
        fail("PRODUCTION_DATA_EXACT_MATCH_FAILED")

    schemes = {
        node.attrib.get(ANDROID + "scheme")
        for node in application.findall(".//data")
    }
    if "subshell-auth-test" in schemes:
        fail("DEBUG_CUSTOM_SCHEME_LEAKED_INTO_RELEASE")

    print(f"AUTH05E2_MANIFEST={path}")
    print(f"AUTH05E2_APPLICATION_ID={package_name}")
    print(f"AUTH05E2_CALLBACK_ALIAS={EXPECTED_ALIAS}")
    print(f"AUTH05E2_CALLBACK_ACTIVITY={EXPECTED_CALLBACK_ACTIVITY}")
    print(f"AUTH05E2_CALLBACK_URI={EXPECTED_CALLBACK_URI}")
    print("AUTH05E2_RELEASE_CALLBACK_ENABLED=true")
    print("AUTH05E2_RELEASE_CALLBACK_AUTOVERIFY=true")
    print("AUTH05E2_RELEASE_CALLBACK_EXACT_DATA=PASS")
    print("AUTH05E2_DEBUG_SCHEME_IN_RELEASE=ABSENT")
    print("RESULT=SUBSHELL_AUTH05E2_RELEASE_MANIFEST_VERIFIED")


def verify_assetlinks(path: Path) -> None:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, UnicodeDecodeError, json.JSONDecodeError):
        fail("ASSETLINKS_UNREADABLE")

    expected = [
        {
            "relation": ["delegate_permission/common.handle_all_urls"],
            "target": {
                "namespace": "android_app",
                "package_name": EXPECTED_APPLICATION_ID,
                "sha256_cert_fingerprints": [EXPECTED_CERT_FINGERPRINT],
            },
        }
    ]
    if payload != expected:
        fail("ASSETLINKS_EXACT_IDENTITY_MISMATCH")

    print(f"AUTH05E2_ASSETLINKS={path}")
    print(f"AUTH05E2_ASSETLINKS_APPLICATION_ID={EXPECTED_APPLICATION_ID}")
    print(f"AUTH05E2_ASSETLINKS_CERT_SHA256={EXPECTED_CERT_FINGERPRINT}")
    print("AUTH05E2_ASSETLINKS_RELATION=delegate_permission/common.handle_all_urls")
    print("RESULT=SUBSHELL_AUTH05E2_ASSETLINKS_VERIFIED")


def verify_domain_state(path: Path) -> None:
    try:
        text = path.read_text(encoding="utf-8", errors="strict").replace("\r", "")
    except (FileNotFoundError, UnicodeDecodeError):
        fail("DOMAIN_STATE_UNREADABLE")

    if not re.search(
        rf"(?m)^\s*{re.escape(EXPECTED_APPLICATION_ID)}:\s*$",
        text,
    ):
        fail("DOMAIN_STATE_PACKAGE_MISSING")

    matches = re.findall(
        rf"(?mi)^\s*{re.escape(EXPECTED_HOST)}\s*:\s*([a-z0-9_]+)\s*$",
        text,
    )
    if len(matches) != 1:
        fail("DOMAIN_STATE_HOST_COUNT_INVALID")
    state = matches[0].lower()
    if state != "verified":
        fail(f"DOMAIN_STATE_NOT_VERIFIED_{state.upper()}")

    print(f"AUTH05E2_DOMAIN_STATE={path}")
    print(f"AUTH05E2_DOMAIN={EXPECTED_HOST}")
    print("AUTH05E2_DOMAIN_VERIFICATION_STATE=verified")
    print("RESULT=SUBSHELL_AUTH05E2_DOMAIN_STATE_VERIFIED")


def verify_resolver(path: Path) -> None:
    try:
        text = path.read_text(encoding="utf-8", errors="strict").replace("\r", "")
    except (FileNotFoundError, UnicodeDecodeError):
        fail("RESOLVER_OUTPUT_UNREADABLE")

    allowed = {
        f"{EXPECTED_APPLICATION_ID}/{EXPECTED_ALIAS}",
        f"{EXPECTED_APPLICATION_ID}/{EXPECTED_CALLBACK_ACTIVITY}",
        f"{EXPECTED_APPLICATION_ID}/.{EXPECTED_ALIAS.rsplit('.', 1)[-1]}",
    }
    compact_lines = {line.strip() for line in text.splitlines() if line.strip()}
    if not any(
        line in allowed
        or (
            line.startswith(EXPECTED_APPLICATION_ID + "/")
            and (
                "AuthProductionCallback" in line
                or "AuthCallbackActivity" in line
            )
        )
        for line in compact_lines
    ):
        fail("UNFORCED_CALLBACK_RESOLVER_NOT_SUBSHELL")

    print(f"AUTH05E2_RESOLVER_OUTPUT={path}")
    print(f"AUTH05E2_RESOLVER_APPLICATION_ID={EXPECTED_APPLICATION_ID}")
    print("AUTH05E2_UNFORCED_CALLBACK_RESOLUTION=PASS")
    print("RESULT=SUBSHELL_AUTH05E2_RESOLVER_VERIFIED")


def main() -> None:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    for name in ("manifest", "assetlinks", "domain-state", "resolver"):
        command = subparsers.add_parser(name)
        command.add_argument("--input", type=Path, required=True)

    args = parser.parse_args()
    path = args.input.expanduser().resolve()
    if args.command == "manifest":
        verify_manifest(path)
    elif args.command == "assetlinks":
        verify_assetlinks(path)
    elif args.command == "domain-state":
        verify_domain_state(path)
    elif args.command == "resolver":
        verify_resolver(path)
    else:
        fail("UNKNOWN_COMMAND")


if __name__ == "__main__":
    main()
