package com.skd.notebook.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.skd.notebook.R
import com.skd.notebook.ui.MainActivity

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var tilName: TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etPassword: TextInputEditText
    private lateinit var tilConfirm: TextInputLayout
    private lateinit var etConfirm: TextInputEditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRegister: MaterialButton
    private lateinit var tvSignIn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) { goToMain(); return }
        setContentView(R.layout.activity_register)

        tilName     = findViewById(R.id.tilName)
        etName      = findViewById(R.id.etName)
        tilEmail    = findViewById(R.id.tilEmail)
        etEmail     = findViewById(R.id.etEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etPassword  = findViewById(R.id.etPassword)
        tilConfirm  = findViewById(R.id.tilConfirm)
        etConfirm   = findViewById(R.id.etConfirm)
        progressBar = findViewById(R.id.progressBar)
        btnRegister = findViewById(R.id.btnRegister)
        tvSignIn    = findViewById(R.id.tvSignIn)

        btnRegister.setOnClickListener { register() }
        tvSignIn.setOnClickListener    { finish() }   // back to Login
    }

    private fun register() {
        tilName.error = null; tilEmail.error = null
        tilPassword.error = null; tilConfirm.error = null

        val name     = etName.text.toString().trim()
        val email    = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirm  = etConfirm.text.toString().trim()

        if (name.isEmpty())            { tilName.error     = getString(R.string.error_name_empty);        return }
        if (email.isEmpty())           { tilEmail.error    = getString(R.string.error_email_empty);       return }
        if (password.length < 6)       { tilPassword.error = getString(R.string.error_password_short);    return }
        if (password != confirm)       { tilConfirm.error  = getString(R.string.error_password_mismatch); return }

        setLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                // Save display name; navigate to main regardless of outcome
                val req = UserProfileChangeRequest.Builder().setDisplayName(name).build()
                result.user?.updateProfile(req)?.addOnCompleteListener { goToMain() } ?: goToMain()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                tilEmail.error = e.localizedMessage
            }
    }

    private fun setLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnRegister.isEnabled  = !show
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
