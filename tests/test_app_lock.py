"""Tests for the app lock state machine and PIN fallback.

Mirrors android/app/src/main/java/com/sarvesh173/talos/ui/entry/AppLock.kt and
AppLockPrompt.kt (PinHasher) in pure Python so the rules are verified without a
Gradle build. Backoff schedule: 30s, 60s, 120s, 240s, then capped at 300s.
"""

import hashlib
import hmac
import os
import unittest

DEFAULT_GRACE_SECONDS = 30
MIN_GRACE_SECONDS = 0
MAX_GRACE_SECONDS = 300
FAILURES_BEFORE_LOCKOUT = 5
BASE_LOCKOUT_SECONDS = 30
MAX_LOCKOUT_SECONDS = 300

SALT_BYTES = 16
MIN_PIN_LENGTH = 4
MAX_PIN_LENGTH = 6


def should_lock(last_backgrounded_ms: int, now_ms: int, grace_seconds: int = DEFAULT_GRACE_SECONDS) -> bool:
    elapsed = now_ms - last_backgrounded_ms
    if elapsed < 0:
        return False
    return elapsed >= grace_seconds * 1000


def backoff_seconds(lockout_round: int) -> int:
    safe = max(0, min(lockout_round, 10))
    return min(BASE_LOCKOUT_SECONDS * (2**safe), MAX_LOCKOUT_SECONDS)


class LockStateMachine:
    def __init__(self, enabled=True, has_biometric=False, pin_configured=False, grace_seconds=DEFAULT_GRACE_SECONDS):
        self.enabled = enabled
        self.has_biometric = has_biometric
        self.pin_configured = pin_configured
        self.grace_seconds = grace_seconds
        self.consecutive_failures = 0
        self.lockout_round = 0
        self.state = "unlocked" if not enabled else self._initial()

    def _initial(self):
        return "locked" if (self.has_biometric or self.pin_configured) else "setup_required"

    def set_enabled(self, enabled, has_biometric):
        self.enabled = enabled
        self.has_biometric = has_biometric
        self.state = "unlocked" if not enabled else self._initial()
        return self.state

    def set_grace_seconds(self, seconds):
        if not (MIN_GRACE_SECONDS <= seconds <= MAX_GRACE_SECONDS):
            return ("out_of_range", seconds)
        self.grace_seconds = seconds
        return ("ok", seconds)

    def on_app_backgrounded(self, state, now_ms):
        if not self.enabled:
            self.state = "unlocked"
        elif state in ("unlocked", "authenticating"):
            self.state = "locked"
        else:
            self.state = state
        return self.state

    def on_unlocked(self, state):
        self.consecutive_failures = 0
        self.lockout_round = 0
        self.state = "unlocked"
        return self.state

    def on_auth_failed(self, state, now_ms):
        if isinstance(state, tuple) and state[0] == "locked_out" and now_ms < state[1]:
            return state
        self.consecutive_failures += 1
        if self.consecutive_failures >= FAILURES_BEFORE_LOCKOUT:
            duration_ms = backoff_seconds(self.lockout_round) * 1000
            self.consecutive_failures = 0
            self.lockout_round += 1
            self.state = ("locked_out", now_ms + duration_ms)
        else:
            self.state = "locked" if (isinstance(state, tuple) and state[0] == "locked_out") else state
        return self.state


def is_valid_pin_format(pin: str) -> bool:
    return MIN_PIN_LENGTH <= len(pin) <= MAX_PIN_LENGTH and pin.isdigit()


def hash_pin(pin: str, salt: bytes) -> str:
    assert is_valid_pin_format(pin)
    return hashlib.sha256(salt + pin.encode("utf-8")).hexdigest()


def verify_pin(pin: str, salt: bytes, expected_hex: str) -> bool:
    if not is_valid_pin_format(pin):
        return False
    actual = hash_pin(pin, salt)
    return hmac.compare_digest(actual.encode(), expected_hex.encode())


def show_skip_action(grace_seconds: int, has_biometric: bool, consecutive_failures: int) -> bool:
    if grace_seconds == 0:
        return False
    if has_biometric and consecutive_failures >= 3:
        return False
    return True


class AppLockTest(unittest.TestCase):
    def test_backgrounding_locks_after_grace_not_before(self):
        self.assertFalse(should_lock(1_000_000, 1_000_000 + 29_999))
        self.assertTrue(should_lock(1_000_000, 1_000_000 + 30_000))
        self.assertTrue(should_lock(1_000_000, 1_000_000 + 60_000))

    def test_grace_zero_means_immediate_lock(self):
        self.assertTrue(should_lock(5_000, 5_000, grace_seconds=0))
        self.assertTrue(should_lock(5_000, 5_001, grace_seconds=0))

    def test_clock_skew_never_locks(self):
        self.assertFalse(should_lock(10_000, 9_000))

    def test_five_failures_trigger_lockout(self):
        m = LockStateMachine(has_biometric=True)
        state = "locked"
        for _ in range(4):
            state = m.on_auth_failed(state, 1_000_000)
            self.assertEqual(state, "locked")
        state = m.on_auth_failed(state, 1_000_000)
        self.assertEqual(state, ("locked_out", 1_000_000 + 30_000))

    def test_lockout_backoff_doubles_and_caps(self):
        self.assertEqual(backoff_seconds(0), 30)
        self.assertEqual(backoff_seconds(1), 60)
        self.assertEqual(backoff_seconds(2), 120)
        self.assertEqual(backoff_seconds(3), 240)
        self.assertEqual(backoff_seconds(4), 300)
        self.assertEqual(backoff_seconds(10), 300)

    def test_success_resets_failure_count_and_round(self):
        m = LockStateMachine(has_biometric=True)
        state = "locked"
        for _ in range(5):
            state = m.on_auth_failed(state, 0)
        self.assertEqual(state[0], "locked_out")
        self.assertEqual(m.lockout_round, 1)
        m.on_unlocked(state)
        self.assertEqual(m.consecutive_failures, 0)
        self.assertEqual(m.lockout_round, 0)
        # Next lockout starts again at 30s.
        state = "locked"
        for _ in range(5):
            state = m.on_auth_failed(state, 100_000)
        self.assertEqual(state, ("locked_out", 100_000 + 30_000))

    def test_pin_round_trip(self):
        salt = os.urandom(SALT_BYTES)
        digest = hash_pin("1234", salt)
        self.assertTrue(verify_pin("1234", salt, digest))
        self.assertTrue(verify_pin("123456", salt, hash_pin("123456", salt)))

    def test_wrong_pin_does_not_verify(self):
        salt = os.urandom(SALT_BYTES)
        digest = hash_pin("1234", salt)
        self.assertFalse(verify_pin("1235", salt, digest))
        self.assertFalse(verify_pin("123", salt, digest))  # too short
        self.assertFalse(verify_pin("1234567", salt, digest))  # too long
        self.assertFalse(verify_pin("12a4", salt, digest))  # non-digit

    def test_two_salts_produce_different_hashes(self):
        salt_a = b"\x01" * SALT_BYTES
        salt_b = b"\x02" * SALT_BYTES
        self.assertNotEqual(hash_pin("1234", salt_a), hash_pin("1234", salt_b))
        # And a hash made with one salt does not verify under the other.
        self.assertFalse(verify_pin("1234", salt_b, hash_pin("1234", salt_a)))

    def test_out_of_range_grace_rejected_not_clamped(self):
        m = LockStateMachine(has_biometric=True)
        self.assertEqual(m.set_grace_seconds(-1), ("out_of_range", -1))
        self.assertEqual(m.grace_seconds, DEFAULT_GRACE_SECONDS)
        self.assertEqual(m.set_grace_seconds(301), ("out_of_range", 301))
        self.assertEqual(m.grace_seconds, DEFAULT_GRACE_SECONDS)
        self.assertEqual(m.set_grace_seconds(0), ("ok", 0))
        self.assertEqual(m.set_grace_seconds(300), ("ok", 300))

    def test_skip_action_rules(self):
        self.assertFalse(show_skip_action(0, False, 0))
        self.assertTrue(show_skip_action(30, False, 0))
        self.assertFalse(show_skip_action(30, True, 3))
        self.assertFalse(show_skip_action(30, True, 5))
        self.assertTrue(show_skip_action(30, True, 2))

    def test_set_enabled_without_auth_method_is_setup_required(self):
        m = LockStateMachine(enabled=False)
        self.assertEqual(m.set_enabled(True, False), "setup_required")
        self.assertEqual(m.set_enabled(True, True), "locked")
        self.assertEqual(m.set_enabled(False, True), "unlocked")


if __name__ == "__main__":
    unittest.main()
