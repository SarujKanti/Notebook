package com.skd.notebook.ui

import androidx.appcompat.app.AlertDialog
import com.skd.notebook.R
import com.skd.notebook.util.AppSettings
import com.skd.notebook.util.ArchivePin

/**
 * The dialogs behind the Archive PIN (Settings → Hide Archive Preview): ask for it, set it, change it,
 * and reset it when it is forgotten. They live here so Settings and the Archive screen share them.
 */

private fun BaseActivity.tooManyTries(seconds: Int) = getString(R.string.too_many_tries_format, seconds)

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
        fields = listOf(getString(R.string.pin)), positiveText = positiveText,
        cancelable = cancelable, onForgot = onForgot, onCancel = onCancel, onAccepted = onOk
    ) { values ->
        val wait = ArchivePin.lockedSeconds()
        when {
            wait > 0 -> tooManyTries(wait)
            ArchivePin.check(this, values[0]) -> null
            ArchivePin.lockedSeconds() > 0 -> tooManyTries(ArchivePin.lockedSeconds())
            else -> getString(R.string.wrong_pin)
        }
    }
}

/** Sets a brand-new PIN (type it twice). */
fun BaseActivity.setNewArchivePin(
    title: String = getString(R.string.set_archive_pin),
    subtitle: String? = getString(R.string.set_archive_pin_subtitle),
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    showPinDialog(
        title = title, subtitle = subtitle, icon = R.drawable.ic_lock,
        fields = listOf(getString(R.string.new_pin_hint), getString(R.string.confirm_pin)),
        positiveText = getString(R.string.set_pin), onCancel = onCancel, onAccepted = onDone
    ) { values ->
        when {
            !ArchivePin.isValidFormat(values[0]) -> getString(R.string.pin_format_error)
            values[0] != values[1] -> getString(R.string.pins_do_not_match)
            else -> { ArchivePin.set(this, values[0]); null }
        }
    }
}

/** Replaces the PIN: the current one is needed first. */
fun BaseActivity.changeArchivePin(onChanged: () -> Unit) {
    showPinDialog(
        title = getString(R.string.change_archive_pin), subtitle = getString(R.string.change_archive_pin_subtitle),
        icon = R.drawable.ic_lock,
        fields = listOf(getString(R.string.current_pin), getString(R.string.new_pin_hint), getString(R.string.confirm_new_pin)),
        positiveText = getString(R.string.change_pin),
        onForgot = { forgotArchivePin(onDone = onChanged) { setNewArchivePin(onCancel = onChanged, onDone = onChanged) } },
        onAccepted = onChanged
    ) { values ->
        val wait = ArchivePin.lockedSeconds()
        when {
            wait > 0 -> tooManyTries(wait)
            !ArchivePin.check(this, values[0]) ->
                if (ArchivePin.lockedSeconds() > 0) tooManyTries(ArchivePin.lockedSeconds()) else getString(R.string.current_pin_wrong)
            !ArchivePin.isValidFormat(values[1]) -> getString(R.string.new_pin_format_error)
            values[1] != values[2] -> getString(R.string.new_pins_do_not_match)
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
