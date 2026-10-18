import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.net.URISyntaxException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.serialization)
}

// SUBSHELL_AUTH05E2_PRODUCTION_APP_LINK_V1
private val AUTH05E2_PRODUCTION_CALLBACK_HOST = "brealorg.github.io"
private val AUTH05E2_PRODUCTION_CALLBACK_PATH = "/oauth/callback"
private val AUTH05E2_PRODUCTION_CALLBACK_URI =
    "https://$AUTH05E2_PRODUCTION_CALLBACK_HOST$AUTH05E2_PRODUCTION_CALLBACK_PATH"

// SUBSHELL_AUTH04_GRADLE_KTS_COMPILE_FIX_V1
private val AUTH04_DEFAULT_CALLBACK_PATH = "/oauth/callback"
private val AUTH04_DISABLED_CALLBACK_HOST = "auth-callback.invalid"
private val AUTH04_VALIDATION_CALLBACK_HOST = "auth04-fixture.invalid"

private fun String.asBuildConfigStringLiteral(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private data class Auth04OAuthConfiguration(
    val configured: Boolean,
    val clientId: String = "",
    val redirectUri: String = "",
    val redirectHost: String = "auth-callback.invalid",
    val redirectPath: String = "/oauth/callback",
)

private fun auth04ConfigurationFailure(message: String): Nothing =
    throw GradleException("AUTH04 OAuth configuration rejected: $message")

private fun isReservedAuth04CallbackHost(host: String): Boolean =
    host == "localhost" ||
        host.endsWith(".localhost") ||
        host.endsWith(".local") ||
        host.endsWith(".test") ||
        host.endsWith(".example") ||
        host.endsWith(".invalid") ||
        host.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}""")) ||
        host.contains(':')

private fun validateAuth04ProductionConfiguration(
    rawClientId: String?,
    rawRedirectUri: String?,
): Auth04OAuthConfiguration {
    val clientId = rawClientId.orEmpty()
    val redirectUriText = rawRedirectUri.orEmpty()

    if (clientId.isEmpty() && redirectUriText.isEmpty()) {
        return Auth04OAuthConfiguration(configured = false)
    }
    if (clientId.isEmpty() || redirectUriText.isEmpty()) {
        auth04ConfigurationFailure("client ID and redirect URI must be supplied together")
    }
    if (clientId != clientId.trim() || redirectUriText != redirectUriText.trim()) {
        auth04ConfigurationFailure("leading or trailing whitespace is not allowed")
    }
    if (!clientId.matches(Regex("""[A-Za-z0-9_-]{8,128}"""))) {
        auth04ConfigurationFailure("client ID must use 8-128 URL-safe identifier characters")
    }

    val redirectUri = try {
        URI(redirectUriText)
    } catch (_: URISyntaxException) {
        auth04ConfigurationFailure("redirect URI is malformed")
    }

    if (!redirectUri.isAbsolute || redirectUri.scheme != "https") {
        auth04ConfigurationFailure("redirect URI must use canonical lowercase HTTPS")
    }
    if (redirectUri.rawUserInfo != null || redirectUri.port != -1) {
        auth04ConfigurationFailure("userinfo and explicit ports are not allowed")
    }
    if (redirectUri.rawQuery != null || redirectUri.rawFragment != null) {
        auth04ConfigurationFailure("query and fragment components are not allowed")
    }

    val host = redirectUri.host?.lowercase()
        ?: auth04ConfigurationFailure("redirect URI must contain an ASCII DNS hostname")
    if (
        !host.matches(
            Regex(
                """[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?""" +
                    """(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+""",
            ),
        )
    ) {
        auth04ConfigurationFailure("redirect host must be a valid multi-label DNS hostname")
    }
    if (isReservedAuth04CallbackHost(host)) {
        auth04ConfigurationFailure("redirect host must be owned and non-reserved")
    }

    val path = redirectUri.rawPath.orEmpty()
    if (
        path.isEmpty() ||
        path == "/" ||
        !path.matches(Regex("""/[A-Za-z0-9._~/-]+""")) ||
        path.contains("//") ||
        path.split('/').any { it == "." || it == ".." }
    ) {
        auth04ConfigurationFailure("redirect path must be a canonical non-root absolute path")
    }

    val canonicalRedirectUri = "https://$host$path"
    if (redirectUriText != canonicalRedirectUri) {
        auth04ConfigurationFailure("redirect URI must already be canonical")
    }
    if (canonicalRedirectUri != AUTH05E2_PRODUCTION_CALLBACK_URI) {
        auth04ConfigurationFailure(
            "redirect URI must match the published AUTH05E2 production callback",
        )
    }

    return Auth04OAuthConfiguration(
        configured = true,
        clientId = clientId,
        redirectUri = canonicalRedirectUri,
        redirectHost = host,
        redirectPath = path,
    )
}

private fun parseAuth04BooleanProperty(name: String, rawValue: String?): Boolean {
    if (rawValue == null) return false
    return rawValue.toBooleanStrictOrNull()
        ?: auth04ConfigurationFailure("$name must be exactly true or false")
}

private val auth04RedditClientId = providers
    .gradleProperty("subshell.auth.redditClientId")
    .orElse(providers.environmentVariable("SUBSHELL_REDDIT_CLIENT_ID"))
    .orNull

private val auth04RedirectUri = providers
    .gradleProperty("subshell.auth.redirectUri")
    .orElse(providers.environmentVariable("SUBSHELL_AUTH_REDIRECT_URI"))
    .orNull

private val auth04ProductionConfiguration =
    validateAuth04ProductionConfiguration(
        rawClientId = auth04RedditClientId,
        rawRedirectUri = auth04RedirectUri,
    )

private val auth04AppLinkValidationFixture = parseAuth04BooleanProperty(
    name = "subshell.auth.appLinkValidationFixture",
    rawValue = providers
        .gradleProperty("subshell.auth.appLinkValidationFixture")
        .orNull,
)

if (auth04AppLinkValidationFixture && auth04ProductionConfiguration.configured) {
    auth04ConfigurationFailure(
        "the manifest validation fixture cannot be combined with production configuration",
    )
}

private val auth04ManifestCallbackEnabled =
    auth04ProductionConfiguration.configured || auth04AppLinkValidationFixture
private val auth04ManifestCallbackHost = when {
    auth04ProductionConfiguration.configured ->
        auth04ProductionConfiguration.redirectHost
    auth04AppLinkValidationFixture ->
        AUTH04_VALIDATION_CALLBACK_HOST
    else ->
        AUTH04_DISABLED_CALLBACK_HOST
}
private val auth04ManifestCallbackPath = when {
    auth04ProductionConfiguration.configured ->
        auth04ProductionConfiguration.redirectPath
    else ->
        AUTH04_DEFAULT_CALLBACK_PATH
}

// SUBSHELL_AUTH05C_APPLICATION_ID_MIGRATION_V1
private val AUTH05C_FINAL_APPLICATION_ID = "io.github.brealorg.subshell"
private val AUTH05C_DEBUG_APPLICATION_ID_SUFFIX = ".debug"
private val AUTH05C_DEBUG_APPLICATION_ID =
    AUTH05C_FINAL_APPLICATION_ID + AUTH05C_DEBUG_APPLICATION_ID_SUFFIX
private val AUTH05C_TEST_APPLICATION_ID =
    AUTH05C_DEBUG_APPLICATION_ID + ".test"
private val AUTH05C_COMPONENT_NAMESPACE = "app.subshell.prototype"

// Optional parallel debug installations. Canonical debug remains the default.
private val subshellDebugSlots = setOf("dev1", "dev2")
private val subshellDebugSlot = providers
    .gradleProperty("subshell.debugSlot")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.also { selected ->
        require(selected in subshellDebugSlots) {
            "subshell.debugSlot must be dev1 or dev2"
        }
    }
private val subshellSelectedDebugApplicationIdSuffix =
    AUTH05C_DEBUG_APPLICATION_ID_SUFFIX +
        subshellDebugSlot?.let { ".$it" }.orEmpty()
private val subshellSelectedTestApplicationId =
    AUTH05C_FINAL_APPLICATION_ID +
        subshellSelectedDebugApplicationIdSuffix +
        ".test"
private val subshellDebugAppLabel =
    when (subshellDebugSlot) {
        "dev1" -> "subshell DEV 1"
        "dev2" -> "subshell DEV 2"
        else -> "subshell"
    }
private val subshellDebugLauncherIcon =
    when (subshellDebugSlot) {
        "dev1" -> "@mipmap/ic_launcher_dev1"
        "dev2" -> "@mipmap/ic_launcher_dev2"
        else -> "@mipmap/ic_launcher"
    }

// SUBSHELL_AUTH05D2_RELEASE_SIGNING_WIRING_V1
private val AUTH05D2_RELEASE_KEY_ALIAS = "subshell-app-signing"
private val AUTH05D2_RELEASE_KEYSTORE_TYPE = "JKS"
private val AUTH05D2_EXPECTED_RELEASE_CERT_SHA256 =
    "52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:" +
        "39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84"
private val AUTH05D2_EXPECTED_KEYSTORE_SHA256 =
    "7702295c7184a73cb1baba354becaabcdc186cce76de19a470c41bd66afeb2a7"

private data class Auth05d2ReleaseSigningInputs(
    val configured: Boolean,
    val keystore: File? = null,
    val storePasswordFile: File? = null,
    val keyPasswordFile: File? = null,
    val storePassword: String = "",
    val keyPassword: String = "",
)

private fun auth05d2SigningFailure(message: String): Nothing =
    throw GradleException("AUTH05D2 release signing rejected: $message")

private fun auth05d2InputPresenceConfigured(
    rawKeystorePath: String?,
    rawStorePasswordFile: String?,
    rawKeyPasswordFile: String?,
): Boolean {
    val values = listOf(
        rawKeystorePath?.takeIf { it.isNotEmpty() },
        rawStorePasswordFile?.takeIf { it.isNotEmpty() },
        rawKeyPasswordFile?.takeIf { it.isNotEmpty() },
    )
    if (values.all { it == null }) return false
    if (values.any { it == null }) {
        auth05d2SigningFailure(
            "keystore path, store-password file, and key-password file must be supplied together",
        )
    }
    return true
}

private fun auth05d2PrivateFileModeAccepted(file: File): Boolean =
    runCatching {
        Files.getPosixFilePermissions(file.toPath()).none { permission ->
            permission in setOf(
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_WRITE,
                PosixFilePermission.OTHERS_EXECUTE,
            )
        }
    }.getOrDefault(true)

private fun auth05d2CanonicalExternalFile(
    rawPath: String,
    label: String,
    sourceRoot: File,
): File {
    if (rawPath != rawPath.trim()) {
        auth05d2SigningFailure("$label contains leading or trailing whitespace")
    }
    val path = File(rawPath)
    if (!path.isAbsolute) {
        auth05d2SigningFailure("$label must be an absolute external path")
    }
    val canonical = path.canonicalFile
    if (!canonical.isFile) {
        auth05d2SigningFailure("$label does not identify a regular file")
    }
    if (canonical.toPath().startsWith(sourceRoot.canonicalFile.toPath())) {
        auth05d2SigningFailure("$label must remain outside the source tree")
    }
    if (!auth05d2PrivateFileModeAccepted(canonical)) {
        auth05d2SigningFailure("$label must not grant group or other permissions")
    }
    return canonical
}

private fun auth05d2ReadPasswordFile(file: File, label: String): String {
    val value = file.readText(Charsets.UTF_8)
    if (value.length < 24) {
        auth05d2SigningFailure("$label must contain at least 24 characters")
    }
    if (value.any { it == '\n' || it == '\r' || it == '\u0000' }) {
        auth05d2SigningFailure("$label must contain exactly one newline-free secret")
    }
    return value
}

private fun loadAuth05d2ReleaseSigningInputs(
    rawKeystorePath: String?,
    rawStorePasswordFile: String?,
    rawKeyPasswordFile: String?,
    sourceRoot: File,
): Auth05d2ReleaseSigningInputs {
    if (
        !auth05d2InputPresenceConfigured(
            rawKeystorePath,
            rawStorePasswordFile,
            rawKeyPasswordFile,
        )
    ) {
        return Auth05d2ReleaseSigningInputs(configured = false)
    }

    val keystore = auth05d2CanonicalExternalFile(
        rawKeystorePath.orEmpty(),
        "keystore",
        sourceRoot,
    )
    val storePasswordFile = auth05d2CanonicalExternalFile(
        rawStorePasswordFile.orEmpty(),
        "store-password file",
        sourceRoot,
    )
    val keyPasswordFile = auth05d2CanonicalExternalFile(
        rawKeyPasswordFile.orEmpty(),
        "key-password file",
        sourceRoot,
    )
    if (storePasswordFile == keyPasswordFile) {
        auth05d2SigningFailure("store and private-key passwords require separate files")
    }

    val storePassword = auth05d2ReadPasswordFile(
        storePasswordFile,
        "store-password file",
    )
    val keyPassword = auth05d2ReadPasswordFile(
        keyPasswordFile,
        "key-password file",
    )
    if (storePassword == keyPassword) {
        auth05d2SigningFailure("store and private-key passwords must differ")
    }

    return Auth05d2ReleaseSigningInputs(
        configured = true,
        keystore = keystore,
        storePasswordFile = storePasswordFile,
        keyPasswordFile = keyPasswordFile,
        storePassword = storePassword,
        keyPassword = keyPassword,
    )
}

private val auth05d2ReleaseSigningInputs =
    loadAuth05d2ReleaseSigningInputs(
        rawKeystorePath = providers
            .environmentVariable("SUBSHELL_RELEASE_KEYSTORE_PATH")
            .orNull,
        rawStorePasswordFile = providers
            .environmentVariable("SUBSHELL_RELEASE_STORE_PASSWORD_FILE")
            .orNull,
        rawKeyPasswordFile = providers
            .environmentVariable("SUBSHELL_RELEASE_KEY_PASSWORD_FILE")
            .orNull,
        sourceRoot = rootProject.projectDir,
    )

// SUBSHELL_AUTH05B_PRODUCTION_IDENTITY_BOUNDARY_V1
private data class Auth05ProductionIdentityConfiguration(
    val configured: Boolean,
    val applicationId: String = "",
    val signingCertSha256: String = "",
)

private fun auth05IdentityFailure(message: String): Nothing =
    throw GradleException("AUTH05 production identity rejected: $message")

private fun isReservedAuth05ApplicationId(applicationId: String): Boolean =
    applicationId == "app.subshell.prototype" ||
        applicationId.startsWith("android.") ||
        applicationId.startsWith("com.example.") ||
        applicationId.startsWith("org.example.") ||
        applicationId.startsWith("net.example.") ||
        applicationId.endsWith(".prototype") ||
        applicationId.endsWith(".debug") ||
        applicationId.endsWith(".dev") ||
        applicationId.endsWith(".test") ||
        applicationId.endsWith(".example") ||
        applicationId.endsWith(".invalid")

private fun validateAuth05ProductionIdentity(
    rawApplicationId: String?,
    rawSigningCertSha256: String?,
): Auth05ProductionIdentityConfiguration {
    val applicationId = rawApplicationId.orEmpty()
    val signingCertSha256 = rawSigningCertSha256.orEmpty()

    if (applicationId.isEmpty() && signingCertSha256.isEmpty()) {
        return Auth05ProductionIdentityConfiguration(configured = false)
    }
    if (applicationId.isEmpty() || signingCertSha256.isEmpty()) {
        auth05IdentityFailure(
            "final application ID and release signing certificate SHA-256 must be supplied together",
        )
    }
    if (
        applicationId != applicationId.trim() ||
        signingCertSha256 != signingCertSha256.trim()
    ) {
        auth05IdentityFailure("leading or trailing whitespace is not allowed")
    }
    if (applicationId.length > 255) {
        auth05IdentityFailure("application ID exceeds 255 characters")
    }

    val segments = applicationId.split('.')
    if (
        segments.size < 3 ||
        segments.any {
            !it.matches(Regex("""[a-z][a-z0-9_]{0,62}"""))
        }
    ) {
        auth05IdentityFailure(
            "application ID must contain at least three canonical lowercase package segments",
        )
    }
    if (isReservedAuth05ApplicationId(applicationId)) {
        auth05IdentityFailure(
            "application ID must be final, non-placeholder, and non-development",
        )
    }
    if (
        !signingCertSha256.matches(
            Regex("""(?:[0-9A-F]{2}:){31}[0-9A-F]{2}"""),
        )
    ) {
        auth05IdentityFailure(
            "release signing certificate SHA-256 must be canonical uppercase colon-separated hex",
        )
    }

    return Auth05ProductionIdentityConfiguration(
        configured = true,
        applicationId = applicationId,
        signingCertSha256 = signingCertSha256,
    )
}

private val auth05FinalApplicationId = providers
    .gradleProperty("subshell.identity.applicationId")
    .orElse(providers.environmentVariable("SUBSHELL_FINAL_APPLICATION_ID"))
    .orNull

private val auth05FinalSigningCertSha256 = providers
    .gradleProperty("subshell.identity.signingCertSha256")
    .orElse(
        providers.environmentVariable(
            "SUBSHELL_FINAL_SIGNING_CERT_SHA256",
        ),
    )
    .orNull

private val auth05ProductionIdentity =
    validateAuth05ProductionIdentity(
        rawApplicationId = auth05FinalApplicationId,
        rawSigningCertSha256 = auth05FinalSigningCertSha256,
    )

// AUTH05C applies the selected application ID while keeping release signing
// externally gated and intentionally absent from source.
private val auth05ApplicationIdMigrationApplied = true


// SUBSHELL_BETA01_RELEASE_VERSION_OVERRIDE_V1
// Historical feature contracts intentionally key off the canonical development
// milestone below. Public release artifacts may override package version identity
// through explicit, non-secret Gradle properties without rewriting that milestone.
private val subshellReleaseVersionNameOverride =
    providers.gradleProperty("subshell.release.versionName").orNull
private val subshellReleaseVersionCodeOverride =
    providers.gradleProperty("subshell.release.versionCode").orNull?.let { raw ->
        raw.toIntOrNull()?.takeIf { it > 0 }
            ?: throw GradleException("Release version code override must be a positive integer")
    }
private val subshellReleaseVersionOverrideConfigured = when {
    subshellReleaseVersionNameOverride == null && subshellReleaseVersionCodeOverride == null -> false
    subshellReleaseVersionNameOverride == null || subshellReleaseVersionCodeOverride == null ->
        throw GradleException("Release version name and code overrides must be supplied together")
    subshellReleaseVersionNameOverride != subshellReleaseVersionNameOverride.trim() ->
        throw GradleException("Release version name override must not contain surrounding whitespace")
    subshellReleaseVersionNameOverride.isBlank() ->
        throw GradleException("Release version name override must not be blank")
    else -> true
}

android {
    namespace = AUTH05C_COMPONENT_NAMESPACE
    compileSdk = 37

    defaultConfig {
        applicationId = AUTH05C_FINAL_APPLICATION_ID
        testApplicationId = AUTH05C_TEST_APPLICATION_ID
        if (subshellDebugSlot != null) {
            testApplicationId = subshellSelectedTestApplicationId
        }
        minSdk = 26
        targetSdk = 37
        versionCode = 234
        versionName = "0.76.77-profile01a6a7-profile-content-tab-strip-dev"
        // SUBSHELL_VOTE01_LIVE_POST_VOTING_V1
        // SUBSHELL_VOTE01_VOTE_COLOR_PALETTE_V2
        // SUBSHELL_VOTE01_COMMENT_VOTING_AND_NEUTRAL_PALETTE_V3
        // SUBSHELL_VOTE01_SHARED_VOTE_CONTROLS_V4
        // SUBSHELL_SCROLL01_HOME_SCROLL_TO_TOP_EDGE_TRIGGER_V1
        // SUBSHELL_FEEDSTABILITY01_SEAMLESS_VIEWPORT_PAGING_V1
        // SUBSHELL_SEARCH01A1_CANONICAL_SESSION_PROVIDER_FOUNDATION_V1
        // SUBSHELL_SEARCH01B1_FULL_SCREEN_SEARCH_WORKSPACE_V1
        // SUBSHELL_SEARCH01C1_FEDERATED_REDDIT_POSTS_COMMUNITIES_V1
        // SUBSHELL_SEARCH01C1A1_AUTH_DIRECTORY_MAIN_THREAD_ISOLATION_V1
        // SUBSHELL_SEARCH01C2_LIVE_REDDIT_PEOPLE_V1
        // SUBSHELL_HOMEACTION01_POST_ACTIONS_MEDIA_V1
        // SUBSHELL_HOMEACTION01_RUNTIME_POLISH_V2
        // SUBSHELL_HOMEACTION01_RUNTIME_POLISH_V3
        // SUBSHELL_HOMEACTION01_FULLSCREEN_GALLERY_GESTURE_HANDOFF_V4
        // SUBSHELL_PROFILE01A5_HYDRATION_NEW_COMMENT_ISOLATION_V1
        // SUBSHELL_PROFILE01A5_INMEMORY_HYDRATION_MERGE_V1
        // SUBSHELL_PROFILE01A5_BRANCH_RAIL_COLLAPSE_COHERENCE_V1
        // SUBSHELL_PROFILE01A5_CANONICAL_LAYOUT_IDENTITY_SETTLE_V1
        // SUBSHELL_PROFILE01A5_UNIQUE_COMMENT_IDENTITY_V1
        // SUBSHELL_PROFILE01A5_CANONICAL_TARGET_CONTINUITY_V1
        // SUBSHELL_PROFILE01A5_CANONICAL_TARGET_CONTINUITY_V2
        // SUBSHELL_PROFILE01A6_ACCOUNT_HISTORY_FOUNDATION_V1
        // SUBSHELL_PROFILE01A6A2_COMMENTS_SHARED_HISTORY_V1
        // SUBSHELL_PROFILE01A6A3_LIBRARY_SHORTCUT_V1
        // SUBSHELL_PROFILE01A6A4_LIBRARY_POLISH_V1
        // SUBSHELL_PROFILE01A6A5_ADAPTIVE_LIBRARY_DESTINATIONS_V1
        // SUBSHELL_PROFILE01A6A6_INLINE_YOU_DESTINATIONS_V1
        // SUBSHELL_PROFILE01A6A7_PROFILE_CONTENT_TAB_STRIP_V1
        // SUBSHELL_PROFILE01A7A4_REMOTE_HISTORY_WINDOW_STABILITY_V1
        // SUBSHELL_INBOX02A_ACTIVITY_TARGET_LIFECYCLE_V1
        // SUBSHELL_MESSAGES02A_MATERIAL_LIST_PARITY_V1
        // SUBSHELL_MESSAGES02B_TYPED_DETAIL_V1
        // SUBSHELL_MESSAGES02B1_CHAT_FIDELITY_MEDIA_V1
        // SUBSHELL_MESSAGES02B2_TRUSTED_MATRIX_REDIRECT_V1
        // SUBSHELL_MESSAGES02B21_REDDIT_CDN_REDIRECT_V1        // SUBSHELL_THEME01_SYSTEM_BAR_CONTRAST_V1
        // SUBSHELL_MESSAGES02B22_CONVERSATION_TOP_INSET_OWNER_V1
        // SUBSHELL_MESSAGES02C_TYPED_MATERIAL_COMPOSER_V1
        // SUBSHELL_MESSAGES02C1_DIRECT_CHAT_COMPAT_REPLY_V1
        // SUBSHELL_MESSAGES02C2_INBOX_RETURN_IME_ANCHOR_V1
        // SUBSHELL_MESSAGES02C3_RICH_COMPOSER_GIF_MEDIA_V1
        // SUBSHELL_MESSAGES02C4_SHARED_COMPACT_COMPOSER_MATRIX_MEDIA_V1
        // SUBSHELL_MESSAGES02C4R1_POST_STYLE_REPLY_COMPOSER_V1
        // SUBSHELL_MESSAGES02C4R2_NATIVE_RICHTEXT_MEDIA_COMMENTS_CHAT_V1
        // SUBSHELL_MESSAGES02C4R3_MEDIA_COMMENT_AUTHORITATIVE_ACK_RECONCILIATION_V1
        // SUBSHELL_MESSAGES02C4R3R1_MESSAGE_REPLY_ERROR_PRESENTATION_PARITY_V1
        // SUBSHELL_MESSAGES02C5_DIRECT_CHAT_MEDIA_CAPABILITY_FAIL_CLOSED_V1
        // SUBSHELL_MESSAGES02C5R2_MEDIA_REOPEN_MARKDOWN_PROCESSING_CLEANUP_V1
        // SUBSHELL_MESSAGES02C5R3_MATRIX_DIRECT_CHAT_MEDIA_WRITE_V1
        // SUBSHELL_MESSAGES02C6_MATRIX_SESSION_CACHE_HARDENING_V1
        // SUBSHELL_MESSAGES02C6R2_MATRIX_CONFIRMED_MEDIA_ECHO_V1
        // SUBSHELL_MESSAGES02C6R3_NONBLOCKING_MATRIX_ACCEPT_V1
// SUBSHELL_MESSAGES02C6R4_DIRECT_CHAT_TAIL_FOLLOW_V1
// SUBSHELL_MESSAGES02C6R5_OPTIMISTIC_DIRECT_CHAT_MEDIA_ECHO_V1
// SUBSHELL_MESSAGES02C6R5R1_CONFIRMATION_CONTINUITY_V1
// SUBSHELL_MESSAGES02C7_FOREGROUND_MATRIX_DIRECT_CHAT_LIVE_READ_V1
        // SUBSHELL_COMMENTUSER01_COMMENT_AUTHOR_PROFILE_LONGPRESS_V1
        // SUBSHELL_COMMENTUSER01_COMMENT_AUTHOR_PROFILE_TAP_V2
        // SUBSHELL_PROFILEUSER01_PUBLIC_PROFILE_CORRECTNESS_V1
        // SUBSHELL_PROFILEUSER01_PUBLIC_PROFILE_UNAVAILABLE_V2
        // SUBSHELL_CROSSPOST01_LIVE_REDDIT_CROSSPOST_PROJECTION_V1
        // SUBSHELL_CROSSPOST01_RICH_SOURCE_CARD_V2
        // SUBSHELL_CROSSPOST01_COMMUNITY_IDENTITY_HEADER_V22
        // SUBSHELL_DEEPLINK01_CANONICAL_REDDIT_TARGET_V1
        // SUBSHELL_DEEPLINK01_ACTION_VIEW_COLD_WARM_V2
        // SUBSHELL_DEEPLINK01_LINK_HANDLING_ONBOARDING_V3
        // SUBSHELL_DEEPLINK01_LINK_ONBOARDING_GUIDANCE_V32
        // SUBSHELL_PROFILE01A5_OFFSCREEN_HYDRATION_COMMIT_V1
        // SUBSHELL_PROFILE01A5_HYDRATION_INPUT_OWNERSHIP_V1
        // SUBSHELL_PROFILE01A5_FOCUSED_SCROLL_OWNERSHIP_V1
        // SUBSHELL_PROFILE01A5_STABLE_HYDRATION_ANCHOR_V1
        // SUBSHELL_PROFILE01A5_IDLE_EDGE_HYDRATION_V1
        // SUBSHELL_PROFILE01A5_TRANSPORT_MORE_HIDDEN_V1
        // SUBSHELL_PROFILE01A5_BRANCH_AWARE_RAILS_EXPERIMENT_V1
        // SUBSHELL_PROFILE01A5_CONTEXT_ROUTE_LANDING_FENCE_V1
        // SUBSHELL_PROFILE01A5_COLD_CANONICAL_STABILIZATION_V1
        // SUBSHELL_PROFILE01A5_BOUNDARY_AWARE_FOCUSED_LANDING_V1
        // SUBSHELL_PROFILE01A5_GESTURE_BOUNDED_HYDRATION_V1
        // SUBSHELL_PROFILE01A5_FOCUSED_HYDRATION_OWNERSHIP_V1
        // SUBSHELL_PROFILE01A5_SEAMLESS_INLINE_COMMENT_HYDRATION_V1
        // SUBSHELL_PROFILE01A5_CAPPED_COMMENT_NESTING_V1
        // SUBSHELL_PROFILE01A5_CANONICAL_DISCUSSION_OWNERSHIP_V1
        // SUBSHELL_PROFILE01A5_EXACT_PROFILE_COMMENT_RETURN_V1
        // SUBSHELL_PROFILE01A5_ADAPTIVE_FOCUSED_COMMENT_LANDING_V1
        // SUBSHELL_PROFILE01A5_ADB_COMMENT_REPRO_V1
        // SUBSHELL_PROFILE01A5_LAYOUT_IDENTITY_COMMENT_NAV_V1
        // SUBSHELL_PROFILE01A5_FAST_FOCUSED_COMMENT_NAV_V1
        // SUBSHELL_PROFILE01A5_FLAT_COMMENT_VIEWPORT_V1
        // SUBSHELL_PROFILE01A5_TARGET_FIRST_COMMENT_LANDING_V1
        // SUBSHELL_PROFILE01A5_ROUTE_OWNED_COMMENT_NAV_V1
        // SUBSHELL_PROFILE01A5_FULL_THREAD_PRODUCT_PARITY_V1
        // SUBSHELL_PROFILE01A5_DETERMINISTIC_COMMENT_NAV_V1
        // SUBSHELL_PROFILE01A5_COMMENT_PARITY_V1
        // SUBSHELL_SUBS03B_LIVE_COMMUNITY_MEMBERSHIP_UI_V1
        // SUBSHELL_SUBS03B1_OWNER_BOUND_LIVE_COMMUNITY_MEMBERSHIP_UI_V1
        // SUBSHELL_SUBS04A0_RESTORED_SUBS03_MEMBERSHIP_BASELINE_V1
        // SUBSHELL_SUBS04A1_DISCOVERY_INFORMATION_ARCHITECTURE_V1
        // SUBSHELL_SUBS04A2_CONTEXTUAL_DISCOVER_ENTRY_V1
        // SUBSHELL_SUBS04B_INTEGRATED_DISCOVERY_SURFACE_V1
        // SUBSHELL_SUBS04B_DENSITY_POLISH_V2
        // SUBSHELL_SUBS04B_DISCOVERY_FIRST_HIERARCHY_V3
        // SUBSHELL_SUBS04B_COMPACT_JOIN_ACTION_V4
        // SUBSHELL_SUBS04C_COMMUNITY_NAVIGATION_DISCOVERY_CONSOLIDATION_V1
        // SUBSHELL_SUBS04C1_DRAWER_DYNAMIC_GROUPS_V1
        // SUBSHELL_SUBS04C2_COMMUNITIES_TERMINOLOGY_DRAWER_IA_V1
        // SUBSHELL_SUBS04C2_CHECKPOINT_V1
    // SUBSHELL_AUTH08A1_ACCOUNT_LOGOUT_UI_V1
        // SUBSHELL_ERROR01A1_USER_FACING_ERROR_FOUNDATION_V1
        // SUBSHELL_ERROR01A2_CORE_REDDIT_READ_ERRORS_V1
        // SUBSHELL_ERROR01A22_VIEWPORT_ERROR_HOST_V1
        // SUBSHELL_ERROR01A221_ADAPTIVE_DETAILS_V1
        // SUBSHELL_ERROR01A3_MEDIA_BACKUP_ERRORS_V1
        // SUBSHELL_ERROR01A4_REDDIT_WRITE_ERRORS_V1
        // SUBSHELL_ERROR01A5_FINAL_ERROR_AUDIT_V1
        // SUBSHELL_PROFILE01A4A_LIVE_PROFILE_COMMENTS_FOUNDATION_V1
        // SUBSHELL_PROFILE01A4B_LIVE_PROFILE_COMMENTS_UI_V1
        // SUBSHELL_AUTH07B2_SUBSCRIPTION_AWARE_LANDING_V1
        // SUBSHELL_AUTH07C1_AUTOMATIC_AUTH_HOST_RELOAD_V1
        // SUBSHELL_AUTH07C2_FIRST_RUN_REDDIT_ONBOARDING_V1
        // SUBSHELL_REDDIT01C1D_DELETE_OUTCOME_VISIBILITY_V1
        // SUBSHELL_REDDIT01C2_STANDARD_WRITE_AUTHORIZATION_OWNERS_V1
        // SUBSHELL_REDDIT01C3A_COMPOSER_AUTHORIZATION_OWNERS_V1
        // SUBSHELL_REDDIT01C3B_SPECIALIZED_AUTHORIZATION_OWNERS_V1
        // SUBSHELL_REDDIT01C3C1_AUTHORIZATION_CONTEXT_FOUNDATION_V1
        // SUBSHELL_REDDIT01C3C2_POST_MANAGE_AUTHORIZATION_OWNER_V1
        // SUBSHELL_REDDIT01C4_LEGACY_FALLBACK_FENCING_V1
        // SUBSHELL_REDDIT01D1_CORE_READ_DOMAIN_TERMINOLOGY_V1
        // SUBSHELL_REDDIT01D2A_AUTHENTICATED_REDDIT_DATA_TERMINOLOGY_V1
        // SUBSHELL_REDDIT01D3A_REDDIT_UI_STATE_TERMINOLOGY_V1
        // SUBSHELL_REDDIT01D3B_ACTIVE_REDDIT_RUNTIME_UI_TERMINOLOGY_V1
        // SUBSHELL_REDDIT01D4_FINAL_READONLY_TERMINOLOGY_BOUNDARY_V1
        // SUBSHELL_PROFILE01A1_USER_SUBMITTED_FEED_FOUNDATION_V1
        // SUBSHELL_PROFILE01A2_LIVE_USER_POSTS_UI_V1
        // SUBSHELL_PROFILE01A2_RUNTIME_FEED_PARITY_V1
        // SUBSHELL_PROFILE01A3_ACCOUNT_SESSION_UI_CLEANUP_V1
        // SUBSHELL_AUTH07A_FRESH_ACCOUNT_AUTHORIZATION_V1
        // SUBSHELL_AUTH07A1_NONBLOCKING_FEED_BOOTSTRAP_V1
        // SUBSHELL_AUTH07A2_LIVE_ACCOUNT_PERSISTENCE_V1
        // SUBSHELL_AUTH07B1_DAILY_DRIVER_ACCOUNT_POLICY_V1
        // SUBSHELL_COMPOSER06A_REDDIT_IMAGE_UPLOAD_V1
        // SUBSHELL_COMPOSER06B_REDDIT_GALLERY_V1
        // SUBSHELL_COMPOSER06B1_GALLERY_LINK_REPAIR_V1
        // SUBSHELL_COMPOSER06B2_MEDIA_VALIDATION_ROUTING_V1
        // SUBSHELL_COMPOSER06C_GALLERY_METADATA_RENDERING_V1
        // SUBSHELL_COMPOSER06C1_FULLSCREEN_MEDIA_SCRIM_V1
        // SUBSHELL_MEDIA_CAPTION01_PRESENTATION_PREFERENCES_V1
        // SUBSHELL_MEDIA_CAPTION01A_CONTENT_BOUNDS_V1
        // SUBSHELL_COMPOSER07A_REDDIT_VIDEO_UPLOAD_V1
        // SUBSHELL_COMPOSER08_FINAL_V1
        // SUBSHELL_COMPOSER09_SUBMIT_RECONCILIATION_V1
        // SUBSHELL_COMPOSER09_HISTORY_SCOPE_FIX_V1
        // SUBSHELL_COMPOSER09_FINAL_V1
        // SUBSHELL_WRITE02A_OWN_CONTENT_ACTION_POLICY_V1
        // SUBSHELL_WRITE02B_EDIT_OWN_COMMENTS_V3
        // SUBSHELL_WRITE02B_PROVIDER_ERROR_DETAILS_V1
        // SUBSHELL_WRITE02B_INLINE_MEDIA_EDIT_GUARD_V1
        // SUBSHELL_WRITE02B_INLINE_MEDIA_TOKEN_GUARD_V1
        // SUBSHELL_WRITE02C_DELETE_OWN_COMMENTS_V1
        // SUBSHELL_WRITE02D_EDIT_OWN_POSTS_V1
        // SUBSHELL_WRITE02D1_FEED_EDIT_ENTRY_V1
        // SUBSHELL_WRITE02D2_POST_EDITOR_SINGLE_INSET_OWNER_V1
        // SUBSHELL_WRITE02E_DELETE_OWN_POSTS_V1
        // SUBSHELL_WRITE02F_MANAGE_OWN_POSTS_V1
        // SUBSHELL_WRITE02F1_SHARED_FLAIR_AND_SPOILER_TONE_V1
        // SUBSHELL_WRITE02F2_BODY_EDIT_PROBE_AND_NEUTRAL_SPOILER_V1
        // SUBSHELL_REDDIT01A_CORE_READ_SCOPE_FOUNDATION_V1
        // SUBSHELL_REDDIT01B_PERSISTED_AUTHORIZATION_OWNER_V1
        // SUBSHELL_REDDIT01C1_OWNER_BOUND_SHARED_SCOPE_MIGRATION_V1
        // SUBSHELL_COMPOSER08D_MEDIA_UPLOAD_IO_BOUNDARY_V1
        // SUBSHELL_COMPOSER08C_GIF_SUBMIT_STATE_REPAIR_V1
        // SUBSHELL_COMPOSER08B_REMOTE_GIF_READBACK_V1
        // SUBSHELL_COMPOSER08A_REDDIT_GIF_UPLOAD_V1
        // SUBSHELL_COMPOSER07C_VIDEO_VIDEOGIF_POLICY_V1
        // SUBSHELL_COMPOSER07B3_FINAL_V1
        // SUBSHELL_COMPOSER07B2_VIDEOGIF_READBACK_MATRIX_V1
        // SUBSHELL_COMPOSER07B1_VIDEOGIF_READBACK_V1
        // SUBSHELL_COMPOSER07B_REDDIT_VIDEOGIF_V1
        // SUBSHELL_COMPOSER07A6_IMMEDIATE_VIDEO_POSTER_V1
        // SUBSHELL_COMPOSER07A5_WEBSOCKET_REDIRECT_V1
        // SUBSHELL_COMPOSER07A4_VIDEO_LEASE_HOST_V1
        // SUBSHELL_COMPOSER07A3_VIDEO_PIPELINE_DIAGNOSTICS_V1
        // SUBSHELL_COMPOSER07A2_VIDEO_ERROR_ROUTING_V1
        // SUBSHELL_COMPOSER07A1_VIDEO_PREVIEW_FAILURE_DETAILS_V1
        // SUBSHELL_COMPOSER06B_CONTRACT_REPAIR_V1
        // SUBSHELL_COMPOSER05C_SUBMIT_PREFLIGHT_V1
        // SUBSHELL_COMPOSER05B_FIELD_VALIDATION_ROUTING_V1
        // SUBSHELL_COMPOSER05A_REQUIRED_FLAIR_VALIDATION_V1
        // SUBSHELL_COMPOSER05_REDDIT_POST_FLAIR_V1
        // SUBSHELL_COMPOSER_V4_DOCUMENT_EDITOR_V1
        // SUBSHELL_COMPOSER_V4_COMPACT_LINK_PREVIEW_V1
        // SUBSHELL_COMPOSER_V4_LINK_PREVIEW_IMAGE_HANDOFF_V1
        // SUBSHELL_WRITE01B1_TEXT_POST_SUBMIT_TRANSPORT_V1
        // SUBSHELL_WRITE01B2_TEXT_POST_COMPOSER_V1
        // SUBSHELL_WRITE01B2_VALIDATION_FEEDBACK_V1
        // SUBSHELL_ACCOUNT01_ACTIVE_ACCOUNT_PERSISTENCE_V1
        // SUBSHELL_WRITE01A3B_COMMENT_AUTH_SUBMIT_V1
        // SUBSHELL_WRITE01A3A_COMMENT_DRAFT_STATE_V1
        // SUBSHELL_WRITE01A2_COMMENT_SUBMIT_TRANSPORT_V1
        // SUBSHELL_WRITE01A1_COMMENT_WRITE_AUTH_V1
        // SUBSHELL_COMPOSER_HAND01_INTERACTION_SIDE_ACTIONS_V1
        // SUBSHELL_MEDIA_HAND01_CONTROL_ORDER_NO_QUALITY_BADGE_V2
        // SUBSHELL_MEDIA_HAND01_INTERACTION_SIDE_CONTROLS_V1
        // SUBSHELL_NAV_HAND02_FLOATING_ACTIONS_V1
        // SUBSHELL_NAV_HAND01_FOLLOW_CLARITY_V1
        // SUBSHELL_COMPOSER04_FIXED_MEDIA_ABOVE_TEXT_V1
        // SUBSHELL_NAV_HAND01_INTERACTION_SIDE_V1
        // SUBSHELL_CACHE_GALLERY_SEVEN_FIELD_COMPAT_V1
        // SUBS01C3_OPTIONAL_VIEW_ALL_COMMUNITIES_V1
        // SUBS02A_ACCOUNT_SCOPED_COMMUNITY_FAVORITES_V1
        // SUBS02A1_DISPLAYED_JOINED_COUNT_OWNS_LABEL_V1
        // SUBS02B_ACCOUNT_SCOPED_RECENT_COMMUNITIES_V1
        // SUBS02B1_SUBS_RECENT_FEEDS_INFORMATION_ARCHITECTURE_V1
        // SUBS02B2_FAVORITES_SUBS_RECENT_FEEDS_FINAL_IA_V1
        // SUBS02C1_LIVE_CUSTOM_FEEDS_READ_V1
        // SUBS02C2_MATERIAL_CUSTOM_FEED_CREATE_V1
        // SUBS02C3_COMMUNITY_PICKER_AUTH_HANDOFF_V1
        // SUBS02C4_TOP_MODAL_CREATE_FAB_ALIGNMENT_V1
        // SUBS02C5_SCAFFOLD_FAB_CREATE_TRANSPORT_V1
        // SUBS02C6_UNIFIED_CUSTOM_FEED_SESSION_V1
        // SUBS02C7_CUSTOM_FEED_MANAGEMENT_V1
        // SUBS02C8_DRAWER_CUSTOM_FEEDS_V1
        // SUBS02C81_HOME_CUSTOM_FEED_SURFACE_V1
        // SUBS02C82_DRAWER_SELECTION_AND_FEED_LABEL_V1
        // SUBS02D1_ACCOUNT_SCOPED_FAVORITE_CUSTOM_FEEDS_V1
        // SUBS02D2_ACCOUNT_SCOPED_HIDDEN_SUBSCRIPTIONS_V1
        // SUBS02D21_CONCEALED_HIDDEN_SUBSCRIPTIONS_V1
        // SUBS02D22_DEVICE_UNLOCK_HIDDEN_SUBSCRIPTIONS_V1
        // SUBS02D221_NATIVE_BACK_HIDDEN_SUBSCRIPTIONS_V1
        // SUBS02D23_HIDDEN_ENTRY_FIRST_V1
        // INBOX01A1_LIVE_READ_FOUNDATION_V1
        // INBOX01A11_MESSAGE_THREAD_FIX_V1
        // INBOX01A12_EXACT_ROUTE_THREAD_ORDER_V1
        // INBOX01A13_SENT_THREAD_MERGE_V1
        // INBOX01A14_BALANCED_MESSAGE_HISTORY_V1
        // INBOX01A15_COMMUNITY_MESSAGE_THREAD_V1
        // INBOX01B1_SERVER_READ_SYNC_V1
        // INBOX01B11_READ_ALL_DETERMINISTIC_FALLBACK_V1
        // INBOX01B12_CONVERSATION_LIVE_REFRESH_V1
        // INBOX01B13_TAB_UNREAD_BADGES_V1
        // SUBSHELL_INTEGRATION_DEV1_DEV2_V2_PORT01
        // SUBSHELL_INTEGRATION_DEV1_DEV2_V2_PORT02
        // SUBSHELL_INTEGRATION_DEV1_DEV2_V2_PORT03
        // SUBSHELL_INTEGRATION_DEV1_DEV2_V2_PORT04
        // SUBSHELL_INTEGRATION_DEV1_DEV2_V2_PORT05
        // SUBSHELL_INTEGRATION_DEV1_DEV2_V2_PORT06
        // SUBSHELL_INTEGRATION_DEV1_DEV2_V2_PORT07
        // SUBSHELL_INTEGRATION_DEV1_DEV2_V2_PORT08
        // SUBSHELL_USERACTION01A1_REAL_USER_BLOCK_UNBLOCK_V1
        // SUBSHELL_USERACTION01A2_AUTHORITATIVE_BLOCKED_ACCOUNTS_SURFACE_V1
        // SUBSHELL_LIBRARY02A1_NESTED_COMPACT_YOU_LIBRARY_V1
        // SUBSHELL_MOD01A1_MODERATOR_DIRECTORY_MODQUEUE_FOUNDATION_V1
        // SUBSHELL_MOD01A2_APPROVE_REMOVE_MODQUEUE_ACTIONS_V1
        // SUBSHELL_MOD01A21_REMOVAL_REASONS_V1
        // SUBSHELL_MOD01A22_CONDITIONAL_INBOX_MOD_TAB_V1
        // SUBSHELL_MOD01A221_ACCOUNT_SAFE_INBOX_PREVIEW_V1
        buildConfigField("String", "SOURCE_MARKER", "\"SUBSHELL_DRAWER_BALANCE_R31_V1\"")
        buildConfigField("boolean", "FEED04B_BENCHMARK_PROBES", "false")
        buildConfigField(
            "String",
            "DEBUG_SLOT",
            "\"${subshellDebugSlot.orEmpty()}\"",
        )
        // SUBSHELL_MEDIA_PLAYBACK_BEHAVIOUR_CONTRACT_V1_PHASE1
        // SUBSHELL_VIDEO_LAB_FIXTURE_FEED_V1
        // SUBSHELL_MEDIA_PLAYBACK_BEHAVIOUR_CONTRACT_V2_PHASE1
        // SUBSHELL_MEDIA_PERF01_INSTRUMENTATION_V1
        // SUBSHELL_MEDIA_PERF01_PRELOAD_HANDOFF_V1
        // SUBSHELL_MEDIA_PERF01_TERMINAL_ANALYZER_V1
        // SUBSHELL_MEDIA_PERF01_TIMING_PAUSE_TERMINAL_V1
        // SUBSHELL_MEDIA_PERF01_DIRECTIONAL_HANDOFF_V1
        // SUBSHELL_MEDIA_PERF02_VIEWPORT_OWNER_V1
        // SUBSHELL_MEDIA_PERF02_TUNE01_INSTRUMENTATION_V1
        // SUBSHELL_MEDIA_PERF02_TUNE02_RAMPED_AZ_V1
        // SUBSHELL_MEDIA_PERF02_CLEANUP_V1
        // SUBSHELL_MEDIA_UX01A_TRANSPORT_CONTROLS_V1
        // SUBSHELL_MEDIA_UX01B_SEEK_SCRUBBING_V1
        // SUBSHELL_MEDIA_UX01C_VISUAL_POLISH_V1
        // SUBSHELL_MEDIA_UX01C1_HIGH_CONTRAST_NEUTRAL_V1
        // SUBSHELL_MEDIA_UX01C2_TIMELINE_SIZE_PRESETS_V2
        // SUBSHELL_MEDIA_NAV02_IMMERSIVE_HANDOFF_V1
        // SUBSHELL_MEDIA_NAV021_FULLSCREEN_FIT_AND_TOUCH_TARGET_V1
        // SUBSHELL_MEDIA_NAV022_FULLSCREEN_TEXTURE_SYNC_V1
        // SUBSHELL_MEDIA_NAV023_FULLSCREEN_ROTATION_STATE_V1
        // SUBSHELL_MEDIA_NAV0231_SAVEABLE_ROUTE_MIRROR_V1
        // SUBSHELL_MEDIA03A_FORMAT_AWARE_ADAPTIVE_SOURCES_V1
        // SUBSHELL_MEDIA03B_SPLIT_AV_MERGING_SOURCE_V1
        // SUBSHELL_MEDIA03B1_DETERMINISTIC_AV_SYNC_SLATE_V1
        // SUBSHELL_MEDIA_NAV024_CONFIGURATION_STABLE_PLAYER_OWNER_V1
        // SUBSHELL_MEDIA_NAV025_DIRECT_CONFIGURATION_HANDLING_V1
        // SUBSHELL_MEDIA03C1_TRACK_INTELLIGENCE_DIAGNOSTICS_V1
        // SUBSHELL_MEDIA03C11_MODAL_DIAGNOSTICS_CONTROL_ISOLATION_V1
        // SUBSHELL_MEDIA03C2_PLAYBACK_TRACK_SELECTION_V1
        // SUBSHELL_MEDIA03C21_HIERARCHICAL_PLAYBACK_OPTIONS_V1
        // SUBSHELL_MEDIA03C21_SELECTION_STAYS_ON_CHILD_PAGE_V1
        // SUBSHELL_MEDIA04A_SHARED_REMOTE_CACHE_PIPELINE_V1
        // SUBSHELL_MEDIA04A1_DETERMINISTIC_HTTPS_CACHE_RUNTIME_PROOF_V1
        // SUBSHELL_MEDIA04B_NETWORK_AWARE_PRELOAD_POLICY_V1
        // SUBSHELL_MEDIA04C_CACHE_OBSERVABILITY_AND_CLEAR_V1
        // SUBSHELL_MEDIA04D_NETWORK_QUALITY_PROFILES_V1
        // SUBSHELL_MEDIA05A_PERSISTENT_DOWNLOAD_FOUNDATION_V1
        // SUBSHELL_MEDIA05B_DOWNLOAD_ACTION_AND_LIBRARY_UI_V1
        // SUBSHELL_MEDIA05C_BACKGROUND_DOWNLOAD_QUEUE_AND_RECOVERY_V1
        // SUBSHELL_MEDIA05D_DOWNLOAD_STORAGE_MANAGEMENT_V1
        // SUBSHELL_FEEDPOST_V2A_TYPED_ROUTE_ORIGIN_V1
        // SUBSHELL_FEEDPOST_V2B_CANONICAL_ROUTE_STACK_V1
        // SUBSHELL_FEEDPOST_V2C_PER_FEED_SCROLL_ANCHORS_V1
        // SUBSHELL_FEEDPOST_V2E_FULL_PROCESS_STATE_RESTORE_V1
        // SUBSHELL_DATA01_REPOSITORY_BOUNDARY_FAKE_BACKEND_V1
        // SUBSHELL_PROTOCLEAN01A31_PRODUCTION_FIXTURE_BACKEND_ISOLATION_V1
        // SUBSHELL_PROTOCLEAN01A3_LEGACY_PERSISTED_FIXTURE_RETIREMENT_V1
        // SUBSHELL_DATA02_PAGING_REFRESH_CACHE_POLICY_V1
        // SUBSHELL_DATA03_ROOM_OFFLINE_CACHE_V1
        // SUBSHELL_NET01_SECURE_HTTP_BOUNDARY_V1
        // SUBSHELL_NET02_NETWORK_TO_ROOM_SYNC_V1
        // SUBSHELL_AUTH01_SECURE_OAUTH_SESSION_V1
        // SUBSHELL_AUTH02_ACCOUNT_SESSION_INTEGRATION_V1
        // SUBSHELL_AUTH03_CONNECT_ACCOUNT_FLOW_V1
        // SUBSHELL_AUTH04_PRODUCTION_OAUTH_CONFIGURATION_V1
        // SUBSHELL_AUTH05B_PRODUCTION_IDENTITY_BOUNDARY_V1
        // SUBSHELL_AUTH05C_APPLICATION_ID_MIGRATION_V1
        // SUBSHELL_AUTH05D2_RELEASE_SIGNING_WIRING_V1
        // SUBSHELL_AUTH05E2_PRODUCTION_APP_LINK_V1
        // SUBSHELL_AUTH06B_REDREADER_COMPATIBILITY_V1
        // SUBSHELL_AUTH06C_LIVE_REDDIT_READONLY_V1
        // SUBSHELL_FILTER01_LOCAL_CONTENT_FILTERS_V1
        // SUBSHELL_PREF01_PER_COMMUNITY_SORT_PREFERENCES_V1
        // SUBSHELL_COMMENTS03A_NEW_COMMENTS_V1
        // SUBSHELL_NAV_LIFECYCLE01_STATE_RESTORE_V1
        // SUBSHELL_R28_DUAL_VIDEO_RUNTIME_FIXTURES_V1
        // SUBSHELL_R28_VIDEO_VISIBILITY_ARBITRATION_V2

        // AUTH04 configuration is injected from Gradle properties or environment.
        // No OAuth client identifier, redirect URI, or signing material is committed.
        buildConfigField(
            "boolean",
            "AUTH04_REDDIT_OAUTH_CONFIGURED",
            auth04ProductionConfiguration.configured.toString(),
        )
        buildConfigField(
            "String",
            "AUTH04_REDDIT_CLIENT_ID",
            auth04ProductionConfiguration.clientId.asBuildConfigStringLiteral(),
        )
        buildConfigField(
            "String",
            "AUTH04_REDDIT_REDIRECT_URI",
            auth04ProductionConfiguration.redirectUri.asBuildConfigStringLiteral(),
        )
        manifestPlaceholders["auth04ProductionCallbackEnabled"] =
            auth04ManifestCallbackEnabled.toString()
        manifestPlaceholders["auth04ProductionCallbackHost"] =
            auth04ManifestCallbackHost
        manifestPlaceholders["auth04ProductionCallbackPath"] =
            auth04ManifestCallbackPath

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (auth05d2ReleaseSigningInputs.configured) {
            create("subshellRelease") {
                storeFile = auth05d2ReleaseSigningInputs.keystore
                storePassword = auth05d2ReleaseSigningInputs.storePassword
                keyAlias = AUTH05D2_RELEASE_KEY_ALIAS
                keyPassword = auth05d2ReleaseSigningInputs.keyPassword
                storeType = AUTH05D2_RELEASE_KEYSTORE_TYPE
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = AUTH05C_DEBUG_APPLICATION_ID_SUFFIX
            if (subshellDebugSlot != null) {
                applicationIdSuffix =
                    subshellSelectedDebugApplicationIdSuffix
            }
            manifestPlaceholders["subshellDebugAppLabel"] =
                subshellDebugAppLabel
            manifestPlaceholders["subshellDebugLauncherIcon"] =
                subshellDebugLauncherIcon
        }
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = if (subshellDebugSlot == "dev1") {
                subshellSelectedDebugApplicationIdSuffix
            } else {
                ".benchmark"
            }
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isProfileable = true
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "FEED04B_BENCHMARK_PROBES", "true")
            // initWith copied release before the block below configures it,
            // so the benchmark build takes R8 here too: it measures and
            // device-checks the same shrunk code a release ships.
            // PERF01_BASELINE_PROFILE_V1: -Psubshell.baselineProfileSource builds
            // it without R8, so BaselineProfileGenerator records source names;
            // R8 rewrites a source-named profile for its own output, never one
            // recorded from merged classes.
            val baselineProfileSource =
                providers.gradleProperty("subshell.baselineProfileSource").isPresent
            isMinifyEnabled = !baselineProfileSource
            isShrinkResources = !baselineProfileSource
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("release") {
            if (auth05d2ReleaseSigningInputs.configured) {
                signingConfig = signingConfigs.getByName("subshellRelease")
            }
            // R8_RELEASE_SHRINKS_WITHOUT_RENAMING_V1 (owner 2026-10-16): R8
            // removes unused code and resources and optimises; proguard-rules.pro
            // keeps every name, so crash reports (CRASH01) stay readable
            // without a mapping file per release.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources and manifest on the JVM.
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        lintConfig = rootProject.file("lint.xml")

        // STEP3_LAND_LINT_GATE_V1: tools/subshell land runs lintDebug with
        // -Psubshell.landLint=true. It checks only the rules the retired
        // quality01* checkers stood in for, fails on any new finding, and keeps
        // today's findings in a baseline. Plain lintDebug and `check` keep the
        // full lint run.
        if (providers.gradleProperty("subshell.landLint").orNull == "true") {
            val landLintIssues = setOf(
                "NewApi",
                "UnsafeOptInUsageError",
                "AutoboxingStateCreation",
                "ModifierParameter",
                "LocalContextResourcesRead",
                "ConfigurationScreenWidthHeight",
                "MonochromeLauncherIcon",
            )
            checkOnly.addAll(landLintIssues)
            error.addAll(landLintIssues)
            baseline = file("lint-land-baseline.xml")
            abortOnError = true
            ignoreTestSources = true
        }
    }

    androidResources {
        noCompress += listOf("mpd", "m3u8", "m4s", "mp4")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // graphics-path 1.0.1 ships pre-stripped native libraries. Do not ask
        // AGP to strip them again; the build gate verifies every packaged ABI.
        jniLibs.keepDebugSymbols += "**/libandroidx.graphics.path.so"
    }
}

androidComponents {
    finalizeDsl { extension ->
        if (subshellReleaseVersionOverrideConfigured) {
            extension.defaultConfig.versionCode =
                requireNotNull(subshellReleaseVersionCodeOverride)
            extension.defaultConfig.versionName =
                requireNotNull(subshellReleaseVersionNameOverride)
        }
    }
}

room {
    schemaDirectory(layout.buildDirectory.dir("room-schemas").get().asFile.absolutePath)
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.biometric)
    // PERF01_BASELINE_PROFILE_V1: compiles src/main/baseline-prof.txt on
    // install, which a sideloaded APK otherwise never gets (no Play cloud profile).
    implementation(libs.androidx.profileinstaller)
    // PERF01_LEAKCANARY_IN_DEBUG_ONLY_V1: reports leaked activities and
    // composables in lane builds; never part of a release.
    debugImplementation(libs.leakcanary.android)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.media3.dash)
    implementation(libs.androidx.media3.hls)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.ui.views)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.window)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.browser)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    // JVM harness for composables and ViewModels (HARNESS01): Robolectric renders
    // Compose on the JVM; coroutines-test supplies Dispatchers.setMain.
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)

    ksp(libs.androidx.room.compiler)
}

val verifyAuth04ConfigurationPolicy =
    tasks.register("verifyAuth04ConfigurationPolicy") {
        group = "verification"
        description =
            "Verifies fail-closed AUTH04 OAuth configuration policy without external values."

        doLast {
            fun expectRejected(
                label: String,
                clientId: String?,
                redirectUri: String?,
            ) {
                val rejected = runCatching {
                    validateAuth04ProductionConfiguration(clientId, redirectUri)
                }.isFailure
                check(rejected) { "AUTH04 policy fixture was not rejected: $label" }
                println("PASS=AUTH04_REJECTED_${label}")
            }

            val unconfigured =
                validateAuth04ProductionConfiguration(null, null)
            check(!unconfigured.configured)
            println("PASS=AUTH04_UNCONFIGURED_FAIL_CLOSED")

            val structurallyValid = validateAuth04ProductionConfiguration(
                rawClientId = "auth04_validation_client",
                rawRedirectUri =
                    AUTH05E2_PRODUCTION_CALLBACK_URI,
            )
            check(structurallyValid.configured)
            check(
                structurallyValid.redirectUri ==
                    AUTH05E2_PRODUCTION_CALLBACK_URI,
            )
            println("PASS=AUTH04_STRUCTURALLY_VALID_CONFIGURATION")

            expectRejected(
                label = "PARTIAL_CLIENT_ONLY",
                clientId = "auth04_validation_client",
                redirectUri = null,
            )
            expectRejected(
                label = "PARTIAL_REDIRECT_ONLY",
                clientId = null,
                redirectUri =
                    AUTH05E2_PRODUCTION_CALLBACK_URI,
            )
            expectRejected(
                label = "CLIENT_WHITESPACE",
                clientId = " auth04_validation_client",
                redirectUri =
                    AUTH05E2_PRODUCTION_CALLBACK_URI,
            )
            expectRejected(
                label = "CLIENT_METACHARACTER",
                clientId = "auth04/client",
                redirectUri =
                    AUTH05E2_PRODUCTION_CALLBACK_URI,
            )
            expectRejected(
                label = "NON_HTTPS",
                clientId = "auth04_validation_client",
                redirectUri =
                    "http://oauth.auth04-validation.dev/oauth/callback",
            )
            expectRejected(
                label = "LOCALHOST",
                clientId = "auth04_validation_client",
                redirectUri = "https://localhost/oauth/callback",
            )
            expectRejected(
                label = "IP_LITERAL",
                clientId = "auth04_validation_client",
                redirectUri = "https://127.0.0.1/oauth/callback",
            )
            expectRejected(
                label = "RESERVED_DOMAIN",
                clientId = "auth04_validation_client",
                redirectUri = "https://oauth.auth04.invalid/oauth/callback",
            )
            expectRejected(
                label = "EXPLICIT_PORT",
                clientId = "auth04_validation_client",
                redirectUri =
                    "https://oauth.auth04-validation.dev:443/oauth/callback",
            )
            expectRejected(
                label = "QUERY",
                clientId = "auth04_validation_client",
                redirectUri =
                    "https://oauth.auth04-validation.dev/oauth/callback?x=1",
            )
            expectRejected(
                label = "FRAGMENT",
                clientId = "auth04_validation_client",
                redirectUri =
                    "https://oauth.auth04-validation.dev/oauth/callback#x",
            )
            expectRejected(
                label = "ROOT_PATH",
                clientId = "auth04_validation_client",
                redirectUri = "https://oauth.auth04-validation.dev/",
            )
            expectRejected(
                label = "PATH_TRAVERSAL",
                clientId = "auth04_validation_client",
                redirectUri =
                    "https://oauth.auth04-validation.dev/oauth/../callback",
            )
            expectRejected(
                label = "ENCODED_PATH",
                clientId = "auth04_validation_client",
                redirectUri =
                    "https://oauth.auth04-validation.dev/oauth/%2Fcallback",
            )
            expectRejected(
                label = "WRONG_PUBLISHED_CALLBACK",
                clientId = "auth04_validation_client",
                redirectUri =
                    "https://oauth.auth04-validation.dev/oauth/callback",
            )

            println("AUTH05E2_PRODUCTION_CALLBACK_URI=$AUTH05E2_PRODUCTION_CALLBACK_URI")
            println("PASS=AUTH05E2_EXACT_PUBLISHED_CALLBACK_LOCK")
            println("RESULT=SUBSHELL_AUTH04_CONFIGURATION_POLICY_PASS")
        }
    }

val verifyAuth05ProductionIdentityPolicy =
    tasks.register("verifyAuth05ProductionIdentityPolicy") {
        group = "verification"
        description =
            "Verifies fail-closed AUTH05 production identity policy without applying identity."

        doLast {
            fun expectRejected(
                label: String,
                applicationId: String?,
                signingCertSha256: String?,
            ) {
                val rejected = runCatching {
                    validateAuth05ProductionIdentity(
                        applicationId,
                        signingCertSha256,
                    )
                }.isFailure
                check(rejected) {
                    "AUTH05 production identity fixture was not rejected: $label"
                }
                println("PASS=AUTH05_REJECTED_${label}")
            }

            val fixtureFingerprint =
                "10:11:12:13:14:15:16:17:18:19:1A:1B:1C:1D:1E:1F:" +
                    "20:21:22:23:24:25:26:27:28:29:2A:2B:2C:2D:2E:2F"

            val unconfigured =
                validateAuth05ProductionIdentity(null, null)
            check(!unconfigured.configured)
            println("PASS=AUTH05_UNCONFIGURED_IDENTITY_NOT_APPLIED")

            val structurallyValid = validateAuth05ProductionIdentity(
                rawApplicationId = "org.subshell.validationfixture",
                rawSigningCertSha256 = fixtureFingerprint,
            )
            check(structurallyValid.configured)
            check(
                structurallyValid.applicationId ==
                    "org.subshell.validationfixture",
            )
            println("PASS=AUTH05_STRUCTURALLY_VALID_IDENTITY")

            expectRejected(
                label = "PARTIAL_APPLICATION_ID_ONLY",
                applicationId = "org.subshell.validationfixture",
                signingCertSha256 = null,
            )
            expectRejected(
                label = "PARTIAL_SIGNING_CERT_ONLY",
                applicationId = null,
                signingCertSha256 = fixtureFingerprint,
            )
            expectRejected(
                label = "APPLICATION_ID_WHITESPACE",
                applicationId = " org.subshell.validationfixture",
                signingCertSha256 = fixtureFingerprint,
            )
            expectRejected(
                label = "APPLICATION_ID_UPPERCASE",
                applicationId = "org.Subshell.validationfixture",
                signingCertSha256 = fixtureFingerprint,
            )
            expectRejected(
                label = "APPLICATION_ID_TOO_SHORT",
                applicationId = "org.subshell",
                signingCertSha256 = fixtureFingerprint,
            )
            expectRejected(
                label = "APPLICATION_ID_INVALID_SEGMENT",
                applicationId = "org.sub-shell.validationfixture",
                signingCertSha256 = fixtureFingerprint,
            )
            expectRejected(
                label = "CURRENT_PROTOTYPE_IDENTITY",
                applicationId = "app.subshell.prototype",
                signingCertSha256 = fixtureFingerprint,
            )
            expectRejected(
                label = "EXAMPLE_PREFIX",
                applicationId = "org.example.subshell",
                signingCertSha256 = fixtureFingerprint,
            )
            expectRejected(
                label = "DEVELOPMENT_SUFFIX",
                applicationId = "org.subshell.client.dev",
                signingCertSha256 = fixtureFingerprint,
            )
            expectRejected(
                label = "FINGERPRINT_LOWERCASE",
                applicationId = "org.subshell.validationfixture",
                signingCertSha256 = fixtureFingerprint.lowercase(),
            )
            expectRejected(
                label = "FINGERPRINT_NO_COLONS",
                applicationId = "org.subshell.validationfixture",
                signingCertSha256 = fixtureFingerprint.replace(":", ""),
            )
            expectRejected(
                label = "FINGERPRINT_TOO_SHORT",
                applicationId = "org.subshell.validationfixture",
                signingCertSha256 = "AA:BB:CC:DD",
            )

            val selectedApplicationId = validateAuth05ProductionIdentity(
                rawApplicationId = AUTH05C_FINAL_APPLICATION_ID,
                rawSigningCertSha256 = fixtureFingerprint,
            )
            check(selectedApplicationId.configured)
            check(selectedApplicationId.applicationId == AUTH05C_FINAL_APPLICATION_ID)
            check(auth05ApplicationIdMigrationApplied)
            println("PASS=AUTH05_APPLICATION_ID_MIGRATION_APPLIED")
            println("AUTH05_SELECTED_APPLICATION_ID=$AUTH05C_FINAL_APPLICATION_ID")
            println("AUTH05_DEBUG_APPLICATION_ID=$AUTH05C_DEBUG_APPLICATION_ID")
            println("AUTH05_TEST_APPLICATION_ID=$AUTH05C_TEST_APPLICATION_ID")
            println(
                "AUTH05_EXTERNAL_PRODUCTION_IDENTITY_CONFIGURED=" +
                    auth05ProductionIdentity.configured,
            )
            println("RESULT=SUBSHELL_AUTH05_PRODUCTION_IDENTITY_POLICY_PASS")
        }
    }

val verifyAuth05cApplicationIdPolicy =
    tasks.register("verifyAuth05cApplicationIdPolicy") {
        group = "verification"
        description =
            "Verifies the selected production, debug, test, and component namespace identities."

        doLast {
            check(AUTH05C_FINAL_APPLICATION_ID == "io.github.brealorg.subshell")
            check(AUTH05C_DEBUG_APPLICATION_ID == "io.github.brealorg.subshell.debug")
            check(AUTH05C_TEST_APPLICATION_ID == "io.github.brealorg.subshell.debug.test")
            check(AUTH05C_COMPONENT_NAMESPACE == "app.subshell.prototype")
            check(AUTH05C_FINAL_APPLICATION_ID != AUTH05C_COMPONENT_NAMESPACE)
            check(auth05ApplicationIdMigrationApplied)
            println("PASS=AUTH05C_FINAL_APPLICATION_ID_SELECTED")
            println("PASS=AUTH05C_DEBUG_APPLICATION_ID_SUFFIXED")
            println("PASS=AUTH05C_TEST_APPLICATION_ID_LOCKED")
            println("PASS=AUTH05C_KOTLIN_NAMESPACE_DEFERRED")
            println("PASS=AUTH05C_RELEASE_SIGNING_CONFIGURED_BY_AUTH05D2")
            println("RESULT=SUBSHELL_AUTH05C_APPLICATION_ID_POLICY_PASS")
        }
    }


val verifyAuth05d2ReleaseSigningPolicy =
    tasks.register("verifyAuth05d2ReleaseSigningPolicy") {
        group = "verification"
        description =
            "Verifies the fail-closed external AUTH05D2 release-signing input policy."

        doLast {
            check(
                !auth05d2InputPresenceConfigured(
                    rawKeystorePath = null,
                    rawStorePasswordFile = null,
                    rawKeyPasswordFile = null,
                ),
            )
            println("PASS=AUTH05D2_UNCONFIGURED_DEBUG_BUILD_ALLOWED")

            fun expectPartialRejected(
                label: String,
                keystore: String?,
                storePasswordFile: String?,
                keyPasswordFile: String?,
            ) {
                val rejected = runCatching {
                    auth05d2InputPresenceConfigured(
                        keystore,
                        storePasswordFile,
                        keyPasswordFile,
                    )
                }.isFailure
                check(rejected) { "AUTH05D2 partial fixture accepted: $label" }
                println("PASS=AUTH05D2_REJECTED_$label")
            }

            expectPartialRejected(
                "KEYSTORE_ONLY",
                "/fixture/subshell.jks",
                null,
                null,
            )
            expectPartialRejected(
                "PASSWORD_FILES_ONLY",
                null,
                "/fixture/store.pass",
                "/fixture/key.pass",
            )
            expectPartialRejected(
                "MISSING_KEY_PASSWORD_FILE",
                "/fixture/subshell.jks",
                "/fixture/store.pass",
                null,
            )

            check(
                auth05d2InputPresenceConfigured(
                    "/fixture/subshell.jks",
                    "/fixture/store.pass",
                    "/fixture/key.pass",
                ),
            )
            check(
                AUTH05D2_EXPECTED_RELEASE_CERT_SHA256.matches(
                    Regex("(?:[0-9A-F]{2}:){31}[0-9A-F]{2}"),
                ),
            )
            check(
                AUTH05D2_EXPECTED_KEYSTORE_SHA256.matches(
                    Regex("[0-9a-f]{64}"),
                ),
            )
            check(AUTH05D2_RELEASE_KEY_ALIAS == "subshell-app-signing")
            println("PASS=AUTH05D2_COMPLETE_EXTERNAL_INPUT_SET")
            println("PASS=AUTH05D2_LOCKED_PUBLIC_SIGNING_IDENTITY")
            println(
                "AUTH05D2_EXTERNAL_RELEASE_SIGNING_CONFIGURED=" +
                    auth05d2ReleaseSigningInputs.configured,
            )
            println("RESULT=SUBSHELL_AUTH05D2_RELEASE_SIGNING_POLICY_PASS")
        }
    }

val auth05d2SigningVerificationOutput =
    layout.buildDirectory.file("auth05d2/release-signing-inputs.json")

val verifyAuth05d2ReleaseSigningIdentity =
    tasks.register<org.gradle.api.tasks.Exec>(
        "verifyAuth05d2ReleaseSigningIdentity",
    ) {
        group = "verification"
        description =
            "Verifies the external JKS, separate password files, private-key access, and locked public certificate."

        doFirst {
            if (!auth05d2ReleaseSigningInputs.configured) {
                auth05d2SigningFailure(
                    "release tasks require SUBSHELL_RELEASE_KEYSTORE_PATH, " +
                        "SUBSHELL_RELEASE_STORE_PASSWORD_FILE, and " +
                        "SUBSHELL_RELEASE_KEY_PASSWORD_FILE",
                )
            }
            val output = auth05d2SigningVerificationOutput.get().asFile
            output.parentFile.mkdirs()
            commandLine(
                "python3",
                rootProject.file(
                    "tools/verify-auth05d2-release-signing.py",
                ).absolutePath,
                "keystore",
                "--keystore",
                auth05d2ReleaseSigningInputs.keystore!!.absolutePath,
                "--store-password-file",
                auth05d2ReleaseSigningInputs.storePasswordFile!!.absolutePath,
                "--key-password-file",
                auth05d2ReleaseSigningInputs.keyPasswordFile!!.absolutePath,
                "--key-alias",
                AUTH05D2_RELEASE_KEY_ALIAS,
                "--expected-fingerprint",
                AUTH05D2_EXPECTED_RELEASE_CERT_SHA256,
                "--expected-keystore-sha256",
                AUTH05D2_EXPECTED_KEYSTORE_SHA256,
                "--application-id",
                AUTH05C_FINAL_APPLICATION_ID,
                "--source-root",
                rootProject.projectDir.absolutePath,
                "--output",
                output.absolutePath,
            )
        }

        outputs.upToDateWhen { false }
    }

// SUBSHELL QUALITY GATES V1
val subshellContracts =
    tasks.register<org.gradle.api.tasks.Exec>("subshellContracts") {
        group = "verification"
        description =
            "Runs all canonical subshell source and behaviour contracts."

        workingDir(rootProject.projectDir)
        commandLine("bash", "tools/check-all-contracts.sh")

        // Strict contract execution is explicit; lane landing performs a baseline-aware delta gate.
        outputs.upToDateWhen { false }
    }

val subshellQualityCheck =
    tasks.register("subshellQualityCheck") {
        group = "verification"
        description =
            "Runs contracts, lint, unit tests, and a debug APK build."

        dependsOn(subshellContracts)
        dependsOn(verifyAuth04ConfigurationPolicy)
        dependsOn(verifyAuth05ProductionIdentityPolicy)
        dependsOn(verifyAuth05cApplicationIdPolicy)
        dependsOn(verifyAuth05d2ReleaseSigningPolicy)
        dependsOn("lintDebug", "testDebugUnitTest", "assembleDebug")

        doLast {
            println("RESULT=SUBSHELL_QUALITY_GATE_PASS")
        }
    }

// Ordinary debug builds stay fast and independently buildable.
// Baseline-aware contract regression gating is owned by tools/subshell land.

// Standard Gradle verification executes the complete subshell quality gate.
tasks.matching { it.name == "check" }.configureEach {
    dependsOn(subshellQualityCheck)
}

// SUBSHELL_CONTRACT02F_RELEASE_QUALIFICATION_GATE_V1
// Standard `check` retains the complete legacy quality gate during migration.
// Release builds instead consume a previously completed Contract02 delta
// qualification record for this exact source snapshot.
private val contract02ReleaseQualificationRecord =
    providers
        .gradleProperty("subshell.release.qualificationRecord")
        .orElse(
            providers.environmentVariable(
                "SUBSHELL_RELEASE_QUALIFICATION_RECORD",
            ),
        )
        .orNull
        .orEmpty()

val verifyContract02ReleaseQualification =
    tasks.register<org.gradle.api.tasks.Exec>(
        "verifyContract02ReleaseQualification",
    ) {
        group = "verification"
        description =
            "Verifies a prior Contract02 delta qualification record for the current source snapshot."

        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            rootProject.file(
                "tools/contract02/verify-release-qualification.py",
            ).absolutePath,
            "--record",
            contract02ReleaseQualificationRecord,
            "--source-root",
            rootProject.projectDir.absolutePath,
        )
        outputs.upToDateWhen { false }
    }

// Release signing remains independently fail-closed. The release path must not
// re-enter subshellQualityCheck/subshellContracts after delta qualification.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyContract02ReleaseQualification)
    dependsOn(verifyAuth05d2ReleaseSigningIdentity)
}
