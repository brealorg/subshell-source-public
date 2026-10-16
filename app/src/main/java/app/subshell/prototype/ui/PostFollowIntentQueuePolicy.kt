package app.subshell.prototype.ui

// FOLLOWPOST01A1B_LAST_INTENT_POLICY_V1
internal fun postFollowTargetControlEnabled(
    activeAccountIsAuthenticated: Boolean,
    inFlightFullname: String?,
    targetFullname: String,
): Boolean =
    activeAccountIsAuthenticated &&
        targetFullname.isNotBlank() &&
        (
            inFlightFullname == null ||
                inFlightFullname == targetFullname
            )

internal fun queuedPostFollowFollowUp(
    acknowledgedFollowed: Boolean,
    queuedDesiredFollowed: Boolean?,
): Boolean? =
    queuedDesiredFollowed
        ?.takeIf { desired ->
            desired != acknowledgedFollowed
        }

internal fun postFollowDesiredState(
    currentKnownState: Boolean?,
): Boolean =
    currentKnownState != true

internal fun postFollowActionLabel(
    currentKnownState: Boolean?,
): String =
    if (currentKnownState == true) {
        "Unfollow post"
    } else {
        "Follow post"
    }
