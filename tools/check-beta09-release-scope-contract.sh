#!/usr/bin/env bash
set -uo pipefail

# SUBSHELL_BETA09_RELEASE_SCOPE_CONTRACT_V1 (0.79-beta, versionCode 243)

FAIL=0

pass() {
    echo "PASS=$1"
}

fail() {
    echo "FAIL=$1"
    FAIL=1
}

CHANGELOG='app/src/main/assets/changelog/releases.json'
MANIFEST='tools/contract02/manifests/beta09.tsv'
MODEL='app/src/main/java/app/subshell/prototype/update/UpdateModels.kt'
SUITE='tools/check-beta09-nonnative-suite-contract.sh'
SOURCES='app/src/main/java'

for file in "$CHANGELOG" "$MANIFEST" "$MODEL" "$SUITE"; do
    [[ -f "$file" ]] ||
        fail "BETA09_MISSING_${file//[^A-Za-z0-9]/_}"
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
top = codes.index(243)
assert codes[top:top + 5] == [
    243,
    242,
    241,
    240,
    239,
]

r = releases[top]

assert r == {
    "versionCode": 243,
    "versionName": "0.79-beta",
    "date": "2026-10-08",
    "headline": "Subshell 0.79: saved searches, RedGIFs in the app, Reddit favorites sync and a full review of the code.",
    "highlights": [
        "New: saved searches in the Search hub, an optional scroll position indicator, RedGIFs clips in the app (with Show NSFW posts) and favorites synced with Reddit.",
        "Reddit emotes, GIFs and images show inline in posts and comments and can be edited; spoilers stay hidden until tapped; escapes, nested styles and lists render right.",
        "Safer writing: a post, comment, message or moderator action whose answer was lost is checked with Reddit instead of sent twice, and drafts survive restarts.",
        "Accounts stay apart: votes, scroll positions, lists and notifications no longer cross between accounts; a notification for another account switches to it.",
        "Fewer crashes and stalls: the app returns where you were after Android closes it, large photos no longer crash previews, and slow work left the main thread.",
        "Accessibility: switches, choices and buttons announce what they are and their state, touch targets are larger, and auto-hiding controls follow the system timeout.",
        "Correctness: votes, refreshes, paging, search results, markdown, flair and moderator tools now do what they show, and Retry picks up where a load failed.",
        "No lost work: drafts, replies and moderator actions survive Android closing the app or a missing answer from Reddit, and backups read back everything they hold.",
        "Privacy and security: names and ids stay out of release logs, turning a protection off needs the device unlock, and comment images load only from known image hosts.",
        "Speed: lists, settings, backups and links read their data off the main thread, caches are capped, and long threads load more replies on their own.",
        "Also: inbox notifications are on by default, shares and links open in the Subshell you already have, numbers follow your language and word filters match whole words.",
        "Removed: video downloads. Videos stream; anything an older version downloaded can be deleted in Settings.",
    ],
    "releaseUrl": (
        "https://github.com/brealorg/subshell/releases/tag/"
        "v0.79-beta"
    ),
}

# RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1: from 0.79 a
# release may carry 12 highlights of 240 characters; the headline stays at
# 160 and the first six highlights at 180, because 0.78.0-beta.1 and older
# reject the whole release over a long headline and show only six.
for release in releases:
    if release["versionCode"] > 243:
        continue
    assert len(release["headline"].strip()) <= 160, release["versionName"]
    assert len(release["highlights"]) <= 12, release["versionName"]
    for index, item in enumerate(release["highlights"]):
        limit = 180 if index < 6 else 240
        assert item == item.strip() and 0 < len(item) <= limit, (release["versionName"], item)

beta8 = releases[top + 1]

assert beta8["versionCode"] == 242
assert beta8["versionName"] == "0.78.0-beta.1"
assert beta8["date"] == "2026-09-27"
PY

    [[ $? -eq 0 ]] &&
        pass BETA09_EXACT_BUNDLED_CHANGELOG ||
        fail BETA09_EXACT_BUNDLED_CHANGELOG
fi

if grep -Fq \
    'https://brealorg.github.io/subshell-update-beta.json' \
    "$MODEL"
then
    pass BETA09_UPDATE_ENDPOINT_PRESERVED
else
    fail BETA09_UPDATE_ENDPOINT_PRESERVED
fi

EXPECTED_MANIFEST="$(
cat <<'LIST'
beta09	tools/check-beta09-release-scope-contract.sh	primary	-
beta09-nonnative-suite	tools/check-beta09-nonnative-suite-contract.sh	primary	-
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
    pass BETA09_EXACT_NATIVE_RELEASE_MANIFEST
else
    fail BETA09_EXACT_NATIVE_RELEASE_MANIFEST
fi

while IFS=$'\t' read -r id path block predecessors; do
    [[ -n "$id" ]] || continue
    [[ "$id" == \#* ]] && continue

    if [[ -f "$path" ]]; then
        pass "BETA09_MANIFEST_PATH_${id}"
    else
        fail "BETA09_MANIFEST_PATH_${id}"
    fi

    if [[ "$block" != 'primary' ]]; then
        fail "BETA09_MANIFEST_MODE_${id}"
    fi
    # A manifest row must be Contract02-native; a checker without the primary
    # marker belongs in the non-native suite adapter (beta.4 and 0.78.0-beta.1).
    if [[ -f "$path" ]] && grep -Fq 'CONTRACT02_PRIMARY_RESULT=' "$path"; then
        pass "BETA09_MANIFEST_NATIVE_${id}"
    else
        fail "BETA09_MANIFEST_NATIVE_${id}"
    fi
done <"$MANIFEST"

# The release-defining features were landed with behaviour tests, not native
# checkers; the scope pins that each one is present by its source marker,
# anywhere under the sources (markers move with their code).
for marker in \
    ISSUE205_PASSIVE_SCROLL_POSITION_INDICATOR_V1 \
    ISSUE206_SAVED_SEARCHES_V1 \
    ISSUE206_SAVED_SEARCHES_SCHEMA_V10_V1 \
    ISSUE207_REDGIFS_PLAYS_IN_THE_APP_V1 \
    REDDITFAV01_SHOW_REDDIT_FAVORITES_V1 \
    REDDITFAV04_CUSTOM_FEED_FAVORITES_V1 \
    NOTIFY02_ON_BY_DEFAULT_IN_THE_MAIN_APP_V1 \
    ISSUE81_SPOILERS_HIDDEN_UNTIL_TAPPED_V1 \
    LOWUI4C_LEGACY_DOWNLOADS_CAN_ONLY_BE_DELETED_V1 \
    LOW_EXTERNAL_LAUNCHES_USE_SUBSHELLS_OWN_TASK_V1 \
    RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1
do
    if grep -RFq "$marker" "$SOURCES"; then
        :
    else
        fail "BETA09_RELEASE_FEATURE_MISSING_${marker}"
    fi
done
[[ "$FAIL" -eq 0 ]] && pass BETA09_RELEASE_FEATURES_INCLUDED

if grep -Fq $'beta09-nonnative-suite\ttools/check-beta09-nonnative-suite-contract.sh\tprimary\t-' "$MANIFEST"; then
    pass BETA09_NONNATIVE_CONTRACT02_ADAPTER
else
    fail BETA09_NONNATIVE_CONTRACT02_ADAPTER
fi

# profile01a7a4, media-audio-r27, issue11-video-error and a11y01a411 were retired on
# 2026-10-13 (step 3, owner approved); issue11 also left the beta09 manifest.
# settings02 and theme01 were retired on 2026-10-14 (step 3, owner approved).
# followpost01a1a/b, userstatus01a1 and backup04a4 were retired on 2026-10-14
# (step 3, owner approved); the last two also left the beta09 manifest.
# auth07c1 and auth07c2 were retired on 2026-10-14 (step 3, owner approved);
# both left the beta09 manifest.
for required in \
  tools/check-source-contract.sh
do
    if grep -Fq "$required" "$SUITE"; then
        :
    else
        fail "BETA09_ADAPTER_MISSING_${required//[^A-Za-z0-9]/_}"
    fi
done

if [[ "$FAIL" -eq 0 ]]; then
    pass BETA09_NONNATIVE_SUITE_EXACT_SCOPE
fi

if grep -Fq 'fun topReleaseSurvivesUpdateManifestNormalizationUnchanged' \
    app/src/test/java/app/subshell/prototype/update/BundledChangelogPromotionPolicyTest.kt &&
   grep -Fq 'fun topReleaseReadsTheSameForAnOlderClient' \
    app/src/test/java/app/subshell/prototype/update/BundledChangelogPromotionPolicyTest.kt
then
    pass BETA09_CHANGELOG_PROMOTION_POLICY_TESTS_PRESENT
else
    fail BETA09_CHANGELOG_PROMOTION_POLICY_TESTS_PRESENT
fi

if grep -Fq 'tools/check-releasetool02a-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02b-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02c-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02d-contract.sh' "$MANIFEST"
then
    pass BETA09_RELEASETOOL02_PIPELINE_QUALIFIED
else
    fail BETA09_RELEASETOOL02_PIPELINE_QUALIFIED
fi

if ! grep -Fq 'tools/check-releasetool01c-contract.sh' "$MANIFEST"; then
    pass BETA09_LEGACY_PUBLICATION_PATH_NOT_RELEASE_GATE
else
    fail BETA09_LEGACY_PUBLICATION_PATH_NOT_RELEASE_GATE
fi

if [[ "$FAIL" -eq 0 ]]; then
    echo 'CONTRACT02_PRIMARY_RESULT=PASS'
    echo 'RESULT=SUBSHELL_BETA09_RELEASE_SCOPE_CONTRACT_PASS'
    exit 0
fi

echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
echo 'RESULT=SUBSHELL_BETA09_RELEASE_SCOPE_CONTRACT_FAIL'
exit 1
