# WRITE02C delete own comments

WRITE02C adds live Reddit deletion for comments authored by the active authenticated account.

The visible Delete comment action is ownership-gated by the canonical WRITE02A policy and requires explicit confirmation before any network write.

Deletion uses Reddit `POST /api/del` with a canonical `t1_` `id`. It reuses the existing COMMENT_EDIT capability because the Reddit delete endpoint is covered by the same `edit` OAuth grant. No additional OAuth capability or scope is introduced.

The transport uses the existing non-retrying write client. HTTP acknowledgement from `POST /api/del` is not treated as authoritative deletion success. Subshell performs a bounded targeted `GET /api/info?id=<t1 fullname>` readback and reports success only when Reddit returns a deleted placeholder or the target is consistently absent. If the target remains present, Subshell exposes an explicit unconfirmed-outcome state without removing the comment locally. The UI offers Close and Check again; Check again performs a read refresh and never dispatches another delete request.

WRITE02C does not add moderator removal, post deletion, or optimistic permanent removal before Reddit acknowledges the mutation.
