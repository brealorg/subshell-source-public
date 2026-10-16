# WRITE02D edit own self-post body

WRITE02D adds live Reddit body editing for self-posts authored by the active authenticated account.

The visible Edit post action is ownership-gated by the canonical WRITE02A policy and is exposed only for `PostKind.TEXT`. Link, image, gallery, video, GIF, crosspost and poll posts do not gain an edit action.

Editing uses Reddit `POST /api/editusertext` with `api_type=json`, `raw_json=1`, a canonical `t3_` `thing_id`, and the edited self-post body as `text`.

WRITE02D reuses the existing COMMENT_EDIT capability and `edit` grant. It does not introduce another OAuth capability or broaden the requested scope set.

The transport uses the existing non-retrying write client. After Reddit acknowledges the edit, the currently routed post snapshot is reconciled to the new body. Normal feed refresh remains responsible for obtaining fresh remote listing data later.

WRITE02D does not edit the title, community, flair, destination link, media, post type, or any post authored by another user. It does not add post deletion.
