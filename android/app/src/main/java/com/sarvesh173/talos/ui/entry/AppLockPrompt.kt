package com.sarvesh173.talos.ui.entry

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PIN hashing for the fallback path of [AppLockPrompt].
 *
 * The PIN is never stored. Only a salted SHA-256 hash is persisted (by the
 * caller, e.g. in EncryptedSharedPreferences or the provider vault store),
 * together with the per-install random salt. Verification recomputes the hash
 * and compares in constant time via [MessageDigest.isEqual], so timing does not
 * leak how many leading bytes matched.
 *
 * Each salt must be a fresh 16-byte value from [generateSalt]; the same PIN
 * with two different salts must produce different hashes.
 */
object PinHasher {
    const val SALT_BYTES: Int = 16
    const val MIN_PIN_LENGTH: Int = 4
    const val MAX_PIN_LENGTH: Int = 6

    private val secureRandom: SecureRandom = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun isValidPinFormat(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    /** Returns the lowercase hex SHA-256 of salt bytes followed by the PIN. */
    fun hashPin(pin: String, salt: ByteArray): String {
        require(isValidPinFormat(pin)) { "PIN must be 4-6 digits" }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /** Constant-time verification of [pin] against a stored hex hash. */
    fun verifyPin(pin: String, salt: ByteArray, expectedHex: String): Boolean {
        if (!isValidPinFormat(pin)) return false
        val actual = hashPin(pin, salt).toByteArray(Charsets.UTF_8)
        val expected = expectedHex.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(actual, expected)
    }
}

/**
 * Totally plain lock screen. Neutral monochrome (MaterialTheme colors only),
 * sentence case, no emoji, no provider names, no recent message text, and no
 * credential hints — while locked, TALOS shows nothing about what it holds.
 *
 * The prompt text states what is being protected: provider keys, messages and
 * device credentials, in the app named TALOS.
 *
 * [onUnlocked] is called after a successful biometric or PIN authentication.
 * [onSkipForSession] is only invoked from the skip action, which is only shown
 * when a non-zero idle grace is configured (see [showSkipAction]).
 *
 * Host requirement: BiometricPrompt needs a FragmentActivity. MainActivity is
 * currently a ComponentActivity, so it must become a FragmentActivity before
 * the biometric path can appear; the PIN path works regardless.
 */
@Composable
fun AppLockPrompt(
    hasBiometric: Boolean,
    graceSeconds: Int,
    consecutiveFailures: Int,
    pinSalt: ByteArray?,
    pinHashHex: String?,
    onUnlocked: () -> Unit,
    onSkipForSession: () -> Unit,
    onAuthFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    fun tryBiometric() {
        val activity = context as? FragmentActivity ?: run {
            biometricError = "Biometric unlock is not available in this screen."
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    biometricError = "Biometric unlock did not work. Use your PIN instead."
                }
            }

            override fun onAuthenticationFailed() {
                onAuthFailed()
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock TALOS")
            .setSubtitle("TALOS is locked. Unlock to access your provider keys, messages and settings.")
            .setNegativeButtonText("Use PIN instead")
            .build()
        prompt.authenticate(info)
    }

    fun submitPin() {
        val salt = pinSalt
        val stored = pinHashHex
        if (salt == null || stored == null) {
            pinError = "No PIN is set up yet. Set one up in security settings."
            return
        }
        if (!PinHasher.isValidPinFormat(pinInput)) {
            pinError = "Enter your 4 to 6 digit PIN."
            return
        }
        if (PinHasher.verifyPin(pinInput, salt, stored)) {
            onUnlocked()
        } else {
            pinError = "Incorrect PIN."
            onAuthFailed()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TALOS is locked",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Unlock to access your provider keys, messages and settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (hasBiometric) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { tryBiometric() }) {
                Text(text = "Unlock with biometrics")
            }
            if (biometricError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = biometricError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (pinHashHex != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Or enter your PIN",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it.filter { ch -> ch in '0'..'9' } },
                label = { Text(text = "PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = pinError != null,
                modifier = Modifier.fillMaxWidth()
            )
            if (pinError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pinError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { submitPin() }) {
                Text(text = "Unlock")
            }
        }

        if (showSkipAction(graceSeconds, hasBiometric, consecutiveFailures)) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onSkipForSession) {
                Text(text = "Skip for this session (reduces protection)")
            }
        }
    }
}

/**
 * Skip-for-this-session is only valid when the idle grace is non-zero, and it
 * is never offered when biometrics are available and the user has already
 * failed 3 or more times (repeated failure means the lock is doing its job).
 */
fun showSkipAction(graceSeconds: Int, hasBiometric: Boolean, consecutiveFailures: Int): Boolean {
    if (graceSeconds == 0) return false
    if (hasBiometric && consecutiveFailures >= 3) return false
    return true
}

/**
 * Convenience check for callers deciding which path to show first.
 * Returns true when the device has an enrolled biometric authenticator.
 */
fun isBiometricAvailable(activity: FragmentActivity): Boolean {
    val manager = BiometricManager.from(activity)
    return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS
}
