#!/usr/bin/env python3
"""Artifact-free offline tests for RELEASETOOL02D operator orchestration."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[2]
DRIVER = ROOT / "tools/release02/release-beta-operator.py"

spec = importlib.util.spec_from_file_location("release02d", DRIVER)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def expect_error(fn, marker: str) -> None:
    try:
        fn()
    except module.OperatorError as exc:
        assert marker in str(exc), (marker, str(exc))
    else:
        raise AssertionError(f"expected OperatorError containing {marker}")


assert module.STATES == (
    "INIT",
    "QUALIFIED",
    "SIGNED",
    "DEVICE_SMOKED",
    "COMPLETE",
)
assert module.STATE_RANK["INIT"] < module.STATE_RANK["QUALIFIED"]
assert module.STATE_RANK["QUALIFIED"] < module.STATE_RANK["SIGNED"]
assert module.STATE_RANK["SIGNED"] < module.STATE_RANK["DEVICE_SMOKED"]
assert module.STATE_RANK["DEVICE_SMOKED"] < module.STATE_RANK["COMPLETE"]

source = Path("/source")
args = SimpleNamespace(
    version_name="0.80.0-beta.1",
    version_code=300,
    serial="SERIAL1",
    public_repo="brealorg/subshell",
    public_target="3" * 40,
    title="Subshell 0.80.0-beta.1",
    notes_file="/tmp/notes.md",
    public_main_before="4" * 40,
    pages_repo="brealorg/brealorg.github.io",
    pages_main_before="5" * 40,
    previous_version="0.79.0-beta.9",
    manifest_url="https://brealorg.github.io/subshell-update-beta.json",
    site_url="https://brealorg.github.io/subshell/",
    propagation_attempts=36,
    propagation_delay_seconds=5.0,
)

qualify = module.build_qualification_command(
    source_root=source,
    base="1" * 40,
    manifest=source / "tools/contract02/manifests/release.tsv",
    output=Path("/tmp/q"),
)
assert qualify == [
    "bash",
    "/source/tools/contract02/qualify-delta.sh",
    "--base",
    "1" * 40,
    "--manifest",
    "/source/tools/contract02/manifests/release.tsv",
    "--output",
    "/tmp/q",
]

signed = module.build_signed_command(
    source_root=source,
    qualification_record=Path("/tmp/q/qualification-record.env"),
    version_name=args.version_name,
    version_code=args.version_code,
    output=Path("/tmp/signed"),
)
assert "--qualification-record" in signed
assert signed[signed.index("--version-name") + 1] == args.version_name
assert signed[signed.index("--version-code") + 1] == "300"

smoke = module.build_smoke_command(
    source_root=source,
    release_record=Path("/tmp/signed/release-record.env"),
    serial=args.serial,
)
assert "--release-record" in smoke
assert "--qualified-source-root" in smoke
assert smoke[-2:] == ["--serial", "SERIAL1"]

publication = module.build_publication_command(
    source_root=source,
    release_record=Path("/tmp/signed/release-record.env"),
    args=args,
    output=Path("/tmp/publication"),
)
assert publication[1] == "/source/tools/release02/release-beta.py"
assert publication[publication.index("--release-record") + 1] == (
    "/tmp/signed/release-record.env"
)
assert publication[publication.index("--public-target") + 1] == "3" * 40
assert publication[publication.index("--public-main-before") + 1] == "4" * 40
assert publication[publication.index("--pages-main-before") + 1] == "5" * 40

with tempfile.TemporaryDirectory() as temp:
    root = Path(temp)
    state = root / "operator-state.env"
    immutable = [
        ("SOURCE_COMMIT", "1" * 40),
        ("SOURCE_TREE", "2" * 40),
        ("VERSION_NAME", "0.80.0-beta.1"),
    ]
    module.write_state(
        state_path=state,
        state="QUALIFIED",
        immutable=immutable,
        dynamic={"QUALIFICATION_RECORD": "/tmp/q/qualification-record.env"},
    )
    loaded_state, values = module.validate_resume_state(
        state_path=state,
        immutable=immutable,
    )
    assert loaded_state == "QUALIFIED"
    assert values["QUALIFICATION_RECORD"] == "/tmp/q/qualification-record.env"

    wrong = list(immutable)
    wrong[-1] = ("VERSION_NAME", "0.80.0-beta.2")
    expect_error(
        lambda: module.validate_resume_state(
            state_path=state,
            immutable=wrong,
        ),
        "RESUME_INPUT_MISMATCH_VERSION_NAME",
    )

with tempfile.TemporaryDirectory() as temp:
    root = Path(temp)
    first = module.next_attempt_dir(root, "qualification")
    assert first.name == "qualification-attempt-001"
    first.mkdir()
    second = module.next_attempt_dir(root, "qualification")
    assert second.name == "qualification-attempt-002"

with tempfile.TemporaryDirectory() as temp:
    root = Path(temp)
    failed_log = root / "signed-preview-attempt-001.log"
    failed_log.write_text("first failed signed attempt\n")
    second = module.next_attempt_dir(root, "signed-preview")
    assert second.name == "signed-preview-attempt-002"
    assert failed_log.read_text() == "first failed signed attempt\n"

with tempfile.TemporaryDirectory() as temp:
    root = Path(temp)
    failed_log = root / "publication-attempt-001.log"
    failed_log.write_text("first failed publication attempt\n")
    second = module.next_attempt_dir(root, "publication")
    assert second.name == "publication-attempt-002"
    assert failed_log.read_text() == "first failed publication attempt\n"

print("PASS=RELEASETOOL02D_STAGE_ORDER_INIT_TO_COMPLETE")
print("PASS=RELEASETOOL02D_EXACT_QUALIFICATION_COMMAND")
print("PASS=RELEASETOOL02D_EXACT_SIGNED_PREVIEW_COMMAND")
print("PASS=RELEASETOOL02D_EXACT_DEVICE_SMOKE_COMMAND")
print("PASS=RELEASETOOL02D_SAME_RELEASE_RECORD_TO_SMOKE_AND_PUBLICATION")
print("PASS=RELEASETOOL02D_IMMUTABLE_RESUME_INPUT_FENCE")
print("PASS=RELEASETOOL02D_NONDESTRUCTIVE_ATTEMPT_DIRECTORIES")
print("PASS=RELEASETOOL02D_FAILED_SIBLING_LOG_RESERVES_ATTEMPT_NUMBER")
print("RESULT=SUBSHELL_RELEASETOOL02D_SELFTEST_PASS")
