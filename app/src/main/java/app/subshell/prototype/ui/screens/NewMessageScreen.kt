package app.subshell.prototype.ui.screens

// SUBSHELL_MESSAGES02D5B2_FULLSCREEN_M3_EXPRESSIVE_NEW_MESSAGE_V1

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.DirectChatStartPhase
import app.subshell.prototype.data.account.DirectChatStartRequest
import app.subshell.prototype.data.account.DirectChatStartUiState
import app.subshell.prototype.data.account.MESSAGE_REPLY_TEXT_MAX_LENGTH
import app.subshell.prototype.data.account.MessageReplyMediaAttachment
import app.subshell.prototype.model.RedditPeopleSearchItem
import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.ComposerMediaKind
import app.subshell.prototype.ui.NewMessagePrefill
import app.subshell.prototype.ui.components.ComposerDiscardChangesDialog
import app.subshell.prototype.ui.components.ComposerMediaReplacementDialog
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import app.subshell.prototype.ui.components.CompactReplyComposer
import app.subshell.prototype.ui.components.InlineErrorNotice
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.symbols.Symbols
import kotlinx.coroutines.flow.drop
import app.subshell.prototype.ui.components.RemoteIdentityImage
import app.subshell.prototype.ui.components.rememberComposerMediaPicker
import kotlinx.coroutines.delay
import app.subshell.prototype.ui.components.SubshellSearchField
import app.subshell.prototype.ui.components.ListCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    accountStableId: String,
    authenticated: Boolean,
    peopleSearchQuery: String,
    peopleSearchItems: List<RedditPeopleSearchItem>,
    peopleSearchLoading: Boolean,
    peopleSearchLoaded: Boolean,
    directChatStartState: DirectChatStartUiState,
    onSearchPeople: (String) -> Unit,
    onStartDirectChat: (DirectChatStartRequest) -> Unit,
    onResetDirectChatStartState: () -> Unit,
    onRefreshInbox: () -> Unit,
    onBack: () -> Unit,
    interactionActionsAtStart: Boolean = false,
    consumePrefill: () -> NewMessagePrefill? = { null },
    // LOWUI8_COMPOSE_LINK_FILLS_AN_OPEN_NEW_MESSAGE_V1 (OCB-229): a compose
    // link that arrives while New message is open is taken at once; it waited
    // and then filled a later, unrelated message.
    prefillPending: Boolean = false,
) {
    var recipientQuery by rememberSaveable(accountStableId) { mutableStateOf("") }
    // LOWUI6_NEW_MESSAGE_SURVIVES_RECREATION_V1 (OCB-228): the recipient, the
    // first message and its attachment are saved state, so a theme/locale
    // change or a picker round trip does not lose them.
    var selectedRecipient by rememberSaveable(accountStableId, stateSaver = NewMessageRecipientSaver) {
        mutableStateOf<RedditPeopleSearchItem?>(null)
    }
    // COMPOSE01A1_SHARED_EDITOR_V1: the first message uses the same docked
    // composer as a conversation reply (issue #3).
    val editor = rememberSaveable(accountStableId, saver = TextFieldState.Saver) { TextFieldState() }
    val editorText = editor.text.toString()
    var media by rememberSaveable(accountStableId, stateSaver = NewMessageMediaSaver) {
        mutableStateOf<ComposerMediaAttachment?>(null)
    }
    // LOWUI6_NEW_MESSAGE_ASKS_BEFORE_DISCARDING_V1 (OCB-230): Back with a typed
    // message or an attachment asks first, like the reply and edit composers.
    var discardConfirmationVisible by remember(accountStableId) { mutableStateOf(false) }
    var pendingMediaReplacement by remember(accountStableId) {
        mutableStateOf<ComposerMediaAttachment?>(null)
    }
    val composerFocusRequester = remember(accountStableId) { FocusRequester() }
    // REDDITURL04_COMPOSE_LINK_V1: a compose link chose the recipient and
    // wrote the first message; take it once for this account.
    LaunchedEffect(accountStableId, prefillPending) {
        val prefill = consumePrefill() ?: return@LaunchedEffect
        recipientQuery = prefill.recipientUsername
        selectedRecipient = RedditPeopleSearchItem(
            username = prefill.recipientUsername,
            title = prefill.recipientUsername,
            description = "",
        )
        if (prefill.text.isNotEmpty()) editor.setTextAndPlaceCursorAtEnd(prefill.text)
    }

    val normalizedQuery = recipientQuery
        .trim()
        .removePrefix("u/")
        .trim()
    val searchMatches =
        peopleSearchQuery.equals(normalizedQuery, ignoreCase = true)
    val visiblePeople = if (searchMatches) {
        peopleSearchItems.take(8)
    } else {
        emptyList()
    }
    val scopedStartState = directChatStartState.takeIf { state ->
        state.accountStableId == null || state.accountStableId == accountStableId
    } ?: DirectChatStartUiState()
    val startInProgress = scopedStartState.inProgress
    val outcomeUnknown =
        scopedStartState.phase == DirectChatStartPhase.OUTCOME_UNKNOWN
    val handoffPending =
        scopedStartState.phase == DirectChatStartPhase.SUCCEEDED
    val selectedUsername = selectedRecipient?.username
    // SUBSHELL_MESSAGES02D5C_INITIAL_IMAGE_GIF_V1
    val mediaPicker = rememberComposerMediaPicker { next ->
        if (
            !startInProgress &&
            !outcomeUnknown &&
            !handoffPending &&
            editorText.isBlank()
        ) {
            if (media == null) {
                media = next
            } else {
                pendingMediaReplacement = next
            }
            onResetDirectChatStartState()
        }
    }
    // SUBSHELL_COMPOSE01_NEW_MESSAGE_SHARED_COMPOSER_V1
    // Typing clears a settled start outcome, as the predecessor's
    // onValueChange did. The effect outlives recompositions, so it reads the
    // latest start phase; an in-flight, unknown or succeeded (handoff-pending)
    // start is never reset from here.
    val latestStartState by rememberUpdatedState(scopedStartState)
    val latestResetStartState by rememberUpdatedState(onResetDirectChatStartState)
    LaunchedEffect(editor, accountStableId) {
        snapshotFlow { editor.text.toString() }
            .drop(1)
            .collect {
                val current = latestStartState
                val settled =
                    !current.inProgress &&
                        current.phase != DirectChatStartPhase.OUTCOME_UNKNOWN &&
                        current.phase != DirectChatStartPhase.SUCCEEDED
                if (settled) latestResetStartState()
            }
    }

    fun leave() {
        if (editorText.isNotBlank() || media != null) {
            discardConfirmationVisible = true
        } else {
            if (!outcomeUnknown) onResetDirectChatStartState()
            onBack()
        }
    }
    BackHandler {
        if (!startInProgress) leave()
    }
    if (discardConfirmationVisible) {
        ComposerDiscardChangesDialog(
            subject = "message",
            onKeepEditing = { discardConfirmationVisible = false },
            onDiscard = {
                discardConfirmationVisible = false
                if (!outcomeUnknown) onResetDirectChatStartState()
                onBack()
            },
        )
    }

    LaunchedEffect(
        normalizedQuery,
        selectedUsername,
        authenticated,
        accountStableId,
    ) {
        if (
            authenticated &&
            selectedRecipient == null &&
            normalizedQuery.length >= 2
        ) {
            delay(300L)
            onSearchPeople(normalizedQuery)
        }
    }

    LaunchedEffect(selectedUsername) {
        if (selectedUsername == null) {
            media = null
            pendingMediaReplacement = null
        } else {
            delay(80L)
            composerFocusRequester.requestFocus()
        }
    }

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior(ChromeBehavior.ALWAYS_SHOW)
    Scaffold(
        modifier = Modifier.nestedScroll(routeTopBarScroll.nestedScrollConnection),
        topBar = {
            RouteTopBar(
                scrollBehavior = routeTopBarScroll,
                title = {
                    Text(
                        text = "New message",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        enabled = !startInProgress,
                        onClick = { leave() },
                    ) {
                        Icon(
                            Symbols.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // COMPOSER232_COMPOSER_SITS_ON_THE_KEYBOARD_V1 (#232)
                .fitInside(WindowInsetsRulers.Ime.current),
        ) {
            if (selectedRecipient == null) {
                RecipientSearchPhase(
                    query = recipientQuery,
                    onQueryChange = { value ->
                        recipientQuery = value
                        if (!outcomeUnknown && !startInProgress) {
                            onResetDirectChatStartState()
                        }
                    },
                    authenticated = authenticated,
                    searchMatches = searchMatches,
                    loading = peopleSearchLoading,
                    loaded = peopleSearchLoaded,
                    people = visiblePeople,
                    enabled =
                        !startInProgress &&
                            !outcomeUnknown &&
                            !handoffPending,
                    onSelect = { person ->
                        selectedRecipient = person
                        recipientQuery = person.username
                        onResetDirectChatStartState()
                    },
                )
            } else {
                val recipient = selectedRecipient ?: return@Column
                SelectedRecipientSurface(
                    person = recipient,
                    enabled =
                        !startInProgress &&
                            !outcomeUnknown &&
                            !handoffPending,
                    onClear = {
                        selectedRecipient = null
                        onResetDirectChatStartState()
                    },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor =
                                MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Icon(
                                Symbols.Forum,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .size(28.dp),
                            )
                        }
                        Text(
                            text = if (handoffPending) {
                                "Message sent"
                            } else {
                                "Start a conversation"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (handoffPending) {
                                "Opening Direct Chat…"
                            } else {
                                "Your first message starts the Direct Chat request."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                scopedStartState.message
                    ?.takeIf(String::isNotBlank)
                    ?.let { message ->
                        DirectChatStartErrorNotice(
                            message = message,
                            outcomeUnknown = outcomeUnknown,
                            onRefreshInbox = onRefreshInbox,
                        )
                    }

                CompactReplyComposer(
                    state = editor,
                    media = media,
                    placeholder = "Message",
                    sendEnabled =
                        authenticated &&
                            (editorText.isNotBlank() != (media != null)) &&
                            !scopedStartState.blocksSend &&
                            !handoffPending &&
                            !startInProgress,
                    onSend = {
                        onStartDirectChat(
                            DirectChatStartRequest(
                                accountStableId = accountStableId,
                                participantUsername = recipient.username,
                                text = editorText,
                                media = media?.let { attachment ->
                                    MessageReplyMediaAttachment(
                                        uri = attachment.uri,
                                        mimeType = attachment.mimeType,
                                        sourceLinkUri = attachment.sourceLinkUri,
                                    )
                                },
                            ),
                        )
                    },
                    onMediaReceived = { next ->
                        if (
                            !startInProgress &&
                            !outcomeUnknown &&
                            !handoffPending &&
                            editorText.isBlank()
                        ) {
                            if (media == null) media = next else pendingMediaReplacement = next
                            onResetDirectChatStartState()
                        }
                    },
                    onMediaRemoved = {
                        if (!startInProgress && !outcomeUnknown) {
                            media = null
                            pendingMediaReplacement = null
                            onResetDirectChatStartState()
                        }
                    },
                    onPickImage = mediaPicker.pickImage,
                    onPickGif = mediaPicker.pickGif,
                    mediaEnabled =
                        !startInProgress &&
                            !outcomeUnknown &&
                            !handoffPending &&
                            editorText.isBlank(),
                    interactionActionsAtStart = interactionActionsAtStart,
                    focusRequester = composerFocusRequester,
                    maxCharacters = MESSAGE_REPLY_TEXT_MAX_LENGTH,
                    sendContentDescription = if (startInProgress || handoffPending) "Sending message" else "Send message",
                )

                val replacement = pendingMediaReplacement
                if (replacement != null && media != null) {
                    ComposerMediaReplacementDialog(
                        currentMedia = requireNotNull(media),
                        replacementMedia = replacement,
                        onReplace = {
                            media = replacement
                            pendingMediaReplacement = null
                            onResetDirectChatStartState()
                        },
                        onKeepExisting = {
                            pendingMediaReplacement = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipientSearchPhase(
    query: String,
    onQueryChange: (String) -> Unit,
    authenticated: Boolean,
    searchMatches: Boolean,
    loading: Boolean,
    loaded: Boolean,
    people: List<RedditPeopleSearchItem>,
    enabled: Boolean,
    onSelect: (RedditPeopleSearchItem) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            text = "Who do you want to message?",
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 12.dp,
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        SubshellSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = "Search Reddit users",
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = enabled,
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(
                        enabled = enabled,
                        onClick = { onQueryChange("") },
                    ) {
                        Icon(
                            Symbols.Close,
                            contentDescription = "Clear recipient search",
                        )
                    }
                }
            } else {
                null
            },
            supportingText = {
                when {
                    !authenticated ->
                        Text("Connect a Reddit account to use Direct Chat.")
                    query.trim().length == 1 ->
                        Text("Type at least 2 characters.")
                    loading && searchMatches ->
                        Text("Searching Reddit users…")
                    loaded && searchMatches && query.trim().length >= 2 &&
                        people.isEmpty() ->
                        Text("No Reddit users found.")
                }
            },
        )

        Spacer(Modifier.size(8.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        if (query.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Search by username to start a new Direct Chat.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding =
                    androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 10.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = people,
                    key = RedditPeopleSearchItem::username,
                ) { person ->
                    RecipientResult(
                        person = person,
                        enabled = enabled,
                        onClick = { onSelect(person) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipientResult(
    person: RedditPeopleSearchItem,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListCard(
        headline = "u/${person.username}",
        supporting = person.description,
        supportingMaxLines = 2,
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        leading = { RedditPersonAvatar(person = person, size = 48) },
    )
}

@Composable
private fun SelectedRecipientSurface(
    person: RedditPeopleSearchItem,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(
                start = 14.dp,
                top = 10.dp,
                end = 6.dp,
                bottom = 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RedditPersonAvatar(person = person, size = 42)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "u/${person.username}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Direct Chat recipient",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                enabled = enabled,
                onClick = onClear,
            ) {
                Icon(
                    Symbols.Close,
                    contentDescription = "Change recipient",
                )
            }
        }
    }
}

@Composable
private fun RedditPersonAvatar(
    person: RedditPeopleSearchItem,
    size: Int,
) {
    RemoteIdentityImage(
        uri = person.avatarImageUri,
        contentDescription = "Avatar for u/${person.username}",
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape),
    ) {
        Text(
            text = person.username.firstOrNull()
                ?.uppercase()
                ?: "u",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DirectChatStartErrorNotice(
    message: String,
    outcomeUnknown: Boolean,
    onRefreshInbox: () -> Unit,
) {
    // ISSUE222_ONE_INLINE_ERROR_NOTICE_V1
    InlineErrorNotice(
        message = message,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        actionLabel = if (outcomeUnknown) "Refresh Inbox" else null,
        onAction = onRefreshInbox,
    )
}

private val NewMessageRecipientSaver = Saver<RedditPeopleSearchItem?, ArrayList<String>>(
    save = { item ->
        item?.let {
            arrayListOf(it.username, it.title, it.description, it.avatarImageUri.orEmpty(), it.nsfw.toString())
        }
    },
    restore = { fields ->
        RedditPeopleSearchItem(
            username = fields[0],
            title = fields[1],
            description = fields[2],
            avatarImageUri = fields[3].takeIf(String::isNotEmpty),
            nsfw = fields[4].toBoolean(),
        )
    },
)

private val NewMessageMediaSaver = Saver<ComposerMediaAttachment?, ArrayList<String>>(
    save = { media ->
        media?.let {
            arrayListOf(it.uri, it.mimeType, it.kind.name, it.sourceLinkUri.orEmpty(), it.caption, it.outboundUrl)
        }
    },
    restore = { fields ->
        ComposerMediaAttachment(
            uri = fields[0],
            mimeType = fields[1],
            kind = ComposerMediaKind.valueOf(fields[2]),
            sourceLinkUri = fields[3].takeIf(String::isNotEmpty),
            caption = fields[4],
            outboundUrl = fields[5],
        )
    },
)
