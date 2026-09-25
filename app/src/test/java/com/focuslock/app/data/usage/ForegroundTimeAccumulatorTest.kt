package com.focuslock.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundTimeAccumulatorTest {

    private val chrome = "com.android.chrome"
    private val other = "com.example.other"

    private fun resumed(pkg: String, at: Long, cls: String = "Main") =
        ForegroundEvent(ForegroundEventKind.RESUMED, pkg, cls, at)

    private fun paused(pkg: String, at: Long, cls: String = "Main") =
        ForegroundEvent(ForegroundEventKind.PAUSED, pkg, cls, at)

    private fun screenOff(at: Long) =
        ForegroundEvent(ForegroundEventKind.EVERYTHING_PAUSED, "", "", at)

    @Test
    fun `resume then pause counts the time between them`() {
        val acc = ForegroundTimeAccumulator(windowStartMs = 0)
        acc.onEvent(resumed(chrome, 10_000))
        acc.onEvent(paused(chrome, 70_000))
        assertEquals(60_000, acc.totalMs(chrome, liveUntilMs = 500_000))
        assertFalse(acc.isInForeground(chrome))
    }

    @Test
    fun `open session keeps counting up to the live instant`() {
        val acc = ForegroundTimeAccumulator(0)
        acc.onEvent(resumed(chrome, 10_000))
        assertEquals(5_000, acc.totalMs(chrome, liveUntilMs = 15_000))
        assertEquals(20_000, acc.totalMs(chrome, liveUntilMs = 30_000))
        assertTrue(acc.isInForeground(chrome))
    }

    @Test
    fun `sessions add up and apps are counted separately`() {
        val acc = ForegroundTimeAccumulator(0)
        acc.onEvent(resumed(chrome, 0))
        acc.onEvent(paused(chrome, 10_000))
        acc.onEvent(resumed(other, 10_000))
        acc.onEvent(paused(other, 40_000))
        acc.onEvent(resumed(chrome, 40_000))
        acc.onEvent(paused(chrome, 55_000))
        assertEquals(25_000, acc.totalMs(chrome, 100_000))
        assertEquals(30_000, acc.totalMs(other, 100_000))
    }

    @Test
    fun `two activities of one app count once`() {
        val acc = ForegroundTimeAccumulator(0)
        acc.onEvent(resumed(chrome, 0, "A"))
        acc.onEvent(resumed(chrome, 4_000, "B")) // B opens on top before A is paused
        acc.onEvent(paused(chrome, 4_000, "A"))
        acc.onEvent(paused(chrome, 10_000, "B"))
        assertEquals(10_000, acc.totalMs(chrome, 50_000))
    }

    @Test
    fun `repeated pause does not add anything`() {
        val acc = ForegroundTimeAccumulator(windowStartMs = 0)
        acc.onEvent(resumed(chrome, 1_000_000))
        acc.onEvent(paused(chrome, 1_010_000)) // PAUSED...
        acc.onEvent(paused(chrome, 1_010_000)) // ...then STOPPED, which is mapped to the same kind
        assertEquals(10_000, acc.totalMs(chrome, 2_000_000))
    }

    @Test
    fun `a pause with no resume before it is ignored, not guessed at`() {
        // Regression: this used to credit "window start until the pause" and invented 4 min 44 s of use.
        val acc = ForegroundTimeAccumulator(windowStartMs = 100_000)
        acc.onEvent(paused(chrome, 130_000))
        assertEquals(0, acc.totalMs(chrome, 500_000))
        assertFalse(acc.isInForeground(chrome))
    }

    @Test
    fun `a session that began before the window counts only from the window start`() {
        // What the tracker's look-back makes possible: the resume from before midnight is visible.
        val acc = ForegroundTimeAccumulator(windowStartMs = 100_000)
        acc.onEvent(resumed(chrome, 40_000))
        acc.onEvent(paused(chrome, 130_000))
        assertEquals(30_000, acc.totalMs(chrome, 500_000))
    }

    @Test
    fun `screen off closes every open session at that moment`() {
        val acc = ForegroundTimeAccumulator(0)
        acc.onEvent(resumed(chrome, 10_000))
        acc.onEvent(screenOff(25_000))
        assertEquals(15_000, acc.totalMs(chrome, liveUntilMs = 9_000_000))
        assertTrue(acc.foregroundPackages.isEmpty())
    }

    @Test
    fun `live time can be frozen by passing an earlier instant`() {
        // Used while the screen is off: the tracker passes the last instant it saw the screen on.
        val acc = ForegroundTimeAccumulator(0)
        acc.onEvent(resumed(chrome, 10_000))
        assertEquals(20_000, acc.totalMs(chrome, liveUntilMs = 30_000))
        assertEquals(0, acc.totalMs(chrome, liveUntilMs = 5_000)) // never negative
    }

    @Test
    fun `new window drops totals but keeps an app that is still on screen`() {
        val acc = ForegroundTimeAccumulator(0)
        acc.onEvent(resumed(other, 1_000))
        acc.onEvent(paused(other, 2_000))
        acc.onEvent(resumed(chrome, 90_000))

        acc.startNewWindow(100_000)

        assertEquals(0, acc.totalMs(other, 150_000))
        assertEquals(50_000, acc.totalMs(chrome, 150_000)) // runs from the new window start
        acc.onEvent(paused(chrome, 160_000))
        assertEquals(60_000, acc.totalMs(chrome, 999_000))
    }

    @Test
    fun `events from before the window are clamped to its start`() {
        val acc = ForegroundTimeAccumulator(0)
        acc.startNewWindow(100_000)
        // A poll that straddles midnight delivers the tail of yesterday's session first.
        acc.onEvent(resumed(chrome, 90_000))
        acc.onEvent(paused(chrome, 130_000))
        assertEquals(30_000, acc.totalMs(chrome, 200_000))
    }

    @Test
    fun `late repeat pause after a new window adds nothing`() {
        val acc = ForegroundTimeAccumulator(0)
        acc.onEvent(resumed(chrome, 10_000))
        acc.onEvent(paused(chrome, 20_000))
        acc.startNewWindow(100_000)
        acc.onEvent(paused(chrome, 101_000)) // STOPPED arriving late
        assertEquals(0, acc.totalMs(chrome, 200_000))
    }
}
