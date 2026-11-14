package com.primex.gateway.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.primex.gateway.databinding.FragmentSettingsBinding
import com.primex.gateway.model.GatewayConfig
import com.primex.gateway.ui.MainActivity
import com.primex.gateway.utils.PrefsManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val session = PrefsManager.getSession()
        val config = PrefsManager.getConfig()

        // Show user info
        binding.tvUsername.text = session?.username ?: "-"
        binding.tvStoreName.text = session?.name ?: "-"
        binding.tvApiKey.text = session?.apiKey ?: "N/A"
        binding.tvSpecialNumber.text = session?.specialNumber ?: "N/A"

        // Load current config
        binding.etServerUrl.setText(config.serverUrl)
        binding.switchBkash.isChecked = config.bkashEnabled
        binding.switchNagad.isChecked = config.nagadEnabled
        binding.switchRocket.isChecked = config.rocketEnabled
        binding.switchAutoSync.isChecked = config.autoSync
        binding.switchSound.isChecked = config.soundAlerts

        binding.btnSaveSettings.setOnClickListener {
            val newUrl = binding.etServerUrl.text.toString().trim()
            if (newUrl.isEmpty()) {
                Toast.makeText(requireContext(), "Server URL দিন", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val newConfig = GatewayConfig(
                monitoringEnabled = config.monitoringEnabled,
                bkashEnabled = binding.switchBkash.isChecked,
                nagadEnabled = binding.switchNagad.isChecked,
                rocketEnabled = binding.switchRocket.isChecked,
                autoSync = binding.switchAutoSync.isChecked,
                soundAlerts = binding.switchSound.isChecked,
                serverUrl = newUrl,
                permissionGranted = config.permissionGranted
            )
            PrefsManager.saveConfig(newConfig)
            Toast.makeText(requireContext(), "Settings সেভ হয়েছে ✓", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            (activity as? MainActivity)?.logout()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
