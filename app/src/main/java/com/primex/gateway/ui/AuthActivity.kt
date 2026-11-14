package com.primex.gateway.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.primex.gateway.databinding.ActivityAuthBinding
import com.primex.gateway.model.UserSession
import com.primex.gateway.network.CreateUserRequest
import com.primex.gateway.network.LoginRequest
import com.primex.gateway.network.RetrofitClient
import com.primex.gateway.utils.PrefsManager
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private var mode = "register" // "register" | "login"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        PrefsManager.init(this)

        updateMode()

        binding.btnToggleMode.setOnClickListener {
            mode = if (mode == "register") "login" else "register"
            updateMode()
        }

        binding.btnSubmit.setOnClickListener { handleSubmit() }
    }

    private fun updateMode() {
        if (mode == "register") {
            binding.tvTitle.text = "নতুন অ্যাকাউন্ট তৈরি"
            binding.tvSubtitle.text = "আপনার দোকানের নাম দিয়ে রেজিস্ট্রেশন করুন"
            binding.tilConfirmPassword.visibility = View.VISIBLE
            binding.btnSubmit.text = "রেজিস্ট্রেশন করুন"
            binding.btnToggleMode.text = "আগে থেকে অ্যাকাউন্ট আছে? লগইন করুন"
        } else {
            binding.tvTitle.text = "লগইন করুন"
            binding.tvSubtitle.text = "আপনার username এবং পাসওয়ার্ড দিন"
            binding.tilConfirmPassword.visibility = View.GONE
            binding.btnSubmit.text = "লগইন করুন"
            binding.btnToggleMode.text = "নতুন অ্যাকাউন্ট তৈরি করুন"
        }
        clearErrors()
    }

    private fun handleSubmit() {
        clearErrors()
        val nameOrUsername = binding.etName.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirm = binding.etConfirmPassword.text.toString()

        if (nameOrUsername.isEmpty()) {
            binding.tilName.error = "নাম / username দিন"
            return
        }
        if (password.length < 6) {
            binding.tilPassword.error = "পাসওয়ার্ড কমপক্ষে ৬ অক্ষর হতে হবে"
            return
        }
        if (mode == "register" && password != confirm) {
            binding.tilConfirmPassword.error = "পাসওয়ার্ড মিলছে না"
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val config = PrefsManager.getConfig()
                val api = RetrofitClient.get(config.serverUrl)
                if (mode == "register") {
                    val res = api.createUser(CreateUserRequest(nameOrUsername, password))
                    if (res.error != null) throw Exception(res.error)
                    val session = UserSession(
                        username = res.username ?: nameOrUsername,
                        name = res.name ?: nameOrUsername,
                        token = res.token,
                        specialNumber = res.specialNumber,
                        apiKey = res.apiKey,
                        balance = res.balance
                    )
                    PrefsManager.saveSession(session)
                    goToMain()
                } else {
                    val res = api.login(LoginRequest(nameOrUsername, password))
                    if (res.error != null) throw Exception(res.error)
                    val session = UserSession(
                        username = res.username ?: nameOrUsername,
                        name = res.name ?: nameOrUsername,
                        token = res.token,
                        specialNumber = res.specialNumber,
                        apiKey = res.apiKey,
                        balance = res.balance
                    )
                    PrefsManager.saveSession(session)
                    goToMain()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@AuthActivity, e.message ?: "সার্ভার error", Toast.LENGTH_LONG).show()
                    setLoading(false)
                }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled = !loading
        binding.btnToggleMode.isEnabled = !loading
    }

    private fun clearErrors() {
        binding.tilName.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
    }
}
