#!/usr/bin/env bash
set -euo pipefail

# SUBSHELL_RELEASETOOL01A_SIGNED_PREVIEW_DRIVER_V1

FINAL_APPLICATION_ID='io.github.brealorg.subshell'
EXPECTED_RELEASE_CERT_SHA256='52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84'
AUTH05D2_VERIFIER='tools/verify-auth05d2-release-signing.py'
QUALIFICATION_VERIFIER='tools/contract02/verify-release-qualification.py'
PREVIEW_VERIFIER='tools/release01/verify-preview.py'
RELEASE_APK='app/build/outputs/apk/release/app-release.apk'

usage() {
    cat <<'USAGE'
Usage:
  tools/release01/prepare-signed-preview.sh \
    --qualification-record /absolute/path/qualification-record.env \
    --version-name VERSION \
    --version-code POSITIVE_INTEGER \
    --output /absolute/external/preview-directory

Required signing environment:
  SUBSHELL_RELEASE_KEYSTORE_PATH
  SUBSHELL_RELEASE_STORE_PASSWORD_FILE
  SUBSHELL_RELEASE_KEY_PASSWORD_FILE
USAGE
}

QUALIFICATION_RECORD=''
VERSION_NAME=''
VERSION_CODE=''
OUTPUT=''

while [[ $# -gt 0 ]]; do
    case "$1" in
        --qualification-record)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            QUALIFICATION_RECORD="$2"
            shift 2
            ;;
        --version-name)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            VERSION_NAME="$2"
            shift 2
            ;;
        --version-code)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            VERSION_CODE="$2"
            shift 2
            ;;
        --output)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            OUTPUT="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "ERROR=UNKNOWN_ARGUMENT:$1" >&2
            usage >&2
            exit 64
            ;;
    esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

fail() {
    echo "STOP=RELEASETOOL01A_SIGNED_PREVIEW_FAILED reason=$1" >&2
    echo 'DEVICE_MUTATIONS=NONE' >&2
    echo 'REMOTE_OPERATIONS=NONE' >&2
    echo 'RESULT=SUBSHELL_RELEASETOOL01A_SIGNED_PREVIEW_FAILED' >&2
    exit 1
}

canonical_path() {
    python3 - "$1" <<'PY'
from pathlib import Path
import sys
print(Path(sys.argv[1]).expanduser().resolve())
PY
}

find_android_build_tool() {
    local tool="$1"
    local direct sdk_root build_tools

    direct="$(command -v "$tool" 2>/dev/null || true)"
    if [[ -n "$direct" ]]; then
        printf '%s\n' "$direct"
        return 0
    fi

    sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
    build_tools="$(
        find "$sdk_root/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null |
            sort -V |
            tail -n 1
    )"
    [[ -x "$build_tools/$tool" ]] || return 1
    printf '%s\n' "$build_tools/$tool"
}

[[ -n "$QUALIFICATION_RECORD" ]] || { usage >&2; exit 64; }
[[ -n "$VERSION_NAME" ]] || { usage >&2; exit 64; }
[[ -n "$VERSION_CODE" ]] || { usage >&2; exit 64; }
[[ -n "$OUTPUT" ]] || { usage >&2; exit 64; }

[[ "$QUALIFICATION_RECORD" == /* ]] ||
    fail QUALIFICATION_RECORD_MUST_BE_ABSOLUTE
[[ "$OUTPUT" == /* ]] ||
    fail OUTPUT_MUST_BE_ABSOLUTE

QUALIFICATION_RECORD="$(canonical_path "$QUALIFICATION_RECORD")"
OUTPUT="$(canonical_path "$OUTPUT")"
ROOT_CANON="$(canonical_path "$ROOT")"

[[ -f "$QUALIFICATION_RECORD" ]] ||
    fail QUALIFICATION_RECORD_NOT_FOUND

case "$QUALIFICATION_RECORD/" in
    "$ROOT_CANON/"*) fail QUALIFICATION_RECORD_MUST_BE_EXTERNAL ;;
esac
case "$OUTPUT/" in
    "$ROOT_CANON/"*) fail OUTPUT_MUST_BE_EXTERNAL ;;
esac

[[ "$VERSION_NAME" =~ ^[0-9A-Za-z][0-9A-Za-z._+-]*$ ]] ||
    fail VERSION_NAME_NOT_CANONICAL
[[ "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]] ||
    fail VERSION_CODE_NOT_POSITIVE_INTEGER

[[ ! -e "$OUTPUT" ]] ||
    fail FINAL_OUTPUT_ALREADY_EXISTS

KEYSTORE_PATH="${SUBSHELL_RELEASE_KEYSTORE_PATH:-}"
STORE_PASSWORD_FILE="${SUBSHELL_RELEASE_STORE_PASSWORD_FILE:-}"
KEY_PASSWORD_FILE="${SUBSHELL_RELEASE_KEY_PASSWORD_FILE:-}"

[[ "$KEYSTORE_PATH" == /* && -f "$KEYSTORE_PATH" ]] ||
    fail RELEASE_KEYSTORE_NOT_CONFIGURED
[[ "$STORE_PASSWORD_FILE" == /* && -f "$STORE_PASSWORD_FILE" ]] ||
    fail STORE_PASSWORD_FILE_NOT_CONFIGURED
[[ "$KEY_PASSWORD_FILE" == /* && -f "$KEY_PASSWORD_FILE" ]] ||
    fail KEY_PASSWORD_FILE_NOT_CONFIGURED
[[ "$STORE_PASSWORD_FILE" != "$KEY_PASSWORD_FILE" ]] ||
    fail PASSWORD_FILES_MUST_DIFFER

[[ -z "$(git status --porcelain)" ]] ||
    fail SOURCE_WORKTREE_NOT_CLEAN

SOURCE_COMMIT="$(git rev-parse HEAD)"
SOURCE_TREE="$(git rev-parse 'HEAD^{tree}')"

python3 "$QUALIFICATION_VERIFIER" \
    --record "$QUALIFICATION_RECORD" \
    --source-root "$ROOT" ||
    fail QUALIFICATION_RECORD_REJECTED

QUALIFICATION_SHA256="$(sha256sum "$QUALIFICATION_RECORD" | awk '{print $1}')"

PARENT="$(dirname "$OUTPUT")"
mkdir -p "$PARENT"
STAGE="$(mktemp -d "$PARENT/.releasetool01a.$(basename "$OUTPUT").XXXXXX")"
SUCCESS=NO

cleanup() {
    if [[ "$SUCCESS" != YES && -d "$STAGE" ]]; then
        rm -rf -- "$STAGE"
    fi
}
trap cleanup EXIT

BUILD_LOG="$STAGE/assembleRelease.log"
KEYSTORE_ARTIFACT="$STAGE/auth05d2-keystore.json"
APK_ARTIFACT="$STAGE/auth05d2-apk.json"
RELEASE_RECORD="$STAGE/release-record.env"
PREVIEW_APK="$STAGE/subshell-$VERSION_NAME.apk"

AAPT2="$(find_android_build_tool aapt2)" ||
    fail AAPT2_NOT_FOUND
APKSIGNER="$(find_android_build_tool apksigner)" ||
    fail APKSIGNER_NOT_FOUND

python3 "$AUTH05D2_VERIFIER" keystore \
    --keystore "$KEYSTORE_PATH" \
    --store-password-file "$STORE_PASSWORD_FILE" \
    --key-password-file "$KEY_PASSWORD_FILE" \
    --key-alias subshell-app-signing \
    --expected-fingerprint "$EXPECTED_RELEASE_CERT_SHA256" \
    --expected-keystore-sha256 7702295c7184a73cb1baba354becaabcdc186cce76de19a470c41bd66afeb2a7 \
    --application-id "$FINAL_APPLICATION_ID" \
    --source-root "$ROOT" \
    --output "$KEYSTORE_ARTIFACT" ||
    fail KEYSTORE_VERIFICATION_FAILED

python3 "$AUTH05D2_VERIFIER" verify-artifact \
    --input "$KEYSTORE_ARTIFACT" ||
    fail KEYSTORE_ARTIFACT_REJECTED

rm -rf -- app/build/outputs/apk/release

./gradlew \
    :app:assembleRelease \
    --offline \
    --no-daemon \
    --no-configuration-cache \
    "-Psubshell.release.qualificationRecord=$QUALIFICATION_RECORD" \
    "-Psubshell.release.versionName=$VERSION_NAME" \
    "-Psubshell.release.versionCode=$VERSION_CODE" \
    > >(tee "$BUILD_LOG") 2>&1

[[ -f "$RELEASE_APK" ]] ||
    fail RELEASE_APK_MISSING

grep -Fq 'RESULT=SUBSHELL_CONTRACT02_RELEASE_QUALIFICATION_VERIFICATION_PASS' "$BUILD_LOG" ||
    fail CONTRACT02_QUALIFICATION_GATE_RESULT_MISSING
grep -Fq 'RESULT=SUBSHELL_AUTH05D2_RELEASE_SIGNING_IDENTITY_PASS' "$BUILD_LOG" ||
    fail AUTH05D2_SIGNING_IDENTITY_GATE_RESULT_MISSING
grep -Fq 'BUILD SUCCESSFUL' "$BUILD_LOG" ||
    fail RELEASE_BUILD_NOT_SUCCESSFUL

if grep -Eq ':app:(subshellContracts|subshellQualityCheck)([[:space:]]|$)' "$BUILD_LOG"; then
    fail LEGACY_QUALITY_REENTRY_DETECTED
fi

python3 "$AUTH05D2_VERIFIER" apk \
    --apk "$RELEASE_APK" \
    --aapt2 "$AAPT2" \
    --apksigner "$APKSIGNER" \
    --application-id "$FINAL_APPLICATION_ID" \
    --expected-fingerprint "$EXPECTED_RELEASE_CERT_SHA256" \
    --output "$APK_ARTIFACT" ||
    fail AUTH05D2_APK_VERIFICATION_FAILED

python3 "$AUTH05D2_VERIFIER" verify-artifact \
    --input "$APK_ARTIFACT" ||
    fail AUTH05D2_APK_ARTIFACT_REJECTED

cp -- "$RELEASE_APK" "$PREVIEW_APK"

BUILT_SHA256="$(sha256sum "$RELEASE_APK" | awk '{print $1}')"
PREVIEW_SHA256="$(sha256sum "$PREVIEW_APK" | awk '{print $1}')"
[[ "$BUILT_SHA256" == "$PREVIEW_SHA256" ]] ||
    fail PREVIEW_COPY_NOT_BIT_IDENTICAL

python3 "$PREVIEW_VERIFIER" \
    --apk "$PREVIEW_APK" \
    --aapt2 "$AAPT2" \
    --apksigner "$APKSIGNER" \
    --application-id "$FINAL_APPLICATION_ID" \
    --version-name "$VERSION_NAME" \
    --version-code "$VERSION_CODE" \
    --expected-fingerprint "$EXPECTED_RELEASE_CERT_SHA256" \
    --source-root "$ROOT" \
    --qualification-record "$QUALIFICATION_RECORD" \
    --qualification-sha256 "$QUALIFICATION_SHA256" \
    --build-log "$BUILD_LOG" \
    --auth05d2-apk-artifact "$APK_ARTIFACT" \
    --release-record "$RELEASE_RECORD" ||
    fail PREVIEW_RECORD_VERIFICATION_FAILED

[[ -z "$(git status --porcelain)" ]] ||
    fail SOURCE_CHANGED_DURING_RELEASE_BUILD
[[ "$(git rev-parse HEAD)" == "$SOURCE_COMMIT" ]] ||
    fail SOURCE_COMMIT_CHANGED_DURING_BUILD
[[ "$(git rev-parse 'HEAD^{tree}')" == "$SOURCE_TREE" ]] ||
    fail SOURCE_TREE_CHANGED_DURING_BUILD

FINAL_RECORD="$OUTPUT/release-record.env"
FINAL_APK="$OUTPUT/subshell-$VERSION_NAME.apk"

# Rewrite only the staging APK path to the final immutable path before publish.
python3 - "$RELEASE_RECORD" "$FINAL_APK" <<'PY'
from pathlib import Path
import sys

record = Path(sys.argv[1])
final_apk = sys.argv[2]
lines = []
found = False
for line in record.read_text(encoding="utf-8").splitlines():
    if line.startswith("PREVIEW_APK="):
        lines.append(f"PREVIEW_APK={final_apk}")
        found = True
    else:
        lines.append(line)
if not found:
    raise SystemExit("PREVIEW_APK_FIELD_MISSING")
record.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY

mv -- "$STAGE" "$OUTPUT"
SUCCESS=YES

echo "SOURCE_COMMIT=$SOURCE_COMMIT"
echo "SOURCE_TREE=$SOURCE_TREE"
echo "VERSION_NAME=$VERSION_NAME"
echo "VERSION_CODE=$VERSION_CODE"
echo "APPLICATION_ID=$FINAL_APPLICATION_ID"
echo "SIGNER_CERT_SHA256=$EXPECTED_RELEASE_CERT_SHA256"
echo "APK_SHA256=$PREVIEW_SHA256"
echo "PREVIEW_APK=$FINAL_APK"
echo "RELEASE_RECORD=$FINAL_RECORD"
echo 'LEGACY_QUALITY_REENTRY=NO'
echo 'DEVICE_MUTATIONS=NONE'
echo 'REMOTE_OPERATIONS=NONE'
echo 'RESULT=SUBSHELL_RELEASETOOL01A_SIGNED_PREVIEW_PASS'
