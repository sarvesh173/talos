package com.sarvesh173.talos.orb

import com.sarvesh173.talos.ui.orb.OrbPresets
import com.sarvesh173.talos.ui.orb.OrbState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the ported orb geometry.
 *
 * These assert behaviour a renderer depends on, not pixel values: a frame must
 * be non-empty, z-sorted far→near, free of invisible dots, and stable for a
 * given instant (the same (state, size, t) always yields the same frame, which
 * is what makes the animation reproducible frame to frame).
 */
class ThinkingOrbEngineTest {

    @Test
    fun everyStateProducesDotsAtBothTunedSizes() {
        for (state in OrbState.entries) {
            for (size in listOf(20f, 64f)) {
                val frame = OrbPresets.frame(state, size, 1.0f)
                assertTrue("$state @${size}dp produced no dots", frame.dots.isNotEmpty())
            }
        }
    }

    @Test
    fun framesAreSortedFarToNear() {
        for (state in OrbState.entries) {
            val dots = OrbPresets.frame(state, 64f, 2.4f).dots
            val zs = dots.map { it.z }
            assertEquals("$state frame is not z-sorted", zs.sorted(), zs)
        }
    }

    @Test
    fun invisibleDotsAreDroppedAndRadiiClamped() {
        for (state in OrbState.entries) {
            val dots = OrbPresets.frame(state, 64f, 0.7f).dots
            assertTrue("$state kept an invisible dot", dots.all { it.alpha >= 0.02f })
            assertTrue("$state has a zero radius dot", dots.all { it.r > 0f })
        }
    }

    @Test
    fun geometryIsDeterministicForTheSameInstant() {
        val a = OrbPresets.frame(OrbState.WORKING, 64f, 3.1f).dots
        val b = OrbPresets.frame(OrbState.WORKING, 64f, 3.1f).dots
        assertEquals(a.size, b.size)
        a.zip(b).forEach { (x, y) ->
            assertEquals(x.x, y.x, 0.0001f)
            assertEquals(x.y, y.y, 0.0001f)
        }
    }

    @Test
    fun animationActuallyMovesBetweenInstants() {
        val t0 = OrbPresets.frame(OrbState.WORKING, 64f, 0.0f).dots
        val t1 = OrbPresets.frame(OrbState.WORKING, 64f, 0.8f).dots
        val moved = t0.zip(t1).count { (a, b) -> kotlin.math.abs(a.x - b.x) > 0.01f }
        assertTrue("orbits frame did not move between instants", moved > 0)
    }

    @Test
    fun smallSizeUsesItsOwnTuningNotJustAScale() {
        val large = OrbPresets.frame(OrbState.WORKING, 64f, 1.0f).dots.size
        val small = OrbPresets.frame(OrbState.WORKING, 20f, 1.0f).dots.size
        assertTrue("20dp is not a separate design from 64dp", small != large)
    }
}
