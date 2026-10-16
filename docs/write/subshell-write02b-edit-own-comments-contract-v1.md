# WRITE02B edit own comments

WRITE02B adds live Reddit editing for comments authored by the active authenticated account.

The own-content action policy remains authoritative. The Edit action is shown only when the active authenticated username owns the comment.

Comment editing uses Reddit `POST /api/editusertext` with `api_type=json`, a canonical `t1_` `thing_id`, and raw markdown `text`.

The edit authorization capability is distinct from ordinary comment submission and requests the text-post write scopes plus Reddit `edit`, preserving the history scope used by submit reconciliation. Read-only scope selection is unchanged.

The editor is an explicit Edit comment mode. It reuses Subshell's Reddit composer editor and Markdown/plain controls but does not expose media attachment actions.

A successful edit updates the active in-memory comment tree immediately and triggers the existing read-only comment refresh path so the canonical Reddit response is written through the established cache flow.

Reddit JSON API failures preserve a bounded provider error code and message through the network, authenticated editor, coordinator, and UI layers. A provider message is shown instead of collapsing every API failure to a generic rejection string.

Comments containing GIFs or other inline Reddit media remain visible as owned content, but selecting Edit is intercepted before any write request. Detection covers structured `inlineMedia`, local media, and raw Reddit placeholders such as `![gif](giphy|...)` or `![img](...)`, because older or incomplete responses may expose the placeholder without populating the structured media field. Restored editor state is guarded by the same policy. The UI explains that Subshell cannot safely preserve Reddit's separately stored rich-media structure during edits yet. Plain-text own comments remain editable.

WRITE02B does not add comment deletion and does not add post editing.
