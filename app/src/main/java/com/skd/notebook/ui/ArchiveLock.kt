package com.skd.notebook.ui

import androidx.appcompat.app.AlertDialog
import com.skd.notebook.R
import com.skd.notebook.util.AppSettings
import com.skd.notebook.util.ArchivePin

/**
 * The dialogs behind the Archive PIN (Settings → Hide Archive Preview): ask for it, set it, change it,
 * and reset it when it is forgotten. They live here so Settings and the Archive screen share them.
 */

private fun tooManyTries(seconds: Int) = "Too many wrong tries. Try again in $seconds seconds."

/** Asks for the current PIN and calls [onOk] once a right one is entered. */
fun BaseActivity.askArchivePin(
    title: String,
    subtitle: String?,
    positiveText: String,
    cancelable: Boolean = true,
    onForgot: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onOk: () -> Unit
): AlertDialog {
    return showPinDialog(
        title = title, subtitle = subtitle, icon = R.drawable.ic_lock,
        fields = listOf("PIN"), positiveText = positiveText,
        cancelable = cancelable, onForgot = onForgot, onCancel = onCancel, onAccepted = onOk
    ) { values ->
        val wait = ArchivePin.lockedSeconds()
        when {
            wait > 0 -> tooManyTries(wait)
            ArchivePin.check(this, values[0]) -> null
            ArchivePin.lockedSeconds() > 0 -> tooManyTries(ArchivePin.lockedSeconds())
            else -> "Wrong PIN. Try again."
        }
    }
}

/** Sets a brand-new PIN (type it twice). */
fun BaseActivity.setNewArchivePin(
    title: String = "Set Archive PIN",
    subtitle: String? = "You will need it every time you open the Archive",
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    showPinDialog(
        title = title, subtitle = subtitle, icon = R.drawable.ic_lock,
        fields = listOf("New PIN (4 to 6 digits)", "Confirm PIN"),
        positiveText = "Set PIN", onCancel = onCancel, onAccepted = onDone
    ) { values ->
        when {
            !ArchivePin.isValidFormat(values[0]) -> "The PIN must be 4 to 6 digits."
            values[0] != values[1] -> "The two PINs do not match."
            else -> { ArchivePin.set(this, values[0]); null }
        }
    }
}

/** Replaces the PIN: the current one is needed first. */
fun BaseActivity.changeArchivePin(onChanged: () -> Unit) {
    showPinDialog(
        title = "Change Archive PIN", subtitle = "Enter your current PIN, then a new one",
        icon = R.drawable.ic_lock,
        fields = listOf("Current PIN", "New PIN (4 to 6 digits)", "Confirm new PIN"),
        positiveText = "Change PIN",
        onForgot = { forgotArchivePin(onDone = onChanged) { setNewArchivePin(onCancel = onChanged, onDone = onChanged) } },
        onAccepted = onChanged
    ) { values ->
        val wait = ArchivePin.lockedSeconds()
        when {
            wait > 0 -> tooManyTries(wait)
            !ArchivePin.check(this, values[0]) ->
                if (ArchivePin.lockedSeconds() > 0) tooManyTries(ArchivePin.lockedSeconds()) else "The current PIN is wrong."
            !ArchivePin.isValidFormat(values[1]) -> "The new PIN must be 4 to 6 digits."
            values[1] != values[2] -> "The two new PINs do not match."
            else -> { ArchivePin.set(this, values[1]); null }
        }
    }
}

/**
 * "Forgot PIN?": after the phone's own screen lock is confirmed the old PIN is removed and the
 * protection is switched off, so the user can turn it on again with a new PIN.
 */
fun BaseActivity.forgotArchivePin(onDone: () -> Unit = {}, onReset: () -> Unit = onDone) {
    verifyDeviceLock(onDenied = onDone) {
        ArchivePin.clear(this)
        AppSettings.setHideArchivePreview(this, false)
        onReset()
    }
}
