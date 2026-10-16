# CONTRACT02C — AUTH07C1/AUTH07C2 native migration

CONTRACT02C is the first migration of existing Subshell contracts to the
Contract02 run-once execution model.

## Scope

Only these existing contracts change:

- `tools/check-auth07c1-contract.sh`
- `tools/check-auth07c2-contract.sh`

No Android product source is modified. The legacy global source-contract
aggregator is not modified.

## AUTH07C1

AUTH07C1 already consists only of its own assertions. Contract02C adds the
Contract02 primary-result protocol:

```text
CONTRACT02_PRIMARY_RESULT=PASS
CONTRACT02_PRIMARY_RESULT=FAIL
```

Those markers are emitted only while
`SUBSHELL_CONTRACT02_RUNNER_ACTIVE=1`, so legacy direct invocation retains
its existing result surface.

## AUTH07C2 predecessor split

Historically AUTH07C2 directly executed AUTH07C1 as a regression gate.

AUTH07C2 does not execute AUTH07C1 in Contract02 mode. It emits:

```text
CONTRACT02_PREDECESSOR_DELEGATED=auth07c1
```

and evaluates only its own assertions. The runner has already executed
AUTH07C1 and reads its status from the run-owned cache.

Legacy direct invocation remains supported. Outside Contract02 mode,
AUTH07C2 continues to execute the existing AUTH07C1 regression gate so
the old global aggregator remains compatible during incremental
migration.

## Manifest and release policy

The native manifest is:

```text
auth07c1    tools/check-auth07c1-contract.sh    primary    -
auth07c2    tools/check-auth07c2-contract.sh    primary    auth07c1
```

Both entries use `block_mode=primary`.

This has an intentional classification effect if AUTH07C1 becomes red:

- AUTH07C1 records one **Primary failure** and one release-blocking row.
- AUTH07C2 records a **Predecessor failure** from the cache.
- AUTH07C2 does not double-count that predecessor failure as another
  release-blocking row.

If AUTH07C2's own assertions fail, AUTH07C2 records its own Primary
failure and blocks because its manifest policy is `primary`.

## Run-once proof

The preview uses a non-mutating `BASH_ENV` trace to record every
non-interactive Bash script invocation during the native manifest run.
AUTH07C1 and AUTH07C2 must each appear exactly once.

This proves that the native successor does not recursively re-execute its
predecessor.
