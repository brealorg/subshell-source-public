package app.subshell.prototype.ui.screens

import java.util.Locale

internal data class SettingsSearchEntry(
    val key: String,
    val section: String,
    val terms: String,
) {
    val searchableText: String = normalizeSettingsSearchText("$section $terms")
}

internal data class SettingsSearchResult(
    val query: String,
    val itemKeys: Set<String>,
    val sections: Set<String>,
) {
    val hasMatches: Boolean
        get() = query.isBlank() || itemKeys.isNotEmpty()

    fun isItemVisible(key: String): Boolean = query.isBlank() || key in itemKeys

    fun isSectionVisible(section: String): Boolean = query.isBlank() || section in sections
}

internal val settingsSearchEntries: List<SettingsSearchEntry> = listOf(
    setting("accounts", "Accounts", "Reddit accounts current account switch add connect sign in log out manage"),
    setting("online-visibility", "Account privacy", "online status visibility show presence Reddit account privacy"),

    // ISSUE234_FEED_POST_LAYOUTS_V1: the Thumbnail row shows only with List, so its words lead here.
    setting("post-layout", "Feed", "post layout large compact list grid density cards size small dense columns view thumbnail left right"),
    setting("unread-marker", "Feed", "show unread marker post age dot read unread"),
    setting("show-nsfw-posts", "Feed", "show NSFW posts 18+ sensitive content"),
    setting("content-filters", "Feed", "content filters words communities domains users flair filter hide"),
    setting("filtered-posts-summary", "Feed", "filtered posts summary show hidden count reveal filter"),
    setting("scroll-position-indicator", "Feed", "scroll position indicator scrollbar bar where thread comments"),
    setting("random-nsfw-community", "Feed", "random nsfw community shuffle adult"),
    setting("pinned-posts", "Feed", "pinned posts stickied sticky community highlights top"),
    // ISSUE116_EVERY_VISIBLE_SETTING_IS_SEARCHABLE_V1: these rows had no entry,
    // so a search for them found nothing.
    setting("muted-community-direct-view", "Feed", "muted communities mute opening a muted community direct visit show posts spoiler"),

    setting("theme", "Appearance", "theme system light dark pure black oled amoled"),
    setting("dynamic", "Appearance", "dynamic colour color device palette subshell orange"),
    setting("text-size", "Appearance", "text size font size larger smaller scale readability accessibility"),
    setting("vote-controls", "Appearance", "vote controls comfortable compact thumbs arrows score"),
    setting("vote-colours", "Appearance", "vote colours colors theme accent red blue upvote downvote"),
    setting("code-font", "Appearance", "code font monospace JetBrains Mono Iosevka Recursive balanced compact casual"),

    setting("top-bar-behavior", "Home & feeds", "top bar always show visible hide on scroll"),
    setting("bottom-navigation-behavior", "Home & feeds", "bottom navigation bar always show visible hide on scroll"),

    setting("interaction-side", "Navigation", "interaction side left right controls floating actions media composer"),
    setting("community-discovery-layout", "Navigation", "community discovery popular communities carousel list layout cards rows"),
    setting("drawer-side", "Navigation", "side menu drawer position left right edge"),
    setting("drawer-communities", "Navigation", "communities in drawer simple dropdown full list joined communities"),
    setting("drawer-recent-communities", "Navigation", "recent communities in drawer favorites history"),
    setting("back-twice-to-exit", "Navigation", "press back twice exit confirm leave close app quit"),
    setting("launcher-shortcuts", "Navigation", "launcher shortcuts home screen pin favorite communities app icon long press"),
    setting("reddit-favorite-sync", "Navigation", "sync favorites with reddit favourite communities star reddit account"),
    setting("share-into-subshell", "Navigation", "share into subshell share menu share sheet post link text images send"),
    setting("navigation-labels", "Navigation", "navigation labels on partial off active label"),
    setting("tablet-layout-mode", "Navigation", "tablet mode layout split single two pane large screen foldable landscape"),
    setting("subreddit-labels", "Navigation", "communities labels community labels subreddit labels modern classic plain prefix community names"),

    setting("hidden-subscriptions-device-unlock", "Privacy", "require device unlock hidden communities subscriptions biometric screen lock"),
    setting("app-lock", "Privacy", "lock subshell app lock biometric fingerprint pin device unlock open launch"),
    setting("app-lock-delay", "Privacy", "lock after delay timeout background minutes hours immediately custom app lock"),
    setting("app-lock-notifications", "Privacy", "hide notification content preview app lock private messages modmail"),

    setting("search-keyboard", "Search", "open keyboard entering search focus suggestions scope"),
    setting("saved-searches", "Search", "saved searches save bookmark run again query"),

    setting("sensitive-media-previews", "Media & links", "sensitive media previews NSFW spoiler blur preload load on tap hide preview"),
    setting("reddit-link-handling", "Media & links", "Open Reddit links reddit links open links browser default Android app links supported domains"),
    setting("media-autoplay", "Media & links", "autoplay videos GIFs one visible media item"),
    setting("media-muted", "Media & links", "start videos muted volume audio sound"),
    setting("media-loop", "Media & links", "loop videos repeat replay automatically automatic end"),
    setting("video-quality-wifi", "Media & links", "video quality Wi-Fi wifi unmetered adaptive ceiling"),
    setting("video-quality-mobile", "Media & links", "video quality mobile data cellular metered adaptive ceiling"),
    setting("video-subtitle-default", "Media & links", "subtitles captions closed captions default off android caption settings"),
    setting("video-audio-default", "Media & links", "audio language track default device language dub"),
    setting("video-speed-controls", "Media & links", "playback speed faster slower 2x 1.5x 0.5x"),
    setting("video-double-tap-seek", "Media & links", "double tap skip seek forward back 10 seconds"),
    setting("video-hold-double-speed", "Media & links", "hold press 2x double speed faster"),
    setting("video-pinch-to-zoom", "Media & links", "pinch zoom video magnify enlarge closer look"),
    setting("video-rotate-fullscreen", "Media & links", "rotate landscape orientation wide video full screen turn"),
    setting("video-technical-details", "Media & links", "technical details codecs decoders dropped frames diagnostics developer"),
    setting("video-picture-in-picture", "Media & links", "picture in picture pip small window background floating"),
    setting("playback-options-placement", "Media & links", "playback options button gear quality audio subtitles post full screen feed"),
    setting("video-timeline-size", "Media & links", "video timeline size seekbar slider compact standard large"),
    setting("media-cache", "Media & links", "media cache storage clear cached disk"),
    setting("video-cache-limit", "Media & links", "video cache size limit storage space gigabyte megabyte 1 gb"),
    setting("download-storage", "Media & links", "downloaded videos downloads storage offline delete all files"),
    setting("media-captions", "Media & links", "gallery captions media captions image captions overlay below media hidden links"),
    setting("fullscreen-captions", "Media & links", "fullscreen gallery captions fullscreen captions viewer controls always visible"),
    setting("media-labels", "Media & links", "media labels descriptive image GIF video"),
    setting("landscape-video-gestures", "Media & links", "fullscreen gestures landscape swipe controls video player"),
    setting("landscape-brightness-gesture", "Media & links", "brightness gesture fullscreen landscape swipe screen light"),
    setting("landscape-volume-gesture", "Media & links", "volume gesture fullscreen landscape swipe sound audio"),

    setting("reduced-motion", "Accessibility", "reduce motion animation animations immediate state changes remove system android"),

    setting("feed-state", "Prototype states", "feed preview loading offline error empty content state prototype"),

    setting("swipe-actions-enabled", "Navigation", "swipe actions gestures posts comments horizontal enable disable"),
    setting("post-swipe-left", "Navigation", "post swipe left upvote downvote save hide none gesture"),
    setting("post-swipe-right", "Navigation", "post swipe right upvote downvote save hide none gesture"),
    setting("comment-swipe-left", "Navigation", "comment swipe left upvote downvote none gesture"),
    setting("comment-swipe-right", "Navigation", "comment swipe right upvote downvote none gesture"),
    setting("comment-hierarchy-style", "Comments", "comment hierarchy guide lines parent connectors replies thread"),
    setting("highlight-new-comments", "Comments", "highlight new comments replies"),
    setting("show-comment-avatars", "Comments", "avatars snoovatar profile picture comment author image"),
    setting("top-level-comment-jump", "Comments", "jump next previous top-level parent comment navigate button thread"),
    setting("use-suggested-comment-sort", "Comments", "suggested comment sort Reddit default sorting"),
    setting("comment-actions", "Comments", "comment actions placement follow balanced left right interaction side"),
    setting("comment-time-format", "Comments", "comment time relative local timestamp 24-hour 12-hour am pm"),
    setting("comment-collapse-mode", "Comments", "collapse comments tap header comment hide replies thread fold"),

    setting("notifications", "Notifications", "inbox notifications background activity messages Reddit 15 minute"),
    setting("notification-types", "Notifications", "notification types categories replies mentions private messages Reddit notices Modmail"),
    setting("notify-replies", "Notifications", "replies post comment notifications"),
    setting("notify-mentions", "Notifications", "mentions username notification alerts"),
    setting("notify-private-messages", "Notifications", "private messages PM notifications"),
    setting("notify-reddit-notices", "Notifications", "Reddit notices system messages notification"),
    setting("notify-modmail", "Notifications", "Modmail moderation notification"),

    setting("redreader-compatibility", "Reddit connection", "RedReader compatibility OAuth client ID account encrypted connection"),
    setting("diagnostics", "Diagnostics", "diagnostics debug troubleshooting event history report share clear privacy"),

    // SETTINGSREVIEW01_BACKUP_CHOICES_ARE_SEARCHABLE_V1: the Backup screen's own
    // switches and retention choices lead to its row.
    setting(
        "backup",
        "Backup & restore",
        "saved setups backup restore snapshot import export automatic history folder " +
            "include hidden communities muted communities post drafts saved moderator responses " +
            "private notes tags retention safety snapshots",
    ),

    setting(
        "about",
        "About",
        "help about version build prototype changelog release notes what is new updates history",
    ),
)

// LOWUX3_SETTINGS_SEARCH_SKIPS_ROWS_THE_STATE_HIDES_V1 (OCB-267/278): a match on
// a row the current state does not show (Lock after with the lock off) left a
// lone section heading or a blank list with no "no results"; such keys are
// left out of the result, so sections and hasMatches follow what is shown.
internal fun settingsSearchResult(
    rawQuery: String,
    hiddenKeys: Set<String> = emptySet(),
): SettingsSearchResult {
    val query = normalizeSettingsSearchText(rawQuery)
    if (query.isBlank()) {
        return SettingsSearchResult(
            query = "",
            itemKeys = settingsSearchEntries.mapTo(linkedSetOf()) { it.key },
            sections = settingsSearchEntries.mapTo(linkedSetOf()) { it.section },
        )
    }

    val tokens = query.split(' ').filter(String::isNotBlank)
    val matches = settingsSearchEntries.filter { entry ->
        entry.key !in hiddenKeys && tokens.all(entry.searchableText::contains)
    }
    return SettingsSearchResult(
        query = query,
        itemKeys = matches.mapTo(linkedSetOf()) { it.key },
        sections = matches.mapTo(linkedSetOf()) { it.section },
    )
}

private fun setting(key: String, section: String, terms: String) =
    SettingsSearchEntry(key = key, section = section, terms = terms)

private fun normalizeSettingsSearchText(value: String): String =
    value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9+]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
