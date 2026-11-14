package com.primex.gateway.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.primex.gateway.R
import com.primex.gateway.model.Transaction
import com.primex.gateway.model.TransactionStatus
import com.primex.gateway.network.RetrofitClient
import com.primex.gateway.network.TransactionRequest
import com.primex.gateway.parser.SmsParser
import com.primex.gateway.ui.MainActivity
import com.primex.gateway.utils.AppDatabase
import com.primex.gateway.utils.PrefsManager
import kotlinx.coroutines.*
import java.util.UUID

class GatewayForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.primex.gateway.START"
        const val ACTION_STOP = "com.primex.gateway.STOP"
        const val ACTION_PROCESS_SMS = "com.primex.gateway.PROCESS_SMS"
        const val EXTRA_SMS_BODY = "sms_body"
        const val EXTRA_SMS_SENDER = "sms_sender"
        private const val CHANNEL_ID = "primex_gateway_channel"
        private const val NOTIF_ID = 1001
        private const val TAG = "PrimeX_Service"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: AppDatabase

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.get(this)
        PrefsManager.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification("Gateway aktif - SMS monitoring চলছে"))
                scheduleRetryWorker()
            }
            ACTION_STOP -> {
                serviceScope.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PROCESS_SMS -> {
                val body = intent.getStringExtra(EXTRA_SMS_BODY) ?: return START_STICKY
                val sender = intent.getStringExtra(EXTRA_SMS_SENDER)
                startForeground(NOTIF_ID, buildNotification("SMS প্রসেস করা হচ্ছে..."))
                serviceScope.launch { handleSms(body, sender) }
            }
        }
        return START_STICKY
    }

    private suspend fun handleSms(body: String, senderHeader: String?) {
        val config = PrefsManager.getConfig()
        val session = PrefsManager.getSession() ?: run {
            Log.w(TAG, "No session - SMS skipped")
            return
        }

        if (!config.monitoringEnabled) {
            Log.d(TAG, "Monitoring disabled - SMS skipped")
            return
        }

        val parsed = SmsParser.parseIncoming(
            text = body,
            senderHeader = senderHeader,
            bkashEnabled = config.bkashEnabled,
            nagadEnabled = config.nagadEnabled,
            rocketEnabled = config.rocketEnabled
        )

        if (!parsed.isValid) {
            Log.d(TAG, "SMS rejected: ${parsed.rejectionReason}")
            return
        }

        // Duplicate check
        if (PrefsManager.isTrxSeen(parsed.trxId!!)) {
            Log.d(TAG, "Duplicate TrxID: ${parsed.trxId}")
            return
        }

        PrefsManager.markTrxSeen(parsed.trxId)

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
            rawSmsPreview = body.take(100)
        )

        db.transactionDao().insert(tx)
        updateNotification("নতুন ${parsed.method.name}: ৳${parsed.amount}")

        // Sync to server
        try {
            val api = RetrofitClient.get(config.serverUrl)
            val res = api.sendTransaction(
                session.username,
                TransactionRequest(
                    trxId = parsed.trxId,
                    amount = parsed.amount,
                    method = parsed.method.name.lowercase(),
                    senderNumber = parsed.senderNumber
                )
            )
            val synced = tx.copy(
                status = if (res.status == "queued") TransactionStatus.QUEUED else TransactionStatus.SYNCED,
                serverMessage = res.message ?: "সার্ভারে সফলভাবে সেভ হয়েছে",
                lastAttemptAt = System.currentTimeMillis()
            )
            db.transactionDao().update(synced)
            updateNotification("✓ Synced: ${parsed.method.name} ৳${parsed.amount}")
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
            val failed = tx.copy(
                status = TransactionStatus.FAILED,
                serverMessage = e.message ?: "সার্ভারে পাঠানো যায়নি",
                lastAttemptAt = System.currentTimeMillis()
            )
            db.transactionDao().update(failed)
        }
    }

    private fun scheduleRetryWorker() {
        serviceScope.launch {
            while (isActive) {
                delay(30_000) // 30 seconds
                val session = PrefsManager.getSession() ?: continue
                val config = PrefsManager.getConfig()
                if (!config.autoSync) continue

                val pending = db.transactionDao().getPending(session.username)
                for (tx in pending.take(3)) {
                    try {
                        val api = RetrofitClient.get(config.serverUrl)
                        val res = api.sendTransaction(
                            session.username,
                            TransactionRequest(tx.trxId, tx.amount, tx.method.name.lowercase(), tx.senderNumber)
                        )
                        db.transactionDao().update(tx.copy(
                            status = if (res.status == "queued") TransactionStatus.QUEUED else TransactionStatus.SYNCED,
                            serverMessage = res.message,
                            lastAttemptAt = System.currentTimeMillis()
                        ))
                    } catch (e: Exception) {
                        db.transactionDao().update(tx.copy(
                            retryCount = tx.retryCount + 1,
                            lastAttemptAt = System.currentTimeMillis()
                        ))
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "PRIME X Gateway", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "SMS Gateway monitoring service"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PRIME X GATEWAY")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pi)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
