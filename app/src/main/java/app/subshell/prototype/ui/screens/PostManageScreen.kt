package app.subshell.prototype.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageOperation
import app.subshell.prototype.data.account.PostManagePhase
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.account.PostManageUiState
import app.subshell.prototype.data.account.normalizePostEditFullname
import app.subshell.prototype.data.account.normalizePostManageCommunity
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.ComposerFlair
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.ErrorNotice
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.postManageErrorPresentation
import app.subshell.prototype.ui.components.InlineProgressIndicator
import app.subshell.prototype.ui.symbols.Symbols
import app.subshell.prototype.ui.app.predictiveBackPage
import app.subshell.prototype.ui.app.rememberPredictiveBackPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostManageScreen(
    post: Post,
    state: SubshellState,
    manageState: PostManageUiState,
    onLoad: (PostManageTarget) -> Unit,
    onMutate: (PostManageMutationRequest) -> Unit,
    onAuthorize: () -> Unit,
    onCancelAuthorization: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = remember(
        post.id,
        state.activeAccountId,
        post.author,
        post.community,
    ) {
        PostManageTarget(
            accountStableId = state.activeAccountId,
            postFullname = normalizePostEditFullname(post.id),
            postAuthor = post.author,
            community = normalizePostManageCommunity(post.community),
        )
    }
    val scopedState = manageState.takeIf {
        it.matches(target.accountStableId, target.postFullname)
    } ?: PostManageUiState()
    val snapshot = scopedState.snapshot
    var flairPickerVisible by rememberSaveable(post.id) {
        mutableStateOf(false)
    }

    LaunchedEffect(target) {
        // LOWUI8_MANAGE_ENTRY_KEEPS_A_CHANGE_WAITING_FOR_AUTHORIZATION_V1
        // (OCB-232): a recreation enters again; a change of this post that
        // waits for authorization is kept (load() keeps it too, LOW9).
        if (scopedState.phase != PostManagePhase.AUTHORIZATION_REQUIRED) onReset()
        onLoad(target)
    }

    LaunchedEffect(
        scopedState.phase,
        scopedState.operation,
        scopedState.completionRevision,
    ) {
        if (scopedState.phase != PostManagePhase.SUCCEEDED) return@LaunchedEffect
        if (scopedState.operation == PostManageOperation.FLAIR_ACCESS) {
            onLoad(target)
            return@LaunchedEffect
        }
        scopedState.snapshot?.let { updated ->
            state.applyManagedPostMetadata(
                postId = post.id,
                flairText = updated.flairText,
                nsfw = updated.nsfw,
                spoiler = updated.spoiler,
            )
        }
    }

    // ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1: Back and the arrow used to do
    // nothing while loading or waiting for the browser (no timeout). Leaving
    // cancels the authorization; only saving on Reddit keeps the screen.
    val closeLocked = postManageCloseLocked(scopedState.phase)
    // ISSUE249_IN_SCREEN_PAGES_FOLLOW_THE_BACK_GESTURE_V1: leaving follows the
    // back gesture; while a save locks the screen, Back is swallowed as before.
    val pageBack = rememberPredictiveBackPage(enabled = !closeLocked) {
        onCancelAuthorization()
        onReset()
        onClose()
    }
    BackHandler(enabled = closeLocked) {}

    if (scopedState.phase == PostManagePhase.AUTHORIZATION_REQUIRED) {
        AlertDialog(
            onDismissRequest = onCancelAuthorization,
            title = { Text("Permission required") },
            text = {
                Text(postManageAuthorizationMessage(scopedState.operation))
            },
            confirmButton = {
                TextButton(onClick = onAuthorize) {
                    Text("Authorize")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelAuthorization) {
                    Text("Cancel")
                }
            },
        )
    }

    if (flairPickerVisible && snapshot != null) {
        val options = snapshot.flairOptions.map { option ->
            ComposerFlair(
                templateId = option.templateId,
                text = option.text,
                editable = option.editable,
            )
        }
        val selected = snapshot.flairTemplateId
            ?.let { templateId ->
                options.firstOrNull { option ->
                    option.templateId == templateId
                }?.copy(text = snapshot.flairText.orEmpty())
            }
            ?: snapshot.flairText
                ?.let { currentText ->
                    options.singleOrNull { option ->
                        option.text == currentText
                    }
                }
        PostFlairPickerSheet(
            required = false,
            options = options,
            selected = selected,
            onSelect = { option ->
                flairPickerVisible = false
                onMutate(
                    PostManageMutationRequest(
                        target = target,
                        operation = PostManageOperation.SET_FLAIR,
                        flairTemplateId = option.templateId,
                        flairText = option.text,
                    ),
                )
            },
            onDismiss = { flairPickerVisible = false },
            onRemove = {
                flairPickerVisible = false
                onMutate(
                    PostManageMutationRequest(
                        target = target,
                        operation = PostManageOperation.SET_FLAIR,
                    ),
                )
            },
        )
    }

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior(ChromeBehavior.ALWAYS_SHOW)
    Scaffold(
        modifier = modifier.predictiveBackPage(pageBack).fillMaxSize()
            .nestedScroll(routeTopBarScroll.nestedScrollConnection),
        topBar = {
            RouteTopBar(
                scrollBehavior = routeTopBarScroll,
                title = { Text("Manage post") },
                navigationIcon = {
                    IconButton(
                        enabled = !closeLocked,
                        onClick = {
                            onCancelAuthorization()
                            onReset()
                            onClose()
                        },
                    ) {
                        Icon(
                            Symbols.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = post.title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
            )

            when {
                scopedState.phase == PostManagePhase.LOADING -> {
                    Row(modifier = Modifier.padding(16.dp)) {
                        InlineProgressIndicator()
                        Spacer(Modifier.size(12.dp))
                        Text("Loading current Reddit settings…")
                    }
                }
                scopedState.phase == PostManagePhase.FAILED && snapshot == null -> {
                    ErrorNotice(
                        presentation = postManageErrorPresentation(scopedState),
                        diagnosticScopeKey = "post_manage",
                        modifier = Modifier.padding(16.dp),
                        onAction = { action ->
                            if (action == ErrorActionKind.RETRY) onLoad(target)
                        },
                    )
                }
                snapshot != null -> {
                    val controlsEnabled = !scopedState.inProgress
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        content = { Text("Post flair") },
                        supportingContent = { Text(snapshot.flairText ?: "No flair") },
                        trailingContent = {
                            TextButton(
                                enabled = controlsEnabled,
                                onClick = {
                                    if (snapshot.flairAuthorizationRequired) {
                                        onMutate(
                                            PostManageMutationRequest(
                                                target = target,
                                                operation = PostManageOperation.FLAIR_ACCESS,
                                            ),
                                        )
                                    } else {
                                        flairPickerVisible = true
                                    }
                                },
                            ) {
                                Text(
                                    if (snapshot.flairAuthorizationRequired) {
                                        "Grant access"
                                    } else {
                                        "Change"
                                    },
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                    // LOWUI7_SWITCH_ROWS_ARE_LABELLED_V1 (OCB-234): the row is the switch.
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(
                            value = snapshot.nsfw,
                            enabled = controlsEnabled,
                            role = Role.Switch,
                            onValueChange = { enabled ->
                                onMutate(
                                    PostManageMutationRequest(
                                        target = target,
                                        operation = PostManageOperation.SET_NSFW,
                                        enabled = enabled,
                                    ),
                                )
                            },
                        ),
                        content = { Text("NSFW") },
                        supportingContent = {
                            Text("Mark this post as not safe for work")
                        },
                        trailingContent = {
                            Switch(
                                checked = snapshot.nsfw,
                                enabled = controlsEnabled,
                                onCheckedChange = null,
                            )
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(
                            value = snapshot.spoiler,
                            enabled = controlsEnabled,
                            role = Role.Switch,
                            onValueChange = { enabled ->
                                onMutate(
                                    PostManageMutationRequest(
                                        target = target,
                                        operation = PostManageOperation.SET_SPOILER,
                                        enabled = enabled,
                                    ),
                                )
                            },
                        ),
                        content = { Text("Spoiler") },
                        supportingContent = {
                            Text("Hide previews behind a spoiler warning")
                        },
                        trailingContent = {
                            Switch(
                                checked = snapshot.spoiler,
                                enabled = controlsEnabled,
                                onCheckedChange = null,
                            )
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(
                            value = snapshot.sendReplies,
                            enabled = controlsEnabled,
                            role = Role.Switch,
                            onValueChange = { enabled ->
                                onMutate(
                                    PostManageMutationRequest(
                                        target = target,
                                        operation = PostManageOperation.SET_SEND_REPLIES,
                                        enabled = enabled,
                                    ),
                                )
                            },
                        ),
                        content = { Text("Reply notifications") },
                        supportingContent = {
                            Text("Receive Reddit inbox replies for this post")
                        },
                        trailingContent = {
                            Switch(
                                checked = snapshot.sendReplies,
                                enabled = controlsEnabled,
                                onCheckedChange = null,
                            )
                        },
                    )
                    if (post.kind != PostKind.TEXT) {
                        HorizontalDivider()
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            content = { Text("Published media") },
                            supportingContent = {
                                Text(
                                    "Reddit does not expose a supported operation to replace " +
                                        "the published media asset. Metadata above can still be changed.",
                                )
                            },
                        )
                    }
                    if (
                        scopedState.phase in setOf(
                            PostManagePhase.SENDING,
                            PostManagePhase.AUTHORIZING,
                            PostManagePhase.AWAITING_CALLBACK,
                        )
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            InlineProgressIndicator()
                            Spacer(Modifier.size(12.dp))
                            Text(
                                if (scopedState.phase == PostManagePhase.SENDING) {
                                    "Saving on Reddit…"
                                } else {
                                    "Authorizing…"
                                },
                            )
                        }
                        if (scopedState.phase != PostManagePhase.SENDING) {
                            TextButton(
                                onClick = onCancelAuthorization,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                Text("Cancel authorization")
                            }
                        }
                    }
                    if (scopedState.phase == PostManagePhase.FAILED) {
                        ErrorNotice(
                            presentation = postManageErrorPresentation(scopedState),
                            diagnosticScopeKey = "post_manage",
                            modifier = Modifier.padding(16.dp),
                            onAction = {},
                        )
                    }
                }
            }
        }
    }
}

private fun postManageAuthorizationMessage(
    operation: PostManageOperation?,
): String = when (operation) {
    PostManageOperation.FLAIR_ACCESS,
    PostManageOperation.SET_FLAIR,
    -> "Reddit's flair permission is required to change post flair."
    PostManageOperation.SET_NSFW,
    PostManageOperation.SET_SPOILER,
    -> "Reddit's post-moderation permission is required by this endpoint. " +
        "subshell requests it only when you change NSFW or spoiler status."
    PostManageOperation.SET_SEND_REPLIES ->
        "Reddit's edit permission is required to change reply notifications."
    null -> "Reddit requires additional permission for this change."
}


// ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1: only saving on Reddit keeps
// Manage post open; loading and authorizing can be left.
internal fun postManageCloseLocked(phase: PostManagePhase): Boolean =
    phase == PostManagePhase.SENDING
