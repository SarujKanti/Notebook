package com.skd.notebook.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.skd.notebook.R
import com.skd.notebook.ui.MainActivity
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var enteredPhone: String = ""

    // Views — Step 1
    private lateinit var stepPhoneLayout: LinearLayout
    private lateinit var tilPhone: TextInputLayout
    private lateinit var etPhone: TextInputEditText
    private lateinit var btnSendOtp: MaterialButton

    // Views — Step 2
    private lateinit var stepOtpLayout: LinearLayout
    private lateinit var tvPhoneMasked: TextView
    private lateinit var tilOtp: TextInputLayout
    private lateinit var etOtp: TextInputEditText
    private lateinit var btnVerifyOtp: MaterialButton
    private lateinit var tvResend: TextView
    private lateinit var tvChangeNumber: TextView

    // Shared
    private lateinit var tvSubtitle: TextView
    private lateinit var progressBar: ProgressBar

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Already signed in — skip straight to main
        if (auth.currentUser != null) { goToMain(); return }

        setContentView(R.layout.activity_login)
        bindViews()
        showStep1()

        btnSendOtp.setOnClickListener   { sendOtp(useResendToken = false) }
        btnVerifyOtp.setOnClickListener { verifyOtp() }
        tvResend.setOnClickListener     { sendOtp(useResendToken = true) }
        tvChangeNumber.setOnClickListener {
            verificationId = null
            resendToken    = null
            etOtp.text?.clear()
            tilOtp.error = null
            showStep1()
        }
    }

    // ─── Bind views ──────────────────────────────────────────────────────────

    private fun bindViews() {
        tvSubtitle      = findViewById(R.id.tvSubtitle)

        stepPhoneLayout = findViewById(R.id.stepPhoneLayout)
        tilPhone        = findViewById(R.id.tilPhone)
        etPhone         = findViewById(R.id.etPhone)
        btnSendOtp      = findViewById(R.id.btnSendOtp)

        stepOtpLayout   = findViewById(R.id.stepOtpLayout)
        tvPhoneMasked   = findViewById(R.id.tvPhoneMasked)
        tilOtp          = findViewById(R.id.tilOtp)
        etOtp           = findViewById(R.id.etOtp)
        btnVerifyOtp    = findViewById(R.id.btnVerifyOtp)
        tvResend        = findViewById(R.id.tvResend)
        tvChangeNumber  = findViewById(R.id.tvChangeNumber)

        progressBar     = findViewById(R.id.progressBar)
    }

    // ─── Step transitions ────────────────────────────────────────────────────

    private fun showStep1() {
        tvSubtitle.setText(R.string.phone_subtitle_step1)
        stepPhoneLayout.visibility = View.VISIBLE
        stepOtpLayout.visibility   = View.GONE
        etPhone.requestFocus()
    }

    private fun showStep2() {
        tvSubtitle.setText(R.string.phone_subtitle_step2)
        stepPhoneLayout.visibility = View.GONE
        stepOtpLayout.visibility   = View.VISIBLE
        // Show masked number: +91XXXXXX1234 → +91 ••••••1234
        val masked = maskPhone(enteredPhone)
        tvPhoneMasked.text = masked
        etOtp.requestFocus()
    }

    // ─── OTP flow ────────────────────────────────────────────────────────────

    private fun sendOtp(useResendToken: Boolean) {
        tilPhone.error = null
        val phone = etPhone.text.toString().trim()

        if (!useResendToken) {
            if (!phone.startsWith("+") || phone.length < 8) {
                tilPhone.error = getString(R.string.error_phone_invalid)
                return
            }
            enteredPhone = phone
        }

        setLoading(true)

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-retrieval or instant verification (mostly on test devices)
                signInWithCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                setLoading(false)
                val msg = e.localizedMessage ?: e.javaClass.simpleName
                tilPhone.error = msg
                Toast.makeText(this@LoginActivity, "OTP failed: $msg", Toast.LENGTH_LONG).show()
                android.util.Log.e("PhoneAuth", "onVerificationFailed", e)
                showStep1()
            }

            override fun onCodeSent(
                vId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = vId
                resendToken    = token
                setLoading(false)
                showStep2()
                Toast.makeText(
                    this@LoginActivity,
                    R.string.otp_sent,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(enteredPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)

        if (useResendToken && resendToken != null) {
            builder.setForceResendingToken(resendToken!!)
        }

        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    private fun verifyOtp() {
        tilOtp.error = null
        val otp = etOtp.text.toString().trim()
        val vid = verificationId

        if (otp.length < 6) {
            tilOtp.error = getString(R.string.error_otp_empty)
            return
        }
        if (vid == null) {
            Toast.makeText(this, R.string.error_request_otp, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        signInWithCredential(PhoneAuthProvider.getCredential(vid, otp))
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { goToMain() }
            .addOnFailureListener { e ->
                setLoading(false)
                tilOtp.error = e.localizedMessage
            }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun setLoading(show: Boolean) {
        progressBar.visibility  = if (show) View.VISIBLE else View.GONE
        btnSendOtp.isEnabled    = !show
        btnVerifyOtp.isEnabled  = !show
        tvResend.isEnabled      = !show
    }

    /** Masks middle digits: +91XXXXXXXX89 → +91 ••••••••89 */
    private fun maskPhone(phone: String): String {
        if (phone.length < 6) return phone
        val visible = phone.takeLast(2)
        val dots    = "•".repeat((phone.length - 3).coerceAtLeast(4))
        val prefix  = phone.take(3)          // e.g. "+91"
        return "$prefix $dots$visible"
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
