package app.subshell.prototype.ui.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import app.subshell.prototype.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.ui.InteractionSide
import app.subshell.prototype.ui.PostLayout
import app.subshell.prototype.ui.RedditLinkHandlingState
import app.subshell.prototype.ui.RedditLinkHandlingStatus
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.ThemeChoice
import app.subshell.prototype.ui.VideoAutoplay
import app.subshell.prototype.ui.components.SegmentedChoiceRow
import app.subshell.prototype.ui.components.SubshellWordmark
import app.subshell.prototype.ui.symbols.Symbols
import app.subshell.prototype.ui.theme.AppTextScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ISSUE271_ONE_SETUP_FLOW_AFTER_SIGN_IN_V1 (owner 2026-10-28): after the first
 * sign-in, one full-screen flow asks for the choices that matter — hand,
 * look, content and video, notifications, Reddit links, backups — instead of
 * the Android notification question, the link screen, the side dialog and the
 * backup dialog interrupting one after another. Every page can be skipped and
 * says where to change it later. It runs while the interaction side has never
 * been chosen (a fresh install); Skip or Done confirms the side, so it never
 * returns and existing installs never see it.
 */
internal enum class FirstRunSetupPage { HAND, LOOK, CONTENT, NOTIFICATIONS, LINKS, BACKUPS, DONE }

/** The link page only where Android lets subshell open Reddit links and it does not yet. */
internal fun firstRunSetupPages(linkHandlingState: RedditLinkHandlingState): List<FirstRunSetupPage> =
    FirstRunSetupPage.entries.filter { page ->
        page != FirstRunSetupPage.LINKS ||
            (linkHandlingState.supported && linkHandlingState.status != RedditLinkHandlingStatus.ENABLED)
    }

@Composable
internal fun FirstRunSetupScreen(
    state: SubshellState,
    backupRepository: SettingsBackupRepository,
    linkHandlingState: RedditLinkHandlingState,
    onOpenRedditLinkSettings: () -> Unit,
    onDismissRedditLinkPrompt: () -> Unit,
    modifier: Modifier = Modifier,
    // Replaces Android's permission question in tests.
    onRequestNotificationPermission: (((Boolean) -> Unit) -> Unit)? = null,
) {
    val pages = firstRunSetupPages(linkHandlingState)
    var pageName by rememberSaveable { mutableStateOf(FirstRunSetupPage.HAND.name) }
    val page = FirstRunSetupPage.valueOf(pageName)
    val index = pages.indexOf(page).coerceAtLeast(0)
    val steps = pages.size - 1

    fun goTo(target: FirstRunSetupPage) {
        pageName = target.name
    }
    fun next() {
        pages.getOrNull(index + 1)?.let(::goTo)
    }
    fun back() {
        pages.getOrNull(index - 1)?.let(::goTo)
    }
    // Done or Skip: the side is the one shown (Right unless picked), and the
    // link and backup questions are answered, so none of them comes back.
    fun finish() {
        onDismissRedditLinkPrompt()
        backupRepository.dismissFolderOnboarding()
        state.confirmInteractionSide()
    }

    BackHandler(enabled = index > 0) { back() }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubshellWordmark(Modifier.weight(1f))
                if (page != FirstRunSetupPage.DONE) {
                    TextButton(onClick = ::finish) { Text("Skip") }
                }
            }
            if (page != FirstRunSetupPage.DONE) {
                SetupProgress(step = index, steps = steps)
            }
            // Each page starts at its top; a page taller than the screen says so.
            val scroll = rememberSaveable(page, saver = ScrollState.Saver) { ScrollState(0) }
            val scrollScope = rememberCoroutineScope()
            Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (page) {
                    FirstRunSetupPage.HAND -> HandPage(state)
                    FirstRunSetupPage.LOOK -> LookPage(state)
                    FirstRunSetupPage.CONTENT -> ContentPage(state)
                    FirstRunSetupPage.NOTIFICATIONS -> NotificationsPage(
                        state = state,
                        onRequestPermission = onRequestNotificationPermission,
                        onDone = ::next,
                    )
                    FirstRunSetupPage.LINKS -> LinksPage(
                        linkHandlingState = linkHandlingState,
                        onOpenSettings = onOpenRedditLinkSettings,
                        onDone = ::next,
                    )
                    FirstRunSetupPage.BACKUPS -> BackupsPage(backupRepository)
                    FirstRunSetupPage.DONE -> DonePage(state, linkHandlingState, backupRepository)
                }
            }
            // Only when a real part of the page is hidden, not a few pixels of padding.
            val hiddenBelow = with(LocalDensity.current) { (scroll.maxValue - scroll.value).toDp() }
            if (hiddenBelow > 48.dp) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                            ),
                        )
                        .padding(top = 24.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = { scrollScope.launch { scroll.animateScrollTo(scroll.maxValue) } }) {
                        Icon(Symbols.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("More below")
                    }
                }
            }
            }
            if (page != FirstRunSetupPage.DONE) {
                Text(
                    "You can change this later in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index > 0 && page != FirstRunSetupPage.DONE) {
                    TextButton(onClick = ::back) { Text("Back") }
                }
                Spacer(Modifier.weight(1f))
                when (page) {
                    // These pages move on with their own answer.
                    FirstRunSetupPage.NOTIFICATIONS, FirstRunSetupPage.LINKS -> Unit
                    FirstRunSetupPage.DONE -> Button(onClick = ::finish) { Text("Start using subshell") }
                    else -> Button(onClick = ::next) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun SetupProgress(step: Int, steps: Int) {
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .semantics { contentDescription = "Step ${step + 1} of $steps" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(steps) { dot ->
            Box(
                Modifier
                    .size(width = if (dot == step) 20.dp else 8.dp, height = 8.dp)
                    .background(
                        if (dot == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}

@Composable
private fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun PageText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun HandPage(state: SubshellState) {
    PageTitle("Which hand do you use?")
    PageText("Vote and reply buttons, the side menu and the Create button go on that side, where your thumb is.")
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(InteractionSide.LEFT to "Left hand", InteractionSide.RIGHT to "Right hand").forEach { (side, label) ->
            val selected = state.interactionSide == side
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = selected, role = Role.RadioButton) { state.previewInteractionSide(side) },
                shape = MaterialTheme.shapes.large,
                color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                border = BorderStroke(
                    2.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HandPreview(side)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    Text(
        "The orange edge is where the side menu slides in.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A small phone: a post card with its buttons, the menu edge and the Create button on [side]. */
@Composable
private fun HandPreview(side: InteractionSide) {
    val colors = MaterialTheme.colorScheme
    val toSide = if (side == InteractionSide.LEFT) Alignment.Start else Alignment.End
    val edge = if (side == InteractionSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
    val fab = if (side == InteractionSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd
    Box(
        Modifier
            .size(width = 124.dp, height = 220.dp)
            .background(colors.surface, RoundedCornerShape(18.dp))
            .padding(2.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(colors.outlineVariant, RoundedCornerShape(3.dp)))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainerHigh, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = toSide,
            ) {
                Box(Modifier.fillMaxWidth(0.7f).height(6.dp).background(colors.outlineVariant, RoundedCornerShape(3.dp)))
                Box(Modifier.fillMaxWidth().height(44.dp).background(colors.secondaryContainer, RoundedCornerShape(6.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) { Box(Modifier.size(14.dp).background(colors.primary, RoundedCornerShape(7.dp))) }
                }
            }
        }
        Box(
            Modifier
                .align(edge)
                .size(width = 4.dp, height = 64.dp)
                .background(Color(0xFFFF8A3D), RoundedCornerShape(2.dp)),
        )
        Box(
            Modifier
                .align(fab)
                .padding(10.dp)
                .size(30.dp)
                .background(colors.primaryContainer, RoundedCornerShape(10.dp)),
        )
    }
}

@Composable
private fun LookPage(state: SubshellState) {
    PageTitle("How should it look?")
    // How posts look with the layout and theme chosen below.
    PostLayoutPreview(state.postLayout)
    SegmentedChoiceRow(
        options = ThemeChoice.entries,
        selected = state.themeChoice,
        label = ThemeChoice::shortLabel,
        onSelect = state::updateThemeChoice,
        showSelectedIcon = false,
    )
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    if (dynamicSupported) {
        SetupSwitchRow(
            title = "Dynamic colour",
            description = "Wallpaper colours instead of subshell orange",
            checked = state.dynamicColor,
            onCheckedChange = state::updateDynamicColor,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PostLayout.entries.forEach { layout ->
            val selected = state.postLayout == layout
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = selected, role = Role.RadioButton) { state.updatePostLayout(layout) },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                border = BorderStroke(
                    2.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(postLayoutIcon(layout), contentDescription = null)
                    Text(
                        layout.label,
                        // bodySmall, not labelMedium: the label styles use the code font.
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
    Text("Text size", style = MaterialTheme.typography.titleSmall)
    SegmentedChoiceRow(
        options = AppTextScale.entries,
        selected = state.appTextScale,
        label = AppTextScale::label,
        onSelect = state::updateAppTextScale,
        showSelectedIcon = false,
    )
}

private val previewPosts = listOf(
    Triple("r/battlestations", "Finally happy with my desk", R.drawable.media_linux_desk),
    Triple("r/homelab", "The rack is done, for now", R.drawable.media_homelab),
    Triple("r/androiddev", "Where the apps get made", R.drawable.media_android_desk),
)

/**
 * Posts as the feed shows them in [layout] (Large: one big card with its
 * picture and votes; Compact: smaller cards with a shorter picture; List: the
 * title with a thumbnail; Grid: square tiles), in the theme's colours, at one
 * fixed height so switching layouts does not move the page.
 */
@Composable
private fun PostLayoutPreview(layout: PostLayout) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .semantics { contentDescription = "Preview of the ${layout.label} post layout" },
        shape = MaterialTheme.shapes.large,
        color = colors.background,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (layout) {
                PostLayout.LARGE -> previewPosts.first().let { (community, title, image) ->
                    PreviewCard(community, title, image, mediaHeight = 124, stats = true)
                }
                PostLayout.COMPACT -> previewPosts.take(2).forEach { (community, title, image) ->
                    PreviewCard(community, title, image, mediaHeight = 38, stats = false)
                }
                PostLayout.LIST -> previewPosts.forEach { (community, title, image) ->
                    PreviewListRow(community, title, image)
                }
                PostLayout.GRID -> repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { column ->
                            PreviewImage(
                                previewPosts[(row + column) % previewPosts.size].third,
                                Modifier.weight(1f).height(103.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewImage(image: Int, modifier: Modifier) {
    Image(
        painter = painterResource(image),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
    )
}

@Composable
private fun PreviewCard(community: String, title: String, image: Int, mediaHeight: Int, stats: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(community, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        PreviewImage(image, Modifier.fillMaxWidth().height(mediaHeight.dp))
        if (stats) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbols.ThumbUp, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                Text("1.2k", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Icon(Symbols.ThumbDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Icon(Symbols.Comment, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                Text("84", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PreviewListRow(community: String, title: String, image: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(community, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        PreviewImage(image, Modifier.size(48.dp))
    }
}

private fun postLayoutIcon(layout: PostLayout): ImageVector = when (layout) {
    PostLayout.LARGE -> Symbols.ViewAgenda
    PostLayout.COMPACT -> Symbols.ViewDay
    PostLayout.LIST -> Symbols.ViewList
    PostLayout.GRID -> Symbols.GridView
}

@Composable
private fun SetupSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        content = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

@Composable
private fun ContentPage(state: SubshellState) {
    PageTitle("What do you want to see?")
    SectionLabel("NSFW")
    SetupSwitchRow(
        title = "Show NSFW posts",
        description = "Posts Reddit marks NSFW appear in feeds and search",
        checked = state.showNsfwPosts,
        onCheckedChange = state::updateShowNsfwPosts,
    )
    SetupSwitchRow(
        title = "Show NSFW media without a tap",
        description = if (state.showNsfwPosts) {
            "NSFW images, GIFs and videos show without a cover; spoilers stay covered"
        } else {
            "Turn on NSFW posts first"
        },
        checked = state.showNsfwPosts && state.showNsfwMedia,
        onCheckedChange = state::updateShowNsfwMedia,
        enabled = state.showNsfwPosts,
    )
    SectionLabel("Video")
    Text("Play videos and GIFs by themselves", style = MaterialTheme.typography.bodyLarge)
    SegmentedChoiceRow(
        options = VideoAutoplay.entries,
        selected = state.videoAutoplay,
        label = VideoAutoplay::label,
        onSelect = state::updateVideoAutoplay,
        showSelectedIcon = false,
    )
    SetupSwitchRow(
        title = "Start videos muted",
        description = "Tap the sound button to hear them",
        checked = state.startVideosMuted,
        onCheckedChange = state::updateStartVideosMuted,
    )
}

@Composable
private fun NotificationsPage(
    state: SubshellState,
    onRequestPermission: (((Boolean) -> Unit) -> Unit)?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    // The answer turns them off on a refusal, as the Settings switch does.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) state.updateInboxNotificationsEnabled(false)
        onDone()
    }
    PageTitle("Get notified?")
    PageText("subshell checks your Reddit inbox about every 15 minutes and tells you when there is something new.")
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(vertical = 4.dp)) {
            NotificationKind(Symbols.Reply, "Replies", "to your posts and comments")
            NotificationKind(Symbols.Mail, "Messages and chats", "you can answer from the notification")
            NotificationKind(Symbols.AlternateEmail, "Mentions", "when someone writes your u/name")
        }
    }
    Text(
        "After you tap Turn on, Android asks you to allow notifications. Each kind can be switched off later.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        TextButton(
            onClick = {
                state.updateInboxNotificationsEnabled(false)
                onDone()
            },
        ) { Text("Not now") }
        Button(
            onClick = {
                state.updateInboxNotificationsEnabled(true)
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                when {
                    !needsPermission -> onDone()
                    onRequestPermission != null -> onRequestPermission { granted ->
                        if (!granted) state.updateInboxNotificationsEnabled(false)
                        onDone()
                    }
                    else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        ) { Text("Turn on") }
    }
}

@Composable
private fun NotificationKind(icon: ImageVector, title: String, description: String) {
    ListItem(
        content = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun LinksPage(
    linkHandlingState: RedditLinkHandlingState,
    onOpenSettings: () -> Unit,
    onDone: () -> Unit,
) {
    var opened by rememberSaveable { mutableStateOf(false) }
    PageTitle("Open Reddit links here?")
    PageText(
        "Links to reddit.com in other apps can open in subshell instead of the browser. " +
            "Android lets only you turn this on.",
    )
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinkStep(1, "Tap Set up. Android's page for subshell opens.")
            LinkStep(2, "Turn on Open supported links and tick the Reddit addresses.")
            LinkStep(3, "Come back. subshell checks what you turned on.")
        }
    }
    val status = when (linkHandlingState.status) {
        RedditLinkHandlingStatus.ENABLED -> "Reddit links open in subshell."
        RedditLinkHandlingStatus.PARTIAL -> "Some Reddit addresses open in subshell."
        else -> null
    }
    if (opened && status != null) {
        Text(status, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
    Text(
        "If another Reddit app already opens these links, move them to subshell on the same page.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        if (opened) {
            Button(onClick = onDone) { Text("Next") }
        } else {
            TextButton(onClick = onDone) { Text("Not now") }
            Button(
                onClick = {
                    opened = true
                    onOpenSettings()
                },
            ) { Text("Set up") }
        }
    }
}

@Composable
private fun LinkStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BackupsPage(repository: SettingsBackupRepository) {
    val scope = rememberCoroutineScope()
    var folder by remember(repository) { mutableStateOf(repository.status().folderLocation) }
    var connected by remember(repository) { mutableStateOf(repository.folderConnected()) }
    var failed by remember { mutableStateOf(false) }
    var found by remember { mutableIntStateOf(0) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { repository.connectFolder(uri) } }
                    .onSuccess { connection ->
                        failed = !connection.isProtected
                        connected = connection.isProtected
                        found = connection.importedSnapshots
                        folder = repository.status().folderLocation
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        failed = true
                    }
            }
        }
    }
    PageTitle("Keep your setup safe")
    PageText("Your settings are backed up on this phone. Sign-ins never are.")
    // One row: what a folder is for, or where the copies go.
    ListItem(
        content = { Text("Backup folder") },
        supportingContent = {
            Text(
                when {
                    connected && found > 0 -> "Copied to ${folder ?: "your folder"} · $found backups found"
                    connected -> "Copied to ${folder ?: "your folder"}"
                    failed -> "Couldn't use that folder. Try another."
                    else -> "Copies that survive removing subshell"
                },
                color = if (failed && !connected) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        leadingContent = { Icon(Symbols.Folder, contentDescription = null) },
        trailingContent = {
            if (!connected) {
                FilledTonalButton(onClick = { folderLauncher.launch(null) }) { Text("Choose") }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(MaterialTheme.shapes.large),
    )
    SectionLabel("Privacy-sensitive backup data")
    PrivacyBackupSwitch("Hidden communities", repository::hiddenCommunitiesBackupEnabled, repository::setHiddenCommunitiesBackupEnabled)
    PrivacyBackupSwitch("Muted communities", repository::communityMutesBackupEnabled, repository::setCommunityMutesBackupEnabled)
    PrivacyBackupSwitch("Post drafts", repository::postComposerDraftsBackupEnabled, repository::setPostComposerDraftsBackupEnabled)
    PrivacyBackupSwitch(
        "Saved moderator responses",
        repository::moderatorSavedResponsesBackupEnabled,
        repository::setModeratorSavedResponsesBackupEnabled,
    )
    PrivacyBackupSwitch("Private notes & tags", repository::userTagsBackupEnabled, repository::setUserTagsBackupEnabled)
}

@Composable
private fun PrivacyBackupSwitch(title: String, read: () -> Boolean, write: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(read()) }
    ListItem(
        content = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch) { enabled ->
            checked = enabled
            write(enabled)
        },
    )
}

@Composable
private fun DonePage(
    state: SubshellState,
    linkHandlingState: RedditLinkHandlingState,
    repository: SettingsBackupRepository,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Symbols.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text("You're all set", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "Everything here lives in Settings if you change your mind.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    val theme = state.themeChoice.label
    val rows = buildList {
        add("Hand" to if (state.interactionSide == InteractionSide.LEFT) "Left" else "Right")
        add("Theme · Posts · Text" to "$theme · ${state.postLayout.label} · ${state.appTextScale.label}")
        add("NSFW posts" to if (state.showNsfwPosts) "On" else "Off")
        add("Videos" to state.videoAutoplay.label + if (state.startVideosMuted) ", muted" else "")
        add("Notifications" to if (state.inboxNotificationsEnabled) "On" else "Off")
        if (linkHandlingState.supported) {
            add("Reddit links" to if (linkHandlingState.status == RedditLinkHandlingStatus.ENABLED) "Open here" else "Not set up")
        }
        add("Backup folder" to if (repository.folderConnected()) "Chosen" else "Not chosen")
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            rows.forEach { (key, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
