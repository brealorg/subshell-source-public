package io.github.brealorg.subshell.ui.screens

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.input.clearText
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.ui.RedditLinkHandlingState
import io.github.brealorg.subshell.ui.RedditLinkHandlingStatus
import io.github.brealorg.subshell.auth.RedditCompatibilityConfigurationController
import io.github.brealorg.subshell.data.account.ConnectAccountPhase
import io.github.brealorg.subshell.data.account.ConnectAccountUiState
import io.github.brealorg.subshell.ui.symbols.Symbols
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.brealorg.subshell.ui.components.InlineProgressIndicator
import io.github.brealorg.subshell.ui.app.predictiveBackPage
import io.github.brealorg.subshell.ui.app.rememberPredictiveBackPage

internal enum class RedditOnboardingEntry {
    CHECKING,
    SETUP,
    READY,
}

// LOWUI4_ONBOARDING_ENTRY_WITHOUT_ACCOUNT_BRANCH_V1 (OCB-246): the screen is
// only shown without an authenticated account (signed in returns earlier), so
// the entry no longer takes an account flag or answers NONE.
internal fun resolveRedditOnboardingEntry(
    configurationKnown: Boolean,
    configured: Boolean,
): RedditOnboardingEntry = when {
    !configurationKnown ->
        RedditOnboardingEntry.CHECKING

    configured ->
        RedditOnboardingEntry.READY

    else ->
        RedditOnboardingEntry.SETUP
}

/**
 * AUTH08A1_LOGOUT_RESETS_CONNECT_FLOW_V1: which of the two controls the ready
 * panel shows for a connect phase. CONNECTED belongs to neither the working
 * group (ConnectAccountUiState.inProgress excludes it, and this panel only
 * renders while no account is on screen, so a CONNECTED phase here is either
 * the last frame of a finished connect or state left behind by a logout) nor
 * to any other action than connecting again — the same mapping
 * AccountSwitcherPresentation uses.
 */
internal fun redditOnboardingConnectPhaseIsWorking(phase: ConnectAccountPhase): Boolean =
    phase == ConnectAccountPhase.LAUNCHING ||
        phase == ConnectAccountPhase.EXCHANGING ||
        phase == ConnectAccountPhase.RESOLVING_IDENTITY ||
        phase == ConnectAccountPhase.SYNCING_REDDIT_FEED

internal fun redditOnboardingConnectPhaseOffersConnect(phase: ConnectAccountPhase): Boolean =
    phase == ConnectAccountPhase.IDLE || phase == ConnectAccountPhase.CONNECTED

private enum class RedditOnboardingStep {
    INTRO,
    GUIDE,
    CLIENT_ID,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RedditOnboardingScreen(
    authenticatedAccountUsername: String?,
    connectAccountState: ConnectAccountUiState,
    onConnectAccount: () -> Unit,
    onCancelConnectAccount: () -> Unit,
    onResetConnectAccount: () -> Unit,
    onRedditConfigurationChanged: () -> Unit,
    linkHandlingStepPending: Boolean = false,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (authenticatedAccountUsername != null) {
        RedditOnboardingConnected(
            username = authenticatedAccountUsername,
            linkHandlingStepPending = linkHandlingStepPending,
            onFinish = onFinish,
            modifier = modifier,
        )
        return
    }

    val appContext =
        LocalContext.current.applicationContext

    val configurationController =
        remember(appContext) {
            RedditCompatibilityConfigurationController(
                appContext,
            )
        }

    var configurationKnown by
        remember { mutableStateOf(false) }

    var configured by
        remember { mutableStateOf(false) }

    var configurationReadFailed by
        remember { mutableStateOf(false) }

    var configurationReadRevision by
        remember { mutableStateOf(0) }

    var stepName by rememberSaveable {
        mutableStateOf(
            RedditOnboardingStep.INTRO.name,
        )
    }

    val step =
        RedditOnboardingStep.valueOf(stepName)

    LaunchedEffect(
        appContext,
        configurationReadRevision,
    ) {
        configurationKnown = false
        configurationReadFailed = false

        val result = runCatching {
            withContext(Dispatchers.IO) {
                configurationController.isConfigured()
            }
        }

        configured = result.getOrDefault(false)
        configurationReadFailed = result.isFailure
        configurationKnown = true
    }

    val entry = resolveRedditOnboardingEntry(
        configurationKnown = configurationKnown,
        configured = configured,
    )

    // ISSUE249_IN_SCREEN_PAGES_FOLLOW_THE_BACK_GESTURE_V1: a step follows the
    // back gesture back to the introduction.
    val pageBack = rememberPredictiveBackPage(enabled = step != RedditOnboardingStep.INTRO) {
        stepName = RedditOnboardingStep.INTRO.name
    }

    Scaffold(
        modifier = modifier.predictiveBackPage(pageBack).fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    ProductTopBarTitle(
                        text = "subshell",
                    )
                },
                navigationIcon = {
                    if (
                        step !=
                        RedditOnboardingStep.INTRO
                    ) {
                        IconButton(
                            onClick = {
                                stepName =
                                    RedditOnboardingStep
                                        .INTRO
                                        .name
                            },
                        ) {
                            Icon(
                                Symbols.ArrowBack,
                                contentDescription =
                                    stringResource(R.string.all_back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(
                    rememberScrollState(),
                )
                .padding(
                    horizontal = 20.dp,
                    vertical = 18.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(18.dp),
        ) {
            when {
                !configurationKnown -> {
                    RedditOnboardingLoading()
                }

                configurationReadFailed -> {
                    RedditOnboardingConfigurationFailure(
                        onRetry = {
                            configurationReadRevision += 1
                        },
                    )
                }

                entry ==
                    RedditOnboardingEntry.READY &&
                    step ==
                    RedditOnboardingStep.INTRO -> {
                    RedditOnboardingReady(
                        connectAccountState =
                            connectAccountState,
                        onConnectAccount =
                            onConnectAccount,
                        onCancelConnectAccount =
                            onCancelConnectAccount,
                        onResetConnectAccount =
                            onResetConnectAccount,
                        onReplaceClientId = {
                            stepName =
                                RedditOnboardingStep
                                    .CLIENT_ID
                                    .name
                        },
                        onShowGuide = {
                            stepName =
                                RedditOnboardingStep
                                    .GUIDE
                                    .name
                        },
                    )
                }

                step ==
                    RedditOnboardingStep.GUIDE -> {
                    RedditClientIdGuide(
                        googlePlayUri =
                            configurationController
                                .redReaderGooglePlayUri,
                        fdroidUri =
                            configurationController
                                .redReaderFdroidUri,
                        onContinue = {
                            stepName =
                                RedditOnboardingStep
                                    .CLIENT_ID
                                    .name
                        },
                    )
                }

                step ==
                    RedditOnboardingStep.CLIENT_ID -> {
                    RedditClientIdEntry(
                        configurationController =
                            configurationController,
                        onSaved =
                            onRedditConfigurationChanged,
                    )
                }

                else -> {
                    RedditOnboardingIntro(
                        onGetClientId = {
                            stepName =
                                RedditOnboardingStep
                                    .GUIDE
                                    .name
                        },
                        onAlreadyHaveClientId = {
                            stepName =
                                RedditOnboardingStep
                                    .CLIENT_ID
                                    .name
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RedditOnboardingLoading() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator()

        Text(
            stringResource(R.string.all_checking_reddit_connection_setup),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )
    }
}

@Composable
private fun RedditOnboardingConfigurationFailure(
    onRetry: () -> Unit,
) {
    Text(
        stringResource(R.string.all_couldn_t_read_reddit_setup),
        style =
            MaterialTheme.typography
                .headlineSmall,
    )

    Text(
        stringResource(R.string.all_nothing_was_changed_try_reading_the_encrypted),
        color =
            MaterialTheme.colorScheme
                .onSurfaceVariant,
    )

    Button(
        onClick = onRetry,
    ) {
        Text(stringResource(R.string.scr_try_again))
    }
}

@Composable
private fun ProductTopBarTitle(
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter =
                painterResource(
                    R.drawable.ic_subshell_in_app,
                ),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )

        Spacer(Modifier.width(10.dp))

        Text(text)
    }
}

@Composable
private fun RedditOnboardingIntro(
    onGetClientId: () -> Unit,
    onAlreadyHaveClientId: () -> Unit,
) {
    Text(
        stringResource(R.string.all_connect_subshell_to_reddit),
        style =
            MaterialTheme.typography
                .headlineMedium,
        fontWeight = FontWeight.SemiBold,
    )

    Text(
        stringResource(R.string.scr_reddit_sign_in_cannot_start_until),
        color =
            MaterialTheme.colorScheme
                .onSurfaceVariant,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme
                .surfaceContainerHigh,
        contentColor =
            MaterialTheme.colorScheme
                .onSurface,
    ) {
        // M3225_SETUP_CARD_IS_NOT_AN_ERROR_V1 (owner 2026-10-13, settings-09): a
        // setup step before sign-in is not a failure, so the card has no error
        // border, icon or title colour.
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector =
                        Symbols.VpnKey,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint =
                        MaterialTheme.colorScheme
                            .primary,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.scr_setup_required_before_sign_in),
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                    )

                    Text(
                        stringResource(R.string.scr_subshell_cannot_create_this_client_id),
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )
                }
            }

            Text(
                stringResource(R.string.scr_1_open_redreader_and_sign_in),
            )

            Text(
                stringResource(R.string.scr_2_open_reddit_s_authorization_email),
            )

            Text(
                stringResource(R.string.scr_3_paste_that_app_id_into),
            )
        }
    }

    Button(
        onClick = onGetClientId,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.scr_show_me_how_to_get_a_client))
    }

    OutlinedButton(
        onClick = onAlreadyHaveClientId,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.scr_i_already_have_a_client_id))
    }

    Text(
        stringResource(R.string.scr_your_client_id_is_kept_in),
        style =
            MaterialTheme.typography.bodySmall,
        color =
            MaterialTheme.colorScheme
                .onSurfaceVariant,
    )
}

@Composable
private fun RedditClientIdGuide(
    googlePlayUri: String,
    fdroidUri: String,
    onContinue: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Text(
        stringResource(R.string.all_get_your_client_id),
        style =
            MaterialTheme.typography
                .headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )

    SetupStep(
        number = "1",
        title = stringResource(R.string.scr_install_or_open_redreader),
        body =
            stringResource(R.string.all_redreader_is_used_once_to_create_the),
    )

    // LOWUX1_STORE_BADGE_FAILURE_IS_SAID_V1 (OCB-247): with no store or
    // browser to take the link, a tap did nothing at all.
    var storeOpenFailed by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        StoreBadgeButton(
            badgeRes =
                R.drawable.google_play_badge,
            contentDescription =
                stringResource(R.string.all_get_it_on_google_play),
            onClick = {
                storeOpenFailed = runCatching {
                    uriHandler.openUri(
                        googlePlayUri,
                    )
                }.isFailure
            },
            modifier = Modifier.weight(1f),
        )

        StoreBadgeButton(
            badgeRes =
                R.drawable.fdroid_badge,
            contentDescription =
                stringResource(R.string.all_get_it_on_f_droid),
            onClick = {
                storeOpenFailed = runCatching {
                    uriHandler.openUri(
                        fdroidUri,
                    )
                }.isFailure
            },
            modifier = Modifier.weight(1f),
        )
    }
    if (storeOpenFailed) {
        Text(
            stringResource(R.string.all_couldn_t_open_a_store_on_this),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    SetupStep(
        number = "2",
        title = stringResource(R.string.scr_sign_in_to_reddit_in_redreader),
        body =
            stringResource(R.string.scr_complete_redreader_s_normal_reddit_sign),
    )

    SetupStep(
        number = "3",
        title = stringResource(R.string.scr_open_reddit_s_authorization_email),
        body =
            stringResource(R.string.all_find_the_line_labeled_app_id_the),
    )

    RedditAuthorizationEmailExample()

    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.scr_i_found_my_client_id))
    }
}

@Composable
private fun StoreBadgeButton(
    badgeRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(62.dp),
    ) {
        Image(
            painter =
                painterResource(badgeRes),
            contentDescription =
                contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
        )
    }
}

@Composable
private fun RedditAuthorizationEmailExample() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme
                .surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.scr_example_only_not_a_real_email),
                style =
                    MaterialTheme.typography
                        .labelLarge,
                color =
                    MaterialTheme.colorScheme
                        .primary,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Text(
                stringResource(R.string.all_what_to_look_for_in_reddit_s),
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Text(
                stringResource(R.string.all_the_real_reddit_email_contains_these_fields),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            )

            Surface(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    MaterialTheme.shapes.medium,
                color =
                    MaterialTheme.colorScheme
                        .surfaceContainerHigh,
            ) {
                Column(
                    modifier =
                        Modifier.padding(14.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.all_app_name),
                        style =
                            MaterialTheme.typography
                                .labelMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )

                    Text(
                        stringResource(R.string.all_redreader_release),
                        fontWeight =
                            FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        stringResource(R.string.all_app_id),
                        style =
                            MaterialTheme.typography
                                .labelMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )

                    Text(
                        stringResource(R.string.scr_your_client_id_appears_here),
                        color =
                            MaterialTheme.colorScheme
                                .primary,
                        fontWeight =
                            FontWeight.SemiBold,
                    )
                }
            }

            Text(
                stringResource(R.string.all_copy_only_the_value_shown_after_app),
                style =
                    MaterialTheme.typography
                        .bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupStep(
    number: String,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape =
                MaterialTheme.shapes.medium,
            color =
                MaterialTheme.colorScheme
                    .primaryContainer,
        ) {
            Text(
                number,
                modifier = Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 6.dp,
                ),
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme
                        .onPrimaryContainer,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style =
                    MaterialTheme.typography
                        .titleMedium,
            )

            Text(
                body,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RedditClientIdEntry(
    configurationController:
        RedditCompatibilityConfigurationController,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // LOWUI3_CLIENT_ID_DRAFT_NOT_IN_SAVED_STATE_V1 (OCB-248): the Client ID
    // being typed stays in memory only, not in the activity's saved state.
    // COMPOSE113_TEXT_FIELDS_USE_TEXT_FIELD_STATE_V1 (1.0.5 tool update): M3's
    // OutlinedSecureTextField over a TextFieldState, still not saved state (plain remember).
    val draftState = remember { androidx.compose.foundation.text.input.TextFieldState() }
    val draft = draftState.text.toString()

    var visible by rememberSaveable {
        mutableStateOf(false)
    }

    var advanced by rememberSaveable {
        mutableStateOf(false)
    }

    var busy by remember {
        mutableStateOf(false)
    }

    var failureMessage by remember {
        mutableStateOf<String?>(null)
    }
    val couldNotSaveClientId = stringResource(R.string.scr_subshell_couldn_t_save_the_client)

    // ISSUE240_ONBOARDING_EDITS_THE_REDDIT_API_VALUES_V1: a fresh install can
    // sign in even after Reddit or RedReader changed these; saved with the ID.
    var redirectUriDraft by remember {
        mutableStateOf(configurationController.compatibilityRedirectUri)
    }

    var userAgentDraft by remember {
        mutableStateOf(configurationController.compatibilityUserAgent)
    }

    val normalizedDraft = draft.trim()

    val valid =
        configurationController.isValidClientId(
            normalizedDraft,
        )

    val apiValuesValid =
        redirectUriError(redirectUriDraft) == null &&
            userAgentError(userAgentDraft) == null

    Icon(
        Symbols.Lock,
        contentDescription = null,
        modifier = Modifier.size(38.dp),
        tint = MaterialTheme.colorScheme.primary,
    )

    Text(
        stringResource(R.string.all_paste_your_client_id),
        style =
            MaterialTheme.typography
                .headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )

    Text(
        stringResource(R.string.all_paste_only_the_app_id_value_from),
        color =
            MaterialTheme.colorScheme
                .onSurfaceVariant,
    )

    // Typing clears the last save failure, as the old onValueChange did.
    LaunchedEffect(draftState) {
        androidx.compose.runtime.snapshotFlow { draftState.text.toString() }
            .drop(1)
            .collect { failureMessage = null }
    }
    androidx.compose.material3.OutlinedSecureTextField(
        state = draftState,
        // A pasted line break becomes a space, as before.
        inputTransformation = {
            val text = asCharSequence().toString()
            if (text.any { it == '\n' || it == '\r' }) {
                replace(0, length, text.replace('\n', ' ').replace('\r', ' '))
            }
        },
        textObfuscationMode = if (visible) {
            androidx.compose.foundation.text.input.TextObfuscationMode.Visible
        } else {
            androidx.compose.foundation.text.input.TextObfuscationMode.RevealLastTyped
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        label = {
            Text(stringResource(R.string.scr_client_id))
        },
        trailingIcon = {
            IconButton(
                onClick = {
                    visible = !visible
                },
                enabled = !busy,
            ) {
                Icon(
                    if (visible) {
                        Symbols.VisibilityOff
                    } else {
                        Symbols.Visibility
                    },
                    contentDescription =
                        if (visible) {
                            stringResource(R.string.all_hide_client_id)
                        } else {
                            stringResource(R.string.all_show_client_id)
                        },
                )
            }
        },
        isError =
            draft.isNotBlank() && !valid,
        supportingText = {
            when {
                failureMessage != null ->
                    Text(
                        requireNotNull(
                            failureMessage,
                        ),
                    )

                draft.isNotBlank() && !valid ->
                    Text(
                        stringResource(R.string.all_that_doesn_t_look_like_a_valid),
                    )

                else ->
                    Text(
                        stringResource(R.string.scr_stored_locally_in_encrypted_no_backup),
                    )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType =
                KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
    )

    TextButton(
        onClick = {
            advanced = !advanced
        },
        enabled = !busy,
    ) {
        Text(
            if (advanced) {
                stringResource(R.string.all_hide_advanced)
            } else {
                stringResource(R.string.all_advanced)
            },
        )
    }

    if (advanced) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color =
                MaterialTheme.colorScheme
                    .surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(15.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.all_reddit_api_values),
                    style =
                        MaterialTheme.typography
                            .titleSmall,
                )

                Text(
                    stringResource(R.string.scr_only_change_these_if_sign_in),
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                )

                ApiValueField(
                    label = stringResource(R.string.scr_redirect_uri),
                    value = redirectUriDraft,
                    error = redirectUriError(redirectUriDraft),
                    enabled = !busy,
                    keyboardType = KeyboardType.Uri,
                    onValueChange = { value -> redirectUriDraft = value },
                )

                ApiValueField(
                    label = stringResource(R.string.scr_user_agent),
                    value = userAgentDraft,
                    error = userAgentError(userAgentDraft),
                    enabled = !busy,
                    keyboardType = KeyboardType.Ascii,
                    onValueChange = { value -> userAgentDraft = value },
                )

                if (
                    redirectUriDraft != configurationController.compatibilityRedirectUri ||
                    userAgentDraft != configurationController.compatibilityUserAgent
                ) {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            redirectUriDraft = configurationController.compatibilityRedirectUri
                            userAgentDraft = configurationController.compatibilityUserAgent
                        },
                    ) {
                        Text(stringResource(R.string.scr_use_defaults))
                    }
                }
            }
        }
    }

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = valid && apiValuesValid && !busy,
        onClick = {
            val clientId =
                normalizedDraft

            if (
                !configurationController
                    .isValidClientId(clientId) ||
                !apiValuesValid
            ) {
                return@Button
            }

            busy = true
            failureMessage = null

            // LOWUI8_ONBOARDING_CLIENT_ID_SAVE_FINISHES_V1 (OCB-249): Back during
            // the save left the step and cancelled the reload after the ID was
            // stored; the save and its report finish anyway (as LOWUI6a's Settings).
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                withContext(NonCancellable) {
                    val result = runCatching {
                        withContext(
                            Dispatchers.IO,
                        ) {
                            configurationController
                                .save(
                                    clientId = clientId,
                                    redirectUri = redirectUriDraft.trim(),
                                    userAgent = userAgentDraft.trim(),
                                )
                        }
                    }

                    busy = false

                    result.onSuccess {
                        draftState.clearText()
                        onSaved()
                    }.onFailure {
                        failureMessage = couldNotSaveClientId
                    }
                }
            }
        },
    ) {
        if (busy) {
            InlineProgressIndicator()

            Spacer(Modifier.width(9.dp))
        }

        Text(
            if (busy) {
                stringResource(R.string.all_saving_2)
            } else {
                stringResource(R.string.all_save_client_id)
            },
        )
    }
}

@Composable
private fun RedditOnboardingReady(
    connectAccountState: ConnectAccountUiState,
    onConnectAccount: () -> Unit,
    onCancelConnectAccount: () -> Unit,
    onResetConnectAccount: () -> Unit,
    onReplaceClientId: () -> Unit,
    onShowGuide: () -> Unit,
) {
    Icon(
        Symbols.Check,
        contentDescription = null,
        modifier = Modifier.size(42.dp),
        tint = MaterialTheme.colorScheme.primary,
    )

    Text(
        stringResource(R.string.all_ready_to_connect),
        style =
            MaterialTheme.typography
                .headlineMedium,
        fontWeight = FontWeight.SemiBold,
    )

    Text(
        stringResource(R.string.scr_your_client_id_is_configured_subshell),
        color =
            MaterialTheme.colorScheme
                .onSurfaceVariant,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme
                .surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.all_reddit_account),
                style =
                    MaterialTheme.typography
                        .titleMedium,
            )

            Text(
                redditConnectStatus(
                    connectAccountState,
                ),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            )

            when {
                redditOnboardingConnectPhaseIsWorking(
                    connectAccountState.phase,
                ) -> {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        InlineProgressIndicator()

                        Spacer(
                            Modifier.width(10.dp),
                        )

                        Text(stringResource(R.string.scr_working))
                    }
                }

                connectAccountState.phase ==
                    ConnectAccountPhase.AWAITING_CALLBACK -> {
                    OutlinedButton(
                        onClick =
                            onCancelConnectAccount,
                    ) {
                        Text(stringResource(R.string.scr_cancel))
                    }
                }

                else -> Unit
            }
        }
    }

    when {
        redditOnboardingConnectPhaseOffersConnect(
            connectAccountState.phase,
        ) -> {
            Button(
                onClick = onConnectAccount,
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.scr_connect_reddit))
            }
        }

        connectAccountState.phase ==
            ConnectAccountPhase.CANCELLED -> {
            Button(
                onClick = {
                    onResetConnectAccount()
                    onConnectAccount()
                },
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.scr_try_again))
            }
        }

        connectAccountState.phase ==
            ConnectAccountPhase.ERROR -> {
            if (connectAccountState.retryable) {
                Button(
                    onClick = {
                        onResetConnectAccount()
                        onConnectAccount()
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.scr_try_again))
                }
            } else {
                Button(
                    onClick = {
                        onResetConnectAccount()
                        onReplaceClientId()
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.scr_review_client_id))
                }
            }
        }

        connectAccountState.phase ==
            ConnectAccountPhase.UNAVAILABLE -> {
            Button(
                onClick = onReplaceClientId,
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.scr_review_client_id))
            }
        }

        else -> Unit
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
    ) {
        TextButton(
            onClick = onShowGuide,
        ) {
            Text(stringResource(R.string.scr_how_setup_works))
        }

        // LOWUI8_CLIENT_ID_STAYS_DURING_A_SIGN_IN_V1 (OCB-323): replacing it
        // restarts the app and drops a sign-in that is running or waiting for
        // the browser; that sign-in is cancelled or finished first.
        TextButton(
            onClick = onReplaceClientId,
            enabled = !redditOnboardingConnectPhaseIsWorking(connectAccountState.phase) &&
                connectAccountState.phase != ConnectAccountPhase.AWAITING_CALLBACK,
        ) {
            Text(stringResource(R.string.scr_replace_client_id))
        }
    }
}

@Composable
private fun redditConnectStatus(
    state: ConnectAccountUiState,
): String = when (state.phase) {
    ConnectAccountPhase.UNAVAILABLE ->
        stringResource(R.string.scr_connect_status_unavailable)

    ConnectAccountPhase.IDLE ->
        stringResource(R.string.scr_connect_status_idle)

    ConnectAccountPhase.LAUNCHING ->
        stringResource(R.string.scr_connect_status_launching)

    ConnectAccountPhase.AWAITING_CALLBACK ->
        stringResource(R.string.scr_connect_status_awaiting)

    ConnectAccountPhase.EXCHANGING ->
        stringResource(R.string.scr_connect_status_exchanging)

    ConnectAccountPhase.RESOLVING_IDENTITY ->
        stringResource(R.string.scr_connect_status_resolving)

    ConnectAccountPhase.SYNCING_REDDIT_FEED ->
        stringResource(R.string.scr_connect_status_syncing)

    ConnectAccountPhase.CONNECTED ->
        stringResource(R.string.scr_connect_status_connected)

    ConnectAccountPhase.CANCELLED ->
        stringResource(R.string.scr_connect_status_cancelled)

    ConnectAccountPhase.ERROR ->
        if (state.retryable) {
            stringResource(R.string.scr_connect_status_error_retry)
        } else {
            stringResource(R.string.scr_connect_status_error_client_id)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RedditOnboardingConnected(
    username: String,
    linkHandlingStepPending: Boolean,
    onFinish: () -> Unit,
    modifier: Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    ProductTopBarTitle(
                        text = "subshell",
                    )
                },
            )
        },
    ) { padding ->
        // ISSUE111_CONNECTED_SCREEN_SCROLLS_V1: in landscape or with large
        // text the content did not fit and the only button (onFinish) was
        // squeezed off screen; it scrolls now and stays centred when it fits.
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(
                    horizontal = 24.dp,
                    vertical = 28.dp,
                ),
            verticalArrangement =
                Arrangement.Center,
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            Surface(
                shape =
                    MaterialTheme.shapes
                        .extraLarge,
                color =
                    MaterialTheme.colorScheme
                        .primaryContainer,
            ) {
                Icon(
                    Symbols.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(18.dp)
                        .size(44.dp),
                    tint =
                        MaterialTheme.colorScheme
                            .onPrimaryContainer,
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                stringResource(R.string.all_you_re_connected),
                style =
                    MaterialTheme.typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "u/$username",
                style =
                    MaterialTheme.typography
                        .titleMedium,
                color =
                    MaterialTheme.colorScheme
                        .primary,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                stringResource(R.string.scr_subshell_will_use_this_reddit_account),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onFinish,
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (linkHandlingStepPending) {
                        stringResource(R.string.all_continue)
                    } else {
                        stringResource(R.string.all_start_using_subshell)
                    },
                )
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RedditLinkHandlingOnboardingScreen(
    state: RedditLinkHandlingState,
    onOpenSettings: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    ProductTopBarTitle(
                        text = "subshell",
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 24.dp,
                    vertical = 28.dp,
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    Symbols.Link,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(18.dp)
                        .size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                stringResource(R.string.all_open_reddit_links_in_subshell),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                stringResource(R.string.all_android_decides_which_apps_can_open_web),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.all_after_you_tap_set_as_default),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.scr_1_turn_on_open_supported_links),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.all_when_you_return_subshell_will_check_what),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.status == RedditLinkHandlingStatus.PARTIAL) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.onb_domains_selected, state.selectedHostCount, state.supportedHostCount),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(R.string.all_if_another_reddit_app_already_handles_some),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.all_you_can_change_this_later_in_settings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.scr_set_as_default))
            }

            TextButton(
                onClick = onNotNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.scr_not_now))
            }
        }
    }
}
