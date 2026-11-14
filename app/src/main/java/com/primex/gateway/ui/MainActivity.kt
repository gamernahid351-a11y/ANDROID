package com.primex.gateway.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.primex.gateway.R
import com.primex.gateway.databinding.ActivityMainBinding
import com.primex.gateway.service.GatewayForegroundService
import com.primex.gateway.ui.fragments.DashboardFragment
import com.primex.gateway.ui.fragments.HistoryFragment
import com.primex.gateway.ui.fragments.SettingsFragment
import com.primex.gateway.utils.PrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val PERM_REQUEST_CODE = 100
        private val REQUIRED_PERMS = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsManager.init(this)

        // Guard: must be logged in
        if (PrefsManager.getSession() == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        checkPermissions()
        startGatewayServiceIfEnabled()
    }

    private fun setupNavigation() {
        // Show dashboard by default
        loadFragment(DashboardFragment())

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { loadFragment(DashboardFragment()); true }
                R.id.nav_history -> { loadFragment(HistoryFragment()); true }
                R.id.nav_settings -> { loadFragment(SettingsFragment()); true }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun checkPermissions() {
        val missing = REQUIRED_PERMS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERM_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST_CODE) {
            val smsGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
            if (!smsGranted) {
                Toast.makeText(this, "SMS permission required for gateway to work", Toast.LENGTH_LONG).show()
            } else {
                startGatewayServiceIfEnabled()
            }
        }
    }

    fun startGatewayServiceIfEnabled() {
        val config = PrefsManager.getConfig()
        if (config.monitoringEnabled && PrefsManager.getSession() != null) {
            val intent = Intent(this, GatewayForegroundService::class.java).apply {
                action = GatewayForegroundService.ACTION_START
            }
            startForegroundService(intent)
        }
    }

    fun stopGatewayService() {
        val intent = Intent(this, GatewayForegroundService::class.java).apply {
            action = GatewayForegroundService.ACTION_STOP
        }
        startService(intent)
    }

    fun logout() {
        stopGatewayService()
        PrefsManager.saveSession(null)
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }
}
