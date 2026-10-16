package app.subshell.prototype.data.account

import java.util.Locale

// CROSSPOST01A1A_CANONICAL_SOURCE_IDENTITY_V1
// An explicit Reddit post fullname is required; URLs and local IDs are not converted.
internal fun normalizeCrosspostSourceFullname(value: String): String {
    val normalized = value.trim().lowercase(Locale.ROOT)
    require(normalized.matches(Regex("t3_[a-z0-9]{1,16}"))) {
        "Crosspost source must be a Reddit post fullname"
    }
    return normalized
}
