package app.subshell.prototype.ui

internal val selectableDrawerSidePreferences: List<DrawerSidePreference> = listOf(
    DrawerSidePreference.LEFT,
    DrawerSidePreference.RIGHT,
)

internal fun explicitDrawerSidePreference(
    preference: DrawerSidePreference,
    interactionSide: InteractionSide,
): DrawerSidePreference = when (preference) {
    DrawerSidePreference.FOLLOW -> when (interactionSide) {
        InteractionSide.LEFT -> DrawerSidePreference.LEFT
        InteractionSide.RIGHT -> DrawerSidePreference.RIGHT
    }
    DrawerSidePreference.LEFT -> DrawerSidePreference.LEFT
    DrawerSidePreference.RIGHT -> DrawerSidePreference.RIGHT
}
