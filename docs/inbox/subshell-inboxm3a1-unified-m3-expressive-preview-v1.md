# INBOXM3A1 — unified M3 Expressive Inbox preview

Presentation-only preview for the shared Inbox workspace at
`main@006a448ee603a40d685f08a5a4d50ae4c04baa05`.

## Included surfaces

- Activity, Messages, and conditional Mod primary navigation use one rounded
  segmented workspace control with canonical unread/attention counts.
- Queue and Modmail use a second segmented control inside the Mod workspace.
- Existing Activity and Messages tonal rows are preserved.
- Modmail conversation rows now use the same rounded tonal-card grammar.
- Modmail thread messages use role-aware containers: ordinary messages,
  hidden moderator replies, and internal notes remain visually distinct.
- Reply and Internal note are explicit segmented composer modes; the existing
  shared `CompactReplyComposer` and reply-identity selector remain authoritative.
- Direct Chat and private-message details receive the same tonal header and
  composer chrome without changing message rendering or write semantics.

## Deliberate boundaries

No ViewModel, transport, persistence, notification, or authorization behavior changes.
No changes to Matrix/Reddit writers, read reconciliation, cache, unread state,
moderation actions, Modmail triage, or notification routing.

`NewMessageScreen` is already the strongest M3 Expressive surface in this
product area and is retained as the visual reference rather than rewritten.
The existing adaptive routing and list/detail policy remain unchanged in this
first preview.

## Manual review

Review Activity, Messages, Mod queue, Modmail list, one Modmail thread, one
Direct Chat, and one private-message thread. Verify row density, badge fit,
mode clarity, dark/light theme behavior, and that all pre-existing actions
still route and mutate exactly as before.

## R1 visual refinement

The first device preview established the shared Inbox direction. R1 deliberately
keeps the same information architecture and narrows the visual treatment:

- primary and secondary workspace destinations always retain their icons;
  unread/attention badges are state layered onto icon identity rather than
  replacing it;
- Queue / Modmail remains segmented but is narrower and less prominent than
  Activity / Messages / Mod;
- internal-note and hidden-moderator message roles use restrained tonal surfaces
  with semantic borders/labels instead of full tertiary/secondary fills;
- the Modmail thread header is one-line-subject compact;
- composer changes are density-only: less vertical padding while retaining the
  exact Reply/Internal-note semantics, reply identity and shared composer.

No behavior, state ownership, routing, transport, persistence or notification
semantics are changed by R1.

<!-- SUBSHELL_INBOXM3A1R1_VISUAL_REFINEMENT_PREVIEW_V1 -->

## A2 unified conversation presentation (rejected preview)

Device review showed that Modmail had become the coherent Inbox conversation
language while Direct Chat/private messages still retained a separate bubble
system. A2 makes presentation converge without changing capabilities:

- Direct Chat and private-message messages use full-width rounded tonal cards;
- sender identity, timestamp and available message actions live in the card header;
- Direct Chat media remains rendered by the existing authoritative/local-media
  projection, but inside the same card as its message identity;
- reaction summaries move into the same card footer rather than floating below;
- outgoing messages use a restrained primary semantic border/label instead of a
  full-width primary-container bubble;
- the conversation list has no bottom content-padding gap before the shared
  `CompactReplyComposer` surface;
- Activity/Mod unread badges keep the R1 icon identity and receive only a small
  visual offset so the badge no longer obscures the icon center.

The Matrix/OAuth transports, live merge, optimistic media continuity, reactions,
delete/redaction policy, typing, drafts, write state, reply semantics and
`CompactReplyComposer` implementation are unchanged.

<!-- SUBSHELL_INBOXM3A2_UNIFIED_CONVERSATION_PRESENTATION_PREVIEW_V1 -->

## A2R1 grouped conversation presentation

The full-width event-card A2 preview was rejected on-device because Direct Chat
became visually heavy and lost conversational rhythm. A2R1 keeps the useful
Inbox/Modmail hierarchy principles without copying the Modmail physical layout:

- consecutive messages from the same sender are grouped within a five-minute window;
- incoming groups show the participant avatar once on the left;
- outgoing groups show the active Reddit account's own avatar once on the right;
- identity and timestamp are group-level rather than repeated for every event;
- each exact Direct Chat event still owns its reaction/delete affordance and reaction footer;
- media uses the existing renderer inside the same sender group, allowing Reddit-parity
  image + following text dual-write events to read as one conversational cluster;
- Direct Chat and private-message groups use bounded width rather than full-width admin cards;
- the A2 seam removal and Inbox badge offset remain preserved.

No Matrix/OAuth transport, ViewModel state, persistence, notification, typing,
reaction, delete/redaction, optimistic-media, draft, or shared composer behavior changes.

<!-- SUBSHELL_INBOXM3A2R1_GROUPED_CONVERSATION_PRESENTATION_PREVIEW_V1 -->

## A2R2 compact grouped conversation refinement

Device review approved the A2R1 grouping model but rejected the remaining
nested-card density. A2R2 is presentation-only:

- the sender group remains the semantic/layout unit but no longer draws its own outer card;
- identity and timestamp sit directly above a compact message stack;
- consecutive message units use connected rounded shapes and 2dp inter-unit spacing;
- incoming and outgoing group avatars use the same 36dp size, mirrored left/right;
- exact per-event actions stay inside their message unit with a smaller 24dp affordance;
- reactions, media, local echo, delete/reaction identity and five-minute grouping are unchanged;
- the Inbox badge keeps stable icon identity and moves farther from the icon center.

No transport, ViewModel, persistence, notification, live-chat, write, reaction,
delete/redaction, draft, composer or grouping-policy behavior changes.

<!-- SUBSHELL_INBOXM3A2R2_COMPACT_GROUP_PRESENTATION_PREVIEW_V1 -->

## A2R2B1 reaction-progress lifecycle hardening

Device review exposed one real Direct Chat behavior bug after the A2R2 presentation was accepted:
a message action could remain as an indefinite reaction progress spinner. The spinner is driven by
`DirectChatLiveUiState.reactingEventId`, not by message/media loading.

This bounded behavior exception to the presentation-only slice:

- gives each reaction attempt a monotonic generation so stale/cancelled completions cannot clear a newer attempt;
- guarantees cancellation cleanup converts any still-latched reaction progress to `OUTCOME_UNKNOWN`;
- cancels and clears the reaction job when Direct Chat live presentation stops;
- keeps the existing no-blind-retry policy and authoritative Matrix reaction hydration;
- does not change grouping, media, composer, Modmail, database, notifications or Reddit Inbox refresh behavior.

<!-- SUBSHELL_INBOXM3A2R2B1_REACTION_LIFECYCLE_HARDENING_V1 -->

<!-- SUBSHELL_INBOXM3A1_UNIFIED_M3_EXPRESSIVE_WORKSPACE_PREVIEW_V1 -->
