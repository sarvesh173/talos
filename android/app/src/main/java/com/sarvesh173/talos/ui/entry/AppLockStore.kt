package com.sarvesh173.talos.ui.entry

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistence for the app lock.
 *
 * Deliberately tiny and boring: three booleans/ints plus the PIN material. The
 * PIN itself is never stored — only a per-install random salt and the salted
 * SHA-256 hash produced by [PinHasher]. Everything lives in the app's private
 * SharedPreferences, so a second user account on the same device cannot read
 * it, and `adb backup` cannot exfiltrate it on API 31+ (allowBackup is false
 * in the manifest).
 *
 * This is *not* a credential vault. Provider API keys continue to live in the
 * server-side vault; this store only decides whether the UI is reachable.
 */
class AppLockStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // -- configuration ------------------------------------------------------

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var graceSeconds: Int
        get() = prefs.getInt(KEY_GRACE, AppLockController.DEFAULT_GRACE_SECONDS)
            .coerceIn(AppLockController.MIN_GRACE_SECONDS, AppLockController.MAX_GRACE_SECONDS)
        set(value) = prefs.edit()
            .putInt(
                KEY_GRACE,
                value.coerceIn(
                    AppLockController.MIN_GRACE_SECONDS,
                    AppLockController.MAX_GRACE_SECONDS
                )
            )
            .apply()

    /** Wall-clock millis when the app last left the foreground, 0 if never. */
    var lastBackgroundedMs: Long
        get() = prefs.getLong(KEY_LAST_BACKGROUNDED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKGROUNDED, value).apply()

    // -- PIN material -------------------------------------------------------

    /** Stored hex SHA-256 of (salt || pin), or null when no PIN is configured. */
    var pinHashHex: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        private set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_PIN_HASH) else editor.putString(KEY_PIN_HASH, value)
            editor.apply()
        }

    /** Base64 of the 16-byte salt; null when no PIN is configured. */
    var pinSaltBase64: String?
        get() = prefs.getString(KEY_PIN_SALT, null)
        private set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_PIN_SALT) else editor.putString(KEY_PIN_SALT, value)
            editor.apply()
        }

    val hasPin: Boolean
        get() = pinHashHex != null && pinSaltBase64 != null

    fun saltBytes(): ByteArray? =
        pinSaltBase64?.let { raw ->
            runCatching { android.util.Base64.decode(raw, android.util.Base64.NO_WRAP) }.getOrNull()
        }

    /**
     * Set or replace the PIN. A fresh salt is generated every time, so changing
     * the PIN never reuses old salt material.
     */
    fun setPin(pin: String): Boolean {
        if (!PinHasher.isValidPinFormat(pin)) return false
        val salt = PinHasher.generateSalt()
        pinSaltBase64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
        pinHashHex = PinHasher.hashPin(pin, salt)
        return true
    }

    /** Remove the PIN. Only safe when biometrics are available or the lock is off. */
    fun clearPin() {
        pinSaltBase64 = null
        pinHashHex = null
    }

    private companion object {
        const val PREFS = "talos_app_lock"
        const val KEY_ENABLED = "lock_enabled"
        const val KEY_GRACE = "lock_grace_seconds"
        const val KEY_LAST_BACKGROUNDED = "lock_last_backgrounded_ms"
        const val KEY_PIN_HASH = "lock_pin_hash"
        const val KEY_PIN_SALT = "lock_pin_salt"
    }
}
