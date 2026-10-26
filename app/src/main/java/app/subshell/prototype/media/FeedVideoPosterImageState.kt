package app.subshell.prototype.media

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * A feed card's existing decoded poster. Not a video-frame cache or playback authority.
 * The card remembers this by media ID and image URI, outside its selected-player branch.
 * T is Bitmap in production; the generic value lets JVM tests exercise the real state
 * transitions without constructing Android graphics objects.
 */
@Stable
internal class FeedVideoPosterImageState<T>(
    val mediaId: String,
    val uri: String,
    initial: T? = null,
) {
    var result: RemoteMediaLoadResult<T>? by mutableStateOf(initial?.let { RemoteMediaLoadResult.Ready(it) })
        private set
    var retryNonce by mutableIntStateOf(0)
        private set

    val phase: String
        get() = when (result) {
            null -> "loading"
            is RemoteMediaLoadResult.Ready -> "ready"
            is RemoteMediaLoadResult.Failed -> "failed"
        }

    fun matches(mediaId: String, uri: String): Boolean =
        this.mediaId == mediaId && this.uri == uri

    suspend fun load(loader: suspend (String) -> RemoteMediaLoadResult<T>) {
        val requestNonce = retryNonce
        val loaded = loader(uri)
        currentCoroutineContext().ensureActive()
        if (requestNonce == retryNonce) result = loaded
    }

    fun retry() {
        result = null
        retryNonce += 1
    }
}
