package com.sarvesh173.talos.agent

import com.sarvesh173.talos.ui.avatar.AgentAvatarEngine
import com.sarvesh173.talos.ui.avatar.AvatarIdentity
import com.sarvesh173.talos.ui.avatar.AvatarState
import com.sarvesh173.talos.ui.avatar.SpringValue
import com.sarvesh173.talos.ui.avatar.browRadians
import com.sarvesh173.talos.ui.avatar.lidCoverage
import com.sarvesh173.talos.ui.avatar.pupilOffset
import com.sarvesh173.talos.ui.avatar.squashToScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AgentAvatarEngineTest {

    @Test
    fun springSettlesOnItsTarget() {
        val s = SpringValue(0f, 1f)
        repeat(600) { s.step(1f / 240f) }
        assertTrue("expected ~1.0, was ${s.x}", abs(s.x - 1f) < 0.01f)
        assertTrue(s.isSettled)
    }

    @Test
    fun springNeverExplodesOnAHugeDelta() {
        val s = SpringValue(0f, 1f)
        // A resumed app can hand in a multi-second delta.
        s.step(5f)
        assertTrue("value must stay finite", !s.x.isNaN() && !s.x.isInfinite())
        assertTrue("must not overshoot wildly", s.x < 3f)
    }

    @Test
    fun identityIsStableAndSpreadAroundTheWheel() {
        val a1 = AvatarIdentity.of("opencode", "OpenCode")
        val a2 = AvatarIdentity.of("opencode", "OpenCode")
        assertEquals(a1.hue, a2.hue, 0.001f)

        // Different ids should not collapse onto one colour.
        val hues = listOf("a", "b", "c", "d", "e").map { AvatarIdentity.of(it).hue }
        assertTrue(hues.distinct().size >= 4)
    }

    @Test
    fun thinkingNarrowsTheEyesAndBrowsTheOtherWay() {
        val e = AgentAvatarEngine(AvatarIdentity.of("x"), AvatarState.Idle)
        e.setState(AvatarState.Thinking)
        repeat(200) { e.update(1f / 60f) }
        assertTrue("eyes narrow", e.eyeOpen.x < 0.9f)
        assertTrue("brow goes up-inward", e.browAngle.x < -0.05f)
        assertTrue("morph engages", e.morph.x > 0.1f)
    }

    @Test
    fun errorSquashesAndFrowns() {
        val e = AgentAvatarEngine(AvatarIdentity.of("x"))
        e.setState(AvatarState.Error)
        repeat(200) { e.update(1f / 60f) }
        assertTrue("flattened", e.squash.x > 0.02f)
        assertTrue("brows down", e.browAngle.x > 0.05f)
        assertTrue(browRadians(AvatarState.Error) > 0f)
    }

    @Test
    fun tempoIsClampedSoTheAvatarNeverFreezesOrGlitches() {
        val e = AgentAvatarEngine(AvatarIdentity.of("x"))
        e.setTempoFromRate(0.2f)
        assertTrue(e.tempo >= 0.35f)
        e.setTempoFromRate(999f)
        assertTrue(e.tempo <= 2.2f)
    }

    @Test
    fun fasterOutputMakesTheClockRunFaster() {
        val slow = AgentAvatarEngine(AvatarIdentity.of("x"))
        val fast = AgentAvatarEngine(AvatarIdentity.of("x"))
        slow.setTempo(0.5f)
        fast.setTempo(2.0f)
        repeat(60) {
            slow.update(1f / 60f)
            fast.update(1f / 60f)
        }
        assertTrue(fast.clock > slow.clock * 2f)
    }

    @Test
    fun squashPreservesVolume() {
        for (v in listOf(-0.2f, 0f, 0.2f, 0.5f)) {
            val (sx, sy) = squashToScale(v)
            assertEquals("volume at $v", 1f, sx * sy, 0.02f)
        }
    }

    @Test
    fun pupilStaysInsideTheEye() {
        val (x, y) = pupilOffset(9f, 9f, 4f)
        val mag = kotlin.math.sqrt(x * x + y * y)
        assertTrue("pupil escaped: $mag", mag <= 4.001f)
    }

    @Test
    fun lidClosesFullyAtZeroAndOpensPastOne() {
        assertEquals(1f, lidCoverage(0f), 0.001f)
        assertEquals(0f, lidCoverage(1f), 0.001f)
        assertEquals(0f, lidCoverage(1.25f), 0.001f)
    }

    @Test
    fun happyBouncesThenRests() {
        val e = AgentAvatarEngine(AvatarIdentity.of("x"))
        e.setState(AvatarState.Happy)
        repeat(12) { e.update(1f / 60f) }
        val early = e.squash.x
        repeat(240) { e.update(1f / 60f) }
        assertNotEquals("should move", early, e.squash.x, 0.001f)
        assertTrue("settles near rest", abs(e.squash.x) < 0.1f)
    }

    @Test
    fun orbitDotsAreSpreadNotStacked() {
        val e = AgentAvatarEngine(AvatarIdentity.of("x"))
        val points = (0 until 8).map { e.orbitDot(it, 8, 10f) }
        val xs = points.map { it.first }
        assertTrue("dots must differ", xs.distinct().size > 5)
    }
}
