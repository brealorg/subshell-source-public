package app.subshell.prototype

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.AuthConnectViewModel
import app.subshell.prototype.harness.releaseWithoutHanging
import app.subshell.prototype.media.VideoPlaybackViewModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PLAINVIEWMODEL01: Google advises against AndroidViewModel; MainActivity's
 * ViewModels are plain ViewModels that get the Application from their
 * factory's CreationExtras.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlainViewModel01Test {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val root: File = requireNotNull(File(requireNotNull(System.getProperty("user.dir"))).parentFile)

    private fun provider(store: ViewModelStore, factory: ViewModelProvider.Factory) = ViewModelProvider.create(
        store = store,
        factory = factory,
        extras = MutableCreationExtras().apply { set(APPLICATION_KEY, application) },
    )

    @Test
    fun theFactoriesBuildTheViewModelsFromTheApplication() {
        val store = ViewModelStore()
        assertNotNull(provider(store, AuthConnectViewModel.Factory)[AuthConnectViewModel::class])

        val video = provider(ViewModelStore(), VideoPlaybackViewModel.Factory)[VideoPlaybackViewModel::class]
        video.coordinator.releaseWithoutHanging()
        store.clear()
    }

    @Test
    fun noAppSourceUsesAndroidViewModel() {
        val offenders = File(root, "app/src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> Regex("""\bAndroidViewModel\(""").containsMatchIn(line) }
                    .map { (index, _) -> "${file.relativeTo(root)}:${index + 1}" }
            }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
