#!/usr/bin/env python3
"""Offline policy/self-test for RELEASETOOL02A pure functions."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import tempfile


ROOT = Path(__file__).resolve().parents[2]
DRIVER = ROOT / "tools/release02/promote-github-beta.py"

spec = importlib.util.spec_from_file_location("release02a", DRIVER)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def expect_error(fn, marker: str) -> None:
    try:
        fn()
    except module.PromotionError as exc:
        assert marker in str(exc), (marker, str(exc))
    else:
        raise AssertionError(f"expected PromotionError containing {marker}")


record = {
    "SOURCE_COMMIT": "1" * 40,
    "SOURCE_TREE": "2" * 40,
    "VERSION_NAME": "0.80.0-beta.1",
    "VERSION_CODE": "300",
    "APPLICATION_ID": "io.github.brealorg.subshell",
    "SIGNER_CERT_SHA256": "AA:BB",
    "APK_SHA256": "a" * 64,
    "APK_SIZE_BYTES": "123",
    "PREVIEW_APK": "/tmp/example.apk",
    "RELEASE_GATE": "PASS",
}

module.validate_record_values(record)
module.validate_topology(record["SOURCE_COMMIT"], "3" * 40, "brealorg/subshell")

names = module.asset_names(record["VERSION_NAME"])
assert names == (
    "Subshell-0.80.0-beta.1.apk",
    "Subshell-0.80.0-beta.1.apk.sha256",
    "release-record.txt",
)

public_record = module.public_record_text(
    record,
    "brealorg/subshell",
    "3" * 40,
    "v0.80.0-beta.1",
    names[0],
)
assert "app_source_commit=" + "1" * 40 in public_record
assert "app_source_private_commit_public=NO" in public_record
# SECAUDIT26: the record names the public snapshot and no longer says the
# source is unpublished.
assert "app_source_public_repository=brealorg/subshell-source-public" in public_record
assert "app_source_public_tag=v0.80.0-beta.1" in public_record
assert "app_source_public_tree=" + record["SOURCE_TREE"] in public_record
assert "app_source_publicly_pushed" not in public_record
assert "PROJECT_INFRA_SITE_RELEASES_ONLY" not in public_record
assert "public_release_target_commit=" + "3" * 40 in public_record
assert "qualified_apk_rebuilt=NO_BIT_IDENTICAL_PREVIEW_PROMOTION" in public_record

expected = {
    names[0]: ("a" * 64, 123),
    names[1]: ("b" * 64, 90),
    names[2]: ("c" * 64, 500),
}

payload_exact = {
    "tag_name": "v0.80.0-beta.1",
    "target_commitish": "3" * 40,
    "prerelease": True,
    "draft": False,
    "name": "Subshell 0.80.0-beta.1",
    "assets": [
        {
            "name": name,
            "state": "uploaded",
            "digest": f"sha256:{sha}",
            "size": size,
        }
        for name, (sha, size) in expected.items()
    ],
}

missing = module.verify_release_payload(
    payload_exact,
    tag="v0.80.0-beta.1",
    title="Subshell 0.80.0-beta.1",
    public_target="3" * 40,
    expected_assets=expected,
    allow_missing_assets=False,
)
assert missing == set()

payload_missing = dict(payload_exact)
payload_missing["assets"] = payload_exact["assets"][:1]
missing = module.verify_release_payload(
    payload_missing,
    tag="v0.80.0-beta.1",
    title="Subshell 0.80.0-beta.1",
    public_target="3" * 40,
    expected_assets=expected,
    allow_missing_assets=True,
)
assert missing == {names[1], names[2]}

expect_error(
    lambda: module.verify_release_payload(
        payload_missing,
        tag="v0.80.0-beta.1",
        title="Subshell 0.80.0-beta.1",
        public_target="3" * 40,
        expected_assets=expected,
        allow_missing_assets=False,
    ),
    "REMOTE_MISSING_ASSETS",
)

payload_unexpected = dict(payload_exact)
payload_unexpected["assets"] = payload_exact["assets"] + [
    {
        "name": "unexpected.bin",
        "state": "uploaded",
        "digest": "sha256:" + "d" * 64,
        "size": 1,
    }
]
expect_error(
    lambda: module.verify_release_payload(
        payload_unexpected,
        tag="v0.80.0-beta.1",
        title="Subshell 0.80.0-beta.1",
        public_target="3" * 40,
        expected_assets=expected,
        allow_missing_assets=True,
    ),
    "REMOTE_UNEXPECTED_ASSETS",
)

payload_wrong_digest = dict(payload_exact)
payload_wrong_digest["assets"] = [dict(x) for x in payload_exact["assets"]]
payload_wrong_digest["assets"][0]["digest"] = "sha256:" + "0" * 64
expect_error(
    lambda: module.verify_release_payload(
        payload_wrong_digest,
        tag="v0.80.0-beta.1",
        title="Subshell 0.80.0-beta.1",
        public_target="3" * 40,
        expected_assets=expected,
        allow_missing_assets=False,
    ),
    "REMOTE_ASSET_DIGEST_MISMATCH",
)

proof = dict(record)
proof["VERSION_NAME"] = "0.80.0-beta.1-proof"
expect_error(lambda: module.validate_record_values(proof), "PROOF_VERSION_FORBIDDEN")

expect_error(
    lambda: module.validate_topology(record["SOURCE_COMMIT"], "main", "brealorg/subshell"),
    "PUBLIC_TARGET_NOT_IMMUTABLE_SHA",
)

with tempfile.TemporaryDirectory() as temp_dir:
    path = Path(temp_dir) / "record.env"
    path.write_text("A=1\nA=2\n")
    expect_error(lambda: module.parse_env_record(path), "DUPLICATE_KEY")

print("PASS=RELEASETOOL02A_CANONICAL_PUBLIC_ASSET_NAMES")
print("PASS=RELEASETOOL02A_PRIVATE_PUBLIC_TOPOLOGY")
print("PASS=RELEASETOOL02A_EXPLICIT_IMMUTABLE_PUBLIC_TARGET")
print("PASS=RELEASETOOL02A_LOCALE_INDEPENDENT_ASSET_SET")
print("PASS=RELEASETOOL02A_RESUME_MISSING_ASSET_CLASSIFICATION")
print("PASS=RELEASETOOL02A_FAIL_CLOSED_WRONG_OR_UNEXPECTED_ASSETS")
print("PASS=RELEASETOOL02A_PROOF_VERSION_FORBIDDEN")
print("PASS=RELEASETOOL02A_PUBLIC_PROVENANCE_RECORD")
print("RESULT=SUBSHELL_RELEASETOOL02A_SELFTEST_PASS")
