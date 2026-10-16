package app.subshell.prototype.shortcuts

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.subshell.prototype.MainActivity
import app.subshell.prototype.R
import app.subshell.prototype.ui.normalizeFavoriteCustomFeedPath
import java.util.Locale

// ISSUE37_LAUNCHER_SHORTCUTS_V1: optional, off by default and device-local
// (its own preferences file, outside the portable settings map, so never in a
// backup). With it on, the active account's Inbox and favourite communities
// and custom feeds are the app's dynamic launcher shortcuts, and a community
// or custom feed can be pinned to the home screen. A community shortcut is a
// reddit.com/r/NAME VIEW intent addressed to MainActivity, so it opens
// through the same path as an incoming Reddit link. Feed and Inbox shortcuts
// belong to one account (see LauncherShortcutTargets.kt).
// Hidden communities never get a shortcut: their names are behind the
// hidden-community lock. A pinned one is disabled when it becomes hidden,
// because only the user can remove a pinned shortcut.

internal const val LAUNCHER_SHORTCUTS_PREFERENCES = "subshell_launcher_shortcuts"
internal const val LAUNCHER_SHORTCUTS_ENABLED_KEY = "launcher_shortcuts_enabled"

/** Launchers show about four shortcuts on long-press; more only clutter. Inbox counts. */
internal const val LAUNCHER_SHORTCUT_MAX_DYNAMIC = 4

internal const val HIDDEN_COMMUNITY_SHORTCUT_DISABLED_MESSAGE =
    "This community is hidden in subshell"

private const val COMMUNITY_SHORTCUT_ID_PREFIX = "community:"

internal object LauncherShortcutsEnabledStore {
    fun preferences(context: Context): SharedPreferences =
        context.applicationContext
            .getSharedPreferences(LAUNCHER_SHORTCUTS_PREFERENCES, Context.MODE_PRIVATE)

    fun read(context: Context): Boolean =
        preferences(context).getBoolean(LAUNCHER_SHORTCUTS_ENABLED_KEY, false)

    fun write(context: Context, enabled: Boolean) {
        preferences(context)
            .edit()
            .putBoolean(LAUNCHER_SHORTCUTS_ENABLED_KEY, enabled)
            .apply()
    }
}

/** The stored setting, updated whenever Settings writes it. */
@Composable
internal fun rememberLauncherShortcutsEnabled(): State<Boolean> {
    val appContext = LocalContext.current.applicationContext
    // LOW_SHORTCUTS_SETTING_READ_ONCE_V1 (OCB-573): the initial value was read
    // from preferences on every recomposition of the caller.
    val initial = remember(appContext) { LauncherShortcutsEnabledStore.read(appContext) }
    return produceState(
        initialValue = initial,
        appContext,
    ) {
        val preferences = LauncherShortcutsEnabledStore.preferences(appContext)
        // Held here: SharedPreferences keeps its listeners weakly.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LAUNCHER_SHORTCUTS_ENABLED_KEY) {
                value = LauncherShortcutsEnabledStore.read(appContext)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        value = LauncherShortcutsEnabledStore.read(appContext)
        awaitDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

internal fun communityShortcutId(communityName: String): String =
    COMMUNITY_SHORTCUT_ID_PREFIX + communityName.lowercase(Locale.ROOT)

internal fun isCommunityShortcutId(id: String): Boolean =
    id.startsWith(COMMUNITY_SHORTCUT_ID_PREFIX) &&
        id.length > COMMUNITY_SHORTCUT_ID_PREFIX.length

internal fun communityShortcutUri(communityName: String): String =
    "https://www.reddit.com/r/${launcherCommunityDisplayName(communityName)}"

/** Reddit's own casing when the caller has it (r/Android, not r/android); no prefix. */
internal fun launcherCommunityDisplayName(communityName: String): String {
    val trimmed = communityName.trim().removePrefix("/")
    return if (trimmed.startsWith("r/", ignoreCase = true)) trimmed.drop(2) else trimmed
}

internal data class PinnedLauncherShortcut(
    val id: String,
    val enabled: Boolean,
)

/** One launcher shortcut: what it is labelled and where it leads. */
internal sealed interface LauncherShortcutEntry {
    val id: String
    val label: String

    data class Community(val name: String) : LauncherShortcutEntry {
        override val id: String get() = communityShortcutId(launcherCommunityDisplayName(name))
        override val label: String get() = "r/${launcherCommunityDisplayName(name)}"
    }

    data class CustomFeed(
        val accountStableId: String,
        val path: String,
        val displayName: String,
    ) : LauncherShortcutEntry {
        override val id: String get() = customFeedShortcutId(accountStableId, path)
        override val label: String get() = displayName
    }

    data class Inbox(val accountStableId: String) : LauncherShortcutEntry {
        override val id: String get() = inboxShortcutId(accountStableId)
        override val label: String get() = "Inbox"
    }
}

internal fun customFeedShortcutId(accountStableId: String, path: String): String =
    "feed:$accountStableId:${customFeedPathKey(path)}"

/** One key per feed whether the path is Reddit's or the normalised favourite. */
internal fun customFeedPathKey(path: String): String =
    (normalizeFavoriteCustomFeedPath(path) ?: path.trim()).lowercase(Locale.ROOT)

internal fun sameCustomFeedPath(first: String, second: String): Boolean =
    customFeedPathKey(first) == customFeedPathKey(second)

internal fun inboxShortcutId(accountStableId: String): String = "inbox:$accountStableId"

/** A favourite feed's label: its loaded display name, else the path's last segment. */
internal fun customFeedShortcutLabel(path: String, loadedDisplayName: String?): String =
    loadedDisplayName?.takeIf(String::isNotBlank)
        ?: path.trimEnd('/').substringAfterLast('/').ifBlank { path }

internal data class LauncherShortcutPlan(
    /** Inbox first, then favourites by label; empty removes every dynamic shortcut. */
    val dynamic: List<LauncherShortcutEntry>,
    /** Pinned community shortcuts whose community is not hidden, currently disabled. */
    val enableCommunities: List<String>,
    /** Pinned community shortcut ids whose community is hidden, currently enabled. */
    val disablePinnedIds: List<String>,
)

/**
 * What the launcher should show for the active account. The hidden rule
 * applies whether or not the setting is on: turning the setting off keeps
 * existing pins working, but never lets one show a hidden community.
 * Pinned feed and Inbox shortcuts of any account are left as they are.
 */
internal fun planLauncherShortcuts(
    enabled: Boolean,
    favoriteCommunityNames: List<String>,
    hiddenCommunityNames: Collection<String>,
    pinned: List<PinnedLauncherShortcut>,
    favoriteCustomFeeds: List<LauncherShortcutEntry.CustomFeed> = emptyList(),
    inbox: LauncherShortcutEntry.Inbox? = null,
    maxDynamic: Int = LAUNCHER_SHORTCUT_MAX_DYNAMIC,
): LauncherShortcutPlan {
    val hiddenIds = hiddenCommunityNames.mapTo(HashSet(), ::communityShortcutId)
    val dynamic = if (enabled) {
        val communities = favoriteCommunityNames
            .map(LauncherShortcutEntry::Community)
            .filterNot { entry -> entry.id in hiddenIds }
        val favourites = (communities + favoriteCustomFeeds)
            .distinctBy(LauncherShortcutEntry::id)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, LauncherShortcutEntry::sortKey))
        (listOfNotNull(inbox) + favourites).take(maxDynamic.coerceAtLeast(0))
    } else {
        emptyList()
    }
    val communityPins = pinned.filter { shortcut -> isCommunityShortcutId(shortcut.id) }
    val favoriteDisplayNamesById = favoriteCommunityNames.associateBy { name ->
        communityShortcutId(launcherCommunityDisplayName(name))
    }
    return LauncherShortcutPlan(
        dynamic = dynamic,
        enableCommunities = communityPins
            .filter { shortcut -> !shortcut.enabled && shortcut.id !in hiddenIds }
            .map { shortcut ->
                favoriteDisplayNamesById[shortcut.id]
                    ?: shortcut.id.removePrefix(COMMUNITY_SHORTCUT_ID_PREFIX)
            },
        disablePinnedIds = communityPins
            .filter { shortcut -> shortcut.enabled && shortcut.id in hiddenIds }
            .map(PinnedLauncherShortcut::id),
    )
}

/** Communities sort by name without the r/ prefix, beside feeds by display name. */
private val LauncherShortcutEntry.sortKey: String
    get() = when (this) {
        is LauncherShortcutEntry.Community -> launcherCommunityDisplayName(name)
        else -> label
    }

internal interface LauncherShortcutPublisher {
    fun pinnedShortcuts(): List<PinnedLauncherShortcut>

    fun apply(plan: LauncherShortcutPlan)

    fun isPinSupported(): Boolean

    /** Asks the launcher to pin [entry]; false when it refused outright. */
    fun requestPin(entry: LauncherShortcutEntry): Boolean
}

internal class AndroidLauncherShortcutPublisher(context: Context) : LauncherShortcutPublisher {
    private val appContext = context.applicationContext

    override fun pinnedShortcuts(): List<PinnedLauncherShortcut> =
        ShortcutManagerCompat
            .getShortcuts(appContext, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .map { shortcut -> PinnedLauncherShortcut(id = shortcut.id, enabled = shortcut.isEnabled) }

    override fun apply(plan: LauncherShortcutPlan) {
        // Order matters: a disabled shortcut cannot be republished, and the
        // dynamic list never contains one that is about to be disabled.
        if (plan.enableCommunities.isNotEmpty()) {
            ShortcutManagerCompat.enableShortcuts(
                appContext,
                plan.enableCommunities.map { name ->
                    launcherShortcut(appContext, LauncherShortcutEntry.Community(name))
                },
            )
        }
        ShortcutManagerCompat.setDynamicShortcuts(
            appContext,
            plan.dynamic.mapIndexed { rank, entry -> launcherShortcut(appContext, entry, rank) },
        )
        if (plan.disablePinnedIds.isNotEmpty()) {
            ShortcutManagerCompat.disableShortcuts(
                appContext,
                plan.disablePinnedIds,
                HIDDEN_COMMUNITY_SHORTCUT_DISABLED_MESSAGE,
            )
        }
    }

    override fun isPinSupported(): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(appContext)

    override fun requestPin(entry: LauncherShortcutEntry): Boolean =
        ShortcutManagerCompat.requestPinShortcut(
            appContext,
            launcherShortcut(appContext, entry),
            null,
        )
}

internal fun launcherShortcut(
    context: Context,
    entry: LauncherShortcutEntry,
    rank: Int = 0,
): ShortcutInfoCompat {
    val (icon, intent) = when (entry) {
        is LauncherShortcutEntry.Community -> R.mipmap.ic_shortcut_community to
            Intent(Intent.ACTION_VIEW, Uri.parse(communityShortcutUri(entry.name)))
        is LauncherShortcutEntry.CustomFeed -> R.mipmap.ic_shortcut_custom_feed to
            launcherShortcutTargetIntent(
                LauncherShortcutTarget.CustomFeed(
                    accountStableId = entry.accountStableId,
                    path = entry.path,
                    requestId = 0L,
                ),
            )
        is LauncherShortcutEntry.Inbox -> R.mipmap.ic_shortcut_inbox to
            launcherShortcutTargetIntent(
                LauncherShortcutTarget.Inbox(accountStableId = entry.accountStableId, requestId = 0L),
            )
    }
    return ShortcutInfoCompat.Builder(context, entry.id)
        .setShortLabel(entry.label)
        .setLongLabel("Open ${entry.label}")
        .setIcon(IconCompat.createWithResource(context, icon))
        .setIntent(intent.setClass(context, MainActivity::class.java))
        .setRank(rank)
        .build()
}

internal fun communityShortcut(
    context: Context,
    communityName: String,
    rank: Int = 0,
): ShortcutInfoCompat = launcherShortcut(context, LauncherShortcutEntry.Community(communityName), rank)
