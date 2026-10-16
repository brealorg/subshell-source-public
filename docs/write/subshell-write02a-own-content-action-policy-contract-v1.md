# WRITE02A own-content action policy

WRITE02A establishes one canonical ownership and allowed-action policy before edit/delete transport is introduced.

Ownership is derived only from the active authenticated Reddit username and the content author's username. Matching is trimmed, case-insensitive, accepts Reddit `u/` and `/u/` prefixes, and fails closed for missing, malformed, or deleted identities.

An owned comment exposes Edit and Delete.

An owned self-post exposes Edit body and Delete. WRITE02A does not treat post title, destination link, media, community, or author as editable.

An owned non-self post exposes Delete but not Edit.

Content authored by another account exposes neither own-content action.

WRITE02A does not perform network writes, does not add edit/delete endpoints, does not mutate Room, and does not change visible action menus. Transport and UI wiring are successor milestones.
