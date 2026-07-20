package com.rondesnfc.mobile

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rondesnfc.mobile.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: Session
    private lateinit var api: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = Session(this)
        api = ApiClient(session)

        binding.baseUrlInput.setText(session.baseUrl)

        if (session.isLoggedIn) {
            goToScan()
            return
        }

        binding.loginButton.setOnClickListener { login() }
    }

    private fun login() {
        val baseUrl = binding.baseUrlInput.text.toString().trim()
        val badge = binding.badgeInput.text.toString().trim()
        val pin = binding.pinInput.text.toString().trim()

        if (baseUrl.isEmpty() || badge.isEmpty() || pin.isEmpty()) {
            binding.statusText.text = "Renseignez l'adresse du serveur, le badge et le PIN"
            return
        }

        session.baseUrl = baseUrl
        binding.statusText.text = "Connexion..."
        binding.loginButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = api.login(badge, pin)
                session.token = result.token
                session.guardName = result.guardName
                session.role = result.role
                binding.statusText.text = "Connecte : ${result.guardName}"
                goToScan()
            } catch (e: ApiException) {
                binding.statusText.text = "Refuse : ${e.message}"
            } catch (e: NetworkException) {
                binding.statusText.text = "Serveur injoignable : ${e.message}"
            } finally {
                binding.loginButton.isEnabled = true
            }
        }
    }

    private fun goToScan() {
        startActivity(Intent(this, ScanActivity::class.java))
        finish()
    }
}
