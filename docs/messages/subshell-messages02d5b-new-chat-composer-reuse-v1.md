# MESSAGES02D5B — reuse the real chat composer for New message

D5A proved the authenticated Inbox entry point and recipient search, but its first-message UI was
form-shaped: a large multiline `OutlinedTextField`, explanatory copy, and a `Cancel / Send` button
row. That presentation does not match the established Direct Chat experience.

D5B changes presentation only.

## UI contract

The New message flow now has two phases:

1. **Recipient selection**
   - `New message` plus the existing Reddit people search.
   - Suggestions remain account-scoped and debounced exactly as in D5A.

2. **First-message composition**
   - The recipient search surface disappears after a user is selected.
   - `CompactReplyComposer` is reused directly.
   - Its existing context header renders `To u/<recipient>` and the close affordance returns to
     recipient selection.
   - The editor remains the canonical 1–5 line post-style editor with the shared Markdown
     formatting dock and send icon.
   - The composer sits at the bottom of a bounded sheet instead of presenting a large form field.
   - The first request remains text-only: media actions and rich image receive-content are disabled.
   - There is no separate `Cancel / Send` form row. The sheet itself can be dismissed normally.

D5B does not change Matrix directory lookup, room lookup/projection, `createRoom`, first-message
transport, retry policy, authentication, or cache semantics. The known D5A room-lookup protocol
projection failure remains a separate transport bug to resolve after the composer is approved.

No read receipt / Seen behavior is introduced.
