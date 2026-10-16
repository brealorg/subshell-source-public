package app.subshell.prototype.ui.state

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * PERF02_PAGER_LOADS_OFF_MAIN_V1 (#69): where post paging work runs. A pager
 * operation reads (and decodes) the Room cache, which blocked the main thread
 * on every page and feed entry. [execute] runs one pager operation; operations
 * run one at a time, in order. [publish] applies a result on the main thread.
 */
interface PostPagingExecutor {
    fun execute(work: () -> Unit)

    fun publish(update: () -> Unit)

    /** Runs everything at once on the caller (tests, previews; the former behaviour). */
    object Inline : PostPagingExecutor {
        override fun execute(work: () -> Unit) = work()

        override fun publish(update: () -> Unit) = update()
    }
}

/** One background thread for every pager, results posted to the main thread. */
internal class BackgroundPostPagingExecutor private constructor() : PostPagingExecutor {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "subshell-post-paging").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    override fun execute(work: () -> Unit) {
        worker.execute {
            try {
                work()
            } catch (error: Throwable) {
                // Fail where it used to: on the main thread, not silently here.
                main.post { throw error }
            }
        }
    }

    override fun publish(update: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) update() else main.post(update)
    }

    companion object {
        val shared: BackgroundPostPagingExecutor by lazy { BackgroundPostPagingExecutor() }
    }
}
