package app.subshell.prototype.ui.screens

// SUBSHELL_SETTINGS03A_TASK_BASED_MATERIAL_SETTINGS_V1
internal enum class SettingsDestination(
    val title: String,
    val description: String,
    internal val legacySections: Set<String> = emptySet(),
) {
    ROOT(
        title = "Settings",
        description = "",
    ),
    ACCOUNTS(
        title = "Accounts",
        description = "Reddit accounts, privacy and connection",
        legacySections = setOf("Accounts", "Account privacy", "Reddit connection"),
    ),
    APPEARANCE(
        title = "Appearance",
        description = "Theme, colours, voting, fonts and motion",
        legacySections = setOf("Appearance", "Accessibility"),
    ),
    NAVIGATION(
        title = "Navigation & gestures",
        description = "Controls, side menu, navigation bars and search",
        legacySections = setOf("Home & feeds", "Navigation", "Search"),
    ),
    FEEDS(
        title = "Feeds & content",
        description = "Unread state, sensitive content and filters",
        legacySections = setOf("Feed", "Privacy"),
    ),
    MEDIA(
        title = "Media & links",
        description = "Playback, quality, storage and captions",
        legacySections = setOf("Media & links"),
    ),
    COMMENTS(
        title = "Comments & notifications",
        description = "Comment behaviour, actions and inbox alerts",
        legacySections = setOf("Comments", "Notifications"),
    ),
    BACKUP(
        title = "Backup & restore",
        description = "Saved setups, history and safety snapshots",
    ),
    ADVANCED(
        title = "Advanced",
        description = "Developer tools and diagnostics",
        legacySections = setOf("Diagnostics", "Prototype states"),
    ),
    ABOUT(
        title = "Help & about",
        description = "Version, help and application information",
    ),
    NOTIFICATION_TYPES(
        title = "Notification types",
        description = "Choose which Reddit activity can notify you",
    ),
    // ISSUE239_WATCHED_COMMUNITIES_IN_SETTINGS_V1
    NEW_POST_NOTIFICATIONS(
        title = "New posts",
        description = "Communities that notify you about new posts",
    ),
    CONTENT_FILTERS(
        title = "Content filters",
        description = "",
    ),
    SENSITIVE_MEDIA_LAB(
        title = "Sensitive media Reddit lab",
        description = "",
    ),
    DIAGNOSTICS(
        title = "Diagnostics",
        description = "Local structured event history and shareable reports",
    );

    companion object {
        fun fromName(name: String): SettingsDestination =
            entries.firstOrNull { it.name == name } ?: ROOT
    }
}

internal val settingsRootDestinations: List<SettingsDestination> = listOf(
    SettingsDestination.ACCOUNTS,
    SettingsDestination.APPEARANCE,
    SettingsDestination.NAVIGATION,
    SettingsDestination.FEEDS,
    SettingsDestination.MEDIA,
    SettingsDestination.COMMENTS,
    SettingsDestination.BACKUP,
    SettingsDestination.ADVANCED,
    SettingsDestination.ABOUT,
)

internal fun settingsSectionVisible(
    destination: SettingsDestination,
    query: String,
    searchResult: SettingsSearchResult,
    section: String,
    debug: Boolean,
): Boolean {
    if (section == "Prototype states" && !debug) return false
    return when (destination) {
        SettingsDestination.ROOT ->
            query.isNotBlank() && searchResult.isSectionVisible(section)
        else ->
            section in destination.legacySections &&
                searchResult.isSectionVisible(section)
    }
}

internal fun settingsSectionTitle(
    destination: SettingsDestination,
    legacySection: String,
): String = when (destination) {
    SettingsDestination.APPEARANCE -> when (legacySection) {
        "Appearance" -> "Theme & style"
        "Accessibility" -> "Motion"
        else -> legacySection
    }
    SettingsDestination.NAVIGATION -> when (legacySection) {
        "Home & feeds" -> "Navigation bars"
        "Navigation" -> "Controls & side menu"
        else -> legacySection
    }
    SettingsDestination.FEEDS ->
        if (legacySection == "Privacy") "Hidden communities" else "Feed & content"
    SettingsDestination.ACCOUNTS ->
        if (legacySection == "Reddit connection") "Connection" else legacySection
    SettingsDestination.ADVANCED ->
        if (legacySection == "Prototype states") "Developer" else legacySection
    else -> legacySection
}

internal fun settingsDestinationBackTarget(
    destination: SettingsDestination,
    returnDestination: SettingsDestination,
): SettingsDestination = when (destination) {
    SettingsDestination.ROOT -> SettingsDestination.ROOT
    SettingsDestination.NOTIFICATION_TYPES,
    SettingsDestination.NEW_POST_NOTIFICATIONS,
    SettingsDestination.CONTENT_FILTERS,
    SettingsDestination.SENSITIVE_MEDIA_LAB,
    SettingsDestination.DIAGNOSTICS,
    SettingsDestination.BACKUP,
    SettingsDestination.ABOUT -> returnDestination
    else -> SettingsDestination.ROOT
}
