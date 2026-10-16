# tools/slicezero — checker refactoring tooling

Slice zero of the hub-file refactor (see `HANDOVER-HISTORY.md` §1.7 and
`reports/2026-09-19-slice-zero-*.md`). Three tools, all read-only unless
told otherwise, all parameterised so they can be rerun for other files later.

| Tool | Purpose |
|---|---|
| `callgraph.py` | Static graph of which `tools/check-*.sh` executes which. Emits depth, transitive launch counts, gate reachability and the orphan list. |
| `run-baseline-seq.sh` | Runs every checker strictly sequentially (CLAUDE.md hard rule 9) with a per-checker timeout, memo wrapper for children, resumable progress file. Output dir must be outside every repo. |
| `rewrite_checkers.py` | Widens positive fixed-literal assertions on the hub files from "in file X" to "under `app/src/main/java`". Dry run by default: writes the manifest only. `--apply` rewrites the `rewrite` rows in place. |

Tests: `python3 -m unittest discover -s tools/slicezero/tests -v`
(pure Python + bash fixtures in temp dirs; no Gradle, no real checker is run).

## Procedure (as approved 2026-09-19)

```sh
# 1. baseline, outputs outside the repo
tools/slicezero/run-baseline-seq.sh ~/dev/subshell/worklog/slice-zero/baseline-<sha> . 470

# 2. gate scope
python3 tools/slicezero/callgraph.py --tools-dir tools \
    --roots check-all-contracts.sh check-contract-delta.sh \
    --manifests tools/contract02/manifests \
    --reachable docs/slicezero/gate-reachable-<sha>.txt \
    --orphans docs/slicezero/orphans-<sha>.txt --table docs/slicezero/callgraph-<sha>.tsv

# 3. dry run -> manifest (committed under docs/slicezero/)
python3 tools/slicezero/rewrite_checkers.py --repo . \
    --gate-reachable docs/slicezero/gate-reachable-<sha>.txt \
    --manifest docs/slicezero/rewrite-manifest-<sha>-dry-run.tsv \
    --summary docs/slicezero/rewrite-summary-<sha>-dry-run.json --quote-search app/src/test

# 4. apply (same command + --apply), rerun step 1 into a fresh dir, compare
#    signature.tsv / per-checker .out; rerun step 3+4: must report applied=0.
```

## What the rewriter guarantees

- Only lines the manifest marks `rewrite` change; each is a single physical
  line whose file token becomes the tree root in the file's own root
  convention (`"$ROOT/app/src/main/java"`, `app/src/main/java`, ...).
  `grep -Fq` becomes `grep -rFq --include='*.kt'`; `rg -Fq` needs no flag.
- Negated checks (`!`, `then fail`, `&& fail`), low-specificity literals
  (>= 10 main files or <= 12 chars), function bodies, heredocs, multi-target
  lines, other flags, and checkers unreachable from a gate are never changed;
  every such line is still listed with its reason.
- Widened assertions are positive leaves under `&&`/`||` only, so the change
  is monotone: a gate can go red -> green (listed as `flip`), never green -> red.
- `--apply` refuses if a target line changed since analysis; applying twice
  is a no-op.
