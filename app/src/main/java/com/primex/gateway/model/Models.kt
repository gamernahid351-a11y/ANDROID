package com.primex.gateway.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PaymentMethod { BKASH, NAGAD, ROCKET }
enum class TransactionStatus { SYNCED, QUEUED, FAILED, DUPLICATE }

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String,
    val trxId: String,
    val amount: Double,
    val method: PaymentMethod,
    val senderNumber: String,
    val username: String,
    val createdAt: Long = System.currentTimeMillis(),
    val smsDate: String? = null,
    val smsTime: String? = null,
    val status: TransactionStatus = TransactionStatus.QUEUED,
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val serverMessage: String? = null,
    val rawSmsPreview: String? = null
)

data class ParsedSmsResult(
    val isValid: Boolean,
    val method: PaymentMethod? = null,
    val trxId: String? = null,
    val amount: Double? = null,
    val senderNumber: String? = null,
    val smsDate: String? = null,
    val smsTime: String? = null,
    val rejectionReason: String? = null,
    val detectedSenderAlias: String? = null,
    val rawText: String = ""
)

data class UserSession(
    val username: String,
    val name: String,
    val token: String? = null,
    val specialNumber: String? = null,
    val apiKey: String? = null,
    val balance: Double? = null,
    val loginTime: Long = System.currentTimeMillis()
)

data class GatewayConfig(
    val monitoringEnabled: Boolean = false,
    val bkashEnabled: Boolean = true,
    val nagadEnabled: Boolean = true,
    val rocketEnabled: Boolean = true,
    val autoSync: Boolean = true,
    val soundAlerts: Boolean = true,
    val serverUrl: String = "http://5.83.134.223:3000",
    val permissionGranted: Boolean = false
)
