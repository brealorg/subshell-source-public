#!/usr/bin/env bash
set -uo pipefail

# SUBSHELL_BETA10_RELEASE_SCOPE_CONTRACT_V1 (0.80-beta, versionCode 244)

FAIL=0

pass() {
    echo "PASS=$1"
}

fail() {
    echo "FAIL=$1"
    FAIL=1
}

CHANGELOG='app/src/main/assets/changelog/releases.json'
MANIFEST='tools/contract02/manifests/beta10.tsv'
MODEL='app/src/main/java/app/subshell/prototype/update/UpdateModels.kt'
SUITE='tools/check-beta10-nonnative-suite-contract.sh'
SOURCES='app/src/main/java'

for file in "$CHANGELOG" "$MANIFEST" "$MODEL" "$SUITE"; do
    [[ -f "$file" ]] ||
        fail "BETA10_MISSING_${file//[^A-Za-z0-9]/_}"
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
top = codes.index(244)
assert codes[top:top + 5] == [
    244,
    243,
    242,
    241,
    240,
]

r = releases[top]

assert r == {
    "versionCode": 244,
    "versionName": "0.80-beta",
    "date": "2026-10-15",
    "headline": "Subshell 0.80: Material 3 throughout, a new video player and better accessibility",
    "highlights": [
        "Material 3 throughout: one top bar per screen, a navigation rail on tablets and foldables, list rows and segmented choices in Settings.",
        "Video: a new seek bar with live frames, playback options in post and full screen, speed, double-tap skip, hold for 2x and picture-in-picture (each optional).",
        "Wi-Fi only autoplay now really holds autoplay on mobile data; playing video keeps the screen on, and unplugging headphones pauses.",
        "A Video group in Settings: subtitles, audio language, timeline size, quality and where the playback options show.",
        "Better contrast, screen-reader labels and large-text layouts across the app.",
        "Defaults reviewed: the theme follows the system, unread markers are on, and a one-time tip points to Settings.",
        "Comments removed by a moderator are marked Removed in the thread.",
        "NSFW: searches follow Show NSFW posts, quarantined communities can be opted into, banned and private ones say why; optional random NSFW community.",
        "Errors appear in one tray instead of pop-ups; an image that can never load offers Open in browser.",
        "Images linked over http load over https; a link to an image host stays a link.",
        "Fixes: GIFs on your own profile autoplay again, lists without a top bar scroll by touch, Hidden tab for local accounts, link decoding on Android 8-12.",
        "Many internal checks replaced by behaviour tests, and every land now runs a lint gate.",
    ],
    "releaseUrl": (
        "https://github.com/brealorg/subshell/releases/tag/"
        "v0.80-beta"
    ),
}

# RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1: from 0.79 a
# release may carry 12 highlights of 240 characters; the headline stays at
# 160 and the first six highlights at 180, because 0.78.0-beta.1 and older
# reject the whole release over a long headline and show only six.
for release in releases:
    if release["versionCode"] > 244:
        continue
    assert len(release["headline"].strip()) <= 160, release["versionName"]
    assert len(release["highlights"]) <= 12, release["versionName"]
    for index, item in enumerate(release["highlights"]):
        limit = 180 if index < 6 else 240
        assert item == item.strip() and 0 < len(item) <= limit, (release["versionName"], item)

previous = releases[top + 1]

assert previous["versionCode"] == 243
assert previous["versionName"] == "0.79-beta"
assert previous["date"] == "2026-10-08"
PY

    [[ $? -eq 0 ]] &&
        pass BETA10_EXACT_BUNDLED_CHANGELOG ||
        fail BETA10_EXACT_BUNDLED_CHANGELOG
fi

if grep -Fq \
    'https://brealorg.github.io/subshell-update-beta.json' \
    "$MODEL"
then
    pass BETA10_UPDATE_ENDPOINT_PRESERVED
else
    fail BETA10_UPDATE_ENDPOINT_PRESERVED
fi

EXPECTED_MANIFEST="$(
cat <<'LIST'
beta10	tools/check-beta10-release-scope-contract.sh	primary	-
beta10-nonnative-suite	tools/check-beta10-nonnative-suite-contract.sh	primary	-
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
    pass BETA10_EXACT_NATIVE_RELEASE_MANIFEST
else
    fail BETA10_EXACT_NATIVE_RELEASE_MANIFEST
fi

while IFS=$'\t' read -r id path block predecessors; do
    [[ -n "$id" ]] || continue
    [[ "$id" == \#* ]] && continue

    if [[ -f "$path" ]]; then
        pass "BETA10_MANIFEST_PATH_${id}"
    else
        fail "BETA10_MANIFEST_PATH_${id}"
    fi

    if [[ "$block" != 'primary' ]]; then
        fail "BETA10_MANIFEST_MODE_${id}"
    fi
    # A manifest row must be Contract02-native; a checker without the primary
    # marker belongs in the non-native suite adapter (beta.4 and 0.78.0-beta.1).
    if [[ -f "$path" ]] && grep -Fq 'CONTRACT02_PRIMARY_RESULT=' "$path"; then
        pass "BETA10_MANIFEST_NATIVE_${id}"
    else
        fail "BETA10_MANIFEST_NATIVE_${id}"
    fi
done <"$MANIFEST"

# The release-defining features were landed with behaviour tests, not native
# checkers; the scope pins that each one is present by its source marker,
# anywhere under the sources (markers move with their code).
for marker in \
    M3223_ONE_ROUTE_TOP_BAR_V1 \
    M3224_READING_WIDTH_V1 \
    M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1 \
    VIDEO02_AUTOPLAY_FOLLOWS_WIFI_ONLY_V1 \
    VIDEO03_ONE_OWNER_FOR_THE_SEEK_DRAG_V1 \
    VIDEO05_VIDEO_SETTINGS_GROUP_V1 \
    VIDEO07_PICTURE_IN_PICTURE_V1 \
    SETTINGSAUDIT01_DEFAULTS_V1 \
    SETTINGSAUDIT01_REVIEW_SETTINGS_NUDGE_V1 \
    ISSUE226_REMOVED_COMMENT_IS_MARKED_V1 \
    ISSUE211_SEARCH_FOLLOWS_SHOW_NSFW_V1 \
    ISSUE222_NO_TOASTS_V1 \
    ISSUE228_HTTP_IMAGES_ARE_TRIED_OVER_HTTPS_V1 \
    ISSUE228_A_BARE_IMAGE_HOST_LINK_STAYS_A_LINK_V1 \
    STEP3_NO_BAR_NO_BAR_SCROLL_V1 \
    PARITY01_FEED_SURFACE_CLEARS_ONLY_WHAT_IT_OWNS_V1 \
    RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1
do
    if grep -RFq "$marker" "$SOURCES"; then
        :
    else
        fail "BETA10_RELEASE_FEATURE_MISSING_${marker}"
    fi
done
[[ "$FAIL" -eq 0 ]] && pass BETA10_RELEASE_FEATURES_INCLUDED

if grep -Fq $'beta10-nonnative-suite\ttools/check-beta10-nonnative-suite-contract.sh\tprimary\t-' "$MANIFEST"; then
    pass BETA10_NONNATIVE_CONTRACT02_ADAPTER
else
    fail BETA10_NONNATIVE_CONTRACT02_ADAPTER
fi

for required in \
  tools/check-source-contract.sh
do
    if grep -Fq "$required" "$SUITE"; then
        :
    else
        fail "BETA10_ADAPTER_MISSING_${required//[^A-Za-z0-9]/_}"
    fi
done

if [[ "$FAIL" -eq 0 ]]; then
    pass BETA10_NONNATIVE_SUITE_EXACT_SCOPE
fi

if grep -Fq 'fun topReleaseSurvivesUpdateManifestNormalizationUnchanged' \
    app/src/test/java/app/subshell/prototype/update/BundledChangelogPromotionPolicyTest.kt &&
   grep -Fq 'fun topReleaseReadsTheSameForAnOlderClient' \
    app/src/test/java/app/subshell/prototype/update/BundledChangelogPromotionPolicyTest.kt
then
    pass BETA10_CHANGELOG_PROMOTION_POLICY_TESTS_PRESENT
else
    fail BETA10_CHANGELOG_PROMOTION_POLICY_TESTS_PRESENT
fi

if grep -Fq 'tools/check-releasetool02a-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02b-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02c-contract.sh' "$MANIFEST" &&
   grep -Fq 'tools/check-releasetool02d-contract.sh' "$MANIFEST"
then
    pass BETA10_RELEASETOOL02_PIPELINE_QUALIFIED
else
    fail BETA10_RELEASETOOL02_PIPELINE_QUALIFIED
fi

if ! grep -Fq 'tools/check-releasetool01c-contract.sh' "$MANIFEST"; then
    pass BETA10_LEGACY_PUBLICATION_PATH_NOT_RELEASE_GATE
else
    fail BETA10_LEGACY_PUBLICATION_PATH_NOT_RELEASE_GATE
fi

if [[ "$FAIL" -eq 0 ]]; then
    echo 'CONTRACT02_PRIMARY_RESULT=PASS'
    echo 'RESULT=SUBSHELL_BETA10_RELEASE_SCOPE_CONTRACT_PASS'
    exit 0
fi

echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
echo 'RESULT=SUBSHELL_BETA10_RELEASE_SCOPE_CONTRACT_FAIL'
exit 1
