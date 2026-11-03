package com.sarvesh173.talos.agent

import com.sarvesh173.talos.ui.agent.AgentClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentClockTest {

    @Test
    fun ladderDoublesFromThreeMinutes() {
        val expected = listOf(180L, 360L, 720L, 1440L, 2880L, 5760L)
        expected.forEachIndexed { i, secs ->
            assertEquals("rung $i", secs, AgentClock.ladderSeconds(i))
        }
    }

    @Test
    fun noNotificationBeforeTheFirstRung() {
        assertEquals(0, AgentClock.crossedSteps(0))
        assertEquals(0, AgentClock.crossedSteps(179))
    }

    @Test
    fun crossedStepsCountsRungsNotTime() {
        assertEquals(1, AgentClock.crossedSteps(180))
        assertEquals(1, AgentClock.crossedSteps(359))
        assertEquals(2, AgentClock.crossedSteps(360))
        assertEquals(3, AgentClock.crossedSteps(720))
        assertEquals(5, AgentClock.crossedSteps(2880))
    }

    @Test
    fun displayKeepsSecondsEarlyThenDropsThem() {
        assertEquals("45s", AgentClock.format(45))
        assertEquals("1:03", AgentClock.format(63))
        assertEquals("1:00:05", AgentClock.format(3605))
    }

    @Test
    fun displaySurvivesDaysMonthsYearsAndCenturies() {
        assertTrue(AgentClock.format(86_400).startsWith("1d"))
        assertTrue(AgentClock.format(2_592_000).startsWith("1mo"))
        assertTrue(AgentClock.format(31_536_000).startsWith("1y"))
        // A century must not degrade into a nonsense unit.
        assertTrue(AgentClock.format(3_153_600_000).contains("c"))
    }

    @Test
    fun compactIsShorterThanFull() {
        assertEquals("1m 12s", AgentClock.compact(72))
        assertEquals("2h 5m", AgentClock.compact(7500))
        assertEquals("3d 4h", AgentClock.compact(273_600))
    }

    @Test
    fun nextNotificationCountsDownToTheNextRung() {
        assertEquals(150L, AgentClock.nextNotificationIn(30))   // 180 - 30
        assertEquals(0L, AgentClock.nextNotificationIn(180))    // just crossed
        assertEquals(340L, AgentClock.nextNotificationIn(380))  // 720 - 380
    }

    @Test
    fun delegateExpiresAtItsTtlAndMainDoesNot() {
        val now = 1_000_000_000_000L
        val delegate = AgentRuntime(
            id = "d1", name = "Worker", kind = AgentKind.Delegate,
            ttlSeconds = 600, startedAtMillis = now - 700_000
        )
        assertTrue(delegate.isExpired(now))
        assertEquals(0L, delegate.remainingSeconds(now))

        val main = AgentRuntime(id = "m1", name = "Main", startedAtMillis = now - 9_000_000)
        assertEquals(null, main.remainingSeconds(now))
        assertTrue(!main.isExpired(now))
    }

    @Test
    fun elapsedNeverGoesNegative() {
        val future = AgentRuntime(id = "x", name = "x", startedAtMillis = System.currentTimeMillis() + 60_000)
        assertEquals(0L, future.elapsedSeconds())
    }
}
