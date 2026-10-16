# CONTRACT02B — run-once contract execution model

`CONTRACT02B` introduces an additive execution layer for Subshell source
contracts. It does not replace or rewrite the legacy contract scripts.

## Problem being addressed

The CONTRACT02A audit found 242 contract scripts, 585 structural
cross-references, and 161 contracts that reference other contract files.
The legacy global aggregator has grown large enough that release
qualification can repeatedly execute predecessor chains and can report a
single stale global result name for unrelated failures.

The new model separates execution from dependency classification.

## Contract02 protocol

A Contract02-native contract contains **own assertions only**. It does not
execute predecessor contracts.

Every Contract02-native contract must emit exactly one of:

```text
CONTRACT02_PRIMARY_RESULT=PASS
CONTRACT02_PRIMARY_RESULT=FAIL
```

`PASS` exits with status 0. `FAIL` exits non-zero.

The runner executes each manifest entry exactly once and writes the result
to a run-owned TSV cache. Predecessor state is then read from that cache;
the predecessor contract is never executed again merely because a
successor depends on it.

## Manifest

The manifest is tab-separated:

```text
id    path    block_mode    predecessors
```

Entries are deliberately topologically ordered: every predecessor must
appear earlier in the manifest.

`block_mode` has three values:

- `primary`: this contract blocks the release only when its own assertions
  fail.
- `any`: this contract blocks when either its own assertions or a
  predecessor status fails.
- `none`: failures remain visible but do not block the release.

This makes release policy explicit instead of deriving it from recursive
shell exit codes.

## Result classification

The runner reports these separately:

- **Primary failure** — this contract's own assertions failed.
- **Predecessor failure** — this contract's own execution may have passed,
  but one or more declared predecessors have a failed cached overall
  status.
- **Release-blocking failure** — the manifest policy says that the observed
  primary/predecessor state blocks the current gate.

The top-level summary always includes:

```text
PRIMARY_FAILURES=
PREDECESSOR_FAILURES=
RELEASE_BLOCKING_FAILURES=
RELEASE_GATE=PASS|BLOCKED
```

A predecessor failure is therefore not automatically a release-blocking
failure.

## Cache

Each run owns an output directory with:

- `results.tsv` — one row per contract, with `execution_count=1`.
- `logs/<id>.log` — raw contract output.
- `summary.txt` — aggregate classification and release-gate state.

The cache path is exposed to each native contract through
`SUBSHELL_CONTRACT02_RESULT_CACHE` for future read-only diagnostics.

## Migration boundary

Legacy contracts are not modified by CONTRACT02B.

They remain callable through their existing paths. They are not silently
treated as Contract02-native because a legacy non-zero exit can still mix
own assertion failure with recursively inherited predecessor failure.

Migration is incremental:

1. establish and test the new runner;
2. convert selected release-relevant contracts to own-assertions-only;
3. declare predecessors in manifests;
4. move release qualification to Contract02 manifests;
5. retire recursive predecessor execution only after coverage is proven.

The legacy `tools/check-source-contract.sh` is intentionally not invoked by
the Contract02 runner.

## Release direction

The intended release workflow is:

```text
known-good previous release
→ exact source delta
→ Contract02 manifest for affected scope
→ full unit tests
→ RELEASE_GATE
→ signed preview
→ package/version/signer verification
→ in-place update
→ runtime/visual smoke
→ bit-identical artifact promotion
```

A signed release build must not re-enter the legacy global contract
aggregator after the delta release gate has passed.
