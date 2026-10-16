package app.subshell.prototype.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/**
 * Activity-retained owner for the single process-local Media3 player.
 *
 * Compose owns surfaces and controls only. The coordinator is released when the Activity task's
 * ViewModelStore is cleared, not when an Activity instance is recreated for configuration change.
 */
class VideoPlaybackViewModel(application: Application) : AndroidViewModel(application) {
    val coordinator = VideoPlaybackCoordinator(application)

    override fun onCleared() {
        coordinator.release()
        super.onCleared()
    }
}
