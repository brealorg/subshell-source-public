package app.subshell.prototype.ui.error

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.toSafeTechnicalDetail

// LOWUI4_NO_DIALOG_ERROR_KIND_V1 (OCB-133): no error was ever presented as a
// dialog; the DIALOG kind and ErrorPresentationDialog are gone, so every kind
// renders as a notice.
enum class ErrorPresentationKind {
    BLOCKING,
    DEGRADED,
    INLINE,
}

enum class ErrorActionKind {
    RETRY,
    SIGN_IN,
    OPEN_SETTINGS,
    DISMISS,
    OPEN_IN_BROWSER,
}

data class ErrorAction(
    val kind: ErrorActionKind,
    val label: String,
) {
    init {
        require(label.isNotBlank())
    }
}

data class ErrorPresentation(
    val kind: ErrorPresentationKind,
    val title: String,
    val message: String,
    val technicalDetail: String? = null,
    val primaryAction: ErrorAction? = null,
    val secondaryAction: ErrorAction? = null,
    val failureDetails: FailureDetails? = null,
) {
    init {
        require(title.isNotBlank())
        require(message.isNotBlank())
        require(technicalDetail == null || technicalDetail.isNotBlank())
        require(primaryAction == null || primaryAction != secondaryAction)
    }

    fun withFailureDetails(details: FailureDetails?): ErrorPresentation {
        details ?: return this
        return copy(
            technicalDetail = technicalDetail ?: details.toSafeTechnicalDetail(),
            failureDetails = details,
        )
    }
}
