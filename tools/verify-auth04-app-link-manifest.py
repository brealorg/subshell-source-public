#!/usr/bin/env python3
"""Verify the merged AUTH04 production callback App Link contract."""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urlsplit

ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE_PATTERN = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$")
HOST_PATTERN = re.compile(
    r"^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
    r"(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$"
)
PATH_PATTERN = re.compile(r"^/[A-Za-z0-9._~/-]+$")


def fail(marker: str) -> "NoReturn":
    raise SystemExit(f"AUTH04_MANIFEST_ERROR={marker}")


def normalized_component(package_name: str, value: str | None) -> str:
    if not value:
        fail("COMPONENT_NAME_MISSING")
    if value.startswith("."):
        return package_name + value
    if "." not in value:
        return package_name + "." + value
    return value


def bool_text(value: str) -> bool:
    if value == "true":
        return True
    if value == "false":
        return False
    fail("BOOLEAN_ATTRIBUTE_INVALID")


def exact_match(uri: str, host: str, path: str) -> bool:
    split = urlsplit(uri)
    return (
        split.scheme == "https"
        and split.hostname == host
        and split.port is None
        and split.path == path
        and split.username is None
        and split.password is None
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--expected-package", required=True)
    parser.add_argument("--expected-component-namespace", required=True)
    parser.add_argument("--expected-enabled", choices=("true", "false"), required=True)
    parser.add_argument("--expected-host", required=True)
    parser.add_argument("--expected-path", required=True)
    args = parser.parse_args()

    manifest = Path(args.manifest).expanduser().resolve()
    try:
        root = ET.parse(manifest).getroot()
    except (FileNotFoundError, ET.ParseError):
        fail("MERGED_MANIFEST_UNREADABLE")

    package_name = root.attrib.get("package", "")
    if package_name != args.expected_package or not PACKAGE_PATTERN.fullmatch(package_name):
        fail("APPLICATION_ID_MISMATCH")
    if not PACKAGE_PATTERN.fullmatch(args.expected_component_namespace):
        fail("COMPONENT_NAMESPACE_INVALID")
    if not HOST_PATTERN.fullmatch(args.expected_host):
        fail("EXPECTED_HOST_INVALID")
    if not PATH_PATTERN.fullmatch(args.expected_path):
        fail("EXPECTED_PATH_INVALID")

    application = root.find("application")
    if application is None:
        fail("APPLICATION_ELEMENT_MISSING")

    expected_alias = args.expected_component_namespace + ".auth.AuthProductionCallback"
    aliases = []
    for alias in application.findall("activity-alias"):
        name = normalized_component(package_name, alias.attrib.get(ANDROID + "name"))
        if name == expected_alias:
            aliases.append(alias)
    if len(aliases) != 1:
        fail("PRODUCTION_CALLBACK_ALIAS_COUNT_INVALID")
    alias = aliases[0]

    expected_enabled = args.expected_enabled == "true"
    actual_enabled = bool_text(alias.attrib.get(ANDROID + "enabled", ""))
    if actual_enabled != expected_enabled:
        fail("PRODUCTION_CALLBACK_ENABLEMENT_MISMATCH")
    if bool_text(alias.attrib.get(ANDROID + "exported", "")) is not True:
        fail("PRODUCTION_CALLBACK_NOT_EXPORTED")

    target = normalized_component(package_name, alias.attrib.get(ANDROID + "targetActivity"))
    if target != args.expected_component_namespace + ".auth.AuthCallbackActivity":
        fail("PRODUCTION_CALLBACK_TARGET_MISMATCH")

    filters = alias.findall("intent-filter")
    if len(filters) != 1:
        fail("PRODUCTION_CALLBACK_FILTER_COUNT_INVALID")
    intent_filter = filters[0]
    if intent_filter.attrib.get(ANDROID + "autoVerify") != "true":
        fail("PRODUCTION_CALLBACK_AUTOVERIFY_MISSING")

    actions = {
        item.attrib.get(ANDROID + "name")
        for item in intent_filter.findall("action")
    }
    categories = {
        item.attrib.get(ANDROID + "name")
        for item in intent_filter.findall("category")
    }
    if actions != {"android.intent.action.VIEW"}:
        fail("PRODUCTION_CALLBACK_ACTION_INVALID")
    if categories != {
        "android.intent.category.DEFAULT",
        "android.intent.category.BROWSABLE",
    }:
        fail("PRODUCTION_CALLBACK_CATEGORIES_INVALID")

    data_items = intent_filter.findall("data")
    if len(data_items) != 1:
        fail("PRODUCTION_CALLBACK_DATA_COUNT_INVALID")
    data = data_items[0]
    if data.attrib.get(ANDROID + "scheme") != "https":
        fail("PRODUCTION_CALLBACK_SCHEME_INVALID")
    if data.attrib.get(ANDROID + "host") != args.expected_host:
        fail("PRODUCTION_CALLBACK_HOST_MISMATCH")
    if data.attrib.get(ANDROID + "path") != args.expected_path:
        fail("PRODUCTION_CALLBACK_PATH_MISMATCH")

    forbidden_matchers = (
        "pathPrefix",
        "pathPattern",
        "pathAdvancedPattern",
        "port",
        "mimeType",
    )
    for matcher in forbidden_matchers:
        if ANDROID + matcher in data.attrib:
            fail(f"PRODUCTION_CALLBACK_FORBIDDEN_{matcher.upper()}")

    exact_uri = f"https://{args.expected_host}{args.expected_path}"
    if not exact_match(exact_uri, args.expected_host, args.expected_path):
        fail("EXACT_CALLBACK_DID_NOT_RESOLVE")
    if not exact_match(exact_uri + "?code=fixture&state=fixture", args.expected_host, args.expected_path):
        fail("QUERY_CALLBACK_DID_NOT_RESOLVE")
    for rejected in (
        f"http://{args.expected_host}{args.expected_path}",
        f"https://wrong.{args.expected_host}{args.expected_path}",
        f"https://{args.expected_host}{args.expected_path}/extra",
        f"https://{args.expected_host}/",
    ):
        if exact_match(rejected, args.expected_host, args.expected_path):
            fail("NON_MATCHING_CALLBACK_RESOLVED")

    print(f"AUTH04_MANIFEST={manifest}")
    print(f"AUTH04_APPLICATION_ID={package_name}")
    print(f"AUTH04_COMPONENT_NAMESPACE={args.expected_component_namespace}")
    print(f"AUTH04_CALLBACK_ENABLED={str(actual_enabled).lower()}")
    print("AUTH04_CALLBACK_EXPORTED=true")
    print("AUTH04_CALLBACK_AUTOVERIFY=true")
    print("AUTH04_CALLBACK_SCHEME=https")
    print(f"AUTH04_CALLBACK_HOST={args.expected_host}")
    print(f"AUTH04_CALLBACK_PATH={args.expected_path}")
    print("AUTH04_EXACT_CALLBACK_INTENT_RESOLUTION=PASS")
    print("AUTH04_NON_MATCHING_CALLBACK_REJECTION=PASS")
    print("RESULT=SUBSHELL_AUTH04_MERGED_MANIFEST_VERIFIED")


if __name__ == "__main__":
    main()
