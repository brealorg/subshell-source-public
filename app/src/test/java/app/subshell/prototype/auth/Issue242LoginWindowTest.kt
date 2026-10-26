package app.subshell.prototype.auth

import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE242_LOGIN_WINDOW_KEEPS_CLEAR_OF_THE_SYSTEM_BARS_V1 (#242): the RedReader
 * login window is drawn edge to edge; Reddit's page keeps clear of the status
 * bar, the gesture bar and the keyboard. (The dark page setting is not visible
 * to Robolectric's WebView; it was checked on a device.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue242LoginWindowTest {
    private fun openLoginWindow(): ViewGroup {
        val intent = RedReaderCompatibilityLoginActivity.createIntent(
            context = Robolectric.buildActivity(ComponentActivity::class.java).setup().get(),
            authorizationUrl = "https://www.reddit.com/api/v1/authorize.compact?client_id=test",
            freshAccountSession = false,
        )
        val window = Robolectric.buildActivity(RedReaderCompatibilityLoginActivity::class.java, intent).setup().get()
        return window.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
    }

    private fun insets(top: Int, bottom: Int, keyboard: Int): WindowInsetsCompat =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, top, 0, bottom))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
            .build()

    @Test
    fun thePageKeepsClearOfTheSystemBars() {
        val frame = openLoginWindow()

        ViewCompat.dispatchApplyWindowInsets(frame, insets(top = 96, bottom = 63, keyboard = 0))

        assertEquals(96, frame.paddingTop)
        assertEquals(63, frame.paddingBottom)
        assertTrue(frame.getChildAt(0) is WebView)
    }

    @Test
    fun theKeyboardDoesNotCoverTheSignInFields() {
        val frame = openLoginWindow()

        ViewCompat.dispatchApplyWindowInsets(frame, insets(top = 96, bottom = 63, keyboard = 900))

        assertEquals(900, frame.paddingBottom)
    }
}
