package app.subshell.prototype.ui

enum class InteractionSide(val label: String) {
    LEFT("Left"),
    RIGHT("Right"),
}

enum class DrawerSidePreference(val label: String) {
    FOLLOW("Follow"),
    LEFT("Left"),
    RIGHT("Right"),
}

enum class CommentActionPreference(val label: String) {
    FOLLOW("Follow"),
    LEFT("Left"),
    BALANCED("Balanced"),
    RIGHT("Right"),
}

fun InteractionSide.followLabel(): String = "Follow · $label"

fun InteractionSide.floatingActionAtStart(): Boolean = this == InteractionSide.LEFT

fun InteractionSide.mediaActionsAtStart(): Boolean = this == InteractionSide.LEFT

/**
 * VIDEO10_INTERACTION_SIDE_IS_PHYSICAL_IN_RTL_V1 (#227 audit C6): the reader picks a
 * physical side; in a right-to-left layout the row's start is on the right.
 */
fun physicalLeftIsRowStart(left: Boolean, layoutDirection: androidx.compose.ui.unit.LayoutDirection): Boolean =
    if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) !left else left

fun InteractionSide.composerActionsAtStart(): Boolean = this == InteractionSide.LEFT

fun DrawerSidePreference.resolve(interactionSide: InteractionSide): InteractionSide = when (this) {
    DrawerSidePreference.FOLLOW -> interactionSide
    DrawerSidePreference.LEFT -> InteractionSide.LEFT
    DrawerSidePreference.RIGHT -> InteractionSide.RIGHT
}

fun CommentActionPreference.resolve(
    interactionSide: InteractionSide,
): CommentActionPlacement = when (this) {
    CommentActionPreference.FOLLOW -> when (interactionSide) {
        InteractionSide.LEFT -> CommentActionPlacement.LEFT
        InteractionSide.RIGHT -> CommentActionPlacement.RIGHT
    }
    CommentActionPreference.LEFT -> CommentActionPlacement.LEFT
    CommentActionPreference.BALANCED -> CommentActionPlacement.BALANCED
    CommentActionPreference.RIGHT -> CommentActionPlacement.RIGHT
}

internal fun legacyInteractionSide(
    placement: CommentActionPlacement?,
): InteractionSide = when (placement) {
    CommentActionPlacement.LEFT -> InteractionSide.LEFT
    CommentActionPlacement.BALANCED,
    CommentActionPlacement.RIGHT,
    null -> InteractionSide.RIGHT
}

internal fun legacyCommentActionPreference(
    placement: CommentActionPlacement?,
): CommentActionPreference = when (placement) {
    CommentActionPlacement.LEFT,
    CommentActionPlacement.RIGHT,
    null -> CommentActionPreference.FOLLOW
    CommentActionPlacement.BALANCED -> CommentActionPreference.BALANCED
}
