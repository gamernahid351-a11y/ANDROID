package com.primex.gateway.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.primex.gateway.databinding.FragmentHistoryBinding
import com.primex.gateway.model.PaymentMethod
import com.primex.gateway.model.Transaction
import com.primex.gateway.model.TransactionStatus
import com.primex.gateway.network.RetrofitClient
import com.primex.gateway.network.TransactionRequest
import com.primex.gateway.ui.adapters.TransactionAdapter
import com.primex.gateway.utils.AppDatabase
import com.primex.gateway.utils.PrefsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private lateinit var adapter: TransactionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.get(requireContext())

        val session = PrefsManager.getSession() ?: return

        adapter = TransactionAdapter { tx -> retryTransaction(tx) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            db.transactionDao().getAll(session.username).collectLatest { list ->
                adapter.submitList(list)
                binding.tvEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.tvTotalCount.text = "মোট ${list.size}টি transaction"
            }
        }

        binding.btnClearAll.setOnClickListener {
            lifecycleScope.launch {
                db.transactionDao().clearAll(session.username)
                PrefsManager.clearSeenTrxIds()
                Toast.makeText(requireContext(), "সব transaction মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun retryTransaction(tx: Transaction) {
        val session = PrefsManager.getSession() ?: return
        val config = PrefsManager.getConfig()
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.get(config.serverUrl)
                val res = api.sendTransaction(session.username,
                    TransactionRequest(tx.trxId, tx.amount, tx.method.name.lowercase(), tx.senderNumber))
                db.transactionDao().update(tx.copy(
                    status = TransactionStatus.SYNCED,
                    serverMessage = res.message,
                    lastAttemptAt = System.currentTimeMillis()
                ))
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "✓ Synced!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                db.transactionDao().update(tx.copy(
                    retryCount = tx.retryCount + 1,
                    lastAttemptAt = System.currentTimeMillis()
                ))
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
