package app.subshell.prototype.ui

internal enum class DrawerDynamicGroup {
    COMMUNITIES,
    CUSTOM_FEEDS,
}

internal data class DrawerDynamicGroupExpansion(
    val communitiesExpanded: Boolean = false,
    val customFeedsExpanded: Boolean = false,
)

internal fun toggleDrawerDynamicGroup(
    current: DrawerDynamicGroupExpansion,
    group: DrawerDynamicGroup,
): DrawerDynamicGroupExpansion = when (group) {
    DrawerDynamicGroup.COMMUNITIES -> {
        if (current.communitiesExpanded) {
            current.copy(communitiesExpanded = false)
        } else {
            DrawerDynamicGroupExpansion(communitiesExpanded = true)
        }
    }
    DrawerDynamicGroup.CUSTOM_FEEDS -> {
        if (current.customFeedsExpanded) {
            current.copy(customFeedsExpanded = false)
        } else {
            DrawerDynamicGroupExpansion(customFeedsExpanded = true)
        }
    }
}
