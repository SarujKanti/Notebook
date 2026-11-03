package com.skd.notebook.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.os.SystemClock
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.LayoutInflaterCompat
import com.google.android.material.snackbar.Snackbar
import com.skd.notebook.R
import com.skd.notebook.util.AppSettings
import com.skd.notebook.util.FontPrefs
import com.skd.notebook.util.FontStyle
import com.skd.notebook.util.FontStyles

/**
 * Base for every screen:
 *  - applies the text size and font style chosen in Settings, and rebuilds itself when either changed while
 *    the screen was in the background;
 *  - enforces App Lock: after the app has been away for a while (or on a fresh start) the screen
 *    stays covered until the phone's own screen lock (PIN, pattern, password or fingerprint) is passed.
 */
open class BaseActivity : AppCompatActivity() {

    private var appliedFontSize = FontPrefs.MEDIUM
    private var appliedFontStyle = FontStyle.DEVICE
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
        if (FontPrefs.saved(this) != appliedFontSize || FontStyles.saved(this) != appliedFontStyle) {
            recreate()
            return
        }
        if (AppSettings.appLock(this) && AppLock.locked) promptUnlock() else removeCover()
    }

    // ─── Font style (Settings → Font Style) ──────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        installFontFactory()
        super.onCreate(savedInstanceState)
    }

    /** Text on screen that is built in code, and the app bar's title, get the style once the screen is set up. */
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        FontStyles.applyToTree(window.decorView)
    }

    override fun onContentChanged() {
        super.onContentChanged()
        FontStyles.applyToTree(window.decorView)
    }

    /**
     * Puts the chosen font on every text view as it is inflated (list items, sheets and dialogs too).
     * AppCompat normally installs its own view factory; this one hands the work to it and styles the result.
     */
    private fun installFontFactory() {
        appliedFontStyle = FontStyles.saved(this)
        val typeface = appliedFontStyle.typeface(this) ?: return
        val inflater = LayoutInflater.from(this)
        if (inflater.factory2 != null) return
        LayoutInflaterCompat.setFactory2(inflater, object : LayoutInflater.Factory2 {
            override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
                val view = delegate.createView(parent, name, context, attrs)
                FontStyles.applyTo(view, typeface)
                return view
            }

            override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
                onCreateView(null, name, context, attrs)
        })
    }

    // ─── Voice typing in the note editor (English only) ──────────────────────

    private var dictationTarget: EditText? = null
    private val dictationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val target = dictationTarget
        dictationTarget = null
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && target != null && !spoken.isNullOrBlank()) {
            insertSpokenText(target, spoken.trim())
        }
    }

    /** Makes [mic] speak into the title if it has the cursor, otherwise into the note text. */
    fun attachDictation(mic: View, title: EditText, note: EditText) {
        mic.setOnClickListener { dictateInto(if (title.hasFocus()) title else note) }
    }

    /**
     * Opens the phone's speech recognizer, asked for English only, and types the result into [target].
     * The recognizer shows its own microphone screen, so the app needs no microphone permission.
     */
    private fun dictateInto(target: EditText) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, ENGLISH)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, ENGLISH)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speak_now_english))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        dictationTarget = target
        try {
            dictationLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            dictationTarget = null
            Snackbar.make(target, R.string.voice_input_unavailable, Snackbar.LENGTH_SHORT).show()
        }
    }

    /** Puts [spoken] where the cursor is (replacing any selection), with a space before it and a capital letter at a sentence start. */
    private fun insertSpokenText(target: EditText, spoken: String) {
        val text = target.text
        val start = if (target.selectionStart >= 0) minOf(target.selectionStart, target.selectionEnd) else text.length
        val end = if (target.selectionEnd >= 0) maxOf(target.selectionStart, target.selectionEnd) else text.length

        val before = text.substring(0, start).trimEnd()
        val startsSentence = before.isEmpty() || before.last() in ".!?\n"
        val piece = if (startsSentence) spoken.replaceFirstChar { it.uppercaseChar() } else spoken
        val needsSpace = start > 0 && !text[start - 1].isWhitespace()
        val inserted = if (needsSpace) " $piece" else piece

        text.replace(start, end, inserted)
        target.setSelection(start + inserted.length)
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
            getString(R.string.reset_archive_pin), getString(R.string.confirm_screen_lock)
        )
        if (intent == null) {
            showAppDialog(
                title = getString(R.string.no_screen_lock_title),
                icon = R.drawable.ic_lock,
                message = getString(R.string.no_screen_lock_message),
                positiveText = getString(R.string.got_it),
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
        val intent = keyguard.createConfirmDeviceCredentialIntent(getString(R.string.lock_notestack_title), getString(R.string.unlock_to_see_notes))
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

private const val ENGLISH = "en-US"

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
