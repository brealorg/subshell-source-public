# MESSAGES02E1R2 — Direct Chat reply/reaction shape read probe

E1R2 remains discovery-only. It does not add reply/reaction product behavior or any Matrix write.

## Proven E1 runtime result

Reddit Web exposes **Reply**, **React**, **Link**, and **Delete**, but no message edit action.
Delete is already runtime-proven in D3B.

The first E1 runtime probe proved that Reddit Web Reply is delivered to the existing foreground
Matrix `/sync` reader as:

- event type `m.room.message`;
- relation type `m.thread`;
- a relation target is present;
- `m.in_reply_to` reply target is present;
- message type `m.text`.

The isolated Reddit Web reaction action did not produce an observed `m.reaction` /
`m.annotation` event in E1. Existing reply events were observed again, but no separate reaction
relation appeared.

## E1R2 question

E1R2 therefore broadens only the **read-side structural observation**. In addition to the
existing reply/reaction relation probe, it records one sanitized shape row for every timeline
event so that reaction state aggregated onto an existing event can be detected.

The new runtime tag is:

`subshell-matrix-shape`

It records only:

- sanitized event type;
- sanitized relation type;
- self/other sender classification;
- whether `content.m.relates_to` exists;
- whether relation and reply targets are present;
- sanitized message type;
- whether `unsigned` exists;
- whether `unsigned.m.relations` or `unsigned.relations` exists;
- whether the unsigned relation map contains `m.annotation`;
- whether the unsigned relation map contains `m.thread`;
- whether unsigned/content structural key names contain `reaction` or `annotation`.

It does **not** log room IDs, event IDs, target IDs, message bodies, reaction keys/emoji,
reaction payload values, usernames, Matrix user IDs, or arbitrary JSON values.

The existing `subshell-matrix-relation` probe remains unchanged, including the runtime-proven
`m.thread` reply observation.

The reader remains GET-only. E1R2 adds no Matrix POST/PUT/DELETE, no reply write, no reaction
write, no read receipt, no Seen UI, and no presence behavior.

Existing `m.replace` edit suppression also remains unchanged. Editing is not a Reddit Web
product candidate because Reddit Web exposes no edit action.

## Runtime procedure

1. Open the same Direct Chat in DEV1 and wait for foreground live sync.
2. Clear logcat.
3. Add one new reaction in Reddit Web.
4. Wait 5–10 seconds with the Direct Chat still open.
5. Capture `subshell-matrix-shape`, `subshell-matrix-relation`,
   `subshell-matrix-live`, and existing redaction/ephemeral tags.

E1R2 makes no claim about Reddit's reaction transport until runtime evidence is captured.
