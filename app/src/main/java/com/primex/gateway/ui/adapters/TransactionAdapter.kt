package com.primex.gateway.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.primex.gateway.R
import com.primex.gateway.databinding.ItemTransactionBinding
import com.primex.gateway.model.PaymentMethod
import com.primex.gateway.model.Transaction
import com.primex.gateway.model.TransactionStatus
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private val onRetry: (Transaction) -> Unit
) : ListAdapter<Transaction, TransactionAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val b: ItemTransactionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(tx: Transaction) {
            // Method badge
            val (methodLabel, methodColor) = when (tx.method) {
                PaymentMethod.BKASH -> Pair("bKash", Color.parseColor("#E91E8C"))
                PaymentMethod.NAGAD -> Pair("Nagad", Color.parseColor("#FF6600"))
                PaymentMethod.ROCKET -> Pair("Rocket", Color.parseColor("#8B00FF"))
            }
            b.tvMethod.text = methodLabel
            b.tvMethod.setBackgroundColor(methodColor)

            // Amount
            b.tvAmount.text = "৳ %.2f".format(tx.amount)

            // TrxID & sender
            b.tvTrxId.text = tx.trxId
            b.tvSender.text = tx.senderNumber

            // Status
            val (statusText, statusColor) = when (tx.status) {
                TransactionStatus.SYNCED -> Pair("✓ Synced", Color.parseColor("#22c55e"))
                TransactionStatus.QUEUED -> Pair("⏳ Queued", Color.parseColor("#f59e0b"))
                TransactionStatus.FAILED -> Pair("✗ Failed", Color.parseColor("#ef4444"))
                TransactionStatus.DUPLICATE -> Pair("⊘ Duplicate", Color.parseColor("#94a3b8"))
            }
            b.tvStatus.text = statusText
            b.tvStatus.setTextColor(statusColor)

            // Server message
            b.tvServerMsg.text = tx.serverMessage ?: ""

            // Time
            val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            b.tvTime.text = sdf.format(Date(tx.createdAt))

            // Retry button
            val showRetry = tx.status == TransactionStatus.FAILED || tx.status == TransactionStatus.QUEUED
            b.btnRetry.visibility = if (showRetry) android.view.View.VISIBLE else android.view.View.GONE
            b.btnRetry.setOnClickListener { onRetry(tx) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private object DiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(a: Transaction, b: Transaction) = a.id == b.id
        override fun areContentsTheSame(a: Transaction, b: Transaction) = a == b
    }
}
