# CONTRACT02E — beta.2 release-scope reference manifest

CONTRACT02E extends the Contract02 native execution model to the four
source contracts that formed the focused beta.2 release qualification
scope.

This is a workflow/reference migration. It does not re-release beta.2.

## Native scope

The manifest contains:

```text
auth07c1       primary    -
auth07c2       primary    auth07c1
modmail07a1    primary    -
next01a5       primary    -
```

AUTH07C1/AUTH07C2 were migrated in CONTRACT02C.

The CONTRACT02E probe established that MODMAIL07A1 and NEXT01A5 contain
no recursive contract execution and no predecessor/regression gates.
They already consist of their own assertions only.

No predecessor is invented for MODMAIL07A1 or NEXT01A5.

## Native result protocol

MODMAIL07A1 and NEXT01A5 gain a conditional EXIT protocol that emits:

```text
CONTRACT02_PRIMARY_RESULT=PASS
CONTRACT02_PRIMARY_RESULT=FAIL
```

only when `SUBSHELL_CONTRACT02_RUNNER_ACTIVE=1`.

The EXIT protocol preserves the contract's original exit status.
Legacy direct invocation therefore retains its existing output and
success/failure behavior.

## Run-once proof

The preview records actual non-interactive Bash execution through an
isolated `BASH_ENV` trace. The four contracts execute exactly once:

- AUTH07C1
- AUTH07C2
- MODMAIL07A1
- NEXT01A5

AUTH07C2 obtains AUTH07C1 predecessor state from the Contract02 cache and
does not recursively execute it.

## Release-gate meaning

Every row has `block_mode=primary`.

A contract's own assertion failure is release-blocking. A successor may
also show a predecessor failure in its cached overall status, but that
does not create another release-blocking count unless the manifest
explicitly uses `block_mode=any`.

This file is a historical reference manifest for the beta.2 focused
qualification shape and a template for future delta-release manifests.
It is not a claim that future releases always contain these exact four
contracts.
