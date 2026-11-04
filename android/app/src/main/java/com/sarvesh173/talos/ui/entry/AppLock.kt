package com.sarvesh173.talos.ui.entry

/**
 * Pure app-lock state machine for TALOS. No Compose, no Android framework types,
 * so every rule here is unit-testable on the JVM.
 *
 * Position in the app: this lock sits ABOVE [com.sarvesh173.talos.ui.features.SecurityScreen]
 * (node encryption fingerprint, provider vault, token management) and above all
 * chat/provider content. It does not replace any of those screens.
 *
 * Wiring (in MainActivity, where the app first becomes interactive):
 *  - MainActivity currently extends ComponentActivity and calls setContent at the
 *    start of its Compose tree. Wrap the root of that tree: when the current
 *    LockState is Locked / LockedOut / SetupRequired, render AppLockPrompt
 *    INSTEAD of the normal content, so nothing underneath (provider list, recent
 *    messages, credential hints) is composed while locked.
 *  - Record the background timestamp in onPause/onStop and, on onResume,
 *    consult [shouldLock] to decide whether to transition to Locked.
 *  - NOTE: BiometricPrompt requires a FragmentActivity host. MainActivity is
 *    currently a ComponentActivity; it must be changed to FragmentActivity
 *    (or the prompt must be hosted from a Fragment) before AppLockPrompt can
 *    show a biometric dialog. See AppLockPrompt.kt.
 *
 * This lock is unrelated to server/auth.py device tokens (mesh pairing
 * credentials, masked in listings). It guards only on-device access to the app.
 */
sealed class LockState {
    object Unlocked : LockState()
    object Locked : LockState()
    object Authenticating : LockState()
    data class LockedOut(val untilMs: Long) : LockState()
    object SetupRequired : LockState()
}

/** Typed rejection for an out-of-range grace value. Never silently clamped. */
sealed class GraceResult {
    data class Ok(val seconds: Int) : GraceResult()
    data class OutOfRange(val seconds: Int, val minSeconds: Int, val maxSeconds: Int) : GraceResult()
}

/**
 * Totally pure lock predicate: should TALOS lock given it was backgrounded at
 * [lastBackgroundedMs] and the current time is [nowMs]?
 *
 * graceSeconds == 0 means immediate lock. A non-positive elapsed (clock moved
 * backwards, or never backgrounded) never locks.
 */
fun shouldLock(lastBackgroundedMs: Long, nowMs: Long, graceSeconds: Int): Boolean {
    val elapsedMs = nowMs - lastBackgroundedMs
    if (elapsedMs < 0L) return false
    return elapsedMs >= graceSeconds * 1000L
}

/**
 * Stateful controller around the pure rules above. Holds the config and the
 * consecutive-failure / lockout-round counters that LockState alone cannot
 * carry.
 *
 * Lockout backoff schedule (documented, also mirrored in tests/test_app_lock.py):
 * after every block of 5 consecutive auth failures, lock out for
 * 30s, then 60s, then 120s, then 240s, then 300s, and stay at 300s for every
 * further round. I.e. duration = min(30 * 2^round, 300) seconds, round starting
 * at 0. A successful unlock resets both the failure count and the round, so the
 * next lockout starts again at 30s.
 */
class AppLockController(
    enabled: Boolean = true,
    hasBiometric: Boolean = false,
    pinConfigured: Boolean = false,
    graceSeconds: Int = DEFAULT_GRACE_SECONDS
) {
    var enabled: Boolean = enabled
        private set

    var hasBiometric: Boolean = hasBiometric
        private set

    var pinConfigured: Boolean = pinConfigured
        private set

    var graceSeconds: Int = graceSeconds
        private set

    /** Consecutive failed attempts since the last success or lockout. */
    var consecutiveFailures: Int = 0
        private set

    /** Number of lockouts so far this unlocked session; drives the backoff. */
    var lockoutRound: Int = 0
        private set

    var state: LockState = LockState.SetupRequired
        private set

    init {
        require(graceSeconds in MIN_GRACE_SECONDS..MAX_GRACE_SECONDS) {
            "graceSeconds $graceSeconds out of range $MIN_GRACE_SECONDS..$MAX_GRACE_SECONDS"
        }
        state = if (!enabled) LockState.Unlocked else resolveInitialState()
    }

    private fun resolveInitialState(): LockState =
        if (hasBiometric || pinConfigured) LockState.Locked else LockState.SetupRequired

    /**
     * Enable or disable the lock. Disabling always lands in Unlocked.
     * Enabling with neither biometrics nor a PIN lands in SetupRequired (the
     * caller must set up a PIN first); otherwise it lands in Locked.
     */
    fun setEnabled(enabled: Boolean, hasBiometric: Boolean): LockState {
        this.enabled = enabled
        this.hasBiometric = hasBiometric
        state = if (!enabled) LockState.Unlocked else resolveInitialState()
        return state
    }

    /** Record whether a PIN has been configured, without touching the state. */
    fun setPinConfigured(configured: Boolean) {
        pinConfigured = configured
    }

    /**
     * Set the idle grace. Values outside 0..300 are rejected as a typed
     * [GraceResult.OutOfRange] and the previous value is kept — never clamped.
     */
    fun setGraceSeconds(seconds: Int): GraceResult {
        if (seconds !in MIN_GRACE_SECONDS..MAX_GRACE_SECONDS) {
            return GraceResult.OutOfRange(seconds, MIN_GRACE_SECONDS, MAX_GRACE_SECONDS)
        }
        graceSeconds = seconds
        return GraceResult.Ok(seconds)
    }

    /**
     * The app was just backgrounded at [nowMs]. Returns Locked when the lock is
     * enabled and the state was Unlocked/Authenticating; otherwise the state is
     * unchanged. The caller decides, on return, whether to actually lock now or
     * to wait out the grace — that decision is [shouldLock] with the recorded
     * background timestamp, so grace is applied on resume, not here.
     */
    fun onAppBackgrounded(state: LockState, nowMs: Long): LockState {
        this.state = when {
            !enabled -> LockState.Unlocked
            state is LockState.Unlocked || state is LockState.Authenticating -> LockState.Locked
            else -> state
        }
        return this.state
    }

    /** A successful authentication. Resets failures and backoff round. */
    fun onUnlocked(state: LockState): LockState {
        consecutiveFailures = 0
        lockoutRound = 0
        this.state = LockState.Unlocked
        return this.state
    }

    /**
     * A failed authentication at [nowMs]. The 5th consecutive failure (and every
     * 5th one after that) transitions to LockedOut with the next backoff
     * duration; the failure count then restarts at 0 and the round advances.
     * Failures 1-4 stay in Locked/Authenticating.
     */
    fun onAuthFailed(state: LockState, nowMs: Long): LockState {
        if (state is LockState.LockedOut && nowMs < state.untilMs) {
            return state
        }
        consecutiveFailures += 1
        if (consecutiveFailures >= FAILURES_BEFORE_LOCKOUT) {
            val durationMs = backoffSeconds(lockoutRound) * 1000L
            consecutiveFailures = 0
            lockoutRound += 1
            this.state = LockState.LockedOut(nowMs + durationMs)
        } else {
            this.state = if (state is LockState.LockedOut) LockState.Locked else state
        }
        return this.state
    }

    /** True when a LockedOut period has expired and the lock is simply Locked. */
    fun isLockedOutActive(nowMs: Long): Boolean {
        val current = state
        return current is LockState.LockedOut && nowMs < current.untilMs
    }

    companion object {
        const val DEFAULT_GRACE_SECONDS: Int = 30
        const val MIN_GRACE_SECONDS: Int = 0
        const val MAX_GRACE_SECONDS: Int = 300
        const val FAILURES_BEFORE_LOCKOUT: Int = 5
        const val BASE_LOCKOUT_SECONDS: Int = 30
        const val MAX_LOCKOUT_SECONDS: Int = 300

        /**
         * Backoff for [round]: 30s, 60s, 120s, 240s, then capped at 300s.
         * round is 0-based: round 0 is the first lockout (30s).
         */
        fun backoffSeconds(round: Int): Int {
            val safeRound = if (round < 0) 0 else if (round > 10) 10 else round
            val doubled = BASE_LOCKOUT_SECONDS shl safeRound
            return if (doubled > MAX_LOCKOUT_SECONDS) MAX_LOCKOUT_SECONDS else doubled
        }
    }
}
