package com.primex.gateway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.primex.gateway.service.GatewayForegroundService
import com.primex.gateway.utils.PrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        PrefsManager.init(context)
        val config = PrefsManager.getConfig()
        val session = PrefsManager.getSession()
        if (config.monitoringEnabled && session != null) {
            val serviceIntent = Intent(context, GatewayForegroundService::class.java).apply {
                action = GatewayForegroundService.ACTION_START
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
