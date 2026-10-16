package app.subshell.prototype.ui

// ISSUE16_SAVE_LAST_INTENT_QUEUE_POLICY_V1
internal fun postSaveTargetControlEnabled(
    activeAccountIsAuthenticated: Boolean,
    inFlightFullname: String?,
    targetFullname: String,
): Boolean =
    activeAccountIsAuthenticated &&
        targetFullname.isNotBlank() &&
        (inFlightFullname == null || inFlightFullname == targetFullname)

internal fun queuedPostSaveFollowUp(
    acknowledgedSaved: Boolean,
    queuedDesiredSaved: Boolean?,
): Boolean? =
    queuedDesiredSaved?.takeIf { desired -> desired != acknowledgedSaved }
