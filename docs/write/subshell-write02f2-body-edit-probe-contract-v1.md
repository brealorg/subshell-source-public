# WRITE02F2 body edit capability probe

WRITE02F2 is a controlled capability probe. It is not a production claim that Reddit supports editing the body of every post type.

For the active authenticated account, the canonical text/self-post path remains `Edit post`. An owned non-text post exposes `Edit body (test)` only when Reddit already returned a non-empty Markdown body for that post. Other users' posts and non-text posts without a body do not expose the probe action.

The probe reuses the existing WRITE02D public OAuth transport: `POST /api/editusertext` with the post `t3_` fullname. Only the body text is sent. Title, destination link, media assets, gallery contents, community, flair and post kind are not changed by this editor.

No private Reddit endpoint, browser cookie, first-party session or automatic write retry is introduced. The existing non-retrying write transport and server acknowledgement path remain unchanged.

The canonical own-content policy deliberately remains conservative: `OTHER_POST` is still non-editable. The probe uses the already-established own-post delete permission only as an ownership signal for this temporary UI gate. Server-side results from controlled link and media tests determine whether a later production change may broaden the edit policy.

WRITE02F2 also changes the visual SPOILER metadata chip from a tertiary accent to a neutral Material surface tone. NSFW keeps its sensitive/error role and ordinary post flair keeps its existing flair presentation.
