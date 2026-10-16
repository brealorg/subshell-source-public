#!/usr/bin/env bash
set -euo pipefail

# SUBSHELL_CONTRACT02_RUN_ONCE_SELFTEST_V1

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNNER="$ROOT/tools/contract02/run-once.sh"
TMP="$ROOT/build/contract02-selftest.$$"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/contracts"

make_contract() {
    local name="$1"
    local result="$2"
    local rc="$3"
    cat >"$TMP/contracts/$name.sh" <<EOF
#!/usr/bin/env bash
set -u
printf '%s\n' '$name' >>"\$CONTRACT02_COUNTER_FILE"
echo 'CONTRACT02_PRIMARY_RESULT=$result'
exit $rc
EOF
}

make_contract a PASS 0
make_contract b PASS 0
make_contract c FAIL 7
make_contract d PASS 0
make_contract e PASS 0

COUNTER="$TMP/counter.txt"
export CONTRACT02_COUNTER_FILE="$COUNTER"

echo '===== SELFTEST 1/4 ALL PASS + RUN ONCE ====='
cat >"$TMP/pass.tsv" <<EOF
a	build/contract02-selftest.$$/contracts/a.sh	primary	-
b	build/contract02-selftest.$$/contracts/b.sh	primary	a
EOF

"$RUNNER" --manifest "$TMP/pass.tsv" --output "$TMP/out-pass" >"$TMP/pass.log"

grep -Fq 'PRIMARY_FAILURES=0' "$TMP/out-pass/summary.txt"
grep -Fq 'PREDECESSOR_FAILURES=0' "$TMP/out-pass/summary.txt"
grep -Fq 'RELEASE_BLOCKING_FAILURES=0' "$TMP/out-pass/summary.txt"
grep -Fq 'RELEASE_GATE=PASS' "$TMP/out-pass/summary.txt"
[[ "$(grep -cx 'a' "$COUNTER")" -eq 1 ]]
[[ "$(grep -cx 'b' "$COUNTER")" -eq 1 ]]
echo 'PASS=SELFTEST_ALL_PASS_RUN_ONCE'

echo '===== SELFTEST 2/4 PRIMARY VS PREDECESSOR NONBLOCKING ====='
: >"$COUNTER"
cat >"$TMP/classification.tsv" <<EOF
c	build/contract02-selftest.$$/contracts/c.sh	none	-
d	build/contract02-selftest.$$/contracts/d.sh	none	c
EOF

"$RUNNER" --manifest "$TMP/classification.tsv" --output "$TMP/out-classification" >"$TMP/classification.log"

grep -Fq 'PRIMARY_FAILURES=1' "$TMP/out-classification/summary.txt"
grep -Fq 'PREDECESSOR_FAILURES=1' "$TMP/out-classification/summary.txt"
grep -Fq 'RELEASE_BLOCKING_FAILURES=0' "$TMP/out-classification/summary.txt"
grep -Fq 'RELEASE_GATE=PASS' "$TMP/out-classification/summary.txt"
grep -Eq '^c	.*	1	FAIL	PASS	FAIL	none	NO	7	-' "$TMP/out-classification/results.tsv"
grep -Eq '^d	.*	1	PASS	FAIL	FAIL	none	NO	0	c	' "$TMP/out-classification/results.tsv"
[[ "$(grep -cx 'c' "$COUNTER")" -eq 1 ]]
[[ "$(grep -cx 'd' "$COUNTER")" -eq 1 ]]
echo 'PASS=SELFTEST_PRIMARY_PREDECESSOR_SEPARATION'

echo '===== SELFTEST 3/4 PREDECESSOR CAN BE EXPLICITLY RELEASE BLOCKING ====='
: >"$COUNTER"
cat >"$TMP/blocking.tsv" <<EOF
c	build/contract02-selftest.$$/contracts/c.sh	none	-
e	build/contract02-selftest.$$/contracts/e.sh	any	c
EOF

set +e
"$RUNNER" --manifest "$TMP/blocking.tsv" --output "$TMP/out-blocking" >"$TMP/blocking.log"
rc=$?
set -e

[[ "$rc" -eq 1 ]]
grep -Fq 'PRIMARY_FAILURES=1' "$TMP/out-blocking/summary.txt"
grep -Fq 'PREDECESSOR_FAILURES=1' "$TMP/out-blocking/summary.txt"
grep -Fq 'RELEASE_BLOCKING_FAILURES=1' "$TMP/out-blocking/summary.txt"
grep -Fq 'RELEASE_GATE=BLOCKED' "$TMP/out-blocking/summary.txt"
grep -Eq '^e	.*	1	PASS	FAIL	FAIL	any	YES	0	c	' "$TMP/out-blocking/results.tsv"
[[ "$(grep -cx 'c' "$COUNTER")" -eq 1 ]]
[[ "$(grep -cx 'e' "$COUNTER")" -eq 1 ]]
echo 'PASS=SELFTEST_EXPLICIT_RELEASE_BLOCKING'

echo '===== SELFTEST 4/4 A PREDECESSOR MUST APPEAR EARLIER ====='
: >"$COUNTER"
cat >"$TMP/order.tsv" <<EOF
b	build/contract02-selftest.$$/contracts/b.sh	primary	a
a	build/contract02-selftest.$$/contracts/a.sh	primary	-
EOF

set +e
"$RUNNER" --manifest "$TMP/order.tsv" --output "$TMP/out-order" >"$TMP/order.log" 2>"$TMP/order.err"
rc=$?
set -e

[[ "$rc" -eq 65 ]]
grep -Fq 'PREDECESSOR_MUST_APPEAR_EARLIER:b:a' "$TMP/order.err"
[[ ! -s "$COUNTER" ]]
echo 'PASS=SELFTEST_PREDECESSOR_MUST_APPEAR_EARLIER'

echo 'RESULT=SUBSHELL_CONTRACT02_RUN_ONCE_SELFTEST_PASS'
