#!/usr/bin/env python3
"""Artifact-free offline tests for RELEASETOOL02C orchestration policy."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[2]
DRIVER = ROOT / "tools/release02/release-beta.py"

spec = importlib.util.spec_from_file_location("release02c", DRIVER)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def expect_error(fn, marker: str) -> None:
    try:
        fn()
    except module.OrchestratorError as exc:
        assert marker in str(exc), (marker, str(exc))
    else:
        raise AssertionError(f"expected OrchestratorError containing {marker}")


with tempfile.TemporaryDirectory() as temp:
    root = Path(temp)
    promo = root / "promotion-state.env"
    promo.write_text(
        "\n".join(
            [
                "RELEASETOOL02_PUBLIC_PROMOTION_RECORD_VERSION=1",
                "STATE=REMOTE_APK_VERIFIED",
                "SOURCE_COMMIT=" + "1" * 40,
                "SOURCE_TREE=" + "2" * 40,
                "VERSION_NAME=0.80.0-beta.1",
                "VERSION_CODE=300",
                "APPLICATION_ID=io.github.brealorg.subshell",
                "APK_SHA256=" + "a" * 64,
                "SIGNER_CERT_SHA256=AA:BB",
                "PUBLIC_REPOSITORY=brealorg/subshell",
                "PUBLIC_TARGET_COMMIT=" + "3" * 40,
                "TAG=v0.80.0-beta.1",
                "TITLE=Subshell 0.80.0-beta.1",
                "REMOTE_APK_VERIFIED=YES",
                "PRIVATE_APP_SOURCE_PUBLIC=NO",
                "NEXT_STAGE=PUBLIC_SITE_PROMOTION",
                "",
            ]
        )
    )

    state = module.validate_promotion_state_for_resume(
        promo,
        "brealorg/subshell",
    )
    assert state["STATE"] == "REMOTE_APK_VERIFIED"

    bad = root / "bad.env"
    bad.write_text(promo.read_text().replace(
        "REMOTE_APK_VERIFIED=YES",
        "REMOTE_APK_VERIFIED=NO",
    ))
    expect_error(
        lambda: module.validate_promotion_state_for_resume(
            bad,
            "brealorg/subshell",
        ),
        "PROMOTION_STATE_INVALID_REMOTE_APK_VERIFIED",
    )

    args = SimpleNamespace(
        qualified_source_root="/tmp/source",
        public_repo="brealorg/subshell",
        public_main_before="4" * 40,
        pages_repo="brealorg/brealorg.github.io",
        pages_main_before="5" * 40,
        previous_version="0.79.0-beta.9",
        manifest_url="https://brealorg.github.io/subshell-update-beta.json",
        site_url="https://brealorg.github.io/subshell/",
        propagation_attempts=36,
        propagation_delay_seconds=5.0,
    )

    cmd = module.build_stage_b_command(
        tool_root=Path("/tools/release02"),
        promotion_state=promo,
        args=args,
        output=Path("/tmp/out"),
    )
    assert "promote-public-site-manifest.py" in cmd[1]
    assert "--promotion-state" in cmd
    assert "--public-main-before" in cmd
    assert "--pages-main-before" in cmd

    pub = root / "publication-state.env"
    pub.write_text(
        "\n".join(
            [
                "RELEASETOOL02_PUBLICATION_RECORD_VERSION=1",
                "STATE=COMPLETE",
                "SOURCE_COMMIT=" + "1" * 40,
                "SOURCE_TREE=" + "2" * 40,
                "VERSION_NAME=0.80.0-beta.1",
                "VERSION_CODE=300",
                "APPLICATION_ID=io.github.brealorg.subshell",
                "APK_SHA256=" + "a" * 64,
                "PRIVATE_APP_SOURCE_PUBLIC=NO",
                "PRIMARY_FAILURE=NO",
                "PREDECESSOR_FAILURE=NO",
                "RELEASE_BLOCKING_FAILURE=NO",
                "",
            ]
        )
    )

    final = module.write_orchestrator_state(
        output=root,
        mode="RESUME",
        existing_only=True,
        promotion_state=promo,
        publication_state=pub,
    )
    final_state = module.parse_env(final)
    assert final_state["STATE"] == "COMPLETE"
    assert final_state["MODE"] == "RESUME"
    assert final_state["EXISTING_ONLY"] == "YES"
    assert final_state["RELEASE_BLOCKING_FAILURE"] == "NO"

    broken_pub = root / "broken-publication.env"
    broken_pub.write_text(pub.read_text().replace(
        "RELEASE_BLOCKING_FAILURE=NO",
        "RELEASE_BLOCKING_FAILURE=YES",
    ))
    expect_error(
        lambda: module.write_orchestrator_state(
            output=root,
            mode="RESUME",
            existing_only=False,
            promotion_state=promo,
            publication_state=broken_pub,
        ),
        "FINAL_RELEASE_BLOCKING_FAILURE",
    )

print("PASS=RELEASETOOL02C_FULL_OR_RESUME_INPUT_MODEL")
print("PASS=RELEASETOOL02C_REMOTE_APK_VERIFIED_RESUME_FENCE")
print("PASS=RELEASETOOL02C_EXISTING_ONLY_POLICY_SURFACE")
print("PASS=RELEASETOOL02C_STAGE_B_COMMAND_WIRING")
print("PASS=RELEASETOOL02C_COMPLETE_FINAL_STATE")
print("PASS=RELEASETOOL02C_RELEASE_BLOCKING_PROPAGATION")
print("RESULT=SUBSHELL_RELEASETOOL02C_SELFTEST_PASS")
