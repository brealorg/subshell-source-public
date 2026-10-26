#!/usr/bin/env bash
set -uo pipefail

# SUBSHELL_BETA15_RELEASE_SCOPE_CONTRACT_V1 (0.85-beta, versionCode 249)

FAIL=0

pass() {
    echo "PASS=$1"
}

fail() {
    echo "FAIL=$1"
    FAIL=1
}

CHANGELOG='app/src/main/assets/changelog/releases.json'
MANIFEST='tools/contract02/manifests/beta15.tsv'
MODEL='app/src/main/java/app/subshell/prototype/update/UpdateModels.kt'
SUITE='tools/check-beta15-nonnative-suite-contract.sh'
SOURCES='app/src/main/java'

for file in "$CHANGELOG" "$MANIFEST" "$MODEL" "$SUITE"; do
    [[ -f "$file" ]] ||
        fail "BETA15_MISSING_${file//[^A-Za-z0-9]/_}"
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
top = codes.index(249)
assert codes[top:top + 5] == [
    249,
    248,
    247,
    246,
    245,
]

r = releases[top]

assert r == {
    "versionCode": 249,
    "versionName": "0.85-beta",
    "date": "2026-10-23",
    "headline": "Subshell 0.85: a security update for sign-in, notifications and media",
    "highlights": [
        "Signing out of an account now also removes Reddit's web login, unless another signed-in account still uses it.",
        "The sign-in window opens only Reddit, Google and Apple pages, and a sign-in that fails is also withdrawn at Reddit.",
        "Other apps can no longer open accounts or screens in subshell. Notifications from before the update just open the app.",
        "With the app lock on, the recent-apps preview stays blank on Android 12 and older, and new-post notifications from hidden communities don't name them.",
        "Post images and videos load only from public addresses, images and GIFs share one memory limit, and nothing is written to the system log.",
    ],
    "releaseUrl": (
        "https://github.com/brealorg/subshell/releases/tag/"
        "v0.85-beta"
    ),
}

# RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1: from 0.79 a
# release may carry 12 highlights of 240 characters; the headline stays at
# 160 and the first six highlights at 180, because 0.78.0-beta.1 and older
# reject the whole release over a long headline and show only six.
for release in releases:
    if release["versionCode"] > 249:
        continue
    assert len(release["headline"].strip()) <= 160, release["versionName"]
    assert len(release["highlights"]) <= 12, release["versionName"]
    for index, item in enumerate(release["highlights"]):
        limit = 180 if index < 6 else 240
        assert item == item.strip() and 0 < len(item) <= limit, (release["versionName"], item)

previous = releases[top + 1]

assert previous["versionCode"] == 248
assert previous["versionName"] == "0.84-beta"
assert previous["date"] == "2026-10-21"
# UPDATE09: 0.85 carries no advisory.
assert "advisory" not in r
PY

    [[ $? -eq 0 ]] &&
        pass BETA15_EXACT_BUNDLED_CHANGELOG ||
        fail BETA15_EXACT_BUNDLED_CHANGELOG
fi

if grep -Fq \
    'https://brealorg.github.io/subshell-update-beta.json' \
    "$MODEL"
then
    pass BETA15_UPDATE_ENDPOINT_PRESERVED
else
    fail BETA15_UPDATE_ENDPOINT_PRESERVED
fi

EXPECTED_MANIFEST="$(
cat <<'LIST'
beta15	tools/check-beta15-release-scope-contract.sh	primary	-
beta15-nonnative-suite	tools/check-beta15-nonnative-suite-contract.sh	primary	-
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
    pass BETA15_EXACT_NATIVE_RELEASE_MANIFEST
else
    fail BETA15_EXACT_NATIVE_RELEASE_MANIFEST
fi

while IFS=$'\t' read -r id path block predecessors; do
    [[ -n "$id" ]] || continue
    [[ "$id" == \#* ]] && continue

    if [[ -f "$path" ]]; then
        pass "BETA15_MANIFEST_PATH_${id}"
    else
        fail "BETA15_MANIFEST_PATH_${id}"
    fi

    if [[ "$block" != 'primary' ]]; then
        fail "BETA15_MANIFEST_MODE_${id}"
    fi
    # A manifest row must be Contract02-native; a checker without the primary
    # marker belongs in the non-native suite adapter (beta.4 and 0.78.0-beta.1).
    if [[ -f "$path" ]] && grep -Fq 'CONTRACT02_PRIMARY_RESULT=' "$path"; then
        pass "BETA15_MANIFEST_NATIVE_${id}"
    else
        fail "BETA15_MANIFEST_NATIVE_${id}"
    fi
done <"$MANIFEST"

# The release-defining features were landed with behaviour tests, not native
# checkers; the scope pins that each one is present by its source marker,
# anywhere under the sources (markers move with their code).
for marker in \
    SECAUDIT01_MANIFEST_FROM_THE_FUTURE_V1 \
    SECAUDIT02_WEB_SESSION_GOES_WITH_ITS_ACCOUNT_V1 \
    SECAUDIT05_ADVISORY_TEXT_NAMES_NO_LINK_V1 \
    SECAUDIT06_NOTIFICATION_TAPS_CARRY_THE_LAUNCH_KEY_V1 \
    SECAUDIT07_CALLBACK_RESULT_CARRIES_THE_PROCESS_TOKEN_V1 \
    SECAUDIT08_CALLBACK_OPENS_SUBSHELLS_OWN_TASK_V1 \
    SECAUDIT10_RECENTS_CARD_HIDDEN_BELOW_ANDROID_13_V1 \
    SECAUDIT11_LOGIN_WINDOW_LOADS_ONLY_REDDIT_AND_ITS_PROVIDERS_V1 \
    SECAUDIT12_CHAT_BRIDGE_ANSWERS_ONLY_ITS_OWN_SCRIPT_V1 \
    SECAUDIT13_MEDIA_BYTES_SHARE_ONE_MEMORY_BUDGET_V1 \
    SECAUDIT14_POST_MEDIA_ONLY_FROM_PUBLIC_HOSTS_V1 \
    SECAUDIT23_SHARE_TAKES_ONLY_OTHER_APPS_FILES_V1 \
    SECAUDIT28_AUTH_FILES_ARE_BOUND_TO_THEIR_PLACE_V1 \
    SECAUDIT29_HIDDEN_COMMUNITIES_ARE_NOTIFIED_WITHOUT_THEIR_NAME_V1 \
    SECAUDIT31_FAILED_REGISTRATION_REVOKES_ITS_GRANT_V1 \
    ISSUE239_NEW_POSTS_IN_A_WATCHED_COMMUNITY_V1 \
    ISSUE235_LONG_PRESS_SELECTS_TEXT_V1 \
    ISSUE237_EVERY_SCOPE_AT_SIGN_IN_V1 \
    ISSUE240_READER_EDITS_THE_REDDIT_API_VALUES_V1 \
    CRASH01_CRASHES_ARE_RECORDED_LOCALLY_V1 \
    UPDATE09_KNOWN_BAD_VERSION_ADVISORY_V1 \
    RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1
do
    if grep -RFq "$marker" "$SOURCES"; then
        :
    else
        fail "BETA15_RELEASE_FEATURE_MISSING_${marker}"
    fi
done
# R8 and the baseline profile live in the build, not under the sources;
# Stage C (the public source snapshot) needs the secret-scan configuration;
# SECAUDIT15/25 live in the build and SECAUDIT04's dependency verification in gradle/.
grep -Fq 'R8_RELEASE_SHRINKS_WITHOUT_RENAMING_V1' app/build.gradle.kts &&
grep -Fq -- '-dontobfuscate' app/proguard-rules.pro &&
grep -Fq 'PERF01_BASELINE_PROFILE_V1' app/build.gradle.kts &&
[[ -s app/src/main/baseline-prof.txt ]] &&
grep -Fq 'RELEASETOOL02E_SECRET_SCAN_CONFIG_V1' .gitleaks.toml &&
[[ -f LICENSE ]] &&
grep -Fq 'SECAUDIT15_RELEASE_BUILDS_WRITE_NO_LOGCAT_V1' app/proguard-rules.pro &&
grep -Fq 'SECAUDIT25_SIGNING_INPUTS_NEVER_PRINT_THEIR_PASSWORDS_V1' app/build.gradle.kts &&
grep -Fq '<verify-metadata>true</verify-metadata>' gradle/verification-metadata.xml &&
grep -Fq '<verify-signatures>true</verify-signatures>' gradle/verification-metadata.xml &&
[[ -s gradle/verification-keyring.keys ]] ||
    fail BETA15_BUILD_AND_PUBLICATION_FEATURES_MISSING
[[ "$FAIL" -eq 0 ]] && pass BETA15_RELEASE_FEATURES_INCLUDED

if grep -Fq $'beta15-nonnative-suite\ttools/check-beta15-nonnative-suite-contract.sh\tprimary\t-' "$MANIFEST"; then
    pass BETA15_NONNATIVE_CONTRACT02_ADAPTER
else
    fail BETA15_NONNATIVE_CONTRACT02_ADAPTER
fi

for required in \
  tools/check-source-contract.sh
do
    if grep -Fq "$required" "$SUITE"; then
        :
    else
        fail "BETA15_ADAPTER_MISSING_${required//[^A-Za-z0-9]/_}"
    fi
done

if [[ "$FAIL" -eq 0 ]]; then
    pass BETA15_NONNATIVE_SUITE_EXACT_SCOPE
fi

if grep -Fq 'fun topReleaseSurvivesUpdateManifestNormalizationUnchanged' \
    app/src/test/java/app/subshell/prototype/update/BundledChangelogPromotionPolicyTest.kt &&
   grep -Fq 'fun topReleaseReadsTheSameForAnOlderClient' \
    app/src/test/java/app/subshell/prototype/update/BundledChangelogPromotionPolicyTest.kt
then
    pass BETA15_CHANGELOG_PROMOTION_POLICY_TESTS_PRESENT
else
    fail BETA15_CHANGELOG_PROMOTION_POLICY_TESTS_PRESENT
fi

if grep -Fq 'tools/check-releasetool02a-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02b-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02c-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02d-contract.sh' "$MANIFEST"
then
    pass BETA15_RELEASETOOL02_PIPELINE_QUALIFIED
else
    fail BETA15_RELEASETOOL02_PIPELINE_QUALIFIED
fi

if ! grep -Fq 'tools/check-releasetool01c-contract.sh' "$MANIFEST"; then
    pass BETA15_LEGACY_PUBLICATION_PATH_NOT_RELEASE_GATE
else
    fail BETA15_LEGACY_PUBLICATION_PATH_NOT_RELEASE_GATE
fi

if [[ "$FAIL" -eq 0 ]]; then
    echo 'CONTRACT02_PRIMARY_RESULT=PASS'
    echo 'RESULT=SUBSHELL_BETA15_RELEASE_SCOPE_CONTRACT_PASS'
    exit 0
fi

echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
echo 'RESULT=SUBSHELL_BETA15_RELEASE_SCOPE_CONTRACT_FAIL'
exit 1
