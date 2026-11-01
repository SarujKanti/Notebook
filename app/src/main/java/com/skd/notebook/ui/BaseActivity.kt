package com.skd.notebook.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.skd.notebook.R
import com.skd.notebook.util.AppSettings
import com.skd.notebook.util.FontPrefs

/**
 * Base for every screen:
 *  - applies the text size chosen in Settings, and rebuilds itself when that choice changed while
 *    the screen was in the background;
 *  - enforces App Lock: after the app has been away for a while (or on a fresh start) the screen
 *    stays covered until the phone's own screen lock (PIN, pattern, password or fingerprint) is passed.
 */
open class BaseActivity : AppCompatActivity() {

    private var appliedFontSize = FontPrefs.MEDIUM
    private var cover: View? = null
    private var prompting = false

    private val unlockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        prompting = false
        if (result.resultCode == Activity.RESULT_OK) {
            AppLock.locked = false
            removeCover()
        } else {
            finishAffinity() // cancelled: leave the app instead of showing notes
        }
    }

    override fun attachBaseContext(newBase: Context) {
        appliedFontSize = FontPrefs.saved(newBase)
        super.attachBaseContext(FontPrefs.wrap(newBase))
    }

    override fun onStart() {
        super.onStart()
        AppLock.onActivityStarted(this)
    }

    override fun onStop() {
        super.onStop()
        AppLock.onActivityStopped()
    }

    override fun onResume() {
        super.onResume()
        if (FontPrefs.saved(this) != appliedFontSize) {
            recreate()
            return
        }
        if (AppSettings.appLock(this) && AppLock.locked) promptUnlock() else removeCover()
    }

    // ─── Phone screen-lock check (used to reset a forgotten Archive PIN) ─────

    private var deviceLockCallback: (() -> Unit)? = null
    private var deviceLockDenied: (() -> Unit)? = null
    private val deviceLockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = deviceLockCallback
        val denied = deviceLockDenied
        deviceLockCallback = null
        deviceLockDenied = null
        if (result.resultCode == Activity.RESULT_OK) callback?.invoke() else denied?.invoke()
    }

    /**
     * Asks for the phone's own PIN / pattern / password / fingerprint, then runs [onVerified];
     * [onDenied] runs instead if it was cancelled or the phone has no screen lock.
     */
    fun verifyDeviceLock(onDenied: (() -> Unit)? = null, onVerified: () -> Unit) {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "Reset Archive PIN", "Confirm your phone's screen lock to continue"
        )
        if (intent == null) {
            showAppDialog(
                title = "No screen lock set",
                icon = R.drawable.ic_lock,
                message = "The Archive PIN can only be reset after confirming your phone's screen lock, " +
                    "and this phone has none. Set one in your phone's Settings first.",
                positiveText = "Got it",
                negativeText = null,
                onPositive = { onDenied?.invoke() }
            )
            return
        }
        deviceLockCallback = onVerified
        deviceLockDenied = onDenied
        deviceLockLauncher.launch(intent)
    }

    private fun promptUnlock() {
        addCover()
        if (prompting) return
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent = keyguard.createConfirmDeviceCredentialIntent("NoteStack is locked", "Unlock to see your notes")
        if (intent == null) {
            // The phone's screen lock was removed since App Lock was turned on: nothing to ask for.
            AppSettings.setAppLock(this, false)
            AppLock.locked = false
            removeCover()
            return
        }
        prompting = true
        unlockLauncher.launch(intent)
    }

    /** Opaque layer over the whole window so notes are never visible while locked. */
    private fun addCover() {
        if (cover != null) return
        val view = FrameLayout(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.colorBackground))
            isClickable = true
        }
        (window.decorView as ViewGroup).addView(
            view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        cover = view
    }

    private fun removeCover() {
        cover?.let { (it.parent as? ViewGroup)?.removeView(it) }
        cover = null
    }
}

/** Process-wide App Lock state: locked on a cold start and after the app was away too long. */
object AppLock {
    private const val GRACE_MS = 30_000L

    @Volatile var locked = true // a fresh process always starts locked (if App Lock is on)
    private var started = 0
    private var leftAt = 0L

    fun onActivityStarted(context: Context) {
        if (started == 0 && leftAt != 0L &&
            SystemClock.elapsedRealtime() - leftAt > GRACE_MS && AppSettings.appLock(context)
        ) {
            locked = true
        }
        started++
    }

    fun onActivityStopped() {
        started = (started - 1).coerceAtLeast(0)
        if (started == 0) leftAt = SystemClock.elapsedRealtime()
    }
}
