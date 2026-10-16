# MESSAGES02D2A — inbound Direct Chat typing UI

D2A promotes the D1 runtime-proven Reddit Matrix `m.typing` signal to user-visible Direct Chat UI.

- C7/D1 foreground `/sync` forwards explicit peer typing start/stop.
- `DirectChatLiveUiState` owns transient `peerTyping`.
- State remains scoped to the active account, inbox item and participant.
- Stream stop/failure clears typing.
- An incoming peer message defensively clears stale typing.
- Direct Chat renders `u/<participant> is typing…` immediately above the conversation composer.
- Private-message threads never render Matrix typing state.

D2A performs no Matrix writes. It adds no typing write endpoint, no read-receipt write, no "Seen"
UI, no presence, and no persistence.
