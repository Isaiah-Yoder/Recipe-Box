package io.github.isaiahyoder.recipebox.diagnostics

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** One time the screen stopped answering: when, for how long, and what the app was doing. */
@Serializable
data class StallReport(
    val at: Long,
    val millis: Long,
    val versionName: String,
    /** The main thread's stack while it was stuck, app frames first. */
    val stack: List<String>,
)

/**
 * Notices when the main thread stops answering, which freezes the screen, and
 * records where it was stuck so the details can be copied from Settings.
 *
 * A background thread posts an empty task to the main thread and checks that
 * it ran within [thresholdMs]. A stall is recorded once, with its full length,
 * when the main thread answers again or after [maxWaitMs].
 */
class StallWatchdog(
    private val versionName: String,
    private val onStall: (StallReport) -> Unit,
    private val thresholdMs: Long = 1_500,
    private val intervalMs: Long = 2_000,
    private val maxWaitMs: Long = 30_000,
) {
    private val main = Handler(Looper.getMainLooper())

    fun start() {
        thread(name = "stall-watchdog", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            while (true) {
                runCatching { checkOnce() }
                Thread.sleep(intervalMs)
            }
        }
    }

    private fun checkOnce() {
        val answered = AtomicBoolean(false)
        val posted = SystemClock.uptimeMillis()
        main.post { answered.set(true) }
        Thread.sleep(thresholdMs)
        // A debugger pausing the app isn't a freeze she sees.
        if (answered.get() || Debug.isDebuggerConnected()) return
        val stack = Looper.getMainLooper().thread.stackTrace.map { it.toString() }
        while (!answered.get() && SystemClock.uptimeMillis() - posted < maxWaitMs) Thread.sleep(100)
        onStall(
            StallReport(
                at = System.currentTimeMillis(),
                millis = SystemClock.uptimeMillis() - posted,
                versionName = versionName,
                stack = appFramesFirst(stack),
            )
        )
    }

    companion object {
        private const val APP_PACKAGE = "io.github.isaiahyoder.recipebox"
        private const val MAX_FRAMES = 40

        /** Keeps the frames that say most: the app's own, then the top of the stack. */
        internal fun appFramesFirst(stack: List<String>): List<String> {
            val app = stack.filter { APP_PACKAGE in it }
            return (stack.take(12) + app).distinct().take(MAX_FRAMES)
        }

        /** The reports as text for pasting into a message. */
        fun describe(reports: List<StallReport>): String = reports.joinToString("\n\n") { report ->
            val seconds = "%.1f".format(report.millis / 1000.0)
            val time = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(report.at))
            "Recipe Box ${report.versionName} paused for $seconds s at $time\n" +
                report.stack.joinToString("\n") { "  at $it" }
        }
    }
}
