package app.subshell.prototype.share

import app.subshell.prototype.ui.components.convertImageToJpeg
import app.subshell.prototype.ui.components.imageNeedsJpegConversion
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import app.subshell.prototype.ui.COMPOSER_GALLERY_MAX_ITEMS
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.ComposerMediaKind
import app.subshell.prototype.ui.SharedPostContent
import app.subshell.prototype.ui.sharedImageKindOrNull
import app.subshell.prototype.ui.sharedPostFromImages
import app.subshell.prototype.ui.sharedPostFromText
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.subshell.prototype.EXTRA_EXTERNAL_LAUNCH_TOKEN
import app.subshell.prototype.ExternalLaunchToken
import java.util.UUID

// ISSUE33_SHARE_INTO_SUBSHELL_V1
// Subshell appears in Android's share menu only when the owner turns it on
// (Settings, off by default, device-local like launcher shortcuts). The
// share filters live on an activity-alias of MainActivity that ships
// disabled; the setting is the alias's component state, so there is no
// second copy of it to keep in sync.
object ShareIntoSubshellComponent {
    internal const val ALIAS_CLASS = "app.subshell.prototype.ShareIntoSubshell"

    private fun component(context: Context) = ComponentName(context.packageName, ALIAS_CLASS)

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}

/** A share handed to SubshellApp; [content] is null when nothing was usable. */
data class ShareIntoSubshellLaunch(
    val requestId: Long,
    val content: SharedPostContent?,
)

// LOW_SHARE_ONLY_THROUGH_THE_SHARE_TARGET_V1 (OCB-570): MainActivity is
// exported, so another app could send ACTION_SEND to it directly while
// "Share into subshell" is off; only a share delivered through the alias (the
// setting) counts.
// LOW_EXTERNAL_LAUNCHES_USE_SUBSHELLS_OWN_TASK_V1 (OCB-577): the share target
// now forwards to MainActivity; a forwarded share carries this process's token.
internal fun isShareIntent(intent: Intent?): Boolean =
    (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) &&
        (
            intent.component?.className == ShareIntoSubshellComponent.ALIAS_CLASS ||
                intent.getStringExtra(EXTRA_EXTERNAL_LAUNCH_TOKEN) == ExternalLaunchToken.value
            )

/**
 * Reads a share intent. Shared images are copied into Subshell's own files
 * first: the read grant another app gives lasts only as long as this task,
 * and a draft must still reach its images after the process restarts.
 * Blocking I/O; call off the main thread.
 */
internal fun readSharedPost(context: Context, intent: Intent): SharedPostContent? {
    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
    val streams = sharedStreams(intent)
    if (streams.isEmpty()) return sharedPostFromText(text, subject)
    var skipped = 0
    // LOW_SHARE_COPIES_ONLY_WHAT_A_POST_TAKES_V1 (OCB-571): copying stops at
    // the gallery limit (the rest is counted as skipped), and each copy is
    // bounded by what Reddit accepts.
    val images = mutableListOf<ComposerMediaAttachment>()
    streams.forEach { uri ->
        if (images.size >= COMPOSER_GALLERY_MAX_ITEMS) {
            skipped += 1
            return@forEach
        }
        val copied = if (isForeignSharedStream(context, uri)) copySharedImage(context, uri) else null
        if (copied == null) skipped += 1 else images += copied
    }
    return sharedPostFromImages(images, text, subject, skipped)
        ?: sharedPostFromText(text, subject)?.copy(skippedCount = skipped)
}

@Suppress("DEPRECATION")
internal fun sharedStreams(intent: Intent): List<Uri> = when (intent.action) {
    Intent.ACTION_SEND_MULTIPLE -> if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }
    else -> listOfNotNull(
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        },
    )
}

/**
 * SECAUDIT23_SHARE_TAKES_ONLY_OTHER_APPS_FILES_V1 (security audit #23): a share
 * from another app is read inside Subshell's process, so it could name
 * Subshell's own files (`content://<package>.files/…`: copied shares,
 * diagnostics reports) or a `file://` path. A shared stream must be another
 * app's content URI. (A pasted image keeps using copySharedImage directly: an
 * image copied inside Subshell is pasted from its own provider.)
 */
internal fun isForeignSharedStream(context: Context, uri: Uri): Boolean =
    uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) &&
        !uri.authority.equals("${context.packageName}.files", ignoreCase = true)

// COMPOSER16_PASTED_IMAGE_COPIED_V1: also used for pasted images (owner-approved cross-lane change).
internal fun copySharedImage(context: Context, uri: Uri): ComposerMediaAttachment? {
    val mimeType = context.contentResolver.getType(uri)
    // ISSUE90_SENDABLE_IMAGE_V1: a HEIC/HEIF/AVIF image is stored as a JPEG.
    if (imageNeedsJpegConversion(mimeType)) return convertImageToJpeg(context, uri)
    val kind = sharedImageKindOrNull(mimeType) ?: return null
    val directory = sharedMediaDirectory(context).apply { mkdirs() }
    val extension = mimeType?.substringAfter('/')?.replace("jpeg", "jpg") ?: "img"
    val target = File(directory, "${UUID.randomUUID()}.$extension")
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val limit = if (kind == ComposerMediaKind.GIF) SHARED_GIF_MAX_BYTES else SHARED_IMAGE_MAX_BYTES
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > limit) throw java.io.IOException("Shared image is larger than Reddit accepts")
                    output.write(buffer, 0, count)
                }
            }
        } ?: return null
        ComposerMediaAttachment(
            uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target).toString(),
            mimeType = checkNotNull(mimeType),
            kind = kind,
        )
    }.getOrElse {
        target.delete()
        null
    }
}

internal fun sharedMediaDirectory(context: Context): File = File(context.filesDir, SHARED_MEDIA_DIRECTORY)

/**
 * Deletes copied shares that no draft refers to any more. Files younger
 * than an hour stay: a share may still be on its way into a draft.
 */
internal fun pruneSharedMedia(
    context: Context,
    referencedUris: Set<String>,
    nowEpochMs: Long = System.currentTimeMillis(),
) {
    // Copies have unique names, so a draft's URI names its file by its last
    // segment; that does not depend on how the provider maps paths.
    val referencedNames = referencedUris
        .filter { uri -> "/$SHARED_MEDIA_DIRECTORY/" in uri }
        .map { uri -> uri.substringAfterLast('/') }
        .toSet()
    sharedMediaDirectory(context).listFiles().orEmpty().forEach { file ->
        val old = nowEpochMs - file.lastModified() > SHARED_MEDIA_GRACE_MS
        if (old && file.name !in referencedNames) file.delete()
    }
}

private const val SHARED_MEDIA_DIRECTORY = "shared_into_subshell"
private const val SHARED_IMAGE_MAX_BYTES = 20L * 1024L * 1024L
private const val SHARED_GIF_MAX_BYTES = 100L * 1024L * 1024L
private const val SHARED_MEDIA_GRACE_MS = 60 * 60 * 1000L

/**
 * LOW_MAIN_SHARE_SURVIVES_RECREATION_AND_BAD_INPUT_V1 (OCB-393): the share is
 * read in a scope that outlives an activity recreation (the action was
 * already cleared, so a cancelled copy lost the share), and a share that
 * cannot be read (a provider that throws, a non-Uri stream before API 33) is
 * delivered as nothing usable instead of crashing the app.
 */
class ShareImportViewModel : ViewModel() {
    var launch by mutableStateOf<ShareIntoSubshellLaunch?>(null)

    fun read(context: Context, intent: Intent, requestId: Long) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val content = try {
                withContext(Dispatchers.IO) { readSharedPost(appContext, intent) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            launch = ShareIntoSubshellLaunch(requestId, content)
        }
    }
}
