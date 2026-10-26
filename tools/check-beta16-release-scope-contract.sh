#!/usr/bin/env bash
set -uo pipefail

# SUBSHELL_BETA16_RELEASE_SCOPE_CONTRACT_V1 (0.86-beta, versionCode 250)

FAIL=0

pass() {
    echo "PASS=$1"
}

fail() {
    echo "FAIL=$1"
    FAIL=1
}

CHANGELOG='app/src/main/assets/changelog/releases.json'
MANIFEST='tools/contract02/manifests/beta16.tsv'
MODEL='app/src/main/java/app/subshell/prototype/update/UpdateModels.kt'
SUITE='tools/check-beta16-nonnative-suite-contract.sh'
SOURCES='app/src/main/java'

for file in "$CHANGELOG" "$MANIFEST" "$MODEL" "$SUITE"; do
    [[ -f "$file" ]] ||
        fail "BETA16_MISSING_${file//[^A-Za-z0-9]/_}"
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
top = codes.index(250)
assert codes[top:top + 5] == [
    250,
    249,
    248,
    247,
    246,
]

r = releases[top]

assert r == {
    "versionCode": 250,
    "versionName": "0.86-beta",
    "date": "2026-10-26",
    "headline": "Subshell 0.86: reply from notifications, RedGIFs with sound and a faster inbox",
    "highlights": [
        "Private messages and chats arrive as conversation notifications, and you can reply right from the notification.",
        "RedGIFs clips play with sound, videos pause the moment you switch tabs, and headset or Bluetooth buttons play and pause them.",
        "The inbox refreshes several times faster, and messages you read elsewhere no longer count as unread.",
        "You can post to your own profile, and galleries posted from the app show their images again.",
        "Subshell opens straight to your feeds, and switching dark mode or text size keeps your place, drafts and votes.",
    ],
    "releaseUrl": (
        "https://github.com/brealorg/subshell/releases/tag/"
        "v0.86-beta"
    ),
}

# RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1: from 0.79 a
# release may carry 12 highlights of 240 characters; the headline stays at
# 160 and the first six highlights at 180, because 0.78.0-beta.1 and older
# reject the whole release over a long headline and show only six.
for release in releases:
    if release["versionCode"] > 250:
        continue
    assert len(release["headline"].strip()) <= 160, release["versionName"]
    assert len(release["highlights"]) <= 12, release["versionName"]
    for index, item in enumerate(release["highlights"]):
        limit = 180 if index < 6 else 240
        assert item == item.strip() and 0 < len(item) <= limit, (release["versionName"], item)

previous = releases[top + 1]

assert previous["versionCode"] == 249
assert previous["versionName"] == "0.85-beta"
assert previous["date"] == "2026-10-23"
# UPDATE09: 0.86 carries no advisory.
assert "advisory" not in r
PY

    [[ $? -eq 0 ]] &&
        pass BETA16_EXACT_BUNDLED_CHANGELOG ||
        fail BETA16_EXACT_BUNDLED_CHANGELOG
fi

if grep -Fq \
    'https://brealorg.github.io/subshell-update-beta.json' \
    "$MODEL"
then
    pass BETA16_UPDATE_ENDPOINT_PRESERVED
else
    fail BETA16_UPDATE_ENDPOINT_PRESERVED
fi

EXPECTED_MANIFEST="$(
cat <<'LIST'
beta16	tools/check-beta16-release-scope-contract.sh	primary	-
beta16-nonnative-suite	tools/check-beta16-nonnative-suite-contract.sh	primary	-
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
    pass BETA16_EXACT_NATIVE_RELEASE_MANIFEST
else
    fail BETA16_EXACT_NATIVE_RELEASE_MANIFEST
fi

while IFS=$'\t' read -r id path block predecessors; do
    [[ -n "$id" ]] || continue
    [[ "$id" == \#* ]] && continue

    if [[ -f "$path" ]]; then
        pass "BETA16_MANIFEST_PATH_${id}"
    else
        fail "BETA16_MANIFEST_PATH_${id}"
    fi

    if [[ "$block" != 'primary' ]]; then
        fail "BETA16_MANIFEST_MODE_${id}"
    fi
    # A manifest row must be Contract02-native; a checker without the primary
    # marker belongs in the non-native suite adapter (beta.4 and 0.78.0-beta.1).
    if [[ -f "$path" ]] && grep -Fq 'CONTRACT02_PRIMARY_RESULT=' "$path"; then
        pass "BETA16_MANIFEST_NATIVE_${id}"
    else
        fail "BETA16_MANIFEST_NATIVE_${id}"
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
    RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1 \
    ISSUE253_MESSAGES_ARE_CONVERSATION_NOTIFICATIONS_V1 \
    ISSUE253_DIRECT_REPLY_FROM_THE_NOTIFICATION_V1 \
    ISSUE256_REDGIFS_ORIGINAL_PLAYS_FIRST_V1 \
    ISSUE251_MEDIA_BUTTONS_CONTROL_THE_VIDEO_V1 \
    INBOXFAST01_INBOX_AND_SENT_IN_PARALLEL_V1 \
    INBOXORDER01_STORED_ROWS_READ_ELSEWHERE_TURN_READ_V1 \
    ISSUE262_POST_TO_YOUR_PROFILE_V1 \
    ISSUE262_FAILED_GALLERY_IMAGES_STILL_SHOW_V1 \
    ISSUE257_COLD_START_LANDS_ON_CACHED_SUBSCRIPTIONS_V1 \
    ISSUE245_STATE_SURVIVES_CONFIGURATION_CHANGES_V1 \
    RECREATE01_MESSAGE_DRAFTS_SURVIVE_RECREATION_V1 \
    VIDEOTABSWITCH01_LEAVING_TAB_PAUSES_AT_ONCE_V1
do
    if grep -RFq "$marker" "$SOURCES"; then
        :
    else
        fail "BETA16_RELEASE_FEATURE_MISSING_${marker}"
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
    fail BETA16_BUILD_AND_PUBLICATION_FEATURES_MISSING
[[ "$FAIL" -eq 0 ]] && pass BETA16_RELEASE_FEATURES_INCLUDED

if grep -Fq $'beta16-nonnative-suite\ttools/check-beta16-nonnative-suite-contract.sh\tprimary\t-' "$MANIFEST"; then
    pass BETA16_NONNATIVE_CONTRACT02_ADAPTER
else
    fail BETA16_NONNATIVE_CONTRACT02_ADAPTER
fi

for required in \
  tools/check-source-contract.sh
do
    if grep -Fq "$required" "$SUITE"; then
        :
    else
        fail "BETA16_ADAPTER_MISSING_${required//[^A-Za-z0-9]/_}"
    fi
done

if [[ "$FAIL" -eq 0 ]]; then
    pass BETA16_NONNATIVE_SUITE_EXACT_SCOPE
fi

if grep -Fq 'fun topReleaseSurvivesUpdateManifestNormalizationUnchanged' \
    app/src/test/java/app/subshell/prototype/update/BundledChangelogPromotionPolicyTest.kt &&
   grep -Fq 'fun topReleaseReadsTheSameForAnOlderClient' \
    app/src/test/java/app/subshell/prototype/update/BundledChangelogPromotionPolicyTest.kt
then
    pass BETA16_CHANGELOG_PROMOTION_POLICY_TESTS_PRESENT
else
    fail BETA16_CHANGELOG_PROMOTION_POLICY_TESTS_PRESENT
fi

if grep -Fq 'tools/check-releasetool02a-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02b-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02c-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02d-contract.sh' "$MANIFEST"
then
    pass BETA16_RELEASETOOL02_PIPELINE_QUALIFIED
else
    fail BETA16_RELEASETOOL02_PIPELINE_QUALIFIED
fi

if ! grep -Fq 'tools/check-releasetool01c-contract.sh' "$MANIFEST"; then
    pass BETA16_LEGACY_PUBLICATION_PATH_NOT_RELEASE_GATE
else
    fail BETA16_LEGACY_PUBLICATION_PATH_NOT_RELEASE_GATE
fi

if [[ "$FAIL" -eq 0 ]]; then
    echo 'CONTRACT02_PRIMARY_RESULT=PASS'
    echo 'RESULT=SUBSHELL_BETA16_RELEASE_SCOPE_CONTRACT_PASS'
    exit 0
fi

echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
echo 'RESULT=SUBSHELL_BETA16_RELEASE_SCOPE_CONTRACT_FAIL'
exit 1
