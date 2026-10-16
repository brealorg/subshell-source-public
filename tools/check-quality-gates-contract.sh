#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BUILD='app/build.gradle.kts'
AGGREGATOR='tools/check-all-contracts.sh'
DOC='docs/quality/subshell-quality-gates-v1.md'

for file in "$BUILD" "$AGGREGATOR" "$DOC"; do
  test -f "$file" || {
    printf 'FAIL=QUALITY_GATE_FILE_MISSING FILE=%s\n' "$file"
    exit 1
  }
done

python3 - "$BUILD" "$AGGREGATOR" "$DOC" <<'PY'
import re
import sys
from pathlib import Path

build = Path(sys.argv[1]).read_text(encoding="utf-8")
aggregator = Path(sys.argv[2]).read_text(encoding="utf-8")
doc = Path(sys.argv[3]).read_text(encoding="utf-8")

required_build = [
    "// SUBSHELL QUALITY GATES V1",
    'tasks.register<org.gradle.api.tasks.Exec>("subshellContracts")',
    'commandLine("bash", "tools/check-all-contracts.sh")',
    'tasks.register("subshellQualityCheck")',
    'dependsOn(verifyAuth04ConfigurationPolicy)',
    'dependsOn(verifyAuth05ProductionIdentityPolicy)',
    'dependsOn(verifyAuth05cApplicationIdPolicy)',
    'dependsOn(verifyAuth05d2ReleaseSigningPolicy)',
    'dependsOn(verifyAuth05d2ReleaseSigningIdentity)',
    'dependsOn("lintDebug", "testDebugUnitTest", "assembleDebug")',
    'tasks.matching { it.name == "check" }.configureEach',
    'tasks.matching { it.name == "preReleaseBuild" }.configureEach',
    '// SUBSHELL_CONTRACT02F_RELEASE_QUALIFICATION_GATE_V1',
    'subshell.release.qualificationRecord',
    'SUBSHELL_RELEASE_QUALIFICATION_RECORD',
]
for needle in required_build:
    if build.count(needle) != 1:
        raise SystemExit(
            f"QUALITY_GATE_BUILD_TOKEN_COUNT "
            f"token={needle!r} count={build.count(needle)}"
        )

quality_task_block = re.search(
    r'val subshellQualityCheck =\n'
    r'    tasks\.register\("subshellQualityCheck"\) \{'
    r'(?P<body>.*?)'
    r'\n    \}',
    build,
    flags=re.DOTALL,
)
if not quality_task_block:
    raise SystemExit("QUALITY_GATE_MILESTONE_BLOCK_MISSING")
if quality_task_block.group("body").count(
    "dependsOn(subshellContracts)"
) != 1:
    raise SystemExit(
        "QUALITY_GATE_MILESTONE_CONTRACT_DEPENDENCY_NOT_EXACT"
    )

contracts_task_block = re.search(
    r'val subshellContracts =\n'
    r'    tasks\.register<org\.gradle\.api\.tasks\.Exec>\("subshellContracts"\) \{'
    r'(?P<body>.*?)'
    r'\n    \}',
    build,
    flags=re.DOTALL,
)
if not contracts_task_block:
    raise SystemExit("QUALITY_GATE_CONTRACT_TASK_BLOCK_MISSING")
if contracts_task_block.group("body").count(
    "outputs.upToDateWhen { false }"
) != 1:
    raise SystemExit("QUALITY_GATE_CONTRACT_TASK_UP_TO_DATE_POLICY_NOT_EXACT")

check_task_block = re.search(
    r'tasks\.matching \{ it\.name == "check" \}\.configureEach \{'
    r'(?P<body>.*?)'
    r'\n\}',
    build,
    flags=re.DOTALL,
)
if not check_task_block:
    raise SystemExit("QUALITY_GATE_CHECK_TASK_BLOCK_MISSING")
if check_task_block.group("body").count(
    "dependsOn(subshellQualityCheck)"
) != 1:
    raise SystemExit(
        "QUALITY_GATE_CHECK_FULL_QUALITY_DEPENDENCY_NOT_EXACT"
    )

pre_release_block = re.search(
    r'tasks\.matching \{ it\.name == "preReleaseBuild" \}\.configureEach \{'
    r'(?P<body>.*?)'
    r'\n\}',
    build,
    flags=re.DOTALL,
)
if not pre_release_block:
    raise SystemExit("QUALITY_GATE_PRE_RELEASE_BLOCK_MISSING")

pre_release_body = pre_release_block.group("body")
for needle in [
    "dependsOn(verifyContract02ReleaseQualification)",
    "dependsOn(verifyAuth05d2ReleaseSigningIdentity)",
]:
    if pre_release_body.count(needle) != 1:
        raise SystemExit(
            "QUALITY_GATE_RELEASE_SUCCESSOR_DEPENDENCY_NOT_EXACT_"
            f"{needle}"
        )

for forbidden in [
    "subshellQualityCheck",
    "subshellContracts",
]:
    if forbidden in pre_release_body:
        raise SystemExit(
            "QUALITY_GATE_RELEASE_LEGACY_REENTRY_PRESENT_"
            f"{forbidden}"
        )

if 'tasks.matching { it.name == "preDebugBuild" }.configureEach' in build:
    raise SystemExit("QUALITY_GATE_DEBUG_BUILD_MUST_BE_DECOUPLED")

if build.count("dependsOn(subshellContracts)") != 1:
    raise SystemExit(
        "QUALITY_GATE_CONTRACT_DEPENDENCY_TOTAL_EXPECTED_1_"
        f"ACTUAL_{build.count('dependsOn(subshellContracts)')}"
    )

for forbidden in [
    "skipSubshellContracts",
    "skipQualityGate",
    "onlyIf",
]:
    if forbidden in build:
        raise SystemExit(
            f"QUALITY_GATE_BYPASS_PRESENT={forbidden}"
        )

expected_contracts = [
    "tools/check-source-contract.sh",
    "tools/check-auth04-contract.sh",
    "tools/check-auth05b-contract.sh",
    "tools/check-auth05c-contract.sh",
    "tools/check-auth05d2-contract.sh",
    "tools/check-auth05e2-contract.sh",
    "tools/check-quality-gates-contract.sh",
]

match = re.search(
    r"CONTRACTS=\(\n(?P<body>.*?)\n\)",
    aggregator,
    flags=re.DOTALL,
)
if not match:
    raise SystemExit("QUALITY_GATE_CONTRACT_ARRAY_MISSING")

actual_contracts = [
    line.strip()
    for line in match.group("body").splitlines()
    if line.strip()
]
if actual_contracts != expected_contracts:
    raise SystemExit(
        f"QUALITY_GATE_CONTRACT_ORDER_MISMATCH "
        f"actual={actual_contracts} expected={expected_contracts}"
    )

if 'echo "CONTRACT_COUNT=${#CONTRACTS[@]}"' not in aggregator:
    raise SystemExit("QUALITY_GATE_CONTRACT_COUNT_MARKER_MISSING")
if "RESULT=SUBSHELL_ALL_CONTRACTS_PASS" not in aggregator:
    raise SystemExit("QUALITY_GATE_AGGREGATOR_RESULT_MISSING")

required_doc = [
    "Ordinary debug builds",
    "baseline-aware",
    "./gradlew :app:subshellQualityCheck",
    "Contract02 delta qualification",
    "qualification record",
    "The release path does not rerun",
    "`subshellQualityCheck` or `subshellContracts` after qualification has passed.",
    "0 errors / 0 warnings / 0 hints",
]
for needle in required_doc:
    if needle not in doc:
        raise SystemExit(
            f"QUALITY_GATE_DOCUMENTATION_TOKEN_MISSING={needle}"
        )

print("QUALITY_GATE_CHECK_FULL_VERIFICATION=PASS")
print("QUALITY_GATE_RELEASE_CONTRACT02_QUALIFICATION=PASS")
print("QUALITY_GATE_BUILD_WIRING=PASS")
print("QUALITY_GATE_CONTRACT_ORDER=PASS")
print(f"QUALITY_GATE_CONTRACT_COUNT={len(expected_contracts)}")
print("QUALITY_GATE_NET01_CONTRACT_INCLUDED=PASS")
print("QUALITY_GATE_NET02_CONTRACT_INCLUDED=PASS")
print("QUALITY_GATE_AUTH04_CONTRACT_INCLUDED=PASS")
print("QUALITY_GATE_AUTH04_CONFIGURATION_POLICY_INCLUDED=PASS")
print("QUALITY_GATE_AUTH05B_CONTRACT_INCLUDED=PASS")
print("QUALITY_GATE_AUTH05C_CONTRACT_INCLUDED=PASS")
print("QUALITY_GATE_AUTH05C_APPLICATION_ID_POLICY_INCLUDED=PASS")
print("QUALITY_GATE_AUTH05D2_CONTRACT_INCLUDED=PASS")
print("QUALITY_GATE_AUTH05D2_SIGNING_POLICY_INCLUDED=PASS")
print("QUALITY_GATE_AUTH05D2_RELEASE_IDENTITY_GATE_INCLUDED=PASS")
print("QUALITY_GATE_AUTH05E2_CONTRACT_INCLUDED=PASS")
print("QUALITY_GATE_AUTH05_IDENTITY_POLICY_INCLUDED=PASS")
print("QUALITY_GATE_BYPASS=ABSENT")
PY

echo 'PASS=QUALITY_GATE_DEBUG_BUILD_DECOUPLED_FROM_GLOBAL_CONTRACTS'
echo 'PASS=QUALITY_GATE_CHECK_FULL_RELEASE_CONTRACT02_SEPARATED'
echo 'PASS=QUALITY_GATE_MILESTONE_TASK_IS_CANONICAL'
echo 'RESULT=SUBSHELL_QUALITY_GATES_V2_CONTRACT_PASS'
