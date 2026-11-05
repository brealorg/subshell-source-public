#!/usr/bin/env bash
set -uo pipefail

# RELEASE48_ONE_RELEASE_SCOPE_CHECKER_V1 selftest: the shared contract passes
# for 0.91-beta as it was published (selftest-0.91.json) and fails for a
# changed highlight, a wrong release below it and a wrong manifest.

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT" || exit 2
TMP="$(mktemp -d "${TMPDIR:-/tmp}/subshell-release-scope-selftest.XXXXXX")"
trap 'rm -rf -- "$TMP"' EXIT INT TERM
FAIL=0

manifest() {
    printf '%s\t%s\t%s\t%s\n' \
        selftest-0-91 tools/release-scope/release-scope-contract.sh primary - \
        release-nonnative-suite tools/check-release-nonnative-suite-contract.sh primary - \
        releasetool02a tools/check-releasetool02a-contract.sh primary - \
        releasetool02b tools/check-releasetool02b-contract.sh primary - \
        releasetool02c tools/check-releasetool02c-contract.sh primary - \
        releasetool02d tools/check-releasetool02d-contract.sh primary - >"$1"
}

spec() { # out, python edit
    python3 -I - "tools/release-scope/selftest-0.91.json" "$1" "$TMP/manifest.tsv" "$2" <<'PY'
import json, sys
spec = json.load(open(sys.argv[1]))
spec["manifest"] = sys.argv[3]
spec["wrapper"] = "tools/release-scope/release-scope-contract.sh"
exec(sys.argv[4])
json.dump(spec, open(sys.argv[2], "w"), ensure_ascii=False)
PY
}

expect() { # name, want(pass|fail), spec
    bash tools/release-scope/release-scope-contract.sh "$3" >"$TMP/$1.log" 2>&1
    rc=$?
    if [[ "$2" == pass && "$rc" -eq 0 ]] || [[ "$2" == fail && "$rc" -ne 0 ]]; then
        echo "PASS=RELEASE_SCOPE_SELFTEST_$1"
    else
        echo "FAIL=RELEASE_SCOPE_SELFTEST_$1"; FAIL=1; sed -n '1,20p' "$TMP/$1.log"
    fi
}

manifest "$TMP/manifest.tsv"
spec "$TMP/ok.json" ""
expect PUBLISHED_0_91 pass "$TMP/ok.json"
spec "$TMP/highlight.json" 'spec["release"]["highlights"][0] += "!"'
expect CHANGED_HIGHLIGHT fail "$TMP/highlight.json"
spec "$TMP/below.json" 'spec["below"][0]["versionName"] = "0.89-beta"'
expect WRONG_RELEASE_BELOW fail "$TMP/below.json"
manifest "$TMP/manifest.tsv"; printf 'releasetool01c\ttools/check-releasetool01c-contract.sh\tprimary\t-\n' >>"$TMP/manifest.tsv"
expect WRONG_MANIFEST fail "$TMP/ok.json"

[[ "$FAIL" -eq 0 ]] && { echo 'RESULT=SUBSHELL_RELEASE_SCOPE_SELFTEST_PASS'; exit 0; }
echo 'RESULT=SUBSHELL_RELEASE_SCOPE_SELFTEST_FAIL'; exit 1
