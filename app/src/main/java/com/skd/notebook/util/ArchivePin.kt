package com.skd.notebook.util

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The PIN that guards the Archive screen (Settings → Hide Archive Preview).
 *
 * It is kept in SharedPreferences, but never as the digits themselves: only a random salt and a
 * repeatedly-hashed digest are stored, so reading the preferences file does not reveal the PIN.
 * Wrong guesses are slowed down: after [MAX_FAILURES] misses in a row, checking pauses for [LOCK_MS].
 */
object ArchivePin {
    private const val PREFS_NAME = "notebook_prefs"
    private const val KEY_SALT = "archive_pin_salt"
    private const val KEY_HASH = "archive_pin_hash"

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6
    private const val ROUNDS = 5_000
    private const val MAX_FAILURES = 5
    private const val LOCK_MS = 30_000L

    private var failures = 0
    private var lockedUntil = 0L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isSet(context: Context) = prefs(context).contains(KEY_HASH)

    /** 4 to 6 digits, nothing else. */
    fun isValidFormat(pin: String) = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    fun set(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(context).edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(salt, pin), Base64.NO_WRAP))
            .apply()
        failures = 0
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_SALT).remove(KEY_HASH).apply()
        failures = 0
    }

    /** Seconds left in a "too many tries" pause, or 0 when guesses are allowed. */
    fun lockedSeconds(): Int {
        val left = lockedUntil - SystemClock.elapsedRealtime()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    /** True if [pin] is right. A miss counts toward the pause; a hit resets the count. */
    fun check(context: Context, pin: String): Boolean {
        if (lockedSeconds() > 0) return false
        val saltB64 = prefs(context).getString(KEY_SALT, null) ?: return false
        val hashB64 = prefs(context).getString(KEY_HASH, null) ?: return false
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val actual = hash(Base64.decode(saltB64, Base64.NO_WRAP), pin)
        val ok = MessageDigest.isEqual(expected, actual)
        if (ok) {
            failures = 0
        } else if (++failures >= MAX_FAILURES) {
            failures = 0
            lockedUntil = SystemClock.elapsedRealtime() + LOCK_MS
        }
        return ok
    }

    private fun hash(salt: ByteArray, pin: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        var digest = salt + pin.toByteArray(Charsets.UTF_8)
        repeat(ROUNDS) { digest = md.digest(digest + salt) }
        return digest
    }
}
