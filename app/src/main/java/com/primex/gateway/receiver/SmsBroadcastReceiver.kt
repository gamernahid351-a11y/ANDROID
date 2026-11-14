package com.primex.gateway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.primex.gateway.service.GatewayForegroundService

class SmsBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PrimeX_SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Stitch multi-part SMS messages together
        val grouped = messages.groupBy { it.originatingAddress ?: "" }

        for ((address, parts) in grouped) {
            val fullBody = parts.joinToString("") { it.messageBody ?: "" }
            if (fullBody.isBlank()) continue

            Log.d(TAG, "SMS from: $address | body: ${fullBody.take(80)}")

            // Forward to foreground service for processing
            val serviceIntent = Intent(context, GatewayForegroundService::class.java).apply {
                action = GatewayForegroundService.ACTION_PROCESS_SMS
                putExtra(GatewayForegroundService.EXTRA_SMS_BODY, fullBody)
                putExtra(GatewayForegroundService.EXTRA_SMS_SENDER, address)
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
