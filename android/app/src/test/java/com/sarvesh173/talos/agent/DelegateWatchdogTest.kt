package com.sarvesh173.talos.agent

import com.sarvesh173.talos.ui.agent.AgentRuntime
import com.sarvesh173.talos.ui.agent.AgentKind
import com.sarvesh173.talos.ui.agent.DelegateWatchdog
import com.sarvesh173.talos.ui.agent.distributeTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DelegateWatchdogTest {

    private fun delegate(id: String, ttl: Long? = null, startedAgoMs: Long = 0) = AgentRuntime(
        id = id,
        name = id,
        kind = AgentKind.Delegate,
        ttlSeconds = ttl,
        startedAtMillis = System.currentTimeMillis() - startedAgoMs
    )

    @Test
    fun expiredDelegateIsSweptOnce() {
        val w = DelegateWatchdog()
        w.track(delegate("a", ttl = 10, startedAgoMs = 20_000))
        val first = w.sweep()
        assertEquals(listOf("a"), first)
        // A second sweep must not report it again.
        assertTrue(w.sweep().isEmpty())
        assertEquals(1, w.finishedCount)
    }

    @Test
    fun stallIsDetectedButNotKilled() {
        val w = DelegateWatchdog(stallSeconds = 5)
        val d = delegate("b")
        w.track(d)
        val later = System.currentTimeMillis() + 10_000
        w.sweep(later)
        val entry = w.snapshot().first { it.agent.id == "b" }
        assertEquals(DelegateWatchdog.Status.Stalled, entry.status)
        // Still present: a stall may recover.
        assertEquals(1, w.running().size)
    }

    @Test
    fun progressClearsAStall() {
        val w = DelegateWatchdog(stallSeconds = 5)
        w.track(delegate("c"))
        w.sweep(System.currentTimeMillis() + 10_000)
        w.touch("c")
        w.sweep(System.currentTimeMillis())
        assertEquals(DelegateWatchdog.Status.Running, w.snapshot().first().status)
    }

    @Test
    fun completionIsTerminalAndCountedOnce() {
        val w = DelegateWatchdog()
        w.track(delegate("d"))
        w.complete("d", "report")
        assertTrue(w.sweep().isEmpty()) // already terminal before the sweep
        assertEquals(0, w.finishedCount)
        assertEquals("report", w.snapshot().first().result)
    }

    @Test
    fun failureIsTerminal() {
        val w = DelegateWatchdog()
        w.track(delegate("e"))
        w.fail("e", "boom")
        assertEquals(DelegateWatchdog.Status.Failed, w.snapshot().first().status)
    }

    @Test
    fun tasksDistributeRoundRobinWithoutDuplicates() {
        val tasks = (1..7).map { "t$it" }
        val buckets = distributeTasks(tasks, 3)
        assertEquals(3, buckets.size)
        // Every task lands exactly once.
        assertEquals(tasks.toSet(), buckets.flatten().toSet())
        assertEquals(tasks.size, buckets.flatten().size)
    }

    @Test
    fun distributingIntoMoreBucketsThanTasksDropsEmpty() {
        val buckets = distributeTasks(listOf("only"), 5)
        assertEquals(1, buckets.size)
        assertEquals(listOf("only"), buckets.first())
    }

    @Test
    fun zeroDelegatesYieldsNothing() {
        assertTrue(distributeTasks(listOf("a", "b"), 0).isEmpty())
    }
}
