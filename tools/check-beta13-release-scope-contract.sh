#!/usr/bin/env bash
set -uo pipefail

# SUBSHELL_BETA13_RELEASE_SCOPE_CONTRACT_V1 (0.83-beta, versionCode 247)

FAIL=0

pass() {
    echo "PASS=$1"
}

fail() {
    echo "FAIL=$1"
    FAIL=1
}

CHANGELOG='app/src/main/assets/changelog/releases.json'
MANIFEST='tools/contract02/manifests/beta13.tsv'
MODEL='app/src/main/java/app/subshell/prototype/update/UpdateModels.kt'
SUITE='tools/check-beta13-nonnative-suite-contract.sh'
SOURCES='app/src/main/java'

for file in "$CHANGELOG" "$MANIFEST" "$MODEL" "$SUITE"; do
    [[ -f "$file" ]] ||
        fail "BETA13_MISSING_${file//[^A-Za-z0-9]/_}"
done

if [[ "$FAIL" -eq 0 ]]; then
    python3 - "$CHANGELOG" <<'PY'
import json
import sys
from pathlib import Path

p = Path(sys.argv[1])
data = json.loads(p.read_text())

assert data["schemaVersion"] == 1
releases = data["releases"]

# Historical once a later release exists: anchored on its own versionCode.
codes = [r["versionCode"] for r in releases]
top = codes.index(247)
assert codes[top:top + 5] == [
    247,
    246,
    245,
    244,
    243,
]

r = releases[top]

assert r == {
    "versionCode": 247,
    "versionName": "0.83-beta",
    "date": "2026-10-19",
    "headline": "Subshell 0.83: choose how posts look, pinned posts first, and pinch to zoom videos",
    "highlights": [
        "Choose how posts look: Large, Compact, List or Grid, from the view button beside the feed (and in Settings).",
        "A community's pinned posts now come first in every sort, as cards or as compact highlights.",
        "Pinch to zoom videos in the full-screen player (turn it on in Settings).",
        "A fresh look: Google's Material Symbols icons, Material 3 Expressive motion, and dynamic colour on by default.",
        "More reliable: one unreadable post no longer breaks a whole feed, removed images say so instead of retrying, and the inbox sync retries on a shaky network.",
    ],
    "releaseUrl": (
        "https://github.com/brealorg/subshell/releases/tag/"
        "v0.83-beta"
    ),
}

# RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1: from 0.79 a
# release may carry 12 highlights of 240 characters; the headline stays at
# 160 and the first six highlights at 180, because 0.78.0-beta.1 and older
# reject the whole release over a long headline and show only six.
for release in releases:
    if release["versionCode"] > 247:
        continue
    assert len(release["headline"].strip()) <= 160, release["versionName"]
    assert len(release["highlights"]) <= 12, release["versionName"]
    for index, item in enumerate(release["highlights"]):
        limit = 180 if index < 6 else 240
        assert item == item.strip() and 0 < len(item) <= limit, (release["versionName"], item)

previous = releases[top + 1]

assert previous["versionCode"] == 246
assert previous["versionName"] == "0.82-beta"
assert previous["date"] == "2026-10-16"
# UPDATE09: 0.83 carries no advisory.
assert "advisory" not in r
PY

    [[ $? -eq 0 ]] &&
        pass BETA13_EXACT_BUNDLED_CHANGELOG ||
        fail BETA13_EXACT_BUNDLED_CHANGELOG
fi

if grep -Fq \
    'https://brealorg.github.io/subshell-update-beta.json' \
    "$MODEL"
then
    pass BETA13_UPDATE_ENDPOINT_PRESERVED
else
    fail BETA13_UPDATE_ENDPOINT_PRESERVED
fi

EXPECTED_MANIFEST="$(
cat <<'LIST'
beta13	tools/check-beta13-release-scope-contract.sh	primary	-
beta13-nonnative-suite	tools/check-beta13-nonnative-suite-contract.sh	primary	-
releasetool02a	tools/check-releasetool02a-contract.sh	primary	-
releasetool02b	tools/check-releasetool02b-contract.sh	primary	-
releasetool02c	tools/check-releasetool02c-contract.sh	primary	-
releasetool02d	tools/check-releasetool02d-contract.sh	primary	-
LIST
)"

ACTUAL_MANIFEST="$(
    grep -Ev '^[[:space:]]*(#|$)' "$MANIFEST"
)"

if [[ "$ACTUAL_MANIFEST" == "$EXPECTED_MANIFEST" ]]; then
    pass BETA13_EXACT_NATIVE_RELEASE_MANIFEST
else
    fail BETA13_EXACT_NATIVE_RELEASE_MANIFEST
fi

while IFS=$'\t' read -r id path block predecessors; do
    [[ -n "$id" ]] || continue
    [[ "$id" == \#* ]] && continue

    if [[ -f "$path" ]]; then
        pass "BETA13_MANIFEST_PATH_${id}"
    else
        fail "BETA13_MANIFEST_PATH_${id}"
    fi

    if [[ "$block" != 'primary' ]]; then
        fail "BETA13_MANIFEST_MODE_${id}"
    fi
    # A manifest row must be Contract02-native; a checker without the primary
    # marker belongs in the non-native suite adapter (beta.4 and 0.78.0-beta.1).
    if [[ -f "$path" ]] && grep -Fq 'CONTRACT02_PRIMARY_RESULT=' "$path"; then
        pass "BETA13_MANIFEST_NATIVE_${id}"
    else
        fail "BETA13_MANIFEST_NATIVE_${id}"
    fi
done <"$MANIFEST"

# The release-defining features were landed with behaviour tests, not native
# checkers; the scope pins that each one is present by its source marker,
# anywhere under the sources (markers move with their code).
for marker in \
    ISSUE234_FEED_POST_LAYOUTS_V1 \
    ISSUE234_LAYOUT_CHOICE_BESIDE_THE_FEED_V1 \
    ISSUE230_PINNED_POSTS_FIRST_V1 \
    ISSUE231_PINCH_TO_ZOOM_VIDEOS_V1 \
    MATERIALSYMBOLS01_ICONS_ARE_MATERIAL_SYMBOLS_V1 \
    EXPRESSIVE01_SPRING_MOTION_V1 \
    SETTINGSREVIEW01_DYNAMIC_COLOUR_ON_BY_DEFAULT_V1 \
    LISTINGSKIP01_ONE_BAD_POST_DOES_NOT_FAIL_THE_PAGE_V1 \
    MEDIAGONE01_A_GONE_SOURCE_READS_REMOVED_V1 \
    BGSYNCDIAG01_TRANSIENT_NETWORK_FAILURE_RETRIES_V1 \
    DIAG04_ACTION_TOKEN_V1 \
    DIAG05_PLAIN_NOTICES_ARE_RECORDED_V1 \
    CRASH01_CRASHES_ARE_RECORDED_LOCALLY_V1 \
    UPDATE09_KNOWN_BAD_VERSION_ADVISORY_V1 \
    RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1
do
    if grep -RFq "$marker" "$SOURCES"; then
        :
    else
        fail "BETA13_RELEASE_FEATURE_MISSING_${marker}"
    fi
done
# R8 and the baseline profile live in the build, not under the sources;
# Stage C (the public source snapshot) needs the secret-scan configuration.
grep -Fq 'R8_RELEASE_SHRINKS_WITHOUT_RENAMING_V1' app/build.gradle.kts &&
grep -Fq -- '-dontobfuscate' app/proguard-rules.pro &&
grep -Fq 'PERF01_BASELINE_PROFILE_V1' app/build.gradle.kts &&
[[ -s app/src/main/baseline-prof.txt ]] &&
grep -Fq 'RELEASETOOL02E_SECRET_SCAN_CONFIG_V1' .gitleaks.toml &&
[[ -f LICENSE ]] ||
    fail BETA13_BUILD_AND_PUBLICATION_FEATURES_MISSING
[[ "$FAIL" -eq 0 ]] && pass BETA13_RELEASE_FEATURES_INCLUDED

if grep -Fq $'beta13-nonnative-suite\ttools/check-beta13-nonnative-suite-contract.sh\tprimary\t-' "$MANIFEST"; then
    pass BETA13_NONNATIVE_CONTRACT02_ADAPTER
else
    fail BETA13_NONNATIVE_CONTRACT02_ADAPTER
fi

for required in \
  tools/check-source-contract.sh
do
    if grep -Fq "$required" "$SUITE"; then
        :
    else
        fail "BETA13_ADAPTER_MISSING_${required//[^A-Za-z0-9]/_}"
    fi
done

if [[ "$FAIL" -eq 0 ]]; then
    pass BETA13_NONNATIVE_SUITE_EXACT_SCOPE
fi

if grep -Fq 'fun topReleaseSurvivesUpdateManifestNormalizationUnchanged' \
    app/src/test/java/app/subshell/prototype/update/BundledChangelogPromotionPolicyTest.kt &&
   grep -Fq 'fun topReleaseReadsTheSameForAnOlderClient' \
    app/src/test/java/app/subshell/prototype/update/BundledChangelogPromotionPolicyTest.kt
then
    pass BETA13_CHANGELOG_PROMOTION_POLICY_TESTS_PRESENT
else
    fail BETA13_CHANGELOG_PROMOTION_POLICY_TESTS_PRESENT
fi

if grep -Fq 'tools/check-releasetool02a-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02b-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02c-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02d-contract.sh' "$MANIFEST"
then
    pass BETA13_RELEASETOOL02_PIPELINE_QUALIFIED
else
    fail BETA13_RELEASETOOL02_PIPELINE_QUALIFIED
fi

if ! grep -Fq 'tools/check-releasetool01c-contract.sh' "$MANIFEST"; then
    pass BETA13_LEGACY_PUBLICATION_PATH_NOT_RELEASE_GATE
else
    fail BETA13_LEGACY_PUBLICATION_PATH_NOT_RELEASE_GATE
fi

if [[ "$FAIL" -eq 0 ]]; then
    echo 'CONTRACT02_PRIMARY_RESULT=PASS'
    echo 'RESULT=SUBSHELL_BETA13_RELEASE_SCOPE_CONTRACT_PASS'
    exit 0
fi

echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
echo 'RESULT=SUBSHELL_BETA13_RELEASE_SCOPE_CONTRACT_FAIL'
exit 1
