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
import com.google.android.material.button.MaterialButtonToggleGroup
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

    // Views
    private lateinit var toggleAuthMode: MaterialButtonToggleGroup
    private lateinit var emailForm: LinearLayout
    private lateinit var phoneForm: LinearLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etPassword: TextInputEditText
    private lateinit var tilPhone: TextInputLayout
    private lateinit var etPhone: TextInputEditText
    private lateinit var btnSendOtp: MaterialButton
    private lateinit var tilOtp: TextInputLayout
    private lateinit var etOtp: TextInputEditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnPrimary: MaterialButton
    private lateinit var tvSwitchToRegister: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        // Skip login if already signed in
        if (auth.currentUser != null) { goToMain(); return }
        setContentView(R.layout.activity_login)

        bindViews()
        setupToggle()

        btnPrimary.setOnClickListener {
            if (toggleAuthMode.checkedButtonId == R.id.btnTabEmail) emailSignIn()
            else verifyOtp()
        }
        btnSendOtp.setOnClickListener { sendOtp() }
        tvSwitchToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // ─── Bind ────────────────────────────────────────────────────────────────

    private fun bindViews() {
        toggleAuthMode      = findViewById(R.id.toggleAuthMode)
        emailForm           = findViewById(R.id.emailForm)
        phoneForm           = findViewById(R.id.phoneForm)
        tilEmail            = findViewById(R.id.tilEmail)
        etEmail             = findViewById(R.id.etEmail)
        tilPassword         = findViewById(R.id.tilPassword)
        etPassword          = findViewById(R.id.etPassword)
        tilPhone            = findViewById(R.id.tilPhone)
        etPhone             = findViewById(R.id.etPhone)
        btnSendOtp          = findViewById(R.id.btnSendOtp)
        tilOtp              = findViewById(R.id.tilOtp)
        etOtp               = findViewById(R.id.etOtp)
        progressBar         = findViewById(R.id.progressBar)
        btnPrimary          = findViewById(R.id.btnPrimary)
        tvSwitchToRegister  = findViewById(R.id.tvSwitchToRegister)
    }

    // ─── Tab toggle ──────────────────────────────────────────────────────────

    private fun setupToggle() {
        toggleAuthMode.check(R.id.btnTabEmail)
        showEmailMode()
        toggleAuthMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) when (checkedId) {
                R.id.btnTabEmail -> showEmailMode()
                R.id.btnTabPhone -> showPhoneMode()
            }
        }
    }

    private fun showEmailMode() {
        emailForm.visibility = View.VISIBLE
        phoneForm.visibility = View.GONE
        btnPrimary.setText(R.string.sign_in)
        btnPrimary.visibility = View.VISIBLE
    }

    private fun showPhoneMode() {
        emailForm.visibility = View.GONE
        phoneForm.visibility = View.VISIBLE
        // Primary action only appears after OTP is sent
        btnPrimary.visibility = if (verificationId != null) View.VISIBLE else View.GONE
    }

    // ─── Email sign-in ───────────────────────────────────────────────────────

    private fun emailSignIn() {
        tilEmail.error = null; tilPassword.error = null
        val email    = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        if (email.isEmpty())    { tilEmail.error    = getString(R.string.error_email_empty);   return }
        if (password.isEmpty()) { tilPassword.error = getString(R.string.error_password_short); return }

        setLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { goToMain() }
            .addOnFailureListener { e ->
                setLoading(false)
                tilPassword.error = e.localizedMessage
            }
    }

    // ─── Phone / OTP ─────────────────────────────────────────────────────────

    private fun sendOtp() {
        tilPhone.error = null
        val phone = etPhone.text.toString().trim()
        if (!phone.startsWith("+") || phone.length < 8) {
            tilPhone.error = getString(R.string.error_phone_invalid); return
        }

        setLoading(true)
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(cred: PhoneAuthCredential) {
                signInWithCredential(cred)
            }
            override fun onVerificationFailed(e: FirebaseException) {
                setLoading(false)
                tilPhone.error = e.localizedMessage
            }
            override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = vId
                setLoading(false)
                tilOtp.visibility = View.VISIBLE
                btnSendOtp.setText(R.string.resend_otp)
                btnPrimary.setText(R.string.verify_otp)
                btnPrimary.visibility = View.VISIBLE
                Toast.makeText(this@LoginActivity, R.string.otp_sent, Toast.LENGTH_SHORT).show()
            }
        }

        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build()
        )
    }

    private fun verifyOtp() {
        tilOtp.error = null
        val otp = etOtp.text.toString().trim()
        val vid = verificationId
        if (otp.isEmpty()) { tilOtp.error = getString(R.string.error_otp_empty); return }
        if (vid == null)   { Toast.makeText(this, R.string.error_request_otp, Toast.LENGTH_SHORT).show(); return }

        setLoading(true)
        signInWithCredential(PhoneAuthProvider.getCredential(vid, otp))
    }

    private fun signInWithCredential(cred: PhoneAuthCredential) {
        auth.signInWithCredential(cred)
            .addOnSuccessListener { goToMain() }
            .addOnFailureListener { e ->
                setLoading(false)
                tilOtp.error = e.localizedMessage
            }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun setLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnPrimary.isEnabled   = !show
        btnSendOtp.isEnabled   = !show
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
