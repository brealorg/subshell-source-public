#!/usr/bin/env bash
set -uo pipefail

# RELEASE48_ONE_RELEASE_SCOPE_CHECKER_V1 (#48): one release-scope contract for
# every release from 0.92 on, driven by tools/release-scope/<id>.json; it
# replaces the per-beta copies (check-beta09..21-release-scope-contract.sh),
# which stay as they are for the releases they qualified.
#   tools/release-scope/release-scope-contract.sh tools/release-scope/<id>.json
# A release's tools/check-<id>-release-scope-contract.sh only calls this.

FAIL=0
pass() { echo "PASS=$1"; }
fail() { echo "FAIL=$1"; FAIL=1; }

SPEC="${1:-}"
[[ -n "$SPEC" && -f "$SPEC" ]] || { echo "FAIL=RELEASE_SCOPE_SPEC_MISSING"; echo 'CONTRACT02_PRIMARY_RESULT=FAIL'; exit 1; }

CHANGELOG="${SUBSHELL_RELEASE_SCOPE_CHANGELOG:-app/src/main/assets/changelog/releases.json}"
MODEL='app/src/main/java/io/github/brealorg/subshell/update/UpdateModels.kt'
FEATURES='tools/release-scope/release-features.txt'
SOURCES="${SUBSHELL_RELEASE_SCOPE_SOURCES:-app/src/main/java}"

ID="$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "$SPEC")" ||
    { echo "FAIL=RELEASE_SCOPE_SPEC_UNREADABLE"; echo 'CONTRACT02_PRIMARY_RESULT=FAIL'; exit 1; }
[[ "$ID" =~ ^[a-z0-9-]+$ ]] || fail RELEASE_SCOPE_ID_INVALID
TAG="RELEASE_SCOPE_${ID//-/_}"
spec_field() { python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1])).get(sys.argv[2]) or sys.argv[3])' "$SPEC" "$1" "$2"; }
# Both default to the release's own files; the selftest points them elsewhere.
MANIFEST="$(spec_field manifest "tools/contract02/manifests/${ID}.tsv")"
WRAPPER="$(spec_field wrapper "tools/check-${ID}-release-scope-contract.sh")"
SUITE='tools/check-release-nonnative-suite-contract.sh'

for file in "$CHANGELOG" "$MANIFEST" "$MODEL" "$SUITE" "$FEATURES"; do
    [[ -f "$file" ]] || fail "${TAG}_MISSING_${file//[^A-Za-z0-9]/_}"
done

if [[ "$FAIL" -eq 0 ]]; then
    # The bundled changelog entry is exactly the approved one, the releases
    # below it are the ones named, and every release stays readable for
    # older clients (RELNOTES01: headline <=160, first six highlights <=180).
    python3 -I - "$CHANGELOG" "$SPEC" <<'PY'
import json
import sys
from pathlib import Path

data = json.loads(Path(sys.argv[1]).read_text())
spec = json.loads(Path(sys.argv[2]).read_text())
assert data["schemaVersion"] == 1
releases = data["releases"]
entry = spec["release"]
code = entry["versionCode"]
codes = [r["versionCode"] for r in releases]
top = codes.index(code)
assert codes[top:top + len(spec["below"]) + 1] == [code] + [b["versionCode"] for b in spec["below"]]
assert releases[top] == entry, "bundled entry differs from the approved one"
assert entry["releaseUrl"] == "https://github.com/brealorg/subshell/releases/tag/v" + entry["versionName"]
for expected, actual in zip(spec["below"], releases[top + 1:]):
    for key, value in expected.items():
        assert actual[key] == value, (key, actual[key], value)
for release in releases:
    if release["versionCode"] > code:
        continue
    assert len(release["headline"].strip()) <= 160, release["versionName"]
    assert len(release["highlights"]) <= 12, release["versionName"]
    for index, item in enumerate(release["highlights"]):
        limit = 180 if index < 6 else 240
        assert item == item.strip() and 0 < len(item) <= limit, (release["versionName"], item)
if not spec.get("advisory", False):
    assert "advisory" not in entry
PY
    [[ $? -eq 0 ]] && pass "${TAG}_EXACT_BUNDLED_CHANGELOG" || fail "${TAG}_EXACT_BUNDLED_CHANGELOG"
fi

# Every installed 0.9x polls this feed: its name and channel are permanent.
grep -Fq 'https://brealorg.github.io/subshell-update-beta.json' "$MODEL" &&
    pass "${TAG}_UPDATE_ENDPOINT_PRESERVED" || fail "${TAG}_UPDATE_ENDPOINT_PRESERVED"

EXPECTED_MANIFEST="$(printf '%s\t%s\t%s\t%s\n' \
    "$ID" "$WRAPPER" primary - \
    release-nonnative-suite "$SUITE" primary - \
    releasetool02a tools/check-releasetool02a-contract.sh primary - \
    releasetool02b tools/check-releasetool02b-contract.sh primary - \
    releasetool02c tools/check-releasetool02c-contract.sh primary - \
    releasetool02d tools/check-releasetool02d-contract.sh primary -)"
ACTUAL_MANIFEST="$(grep -Ev '^[[:space:]]*(#|$)' "$MANIFEST" 2>/dev/null)"
[[ "$ACTUAL_MANIFEST" == "$EXPECTED_MANIFEST" ]] &&
    pass "${TAG}_EXACT_NATIVE_RELEASE_MANIFEST" || fail "${TAG}_EXACT_NATIVE_RELEASE_MANIFEST"

while IFS=$'\t' read -r id path block _; do
    [[ -n "$id" && "$id" != \#* ]] || continue
    [[ -f "$path" ]] || fail "${TAG}_MANIFEST_PATH_${id}"
    [[ "$block" == primary ]] || fail "${TAG}_MANIFEST_MODE_${id}"
done <"$MANIFEST"

while IFS= read -r marker; do
    [[ -z "$marker" || "$marker" == \#* ]] && continue
    grep -RFq "$marker" "$SOURCES" || fail "${TAG}_RELEASE_FEATURE_MISSING_${marker}"
done <"$FEATURES"

# R8, the baseline profile, the secret scan and dependency verification live
# outside the sources.
grep -Fq 'R8_RELEASE_SHRINKS_WITHOUT_RENAMING_V1' app/build.gradle.kts &&
grep -Fq -- '-dontobfuscate' app/proguard-rules.pro &&
grep -Fq 'PERF01_BASELINE_PROFILE_V1' app/build.gradle.kts &&
grep -Fq 'A11Y01_WAITS_FOR_THE_EMULATOR_TO_SETTLE_V1' tools/a11y-emulator-check.sh &&
grep -Fq 'ISSUE259_BASELINE_PROFILE_IS_A_RELEASE_STEP_V1' tools/perf/record-baseline-profile.sh &&
[[ -s app/src/main/baseline-prof.txt ]] &&
grep -Fq 'RELEASETOOL02E_SECRET_SCAN_CONFIG_V1' .gitleaks.toml &&
[[ -f LICENSE ]] &&
grep -Fq 'SECAUDIT15_RELEASE_BUILDS_WRITE_NO_LOGCAT_V1' app/proguard-rules.pro &&
grep -Fq 'SECAUDIT25_SIGNING_INPUTS_NEVER_PRINT_THEIR_PASSWORDS_V1' app/build.gradle.kts &&
grep -Fq '<verify-metadata>true</verify-metadata>' gradle/verification-metadata.xml &&
grep -Fq '<verify-signatures>true</verify-signatures>' gradle/verification-metadata.xml &&
[[ -s gradle/verification-keyring.keys ]] ||
    fail "${TAG}_BUILD_AND_PUBLICATION_FEATURES_MISSING"

grep -Fq 'fun topReleaseSurvivesUpdateManifestNormalizationUnchanged' \
    app/src/test/java/io/github/brealorg/subshell/update/BundledChangelogPromotionPolicyTest.kt &&
grep -Fq 'fun topReleaseReadsTheSameForAnOlderClient' \
    app/src/test/java/io/github/brealorg/subshell/update/BundledChangelogPromotionPolicyTest.kt ||
    fail "${TAG}_CHANGELOG_PROMOTION_POLICY_TESTS_PRESENT"

grep -Fq 'tools/check-releasetool01c-contract.sh' "$MANIFEST" 2>/dev/null &&
    fail "${TAG}_LEGACY_PUBLICATION_PATH_IS_RELEASE_GATE"

if [[ "$FAIL" -eq 0 ]]; then
    echo 'CONTRACT02_PRIMARY_RESULT=PASS'
    echo "RESULT=SUBSHELL_${TAG}_CONTRACT_PASS"
    exit 0
fi
echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
echo "RESULT=SUBSHELL_${TAG}_CONTRACT_FAIL"
exit 1
