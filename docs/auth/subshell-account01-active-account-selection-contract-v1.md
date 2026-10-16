# subshell ACCOUNT01 active-account persistence contract v1

## Scope

ACCOUNT01 keeps `active_account_id` authoritative while maintaining the legacy
`active_account_username` preference as a coherent compatibility mirror.

## Required behavior

- A valid persisted stable ID wins over legacy username state.
- An invalid persisted stable ID falls back to the active account selected by
  the authenticated account directory.
- Legacy username fallback may resolve either local or authenticated accounts
  when no usable stable ID exists.
- Startup repairs a stale legacy username and writes the stable ID and username
  as one committed preference pair.
- Every later account switch persists the same stable-ID/username pair.
- The active authenticated account remains pinned by stable ID for comment and
  future post writes.

## Isolation

- No Reddit request is required.
- No comment or post write is performed.
- No UI redesign is included.
- All user-facing app strings remain English.
- The legacy username preference is compatibility metadata, not account
  authority.
