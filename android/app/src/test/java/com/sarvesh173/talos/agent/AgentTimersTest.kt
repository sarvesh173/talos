package com.sarvesh173.talos.agent

import com.sarvesh173.talos.ui.agent.AgentKind
import com.sarvesh173.talos.ui.agent.AgentRuntime
import com.sarvesh173.talos.ui.agent.AgentTimers
import com.sarvesh173.talos.ui.agent.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTimersTest {

    private val t0 = 1_700_000_000_000L

    private fun agent(startedAgoMs: Long, endedAgoMs: Long? = null) = AgentRuntime(
        id = "a", name = "A", kind = AgentKind.Main,
        startedAtMillis = t0 - startedAgoMs,
        endedAtMillis = endedAgoMs?.let { t0 - it }
    )

    @Test
    fun runningAgentCountsInSeconds() {
        val timers = AgentTimers(t0 - 600_000)
        timers.tick(t0)
        assertEquals(30L, timers.workingSeconds(agent(30_000)))
    }

    @Test
    fun finishedAgentFreezesAtItsEndTime() {
        val timers = AgentTimers(t0)
        timers.tick(t0 + 3_600_000)
        // Started an hour ago, ended 40 minutes ago -> worked ~20 minutes.
        val a = agent(startedAgoMs = 3_600_000, endedAgoMs = 2_400_000)
        assertEquals(1_200L, timers.workingSeconds(a))
    }

    @Test
    fun sessionClockKeepsCounting() {
        val timers = AgentTimers(t0)
        timers.tick(t0 + 90_000)
        assertEquals(90L, timers.sessionSeconds())
    }

    @Test
    fun coarseValueOnlyAdvancesOnTheMinute() {
        val timers = AgentTimers(t0)
        timers.tick(t0 + 1_000)
        assertEquals(t0, timers.coarseNow)
        timers.tick(t0 + 59_000)
        assertEquals(t0, timers.coarseNow)
        timers.tick(t0 + 60_000)
        assertEquals(t0 + 60_000, timers.coarseNow)
        // Live value moved immediately, coarse did not.
        assertEquals(t0 + 60_000, timers.now)
    }

    @Test
    fun aLongSleepDoesNotBurstCoarseUpdates() {
        val timers = AgentTimers(t0)
        // App was backgrounded for an hour.
        timers.tick(t0 + 3_600_000)
        assertEquals(t0 + 3_600_000, timers.coarseNow)
        // The next tick must not fire the coarse update again.
        timers.tick(t0 + 3_601_000)
        assertEquals(t0 + 3_600_000, timers.coarseNow)
    }

    @Test
    fun totalWorkingTimeSumstheFleet() {
        val timers = AgentTimers(t0 - 600_000)
        timers.tick(t0)
        val fleet = listOf(agent(60_000), agent(120_000), agent(30_000))
        assertEquals(210L, timers.totalWorkingSeconds(fleet, useCoarse = false))
    }

    @Test
    fun utilisationIsAShareOfTheSession() {
        val s = SessionSummary(
            workingSeconds = 300, sessionSeconds = 600,
            agentsRun = 2, delegatesRun = 1, toolCalls = 12
        )
        assertEquals(0.5f, s.utilisation(), 0.001f)
    }

    @Test
    fun utilisationOfAZeroLengthSessionDoesNotDivideByZero() {
        val s = SessionSummary(0, 0, 0, 0, 0)
        assertEquals(0f, s.utilisation(), 0.001f)
        assertTrue(!s.utilisation().isNaN())
    }
}
