package com.focuslock.app.data.usage

/** What happened to an app's screen, reduced to the three cases the accounting cares about. */
enum class ForegroundEventKind {
    /** An activity of [ForegroundEvent.packageName] came to the front. */
    RESUMED,

    /** An activity of [ForegroundEvent.packageName] left the front (paused or stopped). */
    PAUSED,

    /** The screen turned off or the device shut down: nothing is in the foreground any more. */
    EVERYTHING_PAUSED,
}

data class ForegroundEvent(
    val kind: ForegroundEventKind,
    /** Empty for [ForegroundEventKind.EVERYTHING_PAUSED]. */
    val packageName: String,
    /** Activity class, so that two activities of one app are not double counted. May be blank. */
    val className: String,
    val timeMs: Long,
)

/**
 * Turns a stream of resume/pause events into "how long was each package on screen since [windowStartMs]".
 *
 * Pure Kotlin (no Android types) so it can be unit tested on the JVM. It is fed incrementally: call
 * [onEvent] for every new event in order, then ask for [totalMs]. A package counts as foreground while
 * at least one of its activities is resumed, so moving between two screens of one app counts once.
 *
 * Time is only ever counted between a resume and the matching pause. A pause with no resume before it is
 * ignored rather than guessed at (an early version assumed "it was already on screen when the day began",
 * which invented minutes for apps whose late-arriving pause simply lacked its resume). For an app that
 * really is on screen across midnight, the caller feeds events from a little before [windowStartMs]; the
 * resume is then seen and its session counts from the window start.
 *
 * Known limit: if the system never reports a pause (e.g. the app's process is killed mid-session) the
 * session stays open until the screen turns off, which closes every session.
 */
class ForegroundTimeAccumulator(windowStartMs: Long) {

    private class Session(var startMs: Long, val activities: MutableSet<String> = mutableSetOf())

    private var windowStartMs = windowStartMs
    private val closedMs = HashMap<String, Long>()
    private val open = HashMap<String, Session>()

    fun onEvent(event: ForegroundEvent) {
        // Events from before the window (a poll straddling midnight) act as if they happened at its start.
        val time = maxOf(event.timeMs, windowStartMs)
        when (event.kind) {
            ForegroundEventKind.RESUMED -> {
                val session = open.getOrPut(event.packageName) { Session(time) }
                session.activities += event.className
            }

            ForegroundEventKind.PAUSED -> {
                // No open session means a repeat (PAUSED is followed by STOPPED) or a resume we never saw.
                val session = open[event.packageName] ?: return
                session.activities -= event.className
                if (session.activities.isEmpty()) close(event.packageName, session, time)
            }

            ForegroundEventKind.EVERYTHING_PAUSED -> {
                for ((pkg, session) in open.entries.toList()) close(pkg, session, time)
            }
        }
    }

    /**
     * Foreground milliseconds of [packageName] up to [liveUntilMs]. A session that is still open is
     * counted up to that instant, which is how the number keeps rising while an app is being used.
     */
    fun totalMs(packageName: String, liveUntilMs: Long): Long {
        val live = open[packageName]?.let { maxOf(0L, liveUntilMs - it.startMs) } ?: 0L
        return (closedMs[packageName] ?: 0L) + live
    }

    fun isInForeground(packageName: String): Boolean = packageName in open

    val foregroundPackages: Set<String> get() = open.keys

    /** Starts a new day: totals are dropped, but apps still on screen keep running from the new start. */
    fun startNewWindow(newWindowStartMs: Long) {
        windowStartMs = newWindowStartMs
        closedMs.clear()
        open.values.forEach { it.startMs = newWindowStartMs }
    }

    private fun close(packageName: String, session: Session, endMs: Long) {
        open.remove(packageName)
        addClosed(packageName, endMs - session.startMs)
    }

    private fun addClosed(packageName: String, durationMs: Long) {
        if (durationMs > 0) closedMs[packageName] = (closedMs[packageName] ?: 0L) + durationMs
    }
}
