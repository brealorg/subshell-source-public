package app.subshell.prototype.media

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Activity-retained owner for the single process-local Media3 player.
 *
 * Compose owns surfaces and controls only. The coordinator is released when the Activity task's
 * ViewModelStore is cleared, not when an Activity instance is recreated for configuration change.
 */
class VideoPlaybackViewModel(application: Application) : ViewModel() {
    val coordinator = VideoPlaybackCoordinator(application)

    companion object {
        // PLAINVIEWMODEL01_NO_ANDROIDVIEWMODEL_V1
        val Factory = viewModelFactory {
            initializer { VideoPlaybackViewModel(requireNotNull(this[APPLICATION_KEY])) }
        }
    }

    override fun onCleared() {
        coordinator.release()
        super.onCleared()
    }
}
