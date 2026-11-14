package com.primex.gateway.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.primex.gateway.databinding.FragmentDashboardBinding
import com.primex.gateway.model.GatewayConfig
import com.primex.gateway.model.PaymentMethod
import com.primex.gateway.model.Transaction
import com.primex.gateway.model.TransactionStatus
import com.primex.gateway.network.RetrofitClient
import com.primex.gateway.network.TransactionRequest
import com.primex.gateway.parser.SmsParser
import com.primex.gateway.service.GatewayForegroundService
import com.primex.gateway.ui.MainActivity
import com.primex.gateway.utils.AppDatabase
import com.primex.gateway.utils.PrefsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.get(requireContext())

        val session = PrefsManager.getSession() ?: return
        val config = PrefsManager.getConfig()

        // Header info
        binding.tvUsername.text = session.username
        binding.tvStoreName.text = session.name
        binding.tvServerUrl.text = config.serverUrl

        // Gateway toggle
        updateToggleUI(config)
        binding.btnToggleGateway.setOnClickListener {
            val current = PrefsManager.getConfig()
            val newConfig = current.copy(monitoringEnabled = !current.monitoringEnabled)
            PrefsManager.saveConfig(newConfig)
            updateToggleUI(newConfig)
            if (newConfig.monitoringEnabled) {
                (activity as? MainActivity)?.startGatewayServiceIfEnabled()
                Toast.makeText(requireContext(), "Gateway চালু হয়েছে ✓", Toast.LENGTH_SHORT).show()
            } else {
                (activity as? MainActivity)?.stopGatewayService()
                Toast.makeText(requireContext(), "Gateway বন্ধ হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }

        // Manual SMS test
        binding.btnTestSms.setOnClickListener {
            val text = binding.etManualSms.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "SMS text দিন", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            processManualSms(text)
        }

        // Observe transactions for stats
        lifecycleScope.launch {
            db.transactionDao().getAll(session.username).collectLatest { list ->
                updateStats(list)
            }
        }
    }

    private fun updateToggleUI(config: GatewayConfig) {
        if (config.monitoringEnabled) {
            binding.btnToggleGateway.text = "⬛ Gateway বন্ধ করুন"
            binding.tvGatewayStatus.text = "● ACTIVE — SMS মনিটর করা হচ্ছে"
            binding.tvGatewayStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            binding.btnToggleGateway.text = "▶ Gateway চালু করুন"
            binding.tvGatewayStatus.text = "○ INACTIVE — Gateway বন্ধ"
            binding.tvGatewayStatus.setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }
    }

    private fun updateStats(list: List<Transaction>) {
        val bkash = list.filter { it.method == PaymentMethod.BKASH }
        val nagad = list.filter { it.method == PaymentMethod.NAGAD }
        val rocket = list.filter { it.method == PaymentMethod.ROCKET }
        val synced = list.filter { it.status == TransactionStatus.SYNCED }
        val queued = list.filter { it.status == TransactionStatus.QUEUED || it.status == TransactionStatus.FAILED }
        val total = list.sumOf { it.amount }

        binding.tvTotalCount.text = list.size.toString()
        binding.tvTotalAmount.text = "৳ %.2f".format(total)
        binding.tvBkashCount.text = "${bkash.size} · ৳%.0f".format(bkash.sumOf { it.amount })
        binding.tvNagadCount.text = "${nagad.size} · ৳%.0f".format(nagad.sumOf { it.amount })
        binding.tvRocketCount.text = "${rocket.size} · ৳%.0f".format(rocket.sumOf { it.amount })
        binding.tvSyncedCount.text = synced.size.toString()
        binding.tvQueuedCount.text = queued.size.toString()
    }

    private fun processManualSms(text: String) {
        val session = PrefsManager.getSession() ?: return
        val config = PrefsManager.getConfig()
        val parsed = SmsParser.parseIncoming(text,
            bkashEnabled = config.bkashEnabled,
            nagadEnabled = config.nagadEnabled,
            rocketEnabled = config.rocketEnabled)

        if (!parsed.isValid) {
            Toast.makeText(requireContext(), "Invalid SMS: ${parsed.rejectionReason}", Toast.LENGTH_LONG).show()
            return
        }

        if (PrefsManager.isTrxSeen(parsed.trxId!!)) {
            Toast.makeText(requireContext(), "Duplicate TrxID: ${parsed.trxId}", Toast.LENGTH_SHORT).show()
            return
        }

        PrefsManager.markTrxSeen(parsed.trxId)

        lifecycleScope.launch {
            val tx = Transaction(
                id = UUID.randomUUID().toString(),
                trxId = parsed.trxId,
                amount = parsed.amount!!,
                method = parsed.method!!,
                senderNumber = parsed.senderNumber!!,
                username = session.username,
                smsDate = parsed.smsDate,
                smsTime = parsed.smsTime,
                status = TransactionStatus.QUEUED,
                rawSmsPreview = text.take(100)
            )
            db.transactionDao().insert(tx)

            try {
                val api = RetrofitClient.get(config.serverUrl)
                val res = api.sendTransaction(session.username,
                    TransactionRequest(tx.trxId, tx.amount, tx.method.name.lowercase(), tx.senderNumber))
                db.transactionDao().update(tx.copy(
                    status = TransactionStatus.SYNCED,
                    serverMessage = res.message ?: "Synced",
                    lastAttemptAt = System.currentTimeMillis()
                ))
                requireActivity().runOnUiThread {
                    binding.etManualSms.text?.clear()
                    Toast.makeText(requireContext(), "✓ ${parsed.method!!.name} ৳${parsed.amount} synced!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                db.transactionDao().update(tx.copy(
                    status = TransactionStatus.FAILED,
                    serverMessage = e.message,
                    lastAttemptAt = System.currentTimeMillis()
                ))
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Queued — সার্ভার unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
