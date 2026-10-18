package app.subshell.prototype.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.subshell.prototype.R
import app.subshell.prototype.ui.RedditLinkHandlingState
import app.subshell.prototype.ui.RedditLinkHandlingStatus
import app.subshell.prototype.auth.RedditCompatibilityConfigurationController
import app.subshell.prototype.data.account.ConnectAccountPhase
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.ui.symbols.Symbols
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.subshell.prototype.ui.components.InlineProgressIndicator

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

    BackHandler(
        enabled =
            step != RedditOnboardingStep.INTRO,
    ) {
        stepName =
            RedditOnboardingStep.INTRO.name
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                                    "Back",
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
            "Checking Reddit connection setup…",
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
        "Couldn't read Reddit setup",
        style =
            MaterialTheme.typography
                .headlineSmall,
    )

    Text(
        "Nothing was changed. Try reading the encrypted local configuration again.",
        color =
            MaterialTheme.colorScheme
                .onSurfaceVariant,
    )

    Button(
        onClick = onRetry,
    ) {
        Text("Try again")
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
        "Connect subshell to Reddit",
        style =
            MaterialTheme.typography
                .headlineMedium,
        fontWeight = FontWeight.SemiBold,
    )

    Text(
        "Reddit sign-in cannot start until subshell has a Client ID. " +
            "You only need to set this up once for this subshell installation.",
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
                        "Setup required before sign-in",
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                    )

                    Text(
                        "subshell cannot create this Client ID for you. Use RedReader once to get the value Reddit assigns.",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )
                }
            }

            Text(
                "1. Open RedReader and sign in to Reddit.",
            )

            Text(
                "2. Open Reddit's authorization email and find App ID.",
            )

            Text(
                "3. Paste that App ID into subshell. Then sign in here.",
            )
        }
    }

    Button(
        onClick = onGetClientId,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Show me how to get a Client ID")
    }

    OutlinedButton(
        onClick = onAlreadyHaveClientId,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("I already have a Client ID")
    }

    Text(
        "Your Client ID is kept in encrypted no-backup storage on this device and isn't included in subshell settings backups.",
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
        "Get your Client ID",
        style =
            MaterialTheme.typography
                .headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )

    SetupStep(
        number = "1",
        title = "Install or open RedReader",
        body =
            "RedReader is used once to create the Reddit authorization that gives you the Client ID subshell needs.",
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
                "Get it on Google Play",
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
                "Get it on F-Droid",
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
            "Couldn't open a store on this phone. Search for RedReader in your app store.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    SetupStep(
        number = "2",
        title = "Sign in to Reddit in RedReader",
        body =
            "Complete RedReader's normal Reddit sign-in and authorize it when Reddit asks.",
    )

    SetupStep(
        number = "3",
        title = "Open Reddit's authorization email",
        body =
            "Find the line labeled App ID. The value beside App ID is the Client ID you will paste into subshell.",
    )

    RedditAuthorizationEmailExample()

    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("I found my Client ID")
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
                "EXAMPLE ONLY — NOT A REAL EMAIL",
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
                "What to look for in Reddit's email",
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Text(
                "The real Reddit email contains these fields. The value after App ID is what subshell calls your Client ID.",
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
                        "App Name",
                        style =
                            MaterialTheme.typography
                                .labelMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )

                    Text(
                        "RedReader (Release)",
                        fontWeight =
                            FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "App ID",
                        style =
                            MaterialTheme.typography
                                .labelMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )

                    Text(
                        "your Client ID appears here",
                        color =
                            MaterialTheme.colorScheme
                                .primary,
                        fontWeight =
                            FontWeight.SemiBold,
                    )
                }
            }

            Text(
                "Copy only the value shown after App ID from your real Reddit email.",
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
    var draft by remember {
        mutableStateOf("")
    }

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

    val normalizedDraft = draft.trim()

    val valid =
        configurationController.isValidClientId(
            normalizedDraft,
        )

    Icon(
        Symbols.Lock,
        contentDescription = null,
        modifier = Modifier.size(38.dp),
        tint = MaterialTheme.colorScheme.primary,
    )

    Text(
        "Paste your Client ID",
        style =
            MaterialTheme.typography
                .headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )

    Text(
        "Paste only the App ID value from Reddit's authorization email.",
        color =
            MaterialTheme.colorScheme
                .onSurfaceVariant,
    )

    OutlinedTextField(
        value = draft,
        onValueChange = { value ->
            draft = value
                .replace('\n', ' ')
                .replace('\r', ' ')
            failureMessage = null
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        singleLine = true,
        label = {
            Text("Client ID")
        },
        visualTransformation =
            if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
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
                            "Hide Client ID"
                        } else {
                            "Show Client ID"
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
                        "That doesn't look like a valid Reddit Client ID.",
                    )

                else ->
                    Text(
                        "Stored locally in encrypted no-backup storage.",
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
                "Hide advanced"
            } else {
                "Advanced"
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
                    "RedReader compatibility defaults",
                    style =
                        MaterialTheme.typography
                            .titleSmall,
                )

                SelectionContainer {
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(
                                10.dp,
                            ),
                    ) {
                        AdvancedValue(
                            label = "Redirect URI",
                            value =
                                configurationController
                                    .compatibilityRedirectUri,
                        )

                        AdvancedValue(
                            label = "User agent",
                            value =
                                configurationController
                                    .compatibilityUserAgent,
                        )
                    }
                }

                Text(
                    "These values are fixed by subshell and normally don't need to be changed.",
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

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = valid && !busy,
        onClick = {
            val clientId =
                normalizedDraft

            if (
                !configurationController
                    .isValidClientId(clientId)
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
                                .save(clientId)
                        }
                    }

                    busy = false

                    result.onSuccess {
                        draft = ""
                        onSaved()
                    }.onFailure {
                        failureMessage =
                            "subshell couldn't save the Client ID."
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
                "Saving…"
            } else {
                "Save Client ID"
            },
        )
    }
}

@Composable
private fun AdvancedValue(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(3.dp),
    ) {
        Text(
            label,
            style =
                MaterialTheme.typography
                    .labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )

        Text(
            value,
            style =
                MaterialTheme.typography
                    .bodyMedium,
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
        "Ready to connect",
        style =
            MaterialTheme.typography
                .headlineMedium,
        fontWeight = FontWeight.SemiBold,
    )

    Text(
        "Your Client ID is configured. subshell can now open Reddit sign-in.",
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
                "Reddit account",
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

                        Text("Working…")
                    }
                }

                connectAccountState.phase ==
                    ConnectAccountPhase.AWAITING_CALLBACK -> {
                    OutlinedButton(
                        onClick =
                            onCancelConnectAccount,
                    ) {
                        Text("Cancel")
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
                Text("Connect Reddit")
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
                Text("Try again")
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
                    Text("Try again")
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
                    Text("Review Client ID")
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
                Text("Review Client ID")
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
            Text("How setup works")
        }

        // LOWUI8_CLIENT_ID_STAYS_DURING_A_SIGN_IN_V1 (OCB-323): replacing it
        // restarts the app and drops a sign-in that is running or waiting for
        // the browser; that sign-in is cancelled or finished first.
        TextButton(
            onClick = onReplaceClientId,
            enabled = !redditOnboardingConnectPhaseIsWorking(connectAccountState.phase) &&
                connectAccountState.phase != ConnectAccountPhase.AWAITING_CALLBACK,
        ) {
            Text("Replace Client ID")
        }
    }
}

private fun redditConnectStatus(
    state: ConnectAccountUiState,
): String = when (state.phase) {
    ConnectAccountPhase.UNAVAILABLE ->
        "The Reddit connection isn't available with the current local configuration."

    ConnectAccountPhase.IDLE ->
        "No Reddit account is connected yet."

    ConnectAccountPhase.LAUNCHING ->
        "Opening Reddit sign-in…"

    ConnectAccountPhase.AWAITING_CALLBACK ->
        "Finish signing in to Reddit, then return to subshell."

    ConnectAccountPhase.EXCHANGING ->
        "Completing Reddit authorization…"

    ConnectAccountPhase.RESOLVING_IDENTITY ->
        "Loading your Reddit account…"

    ConnectAccountPhase.SYNCING_REDDIT_FEED ->
        "Preparing your Reddit account…"

    ConnectAccountPhase.CONNECTED ->
        "Reddit connected. Finishing setup…"

    ConnectAccountPhase.CANCELLED ->
        "Reddit sign-in was cancelled. Nothing was changed."

    ConnectAccountPhase.ERROR ->
        if (state.retryable) {
            "Reddit sign-in didn't finish. You can try again."
        } else {
            "The Reddit connection couldn't be completed. Review the Client ID."
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
                "You're connected",
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
                "subshell will use this Reddit account for your feeds, communities, Inbox, profile and supported account actions.",
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
                        "Continue"
                    } else {
                        "Start using subshell"
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
                "Open Reddit links in subshell?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "Android decides which apps can open web links, so subshell can't enable Reddit links automatically.",
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
                        "After you tap Set as default:",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "1. Turn on Open supported links.\n2. Select the Reddit addresses you want subshell to open.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "When you return, subshell will check what you enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.status == RedditLinkHandlingStatus.PARTIAL) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "${state.selectedHostCount} of ${state.supportedHostCount} supported Reddit domains are currently selected.",
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "If another Reddit app already handles some Reddit links, you may need to move those selections to subshell on the same Android screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "You can change this later in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Set as default")
            }

            TextButton(
                onClick = onNotNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Not now")
            }
        }
    }
}
